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
| `sim` | `gdx` (math only) | `MatchWorld3D`, `GameServer`, `GameConnection`, `PacketType`, `ServerPickRay`, `HitVelocity`, all model / config classes |
| `server` | `sim` | `ServerMain` (~10 lines), application/jar Gradle setup |
| `core` | `sim`, libGDX UI | every `Screen`, `MatchArenaRenderer`, `RetroPostProcess`, `GameContext`, `GameSession`, `GameSettings`, `InProcessServer`, `LocalServerProcess` |
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

### 5. Server-authoritative architecture

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

### 6. Click-based protocol

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
- A malicious client can't fabricate an impossible return velocity —
  `HitVelocity.computeFromRay` is run server-side.

The legacy `HIT(vx,vy,vz)` packet still exists for compatibility but is
not used by the live flow. `HitVelocity.sanitizeNetworkReturn` validates
it if anything ever emits one.

### 7. Networking layer

- `sim/network/GameConnection` — typed binary wrapper around `Socket`.
  Reader thread decodes packets and dispatches via an `Executor`. Clients
  pass `Gdx.app::postRunnable` (callbacks land on the GL thread); the
  server passes `Runnable::run` (callbacks fire on the reader thread,
  queued via `LinkedBlockingQueue` into the game loop).
- `sim/network/PacketType` — wire-format constants. See README for the
  full packet table.
- `sim/network/RoomCode` — host's IPv4 encoded as a 7-character base-36
  string so players don't type dotted quads.

### 8. Server hit-test parity

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

### 9. Rendering

- **`MatchArenaRenderer`** owns the 3D scene (table / net / ball / floor)
  and the camera. Exposes `setCameraShake(dx, dy, dz)` so the match
  screen can wobble the view on bounces and lost lives.
- **`RetroPostProcess`** wraps the match render in an FBO and applies a
  palette-quantized + dithered + vignetted fragment shader. **Match
  screens only** — menus skip it so text stays crisp.
- 2D HUD over the 3D pass: lives, status text, aim ring, bounce particles
  projected back onto screen-space, optional FPS counter overlay.

### 10. Settings

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
   `tryHitBall(ray)` if past the net.
5. Ball flies −z back. On bounce on the bot's side, phase →
   `BOT_RESOLVE`. After `BOT_RESPONSE_DELAY`, the bot returns (with a
   tuned probability) or misses.
6. Scorer serves next: P1 clicks to serve when `nextServer == 1`, bot
   auto-serves when `nextServer == 2`.

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

- **Snapshot interpolation.** Clients currently *extrapolate* from the
  last STATE using gravity. On internet latency (30–150 ms RTT, occasional
  packet loss / reorder) this looks jittery. Buffer ~100 ms of incoming
  snapshots and render the past — smooth and accurate, at the cost of a
  small delay.
- **Client-side prediction.** Same problem from the input side. Today the
  click effect doesn't appear until the next STATE confirms it.
- **HIT-velocity sanitizer is no longer reachable** in the live path —
  `CLICK` made it dead code. Keep the function (cheap insurance for
  future deviations) but the audit surface is now `ServerPickRay` +
  `tryHitBall`.
- **Reconnect.** A momentary disconnect kills the match. Session IDs
  + buffered STATE replay would fix it; not implemented.

## Good next refactors

- Snapshot interpolation in `NetMatchScreen` (see above).
- `ServePattern` declarative bot recipes (fast straight / slow fakeout /
  cross / wide) instead of one `botBaseReturnChance` knob.
- Event bus (`PaddleHit`, `TableBounce`, `PointScored`) so audio,
  particles, and HUD decouple from `MatchWorld3D`'s polled flags.
- Steam Networking + Lobbies for online play (see `docs/plan.md`).
