# Architecture Notes

## Goal

A LibGDX 3D first-person ping-pong reaction game with a clean client/server
split. Single binary runs both **VS BOT** and **LAN multiplayer**; both
modes are served by the same authoritative `GameServer` — either in-process
or out-of-process — so there is one physics implementation.

## Module split

```
PingPongGame/
├── sim/        # shared physics + protocol + headless server
├── server/     # fat-jar launcher around sim/GameServer
├── core/       # client: screens, rendering, settings, input
└── lwjgl3/     # LWJGL3 desktop launcher for `core`
```

| Module | Depends on | Brings |
|---|---|---|
| `sim` | `gdx` (math only) | `MatchWorld3D`, `GameServer`, `GameConnection`, `PacketType`, `ServerPickRay`; physics package: `BallPhysics`, `PaddleContact`, `BotPlanner`, `PhysicsConfig`, `BallState`, `StepContacts`; tutorial package: `DrillCourse`, `TutorialGeometry`, `ZoneRect`; all model / config classes |
| `server` | `sim` | `ServerMain` (~10 lines), application/jar Gradle setup |
| `core` | `sim`, libGDX UI | every `Screen` (incl. `TutorialScreen`), `MatchArenaRenderer`, `RetroPostProcess`, `GameContext`, `GameSession`, `GameSettings`, `InProcessServer`, `LocalServerProcess` |
| `lwjgl3` | `core` | `Lwjgl3Launcher` |

Why three layers instead of two?

- `sim` has no UI / no `SpriteBatch` / no fonts so it's safe to depend on
  from a headless deploy. The libGDX `gdx` dependency is just for `Vector3`,
  `MathUtils`, `Pool`, etc. — no rendering.
- `server` is a single `main()` so the build can produce a small standalone
  fat jar without dragging in any of the client.

## Main technical decisions

### 1. `Main` owns screen navigation

`Main` extends `Game`. The flow:

- `LoadingScreen` — boots `AssetManager`, builds procedural textures.
- `MenuScreen` — entry. **VS BOT**, **MULTIPLAYER**, **CONFIGURATION**
  buttons (ENTER / M / C also work).
- `MatchConnectScreen` — bridges menu → match. For VS BOT it spawns an
  `InProcessServer` then opens a `GameConnection`; for LAN-JOIN it just
  opens the connection. Sends `JOIN(mode)` on connect, waits for
  `WELCOME` + `MATCH_READY`, then transitions to `NetMatchScreen`.
- `MultiplayerLobbyScreen` — H / J / room-code entry. HOST spawns its own
  in-process server bound to `0.0.0.0`; JOIN dials a remote IP.
- `NetMatchScreen` — the actual match. Pure draw + click.
- `PauseMenuScreen` — overlay over a paused match.
- `ConfigScreen` — tabbed settings (AUDIO / GRAPHICS / CONTROLS / GAME).

`Main` doesn't override `render()` — `Game.render()` already delegates to
the active screen.

### 2. `GameContext` holds shared lifetime objects

Owns: `SpriteBatch`, `FitViewport`, fonts, `GlyphLayout`, `GameAssets`,
`GameSession`, `GameSettings`, and the lazily-built `RetroPostProcess`.

`GameAssets` and `ShaderManager` are singletons (`instance()`); the
context's accessors are the normal way to reach them, and
`GameContext.dispose()` tears both down. Compiled `ShaderProgram`s are
cached in `ShaderManager` and owned by it — consumers (e.g.
`RetroPostProcess`) must not dispose them.

Menu-style screens (`MenuScreen`, `PauseMenuScreen`,
`MultiplayerLobbyScreen`) extend `MenuBaseScreen`, which owns cursor
unprojection, input-processor wiring, and button hover/click plumbing.
Reusable widgets (`Button`, `UIDraw`, `SettingsWidgets`) live in the
`ui` package, separate from the screens that compose them.

### 3. `GameSession` carries the multiplayer link

Persists across screens:

- last match outcome (so the menu can show "VICTORY" / "DEFEAT")
- the active `GameConnection`, player number (1 or 2), `MatchMode`
- a stop hook for the embedded server (`InProcessServer::stop` or
  `LocalServerProcess::stop`) — fired on `clearMultiplayer()` so the
  server dies with the match
- the remote player's display name
- a shared `RandomXS128`

`MatchConfig.createDefault()` is built fresh per match — no pre-match
loadout.

### 4. Asset pipeline is procedural

`GameAssets` wraps libGDX `AssetManager`. The visuals are six textures
generated at startup through `ProceduralAssetsLoader` — pixel, panel,
background, glow, aim ring, noise. The only file assets loaded are the
three audio clips and the generated voxel OBJ models (table+net, ball,
items, fly — produced by `tools/voxel/generate_*.py`); `MatchArenaRenderer`
falls back to `ModelBuilder` geometry if a model fails to load.

