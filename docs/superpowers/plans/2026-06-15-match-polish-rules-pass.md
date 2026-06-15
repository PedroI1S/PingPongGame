# Match Polish & Rules Pass Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make match HUD text legible over the retro filter, enforce volley/double-bounce as point-losing faults, surface item descriptions on hover, give the item phase a real END SELECTION button, raise volume, and add a toggleable upper-left event log.

**Architecture:** All UI text moves to a 2D pass drawn *after* the retro post-process blit so it is never pixelated/blurred. Shot-validation rules are enforced server-side in the pure-logic `MatchWorld3D` (headlessly tested). A new server→client `LOG_EVENT` channel carries point-loss reasons; the client renders a fading log fed by that channel plus the existing `onItemUsed`/`onRoundOver` callbacks.

**Tech Stack:** Java 17/21, libGDX, Gradle (multi-module: `sim` = pure logic + netcode, `core` = client/render, `server` = headless host), JUnit 5.

**Spec:** `docs/superpowers/specs/2026-06-15-match-polish-rules-pass-design.md`

---

## Conventions used in every task

**Run the sim tests** (this repo logs `quiet` by default, and pipes hide gradle's exit code — use this exact form):

```bash
./gradlew :sim:test -Dorg.gradle.logging.level=lifecycle --console=plain > /tmp/g.log 2>&1; echo "exit=$?"; tail -8 /tmp/g.log
```

The echoed `exit=` is the truth (0 = pass). To run one class add `--tests "io.github.some_example_name.world.MatchWorld3DRulesTest"`. Gradle may need the Bash tool's `dangerouslyDisableSandbox: true` on macOS.

**Compile the client module** (no test source set in `core`):

```bash
./gradlew :core:compileJava -Dorg.gradle.logging.level=lifecycle --console=plain > /tmp/g.log 2>&1; echo "exit=$?"; tail -8 /tmp/g.log
```

**Coordinate facts:** HUD/world space is `GameConfig.WORLD_WIDTH`×`GameConfig.WORLD_HEIGHT` (1280×720), y-up. Table axes (sim): `+z` is P1's side, `-z` is P2's, net at `z=0`, `MatchWorld3D.TABLE_TOP_Y` is the tabletop. Default lives = 5 (`GameConfig.DEFAULT_LIVES`).

---

## Task 1: Split each match screen into a world pass (inside FBO) and a UI pass (after blit)

Text drawn between `postProcess.begin()` and `endAndBlit()` is pixelated by the retro shader and smeared by the punch blur. Move every text/overlay draw to a second `SpriteBatch` block *after* `endAndBlit()`; keep the world-space cursor ring and particle glows inside the FBO.

**Files:**
- Modify: `core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java` (`render`)
- Modify: `core/src/main/java/io/github/some_example_name/screen/TutorialScreen.java` (`render`)

- [ ] **Step 1: Restructure `NetMatchScreen.render`**

Replace the body from `context.getPostProcess().begin();` through `context.getPostProcess().endAndBlit();` (currently lines ~191–243) with:

```java
        context.getPostProcess().begin();
        arena.render3DScene(ballVisible);

        // Render item cubes in 3D (uses a fresh modelBatch begin/end)
        if (inItemPhase && itemPhaseRenderer != null) {
            com.badlogic.gdx.math.collision.Ray hoverRay =
                arena.getCamera().getPickRay(netInput.lastMouseX, netInput.lastMouseY);
            if (itemPhaseRenderer.updateHover(hoverRay, 1)) {
                context.getAssets().getUiHoverSfx().play(getSfxGain() * 0.4f);
            }
            itemPhaseRenderer.update(delta);
            arena.getModelBatch().begin(arena.getCamera());
            itemPhaseRenderer.render(arena.getModelBatch(), arena.getEnvironment());
            arena.getModelBatch().end();
        }

        context.getViewport().apply(true);

        // Punch blur must be set before endAndBlit() reads the uniform.
        if (punchTimer > 0f) {
            punchTimer -= delta;
            context.getPostProcess().setPunchBlur(punchTimer / 10f);
        } else {
            context.getPostProcess().setPunchBlur(0f);
        }

        SpriteBatch batch = context.getBatch();
        batch.setProjectionMatrix(context.getViewport().getCamera().combined);
        // World-space 2D that SHOULD stay stylized — drawn inside the FBO.
        batch.begin();
        arena.drawCursorMarker(batch, context.getAssets().getProceduralAssets().getAimRing());
        arena.drawParticles(batch, context.getAssets().getProceduralAssets().getGlow(), particles);
        batch.end();

        context.getPostProcess().endAndBlit();

        // ── Crisp UI pass — untouched by the shader or punch blur ──
        context.getViewport().apply(true);
        batch.setProjectionMatrix(context.getViewport().getCamera().combined);
        batch.begin();
        drawHud(batch);
        if (matchOver)    drawOutcomeOverlay(batch);
        if (disconnected) drawDisconnectOverlay(batch);
        if (inItemPhase) {
            String readyLabel = itemReadySent ? "WAITING..." : "[ READY ]";
            drawCentered(batch, context.getBodyFont(), readyLabel,
                GameConfig.WORLD_WIDTH * 0.5f, 60f, Palette.TEXT);
        }
        if (roundOverlayTimer > 0f) {
            roundOverlayTimer -= delta;
            drawCentered(batch, context.getTitleFont(), roundOverlayText,
                GameConfig.WORLD_WIDTH * 0.5f, GameConfig.WORLD_HEIGHT * 0.5f, Palette.TEXT);
        }
        batch.end();
```

(The `[ READY ]` label block stays for now; Task 8 replaces it with a button. The item-description panel and event log get added to this UI pass in Tasks 6–7.)

- [ ] **Step 2: Restructure `TutorialScreen.render`**

Replace the block from `context.getPostProcess().begin();` through `context.getPostProcess().endAndBlit();` (lines ~112–137) with:

```java
        context.getPostProcess().begin();
        boolean ballVisible = graduation != null
            ? graduation.isBallVisible()
            : course.isBallVisible();
        if (graduation != null) {
            arena.setBallPosition(graduation.getBallPos().x,
                graduation.getBallPos().y, graduation.getBallPos().z);
            arena.setLivesDisplay(graduation.getPlayerLives(), graduation.getP2Lives());
        } else {
            arena.setBallPosition(course.ball().pos.x,
                course.ball().pos.y, course.ball().pos.z);
            arena.setLivesDisplay(0, 0);
        }
        arena.unprojectCursorOntoTable(input.lastMouseX, input.lastMouseY);
        arena.render3DScene(ballVisible);
        if (graduation == null) drawZonesAndPole();
        context.getViewport().apply(true);

        SpriteBatch batch = context.getBatch();
        batch.setProjectionMatrix(context.getViewport().getCamera().combined);
        batch.begin();
        arena.drawCursorMarker(batch, context.getAssets().getProceduralAssets().getAimRing());
        batch.end();

        context.getPostProcess().endAndBlit();

        // Crisp UI pass.
        context.getViewport().apply(true);
        batch.setProjectionMatrix(context.getViewport().getCamera().combined);
        batch.begin();
        drawHud(batch);
        batch.end();
```

- [ ] **Step 3: Verify both client modules compile**

Run the `:core:compileJava` command from the Conventions section. Expected: `exit=0`.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java core/src/main/java/io/github/some_example_name/screen/TutorialScreen.java
git commit -m "fix: draw match HUD text after the retro blit so it stays legible

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 2: Log-event infrastructure + P1 volley & double-bounce faults

Add the reason-code constants and a per-tick log-event queue, change the three scoring methods to take a reason byte, tag every existing call site, and enforce the P1 volley rule. TDD.

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/network/PacketType.java`
- Modify: `sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java`
- Create: `sim/src/test/java/io/github/some_example_name/world/MatchWorld3DRulesTest.java`

- [ ] **Step 1: Add the LOG_EVENT packet type + reason codes to `PacketType`**

After the `ITEM_READY` constant block (line ~71) add:

```java
    /**
     * A loggable gameplay event for the client-side event log.
     * Payload: {@code byte eventCode} (one of the LOG_* codes below),
     * {@code byte subjectPlayer} (1 or 2 — the player the event concerns).
     */
    public static final byte LOG_EVENT  = 27;

    // ── LOG_EVENT codes ───────────────────────────────────────────────────────
    public static final byte LOG_VOLLEY         = 1; // struck the ball before it bounced on their side
    public static final byte LOG_DOUBLE_BOUNCE  = 2; // let it bounce twice on their side
    public static final byte LOG_OUT_OF_BOUNDS  = 3; // a struck shot left the play area / own half
    public static final byte LOG_MISS           = 4; // failed to return in time / let it pass
    public static final byte LOG_TIMEOUT        = 5; // serve clock expired
    public static final byte LOG_FLY_HIT        = 6; // ball hit an unswatted fly
    public static final byte LOG_COIN_FLIP_LOSS = 7; // lost the coin flip
```

- [ ] **Step 2: Write failing tests for the P1 volley and double-bounce rules**

Create `MatchWorld3DRulesTest.java`:

```java
package io.github.some_example_name.world;

import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import io.github.some_example_name.network.PacketType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Shot-validation rules: exactly one bounce on the returner's side is required. */
class MatchWorld3DRulesTest {

    private static final float TOP = MatchWorld3D.TABLE_TOP_Y;

    /** Drive a bot serve until the ball is airborne on P1's side, pre-bounce. */
    private static MatchWorld3D incomingOnP1Side(long seed) {
        MatchWorld3D w = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(seed));
        w.setMatchMode(MatchMode.BOT); // bot opens, serving toward P1
        for (int i = 0; i < 60 * 8; i++) {
            w.update(1f / 60f);
            if (w.getPhase() == MatchWorld3D.Phase.INCOMING
                && w.getBallPos().z > 0.1f
                && w.getBallPos().y > TOP + 0.3f) {
                return w; // just crossed the net, not yet bounced
            }
        }
        return null;
    }

    private static Ray rayDownThroughBall(MatchWorld3D w) {
        Vector3 b = w.getBallPos();
        return new Ray(new Vector3(b.x, 10f, b.z), new Vector3(0f, -1f, 0f));
    }

    @Test void p1VolleyLosesThePoint() {
        MatchWorld3D w = incomingOnP1Side(7);
        assertNotNull(w, "ball should reach P1's side airborne");
        int before = w.getPlayerLives();
        w.handlePlayerClick(rayDownThroughBall(w)); // strike pre-bounce
        assertEquals(before - 1, w.getPlayerLives(), "a volley must cost P1 a life");
        assertTrue(w.hasLogEvent(), "a volley must enqueue a log event");
        int packed = w.pollLogEvent();
        assertEquals(PacketType.LOG_VOLLEY, (packed >> 8) & 0xFF);
        assertEquals(1, packed & 0xFF, "subject is P1");
    }

    @Test void p1ReturnAfterOneBounceIsLegal() {
        MatchWorld3D w = incomingOnP1Side(7);
        assertNotNull(w);
        // park the ball low over P1's side so it bounces exactly once, then click
        w.getBallPos().set(0f, TOP + 0.25f, 3f);
        w.getBallVel().set(0f, -2f, 0f);
        w.getBallSpin().setZero();
        boolean bounced = false;
        for (int i = 0; i < 60 && !bounced; i++) {
            w.update(1f / 60f);
            bounced = w.getBallVel().y > 0f && w.getBallPos().y > TOP; // rising after the bounce
        }
        assertTrue(bounced, "ball should bounce once on P1's side");
        int before = w.getPlayerLives();
        assertTrue(w.handlePlayerClick(rayDownThroughBall(w)), "a post-bounce click is a legal return");
        assertEquals(MatchWorld3D.Phase.OUTGOING, w.getPhase());
        assertEquals(before, w.getPlayerLives(), "a legal return must not cost a life");
    }

    @Test void p1DoubleBounceLosesThePoint() {
        MatchWorld3D w = incomingOnP1Side(7);
        assertNotNull(w);
        // park it low and still over P1's side: it bounces, rises, falls, bounces again
        w.getBallPos().set(0f, TOP + 0.25f, 3f);
        w.getBallVel().set(0f, -2f, 0f);
        w.getBallSpin().setZero();
        int before = w.getPlayerLives();
        for (int i = 0; i < 60 * 3; i++) {
            w.update(1f / 60f);
            if (w.getPlayerLives() < before) break;
        }
        assertEquals(before - 1, w.getPlayerLives(), "two bounces on P1's side must cost a life");
        boolean sawDouble = false;
        while (w.hasLogEvent()) if (((w.pollLogEvent() >> 8) & 0xFF) == PacketType.LOG_DOUBLE_BOUNCE) sawDouble = true;
        assertTrue(sawDouble, "double bounce must enqueue LOG_DOUBLE_BOUNCE");
    }
}
```

- [ ] **Step 3: Run the tests — expect a compile failure**

Run with `--tests "io.github.some_example_name.world.MatchWorld3DRulesTest"`. Expected: `exit=1`, log shows `cannot find symbol` for `hasLogEvent`/`pollLogEvent` (and the rule isn't enforced yet).

- [ ] **Step 4: Add the log-event queue to `MatchWorld3D`**

Add the import near the top (after the existing `com.badlogic.gdx.utils.Pool` import):

```java
import com.badlogic.gdx.utils.IntArray;
import io.github.some_example_name.network.PacketType;
```

Add the field next to the other event fields (after `tableBounceEvent`, ~line 121):

```java
    /** Packed (code<<8 | subject) gameplay events for the client log, drained each tick by the server. */
    private final IntArray logEvents = new IntArray();

    private void logEvent(byte code, int subjectPlayer) {
        logEvents.add(((code & 0xFF) << 8) | (subjectPlayer & 0xFF));
    }
