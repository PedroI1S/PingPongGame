# Aim Roulette Pong

A 3D first-person ping-pong reaction game built on LibGDX.

You stand at one end of the table. Balls come at you. You click to return.
Single-player puts you against a prediction-driven bot; LAN multiplayer puts you
against another player. Both modes share a server-authoritative architecture: a
headless `GameServer` runs all physics and the clients are pure draw + click.
Between points, a Buckshot-Roulette-style **item phase** deals each side gadgets
that bend the next rally.

## Modes

The game boots a game server for you. `Main.autoLaunchServer()` runs once at
startup on a background thread, binding `0.0.0.0:7777` so LAN guests can reach
the host directly. It tries, first success wins:

1. **External server** — if `PINGPONG_SERVER_HOST` points somewhere other than
   localhost, no local server is launched; clients just dial that host.
2. **Subprocess** (`LocalServerProcess`) — forks the server fat jar if it's
   already built.
3. **In-process** (`InProcessServer`) — an in-JVM fallback that needs no jar, so
   plain `./gradlew lwjgl3:run` works during development.

Both VS BOT and HOST connect to this already-running server — there's nothing to
pre-launch.

### VS BOT

`Main.openMatch()` opens `MatchConnectScreen(BOT)`, which connects to the local
server and sends `JOIN(BOT)`. The server fills P2 with its `BotPlanner` AI, which
predicts the ball's landing point and swings for a legal return.

### TUTORIAL

A local, offline drill course (`TutorialScreen` + `sim/tutorial/DrillCourse`).
Six drills — timing, aim, topspin, backspin, curve-around-the-pole, aimed serve —
then a graduation rally against a gentle sparring bot. It runs the **same**
`BallPhysics` the match uses, so what you learn transfers exactly. First-run
players are nudged here from the menu.

### LAN multiplayer

Open the lobby (`MultiplayerLobbyScreen`), then:

- **HOST** — connects to the local server and sends `JOIN(PVP)`; the server holds
  the slot until a second player connects. Your **room code** (7 characters,
  base-36 of your IPv4) is shown to share.
- **JOIN** — type the host's room code, which decodes back to their IPv4, then
  connect and pair up as P2.

For local testing, the join screen shows a `LOCAL TEST CODE` that decodes to
`127.0.0.1`.

### Dedicated server (optional)

To run the server as its own process (Docker, VPS, separate machine) instead of
letting the client auto-launch it:

```bash
./gradlew :server:run                     # listens on 0.0.0.0:7777
# or build a fat jar:
./gradlew :server:jar
java -jar server/build/libs/LibGDX-Versao3-server-1.0.0.jar --bind 0.0.0.0 --port 7777
```

Point clients at it with the environment variable `PINGPONG_SERVER_HOST`
(default `127.0.0.1`); when it's set to a remote host, clients skip the local
auto-launch entirely.

## Controls

- **Menu** — `ENTER` / `SPACE` VS BOT · `T` tutorial · `M` multiplayer ·
  `C` configuration (all also clickable buttons)
- **Lobby** — `H` host · `J` join · type the room code · `ENTER` connect
- **Match** — mouse click to serve / return the ball
- `ESC` — pause / back to the previous screen

## Match rules

- Both sides start with 5 lives.
- **P1 serves first** in PvP; the bot serves first in VS BOT.
- Whoever wins a point serves the next ball.
- A point ends on a miss, into-the-net, a double-bounce on your side, a volley
  (striking before the ball bounces on your side), or out-of-bounds.
- After every lost point the match enters the **item phase** before the next
  serve.

## Item phase

When a point ends, both players are dealt 2 items from a 9-item pool, use any
they like, and press **END SELECTION** to serve on. The phase waits for both
sides to ready up (no timeout). A toggleable event log narrates *why* points were
lost (volley, double-bounce, timeout, fly hit, coin-flip).

| Item | Effect |
|---|---|
| `PATCH_KIT` | Restore your lives to full |
| `WIDE_PADDLE` | Your click hit-box is enlarged next rally |
| `SLOW_MO` | Your incoming ball is slowed |
| `STEAL` | Take a random item from the opponent |
| `FAST_SERVE` | The opponent's incoming ball is sped up |
| `TINY_PADDLE` | Shrink the opponent's hit-box |
| `PUNCH` | Client-side screen blur on the opponent (purely visual) |
| `FLY_BAIT` | Spawn flies on the opponent's side; the ball dies if it hits one |
| `COIN_FLIP` | 50/50 — someone loses a life |