### 5. Physics engine

`MatchWorld3D` delegates all ball motion to `BallPhysics`. One `stepBall` call
per server tick runs 240 Hz fixed substeps internally; per-phase rules
(serve placement, scoring, phase transitions) stay in `MatchWorld3D`.

Key decisions:

- **Integrator.** Euler substep (dt = 1/240 s) with gravity, quadratic drag,
  and Magnus force from paper SI constants (Lin, Yu & Huang, *Sensors* 2020,
  §4.2) mapped to world units via `unitsPerMeter = 5.11` and `timeScale = 0.442`
  (≈ half-speed slow motion). Changing `timeScale` slows or speeds every
  trajectory without altering its physical shape.
- **Spin-coupled bounce.** Restitution e = 0.92 (paper); friction impulse +
  2/7 grip cap converts ground-frame slip into spin and vice versa at each
  table contact.
- **Net collider.** Swept check at z = 0; a random cord-jitter draw
  (`netJitter = 0.18`) sets whether the ball dribbles over or falls back.
  Scoring is decided by which half the ball lands on — the old
  crossing-frame fault rule is gone.
- **Off-center click mapping.** `PaddleContact.applyReturn` converts the
  normalised click offset (ndx, ndy) to aim + pace + spin: top = topspin,
  bottom = backspin, sides = aim + sidespin + corkscrew tilt; pace carries
  35% of the incoming speed (so hard incoming balls come back hard). Reversed
  spin transfer (−30%) makes the model self-consistent on long rallies.
  `PaddleContact.clamp` is the anti-cheat envelope (maxSpeedSI = 14 m/s,
  maxSpinSI = 180 rad/s).
- **Tunables** live entirely in `PhysicsConfig`. `createDefault()` reproduces
  today's game feel; any field can be overridden per-match.

### 6. Tutorial drill course

`sim/tutorial/` is a pure-logic package — no rendering, fully headless-testable.

- **`DrillCourse`** owns a `BallPhysics` + `BallState`, schedules practice feeds, and
  evaluates attempts against zone predicates. Six drills in order:

  | # | Drill | Technique gate |
  |---|---|---|
  | 1 | Timing | contact must be inside the near-strip (z ∈ [3.5, 6.0]) |
  | 2 | Aim | landing must reach the lit left or right corridor |
  | 3 | Topspin | contact-time spinX < −10 **and** landing in the near band (z ∈ [−3.4, −0.8]) |
  | 4 | Backspin | contact-time spinX > +10 **and** landing in the deep band (z ∈ [−6.6, −3.8]) |
  | 5 | Curve | contact-time |spinY| > 10 **and** landing in the centre zone; pole intercept fails the attempt |
  | 6 | Serve | serve must land in the alternating short-left / deep-right zone |

  Counterintuitive zone pairing: under the shipped `PhysicsConfig` gains, pace is
  offset-magnitude-driven, so a **topspin click** (flat arc + Magnus dip) lands
  *short* and a **backspin click** (loft + float) lands *deep*. The bands are
  sized so a flat centre-click falls in the gap between them and passes neither
  drill — the zone forces the technique.

  Spin and curve drills play a **slow-motion demo ball** first (timeScale halved via
  `DEMO_SLOWMO = 0.5`). Demo balls are scripted ideal shots from the player's side —
  not incoming feeds — so the on-screen curve handedness matches what the player
  must reproduce. Demo balls ignore player clicks.

  The test suite (`DrillCourseTest`, `SpinDrillTest`, `CurveServeDrillTest`) includes
  per-drill beatability tests that drive `DrillCourse` headlessly with scripted
  ideal offsets and assert graduation to the next drill. These are pinned to
  `PhysicsConfig.createDefault()`, so a physics retune that silently breaks a drill
  fails CI.

- **`TutorialGeometry`** — single source of truth for all zone constants, pole
  geometry (`POLE_X=0, POLE_Z=−3, POLE_RADIUS=0.45, POLE_HEIGHT=1.4 m above table`),
  and `segmentHitsPole` (2D swept circle-vs-cylinder check with height gate).

- **`ZoneRect`** — immutable axis-aligned rectangle on the table plane (x/z);
  `contains` is inclusive on all edges.

`TutorialScreen` (in `core/`) consumes `DrillCourse` and renders it:

- Runs entirely client-side — no `GameServer`, no `GameConnection`, no network.
- After all six drills pass, starts a graduation rally: a real local `MatchWorld3D`
  in BOT mode, 3 lives. The bot profile is softened immediately after construction
  (`aimSigma = 0.95`, `reactionDelay = 0.8 s` via `getBotProfile()`). The item
  phase is skipped by calling `playerReady(1)` + `playerReady(2)` as soon as the
  world enters the item phase.
