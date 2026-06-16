# Match Polish & Rules Pass

**Date:** 2026-06-15
**Status:** Approved (brainstorm with Pedro, full scope confirmed)
**Depends on:** the physics rebuild (`2026-06-10-physics-rebuild-design.md`) and item system (`2026-05-22-items-design.md`).

## Goal

A focused punch-list pass on the networked match experience: make HUD text
legible over the retro filter, fix the broken shot-validation rules (volley /
double-bounce should cost the point), surface what each item does during
selection, give the item phase a real "end selection" button, raise overall
volume, and add a toggleable event log so the player can see *why* points were
won and lost.

Six independent items. They share one connective thread — items 1, 3, and 6
all depend on drawing UI text **outside** the retro post-process pass so it
stays crisp.

## Decisions made during brainstorm

| Question | Decision |
|---|---|
| What does "make descriptions readable once an event happens" mean? | **Stay legible through effects** — show each item's description on hover during selection, and keep that text sharp even while a disruptive effect (punch blur, retro shader) is active. Same readability requirement as item 1, applied to descriptions. Not a discovery/reveal mechanic. |
| What happens on a volley (return before the ball bounces on your side)? | **Lose the point** — real table-tennis rule. Volley = fault, double bounce = fault. |
| How does the log learn *why* a point was lost? | A single server-authoritative `LOG_EVENT` channel. The server already knows the reason; the client only sees a lives delta. One channel, no client-side inference. |
| Does the new READY button replace clicking empty space? | Yes — the empty-space shortcut is removed; the button is the only way to ready up. |
| Audio levels | Raise defaults + base playback gains modestly; sliders still scale everything. Starting numbers, tunable. |

---

## 1. Readable text in front of the retro shader

**Problem.** `NetMatchScreen.render()` and `TutorialScreen.render()` draw the
entire frame — 3D scene *and* the 2D SpriteBatch HUD/overlays — between
`postProcess.begin()` and `postProcess.endAndBlit()`. The retro fragment
shader therefore pixelates and palette-quantizes the text, and the punch blur
smears/desaturates it. (Only these two screens use the post-process; the menus
do not, so their text is already crisp.)

**Approach.** Split each gameplay screen's render into a *world* pass (inside
the FBO) and a *UI* pass (after the blit):

```
postProcess.begin();
    arena.render3DScene(...);          // 3D
    itemPhaseRenderer.render(...);     // 3D item shelf
    // world-space 2D that SHOULD stay stylized:
    arena.drawCursorMarker(...);
    arena.drawParticles(...);
postProcess.endAndBlit();
// UI pass — crisp, full-colour, untouched by shader or punch blur:
batch.begin();
    drawHud(...);
    drawEventLog(...);                 // item 6
    drawItemDescription(...);          // item 3
    drawReadyButton(...);              // item 4
    drawRound/outcome/disconnect overlays(...);
batch.end();
```

The cursor aim-ring and impact-particle glows stay **inside** the FBO — they
are world-space stylized effects and should read as part of the retro scene.
Everything text-shaped moves to the post-blit UI pass.

This one change is also what makes items 3 and 6 legible "through effects":
text drawn after the blit is never touched by the filter or the punch blur.

**Alternatives rejected.** Shader-side region masking to skip quantization on
text (fragile, complicates the frag shader); a second text-only FBO (extra
plumbing). Drawing text after the blit is the simplest correct option.

**Files:** `NetMatchScreen.java`, `TutorialScreen.java`. The post-blit batch
uses `context.getViewport().getCamera().combined` (HUD coordinates), same as
today's HUD pass.

---

## 2. Shot validation — enforce exactly one bounce before a return

**Rule (symmetric for both sides):** an incoming ball must bounce **exactly
once on the returner's own side** before they may legally return it.

- **0 bounces, returner strikes the ball → volley fault → returner loses the point.**
- **1 bounce, returner strikes → legal return.**
- **2 bounces on the returner's side → double-bounce fault → returner loses the point.**

**Current gaps in `MatchWorld3D`:**

- `tryHitBall(pickRay)` (P1) accepts the hit whenever the click intersects the
  ball sphere in `INCOMING`, regardless of `bouncesOnPlayerSide`. → P1 can volley.
- P2's side has no bounce counter. `updateOutgoing` sends the ball to
  `BOT_RESOLVE` on its **first** bounce on P2's side but never counts a second,
  and `isClientCanHit()` returns true for `phase == OUTGOING && z < 0` (mid-air,
  pre-bounce). → in PvP, P2 can volley (OUTGOING) and can double-bounce
  (BOT_RESOLVE) with no penalty.
- The bot is **unaffected** — it swings only via the planner once the ball is in
  `BOT_RESOLVE` (after one bounce), so it never volleys.

**Changes:**

