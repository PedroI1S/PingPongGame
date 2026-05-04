# Aim Roulette Pong

A 3D first-person ping-pong reaction game built on LibGDX.

You stand at one end of the table. Balls come at you. You click to return.
Single-player puts you against a bot; LAN multiplayer puts you against another
player over a server-authoritative connection — one of you hosts and the
other joins with a short room code.

## Modes

### Single-player

A bot serves and returns. Lose all your lives or knock out the bot's. The
match runs entirely locally on a single `MatchWorld3D` simulation.

### LAN multiplayer

Server-authoritative. When you press **HOST**, the game spins up an embedded
TCP game server (port 7777) and connects to it as Player 1. The other player
presses **JOIN**, types your room code, and connects as Player 2. Both clients
are then pure drawing + input — physics, collision, and scoring run on the
server, which broadcasts state snapshots at 30 Hz.

The room code is the host's local IPv4 address encoded in base-36 (7
characters). For testing two clients on the same machine, use the code shown
on the join screen as `LOCAL TEST CODE`, which decodes to `127.0.0.1`.

## Controls

- `ENTER` / mouse click — continue from the menu
- `H` — host a multiplayer match (in the lobby)
- `J` — join a multiplayer match (in the lobby)
- `Mouse click` — serve / return the ball
- `R` — restart the current duel (single-player)
- `ESC` — back to the previous screen

## Match rules

- Both sides start with 5 lives.
- Whoever wins a point serves the next ball.
- A point ends on miss, into-the-net, double-bounce on your side, or
  out-of-bounds.

## How to run

```bash
./gradlew lwjgl3:run
```

To verify the project builds:

```bash
./gradlew build
```

For a quick local LAN test, launch the game twice. On window 1: Multiplayer →
HOST. On window 2: Multiplayer → JOIN, type the local test code shown on the
join screen.

## Architecture

```
PingPongGame/
├── core/             # game logic, screens, networking, embedded server
└── lwjgl3/           # LWJGL3 desktop launcher
```

### Module layout

- `core/.../Main.java` — game bootstrap, screen navigation
- `core/.../core/` — `GameContext`, `GameSession`, lifetime objects
- `core/.../screen/` — Loading, Menu, MatchScreen3D, MultiplayerLobbyScreen, NetMatchScreen
- `core/.../world/` — `MatchWorld3D` (authoritative physics, bot AI), particle pool
- `core/.../model/` — `MatchConfig`, `MatchOutcome`, etc.
- `core/.../network/` — binary `GameConnection`, `PacketType`, `RoomCode`
- `core/.../server/` — `GameServer` (headless authoritative loop, 60 Hz physics, 30 Hz state broadcast)
- `core/.../assets/` — procedurally generated UI textures
- `core/.../input/` — `InputAdapter` subclasses for menus
- `core/.../config/` — `GameConfig`, `Palette`

### Networking

- Two TCP sockets between server and clients. The host's game embeds the
  server in-process; the joining client connects over the network.
- Binary protocol via `DataInputStream` / `DataOutputStream`. See
  `network/PacketType.java` for the wire format.
- STATE packets carry ball position + velocity + lives + active player; clients
  dead-reckon between snapshots using gravity.
- Control packets: `WELCOME`, `WAITING`, `HELLO`, `SERVE`, `HIT`,
  `GAME_OVER`, `SFX`, `BYE`.

### Rendering

- 3D scene assembled from `ModelBuilder` primitives — no model files. The
  table, net, ball, and floor are box / sphere geometry with diffuse colors
  and a single directional light.
- 2D HUD drawn over the 3D pass: lives, status text, aim ring, and bounce
  particles projected back onto screen-space.
- Six procedural textures (pixel, panel, background, glow, aim ring, noise)
  generated at startup — no PNGs ship with the game.

## Planned

- In-match item pickups (the previous pre-match card system is gone — items
  will appear during play instead)
- Difficulty profiles for the bot

## Documentation

- `docs/architecture.md` — deeper technical breakdown
- `docs/gameplay-roadmap.md` — design notes
