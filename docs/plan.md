# Forward plan

The 3D physics + LAN-multiplayer foundation is in. This doc tracks
what's queued next, in rough priority order.

## What's implemented

### Client / server split

Modules: **`sim`** (shared physics + protocol + headless server), **`server`**
(fat-jar launcher), **`core`** (client), **`lwjgl3`** (desktop launcher).

| Client action | What happens |
|---|---|
| **VS BOT** | `MatchConnectScreen(BOT, spawnLocal=true)` boots an `InProcessServer` on `127.0.0.1:7777` on a daemon thread, then opens a `GameConnection` and sends `JOIN(MODE_BOT)`. Server's bot AI plays P2. |
| **HOST** | `MultiplayerLobbyScreen.startHosting()` boots an `InProcessServer` on `0.0.0.0:7777`, connects locally with `JOIN(MODE_PVP)`, waits for a second player. |
| **JOIN** | Decodes the room code to an IP, opens a `GameConnection`, sends `JOIN(MODE_PVP)`. Server pairs it with the waiting host. |

No JAR build is required for VS BOT / HOST — the server lives in the same
JVM. Override with `PINGPONG_SERVER_HOST` if you want clients to connect
to an external server (e.g., a Docker / VPS deploy of `:server:run`).

### Click-driven protocol

Clients never compute physics. On a mouse click they send
`CLICK { screenX, screenY, viewportW, viewportH }`. The server rebuilds
the pick ray with `ServerPickRay.fromScreen(playerNumber, …)` — the
camera matches `MatchArenaRenderer` exactly — and feeds it into
`MatchWorld3D.handlePlayerClick / handleOpponentClick`.

### Settings persistence

`GameSettings` saves to libGDX `Preferences`:

- Audio: Master / Music / SFX / UI volumes. Music gain applied on
  `NetMatchScreen.show()`; SFX gain applied per playback.
- Graphics: fullscreen on/off, window-res preset, retro filter on/off
  + intensity preset.
- Game: Show FPS counter (HUD overlay), Screen shake on/off.

## What's queued

### 1. In-match item pickups

The pre-match loadout was removed. Items return as on-table pickups
mid-match.

### Wire format

A new `ItemSpawn` packet broadcast by the server alongside STATE:
spawn id, position, type, ttl. A `ItemPickedUp` packet acknowledged by
the server when a player's click hit-tests the item.

### Server-side

- `ItemSpawner` ticks alongside `MatchWorld3D`, picks a random legal
  spot on the table at random intervals.
- `ItemPickedUp` is authoritative — first valid client hit wins.
- Active effects live in `DuelistState` (per-player stack with
  timeouts).

### Client-side

- Render items as small colored cubes / spheres on the table.
- Click during INCOMING / OUTGOING already does ray-sphere against the
  ball — extend to also test against active items.
- HUD shows the local player's active effects with countdowns.

### First three items to ship

- **Patch kit** — +1 life on pickup
- **Slow-mo** — next incoming ball arrives ~30% slower
- **Wide click** — next return uses a 1.5× hit radius

Once the loop feels good, add fakeout / sabotage items (forces a
specific shot type from the opponent, etc.).

### 2. Online multiplayer (Steam)

LAN-only today because the room code is the host's local IPv4. Going
to the internet means dealing with NAT and friend discovery. Steamworks
gives both for free.

### Sequencing

1. **Steam Direct** ($100 one-time fee, app id assigned by Valve).
2. **Pick a Steamworks Java binding.** `steamworks4j` (code-disaster)
   is the well-maintained option. Bundle native libs per platform.
3. **Init Steam at startup.** `SteamAPI_Init()`; if it fails, fall back
   to LAN-only mode (or refuse to start in non-dev builds).
4. **Rewrite `GameConnection`** as a wrapper around
   `SteamNetworkingSockets`:
   - Replace `Socket` reader thread with `ReceiveMessagesOnConnection`
     polling.
   - Replace `out.write(...)` with `SendMessageToConnection(handle,
     bytes, k_nSteamNetworkingSend_Reliable)`.
5. **Rework `MultiplayerLobbyScreen`** for Steam:
   - HOST → `CreateLobby(k_ELobbyTypePublic, 2)` → wait for
     `LobbyCreated_t` → start `GameServer` bound to a Steam listen
     socket.
   - JOIN → either show a Steam lobby browser (`RequestLobbyList`) or
     accept friend invites only (simpler).
   - On `LobbyEnter_t`, connect to the host's SteamID.
6. **Handle invites.** Register
   `GameRichPresenceJoinRequested_t`; when a friend invites and the
   user accepts, Steam launches your game (or signals it if running)
   with a connect token; parse it and connect.

### What stays the same

`MatchWorld3D`, `GameServer`, the binary protocol, the symmetric
`NetMatchScreen`. Only the transport layer is swapped.

### 3. Latency / cheating polish

Needed once the game runs over the internet, not before.

### Snapshot interpolation

Today the client extrapolates from the last STATE using gravity. On
internet (30–150 ms RTT, occasional packet loss / reorder) this looks
jittery. Buffer ~100 ms of incoming snapshots and render the past —
smooth and accurate, at the cost of a slight delay.

### Client-side prediction

The local player's hit currently waits for the next STATE to confirm.
On a 100 ms link that's a noticeable lag. Predict locally, render the
return immediately, snap back if the server rejects it.

### Server-side validation

Today the server trusts whatever velocity the client sends in HIT. On
LAN that's fine. Online, a malicious client could send arbitrary
velocities. Mitigations:

- Clamp `vx`, `vy`, `vz` into a plausible range based on `MatchWorld3D`
  return constants.
- Reject HIT if the ball wasn't in the legal click zone for that
  player at the time the packet arrived (some grace for latency).
- Optionally have the client send a coarse claimed click position and
  the server validates the ray-sphere intersection itself.

Not full anti-cheat. Enough to defeat trivial scripting.

### Reconnect / resync

Today, if any packet sequence breaks the match dies. Add a session id
to every packet. If a client misses too long, the server holds the
session for ~10 seconds; reconnecting clients resume from the latest
STATE.

### 4. Bot variety

Currently `MatchWorld3D.computeBotReturnChance()` is a single tuned
value. Better:

- `ServePattern` declarative recipes (fast straight / slow fakeout /
  cross / wide).
- `DifficultyProfile` JSON: reaction window, return chance variance,
  shot-variety probability. Easy / Normal / Hard load different files.
- Tells: bot pauses or twitches before fakeout serves so a skilled
  player can read it.

### 5. Feedback layer

Some of this is already in (camera shake on bounces + lost lives, FPS
counter overlay, mute-via-master). Remaining:

- Glow trail behind the ball (already have a `glow` procedural texture
  — just wire up trail particles).
- Centre-hit vs edge-hit SFX variants.
- Score flash overlay when a point ends.
- Court ambience under the existing music track.
- Rebindable controls (the settings screen has the read-only list
  ready; needs a key-capture mode and `GameSettings` storage).

## Out of scope (for now)

- Mobile / touch port — viewport math is ready but click-to-return on
  a small touch screen probably needs a different mechanic.
- Spectator mode.
- Replay system.
- Tournaments / matchmaking ranking.
