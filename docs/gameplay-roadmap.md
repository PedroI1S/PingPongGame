# Gameplay Roadmap

## What the game is right now

A 3D first-person ping-pong duel. You stand at one end of the table.
Balls come at you. You click the ball with your mouse to return it.
First to drain the other side's lives wins.

Two modes:

- **Single-player** — vs a bot that runs in `MatchWorld3D` locally.
- **LAN multiplayer** — one player hosts (game spins up an embedded
  server in-process), the other joins by typing a 7-character room
  code.

## Current loop

1. Whoever scored last serves the next ball.
2. Ball flies across the net toward the receiver.
3. Receiver clicks the ball before it goes past their end of the table.
4. Hit point on the ball maps to lateral spin + lift + power.
5. Ball returns; if it clears the net and lands on the other side, it's
   a legal return.
6. Otherwise (net, out of bounds, double bounce, missed click) the
   receiver loses a life.
7. Repeat until one side hits zero lives.

## What's already in

### Click → return mapping

The hit point relative to the ball center drives:

- horizontal spin (`vx = ndx * 3.2f`)
- lift (`vy = 5 + ndy * 2`)
- forward velocity scales with how far from center the click landed
  (more power, but easier to send out of bounds)

### Camera / scene

3D scene built from `ModelBuilder` primitives — table, net, ball,
floor as box / sphere geometry with diffuse colors and a directional
light. No model files needed. P1's camera sits at +z, P2's at −z; the
perspective handles the left/right mirror automatically.

### Networking

- Server-authoritative: physics runs on the host's embedded
  `GameServer`, both clients are pure drawing + input
- Binary protocol over TCP, STATE broadcast at 30 Hz
- Client-side dead-reckoning between snapshots using gravity

### Loser-serves rule

`MatchWorld3D` tracks who lost the last point and prepares the serve
for the other side.

## Best next gameplay steps

### Step 1 — In-match item pickups

The pre-match loadout system was removed. Items will come back as
**pickups during play**:

- Items spawn on the table at random intervals.
- The player whose side they spawn on can sweep over them with a click
  to pick up.
- A picked-up item slots into a per-player effect stack: bigger click
  zone for next return, slow-mo for next incoming, extra life, etc.
- Items are visible to both players (no hidden bot pick).

This keeps items from being a "one-time pick before the match starts"
choice and turns them into a constant in-match decision: do I focus on
the ball, or break tempo to grab the powerup?

### Step 2 — Online multiplayer

Today's networking is LAN-only because the room code is the host's
local IPv4. Going to the internet means:

- NAT traversal (most home users are behind a router).
- A way for friends to find each other without typing IPs.

The realistic path is Steam — `SteamNetworkingSockets` for transport
and Steam Lobbies for friend invites. The wire format and the
server-authoritative architecture stay the same. Only `GameConnection`
is rewritten as a thin wrapper over Steam's API.

See `docs/plan.md` for the full breakdown.

### Step 3 — Latency polish

Once the game runs over the internet, the current client behaviour
(extrapolate from the last STATE) starts to look jittery. Fixes:

- **Interpolate, don't extrapolate.** Buffer ~100 ms of state and
  render the past — smooth and accurate, slightly delayed.
- **Client-side prediction for your own hit.** Show the return
  immediately on click; reconcile if the server rejects it.
- **Server-side validation of HIT.** Server clamps the velocity into a
  plausible range, rejects hits when the ball isn't in the legal click
  zone. Defeats trivial cheating.

### Step 4 — Bot variety

Currently the bot is a single AI tuned by a base return-chance
constant. Better bots feel like opponents, not sliders:

- Recognizable shot types: fast straight, slow fakeout, cross-court,
  wide angle.
- Difficulty profiles (reaction time, return chance, shot variety) so
  Easy / Hard feel different beyond just numbers.
- Tells / fakeouts so the bot has rhythm a player can learn to read.

### Step 5 — Feedback layer

The simulation is tight; the feedback isn't yet. Things that would
sell hits and misses harder:

- Camera shake on table bounce
- Glow / smear trail behind the ball
- Hit-specific SFX (clean centre vs glancing edge)
- Score-flash overlay when a point ends
- Crowd ambience or court reverb on the music

## Suggested future systems

- `ServePattern` — declarative bot serve recipes
- `ItemSpawner` — picks up locations and triggers in-match items
- `EffectStack` — per-player active modifiers with timeouts
- `MatchEventBus` — `PaddleHit`, `TableBounce`, `PointScored`,
  `MatchOver` so audio, particles, and HUD don't poll
- `DifficultyProfile` — JSON tuning for bot reaction window, return
  chance, shot variety
- `PostMatchSummary` — round-by-round stats screen

## Design rule of thumb

The game is one sentence:

> A ping-pong ball comes at you, and you must click it in time.

Every new feature should make that sentence feel sharper. If a feature
doesn't, it shouldn't ship.
