# Architecture Notes

## Goal

A LibGDX foundation for a 3D first-person ping-pong reaction game.
Single binary that runs both single-player (vs bot) and LAN multiplayer
through a server-authoritative architecture.

## Main technical decisions

### 1. `Main` owns screen navigation

`Main` extends `Game`. The screen flow is:

- `LoadingScreen` — boots `AssetManager`, builds procedural textures
- `MenuScreen` — entry; ENTER for single-player, M for multiplayer
- `MatchScreen3D` — single-player vs the local bot
- `MultiplayerLobbyScreen` — H to host, J to join, room code entry
- `NetMatchScreen` — the networked match (used by both players)

`Main` doesn't override `render()` — `Game.render()` already delegates to
the active `Screen.render(delta)`.

### 2. `GameContext` holds shared lifetime objects

`GameContext` owns:

- `SpriteBatch`
- `FitViewport`
- title and body fonts
- `GlyphLayout`
- `GameAssets`
- `GameSession`

This keeps screens lightweight and avoids recreating heavy systems
between transitions.

### 3. `GameSession` stores cross-screen state

`GameSession` persists:

- last match outcome (so the menu can show "VICTORY" / "DEFEAT")
- the active multiplayer `GameConnection` and player number (1 or 2)
- the local `GameServer` instance when this client is also the host
- the remote player's display name
- a shared `RandomXS128`

`MatchConfig.createDefault()` is built fresh per match — there is no
pre-match loadout that mutates it.

### 4. Asset pipeline is procedural

`GameAssets` wraps LibGDX `AssetManager`.

The visuals are six textures generated at startup through
`ProceduralAssetsLoader` — pixel, panel, background, glow, aim ring,
noise. No PNGs ship for visuals; the only file assets loaded are the
three audio clips (`Sounds/Effects/...`, `Sounds/Music/...`).

The 3D table, net, ball, and floor are not textures at all — they are
`ModelBuilder` box / sphere geometry assembled when the match screen
shows.

### 5. Input is per-screen

Each screen owns its own `InputAdapter`:

- `MenuInputProcessor` — ENTER / mouse to advance, M for multiplayer
- `MatchScreen3D.Input3D` — click to return, R restart, ESC menu
- `NetMatchScreen.NetInput` — click to serve / return, ESC menu
- `MultiplayerLobbyScreen.InputHandler` — H/J/typing/ENTER

All registered via `Gdx.input.setInputProcessor` in `show()` and cleared
in `hide()`.

### 6. `MatchWorld3D` owns the simulation

`MatchWorld3D` is the authoritative game state. Same class is used for:

- single-player (instantiated locally in `MatchScreen3D`)
- multiplayer (instantiated inside `GameServer` when hosting)

It owns the ball position, velocity, lives, phase machine
(PREPARE_SERVE → INCOMING → OUTGOING → BOT_RESOLVE), bot AI for
single-player, scoring rule (loser serves next), and impact-particle
pool. The 2D side-view world (`MatchWorld`, `IncomingBall`,
`TableGeometry`, etc.) is gone.

### 7. Multiplayer is server-authoritative

When a player presses HOST:

1. `GameServer` starts on a daemon thread; `ServerSocket(7777)` opens.
2. The host's game connects to `127.0.0.1:7777` as Player 1.
3. The other player connects to the host's IP as Player 2.
4. Server runs `MatchWorld3D.update(delta)` at 60 Hz.
5. Server broadcasts a STATE packet to both clients at 30 Hz.
6. Clients dead-reckon the ball between snapshots using gravity, send
   only SERVE / HIT inputs to the server.

Both clients are pure drawing + input — even the host's own game
window. There is no host-advantage in physics; the host just happens
to also run the server.

### 8. Networking layer

- `network/GameConnection` — typed binary wrapper around a TCP `Socket`.
  Reader thread decodes packets and dispatches via an `Executor`.
  Clients pass `Gdx.app::postRunnable` (callbacks land on the GL
  thread). The server passes `Runnable::run` (callbacks fire on the
  reader thread, posted to a `LinkedBlockingQueue` for the game loop).