```

Add the public drain API near the other `consume*`/accessor methods (e.g. after `consumeNetHitEvent`, ~line 841):

```java
    public boolean hasLogEvent() { return logEvents.size > 0; }
    /** Removes and returns the oldest packed event: {@code (code<<8) | subject}. */
    public int pollLogEvent() { return logEvents.removeIndex(0); }
```

- [ ] **Step 5: Change the scoring methods to take a reason and emit a log event**

Change `handlePlayerMiss()` (subject = P1) — replace its signature and add the log line:

```java
    private void handlePlayerMiss(byte reason) {
        logEvent(reason, 1);
        player.loseLife();
        if (player.getLives() <= 0) {
            outcome = MatchOutcome.BOT_WIN;
            statusText = "The shot got through.";
            ballVisible = false;
            return;
        }
        nextServer = 2;
        enterItemPhase();
    }
```

Change `botMissedShot()` (subject = P2) the same way:

```java
    private void botMissedShot(byte reason) {
        logEvent(reason, 2);
        bot.loseLife();
        if (bot.getLives() <= 0) {
            outcome = MatchOutcome.PLAYER_WIN;
            statusText = "The bot could not handle the pressure.";
            ballVisible = false;
            return;
        }
        nextServer = 1;
        enterItemPhase();
    }