1. **P1 volley.** In `tryHitBall`, after confirming the click intersects the
   ball: if `bouncesOnPlayerSide == 0`, call `handlePlayerMiss()` (with a
   `VOLLEY` log reason — see item 6) instead of executing a legal return.
   `bouncesOnPlayerSide >= 1` → legal return as today.
2. **P1 double bounce.** Already handled (`updateIncoming`: `bouncesOnPlayerSide
   >= 2 → handlePlayerMiss`). Keep it; tag it with the `DOUBLE_BOUNCE` reason.
3. **P2 bounce counter (PvP).** Add `bouncesOnClientSide`, reset alongside
   `bouncesOnPlayerSide` at every serve/return. In `updateOutgoing`, the first
   legal bounce on P2's side sets it to 1 and enters `BOT_RESOLVE`. In
   `updateBotResolve` (PvP branch), a further bounce on P2's side → 2 →
   `clientMiss()` with `DOUBLE_BOUNCE`.
4. **P2 volley (PvP).** `isClientCanHit()` returns true only in `BOT_RESOLVE`
   (after exactly one bounce), not in `OUTGOING`. A P2 click that strikes the
   ball during `OUTGOING` (pre-bounce) → `clientMiss()` with `VOLLEY`.

Out-of-bounds / long / timeout outcomes already exist; this item only adds the
volley + double-bounce enforcement and the reason tags.

**Tests (TDD, against the existing `MatchWorld3D…Test` harness):**

- P1 volley (strike before first bounce) → player loses a life, item phase begins.
- P1 single-bounce return → still legal (regression guard).
- P1 double bounce (no click) → player loses a life. *(existing behaviour — lock it.)*
- PvP P2 volley in OUTGOING → P2 loses a life.
- PvP P2 single-bounce return in BOT_RESOLVE → legal.
- PvP P2 double bounce in BOT_RESOLVE → P2 loses a life.
- BOT mode: a normal bot rally still completes (regression — bot never volleys).

---

## 3. Item descriptions on hover, legible through effects

**Copy.** Each of the 9 `ItemType`s gets a short display name + one-line
description derived from what `applyItem` actually does:

| Item | Name | Description |
|---|---|---|
| PATCH_KIT | Patch Kit | Restores one of your lives. |
| WIDE_PADDLE | Wide Paddle | Your hit area is larger this rally. |
| SLOW_MO | Slow-Mo | The ball comes at you slower this rally. |
| STEAL | Steal | Take a random item from your opponent. |
| FAST_SERVE | Fast Serve | The ball comes at your opponent faster. |
| TINY_PADDLE | Tiny Paddle | Shrinks your opponent's hit area. |
| PUNCH | Punch | Blurs your opponent's view for 10 seconds. |
| FLY_BAIT | Fly Bait | Spawns flies on your opponent's side — ball hits cost a point. |
| COIN_FLIP | Coin Flip | 50/50 — someone loses a life. |

`ItemType` stays logic-only (sim module, pure). Display copy lives in a new
core-side holder, `render/ItemCopy.java`: `String name(ItemType)` /
`String description(ItemType)` (a static `EnumMap` or switch). This keeps the
sim module free of presentation strings.

**Presentation.** The item shelf already tracks per-entry `hovered`. When the
local player hovers one of their items during the item phase, a small panel
shows that item's name + description, centred horizontally and sitting just
above the END SELECTION button (item 4) so the two never overlap. Drawn in the
post-blit UI pass (item 1) → sharp through the punch blur and shader. No hover →
no panel. Body font; name brighter, desc dimmed.

`ItemPhaseRenderer` needs a way to report the currently-hovered local item type
(e.g. `ItemType hoveredType(int playerNumber)`); `NetMatchScreen` reads it each
frame and draws the panel.

**Files:** new `render/ItemCopy.java`; `ItemPhaseRenderer.java` (expose hovered
type); `NetMatchScreen.java` (draw panel).

---

## 4. Explicit "END SELECTION" button both players press

**The logic already exists.** `MatchWorld3D.playerReady` requires both
`p1Ready && p2Ready`; in BOT mode the server auto-readies P2
(`GameServer` offers `w.playerReady(2)` on entering ITEM_PHASE). Today the
client readies by clicking empty space, with only a text "[ READY ]" label —
error-prone (an off-target item click readies you by accident).

**Change.** Replace the empty-space shortcut with a real on-screen button
(reuse `ui/Button.java` + `ui/UIDraw.java`), bottom-centre, labelled
**END SELECTION**. Clicking it sends `sendItemReady()` and locks the button to
**WAITING FOR OPPONENT…**; when both are ready the phase ends. In single-player
the bot is already ready, so your press ends it immediately. Picking items is
unchanged; only the empty-space ready path is removed (a click that misses both
items and the button does nothing).

