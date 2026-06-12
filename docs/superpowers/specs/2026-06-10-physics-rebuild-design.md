# Physics Rebuild — Design

**Date:** 2026-06-10
**Status:** Approved (brainstorm with Pedro, all sections approved)
**Source paper:** Lin, Yu & Huang, *Ball Tracking and Trajectory Prediction for Table-Tennis Robots*, Sensors 2020, 20, 333.

## Goal

Rebuild the ball physics of the game around three ideas:

1. **Off-center hits matter** — where the player clicks on the ball decides
   aim, pace, *and spin*, and spin has real consequences (curved flight,
   different bounces).
2. **Full 3D dynamics** — gravity, air drag, and the Magnus effect act on all
   axes; bounces couple spin and horizontal velocity; the net is a real
   collider.
3. **Paper-grounded constants** — the simulation runs internally in real SI
   units using the paper's measured values, with one `timeScale` knob for
   game feel.

Out of scope: spin-dependent visuals (ball rotation, trails), reconnect/
netcode beyond one packet field, new items, renderer/camera changes.

## Decisions made during brainstorm

| Question | Decision |
|---|---|
| What does "off-center" mean? | Keep click-to-hit input; richer mapping (aim + power + spin). No paddle object, no input/protocol changes. |
| Realism vs feel | Simulate in real SI units (paper constants verbatim), convert via `UNITS_PER_METER`, expose a global `timeScale` (slow-motion, default ≈ today's pace). |
| Bot | Prediction-based (paper's robot concept): forward-simulate, Gaussian aim error, geometric hit/whiff. Replaces dice-roll + freeze hack. |
| Net | Real swept collider, play on (no "let" rule). |
| Architecture | New `sim/.../world/physics/` package; `MatchWorld3D` keeps rules, delegates motion. No physics engine dependency. |
| Vertical click mapping | Realistic: click top half = topspin, bottom half = backspin. (Flips the old "click high = higher arc" intuition.) |

## What the paper contributes

- **Physical model skeleton** (paper §4.2): gravity + quadratic air drag +
  restitution bounce, integrated with simple Euler steps (paper used 5 ms).
  Constants: Cd = 0.5, ρ = 1.29 kg/m³, A = 1.3×10⁻³ m², m = 2.7×10⁻³ kg
  → drag factor k_d = Cd·ρ·A/2m ≈ **0.155 m⁻¹**.
- **Restitution** measured over 330 real trajectories: **e ≈ 0.92**
  (the current game uses 0.7).
- **Strike-point prediction** as the bot's core problem, with real error
  stats (mean 37–58 mm, σ 19–30 mm) → template for bot difficulty as
  Gaussian aim error.
- The paper names **Magnus effect and table friction** as the pieces its
  physical model lacked — those come from standard aerodynamics here.

## Current state (what gets replaced)

- `MatchWorld3D` (sim): gravity-only Euler integration **duplicated three
  times** (`updateIncoming`, `updateOutgoing`, `updateBotResolve`); bounce =
  `vy ← −0.7·vy` with x/z untouched; net = pass/fail check at the z=0
  crossing frame; BOT mode freezes the ball horizontally so the bot can
  "settle" it; bot success = dice roll (`computeBotReturnChance`).
- `HitVelocity` (sim): click offset on a 3.5×-padded sphere → hard-coded
  return velocity `(ndx·3.2, 5+ndy·2, ±(7.5+power·2)·mult)`; incoming
  velocity discarded; no spin anywhere.
- `NetMatchScreen` (core): extrapolates between 30 Hz STATE snapshots with
  gravity-only `extrapolateBallY`.
- Serves: fixed velocities `(0, 5, ±10·mult)` regardless of click position.

## Section 1 — Flight, bounce, net

**State.** Ball = position, velocity, **spin ω** (3D angular velocity).
Spin is the new state variable.

**Units.** The integrator works in real SI internally. World units convert
at the boundary: `UNITS_PER_METER ≈ 5.11` (14-unit table = 2.74 m real).
`timeScale` multiplies the `dt` fed to the integrator — i.e. slow motion,
not weak gravity, so trajectory *shapes* are always physically correct and
only playback speed changes. Default `timeScale` calibrated so a flat drive
crosses the table in roughly today's time (≈ 1.5 s); `1.0` = real tempo.

**Forces** (semi-implicit Euler, fixed 240 Hz substeps, remainder
accumulated; deterministic and frame-rate independent):

- Gravity: 9.81 m/s².
- Drag: `a = −k_d·|v|·v`, k_d ≈ 0.155 m⁻¹ (paper constants).
- Magnus: `a = k_m·(ω × v)`, k_m ≈ 6×10⁻³ (standard sphere model, tunable;
  ~0.6 g lateral at 100 rad/s and 10 m/s).
- Spin decay in flight: exponential, τ ≈ 4 s; larger cut at each contact.

**Bounce** (impulse contact on the table plane):

- Vertical: `vy ← −e·vy`, e = 0.92 (paper).
- Horizontal: friction impulse opposes contact-point slip
  (slip = horizontal velocity + r·ω at the contact), updating both
  horizontal velocity and spin with sphere inertia (2/5·m·r²).
  Net effect: topspin kicks forward, backspin checks/dies, sidespin
  deflects laterally. Spin magnitude drops at each bounce.

**Net.** Thin box collider at z = 0 (table width, net height), tested with
the swept segment prev→current position (no tunneling at clamped max
speed). Contact kills 80–90 % of forward speed with small random jitter;
ball dribbles over or falls back, and the *existing* landing/out rules
score the result. No "let" rule. The old crossing-frame fault check is
deleted. Floor and out-of-bounds rules unchanged.

## Section 2 — PaddleContact (off-center mapping)

Hit test unchanged: ray vs sphere, 3.5× padding, scaled by duelist
`targetScale` and wide/tiny-paddle items. From the contact point,
normalized offsets `(ndx, ndy) ∈ [−1,1]`:

- **Aim**: lateral target ∝ ndx (as today, retuned for the new flight).
- **Sidespin**: ω_vertical ∝ ndx, signed so the ball *continues* curving
  in the aimed direction (hook). Edge clicks = risky curve shots.
- **Top/backspin**: ω_lateral ∝ ndy with **top half = topspin**
  (flatter, dips, kicks forward on bounce), **bottom half = backspin**
  (lofted, floats, dies on bounce). Reads as "where the paddle brushes
  the ball".
- **Pace**: `v_forward = basePace·(1 + 0.25·|offset|) + 0.35·|v_in|` —
  incoming speed partially carries through (today it is discarded).
  `returnPower` stat and the rally pace ramp multiply `basePace`.
- **Spin transfer**: `ω_out += −0.3·ω_in` — returning heavy topspin with a
  lazy center click pops the return up/long unless compensated.
- **Clamps**: hard caps on outgoing speed and spin applied *after* all
  multipliers; doubles as validation for the legacy `HIT` packet
  (replaces `HitVelocity.sanitizeNetworkReturn`).

**Serves** use the same mapping: the serve click's offset aims and spins
the serve inside a clamped legal envelope (today serves are fixed
velocity). Applies to P1, P2, and bot serves alike.

**The bot returns through this same function** with its erred contact
offset, so bot mishits produce physical shanks with spin.

All gain constants (`basePace`, carry 0.35, transfer −0.3, spin gains,
clamps) live in `PhysicsConfig` and are tuning starting points, not
contracts — the paddle property test (Section 4) is the acceptance bar.

## Section 3 — Components and data flow

New package `sim/src/main/java/io/github/some_example_name/world/physics/`
(plain math; libGDX `Vector3`/`MathUtils` only):

| Class | Responsibility |
|---|---|
| `PhysicsConfig` | All constants in SI + `UNITS_PER_METER` + `timeScale` + paddle gains + clamps. Carried by `MatchConfig` (like fighter config). |
| `BallState` | pos, vel, spin; copy/set; pooled scratch instances for prediction. |
| `BallPhysics` | The single integrator: `step(state, dt)` → substeps, forces, swept contacts (table, net, floor). Emits `ContactEvent` (TABLE_BOUNCE + side, NET_HIT, OUT_OF_PLAY, NONE); rules stay with the caller. Stateless. |
| `PaddleContact` | Section 2 mapping; `clamp()` validates legacy HIT. Replaces `HitVelocity` (deleted). |
| `BotPlanner` | `BotProfile { aimSigma, reactionDelay, paceAggression }`. On player hit: copy state → forward-simulate to the bot's legal hitting window → sample Gaussian-erred contact offset → geometric hit/whiff → schedule return. Replaces `computeBotReturnChance`, `pendingBotReturnChance`, `lastClickAccuracy`, freeze hack. |

**MatchWorld3D**: the three duplicated integrate+bounce blocks collapse
into one `stepBall(delta)` + per-phase rules switch. Unchanged behavior:
phase enum and transitions, serve order, 2-bounce rule, fly collisions
(swept, as now), items, lives, status text, PvP timeouts. The
`approachDuration` decay trio is reinterpreted as a growing rally pace
multiplier into `PaddleContact` (config keys keep working). In BOT mode
the ball keeps moving during `BOT_RESOLVE` (freeze removed); the
`BotPlanner`'s scheduled return or whiff resolves the phase.

**Networking**: STATE gains the spin vector (+12 bytes). `NetMatchScreen`
replaces `extrapolateBallY` with shared `BallPhysics.step` on a scratch
state — client curves match the server exactly between snapshots.
`CLICK`, `ServerPickRay`, anti-cheat path: untouched. Same-build LAN
assumption as today (no wire-format versioning needed).

**Events**: `ContactEvent`s map 1:1 onto the existing
paddle-hit/table-bounce audio + particle flags; `NET_HIT` reuses the
bounce SFX initially.

**Renderer**: no required changes.

**Item hooks** (behavior preserved):
- SLOW_MO / FAST_SERVE → `incomingSpeedMultiplier` now scales serve/return
  pace in `PaddleContact`.
- WIDE_PADDLE / TINY_PADDLE → hit-test radius multiplier (as today).
- PATCH_KIT, STEAL, PUNCH, FLY_BAIT, COIN_FLIP → untouched.

## Section 4 — Testing, calibration, migration

**Unit tests** (`sim/src/test`, headless):

- Flight: zero-spin matches closed-form ballistics within tolerance; drag
  strictly shortens range; Magnus curves symmetrically with spin sign;
  `timeScale` preserves the path (same landing point, different duration).
- Bounce: rebound height ratio ≈ e²; topspin gains forward speed across a
  bounce, backspin loses, sidespin deflects; spin decays at contact.
- Net: swept detection at clamped max speed (no tunneling); dribble-over
  and fall-back outcomes both reachable.
- Paddle (property test, doubles as tuning harness): across a grid of
  click offsets × incoming states, center-ish clicks land on the
  opponent's half ≥ 95 %; spin signs match offsets; pace monotone in
  incoming speed; clamps hold under max item stacks.
- Bot calibration: Monte-Carlo 1,000 rallies at default profile → return
  rate within ±5 % of 0.73; harder profiles strictly harder.
- Determinism: same seed + same clicks → bit-identical trajectories.

**Calibration order**: `timeScale` (≈ today's crossing time) → paddle
`basePace`/arc (property test green) → bot `aimSigma` (Monte-Carlo green).

**Migration steps** (each compiles, tests pass, game playable):

1. `physics/` package + full test suite (no callers).
2. `MatchWorld3D` flight/bounce swap — single-player feel check.
3. `PaddleContact` for returns + serves; delete `HitVelocity`.
4. Net collider; delete crossing-frame fault check.
5. `BotPlanner`; delete freeze hack + chance formula; calibrate.
6. STATE spin field + client extrapolation swap; PvP LAN smoke test.
7. Item/config mapping audit; update `docs/architecture.md`.

**Risks**:
- Feel regression → timeScale calibration + play-testing between steps 2–5.
- Bot balance shift → pinned by the Monte-Carlo test.
- SLOW_MO interacting oddly with pace carry → audited explicitly in step 7.