```

Change the public `clientMiss()` to a reason-taking version plus a no-arg delegate (subject = P2):

```java
    /** Client timed out without hitting — score a point for the host. */
    public void clientMiss() { clientMiss(PacketType.LOG_MISS); }

    public void clientMiss(byte reason) {
        logEvent(reason, 2);
        bot.loseLife();
        if (bot.getLives() <= 0) {
            outcome = MatchOutcome.PLAYER_WIN;
            statusText = "Opponent couldn't keep up.";
            ballVisible = false;
            return;
        }
        nextServer = 1;
        enterItemPhase();
    }
```

- [ ] **Step 6: Tag every existing call site with a reason, and add the P1 volley check**

In `updatePrepareServe` (PVP timeout):

```java
            if (phaseTimer <= -PVP_SERVE_TIMEOUT) {
                if (nextServer == 1) handlePlayerMiss(PacketType.LOG_TIMEOUT);
                else                 botMissedShot(PacketType.LOG_TIMEOUT);
            }
```

In `updateIncoming`:

```java
        if (contacts.tableBounce) {
            if (contacts.bounceZ > 0f && crossedNet) {
                bouncesOnPlayerSide++;
                if (bouncesOnPlayerSide >= 2) { handlePlayerMiss(PacketType.LOG_DOUBLE_BOUNCE); return; }
            } else {
                botMissedShot(PacketType.LOG_OUT_OF_BOUNDS); // landed on its own side
                return;
            }
        }
```
...and the trailing checks:
```java
        if (ball.pos.z > TABLE_HALF_LENGTH + 1.5f) { handlePlayerMiss(PacketType.LOG_MISS); return; }
        if (ball.pos.y < TABLE_TOP_Y) {
            if (bouncesOnPlayerSide == 0) botMissedShot(PacketType.LOG_OUT_OF_BOUNDS);
            else handlePlayerMiss(PacketType.LOG_MISS);
        }
```

In `updateOutgoing` — the invalid-bounce branch and the out/fell branch:

```java
            } else {
                handlePlayerMiss(PacketType.LOG_OUT_OF_BOUNDS); // bounced on own side
            }
```
```java
        if (ball.pos.y < TABLE_TOP_Y
            || ball.pos.z < -TABLE_HALF_LENGTH - 4f
            || Math.abs(ball.pos.x) > TABLE_HALF_WIDTH + 6f) {
            handlePlayerMiss(PacketType.LOG_OUT_OF_BOUNDS); // went long/wide or fell
        }
```

In `updateBotResolve` — PVP OOB, PVP timeout, and the three bot-miss sites:

```java
        if (matchMode == MatchMode.PVP) {
            if (ball.pos.z < -TABLE_HALF_LENGTH - 2f
                || Math.abs(ball.pos.x) > TABLE_HALF_WIDTH + 4f
                || ball.pos.y < 0f) {
                clientMiss(PacketType.LOG_OUT_OF_BOUNDS);
                return;
            }
        }

        phaseTimer -= delta;

        if (matchMode == MatchMode.PVP) {
            if (phaseTimer <= 0f) clientMiss(PacketType.LOG_TIMEOUT);
            return;
        }
```
```java
        if (!botPlanArmed) {
            if (phaseTimer <= 0f) botMissedShot(PacketType.LOG_MISS); // safety net
            return;
        }
```
```java
        if (botPlan.whiff || botPlan.strikeTime < 0f) {
            botMissedShot(PacketType.LOG_MISS);
            return;
        }
```
```java
        if (ball.pos.y < TABLE_TOP_Y
            || ball.pos.z < -TABLE_HALF_LENGTH - 2f
            || Math.abs(ball.pos.x) > TABLE_HALF_WIDTH + 4f) {
            botMissedShot(PacketType.LOG_OUT_OF_BOUNDS);
            return;
        }