- `network/PacketType` — wire format constants (WAITING, WELCOME,
  STATE, GAME_OVER, SFX, HELLO, SERVE, HIT, BYE).
- `network/RoomCode` — host's IPv4 encoded as a 7-character base-36
  string so players don't type dotted quads.

### 9. Object pooling

`ImpactParticle3D` is managed through a LibGDX `Pool` so bounce sparks
don't allocate per frame.

## Package responsibilities

### `assets`

- `GameAssets` — central asset gateway
- `ProceduralAssets` — six procedurally generated textures
- `ProceduralAssetsLoader` — custom `AssetManager` loader that has no
  external file dependencies

### `config`

- `GameConfig` — shared tuning constants (lives, timings, viewport
  size, network timeout, port)
- `Palette` — UI colors

### `core`

- `GameContext` — app-wide resources
- `GameSession` — cross-screen state, holds the multiplayer connection

### `input`

- `MenuInputProcessor` — single-screen processor; lobby / match
  screens have their own inner-class adapters

### `model`

- `MatchConfig` / `FighterConfig` — per-match tuning
- `MatchOutcome` / `ArenaSide` — enums

### `network`

- `GameConnection`, `PacketType`, `RoomCode`

### `screen`

- `BaseScreen` — shared helpers
- `LoadingScreen` — asset boot with progress bar
- `MenuScreen` — entry point
- `MatchScreen3D` — single-player match
- `MultiplayerLobbyScreen` — host / join flow
- `NetMatchScreen` — networked match (symmetric for P1 and P2)
- `UIDraw` — primitive HUD rendering helpers

### `server`

- `GameServer` — headless authoritative loop, accepts two TCP
  connections, runs `MatchWorld3D` at 60 Hz, broadcasts STATE at
  30 Hz, processes SERVE / HIT inputs through a thread-safe queue

### `world`

- `MatchWorld3D` — authoritative simulation + bot AI
- `DuelistState` — per-side runtime state (lives, multipliers)
- `ImpactParticle3D` — pooled feedback effect

## Match loop

### Single-player

1. Bot serves automatically (PREPARE_SERVE phase, short timer).
2. Ball flies toward the player at +z.
3. Player clicks the ball; `MatchWorld3D.tryHitBall(Ray)` does ray-sphere
   intersection and computes a return velocity.
4. Ball flies toward bot at −z.
5. Bot either returns it (probability based on `botBaseReturnChance`)
   or scores a miss for itself.
6. Whoever scored last serves the next point.

### Multiplayer

Same simulation, but on the server. P1's input drives `tryPlayerServe`
during PREPARE_SERVE and `playerHit` during INCOMING. P2's input drives
`acceptClientHit` during OUTGOING / BOT_RESOLVE. `getActivePlayer()`
tells each client whose turn it is so the UI can prompt correctly.

## Why server-authoritative

Two reasons:

- **Symmetry.** P1 and P2 see the exact same physics — neither has a
  local-simulation advantage. The screen code can be one class
  (`NetMatchScreen`) instead of host-vs-client branches.
- **Anti-cheat surface area.** Even on LAN, the server gets to validate
  inputs (it doesn't yet — see `playerHit` / `acceptClientHit` for the
  hooks). When this moves to the internet, that validation matters more.

## Rendering

3D pass: `ModelBatch` with a single `DirectionalLight` + ambient
attribute. Floor / table / net / ball are box / sphere instances drawn
in that order with depth test enabled.

2D HUD pass: depth disabled, `SpriteBatch` over the same frame. The
aim ring and bounce particles are placed via `camera.project(...)` so
they track 3D positions. The cursor projects onto the table-top plane
through `camera.unproject(...)` and an analytic ray-plane intersection.

## Good next refactors

- Server-side validation of HIT velocities (clamp into a plausible
  range, reject hits outside the legal click zone).
- Snapshot interpolation instead of extrapolation in the client (see
  `docs/plan.md` — needed for internet latency).
- Pull match-tuning constants out of `GameConfig` into a JSON profile
  so different difficulties can be loaded without recompiling.
- Add an event bus (`PaddleHit`, `TableBounce`, `PointScored`) so
  audio, particles, and HUD are decoupled from the simulation.