- Zones and pole are rendered as 3D overlay models via the arena renderer's
  `getModelBatch()` / `getCamera()` / `getEnvironment()` accessors — no new render
  infrastructure.

Menu integration:

- `MenuScreen` shows a `[ TUTORIAL ]` button (T key shortcut) between VS BOT and
  MULTIPLAYER.
- `GameSettings.tutorialCompleted` (persisted via libGDX `Preferences`) gates the
  caption below the buttons: uncompleted shows a first-run nudge; completed shows
  the normal mode summary.

### 7. Server-authoritative architecture

`GameServer` (in `sim/`) is a persistent loop:

```
runServer:
  bind(serverSocket); onListening.run();
  while (!shutdown) {
    runBestOf3():
      MatchLobby lobby; accept() connections until ready
        - first JOIN locks the mode
        - PVP needs P1 + P2; BOT is ready with just P1
      send MATCH_READY to all
      while (p1Wins < 2 && p2Wins < 2 && !shutdown):
        winner = runOneRound(mode):
          build MatchWorld3D + setMatchMode
          loop at 60 Hz while the match is running:
            - drain action queue (CLICK / USE_ITEM / READY / BYE)
            - world.update(delta)
            - emit SFX
            - every 1/30 s: broadcast STATE to all
          return round winner (0 if a player disconnected mid-round)
        if winner == 0: abort the match (a player left) and break
        tally the win; send ROUND_OVER; pause between rounds
      if not aborted: send GAME_OVER to the best-of-3 winner
      close connections, loop again
  }
```

Two entry points for a client to reach this server:

- **In-process** (`InProcessServer`) — spawns the server on a daemon thread
  in the client's JVM. Used by VS BOT (bound `127.0.0.1`) and HOST
  (bound `0.0.0.0`). Synchronous startup via a `CountDownLatch`
  triggered by the `onListening` callback added to `GameServer.run`.
- **Subprocess** (`LocalServerProcess`) — runs the `server` fat jar in
  a child JVM. Detects readiness by tailing stdout. This is the primary
  path tried first by `Main.autoLaunchServer()`; the in-process server
  above is the fallback used when the jar can't be located or launched.

### 8. Click-based protocol

Clients **never compute physics**. On a mouse click they emit:

```
CLICK { int screenX, int screenY, int viewportWidth, int viewportHeight }
```