```

In `tryHitBall`, immediately after the `intersectRaySphere` guard succeeds (before computing `ndx`/`ndy`):

```java
        if (!Intersector.intersectRaySphere(pickRay, ball.pos, hitRadius, hitPoint)) return false;
        // A legal return requires exactly one prior bounce on P1's side.
        if (bouncesOnPlayerSide == 0) { handlePlayerMiss(PacketType.LOG_VOLLEY); return true; }
```

- [ ] **Step 7: Run the tests — expect pass**

Run with `--tests "io.github.some_example_name.world.MatchWorld3DRulesTest"`. Expected: `exit=0`.

- [ ] **Step 8: Run the full sim suite to catch regressions**

Run the full `:sim:test` command. Expected: `exit=0` (the existing rally/bot tests still pass — the bot never volleys, and legal returns are unchanged).

- [ ] **Step 9: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/network/PacketType.java sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java sim/src/test/java/io/github/some_example_name/world/MatchWorld3DRulesTest.java
git commit -m "feat: enforce one-bounce return rule for P1 (volley/double-bounce = fault) + log-event queue

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 3: P2 (PvP) bounce counter, volley & double-bounce faults

P2's side has no bounce counter, and `isClientCanHit()` lets P2 strike mid-air during `OUTGOING`. Add `bouncesOnClientSide`, restrict legal returns to `BOT_RESOLVE`, and penalize the volley and the second bounce. TDD.

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java`
- Modify: `sim/src/test/java/io/github/some_example_name/world/MatchWorld3DRulesTest.java`

- [ ] **Step 1: Write failing PvP tests**

Append to `MatchWorld3DRulesTest`:

```java
    /** PvP: P1 serves; drive until the ball is airborne on P2's side, pre-bounce. */
    private static MatchWorld3D pvpOutgoingOnP2Side(long seed) {
        MatchWorld3D w = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(seed));
        w.setMatchMode(MatchMode.PVP);
        if (!w.tryPlayerServe(null)) return null;
        for (int i = 0; i < 60 * 8; i++) {
            w.update(1f / 60f);
            if (w.getPhase() == MatchWorld3D.Phase.OUTGOING
                && w.getBallPos().z < -0.1f
                && w.getBallPos().y > TOP + 0.3f) {
                return w;
            }
        }
        return null;
    }

    @Test void p2VolleyLosesThePoint() {
        MatchWorld3D w = pvpOutgoingOnP2Side(3);
        assertNotNull(w, "serve should reach P2's side airborne");
        int before = w.getP2Lives();
        w.handleOpponentClick(rayDownThroughBall(w)); // strike pre-bounce
        assertEquals(before - 1, w.getP2Lives(), "a P2 volley must cost P2 a life");
        boolean sawVolley = false;
        while (w.hasLogEvent()) {
            int packed = w.pollLogEvent();
            if (((packed >> 8) & 0xFF) == PacketType.LOG_VOLLEY) { sawVolley = true; assertEquals(2, packed & 0xFF); }
        }
        assertTrue(sawVolley, "P2 volley must enqueue LOG_VOLLEY for subject 2");
    }

    @Test void p2ReturnAfterOneBounceIsLegal() {
        MatchWorld3D w = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(3));
        w.setMatchMode(MatchMode.PVP);
        assertTrue(w.tryPlayerServe(null));
        boolean resolve = false;
        for (int i = 0; i < 60 * 6 && !resolve; i++) {
            w.update(1f / 60f);
            resolve = w.getPhase() == MatchWorld3D.Phase.BOT_RESOLVE;
        }
        assertTrue(resolve, "serve should bounce once on P2's side and enter BOT_RESOLVE");
        int before = w.getP2Lives();
        assertTrue(w.handleOpponentClick(rayDownThroughBall(w)), "a post-bounce P2 click is a legal return");
        assertEquals(MatchWorld3D.Phase.INCOMING, w.getPhase());
        assertEquals(before, w.getP2Lives());
    }

    @Test void p2DoubleBounceLosesThePoint() {
        MatchWorld3D w = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(3));
        w.setMatchMode(MatchMode.PVP);
        assertTrue(w.tryPlayerServe(null));
        boolean resolve = false;
        for (int i = 0; i < 60 * 6 && !resolve; i++) {
            w.update(1f / 60f);
            resolve = w.getPhase() == MatchWorld3D.Phase.BOT_RESOLVE;
        }
        assertTrue(resolve);
        // park it low over P2's side so a second bounce fires
        w.getBallPos().set(0f, TOP + 0.25f, -3f);
        w.getBallVel().set(0f, -2f, 0f);
        w.getBallSpin().setZero();
        int before = w.getP2Lives();
        for (int i = 0; i < 60 * 3; i++) {
            w.update(1f / 60f);
            if (w.getP2Lives() < before) break;
        }
        assertEquals(before - 1, w.getP2Lives(), "a second bounce on P2's side must cost P2 a life");
    }
```

- [ ] **Step 2: Run the new tests — expect failures**

Run with `--tests "io.github.some_example_name.world.MatchWorld3DRulesTest"`. Expected: `exit=1` (P2 volley currently returns legally; double-bounce in BOT_RESOLVE is never scored).

- [ ] **Step 3: Add the `bouncesOnClientSide` field**

Next to `private int bouncesOnPlayerSide;` (~line 81):

```java
    private int bouncesOnClientSide;
```

- [ ] **Step 4: Reset it everywhere `bouncesOnPlayerSide` resets**

In each of `botServe`, `tryHitBall` (the legal-return path), `tryPlayerServe`, `handleOpponentClick` (legal-return path), `tryClientServe`, and `updateBotResolve`'s bot-return path — wherever you see `bouncesOnPlayerSide = 0;` add `bouncesOnClientSide = 0;` directly after it. (Search the file for `bouncesOnPlayerSide = 0;` and mirror each.)

- [ ] **Step 5: Count the first P2 bounce in `updateOutgoing`**

In the valid-bounce branch:

```java
            if (valid) {
                bouncesOnClientSide = 1;
                phase = Phase.BOT_RESOLVE;
                phaseTimer = GameConfig.NET_CLIENT_MISS_TIMEOUT;
                statusText = matchMode == MatchMode.PVP
                    ? "P2 — return the ball!"
                    : "Clean return. Bot is trying to answer.";
            } else {
```

- [ ] **Step 6: Score a second P2 bounce in `updateBotResolve` (PvP only)**

Immediately after `stepBall(delta);` at the top of `updateBotResolve`, add:

```java
        if (matchMode == MatchMode.PVP && contacts.tableBounce
            && contacts.bounceZ < 0f && crossedNet) {
            bouncesOnClientSide++;
            if (bouncesOnClientSide >= 2) { clientMiss(PacketType.LOG_DOUBLE_BOUNCE); return; }
        }
```

- [ ] **Step 7: Restrict `isClientCanHit` to BOT_RESOLVE and add the P2 volley to `handleOpponentClick`**

Replace `isClientCanHit()`:

```java
    public boolean isClientCanHit() {
        return phase == Phase.BOT_RESOLVE;
    }
```

Replace the body of `handleOpponentClick` after the fly-swat and serve guards (from `if (!isClientCanHit()) return false;` onward) with:

```java
        float scale = bot.getTargetScaleMultiplier() * p2Effects.hitScaleMultiplier();
        // Volley: P2 struck the ball before it bounced on their side.
        if (phase == Phase.OUTGOING && crossedNet && ball.pos.z < 0f
            && Intersector.intersectRaySphere(pickRay, ball.pos, PaddleContact.hitRadius(scale), hitPoint)) {
            clientMiss(PacketType.LOG_VOLLEY);
            return true;
        }
        if (!isClientCanHit()) return false;
        if (!PaddleContact.returnFromRay(pickRay, ball, config.getPhysics(),
                scale, bot.getReturnPowerMultiplier(), p1Effects.incomingSpeedMultiplier(), false)) {
            return false;
        }
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        bouncesOnClientSide = 0;
        phase = Phase.INCOMING;
        statusText = "P2 returns! Ball heading to P1.";
        paddleHitEvent = true;
        return true;
```

- [ ] **Step 8: Run the rules tests — expect pass**

Run with `--tests "io.github.some_example_name.world.MatchWorld3DRulesTest"`. Expected: `exit=0`.

- [ ] **Step 9: Run the full sim suite**

Full `:sim:test`. Expected: `exit=0`.

- [ ] **Step 10: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java sim/src/test/java/io/github/some_example_name/world/MatchWorld3DRulesTest.java
git commit -m "feat: enforce one-bounce return rule for P2 in PvP (volley/double-bounce = fault)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 4: Log coin-flip and fly-hit outcomes

Tag the two fly-hit handlers and the coin-flip resolution so they appear in the log. TDD for the deterministic coin-flip path.

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java`
- Modify: `sim/src/test/java/io/github/some_example_name/world/MatchWorld3DRulesTest.java`

- [ ] **Step 1: Write a failing coin-flip log test**

Append to `MatchWorld3DRulesTest`:

```java
    @Test void coinFlipLossEnqueuesLogEventForTheLoser() {
        // Loop seeds so we hit at least one flip that lands on each side.
        boolean sawSelfLoss = false, sawOppLoss = false;
        for (long seed = 0; seed < 30 && !(sawSelfLoss && sawOppLoss); seed++) {
            MatchWorld3D w = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(seed));
            w.setMatchMode(MatchMode.BOT);
            w.enterItemPhase();
            while (w.hasLogEvent()) w.pollLogEvent(); // drain dealing noise, if any
            w.getP1Inventory().clear();
            w.getP1Inventory().add(io.github.some_example_name.model.ItemType.COIN_FLIP);
            assertTrue(w.applyItem(1, io.github.some_example_name.model.ItemType.COIN_FLIP));
            int code = -1, subject = -1;
            while (w.hasLogEvent()) {
                int packed = w.pollLogEvent();
                if (((packed >> 8) & 0xFF) == PacketType.LOG_COIN_FLIP_LOSS) { code = PacketType.LOG_COIN_FLIP_LOSS; subject = packed & 0xFF; }
            }
            assertEquals(PacketType.LOG_COIN_FLIP_LOSS, code, "coin flip must enqueue a loss event, seed " + seed);
            if (subject == 1) sawSelfLoss = true; else if (subject == 2) sawOppLoss = true;
        }
        assertTrue(sawSelfLoss && sawOppLoss, "both flip outcomes must be reachable and logged");
    }
```

- [ ] **Step 2: Run — expect failure**

Run with `--tests`. Expected: `exit=1` (no coin-flip log yet).

- [ ] **Step 3: Tag the fly-hit handlers**

In `handlePlayerFlyHit`, add `logEvent(PacketType.LOG_FLY_HIT, 1);` as the first line. In `handleBotFlyHit`, add `logEvent(PacketType.LOG_FLY_HIT, 2);` as the first line.

- [ ] **Step 4: Tag the coin-flip resolution in `applyItem`**

Replace the `COIN_FLIP` case:

```java
            case COIN_FLIP   -> {
                int loser;
                if (random.nextFloat() < 0.5f) { self.loseLife(); loser = playerNumber; }
                else { opp.loseLife(); loser = (playerNumber == 1) ? 2 : 1; }
                logEvent(PacketType.LOG_COIN_FLIP_LOSS, loser);
                checkMatchOver();
            }
```

- [ ] **Step 5: Run the rules tests — expect pass**

Run with `--tests`. Expected: `exit=0`.

- [ ] **Step 6: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java sim/src/test/java/io/github/some_example_name/world/MatchWorld3DRulesTest.java
git commit -m "feat: log coin-flip losses and fly hits

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 5: LOG_EVENT transport (connection + server broadcast)

Wire the new packet through `GameConnection` and drain `MatchWorld3D`'s queue each server tick.

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/network/GameConnection.java`
- Modify: `sim/src/main/java/io/github/some_example_name/server/GameServer.java`

- [ ] **Step 1: Add the listener callback**

In `GameConnection.Listener`, after `onFlyKilled` (line ~58):

```java
        /** @param subject player (1/2) the event concerns */
        default void onLogEvent(int code, int subject)              {}
```

- [ ] **Step 2: Add the reader case**

In `readLoop`'s switch, after the `FLY_KILLED` case (line ~226):

```java
                    case PacketType.LOG_EVENT -> {
                        int code    = in.readByte() & 0xFF;
                        int subject = in.readByte() & 0xFF;
                        dispatch.execute(() -> listener.onLogEvent(code, subject));
                    }
```

- [ ] **Step 3: Add the send method**

After `sendFlyKilled` (line ~341):

```java
    public void sendLogEvent(int code, int subject) {
        write(() -> {
            out.writeByte(PacketType.LOG_EVENT);
            out.writeByte(code);
            out.writeByte(subject);
        });
    }
```

- [ ] **Step 4: Drain the queue in the server tick**

