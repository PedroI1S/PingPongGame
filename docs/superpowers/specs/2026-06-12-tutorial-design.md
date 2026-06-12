# Tutorial Design — Guided Drill Course

**Date:** 2026-06-12
**Status:** Approved (brainstorm with Pedro, all sections approved)
**Depends on:** the physics rebuild (`2026-06-10-physics-rebuild-design.md`), feel-verified 2026-06-12.

## Goal

Teach the spin mechanics hands-on: a local, offline, guided drill course where
**the zone is the lesson** — each step paints a goal on the table that is only
comfortably reachable with the technique being taught. One instruction line,
infinite free balls, no text walls. Ends with a winnable rally against a
gentle sparring bot.

## Decisions made during brainstorm

| Question | Decision |
|---|---|
| Where does it run? | Locally on the client — no server, no netcode. The tutorial drives `BallPhysics`/`PaddleContact` directly so it can slow time, pre-spin demo balls, and re-feed instantly. |
| Format | Ordered guided drill course with pass criteria; not a sandbox, no contextual-hints mode. |
| Scope | Seven steps: timing, aim, topspin, backspin, curve, serve, graduation rally. No items lesson (the item phase teaches itself); no spin-counter lesson. |
| Build approach | New `TutorialScreen` in `core` + pure-logic course engine in `sim` (headlessly testable). Drills are NOT wedged into `MatchWorld3D`. |

## The course

Shared frame: player at +z (same camera as a real match), drill header
("DRILL 3/7 — TOPSPIN"), one instruction line in the existing status-text
voice, progress pips (●●○). Attempts re-feed 0.8 s after resolving. Failing
costs nothing. ESC opens the existing pause menu (Resume / Leave).

| # | Drill | Feed | Goal (pass) | Success detection | Teaching aid |
|---|---|---|---|---|---|
| 1 | **Timing** — "Let it come." | Gentle straight balls from the far side, landing mid-half | 3 returns that land anywhere on the far half | contact happened at `ball.pos.z > 3.5` AND first bounce `bounceZ < 0`, no net | Amber "sweet strip" glows at z ∈ [3.5, 6.0]; early clicks get "Too early — let it cross the glow" |
| 2 | **Aim** — "Place it." | Same | 2 landings in each lit zone, alternating | bounce inside the lit `ZoneRect` | Teal zones x ∈ [−2.6,−0.8] and [0.8,2.6], z ∈ [−5.8,−1.2] |
| 3 | **Topspin** — "Roll over it." | First feed: slow-mo (timeScale ×0.5) pre-spun topspin demo, unclickable, WATCH tag. Then normal feeds | 3 returns landing in the deep band WITH topspin | `spin.x < −10` after contact (−z travel) AND bounce in zone | Deep band z ∈ [−6.5,−3.0]; the flat dip-and-kick of topspin makes deep landings natural |
| 4 | **Backspin** — "Slice under it." | Slow-mo backspin floater demo, then normal | 3 drop shots landing short WITH backspin | `spin.x > +10` AND bounce in zone | Short band z ∈ [−3.0,−0.6]; backspin's loft + dying bounce fits the short zone |
| 5 | **Curve** — "Bend it." | First feed: slow-mo sidespin demo (like drills 3-4). Then feeds offset to x = ±1.5 (alternating side, mirrored handedness) | 2 shots that pass around the pole and land behind it | bounce in zone AND `|spin.y| > 10` AND no pole contact | Pole at (0, table, −3), r = 0.45, h = 1.4 — blocks the straight line from the feed-side contact to the zone (x ∈ [−1.2,1.2], z ∈ [−6.2,−4.2]). Pole hit = thunk + "Clipped the pole — bend it wider" + re-feed. Ball-vs-pole uses the swept-segment test (same math as flies) |
| 6 | **Serve** — "Open the point." | Player serves (same click-aimed serve as the real game) | 1 serve into each zone | bounce inside the lit zone | Short-left zone x ∈ [−2.6,−0.6], z ∈ [−3.2,−0.8]; then deep-right x ∈ [0.6,2.6], z ∈ [−6.4,−4.0] |
| 7 | **Graduation** — "Beat the sparring bot." | Real local match | First to 3 points | match outcome | Local `MatchWorld3D` in BOT mode, lives 3 vs 3, gentle bot profile, item phase auto-skipped (both `playerReady`s fired immediately) |