The button hit-test runs in `handleClick`'s item-phase branch, in HUD/screen
coordinates (the button is 2D UI), checked before the 3D item ray-pick so the
two never conflict (they occupy different screen regions).

**Files:** `NetMatchScreen.java` (button instance, hit-test, render in UI pass).

---

## 5. Make it louder

Raise defaults and base playback gains modestly; the master/music/sfx sliders
still scale on top, so the player can tune from there.

- `GameSettings` default master volume **80 → 100**. (music 60, sfx 90 unchanged.)
- `NetMatchScreen` background music base mix **0.25 → 0.35**.
- SFX base multipliers, ~+30–40%:
  - paddle hit `0.7 → 0.95`
  - table hit `0.6 → 0.85`
  - life lost `0.8 → 1.0` (self), `0.45 → 0.6` (opponent)
  - item use `0.7 → 0.9`
  - ui click `0.6 → 0.8`, ui hover `0.4 → 0.5`
  - fly buzz loop `0.25 → 0.35`

Numbers are a starting point; final values get a quick listen-test.

**Files:** `GameSettings.java` (default), `NetMatchScreen.java` (play-call gains).

---

## 6. Event log (upper-left, toggleable)

**A rolling feed of recent gameplay events in the upper-left corner, fading
after a few seconds, with an on/off toggle in settings (default on).**

**Channel.** One server-authoritative `LOG_EVENT` packet —
`{byte eventCode, byte subjectPlayer}`. The server emits it at each loggable
moment in `MatchWorld3D`; each client maps `code + subject` to a line, phrasing
the subject from its own perspective (`subject == myPlayerNumber` → "You", else
"Opponent"). Single source of truth; no client-side inference of *why* a life
changed.

- `PacketType`: new `LOG_EVENT = 27` (+ an event-code constant block).
- `GameConnection`: `sendLogEvent(code, subject)`, reader case, listener
  `onLogEvent(int code, int subject)`.
- `GameServer`: forward `MatchWorld3D`'s queued log events to both clients each
  tick (same consume-event pattern as `consumeItemUsedEvent`, etc.).
- `MatchWorld3D`: enqueue a log event at each scoring/effect site.

**Event catalog (initial codes):**

- Point losses: `VOLLEY`, `DOUBLE_BOUNCE`, `OUT_OF_BOUNDS`, `TIMEOUT` (serve/return
  too slow), `FLY_HIT`.
- Items (subject = user): one per `ItemType`, e.g. `Opponent used Wide Paddle`.
  Coin flip resolves to `COIN_FLIP_LOSS` with subject = the loser. `FLY_BAIT` →
  `Flies freed on your side` (subject = side owner). `PATCH_KIT` → life restored.
  `STEAL` → item taken.
- Round: `ROUND_WON` (subject = winner).

Item-use and fly events already have gameplay packets (`ITEM_USED`,
`FLY_SPAWN`); those continue to drive visuals/audio. `LOG_EVENT` is the log's
own clean feed so phrasing lives in one place.

**Rendering.** Upper-left, newest line on top, max ~6 lines, each fades out
~6 s after arrival (alpha ramp in the last ~1 s). A small ring/list of
`{text, age}` in `NetMatchScreen`, ticked in `update`/`render`. Body font,
dimmed; drawn in the post-blit UI pass (item 1) so it stays crisp.

**Config.** New `eventLogEnabled` setting in `GameSettings` (persisted via a
new prefs key, default `true`) with getter/setter following the existing
`showFpsCounter` / `screenShake` pattern. Toggle added to the GAME tab of
`ConfigScreen`, beside the FPS and screen-shake toggles. The log only renders
when enabled.

**Scope.** The real match (`NetMatchScreen`) only; the tutorial can adopt the
renderer later.

**Files:** `PacketType.java`, `GameConnection.java`, `GameServer.java`,
`MatchWorld3D.java`, `GameSettings.java`, `ConfigScreen.java`,
`NetMatchScreen.java`.

---

## Out of scope

- The retro shader internals (no change to `retro.frag`).
- Menu screens' text (already crisp — not post-processed).
- A coin-flip / punch *visual* flash (only the log line + existing blur).
- Tutorial event log and tutorial volley enforcement (match-scoped for now).
- Rebinding or per-sound volume sliders (only a master-default bump + base gains).

## Testing summary

- **Item 2** is the load-bearing correctness change → TDD against the headless
  `MatchWorld3D…Test` harness (cases listed in §2).
- **Item 6** protocol round-trip: a small test that `sendLogEvent` →
  `onLogEvent` carries code + subject intact (mirrors existing connection
  coverage if present).
- Items 1, 3, 4, 5 are rendering/tuning/UI — verified by running the match
  (build + launch), not unit tests.