In `GameServer.runOneRound`, after the `consumeFlyKilledIndex` block (line ~200) and before the SFX consume block:

```java
            while (w.hasLogEvent()) {
                int packed = w.pollLogEvent();
                sendLogEventToAll((packed >> 8) & 0xFF, packed & 0xFF);
            }
```

- [ ] **Step 5: Add the broadcast helper**

Next to `sendSfxToAll` (line ~258):

```java
    private void sendLogEventToAll(int code, int subject) {
        GameConnection c1 = p1, c2 = p2;
        if (c1 != null) c1.sendLogEvent(code, subject);
        if (c2 != null) c2.sendLogEvent(code, subject);
    }
```

- [ ] **Step 6: Verify the sim module compiles**

```bash
./gradlew :sim:compileJava -Dorg.gradle.logging.level=lifecycle --console=plain > /tmp/g.log 2>&1; echo "exit=$?"; tail -8 /tmp/g.log
```
Expected: `exit=0`.

- [ ] **Step 7: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/network/GameConnection.java sim/src/main/java/io/github/some_example_name/server/GameServer.java
git commit -m "feat: LOG_EVENT packet — server broadcasts gameplay reasons to clients

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 6: Item descriptions on hover

Add the display copy and show the hovered item's name + description in the UI pass. (Done before the event log because that task uses `ItemCopy.name`.)

**Files:**
- Create: `core/src/main/java/io/github/some_example_name/render/ItemCopy.java`
- Modify: `core/src/main/java/io/github/some_example_name/render/ItemPhaseRenderer.java`
- Modify: `core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java`

- [ ] **Step 1: Create `ItemCopy`**

```java
package io.github.some_example_name.render;

import io.github.some_example_name.model.ItemType;

/** Display name + one-line description per item, shown during the item phase. */
public final class ItemCopy {
    private ItemCopy() {}

    public static String name(ItemType t) {
        return switch (t) {
            case PATCH_KIT   -> "Patch Kit";
            case WIDE_PADDLE -> "Wide Paddle";
            case SLOW_MO     -> "Slow-Mo";
            case STEAL       -> "Steal";
            case FAST_SERVE  -> "Fast Serve";
            case TINY_PADDLE -> "Tiny Paddle";
            case PUNCH       -> "Punch";
            case FLY_BAIT    -> "Fly Bait";
            case COIN_FLIP   -> "Coin Flip";
        };
    }

    public static String description(ItemType t) {
        return switch (t) {
            case PATCH_KIT   -> "Restores one of your lives.";
            case WIDE_PADDLE -> "Your hit area is larger this rally.";
            case SLOW_MO     -> "The ball comes at you slower this rally.";
            case STEAL       -> "Take a random item from your opponent.";
            case FAST_SERVE  -> "The ball comes at your opponent faster.";
            case TINY_PADDLE -> "Shrinks your opponent's hit area.";
            case PUNCH       -> "Blurs your opponent's view for 10 seconds.";
            case FLY_BAIT    -> "Spawns flies on your opponent's side — ball hits cost a point.";
            case COIN_FLIP   -> "50/50 — someone loses a life.";
        };
    }
}
```

(The `switch` expressions over the enum are exhaustive — the compiler guarantees every `ItemType` is covered, so no separate test is needed.)

- [ ] **Step 2: Expose the hovered item type from `ItemPhaseRenderer`**

Add after `pickItem` (~line 246):

```java
    /** The item the cursor is currently over (first live hovered entry), or null. */
    public ItemType hoveredType(int playerNumber) {
        Array<ItemEntry> entries = playerNumber == 1 ? p1Entries : p2Entries;
        for (ItemEntry e : entries) {
            if (e.hovered && !e.used) return e.type;
        }
        return null;
    }
```

- [ ] **Step 3: Draw the description panel in the UI pass**

Add the import to `NetMatchScreen` (next to the other `render.*` imports):

```java
import io.github.some_example_name.render.ItemCopy;
```

In `NetMatchScreen.render`, in the crisp UI-pass batch block, add inside the `if (inItemPhase)` area (after the READY label, before `batch.end()`):

```java
            ItemType hov = itemPhaseRenderer != null ? itemPhaseRenderer.hoveredType(1) : null;
            if (hov != null) {
                drawCentered(batch, context.getBodyFont(), ItemCopy.name(hov),
                    GameConfig.WORLD_WIDTH * 0.5f, 240f, Palette.TEXT);
                drawCentered(batch, context.getBodyFont(), ItemCopy.description(hov),
                    GameConfig.WORLD_WIDTH * 0.5f, 212f, Palette.TEXT_DIM);
            }
```

- [ ] **Step 4: Compile the client**

Run `:core:compileJava`. Expected: `exit=0`.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/io/github/some_example_name/render/ItemCopy.java core/src/main/java/io/github/some_example_name/render/ItemPhaseRenderer.java core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java
git commit -m "feat: show item name + description on hover during the item phase

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 7: Event-log rendering + settings toggle

Add the `eventLogEnabled` setting and its config toggle, then render a fading upper-left log in `NetMatchScreen`'s UI pass, fed by `onLogEvent`, `onItemUsed`, and `onRoundOver`. (`ItemCopy` already exists from Task 6.)

**Files:**
- Modify: `core/src/main/java/io/github/some_example_name/core/GameSettings.java`
- Modify: `core/src/main/java/io/github/some_example_name/screen/ConfigScreen.java`
- Modify: `core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java`

- [ ] **Step 1: Add the `eventLogEnabled` setting**

In `GameSettings`: add the key constant beside `KEY_SCREEN_SHAKE`:

```java
    private static final String KEY_EVENT_LOG = "eventLog";
```
the field beside `screenShake`:
```java
    private boolean eventLog = true;
```
load it in `load()` beside `screenShake`:
```java
        eventLog = prefs.getBoolean(KEY_EVENT_LOG, eventLog);
```
save it in `save()`:
```java
        prefs.putBoolean(KEY_EVENT_LOG, eventLog);
```
and the accessors beside `isScreenShakeEnabled`:
```java
    public boolean isEventLogEnabled() { return eventLog; }
    public void    setEventLogEnabled(boolean v) { eventLog = v; save(); }
```

- [ ] **Step 2: Add the GAME-tab row + toggle in `ConfigScreen`**

In `drawTabContent`, case `GAME`, add a third row:

```java
            case GAME -> {
                drawSettingRow(batch, pixel, body, rowX, rowY(0), rowW, "Show FPS Counter", "top-left overlay during the match");
                drawSettingRow(batch, pixel, body, rowX, rowY(1), rowW, "Screen Shake",     "camera shake on miss");
                drawSettingRow(batch, pixel, body, rowX, rowY(2), rowW, "Event Log",        "upper-left feed of points, items, faults");
            }
```

In `rebuildTabContent`, case `GAME`, add the third toggle:

```java
            case GAME -> {
                toggles.add(new Toggle(toggleX, widgetY(0) - 6f,
                    s::isShowFpsCounter, s::setShowFpsCounter));
                toggles.add(new Toggle(toggleX, widgetY(1) - 6f,
                    s::isScreenShakeEnabled, s::setScreenShakeEnabled));
                toggles.add(new Toggle(toggleX, widgetY(2) - 6f,
                    s::isEventLogEnabled, s::setEventLogEnabled));
            }
```

- [ ] **Step 3: Add the log model + helpers to `NetMatchScreen`**

(The `ItemCopy` import was already added in Task 6 — don't duplicate it.)

Add fields near the other UI state (after `roundOverlayTimer`, ~line 86):

```java
    // ── Event log (upper-left, fading) ──────────────────────────────────────────
    private static final float LOG_LIFETIME = 6f;
    private static final int   LOG_MAX      = 6;
    private final java.util.ArrayDeque<LogLine> logLines = new java.util.ArrayDeque<>();

    private static final class LogLine {
        final String text; float age;
        LogLine(String t) { this.text = t; }
    }

    private void pushLog(String text) {
        logLines.addFirst(new LogLine(text));
        while (logLines.size() > LOG_MAX) logLines.removeLast();
    }
```

Add the describe + render helpers (anywhere among the private methods, e.g. after `drawHud`):

```java
    private String describeLog(int code, int subject) {
        String who = (subject == playerNumber) ? "You" : "Opponent";
        return switch (code) {
            case PacketType.LOG_VOLLEY         -> who + ": volley — point lost";
            case PacketType.LOG_DOUBLE_BOUNCE  -> who + ": double bounce — point lost";
            case PacketType.LOG_OUT_OF_BOUNDS  -> who + ": shot out of bounds";
            case PacketType.LOG_MISS           -> who + ": missed the return";
            case PacketType.LOG_TIMEOUT        -> who + ": too slow — point lost";
            case PacketType.LOG_FLY_HIT        -> who + ": hit a fly — point lost";
            case PacketType.LOG_COIN_FLIP_LOSS -> "Coin flip — " + (subject == playerNumber ? "you lose" : "opponent loses");
            default -> null;
        };
    }

    private void drawEventLog(SpriteBatch batch, float delta) {
        if (!context.getSettings().isEventLogEnabled()) return;
        java.util.Iterator<LogLine> it = logLines.iterator();
        int i = 0;
        while (it.hasNext()) {
            LogLine line = it.next();
            line.age += delta;
            if (line.age >= LOG_LIFETIME) { it.remove(); continue; }
            float alpha = Math.min(1f, (LOG_LIFETIME - line.age)); // fade over the last 1s
            context.getBodyFont().setColor(Palette.TEXT_DIM.r, Palette.TEXT_DIM.g, Palette.TEXT_DIM.b, alpha);
            context.getBodyFont().draw(batch, line.text, GameConfig.HUD_PADDING, 610f - i * 24f);
            i++;
        }
        context.getBodyFont().setColor(Palette.TEXT);
    }
```

- [ ] **Step 4: Feed the log from the network callbacks**

In `onLogEvent` (new override — add it next to the other callbacks):

```java
    @Override
    public void onLogEvent(int code, int subject) {
        Gdx.app.postRunnable(() -> {
            String line = describeLog(code, subject);
            if (line != null) pushLog(line);
        });
    }
```

In the existing `onItemUsed` (inside its `postRunnable`, after the sound plays), add a log line:

```java
            String who = (byPlayer == playerNumber) ? "You" : "Opponent";
            if (t == ItemType.FLY_BAIT) {
                pushLog(byPlayer == playerNumber
                    ? "You set Fly Bait — flies on opponent's side"
                    : "Opponent set Fly Bait — flies on your side!");
            } else {
                pushLog(who + " used " + ItemCopy.name(t));
            }
```

In the existing `onRoundOver` (inside its `postRunnable`), add:

```java
            pushLog(playerNumber == winner ? "You win the round" : "Opponent wins the round");
```

- [ ] **Step 5: Draw the log in the UI pass**

In `render`, in the crisp UI-pass batch block from Task 1, add right after `drawHud(batch);`:

```java
        drawEventLog(batch, delta);
```

- [ ] **Step 6: Compile the client**

Run `:core:compileJava`. Expected: `exit=0`.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/io/github/some_example_name/core/GameSettings.java core/src/main/java/io/github/some_example_name/screen/ConfigScreen.java core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java
git commit -m "feat: toggleable upper-left event log fed by LOG_EVENT + item/round callbacks

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 8: Explicit END SELECTION button

Replace the "click empty space = READY" shortcut with a real button both players press.

**Files:**
- Modify: `core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java`

- [ ] **Step 1: Add imports and the button field**

Imports:

```java
import com.badlogic.gdx.math.Vector3;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.ui.Button;
```
(`Vector3` and `GameConfig` are already imported — only add `Button`.) Field, near `itemReadySent` (~line 70):

```java
    private Button readyButton;
    private final Vector3 tmpUiWorld = new Vector3();
```

- [ ] **Step 2: Create the button in `show()`**

In `show()`, after `itemPhaseRenderer` is ensured (~line 144):

```java
        if (readyButton == null) {
            float w = 240f, h = 56f;
            readyButton = new Button(GameConfig.WORLD_WIDTH * 0.5f - w * 0.5f, 40f, w, h,
                "END SELECTION", Palette.RED, this::confirmReady);
        }
```

- [ ] **Step 3: Add the `confirmReady` action and a screen→world helper**

Note: `confirmReady` deliberately does **not** set `inItemPhase = false` (unlike the old empty-space path). Staying in the item phase keeps the items and button on screen, with the button locked to "WAITING...". The existing `onState` handler already clears `inItemPhase` and `itemReadySent` when the server advances (`activePlayer != 0`), which ends the waiting state.

```java
    private void confirmReady() {
        if (itemReadySent) return;
        itemReadySent = true; // stay in the item phase; the button now shows WAITING
        context.getAssets().getUiClickSfx().play(getSfxGain() * 0.6f);
        if (conn != null) conn.sendItemReady();
    }

    /** Unprojects a raw screen point to viewport world (1280×720) coordinates into {@link #tmpUiWorld}. */
    private void toUiWorld(int screenX, int screenY) {
        tmpUiWorld.set(screenX, screenY, 0f);
        context.getViewport().unproject(tmpUiWorld);
    }
```

- [ ] **Step 4: Replace the empty-space ready in `handleClick`**

In `handleClick`, replace the item-phase branch (currently the `if (inItemPhase) { ... }` block, lines ~319–343) with:

```java
        if (inItemPhase) {
            if (itemReadySent) return; // locked in — waiting for the opponent, ignore clicks
            // END SELECTION button first (2D UI, world coords).
            toUiWorld(netInput.lastClickX, netInput.lastClickY);
            if (readyButton != null && readyButton.tryClick(tmpUiWorld.x, tmpUiWorld.y)) {
                return; // confirmReady() ran via the button action
            }
            // Then the 3D item shelf.
            if (itemPhaseRenderer != null && arena != null) {
                com.badlogic.gdx.math.collision.Ray ray = arena.getCamera()
                    .getPickRay(netInput.lastClickX, netInput.lastClickY);
                ItemType picked = itemPhaseRenderer.pickItem(ray, 1);
                if (picked != null) {
                    context.getAssets().getUiClickSfx().play(getSfxGain() * 0.6f);
                    if (conn != null) conn.sendUseItem(picked.getId());
                }
            }
            return; // never sent to the server during ITEM_PHASE; empty space does nothing
        }
```

- [ ] **Step 5: Update hover + draw the button; remove the old text label**

In `render`, in the world/update area during item phase (where `updateHover` is called), add the button hover update:

```java
            toUiWorld(netInput.lastMouseX, netInput.lastMouseY);
            readyButton.enabled = !itemReadySent;
            readyButton.label = itemReadySent ? "WAITING..." : "END SELECTION";
            readyButton.updateHover(tmpUiWorld.x, tmpUiWorld.y);
```

In the UI-pass block, replace the `[ READY ]` text label (added in Task 1 Step 1) with the button draw:

```java
        if (inItemPhase && readyButton != null) {
            readyButton.draw(batch, context.getAssets().getProceduralAssets().getPixel(),
                context.getBodyFont(), context.getGlyphLayout());
            // (item description panel from Task 6 sits just above, at y≈212–240)
        }
```

- [ ] **Step 6: Fix the status line wording for the new flow**

In `deriveStatus`, replace the item-phase branch so the WAITING state reads correctly (the item phase now stays active after readying):

```java
        if (inItemPhase) {
            if (itemReadySent) return "Waiting for opponent...";
            return "Pick your items, then press END SELECTION.";
        }
```

(Delete the now-redundant `if (itemReadySent && activePlayer == 0) return "Waiting for opponent's items...";` line below it.)

- [ ] **Step 7: Compile the client**

Run `:core:compileJava`. Expected: `exit=0`.

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java
git commit -m "feat: explicit END SELECTION button for the item phase (replaces empty-space ready)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Task 9: Raise the volume

Bump the default master volume and the per-sound base gains. Sliders still scale on top.

**Files:**
- Modify: `core/src/main/java/io/github/some_example_name/core/GameSettings.java`
- Modify: `core/src/main/java/io/github/some_example_name/screen/ConfigScreen.java`
- Modify: `core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java`

- [ ] **Step 1: Default master volume 80 → 100**

In `GameSettings`, change `private int masterVolume = 80;` to `private int masterVolume = 100;`.

- [ ] **Step 2: Mute toggle restore value 80 → 100**

In `ConfigScreen.rebuildTabContent`, case `AUDIO`, change the mute toggle writer `on -> s.setMasterVolume(on ? 0 : 80)` to `on -> s.setMasterVolume(on ? 0 : 100)`.

- [ ] **Step 3: Raise the per-sound base gains in `NetMatchScreen`**

Apply these exact replacements (each is a `.play(...)`/`.loop(...)`/`setVolume(...)` multiplier):

- `backgroundMusic.setVolume(0.25f * context.getSettings().getMusicGain());` → `0.35f * ...`
- fly buzz: `getFlyBuzzSfx().loop(getSfxGain() * 0.25f)` → `* 0.35f`
- life lost (self): `getLifeLostSfx().play(getSfxGain() * 0.8f)` → `* 1.0f`
- life lost (opponent): `getLifeLostSfx().play(getSfxGain() * 0.45f)` → `* 0.6f`
- paddle hit: `paddleHitSfx.play(getSfxGain() * 0.7f)` → `* 0.95f`
- table hit: `tableHitSfx.play(getSfxGain() * 0.6f)` → `* 0.85f`
- item use: `getItemUseSfx().play(getSfxGain() * 0.7f)` → `* 0.9f`
- ui hover: `getUiHoverSfx().play(getSfxGain() * 0.4f)` → `* 0.5f`
- ui click: `getUiClickSfx().play(getSfxGain() * 0.6f)` → `* 0.8f` (both call sites: the item-pick click and `confirmReady`)

- [ ] **Step 4: Compile the client**

Run `:core:compileJava`. Expected: `exit=0`.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/io/github/some_example_name/core/GameSettings.java core/src/main/java/io/github/some_example_name/screen/ConfigScreen.java core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java
git commit -m "feat: raise default master volume and per-sound base gains

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Final verification

- [ ] **Run the full sim test suite** — `./gradlew :sim:test -Dorg.gradle.logging.level=lifecycle --console=plain > /tmp/g.log 2>&1; echo "exit=$?"; tail -8 /tmp/g.log` → `exit=0`.
- [ ] **Compile every module** — `./gradlew build -x test -Dorg.gradle.logging.level=lifecycle --console=plain > /tmp/g.log 2>&1; echo "exit=$?"; tail -8 /tmp/g.log` → `exit=0`.
- [ ] **Manual smoke test** (run the app): start a bot match; confirm HUD text is crisp with the retro filter ON; volley the ball (click it mid-air before it bounces) and confirm you lose the point with a "volley — point lost" log line upper-left; reach an item phase, hover items to read descriptions, press END SELECTION; toggle the Event Log off in Settings → GAME and confirm the feed disappears; confirm the game is audibly louder.