Demo balls (drills 3–5 first feed): the tutorial sets `BallState.spin`
directly and halves its own `timeScale` so the player watches the curve/kick
in slow motion; any click during a demo shows "watch first — your ball is
next" and does nothing. Spin thresholds (±10) are deliberately forgiving
(~20% of a full-edge click).

On graduation win: "COURSE COMPLETE", persist the flag, return to menu.
On loss: offer retry of drill 7 only.

## Architecture

**`sim/src/main/java/io/github/some_example_name/tutorial/`** — pure course
logic, no graphics dependencies, fully headless-testable:
- `DrillCourse` — sequencing state machine: current drill, pips, advance,
  feed scheduling, demo/normal feed state. Consumes per-frame physics
  observations (`StepContacts`, OOB, net) and per-click contact results
  (the post-contact `BallState`); produces instruction/feedback strings,
  zone+pole geometry to draw, and feed commands.
- `Drill` definitions — instruction, feed recipe, zones, success predicate,
  required count, failure messages. Exact file split decided at plan time;
  geometry constants in one place so tuning is one-line.
- `ZoneRect` — x/z rectangle + contains test against bounce coordinates.
- Pole sweep check — segment-vs-vertical-cylinder, reusing the
  `distSqPointToSegment` approach.

**`core`:**
- `screen/TutorialScreen` — mirrors `NetMatchScreen`'s loop (arena render →
  retro post-process → HUD): owns its own `PhysicsConfig` (mutable
  `timeScale` for demos), `BallPhysics`, `BallState`, `StepContacts`,
  `DrillCourse`, and for drill 7 a local `MatchWorld3D`. Clicks →
  `arena.getCamera().getPickRay(...)` (identical camera to a real match) →
  `PaddleContact.returnFromRay` / serve path. Zones and pole drawn as
  translucent unlit boxes through the renderer's existing
  `getModelBatch()`/`getCamera()` accessors — `MatchArenaRenderer` itself
  unchanged. Sounds: paddle/table SFX from contacts, `ui_click` on success
  pips. Palette: teal zones, amber strip, coral pole.
- `Main.openTutorial()`; `MenuScreen` gains a `[ TUTORIAL ]` button with a
  small "start here" nudge drawn until the completed flag is set.
- `GameSettings`: persisted boolean `tutorialCompleted` (new prefs key).

**Sim API additions:** exactly one — `MatchWorld3D.getBotProfile()` exposing
the bot's mutable `BotPlanner.Profile` (house style: live mutable accessor)
so drill 7 can soften it (starting point: aimSigma 0.95, reactionDelay 0.8).
Lives use the existing `FighterConfig.addLives`.

**Not touched:** server, protocol, `MatchWorld3D` rules (beyond the
accessor), renderer, items.

## Testing

Headless, in `sim/src/test/.../tutorial/`:
- **Beatability tests (the important ones):** for every drill, a scripted
  ideal shot — built with the same `PaddleContact` call the player's click
  produces, from that drill's feed position — must satisfy the drill's
  success predicate under the shipped `PhysicsConfig` (e.g., the curve
  drill's edge-click sidespin return clears the pole and lands in-zone).
  These pin the tutorial against future physics retuning, exactly like the
  bot's Monte-Carlo calibration test pins difficulty.
- Course state machine: synthetic event sequences advance pips/drills and
  complete the course; failures don't advance; demo feeds don't count.
- Zone rect contains/edge cases; pole swept hit/miss.
- Spin predicates: a `returnFromRay` with ndy = +0.7 passes the topspin
  check and fails backspin's; mirrored for ndy = −0.7.
- Graduation bot: gentle profile returns strictly less than the default
  profile (comparative, seeded).

Manual: course playthrough feel, menu nudge, pause/leave, completion flag
persists across restarts.

## Risks

- **Curve drill difficulty** — it demands near-edge clicks by design. The
  geometry lives in named constants; widening the zone or shrinking the pole
  is a one-line tune, and the beatability test keeps it honest.
- **Physics retuning breaking drills** — covered by the beatability tests.
- **Menu layout shift** — third action button restacks `MenuScreen`; minor.
- **Graduation inherits real match rules** — by design (rules changes flow
  in automatically), noted so nobody is surprised.

## Out of scope

Items lesson, spin-counter lesson, sandbox/free-practice mode, partial
course progress persistence, spin visuals on the ball, localization.