## Settings (in-game)

Open from the menu (`C` key or **CONFIGURATION** button). Four tabs:

- **AUDIO** — Master / Music / SFX / UI volume sliders. The active match reads
  these (music + SFX) on the next paddle hit and on re-entry.
- **GRAPHICS** — Fullscreen toggle, window-resolution radio (hidden in
  fullscreen), retro filter on/off, filter intensity preset.
- **CONTROLS** — read-only key map (rebinding not implemented yet).
- **GAME** — Show FPS Counter (overlay in the top-left of the match HUD),
  Screen Shake (camera shakes on bounces and lost lives).

All settings persist via libGDX `Preferences`.

## How to run

```bash
./gradlew lwjgl3:run     # play
./gradlew build          # verify everything compiles
./gradlew test           # run the headless physics / rules / tutorial tests
./gradlew :server:run    # only if you want a standalone dedicated server
```

For a quick local LAN test: launch the game twice in separate terminals.
Window 1: MULTIPLAYER → HOST. Window 2: MULTIPLAYER → JOIN, type the local
test code shown on the join screen.

## Physics

The ball motion is a proper SI-unit simulation (`sim/world/physics`), not a
scripted arc. Constants come from Lin, Yu & Huang, *"Ball Tracking and
Trajectory Prediction for Table-Tennis Robots"* (Sensors 2020) and are converted
into world units by `PhysicsConfig`, with a `timeScale` for exact slow-motion.

- **`BallPhysics`** — flight integrator: gravity, quadratic drag, the Magnus
  force (`a = k·(ω × v)`), and spin decay.
- **Table bounce** — spin-coupled restitution (e = 0.92) with a friction impulse
  capped at the 2/7 rolling-grip limit, so topspin/backspin change the bounce.
- **Net** — a swept physical collider with cord jitter (a shot can dribble over
  or fall back), not a scripted kill plane.
- **`PaddleContact`** — maps *where* on the paddle you hit (off-center) to pace,
  arc, aim, and spin. Serves are aimable. Replaces the old `HitVelocity`.
- **`SpinOrientation`** — integrates the ball's spin into a visible model
  rotation; the renderer spins the ball each frame.
- **`BotPlanner`** — predicts the landing point through the ball's flight and
  swings for a legal return, replacing the earlier dice-roll bot.

`PhysicsConfig` also defines an anti-cheat clamp envelope (max speed / spin).

## Architecture

```
PingPongGame/
├── core/             # client only — screens, rendering, settings, input
├── sim/              # shared: physics + wire format + dedicated-server loop
├── server/           # thin fat-jar launcher around sim/GameServer
└── lwjgl3/           # LWJGL3 desktop launcher for `core`
```

### Module split

- **`sim`** holds the authoritative pieces that don't need a window:
  `world/MatchWorld3D` (rules + phase machine), the `world/physics` package
  (`BallPhysics`, `PaddleContact`, `BotPlanner`, `PhysicsConfig`,
  `SpinOrientation`, …), `world/ServerPickRay` (server-side click validation),
  the item model (`model/ItemType`, `ItemEffects`, `PlayerInventory`), the
  tutorial engine (`tutorial/DrillCourse`, `TutorialGeometry`, `ZoneRect`), the
  binary protocol (`network/PacketType`, `GameConnection`, `RoomCode`), model
  classes (`MatchConfig`, `MatchMode`, `MatchOutcome`, `FighterConfig`,
  `ArenaSide`), and the persistent `server/GameServer`. **No libGDX UI
  dependencies** (it depends on `gdx` for math only, and is headless-testable —
  see `sim/src/test`).
- **`server`** is just a `main()` (`ServerMain.java`) plus `application` /
  fat-jar Gradle config so the headless server can be deployed.