The server reconstructs the same camera the client used
(`ServerPickRay.fromScreen(playerNumber, x, y, vw, vh)` — mirrors
`MatchArenaRenderer`'s position / FOV) and feeds the pick ray into
`MatchWorld3D.handlePlayerClick` / `handleOpponentClick`. This guarantees:

- The same physics decides whether a click was a hit, regardless of who
  clicked.
- A malicious client can't fabricate an impossible return — `PaddleContact`
  is run server-side and `PaddleContact.clamp` is the velocity/spin envelope.

### 9. Networking layer

- `sim/network/GameConnection` — typed binary wrapper around `Socket`.
  Reader thread decodes packets and dispatches via an `Executor`. Clients
  pass `Gdx.app::postRunnable` (callbacks land on the GL thread); the
  server passes `Runnable::run` (callbacks fire on the reader thread,
  queued via `LinkedBlockingQueue` into the game loop).
- `sim/network/PacketType` — wire-format constants. See README for the
  full packet table. The STATE packet carries 9 floats: position (px, py, pz),
  velocity (vx, vy, vz), and spin (sx, sy, sz). Clients extrapolate with the
  shared `BallPhysics` integrator from the last snapshot (capped at 0.25 s);
  the only remaining client/server visual divergence is the net-cord jitter
  roll, which resolves within ≤1 snapshot (~33 ms).
- `sim/network/RoomCode` — host's IPv4 encoded as a 7-character base-36
  string so players don't type dotted quads.

### 10. Server hit-test parity

`MatchArenaRenderer` builds its `PerspectiveCamera` with:

```
fov = 60°
target = (0, TABLE_TOP_Y, 0)
position = target + (0, 2.5, ±11)   // ±11 for P1 / P2
near = 0.1, far = 100
up = (0, 1, 0)
```

`ServerPickRay.fromScreen` constructs an identical camera so
`cam.getPickRay(x, y)` produces the same ray the client sees. The
viewport dimensions sent in `CLICK` make this resolution-independent.

### 11. Rendering

- **`MatchArenaRenderer`** owns the 3D scene (table / net / ball / floor)
  and the camera. Exposes `setCameraShake(dx, dy, dz)` so the match
  screen can wobble the view on bounces and lost lives.
- **`RetroPostProcess`** wraps the match render in an FBO and applies a
  palette-quantized + dithered + vignetted fragment shader. **Match
  screens only** — menus skip it so text stays crisp.
- 2D HUD over the 3D pass: lives, status text, aim ring, bounce particles
  projected back onto screen-space, optional FPS counter overlay.

### 12. Settings

`GameSettings` persists via libGDX `Preferences`:

- Audio: master / music / sfx / ui volumes (0–100). Composite getters
  `getMusicGain()` and `getSfxGain()` give the master-multiplied 0..1
  values used by `NetMatchScreen` when playing sounds.
- Graphics: fullscreen on/off, window resolution preset (hidden when
  fullscreen), retro filter on/off, retro intensity preset.
- Game: show FPS counter, screen shake on/off.

`MatchConnectScreen` / `MultiplayerLobbyScreen` / `MenuScreen` /
`PauseMenuScreen` / `ConfigScreen` / `LoadingScreen` all render straight
to the back buffer (no `RetroPostProcess`); only `NetMatchScreen`
wraps in the post-process.

## Match loop

### VS BOT

1. P1 (player) connects, sends `JOIN(BOT)`. `setMatchMode(BOT)` sets
   `nextServer = 2` and `statusText = "Bot is preparing the opening shot."`.
2. After `OPENING_DELAY`, the server auto-runs `botServe()` (this only
   happens in BOT mode).
3. Ball flies +z toward P1 (INCOMING phase from the server's POV).
4. P1 clicks → `CLICK` packet → server `handlePlayerClick(ray)` →
   `tryHitBall(ray)` if past the net. On a successful hit, `BotPlanner.plan`
   is called immediately: it forward-simulates the ball with the same
   integrator, finds the post-bounce apex on the bot's half, and records a
   strike time (floored at `reactionDelay = 0.55 s`). Gaussian aim error
   σ = 0.60 in contact-offset units gives ≈73% geometric return rate; a
   draw outside the unit disc is a whiff.
5. Ball flies −z back. When the strike time elapses, `BotPlanner` fires
   `PaddleContact.applyReturn` with the planned offsets (or whiffs). A
   swing-time OOB guard prevents the bot from swinging after the ball has
   already scored.
6. Scorer serves next: P1 clicks to serve when `nextServer == 1`, bot
   auto-serves when `nextServer == 2`.
7. Difficulty knobs live in `BotPlanner.Profile` (`aimSigma`,
   `reactionDelay`, `aggression`). `rallySpeedup` ramps outgoing pace
   linearly from 1× to 1.6× over the rally, reusing the legacy
   `BASE_APPROACH_DURATION` / `APPROACH_DURATION_DECAY` / `MIN_APPROACH_DURATION`
   config keys (those keys are still live). `BOT_BASE_RETURN_CHANCE` and
   `BOT_RESPONSE_DELAY` are legacy/unused (kept for config compatibility —
   the dice-roll bot and freeze timer they controlled are gone).

### PvP

Same physics. `setMatchMode(PVP)` sets `nextServer = 1` so P1 opens.
Either side's click goes through `handlePlayerClick` (P1) or
`handleOpponentClick` (P2). No auto-serve.

## Why server-authoritative

- **Symmetry.** P1 and P2 see the same physics — no local-simulation
  advantage. The client is one class (`NetMatchScreen`) instead of
  host-vs-client branches.
- **Anti-cheat surface area.** Server validates every CLICK. Clients
  can't send velocities directly.
- **Deploy flexibility.** Same `GameServer` runs in-process for VS BOT
  / HOST, or out-of-process for a dedicated deploy. The wire format is
  identical.

## Things that hurt and what to do

- **Snapshot interpolation.** Clients extrapolate from the last STATE packet
  using the shared `BallPhysics` integrator (capped at 0.25 s). On internet
  latency (30–150 ms RTT, packet loss / reorder) this still looks jittery.
  Buffer ~100 ms of incoming snapshots and render the past — smooth and
  accurate, at the cost of a small fixed delay. Interpolation buffering is
  the next meaningful improvement here; only the net-cord jitter roll
  (~33 ms gap) remains an inherent client/server divergence.
- **Client-side prediction.** The click effect doesn't appear until the next
  STATE confirms it.
- **Reconnect.** A momentary disconnect kills the match. Session IDs
  + buffered STATE replay would fix it; not implemented.

## Good next refactors

- Snapshot interpolation in `NetMatchScreen` (see above).
- `BotPlanner.Profile` presets per difficulty level (easy/medium/hard) —
  tuning `aimSigma` / `reactionDelay` / `aggression` gives a range from
  beginner to expert without touching physics.
- Event bus (`PaddleHit`, `TableBounce`, `PointScored`) so audio,
  particles, and HUD decouple from `MatchWorld3D`'s polled flags.
- Steam Networking + Lobbies for online play (see `docs/plan.md`).
