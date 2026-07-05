# Visible Ball Spin Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render the ball rotating at exactly the physical angular velocity `ω` (`BallState.spin`) the sim already integrates — real constants, no fake animation.

**Architecture:** A pure static quaternion-integration helper lives in the sim module (`world/physics/SpinOrientation.java`, testable with sim's existing JUnit setup). `MatchArenaRenderer` owns a persistent `Quaternion ballOrientation`, exposes `spinBall(Vector3 spin, float delta)`, and composes the orientation into the ball transform in `setBallPosition`. The two screens that drive the ball each add one `spinBall` call per frame.

**Tech Stack:** Java 17, libGDX (`Quaternion`, `Matrix4`), JUnit 5 (sim module only), Gradle.

**Spec:** `docs/superpowers/specs/2026-07-04-visible-ball-spin-design.md`

**Gradle gotcha (repo-specific):** `gradle.properties` sets logging to quiet — a successful build prints NOTHING, and piping eats the exit code. Always run builds as:

```bash
./gradlew <task> -Dorg.gradle.logging.level=lifecycle --console=plain > /tmp/g.log 2>&1; echo "exit=$?"; tail -6 /tmp/g.log
```

The echoed `exit=` is the truth. Test details land in `sim/build/test-results/test/TEST-*.xml`. If Gradle fails with sandbox errors on macOS, re-run the Bash tool call with `dangerouslyDisableSandbox: true`.

---

### Task 1: `SpinOrientation` quaternion-integration helper (TDD)

The sim module already depends on libGDX core (`Quaternion` is available) and has JUnit 5 wired up (`sim/build.gradle` → `useJUnitPlatform()`).

**Files:**
- Create: `sim/src/main/java/io/github/some_example_name/world/physics/SpinOrientation.java`
- Test: `sim/src/test/java/io/github/some_example_name/world/physics/SpinOrientationTest.java`

- [ ] **Step 1: Write the failing test**

Create `sim/src/test/java/io/github/some_example_name/world/physics/SpinOrientationTest.java`:

```java
package io.github.some_example_name.world.physics;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link SpinOrientation}: angular velocity → orientation, world axes. */
class SpinOrientationTest {

    private static final float EPS = 1e-4f;

    @Test
    void zeroSpinLeavesOrientationUntouched() {
        Quaternion q = new Quaternion(0.1f, 0.2f, 0.3f, 0.9f).nor();
        Quaternion before = new Quaternion(q);
        SpinOrientation.integrate(q, new Vector3(0f, 0f, 0f), 1f / 60f);
        assertEquals(before.x, q.x, EPS);
        assertEquals(before.y, q.y, EPS);
        assertEquals(before.z, q.z, EPS);
        assertEquals(before.w, q.w, EPS);
    }

    @Test
    void spinAboutYRotatesByOmegaTimesDt() {
        // ω = π rad/s about +Y for 1 s → 180°: +X maps to −X
        Quaternion q = new Quaternion();
        SpinOrientation.integrate(q, new Vector3(0f, MathUtils.PI, 0f), 1f);
        Vector3 v = q.transform(new Vector3(1f, 0f, 0f));
        assertEquals(-1f, v.x, EPS);
        assertEquals(0f, v.y, EPS);
        assertEquals(0f, v.z, EPS);
    }

    @Test
    void topspinAxisFollowsRightHandRule() {
        // ω about +X for 90°: +Z maps to −Y (Rx: y' = −sinθ·z, z' = cosθ·z)
        Quaternion q = new Quaternion();
        SpinOrientation.integrate(q, new Vector3(MathUtils.HALF_PI, 0f, 0f), 1f);
        Vector3 v = q.transform(new Vector3(0f, 0f, 1f));
        assertEquals(0f, v.x, EPS);
        assertEquals(-1f, v.y, EPS);
        assertEquals(0f, v.z, EPS);
    }

    @Test
    void manySmallStepsMatchOneBigStepAndStayNormalized() {
        Vector3 spin = new Vector3(40f, 25f, -60f); // realistic world rad/s
        Quaternion many = new Quaternion();
        for (int i = 0; i < 240; i++) {
            SpinOrientation.integrate(many, spin, 1f / 240f);
        }
        Quaternion one = new Quaternion();
        SpinOrientation.integrate(one, spin, 1f);
        // constant ω ⇒ integration is exact regardless of step size
        Vector3 a = many.transform(new Vector3(1f, 2f, 3f));
        Vector3 b = one.transform(new Vector3(1f, 2f, 3f));
        assertEquals(b.x, a.x, 1e-3f);
        assertEquals(b.y, a.y, 1e-3f);
        assertEquals(b.z, a.z, 1e-3f);
        assertTrue(Math.abs(many.len() - 1f) < EPS, "quaternion must stay normalized");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails to compile (class missing)**

```bash
cd /Users/pedro_mariano/Documents/7_semestre/DesenvolvimentoJogosSim/versoes_libgdx/PingPongGame
./gradlew :sim:test --tests "io.github.some_example_name.world.physics.SpinOrientationTest" -Dorg.gradle.logging.level=lifecycle --console=plain > /tmp/g.log 2>&1; echo "exit=$?"; tail -6 /tmp/g.log
```

Expected: `exit=1`, log shows `compileTestJava` failure — `SpinOrientation` does not exist.

- [ ] **Step 3: Write the implementation**

Create `sim/src/main/java/io/github/some_example_name/world/physics/SpinOrientation.java`:

```java
package io.github.some_example_name.world.physics;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Integrates an angular velocity into an orientation quaternion. The visual
 * companion to {@link BallState#spin}: the renderer accumulates the ball's
 * real spin (world rad/s) here so the drawn ball rotates at exactly the
 * simulated rate.
 *
 * <p>Allocation-free and stateless, matching the physics classes' rule that
 * shared code must stay safe for parallel worlds.</p>
 */
public final class SpinOrientation {

    private SpinOrientation() {}

    /**
     * Rotates {@code orientation} by {@code spin}·{@code dt}. The spin axis is
     * in world space, so the delta is applied on the left. Normalizes to keep
     * float drift from accumulating across frames. No-op for negligible spin.
     */
    public static void integrate(Quaternion orientation, Vector3 spin, float dt) {
        float omega = spin.len();
        if (omega < 1e-4f || dt <= 0f) return;
        float half = 0.5f * omega * dt;
        // Δq = (sin(θ/2)·ω̂, cos(θ/2)) composed without allocating
        float s = (float) Math.sin(half) / omega;
        orientation.mulLeft(spin.x * s, spin.y * s, spin.z * s, (float) Math.cos(half));
        orientation.nor();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
./gradlew :sim:test --tests "io.github.some_example_name.world.physics.SpinOrientationTest" -Dorg.gradle.logging.level=lifecycle --console=plain > /tmp/g.log 2>&1; echo "exit=$?"; tail -6 /tmp/g.log
```

Expected: `exit=0`.

- [ ] **Step 5: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/physics/SpinOrientation.java sim/src/test/java/io/github/some_example_name/world/physics/SpinOrientationTest.java
git commit -m "feat(sim): quaternion integration of ball spin for visual rotation

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 2: Renderer — persistent orientation composed into the ball transform

No automated test (renderer needs a GL context); correctness is covered by Task 1's math tests plus compilation and the Task 4 feel check.

**Files:**
- Modify: `core/src/main/java/io/github/some_example_name/render/MatchArenaRenderer.java`

- [ ] **Step 1: Add the import**

In the import block of `MatchArenaRenderer.java` (it already imports `com.badlogic.gdx.math.Vector3`), add:

```java
import com.badlogic.gdx.math.Quaternion;
import io.github.some_example_name.world.physics.SpinOrientation;
```

(Match the file's existing import grouping: gdx imports with gdx imports, project imports with project imports.)

- [ ] **Step 2: Add the orientation field**

Next to the existing ball fields (near `private final Vector3 ballObjCenter = new Vector3();`, around line 61), add:

```java
/** Accumulated visual orientation, integrated from the physical spin. */
private final Quaternion ballOrientation = new Quaternion();
```

- [ ] **Step 3: Add `spinBall` and compose the rotation in `setBallPosition`**

Replace the current `setBallPosition` (lines 248–259):

```java
    public void setBallPosition(float x, float y, float z) {
        ensureInitialized();
        if (usingObjBall) {
            // translate to ball pos, scale to ball size, then re-centre the mesh
            ballInstance.transform.idt();
            ballInstance.transform.translate(x, y, z);
            ballInstance.transform.scale(ballObjScale, ballObjScale, ballObjScale);
            ballInstance.transform.translate(-ballObjCenter.x, -ballObjCenter.y, -ballObjCenter.z);
        } else {
            ballInstance.transform.setToTranslation(x, y, z);
        }
    }
```

with:

```java
    public void setBallPosition(float x, float y, float z) {
        ensureInitialized();
        if (usingObjBall) {
            // translate to ball pos, spin about the ball centre, scale to ball
            // size, then re-centre the mesh
            ballInstance.transform.idt();
            ballInstance.transform.translate(x, y, z);
            ballInstance.transform.rotate(ballOrientation);
            ballInstance.transform.scale(ballObjScale, ballObjScale, ballObjScale);
            ballInstance.transform.translate(-ballObjCenter.x, -ballObjCenter.y, -ballObjCenter.z);
        } else {
            ballInstance.transform.setToTranslation(x, y, z).rotate(ballOrientation);
        }
    }

    /**
     * Advances the ball's visual orientation by its physical spin (world-unit
     * rad/s, straight from {@code BallState.spin}) over {@code delta} seconds.
     * Call once per frame before {@link #setBallPosition}. The orientation
     * deliberately persists across serves — a ball has no canonical rest
     * orientation, and carrying it over avoids visible snaps.
     */
    public void spinBall(Vector3 spin, float delta) {
        SpinOrientation.integrate(ballOrientation, spin, delta);
    }
```

- [ ] **Step 4: Compile**

```bash
./gradlew :core:compileJava -Dorg.gradle.logging.level=lifecycle --console=plain > /tmp/g.log 2>&1; echo "exit=$?"; tail -6 /tmp/g.log
```

Expected: `exit=0`.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/io/github/some_example_name/render/MatchArenaRenderer.java
git commit -m "feat(render): ball model rotates with its accumulated spin orientation

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 3: Screens feed the physical spin to the renderer

**Files:**
- Modify: `core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java:295-304`
- Modify: `core/src/main/java/io/github/some_example_name/screen/TutorialScreen.java:116-124`

- [ ] **Step 1: NetMatchScreen — spin from the extrapolated state**

In `updateSimulation` (line ~295), the current block:

```java
        if (ballVisible) {
            extrapState.pos.set(snapBallPos);
            extrapState.vel.set(snapBallVel);
            extrapState.spin.set(snapBallSpin);
            clientPhysics.resetAccumulator();
            // cap so packet loss can't run physics far past the last snapshot
            clientPhysics.step(extrapState, Math.min(snapAge, 0.25f), null, extrapContacts);
            renderedBallPos.set(extrapState.pos);
            arena.setBallPosition(extrapState.pos.x, extrapState.pos.y, extrapState.pos.z);
        }
```

becomes (one added line before `setBallPosition`):

```java
        if (ballVisible) {
            extrapState.pos.set(snapBallPos);
            extrapState.vel.set(snapBallVel);
            extrapState.spin.set(snapBallSpin);
            clientPhysics.resetAccumulator();
            // cap so packet loss can't run physics far past the last snapshot
            clientPhysics.step(extrapState, Math.min(snapAge, 0.25f), null, extrapContacts);
            renderedBallPos.set(extrapState.pos);
            arena.spinBall(extrapState.spin, delta);
            arena.setBallPosition(extrapState.pos.x, extrapState.pos.y, extrapState.pos.z);
        }
```

Note: `updateSimulation(float delta)` already has `delta` in scope.

- [ ] **Step 2: TutorialScreen — spin in both branches**

In `render` (line ~116), the current block:

```java
        if (graduation != null) {
            arena.setBallPosition(graduation.getBallPos().x,
                graduation.getBallPos().y, graduation.getBallPos().z);
            arena.setLivesDisplay(graduation.getPlayerLives(), graduation.getP2Lives());
        } else {
            arena.setBallPosition(course.ball().pos.x,
                course.ball().pos.y, course.ball().pos.z);
            arena.setLivesDisplay(0, 0);
        }
```

becomes:

```java
        if (graduation != null) {
            arena.spinBall(graduation.getBallSpin(), delta);
            arena.setBallPosition(graduation.getBallPos().x,
                graduation.getBallPos().y, graduation.getBallPos().z);
            arena.setLivesDisplay(graduation.getPlayerLives(), graduation.getP2Lives());
        } else {
            arena.spinBall(course.ball().spin, delta);
            arena.setBallPosition(course.ball().pos.x,
                course.ball().pos.y, course.ball().pos.z);
            arena.setLivesDisplay(0, 0);
        }
```

`graduation.getBallSpin()` exists (`MatchWorld3D.java:852`); `course.ball()` returns the `BallState` (`DrillCourse.java:358`), whose `.spin` is public. `render(float delta)` has `delta` in scope.

- [ ] **Step 3: Compile**

```bash
./gradlew :core:compileJava -Dorg.gradle.logging.level=lifecycle --console=plain > /tmp/g.log 2>&1; echo "exit=$?"; tail -6 /tmp/g.log
```

Expected: `exit=0`.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java core/src/main/java/io/github/some_example_name/screen/TutorialScreen.java
git commit -m "feat(screens): feed physical ball spin to the renderer each frame

Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>"
```

---

### Task 4: Full verification

- [ ] **Step 1: Run the whole build + all tests**

```bash
./gradlew build -Dorg.gradle.logging.level=lifecycle --console=plain > /tmp/g.log 2>&1; echo "exit=$?"; tail -8 /tmp/g.log
```

Expected: `exit=0`. If any sim test regressed, the physics was touched by mistake — this feature must not change sim behavior (only the new `SpinOrientation` class was added there).

- [ ] **Step 2: Manual feel check (user-facing verification)**

Launch the game:

```bash
./gradlew :lwjgl3:run -Dorg.gradle.logging.level=lifecycle --console=plain > /tmp/run.log 2>&1 &
```

Check, per the spec's testing section:
1. Tutorial spin drill: topspin rolls the ball visibly forward, backspin backward.
2. Curve serve drill: sidespin spins the ball about the vertical axis while the trajectory curves the same way.
3. Very hard hits near max spin strobe — expected and accepted.

If launching a window isn't possible in this environment, report that the feel check is pending and hand it to the user.
