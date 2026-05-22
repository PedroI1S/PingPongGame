# Aim Roulette Pong

A 3D first-person ping-pong reaction game built on LibGDX.

You stand at one end of the table. Balls come at you. You click to return.
Single-player puts you against a bot; LAN multiplayer puts you against another
player. Both modes share a server-authoritative architecture: a headless
`GameServer` runs all physics and the clients are pure draw + click.

## Modes

### VS BOT

`Main.openMatch()` spawns an **in-process server** on `127.0.0.1:7777` on a
daemon thread, joins as Player 1 with `JOIN(BOT)`, and lets the server's bot
AI play P2. There is nothing to install or pre-launch — it just works from
`./gradlew lwjgl3:run`.

### LAN multiplayer

- **HOST** — same in-process server, bound on `0.0.0.0:7777` so the LAN peer
  can reach it. The host joins as P1 with `JOIN(PVP)`, the server holds the
  slot until a second player connects.
- **JOIN** — connects to the host's IPv4 (encoded as a 7-character room
  code), sends `JOIN(PVP)`, and is paired up as P2.

The room code is base-36 of the host's IPv4. For local testing, the join
screen shows a `LOCAL TEST CODE` that decodes to `127.0.0.1`.

### Dedicated server (optional)

If you'd rather run the server as its own process (Docker, VPS, separate
machine):

```bash
./gradlew :server:run                     # listens on 0.0.0.0:7777
# or build a fat jar:
./gradlew :server:jar
java -jar server/build/libs/LibGDX-Versao3-server-1.0.0.jar --bind 0.0.0.0 --port 7777
```

Point clients at it with the environment variable `PINGPONG_SERVER_HOST`
(default `127.0.0.1`).

## Controls

- `ENTER` / mouse click — continue from the menu
- `H` — host a multiplayer match (in the lobby)
- `J` — join a multiplayer match (in the lobby)
- `C` — open settings (from menu)
- `Mouse click` — serve / return the ball
- `ESC` — pause / back to the previous screen

## Match rules

- Both sides start with 5 lives.
- **P1 serves first** in PvP; the bot serves first in VS BOT.
- Whoever wins a point serves the next ball.
- A point ends on miss, into-the-net, double-bounce on your side, or
  out-of-bounds.

## Settings (in-game)

Open from the menu (`C` key or **CONFIGURATION** button). Four tabs:

- **AUDIO** — Master / Music / SFX / UI volume sliders. The active match
  reads these (music + SFX) on the next paddle hit and on re-entry.
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
./gradlew :server:run    # only if you want a standalone dedicated server
```

For a quick local LAN test: launch the game twice in separate terminals.
Window 1: Multiplayer → HOST. Window 2: Multiplayer → JOIN, type the local
test code shown on the join screen.

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
  `MatchWorld3D` (physics + bot AI), `HitVelocity` and `ServerPickRay`
  (server-side click validation), the binary protocol in
  `network/PacketType.java`, `network/GameConnection.java`, model classes
  (`MatchConfig`, `MatchMode`, `MatchOutcome`, `FighterConfig`,
  `ArenaSide`), and the persistent `server/GameServer.java`. **No libGDX UI
  dependencies.**
- **`server`** is just a `main()` (`ServerMain.java`) plus `application` /
  fat-jar Gradle config so the headless server can be deployed.
- **`core`** is the client: `MatchArenaRenderer` (3D scene + camera shake),
  the screens (`MenuScreen`, `MatchConnectScreen`, `NetMatchScreen`,
  `MultiplayerLobbyScreen`, `ConfigScreen`, `PauseMenuScreen`,
  `LoadingScreen`), `GameContext` / `GameSession` / `GameSettings`,
  `InProcessServer` + `LocalServerProcess` for spawning the server, and the
  `RetroPostProcess` shader pass.
- **`lwjgl3`** is the desktop launcher (`Lwjgl3Launcher.java`).

### Client → server flow

```
MenuScreen ── VS BOT
   │
   ▼
Main.openMatch()
   └─ MatchConnectScreen(BOT, "127.0.0.1", spawnLocal=true)
        │
        ▼ background thread: InProcessServer.start() → GameServer.run() binds 127.0.0.1:7777
        ▼ on bind → session.attachLocalServer(server::stop) → GameConnection.connect()
        ▼ on connect → sendJoin(MODE_BOT)
        ▼ WELCOME(1) + MATCH_READY(BOT) → openNetMatch()
        ▼
NetMatchScreen
   - sends CLICK(x, y, viewportW, viewportH)
   - receives STATE 30 Hz, dead-reckons ball between snapshots
   - receives SFX, GAME_OVER
```

### Binary protocol

See `sim/.../network/PacketType.java` for constants. Highlights:

| Direction | Packet | Payload |
|---|---|---|
| S → C | `WAITING` | — |
| S → C | `WELCOME` | `byte playerNumber` |
| S → C | `MATCH_READY` | `byte mode` (`MODE_PVP` / `MODE_BOT`) |
| S → C | `STATE` | `float px py pz vx vy vz`, `int p1lives p2lives`, `byte ballVisible`, `byte activePlayer` |
| S → C | `GAME_OVER` | `byte winnerPlayer` |
| S → C | `SFX` | `byte sfxType` (`SFX_PADDLE` / `SFX_TABLE`) |
| C → S | `JOIN` | `byte mode` |
| C → S | `CLICK` | `int screenX screenY viewportW viewportH` |
| C → S | `BYE` | — |

The server rebuilds the player's pick ray from `CLICK` using
`ServerPickRay.fromScreen(playerNumber, x, y, vw, vh)`, which mirrors
`MatchArenaRenderer`'s camera. There is no `HIT` packet on the live path —
clients never send velocities, so a malicious client can't fabricate one.

### Rendering

- 3D scene assembled from `ModelBuilder` primitives — no model files. Table,
  net, ball, floor are box / sphere geometry with diffuse colors and a single
  directional light. Camera shake adds a per-frame offset.
- 2D HUD drawn over the 3D pass: lives, status text, aim ring, bounce
  particles projected back onto screen-space.
- `RetroPostProcess` wraps the match render in an FBO and applies a
  palette-quantized + dithered + vignetted shader pass. Toggled / tuned via
  Settings → GRAPHICS. **Match screens only** — menus skip the post-process
  so text stays readable.

## Planned

- In-match item pickups (the previous pre-match card system is gone — items
  will appear during play instead).
- Online multiplayer over Steam (NAT traversal + friend invites).
- Snapshot interpolation, client-side prediction, server-side reconnect.

## Documentation

- `docs/architecture.md` — deeper technical breakdown.
- `docs/gameplay-roadmap.md` — design notes.
- `docs/plan.md` — forward-looking priority queue.
- `docs/art-style.md` — palette, 3D-model prompts, shader pipeline.