- **`core`** is the client: `render/MatchArenaRenderer` (3D scene + camera
  shake), `render/ItemPhaseRenderer`, the screens (`MenuScreen`,
  `MultiplayerLobbyScreen`, `MatchConnectScreen`, `NetMatchScreen`,
  `TutorialScreen`, `ConfigScreen`, `PauseMenuScreen`, `LoadingScreen`),
  `core/GameContext` / `GameSession` / `GameSettings`, `client/InProcessServer` +
  `client/LocalServerProcess` for launching the server, and the
  `render/RetroPostProcess` shader pass.
- **`lwjgl3`** is the desktop launcher (`Lwjgl3Launcher.java`).

### Client → server flow

```
MenuScreen ── VS BOT
   │
   ▼
Main.openMatch()  (server already launched at boot on 0.0.0.0:7777)
   └─ MatchConnectScreen(BOT)
        │
        ▼ GameConnection.connect() → sendJoin(MODE_BOT)
        ▼ WELCOME(1) + MATCH_READY(BOT) → openNetMatch()
        ▼
NetMatchScreen
   - sends CLICK(x, y, viewportW, viewportH)
   - receives STATE 30 Hz (pos + vel + spin), extrapolates with shared BallPhysics
   - receives SFX, ITEM_DEALT / ITEM_USED, FLY_SPAWN, LOG_EVENT, GAME_OVER
```

### Binary protocol

See `sim/.../network/PacketType.java` for constants. Highlights:

| Direction | Packet | Payload |
|---|---|---|
| S → C | `WAITING` | — |
| S → C | `WELCOME` | `byte playerNumber` |
| S → C | `MATCH_READY` | `byte mode` (`MODE_PVP` / `MODE_BOT`) |
| S → C | `STATE` | `float px py pz`, `float vx vy vz`, `float sx sy sz` (spin), `int p1lives p2lives`, `byte ballVisible`, `byte activePlayer` |
| S → C | `GAME_OVER` | `byte winnerPlayer` |
| S → C | `SFX` | `byte sfxType` (`SFX_PADDLE` / `SFX_TABLE`) |
| S → C | `ROUND_OVER` | `byte winnerPlayer`, `byte p1Wins p2Wins` |
| S → C | `ITEM_DEALT` | `byte player`, `byte count`, `count × byte itemId` |
| S → C | `ITEM_USED` | `byte player`, `byte itemId` |
| S → C | `ITEM_READY` | `byte player` |
| S → C | `FLY_SPAWN` | `byte owner`, `byte count`, `count × (float x, float z)` |
| S → C | `FLY_KILLED` | `byte owner`, `byte flyIndex` |
| S → C | `LOG_EVENT` | `byte eventCode`, `byte subjectPlayer` |
| C → S | `HELLO` | — |
| C → S | `JOIN` | `byte mode` |
| C → S | `CLICK` | `int screenX screenY viewportW viewportH` |
| C → S | `USE_ITEM` | `byte itemId` |
| C → S | `BYE` | — |

The server rebuilds the player's pick ray from `CLICK` using
`ServerPickRay.fromScreen(playerNumber, x, y, vw, vh)`, which mirrors
`MatchArenaRenderer`'s camera. Clients never send velocities or spin, so a
malicious client can't fabricate a shot — the retired `SERVE`/`HIT` packets
(13/14) are gone.

### Rendering

- 3D scene assembled from `ModelBuilder` primitives — no model files. Table,
  net, ball, floor are box / sphere geometry with diffuse colors and a single
  directional light. The ball rotates with its accumulated spin; camera shake
  adds a per-frame offset.
- 2D HUD drawn over the 3D pass: lives, status text, aim ring, bounce particles
  projected back onto screen-space, and the item-phase overlay + event log.
- `RetroPostProcess` wraps the match render in an FBO and applies a
  palette-quantized + dithered + vignetted shader pass. Toggled / tuned via
  Settings → GRAPHICS. **Match screens only** — menus skip the post-process so
  text stays readable.

## Planned

- Online multiplayer over Steam (NAT traversal + friend invites).
- Snapshot interpolation, client-side prediction, server-side reconnect.

## Documentation

- `docs/architecture.md` — deeper technical breakdown.
- `docs/gameplay-roadmap.md` — design notes.
- `docs/plan.md` — forward-looking priority queue.
- `docs/art-style.md` — palette, 3D-model prompts, shader pipeline.
</content>
</invoke>
