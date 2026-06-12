# Physics Rebuild Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the gravity-only ball physics with a spin-aware simulation (drag + Magnus + spin-coupled bounce + real net), an off-center click contact model, and a prediction-based bot — per the approved spec `docs/superpowers/specs/2026-06-10-physics-rebuild-design.md`.

**Architecture:** New `sim/.../world/physics/` package holds one integrator (`BallPhysics`), the contact mapping (`PaddleContact`), and the bot (`BotPlanner`), all configured by `PhysicsConfig` (SI constants from the paper + `unitsPerMeter` + `timeScale`). `MatchWorld3D` keeps every rule but delegates all motion. The client reuses the same integrator for snapshot extrapolation; STATE gains a spin vector.

**Tech Stack:** Java 17, libGDX math only (`Vector3`, `MathUtils`, `RandomXS128`, `Intersector`), JUnit 5 (already wired in `sim/build.gradle` via `useJUnitPlatform()`).

**Commands:**
- All sim tests: `./gradlew :sim:test`
- One class: `./gradlew :sim:test --tests 'io.github.some_example_name.world.physics.BallPhysicsFlightTest'`
- Full build: `./gradlew build`
- Run game: `./gradlew lwjgl3:run`

**Repo facts the engineer needs:**
- Working dir: repo root. Branch: `experiment/multiplayer-lan`. The working tree has unrelated staged deletions (asset cleanup) — **never `git add -A`**; every commit below lists exact paths.
- Test files live in `sim/src/test/java/io/github/some_example_name/...` (see `FlyCollisionTest` for house style: plain JUnit 5, static-import assertions, doc comment explaining intent).
- Coordinates: x lateral, y up (gravity −y), z depth; P1 side +z, P2/bot side −z, net at z = 0. Table top y = 2, half-width 3, half-length 7, ball radius 0.18, net top y = 2.5.
- The unit scheme (documented in `PhysicsConfig` Javadoc): world trajectory = real SI trajectory scaled by `U = unitsPerMeter` in space and slowed by `σ = timeScale`. Exact conversions: `v_w = U·σ·v_si`, `a_w = U·σ²·a_si`, `ω_w = σ·ω_si`, `k_drag_w = k_drag_si / U`, Magnus k dimensionless (unchanged), restitution unchanged. With defaults U = 5.11, σ = 0.442: gravity_w = 9.81·5.11·0.442² ≈ **9.79** ≈ the old hard-coded 9.8, and `velW(4.0 m/s) ≈ 9.0 u/s` ≈ old return speeds — defaults intentionally reproduce today's feel.

**Deviations from the spec (agreed rationale):**
- `OUT_OF_PLAY` is not a `ContactEvent`; floor/out-of-bounds stay as rules inside `MatchWorld3D` (the physics module only reports table/net contacts). Equivalent behavior, smaller API.
- Serves scale click offsets by a `serveControl` factor (0.6) so the serve legality property holds — real serves are more controlled than rally shots.
- `HitVelocity.sanitizeNetworkReturn` has **zero callers** (legacy HIT retired), so `HitVelocity` is deleted outright; `PaddleContact.clamp` is the anti-cheat envelope.

---

### Task 1: PhysicsConfig + BallState + geometry single-sourcing

**Files:**
- Create: `sim/src/main/java/io/github/some_example_name/world/physics/PhysicsConfig.java`
- Create: `sim/src/main/java/io/github/some_example_name/world/physics/BallState.java`
- Modify: `sim/src/main/java/io/github/some_example_name/model/MatchConfig.java` (add physics field + getter)
- Modify: `sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java:28-34` (constants become aliases)
- Test: `sim/src/test/java/io/github/some_example_name/world/physics/PhysicsConfigTest.java`

- [ ] **Step 1: Write the failing test**

```java
package io.github.some_example_name.world.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The unit scheme: world = SI scaled by unitsPerMeter in space, slowed by
 * timeScale. Derived accessors must implement the exact conversions
 * (v_w = U·σ·v, a_w = U·σ²·a, ω_w = σ·ω, k_drag_w = k_drag/U) so that the
 * default config reproduces today's gravity feel (≈ 9.8 world units/s²).
 */
class PhysicsConfigTest {

    @Test void defaultGravityMatchesLegacyFeel() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        assertEquals(9.79f, cfg.gravityW(), 0.05f);
    }

    @Test void conversionsAreExact() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        cfg.unitsPerMeter = 2f;
        cfg.timeScale = 0.5f;
        assertEquals(9.81f * 2f * 0.25f, cfg.gravityW(), 1e-5f);
        assertEquals(10f * 2f * 0.5f,    cfg.velW(10f),  1e-5f);
        assertEquals(100f * 0.5f,        cfg.spinW(100f),1e-5f);
        assertEquals(0.155f / 2f,        cfg.dragKW(),   1e-6f);
        assertEquals(4f / 0.5f,          cfg.spinDecayTauW(), 1e-5f);
    }

    @Test void ballStateCopies() {
        BallState a = new BallState();
        a.pos.set(1f, 2f, 3f); a.vel.set(4f, 5f, 6f); a.spin.set(7f, 8f, 9f);
        BallState b = new BallState().set(a);
        a.pos.x = 99f;
        assertEquals(1f, b.pos.x, 0f);
        assertEquals(6f, b.vel.z, 0f);
        assertEquals(8f, b.spin.y, 0f);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :sim:test --tests 'io.github.some_example_name.world.physics.PhysicsConfigTest'`
Expected: COMPILE FAILURE — `PhysicsConfig` and `BallState` do not exist.

- [ ] **Step 3: Create PhysicsConfig**

```java
package io.github.some_example_name.world.physics;

/**
 * All physics constants in one place.
 *
 * <p>SI values (meters, seconds, radians): drag and restitution are from
 * Lin, Yu &amp; Huang, "Ball Tracking and Trajectory Prediction for
 * Table-Tennis Robots", Sensors 2020, §4.2. The sim runs in world units; the
 * {@code *W()} accessors convert exactly, with U = unitsPerMeter and
 * σ = timeScale:</p>
 *
 * <pre>
 *   x_w(τ) = U·x_si(σ·τ)   v_w = U·σ·v_si   a_w = U·σ²·a_si
 *   ω_w = σ·ω_si           k_drag_w = k_drag_si / U   (magnus k unchanged)
 * </pre>
 *
 * <p>σ is exact slow motion: it never changes a trajectory's shape, only how
 * fast it plays. σ = 1 is real table-tennis tempo; the default ≈ today's pace.</p>
 */
public final class PhysicsConfig {

    // ── table geometry (world units, fixed) ─────────────────────────────────
    public static final float TABLE_HALF_WIDTH  = 3f;
    public static final float TABLE_HALF_LENGTH = 7f;
    public static final float TABLE_TOP_Y       = 2f;
    public static final float NET_TOP_Y         = 2.5f;
    public static final float BALL_RADIUS       = 0.18f;

    // ── world mapping ────────────────────────────────────────────────────────
    /** 14-unit game table / 2.74 m real table. */
    public float unitsPerMeter = 5.11f;
    /** Slow-motion factor; 1 = real tempo. Default reproduces gravity_w ≈ 9.8. */
    public float timeScale = 0.442f;

    // ── flight (SI) ──────────────────────────────────────────────────────────
    public float gravitySI = 9.81f;
    /** Cd·ρ·A/2m = 0.5·1.29·1.3e-3/(2·2.7e-3) [1/m] (paper). */
    public float dragKSI = 0.155f;
    /** Magnus a = k·(ω × v); standard smooth-sphere value, dimensionless. */
    public float magnusK = 0.006f;
    /** Spin decay time constant in flight [s]. */
    public float spinDecayTauSI = 4f;

    // ── table contact ────────────────────────────────────────────────────────
    /** Restitution measured over 330 real trajectories (paper). */
    public float restitution = 0.92f;
    /** Sliding friction coefficient at the bounce. */
    public float bounceFriction = 0.25f;
    /** Spin kept after each table contact. */
    public float spinKeptOnBounce = 0.7f;

    // ── net contact ──────────────────────────────────────────────────────────
    /** Forward-speed fraction kept on a net hit (center of jitter range). */
    public float netForwardKeep = 0.1f;
    /** Jitter half-range; a negative draw means the ball falls back. */
    public float netJitter = 0.18f;
    public float netVerticalKeep = 0.6f;
    public float netLateralKeep  = 0.8f;
    public float netSpinKeep     = 0.5f;

    // ── paddle gains (SI; PaddleContact converts) ────────────────────────────
    public float basePaceSI = 4.0f;
    public float baseArcSI  = 2.2f;
    public float aimGainSI  = 1.4f;
    public float paceOffsetGain = 0.25f;
    public float paceCarry      = 0.35f;
    public float topspinGainSI  = 120f;
    public float sidespinGainSI = 100f;
    public float corkscrewTilt  = 0.5f;
    public float spinTransfer   = -0.3f;
    public float servePaceSI    = 4.4f;
    public float serveArcSI     = 2.4f;
    /** Serve offsets are scaled by this — serves are more controlled. */
    public float serveControl   = 0.6f;

    // ── clamps (anti-cheat envelope) ─────────────────────────────────────────
    public float maxSpeedSI = 14f;
    public float maxSpinSI  = 180f;

    // ── derived world-unit accessors ─────────────────────────────────────────
    public float gravityW()      { return gravitySI * unitsPerMeter * timeScale * timeScale; }
    public float dragKW()        { return dragKSI / unitsPerMeter; }
    public float spinDecayTauW() { return spinDecayTauSI / timeScale; }
    public float velW(float vSI)   { return vSI * unitsPerMeter * timeScale; }
    public float spinW(float wSI)  { return wSI * timeScale; }
    public float maxSpeedW()     { return velW(maxSpeedSI); }
    public float maxSpinW()      { return spinW(maxSpinSI); }

    public static PhysicsConfig createDefault() { return new PhysicsConfig(); }
}
```

- [ ] **Step 4: Create BallState**

```java
package io.github.some_example_name.world.physics;

import com.badlogic.gdx.math.Vector3;

/** Ball motion state: position, velocity, spin (angular velocity), world units. */
public final class BallState {
    public final Vector3 pos  = new Vector3();
    public final Vector3 vel  = new Vector3();
    public final Vector3 spin = new Vector3();

    public BallState set(BallState other) {
        pos.set(other.pos);
        vel.set(other.vel);
        spin.set(other.spin);
        return this;
    }

    public BallState reset() {
        pos.setZero();
        vel.setZero();
        spin.setZero();
        return this;
    }
}
```

- [ ] **Step 5: Single-source the geometry and carry the config**

In `MatchWorld3D.java:28-33` replace the literal constants with aliases (public API and values unchanged):

```java
    public static final float TABLE_HALF_WIDTH  = PhysicsConfig.TABLE_HALF_WIDTH;
    public static final float TABLE_HALF_LENGTH = PhysicsConfig.TABLE_HALF_LENGTH;
    public static final float TABLE_TOP_Y       = PhysicsConfig.TABLE_TOP_Y;
    public static final float NET_TOP_Y         = PhysicsConfig.NET_TOP_Y;
    public static final float NET_HEIGHT        = NET_TOP_Y - TABLE_TOP_Y;
    public static final float BALL_RADIUS       = PhysicsConfig.BALL_RADIUS;
```

(Note: `NET_HEIGHT` is now derived; keep declaration order so it compiles. `GRAVITY` stays untouched for now — `NetMatchScreen` still uses it until Task 8.)

Add the import `io.github.some_example_name.world.physics.PhysicsConfig` to `MatchWorld3D`.

In `MatchConfig.java` add:

```java
import io.github.some_example_name.world.physics.PhysicsConfig;
```

and inside the class:

```java
    private final PhysicsConfig physics = PhysicsConfig.createDefault();

    public PhysicsConfig getPhysics() {
        return physics;
    }
```

- [ ] **Step 6: Run tests**

Run: `./gradlew :sim:test`
Expected: PASS (new `PhysicsConfigTest` + existing `FlyCollisionTest`).

- [ ] **Step 7: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/physics/PhysicsConfig.java \
        sim/src/main/java/io/github/some_example_name/world/physics/BallState.java \
        sim/src/main/java/io/github/some_example_name/model/MatchConfig.java \
        sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java \
        sim/src/test/java/io/github/some_example_name/world/physics/PhysicsConfigTest.java
git commit -m "feat(physics): PhysicsConfig (paper SI constants + timeScale) and BallState"
```

---

### Task 2: BallPhysics — flight forces (gravity, drag, Magnus, spin decay)

**Files:**
- Create: `sim/src/main/java/io/github/some_example_name/world/physics/BallPhysics.java`
- Create: `sim/src/main/java/io/github/some_example_name/world/physics/StepContacts.java`
- Test: `sim/src/test/java/io/github/some_example_name/world/physics/BallPhysicsFlightTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package io.github.some_example_name.world.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Flight integration: with drag and Magnus zeroed the trajectory must match
 * closed-form ballistics; drag must shorten range; Magnus must curve
 * symmetrically with spin sign; timeScale must never change a path's shape.
 */
class BallPhysicsFlightTest {

    /** Config with contacts out of reach so flight is pure. */
    private static PhysicsConfig flightCfg() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        return cfg;
    }

    private static BallState launch(float vx, float vy, float vz) {
        BallState s = new BallState();
        s.pos.set(0f, 8f, 0f); // high above the table: no contacts during the test
        s.vel.set(vx, vy, vz);
        return s;
    }

    @Test void zeroDragMatchesClosedFormBallistics() {
        PhysicsConfig cfg = flightCfg();
        cfg.dragKSI = 0f;
        cfg.magnusK = 0f;
        BallPhysics phys = new BallPhysics(cfg);
        BallState s = launch(2f, 3f, -4f);
        StepContacts c = new StepContacts();
        float t = 0.8f;
        phys.step(s, t, null, c);
        float g = cfg.gravityW();
        // semi-implicit Euler position lags closed form by ~½·g·dt·t
        assertEquals(2f * t,                    s.pos.x,      1e-3f);
        assertEquals(-4f * t,                   s.pos.z - 0f, 1e-3f);
        assertEquals(8f + 3f * t - 0.5f * g * t * t, s.pos.y, 0.05f);
        assertEquals(3f - g * t,                s.vel.y,      1e-3f);
    }

    @Test void dragShortensRange() {
        PhysicsConfig dragless = flightCfg();
        dragless.dragKSI = 0f; dragless.magnusK = 0f;
        PhysicsConfig dragged = flightCfg();
        dragged.magnusK = 0f;
        BallState a = launch(0f, 0f, 9f);
        BallState b = launch(0f, 0f, 9f);
        new BallPhysics(dragless).step(a, 1f, null, new StepContacts());
        new BallPhysics(dragged).step(b, 1f, null, new StepContacts());
        assertTrue(b.pos.z < a.pos.z - 0.3f,
            "drag should cost noticeable distance: " + a.pos.z + " vs " + b.pos.z);
    }

    @Test void magnusCurvesSymmetricallyWithSpinSign() {
        PhysicsConfig cfg = flightCfg();
        cfg.dragKSI = 0f;
        BallPhysics phys = new BallPhysics(cfg);
        BallState left = launch(0f, 0f, -9f);
        left.spin.set(0f, cfg.spinW(80f), 0f);
        BallState right = launch(0f, 0f, -9f);
        right.spin.set(0f, -cfg.spinW(80f), 0f);
        StepContacts c = new StepContacts();
        phys.step(left, 0.8f, null, c);
        new BallPhysics(cfg).step(right, 0.8f, null, c);
        assertTrue(Math.abs(left.pos.x) > 0.15f, "spin should visibly curve the ball");
        assertEquals(left.pos.x, -right.pos.x, 1e-3f);
    }

    @Test void timeScaleChangesSpeedNotShape() {
        // σ values chosen so both world durations are exact substep multiples
        PhysicsConfig fast = flightCfg();
        fast.timeScale = 1f;
        PhysicsConfig slow = flightCfg();
        slow.timeScale = 0.5f;
        // same SI launch expressed in each config's world units
        BallState a = new BallState();
        a.pos.set(0f, 8f, 0f);
        a.vel.set(fast.velW(1.5f), fast.velW(1f), fast.velW(-4f));
        a.spin.set(fast.spinW(40f), 0f, 0f);
        BallState b = new BallState();
        b.pos.set(0f, 8f, 0f);
        b.vel.set(slow.velW(1.5f), slow.velW(1f), slow.velW(-4f));
        b.spin.set(slow.spinW(40f), 0f, 0f);
        // 0.5 SI-seconds of flight each: world time = si / σ
        new BallPhysics(fast).step(a, 0.5f / 1f,   null, new StepContacts());
        new BallPhysics(slow).step(b, 0.5f / 0.5f, null, new StepContacts());
        assertEquals(a.pos.x, b.pos.x, 0.02f);
        assertEquals(a.pos.y, b.pos.y, 0.02f);
        assertEquals(a.pos.z, b.pos.z, 0.02f);
    }

    @Test void spinDecaysExponentiallyInFlight() {
        PhysicsConfig cfg = flightCfg();
        BallPhysics phys = new BallPhysics(cfg);
        BallState s = launch(0f, 0f, 0f);
        s.spin.set(0f, 50f, 0f);
        phys.step(s, cfg.spinDecayTauW(), null, new StepContacts());
        assertEquals(50f / (float) Math.E, s.spin.y, 50f * 0.03f);
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :sim:test --tests 'io.github.some_example_name.world.physics.BallPhysicsFlightTest'`
Expected: COMPILE FAILURE — `BallPhysics`, `StepContacts` missing.

- [ ] **Step 3: Create StepContacts**

```java
package io.github.some_example_name.world.physics;

/** What the ball touched during one {@link BallPhysics#step}. Reused, never allocated per frame. */
public final class StepContacts {
    public boolean tableBounce;
    /** Where the (last) table bounce of the step happened, world units. */
    public float bounceX, bounceZ;
    public boolean netHit;

    public void clear() {
        tableBounce = false;
        netHit = false;
    }
}
```

- [ ] **Step 4: Create BallPhysics (flight only — contacts are no-ops this task)**

```java
package io.github.some_example_name.world.physics;

import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;

/**
 * The single ball integrator: gravity + air drag + Magnus with fixed substeps,
 * plus table and net contacts. Used identically by the server world, the bot's
 * trajectory prediction, and the client's between-snapshot extrapolation.
 *
 * <p>Scoring rules (sides, double bounce, out of bounds) stay with the caller;
 * this class only reports what the ball physically touched.</p>
 */
public final class BallPhysics {

    /** Fixed substep. The paper integrated at 200 Hz; 240 divides 60 fps evenly. */
    public static final float SUBSTEP_DT = 1f / 240f;

    private final PhysicsConfig cfg;
    private final Vector3 accel  = new Vector3();
    private final Vector3 magnus = new Vector3();
    private float remainder;

    public BallPhysics(PhysicsConfig cfg) {
        this.cfg = cfg;
    }

    public PhysicsConfig config() {
        return cfg;
    }

    /** Drop accumulated sub-frame time. Call when teleporting the ball (new serve). */
    public void resetAccumulator() {
        remainder = 0f;
    }

    /**
     * Advances {@code s} by {@code delta} seconds in fixed substeps and records
     * contacts in {@code out}. {@code random} drives net-cord jitter; pass
     * {@code null} for the deterministic mid-range outcome (client extrapolation).
     */
    public void step(BallState s, float delta, RandomXS128 random, StepContacts out) {
        out.clear();
        remainder += delta;
        while (remainder >= SUBSTEP_DT) {
            remainder -= SUBSTEP_DT;
            substep(s, SUBSTEP_DT, random, out);
        }
    }

    private void substep(BallState s, float dt, RandomXS128 random, StepContacts out) {
        float prevX = s.pos.x, prevY = s.pos.y, prevZ = s.pos.z;

        float vLen = s.vel.len();
        accel.set(0f, -cfg.gravityW(), 0f);
        if (vLen > 1e-4f) {
            accel.mulAdd(s.vel, -cfg.dragKW() * vLen);
        }
        magnus.set(s.spin).crs(s.vel).scl(cfg.magnusK);
        accel.add(magnus);

        // semi-implicit Euler: velocity first, then position
        s.vel.mulAdd(accel, dt);
        s.pos.mulAdd(s.vel, dt);

        s.spin.scl(1f - dt / cfg.spinDecayTauW());

        netContact(s, prevX, prevY, prevZ, random, out);
        tableContact(s, out);
    }

    private void netContact(BallState s, float prevX, float prevY, float prevZ,
                            RandomXS128 random, StepContacts out) {
        // Task 4
    }

    private void tableContact(BallState s, StepContacts out) {
        // Task 3
    }
}
```

- [ ] **Step 5: Run tests**

Run: `./gradlew :sim:test --tests 'io.github.some_example_name.world.physics.BallPhysicsFlightTest'`
Expected: PASS (5 tests).

- [ ] **Step 6: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/physics/BallPhysics.java \
        sim/src/main/java/io/github/some_example_name/world/physics/StepContacts.java \
        sim/src/test/java/io/github/some_example_name/world/physics/BallPhysicsFlightTest.java
git commit -m "feat(physics): BallPhysics flight integrator — gravity, drag, Magnus, spin decay"
```

---

### Task 3: Table bounce with spin–friction coupling

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/world/physics/BallPhysics.java` (fill `tableContact`)
- Test: `sim/src/test/java/io/github/some_example_name/world/physics/BallPhysicsBounceTest.java`

**The contact model (for the engineer):** normal impulse flips `vy` with restitution e. The friction impulse opposes the slip of the ball's bottom point, `slip = (vx + ωz·r, vz − ωx·r)`; its magnitude is Coulomb sliding `μ(1+e)|vyIn|` capped by the grip impulse `(2/7)|slip|` that exactly stops slip for a solid sphere (I = 2/5·r² per unit mass). The same impulse torques the spin: `Δω = (−jz/(0.4r), 0, jx/(0.4r))`. Consequences: topspin kicks forward, backspin brakes, corkscrew (ωz) deflects laterally, and pure vertical-axis spin (ωy) does nothing at the bounce — its curve comes from Magnus in flight.

- [ ] **Step 1: Write the failing tests**

```java
package io.github.some_example_name.world.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Spin-coupled bounce: restitution height ratio ≈ e², topspin kicks forward,
 * backspin brakes, corkscrew deflects laterally, vertical-axis spin does
 * nothing at contact, and every bounce costs spin.
 */
class BallPhysicsBounceTest {

    private static final float CONTACT_Y = PhysicsConfig.TABLE_TOP_Y + PhysicsConfig.BALL_RADIUS;

    private static PhysicsConfig pureBounceCfg() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        cfg.dragKSI = 0f;
        cfg.magnusK = 0f;
        cfg.spinDecayTauSI = 1e6f; // isolate contact effects
        return cfg;
    }

    /** Drops the ball from 1.0 above the table and returns the rebound apex height. */
    @Test void reboundHeightIsRestitutionSquared() {
        PhysicsConfig cfg = pureBounceCfg();
        BallPhysics phys = new BallPhysics(cfg);
        BallState s = new BallState();
        s.pos.set(0f, CONTACT_Y + 1f, 0f);
        StepContacts c = new StepContacts();
        float apex = 0f;
        boolean bounced = false;
        for (int i = 0; i < 2400; i++) {
            phys.step(s, BallPhysics.SUBSTEP_DT, null, c);
            if (c.tableBounce) bounced = true;
            if (bounced) apex = Math.max(apex, s.pos.y);
            if (bounced && s.vel.y < 0f && apex > CONTACT_Y) break;
        }
        assertTrue(bounced);
        float e2 = cfg.restitution * cfg.restitution;
        assertEquals(e2 * 1f, apex - CONTACT_Y, 0.06f);
    }

    private static BallState falling(float vx, float vz, float wx, float wy, float wz) {
        BallState s = new BallState();
        s.pos.set(0f, CONTACT_Y + 0.01f, 0f);
        s.vel.set(vx, -3f, vz);
        s.spin.set(wx, wy, wz);
        return s;
    }

    private static void oneBounce(PhysicsConfig cfg, BallState s) {
        BallPhysics phys = new BallPhysics(cfg);
        StepContacts c = new StepContacts();
        for (int i = 0; i < 24 && !c.tableBounce; i++) {
            phys.step(s, BallPhysics.SUBSTEP_DT, null, c);
        }
        assertTrue(c.tableBounce, "ball should have bounced within 0.1s");
    }

    @Test void topspinKicksForwardOnBounce() {
        BallState s = falling(0f, 4f, 30f, 0f, 0f); // +z travel, topspin (ωx > 0)
        oneBounce(pureBounceCfg(), s);
        assertTrue(s.vel.z > 4.2f, "topspin should add forward speed, got " + s.vel.z);
    }

    @Test void backspinBrakesOnBounce() {
        BallState s = falling(0f, 4f, -30f, 0f, 0f);
        oneBounce(pureBounceCfg(), s);
        assertTrue(s.vel.z < 3.0f, "backspin should brake the ball, got " + s.vel.z);
    }

    @Test void corkscrewDeflectsLaterally() {
        BallState s = falling(0f, 4f, 0f, 0f, 40f); // slipX = ωz·r > 0 → friction pushes −x
        oneBounce(pureBounceCfg(), s);
        assertTrue(s.vel.x < -0.5f, "corkscrew should deflect laterally, got " + s.vel.x);
    }

    /**
     * Plain sliding friction brakes any moving ball at the bounce, so the
     * honest claim is comparative: vertical-axis spin changes nothing vs an
     * identical spinless bounce.
     */
    @Test void verticalAxisSpinDoesNothingAtContact() {
        BallState with = falling(0f, 4f, 0f, 60f, 0f);
        BallState without = falling(0f, 4f, 0f, 0f, 0f);
        oneBounce(pureBounceCfg(), with);
        oneBounce(pureBounceCfg(), without);
        assertEquals(without.vel.x, with.vel.x, 1e-4f);
        assertEquals(without.vel.z, with.vel.z, 1e-4f);
    }

    @Test void bounceCostsSpin() {
        BallState s = falling(0f, 4f, 30f, 20f, 10f);
        float before = s.spin.len();
        oneBounce(pureBounceCfg(), s);
        assertTrue(s.spin.len() < before * 0.8f);
    }

    @Test void noBouncePastTableEdge() {
        PhysicsConfig cfg = pureBounceCfg();
        BallPhysics phys = new BallPhysics(cfg);
        BallState s = new BallState();
        s.pos.set(0f, CONTACT_Y + 0.05f, PhysicsConfig.TABLE_HALF_LENGTH + 0.5f);
        s.vel.set(0f, -2f, 0f);
        StepContacts c = new StepContacts();
        phys.step(s, 0.2f, null, c);
        assertFalse(c.tableBounce);
        assertTrue(s.pos.y < CONTACT_Y, "ball should keep falling past the edge");
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :sim:test --tests 'io.github.some_example_name.world.physics.BallPhysicsBounceTest'`
Expected: FAIL — bounces never happen (`tableContact` is empty).

- [ ] **Step 3: Implement tableContact**

Replace the empty `tableContact` in `BallPhysics.java`:

```java
    private void tableContact(BallState s, StepContacts out) {
        if (s.vel.y >= 0f) return;
        float contactY = PhysicsConfig.TABLE_TOP_Y + PhysicsConfig.BALL_RADIUS;
        if (s.pos.y > contactY) return;
        boolean overTable = Math.abs(s.pos.x) <= PhysicsConfig.TABLE_HALF_WIDTH
                         && Math.abs(s.pos.z) <= PhysicsConfig.TABLE_HALF_LENGTH;
        if (!overTable) return; // past the edge: keeps falling, rules score it
        s.pos.y = contactY;

        float vyIn = s.vel.y;
        s.vel.y = -vyIn * cfg.restitution;

        float r = PhysicsConfig.BALL_RADIUS;
        float slipX = s.vel.x + s.spin.z * r;
        float slipZ = s.vel.z - s.spin.x * r;
        float slipLen = (float) Math.sqrt(slipX * slipX + slipZ * slipZ);
        if (slipLen > 1e-5f) {
            float j = Math.min(cfg.bounceFriction * (1f + cfg.restitution) * Math.abs(vyIn),
                               (2f / 7f) * slipLen);
            float jx = -j * slipX / slipLen;
            float jz = -j * slipZ / slipLen;
            s.vel.x += jx;
            s.vel.z += jz;
            // torque from the same impulse at the contact point, I = 2/5·r² per unit mass
            s.spin.x += -jz / (0.4f * r);
            s.spin.z +=  jx / (0.4f * r);
        }
        s.spin.scl(cfg.spinKeptOnBounce);

        out.tableBounce = true;
        out.bounceX = s.pos.x;
        out.bounceZ = s.pos.z;
    }
```

- [ ] **Step 4: Run tests**

Run: `./gradlew :sim:test --tests 'io.github.some_example_name.world.physics.BallPhysicsBounceTest'`
Expected: PASS (7 tests). Also run the flight class — still PASS.

- [ ] **Step 5: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/physics/BallPhysics.java \
        sim/src/test/java/io/github/some_example_name/world/physics/BallPhysicsBounceTest.java
git commit -m "feat(physics): spin-coupled table bounce (e=0.92, friction impulse, 2/7 grip cap)"
```

---

### Task 4: Net collider + determinism

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/world/physics/BallPhysics.java` (fill `netContact`)
- Test: `sim/src/test/java/io/github/some_example_name/world/physics/BallPhysicsNetTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package io.github.some_example_name.world.physics;

import com.badlogic.gdx.math.RandomXS128;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The net is a swept collider at z = 0: crossings are interpolated inside the
 * substep so even max-speed balls can't tunnel. A hit kills most forward
 * speed; jitter decides dribble-over vs fall-back. With null random the
 * outcome is deterministic (client extrapolation path).
 */
class BallPhysicsNetTest {

    private static BallState toward(float y, float vz) {
        BallState s = new BallState();
        s.pos.set(0f, y, vz < 0f ? 0.4f : -0.4f);
        s.vel.set(0f, 0f, vz);
        return s;
    }

    @Test void lowBallHitsNet() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BallPhysics phys = new BallPhysics(cfg);
        BallState s = toward(2.3f, -8f);
        StepContacts c = new StepContacts();
        phys.step(s, 0.1f, null, c);
        assertTrue(c.netHit);
        assertTrue(Math.abs(s.vel.z) < 1.5f, "net should kill forward speed, got " + s.vel.z);
    }

    @Test void highBallClearsNet() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        cfg.dragKSI = 0f;
        BallPhysics phys = new BallPhysics(cfg);
        BallState s = toward(3.2f, -8f); // ball bottom 3.02 > net top 2.5
        StepContacts c = new StepContacts();
        phys.step(s, 0.1f, null, c);
        assertFalse(c.netHit);
        assertTrue(s.pos.z < 0f, "ball should have crossed");
    }

    @Test void maxSpeedBallCannotTunnel() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BallPhysics phys = new BallPhysics(cfg);
        BallState s = toward(2.3f, -cfg.maxSpeedW());
        StepContacts c = new StepContacts();
        phys.step(s, 0.1f, null, c);
        assertTrue(c.netHit, "swept test must catch the crossing at max speed");
    }

    @Test void jitterProducesBothDribbleOverAndFallBack() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        boolean over = false, back = false;
        for (long seed = 0; seed < 50; seed++) {
            BallPhysics phys = new BallPhysics(cfg);
            BallState s = toward(2.3f, -8f);
            StepContacts c = new StepContacts();
            phys.step(s, 0.1f, new RandomXS128(seed), c);
            assertTrue(c.netHit);
            if (s.vel.z < 0f) over = true;  // still travelling toward −z
            if (s.vel.z > 0f) back = true;  // bounced back toward +z
        }
        assertTrue(over, "some net hits should dribble over");
        assertTrue(back, "some net hits should fall back");
    }

    @Test void sameSeedSameInputsGiveIdenticalTrajectories() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BallState a = toward(2.4f, -7f);
        a.spin.set(10f, 20f, 5f);
        BallState b = new BallState().set(a);
        StepContacts c = new StepContacts();
        BallPhysics pa = new BallPhysics(cfg);
        BallPhysics pb = new BallPhysics(cfg);
        RandomXS128 ra = new RandomXS128(7);
        RandomXS128 rb = new RandomXS128(7);
        for (int i = 0; i < 120; i++) {
            pa.step(a, 1f / 60f, ra, c);
            pb.step(b, 1f / 60f, rb, c);
        }
        assertEquals(a.pos.x, b.pos.x, 0f);
        assertEquals(a.pos.y, b.pos.y, 0f);
        assertEquals(a.pos.z, b.pos.z, 0f);
        assertEquals(a.vel.len(), b.vel.len(), 0f);
        assertEquals(a.spin.len(), b.spin.len(), 0f);
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :sim:test --tests 'io.github.some_example_name.world.physics.BallPhysicsNetTest'`
Expected: FAIL — `lowBallHitsNet`, `maxSpeedBallCannotTunnel`, `jitterProducesBothDribbleOverAndFallBack` fail (`netContact` is empty). The clear/determinism tests may already pass.

- [ ] **Step 3: Implement netContact**

Replace the empty `netContact` in `BallPhysics.java`:

```java
    private void netContact(BallState s, float prevX, float prevY, float prevZ,
                            RandomXS128 random, StepContacts out) {
        boolean crossed = (prevZ < 0f && s.pos.z >= 0f) || (prevZ > 0f && s.pos.z <= 0f);
        if (!crossed) return;
        float dz = s.pos.z - prevZ;
        float t = Math.abs(dz) > 1e-6f ? -prevZ / dz : 0f;
        float yAt = prevY + (s.pos.y - prevY) * t;
        float xAt = prevX + (s.pos.x - prevX) * t;
        if (yAt - PhysicsConfig.BALL_RADIUS >= PhysicsConfig.NET_TOP_Y) return; // cleared
        if (Math.abs(xAt) > PhysicsConfig.TABLE_HALF_WIDTH) return;             // wide of the net

        float travelSign = prevZ < 0f ? 1f : -1f;
        float u = cfg.netForwardKeep
                + (random != null ? (random.nextFloat() * 2f - 1f) * cfg.netJitter : 0f);
        s.pos.x = xAt;
        s.pos.y = yAt;
        s.pos.z = (u >= 0f ? travelSign : -travelSign) * 0.02f;
        s.vel.z *= u;                       // u < 0 flips direction = falls back
        s.vel.x *= cfg.netLateralKeep;
        s.vel.y *= cfg.netVerticalKeep;
        s.spin.scl(cfg.netSpinKeep);
        out.netHit = true;
    }
```

- [ ] **Step 4: Run the full physics test suite**

Run: `./gradlew :sim:test`
Expected: PASS — all of flight, bounce, net, config, fly tests.

- [ ] **Step 5: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/physics/BallPhysics.java \
        sim/src/test/java/io/github/some_example_name/world/physics/BallPhysicsNetTest.java
git commit -m "feat(physics): swept net collider with cord jitter + determinism test"
```

---

### Task 5: Wire BallPhysics into MatchWorld3D (flight/bounce/net swap)

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java` (the big one)
- Modify: `sim/src/main/java/io/github/some_example_name/server/GameServer.java` (net SFX, ~line 202)
- Test: `sim/src/test/java/io/github/some_example_name/world/MatchWorld3DRallyTest.java`

**What changes conceptually:** `ballPos`/`ballVel` become `ball.pos`/`ball.vel` (one `BallState`), the three hand-rolled integrate+bounce blocks become one `stepBall(delta)`, the net fault-line checks are deleted (the net is physical now), and "ball fell below table level" gets correct attribution. Old `HitVelocity` returns and fixed serves stay until Task 6; the BOT freeze + dice stay until Task 7.

- [ ] **Step 1: Write the failing rally test**

```java
package io.github.some_example_name.world;

import com.badlogic.gdx.math.RandomXS128;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end rules wiring over the new physics: a P1 serve must fly, clear the
 * net, bounce once on P2's side and hand the rally to P2 (BOT_RESOLVE), with
 * the bounce reported on the correct side.
 */
class MatchWorld3DRallyTest {

    @Test void p1ServeReachesOpponentAndHandsOverTheRally() {
        MatchWorld3D world = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(3));
        world.setMatchMode(MatchMode.PVP);
        assertTrue(world.tryPlayerServe(null), "P1 should be allowed to serve");
        assertEquals(MatchWorld3D.Phase.OUTGOING, world.getPhase());

        boolean reachedResolve = false;
        for (int i = 0; i < 60 * 6 && !reachedResolve; i++) {
            world.update(1f / 60f);
            reachedResolve = world.getPhase() == MatchWorld3D.Phase.BOT_RESOLVE;
        }
        assertTrue(reachedResolve, "serve should land on P2 side; phase=" + world.getPhase()
            + " status=" + world.getStatusText());
        assertTrue(world.getBallPos().z < 0f, "ball should be on P2's side");
        assertEquals(5, world.getPlayerLives());
        assertEquals(5, world.getP2Lives());
    }

    @Test void ballSpinAccessorExists() {
        MatchWorld3D world = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(3));
        assertNotNull(world.getBallSpin());
    }
}
```

(Note: `tryPlayerServe(null)` — the signature gains a `Ray` parameter in this task; `null` keeps a neutral serve, which Task 6 maps to a center-click serve.)

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :sim:test --tests 'io.github.some_example_name.world.MatchWorld3DRallyTest'`
Expected: COMPILE FAILURE — `tryPlayerServe(Ray)` and `getBallSpin()` don't exist yet.

- [ ] **Step 3: Restructure MatchWorld3D state**

In `MatchWorld3D.java`:

1. Add imports:
```java
import io.github.some_example_name.world.physics.BallPhysics;
import io.github.some_example_name.world.physics.BallState;
import io.github.some_example_name.world.physics.StepContacts;
```
2. Replace the fields `private final Vector3 ballPos = new Vector3();` and `private final Vector3 ballVel = new Vector3();` (lines 48–49) with:
```java
    private final BallState ball = new BallState();
    private final BallPhysics ballPhysics;
    private final StepContacts contacts = new StepContacts();
    private boolean netHitEvent;
```
3. In the constructor (after `this.random = random;`): `ballPhysics = new BallPhysics(config.getPhysics());`
4. Mechanical rename through the whole file: `ballPos` → `ball.pos`, `ballVel` → `ball.vel` (every occurrence; `prevBallPos`, `hitPoint`, `tmpVel` stay as they are).
5. Accessors: `getBallPos()` returns `ball.pos`; `getBallVel()` returns `ball.vel`; add:
```java
    public Vector3 getBallSpin() { return ball.spin; }

    public boolean consumeNetHitEvent() {
        boolean v = netHitEvent;
        netHitEvent = false;
        return v;
    }
```
6. Add the shared step helper (private, next to the update methods):
```java
    /** One physics step + shared bookkeeping. Rules stay in the per-phase updates. */
    private void stepBall(float delta) {
        prevBallPos.set(ball.pos);
        ballPhysics.step(ball, delta, random, contacts);
        if (contacts.netHit) netHitEvent = true;
        if (contacts.tableBounce && Math.abs(ball.vel.y) > 0.8f) {
            tableBounceEvent = true;
            spawnBounceSparks(contacts.bounceX, TABLE_TOP_Y, contacts.bounceZ);
        }
    }
```
7. In `botServe()`, `tryPlayerServe(...)`, `tryClientServe(...)`: zero the spin and reset the integrator when (re)placing the ball — add right after the `ball.pos.set(...)` line:
```java
        ball.spin.setZero();
        ballPhysics.resetAccumulator();
```

- [ ] **Step 4: Rewrite the three phase updates**

Replace `updateIncoming` (currently lines 219–260) with:

```java
    private void updateIncoming(float delta) {
        float prevZ = ball.pos.z;
        stepBall(delta);

        if (!crossedNet && prevZ < 0f && ball.pos.z >= 0f) crossedNet = true;

        if (contacts.tableBounce) {
            if (contacts.bounceZ > 0f && crossedNet) {
                bouncesOnPlayerSide++;
                if (bouncesOnPlayerSide >= 2) { handlePlayerMiss(); return; }
            } else {
                botMissedShot(); // landed on its own side (incl. net fall-back)
                return;
            }
        }

        // Fly collision — ball hits an unswatted fly on P1's side
        if (crossedNet && ball.pos.z > 0f) {
            int hit = ballHitsFly(p1Effects.flies);
            if (hit >= 0) {
                p1Effects.flies.get(hit).alive = false;
                flyKilledIndex = hit;
                flyKilledOwner = 1;
                handlePlayerFlyHit();
                return;
            }
        }

        if (ball.pos.z > TABLE_HALF_LENGTH + 1.5f) { handlePlayerMiss(); return; }
        if (ball.pos.y < TABLE_TOP_Y) {
            // fell below table level: long/wide shot is the bot's fault unless it
            // already bounced legally on P1's side (then P1 let it drop)
            if (bouncesOnPlayerSide == 0) botMissedShot(); else handlePlayerMiss();
        }
    }
```

Replace `updateOutgoing` (currently lines 262–317) with:

```java
    private void updateOutgoing(float delta) {
        float prevZ = ball.pos.z;
        stepBall(delta);

        if (!crossedNet && prevZ > 0f && ball.pos.z <= 0f) crossedNet = true;

        if (contacts.tableBounce) {
            boolean valid = crossedNet && contacts.bounceZ < 0f;
            if (valid) {
                if (matchMode != MatchMode.PVP) {
                    // BOT mode: freeze horizontal movement so the AI can "settle"
                    // the ball. Removed in the BotPlanner task.
                    ball.vel.x = 0f;
                    ball.vel.z = 0f;
                }
                phase = Phase.BOT_RESOLVE;
                phaseTimer = matchMode == MatchMode.PVP
                    ? GameConfig.NET_CLIENT_MISS_TIMEOUT
                    : GameConfig.BOT_RESPONSE_DELAY;
                statusText = matchMode == MatchMode.PVP
                    ? "P2 — return the ball!"
                    : "Clean return. Bot is trying to answer.";
            } else {
                handlePlayerMiss(); // bounced on own side (incl. net fall-back)
            }
            return;
        }

        // Fly collision — ball hits an unswatted fly on P2's side
        if (crossedNet && ball.pos.z < 0f) {
            int hit = ballHitsFly(p2Effects.flies);
            if (hit >= 0) {
                p2Effects.flies.get(hit).alive = false;
                flyKilledIndex = hit;
                flyKilledOwner = 2;
                handleBotFlyHit();
                return;
            }
        }

        if (ball.pos.y < TABLE_TOP_Y
            || ball.pos.z < -TABLE_HALF_LENGTH - 4f
            || Math.abs(ball.pos.x) > TABLE_HALF_WIDTH + 6f) {
            handlePlayerMiss(); // went long/wide or fell — P1's shot failed
        }
    }
```

Replace `updateBotResolve` (currently lines 319–373) with:

```java
    private void updateBotResolve(float delta) {
        stepBall(delta);

        // Settle micro-bounces so the BOT-mode ball comes to rest (legacy
        // behavior until BotPlanner lands).
        if (contacts.tableBounce && Math.abs(ball.vel.y) < 0.5f) {
            ball.vel.y = 0f;
        }

        // PVP: score immediately if the ball leaves the playable area.
        if (matchMode == MatchMode.PVP) {
            if (ball.pos.z < -TABLE_HALF_LENGTH - 2f
                || Math.abs(ball.pos.x) > TABLE_HALF_WIDTH + 4f
                || ball.pos.y < 0f) {
                clientMiss();
                return;
            }
        }

        phaseTimer -= delta;
        if (phaseTimer > 0f) return;

        if (matchMode == MatchMode.PVP) {
            clientMiss();
            return;
        }

        if (random.nextFloat() <= pendingBotReturnChance) {
            currentApproachDuration = Math.max(
                config.getMinimumApproachDuration(),
                currentApproachDuration - config.getApproachDurationDecay()
            );
            botReturn();
        } else {
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
    }
```

Also delete the now-unused `BOUNCE_RESTITUTION` constant (line 36).

- [ ] **Step 5: Serve signatures gain the ray (still neutral serves)**

Change `tryPlayerServe()` (line 512) to `tryPlayerServe(Ray pickRay)` and `tryClientServe()` (line 566) to `tryClientServe(Ray pickRay)` — the parameter is unused until Task 6. Update the two call sites:
- `handlePlayerClick`: `return tryPlayerServe(pickRay);`
- `handleOpponentClick`: `return tryClientServe(pickRay);`

- [ ] **Step 6: Net SFX on the server**

In `GameServer.java`, next to the existing event pumps (after the `consumeTableBounceEvent` line ~202):

```java
            if (w.consumeNetHitEvent())      sendSfxToAll(PacketType.SFX_TABLE);
```

- [ ] **Step 7: Run all sim tests**

Run: `./gradlew :sim:test`
Expected: PASS, including the new `MatchWorld3DRallyTest`. If the serve fails to reach BOT_RESOLVE, the fixed serve velocity `(0, 5, −10)` vs gravity 9.79 is the suspect — check `crossedNet` logic before touching constants (the defaults were chosen to reproduce the old arc).

- [ ] **Step 8: Manual feel check**

Run: `./gradlew lwjgl3:run` → VS BOT. Verify: serve/rally arcs feel like before, bounces are livelier (e = 0.92), the ball visibly slows over long flights (drag), clipping the net now makes the ball dribble or fall back instead of insta-scoring. Play 2–3 points each way.

- [ ] **Step 9: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java \
        sim/src/main/java/io/github/some_example_name/server/GameServer.java \
        sim/src/test/java/io/github/some_example_name/world/MatchWorld3DRallyTest.java
git commit -m "feat(physics): MatchWorld3D delegates all ball motion to BallPhysics; net goes physical"
```

---

### Task 6: PaddleContact — off-center clicks become aim + pace + spin

**Files:**
- Create: `sim/src/main/java/io/github/some_example_name/world/physics/PaddleContact.java`
- Delete: `sim/src/main/java/io/github/some_example_name/world/HitVelocity.java`
- Modify: `sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java` (tryHitBall, handleOpponentClick, both serves)
- Test: `sim/src/test/java/io/github/some_example_name/world/physics/PaddleContactTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package io.github.some_example_name.world.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The off-center mapping: top = topspin, bottom = backspin, sides = aim +
 * sidespin (+ corkscrew tilt for the bounce kick), pace carries from the
 * incoming ball, incoming spin transfers reversed, clamps always hold.
 * The landing property tests double as the tuning harness.
 */
class PaddleContactTest {

    private static final PhysicsConfig CFG = PhysicsConfig.createDefault();

    private static BallState incomingForP1() {
        BallState s = new BallState();
        s.pos.set(0f, 3f, 4f);
        s.vel.set(0f, -2f, 7f); // arriving toward P1 (+z)
        return s;
    }

    @Test void topClickGivesTopspinAndFlatterArc() {
        BallState top = incomingForP1();
        PaddleContact.applyReturn(top, CFG, 0f, 0.8f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        BallState bottom = incomingForP1();
        PaddleContact.applyReturn(bottom, CFG, 0f, -0.8f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        assertTrue(top.spin.x < -1f, "P1 topspin (−z travel) needs ωx < 0, got " + top.spin.x);
        assertTrue(bottom.spin.x > 1f, "backspin flips the sign");
        assertTrue(top.vel.y < bottom.vel.y, "top click must fly flatter than bottom click");
    }

    @Test void sideClickAimsCurvesAndTiltsTheBounceKick() {
        BallState s = incomingForP1();
        PaddleContact.applyReturn(s, CFG, 0.8f, 0f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        assertTrue(s.vel.x > 0.5f, "click right of centre aims right");
        assertTrue(s.spin.y < -1f, "P1 sidespin (−z travel) needs ωy < 0 to keep curving right");
        assertTrue(s.spin.z < -1f, "corkscrew tilt kicks the bounce outward");
    }

    @Test void mirroredForP2() {
        BallState s = new BallState();
        s.pos.set(0f, 3f, -4f);
        s.vel.set(0f, -2f, -7f);
        PaddleContact.applyReturn(s, CFG, 0.8f, 0.8f, 1f, 1f, false, CFG.basePaceSI, CFG.baseArcSI);
        assertTrue(s.vel.z > 0f, "P2 returns toward +z");
        assertTrue(s.spin.x > 1f, "P2 topspin (+z travel) needs ωx > 0");
        assertTrue(s.spin.y > 1f, "P2 sidespin sign mirrors");
    }

    @Test void fasterIncomingComesBackFaster() {
        BallState slow = incomingForP1();
        slow.vel.z = 6f;
        BallState fast = incomingForP1();
        fast.vel.z = 12f;
        PaddleContact.applyReturn(slow, CFG, 0f, 0f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        PaddleContact.applyReturn(fast, CFG, 0f, 0f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        float expected = CFG.paceCarry * 6f;
        assertEquals(expected, Math.abs(fast.vel.z) - Math.abs(slow.vel.z), 0.05f);
    }

    @Test void incomingSpinTransfersReversed() {
        BallState s = incomingForP1();
        s.spin.set(0f, 30f, 0f);
        PaddleContact.applyReturn(s, CFG, 0f, 0f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        assertEquals(CFG.spinTransfer * 30f, s.spin.y, 0.5f);
    }

    @Test void clampsHoldUnderStackedMultipliers() {
        BallState s = incomingForP1();
        s.vel.z = 40f;
        PaddleContact.applyReturn(s, CFG, 1f, 1f, 10f, 10f, true, CFG.basePaceSI, CFG.baseArcSI);
        assertTrue(s.vel.len() <= CFG.maxSpeedW() + 1e-3f);
        assertTrue(s.spin.len() <= CFG.maxSpinW() + 1e-3f);
    }

    /** Property: center-ish return clicks land on the opponent's half ≥ 95%. */
    @Test void centerishReturnsLandOnOpponentHalf() {
        int total = 0, landed = 0;
        for (float ndx = -0.4f; ndx <= 0.41f; ndx += 0.2f) {
            for (float ndy = -0.4f; ndy <= 0.41f; ndy += 0.2f) {
                for (float vin = 5f; vin <= 11f; vin += 3f) {
                    for (float x0 = -2f; x0 <= 2.1f; x0 += 2f) {
                        total++;
                        BallState s = new BallState();
                        s.pos.set(x0, 2.8f, 4.5f);
                        s.vel.set(0f, -2f, vin);
                        PaddleContact.applyReturn(s, CFG, ndx, ndy, 1f, 1f, true,
                                                  CFG.basePaceSI, CFG.baseArcSI);
                        if (landsOnOpponentHalf(s)) landed++;
                    }
                }
            }
        }
        assertTrue(landed / (float) total >= 0.95f,
            "legal-landing rate too low: " + landed + "/" + total);
    }

    /** Property: serves from any clamped click offset land legally ≥ 95%. */
    @Test void servesLandOnOpponentHalf() {
        int total = 0, landed = 0;
        for (float ndx = -1f; ndx <= 1.01f; ndx += 0.4f) {
            for (float ndy = -1f; ndy <= 1.01f; ndy += 0.4f) {
                total++;
                BallState s = new BallState();
                s.pos.set(0f, PhysicsConfig.TABLE_TOP_Y + 1.2f, PhysicsConfig.TABLE_HALF_LENGTH - 0.5f);
                PaddleContact.applyReturn(s, CFG, ndx * CFG.serveControl, ndy * CFG.serveControl,
                                          1f, 1f, true, CFG.servePaceSI, CFG.serveArcSI);
                if (landsOnOpponentHalf(s)) landed++;
            }
        }
        assertTrue(landed / (float) total >= 0.95f,
            "serve legality too low: " + landed + "/" + total);
    }

    private static boolean landsOnOpponentHalf(BallState s) {
        BallPhysics phys = new BallPhysics(CFG);
        StepContacts c = new StepContacts();
        for (int i = 0; i < 60 * 4; i++) {
            phys.step(s, 1f / 60f, null, c);
            if (c.netHit) return false;
            if (c.tableBounce) return c.bounceZ < 0f;
            if (s.pos.y < 0f) return false;
        }
        return false;
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :sim:test --tests 'io.github.some_example_name.world.physics.PaddleContactTest'`
Expected: COMPILE FAILURE — `PaddleContact` missing.

- [ ] **Step 3: Create PaddleContact**

```java
package io.github.some_example_name.world.physics;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

/**
 * Click-to-return contact model: where the click ray meets the (padded) ball
 * decides aim, pace and spin. Replaces the old HitVelocity recipe. The same
 * mapping serves player returns, serves, and bot returns (offsets chosen
 * directly by {@link BotPlanner}).
 *
 * <p>Sign conventions (world axes): for travel toward −z (P1 returning),
 * topspin is ωx &lt; 0; toward +z it is ωx &gt; 0 — handled by the travel
 * sign. Sidespin ωy is signed so the Magnus curve continues toward the aimed
 * side; the corkscrew tilt ωz adds a same-direction kick at the bounce.</p>
 */
public final class PaddleContact {

    /** Generous ray-vs-ball padding so clicks near the ball still count. */
    public static final float CLICK_HIT_PADDING = 3.5f;

    /** Scratch — the server loop and tests are single-threaded. */
    private static final Vector3 tmp = new Vector3();

    private PaddleContact() {}

    /** Effective click radius (base × stat/item multipliers × padding). */
    public static float hitRadius(float scaleMultiplier) {
        return PhysicsConfig.BALL_RADIUS * scaleMultiplier * CLICK_HIT_PADDING;
    }

    /**
     * Ray-sphere return. Returns false if the ray misses the padded ball;
     * on success overwrites {@code ball.vel} and {@code ball.spin}.
     */
    public static boolean returnFromRay(Ray ray, BallState ball, PhysicsConfig cfg,
                                        float scaleMultiplier, float powerMultiplier,
                                        float paceMultiplier, boolean towardNegativeZ) {
        float radius = hitRadius(scaleMultiplier);
        if (!Intersector.intersectRaySphere(ray, ball.pos, radius, tmp)) return false;
        float ndx = MathUtils.clamp((tmp.x - ball.pos.x) / radius, -1f, 1f);
        float ndy = MathUtils.clamp((tmp.y - ball.pos.y) / radius, -1f, 1f);
        applyReturn(ball, cfg, ndx, ndy, powerMultiplier, paceMultiplier, towardNegativeZ,
                    cfg.basePaceSI, cfg.baseArcSI);
        return true;
    }

    /**
     * The shared offset→velocity+spin mapping. {@code ndx}/{@code ndy} ∈ [−1, 1]:
     * where the paddle brushed the ball (+ndy = above centre = topspin).
     */
    public static void applyReturn(BallState ball, PhysicsConfig cfg,
                                   float ndx, float ndy,
                                   float powerMultiplier, float paceMultiplier,
                                   boolean towardNegativeZ,
                                   float paceSI, float arcSI) {
        float travelSign = towardNegativeZ ? -1f : 1f;
        float offset = Math.min((float) Math.sqrt(ndx * ndx + ndy * ndy), 1f);

        float carry = cfg.paceCarry * Math.abs(ball.vel.z);
        float pace = (cfg.velW(paceSI) * powerMultiplier * (1f + cfg.paceOffsetGain * offset)
                      + carry) * paceMultiplier;

        float vx = cfg.velW(cfg.aimGainSI) * ndx;
        float vy = cfg.velW(arcSI) * (1f - 0.35f * ndy);

        float spinX = travelSign * cfg.spinW(cfg.topspinGainSI) * ndy;
        float spinY = travelSign * cfg.spinW(cfg.sidespinGainSI) * ndx;
        float spinZ = -cfg.spinW(cfg.sidespinGainSI) * cfg.corkscrewTilt * ndx;

        ball.spin.scl(cfg.spinTransfer); // reversed fraction of the incoming spin
        ball.spin.add(spinX, spinY, spinZ);
        ball.vel.set(vx, vy, travelSign * pace);
        clamp(ball, cfg);
    }

    /**
     * Serve variant — never fails. The click's nearest-approach offset to the
     * ball (clamped to the unit disc, damped by serveControl) shapes the serve,
     * so "click anywhere to serve" still works while aimed clicks matter.
     * Pass a null ray for a neutral centre serve.
     */
    public static void serveFromRay(Ray ray, BallState ball, PhysicsConfig cfg,
                                    float paceMultiplier, boolean towardNegativeZ) {
        float radius = hitRadius(1f);
        float ndx = 0f, ndy = 0f;
        if (ray != null) {
            tmp.set(ball.pos).sub(ray.origin);
            float t = Math.max(0f, tmp.dot(ray.direction));
            tmp.set(ray.direction).scl(t).add(ray.origin).sub(ball.pos);
            ndx = MathUtils.clamp(tmp.x / radius, -1f, 1f) * cfg.serveControl;
            ndy = MathUtils.clamp(tmp.y / radius, -1f, 1f) * cfg.serveControl;
        }
        ball.vel.setZero();  // serves start from rest: no carry
        ball.spin.setZero(); // and no spin transfer
        applyReturn(ball, cfg, ndx, ndy, 1f, paceMultiplier, towardNegativeZ,
                    cfg.servePaceSI, cfg.serveArcSI);
    }

    /** Hard caps after all multipliers — the anti-cheat envelope. */
    public static void clamp(BallState ball, PhysicsConfig cfg) {
        ball.vel.clamp(0f, cfg.maxSpeedW());
        ball.spin.clamp(0f, cfg.maxSpinW());
    }
}
```

- [ ] **Step 4: Run the PaddleContact tests, tune if the property tests fail**

Run: `./gradlew :sim:test --tests 'io.github.some_example_name.world.physics.PaddleContactTest'`
Expected: PASS. If `centerishReturnsLandOnOpponentHalf` or `servesLandOnOpponentHalf` is below 95%, tune in this order and re-run: `baseArcSI` ±0.2 (net clips → raise; long → lower), `basePaceSI`/`servePaceSI` ∓0.3, `aimGainSI` −0.2. Do not weaken the assertions.

- [ ] **Step 5: Swap MatchWorld3D onto PaddleContact**

In `MatchWorld3D.java`:

1. Remove the `HitVelocity` import; add `io.github.some_example_name.world.physics.PaddleContact`.
2. Replace `tryHitBall` (lines 463–483) with:

```java
    /** Returns true if the click hit the incoming ball and triggered a return. */
    public boolean tryHitBall(Ray pickRay) {
        if (phase != Phase.INCOMING) return false;
        if (ball.pos.z < 0f || ball.pos.z > TABLE_HALF_LENGTH + 1.5f) return false;
        float scale = player.getTargetScaleMultiplier() * p1Effects.hitScaleMultiplier();
        float hitRadius = PaddleContact.hitRadius(scale);
        if (!Intersector.intersectRaySphere(pickRay, ball.pos, hitRadius, hitPoint)) return false;

        float ndx = MathUtils.clamp((hitPoint.x - ball.pos.x) / hitRadius, -1f, 1f);
        float ndy = MathUtils.clamp((hitPoint.y - ball.pos.y) / hitRadius, -1f, 1f);
        lastClickAccuracy = 1f - MathUtils.clamp(
            (float) Math.sqrt(ndx * ndx + ndy * ndy), 0f, 1f);
        PaddleContact.applyReturn(ball, config.getPhysics(), ndx, ndy,
            player.getReturnPowerMultiplier(), p2Effects.incomingSpeedMultiplier(), true,
            config.getPhysics().basePaceSI, config.getPhysics().baseArcSI);

        pendingBotReturnChance = computeBotReturnChance();
        crossedNet = false;
        phase = Phase.OUTGOING;
        statusText = "Clean return. Ball is travelling back to the bot.";
        paddleHitEvent = true;
        return true;
    }
```
(`Intersector` and `MathUtils` are already imported; `lastClickAccuracy` and the chance roll die in Task 7.)
3. In `handleOpponentClick` (lines 542–560), replace the `HitVelocity.computeFromRay` block with:

```java
        if (!isClientCanHit()) return false;
        if (!PaddleContact.returnFromRay(pickRay, ball, config.getPhysics(),
                bot.getTargetScaleMultiplier() * p2Effects.hitScaleMultiplier(),
                bot.getReturnPowerMultiplier(), p1Effects.incomingSpeedMultiplier(), false)) {
            return false;
        }
```
4. In `tryPlayerServe(Ray pickRay)`, replace the `ballVel.set(...)` line with:

```java
        PaddleContact.serveFromRay(pickRay, ball, config.getPhysics(),
            p2Effects.incomingSpeedMultiplier(), true);
```
5. In `tryClientServe(Ray pickRay)`, replace its `ballVel.set(...)` line with:

```java
        PaddleContact.serveFromRay(pickRay, ball, config.getPhysics(),
            p1Effects.incomingSpeedMultiplier(), false);
```
6. Delete `sim/src/main/java/io/github/some_example_name/world/HitVelocity.java`.

- [ ] **Step 6: Run all sim tests + feel check**

Run: `./gradlew :sim:test`
Expected: PASS (rally test still green — neutral `null`-ray serve goes through `serveFromRay`).
Run: `./gradlew lwjgl3:run` → VS BOT: click top of ball → flat dipping drive that kicks on bounce; bottom → floaty chop that dies; edges → visible curve. Serve placement follows the click.

- [ ] **Step 7: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/physics/PaddleContact.java \
        sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java \
        sim/src/test/java/io/github/some_example_name/world/physics/PaddleContactTest.java
git rm sim/src/main/java/io/github/some_example_name/world/HitVelocity.java
git commit -m "feat(physics): PaddleContact off-center mapping replaces HitVelocity; serves aimable"
```

---

### Task 7: BotPlanner — prediction bot with Gaussian aim error

**Files:**
- Create: `sim/src/main/java/io/github/some_example_name/world/physics/BotPlanner.java`
- Modify: `sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java` (delete dice/freeze, wire planner)
- Test: `sim/src/test/java/io/github/some_example_name/world/physics/BotPlannerTest.java`

- [ ] **Step 1: Write the failing tests**

```java
package io.github.some_example_name.world.physics;

import com.badlogic.gdx.math.RandomXS128;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The bot is the paper's robot: it forward-simulates the incoming ball with
 * the same integrator and aims with Gaussian error. Difficulty = error size.
 * The Monte-Carlo test pins the default return rate to today's ~73%.
 */
class BotPlannerTest {

    private static BallState playerShot(RandomXS128 random, PhysicsConfig cfg) {
        BallState ball = new BallState();
        ball.pos.set((random.nextFloat() - 0.5f) * 4f,
                     2.6f + random.nextFloat(),
                     3f + random.nextFloat() * 3f);
        ball.vel.set(0f, -2f, 7f);
        PaddleContact.applyReturn(ball, cfg,
            (random.nextFloat() - 0.5f) * 1.2f, (random.nextFloat() - 0.5f) * 1.2f,
            1f, 1f, true, cfg.basePaceSI, cfg.baseArcSI);
        return ball;
    }

    @Test void planTargetsAMomentAfterTheBounceOnBotSide() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BotPlanner planner = new BotPlanner(cfg);
        BotPlanner.Plan plan = new BotPlanner.Plan();
        RandomXS128 random = new RandomXS128(11);
        BallState ball = null;
        // find a seed-stable shot that lands on the bot side
        for (int i = 0; i < 20; i++) {
            ball = playerShot(random, cfg);
            planner.plan(ball, new BotPlanner.Profile(), random, plan);
            if (plan.strikeTime >= 0f) break;
        }
        assertTrue(plan.strikeTime >= 0f, "should find a landing shot in 20 tries");
        assertTrue(plan.strikeTime >= new BotPlanner.Profile().reactionDelay - 1e-4f);
        assertTrue(plan.strikeTime < 6f);
    }

    @Test void hugeErrorAlwaysWhiffs() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BotPlanner planner = new BotPlanner(cfg);
        BotPlanner.Profile blind = new BotPlanner.Profile();
        blind.aimSigma = 50f;
        BotPlanner.Plan plan = new BotPlanner.Plan();
        RandomXS128 random = new RandomXS128(5);
        int whiffs = 0, planned = 0;
        for (int i = 0; i < 50; i++) {
            BallState ball = playerShot(random, cfg);
            planner.plan(ball, blind, random, plan);
            if (plan.strikeTime < 0f) continue;
            planned++;
            if (plan.whiff) whiffs++;
        }
        assertTrue(planned > 0);
        assertEquals(planned, whiffs, "σ=50 must whiff every time");
    }

    @Test void defaultProfileReturnsAboutLikeToday() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BotPlanner planner = new BotPlanner(cfg);
        BotPlanner.Profile profile = new BotPlanner.Profile();
        BotPlanner.Plan plan = new BotPlanner.Plan();
        RandomXS128 random = new RandomXS128(42);
        int swings = 0, hits = 0;
        for (int i = 0; i < 1000; i++) {
            BallState ball = playerShot(random, cfg);
            planner.plan(ball, profile, random, plan);
            if (plan.strikeTime < 0f) continue; // shot misses the table: not the bot's problem
            swings++;
            if (!plan.whiff) hits++;
        }
        assertTrue(swings > 400, "test shots should mostly land; got " + swings);
        float rate = hits / (float) swings;
        assertTrue(rate > 0.68f && rate < 0.78f,
            "default bot return rate drifted from ~0.73: " + rate);
    }

    @Test void harderProfileMissesMore() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BotPlanner planner = new BotPlanner(cfg);
        BotPlanner.Profile easy = new BotPlanner.Profile();
        BotPlanner.Profile hard = new BotPlanner.Profile();
        hard.aimSigma = easy.aimSigma * 2f;
        BotPlanner.Plan plan = new BotPlanner.Plan();
        int easyHits = 0, hardHits = 0;
        RandomXS128 r1 = new RandomXS128(42), r2 = new RandomXS128(42);
        for (int i = 0; i < 500; i++) {
            BallState b1 = playerShot(r1, cfg);
            planner.plan(b1, easy, r1, plan);
            if (plan.strikeTime >= 0f && !plan.whiff) easyHits++;
            BallState b2 = playerShot(r2, cfg);
            planner.plan(b2, hard, r2, plan);
            if (plan.strikeTime >= 0f && !plan.whiff) hardHits++;
        }
        assertTrue(hardHits < easyHits, "bigger σ must mean fewer returns");
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `./gradlew :sim:test --tests 'io.github.some_example_name.world.physics.BotPlannerTest'`
Expected: COMPILE FAILURE — `BotPlanner` missing.

- [ ] **Step 3: Create BotPlanner**

```java
package io.github.some_example_name.world.physics;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;

/**
 * Prediction-based bot, after the paper's robot: forward-simulate the incoming
 * ball with the same integrator, pick the strike moment (post-bounce apex on
 * the bot's side), then aim with Gaussian error. Geometric outcome: if the
 * erred contact offset leaves the unit disc, the bot whiffs. Difficulty is σ.
 */
public final class BotPlanner {

    /** Difficulty knobs. σ is in contact-offset units (1 = padded radius). */
    public static final class Profile {
        /** Calibrated so the default return rate ≈ today's 0.73 (see BotPlannerTest). */
        public float aimSigma = 0.34f;
        /** Minimum seconds between the player's hit and the bot's swing. */
        public float reactionDelay = 0.55f;
        /** 0..1 topspin/pace bias of the bot's intended contact. */
        public float aggression = 0.35f;
    }

    /** One planned bot action, produced when the player hits. */
    public static final class Plan {
        /** Seconds after planning when the bot swings; −1 = no swing (ball won't land). */
        public float strikeTime = -1f;
        public boolean whiff;
        /** Contact offsets for {@link PaddleContact#applyReturn} when not a whiff. */
        public float ndx, ndy;
    }

    /** How long the bot is willing to look into the future. */
    private static final float HORIZON_S = 6f;

    private final PhysicsConfig cfg;
    private final BallPhysics scratchPhysics;
    private final BallState scratch = new BallState();
    private final StepContacts contacts = new StepContacts();

    public BotPlanner(PhysicsConfig cfg) {
        this.cfg = cfg;
        this.scratchPhysics = new BallPhysics(cfg);
    }

    /**
     * Simulates {@code current} forward (without mutating it) and fills
     * {@code plan}. Call once, right when the player's return starts.
     */
    public void plan(BallState current, Profile profile, RandomXS128 random, Plan plan) {
        plan.strikeTime = -1f;
        plan.whiff = false;
        scratch.set(current);
        scratchPhysics.resetAccumulator();

        float t = 0f;
        boolean bounced = false;
        float strikeAt = -1f;
        int steps = (int) (HORIZON_S / BallPhysics.SUBSTEP_DT);
        for (int i = 0; i < steps; i++) {
            float prevVy = scratch.vel.y;
            scratchPhysics.step(scratch, BallPhysics.SUBSTEP_DT, null, contacts);
            t += BallPhysics.SUBSTEP_DT;
            if (contacts.netHit) return;                      // net will decide; no swing
            if (!bounced && contacts.tableBounce) {
                if (contacts.bounceZ >= 0f) return;           // not on the bot's side
                bounced = true;
                continue;
            }
            if (bounced) {
                boolean apex = prevVy > 0f && scratch.vel.y <= 0f;
                boolean dropping = scratch.pos.y < PhysicsConfig.TABLE_TOP_Y + 0.3f;
                if (apex || dropping || contacts.tableBounce) {
                    strikeAt = t;
                    break;
                }
            }
            if (scratch.pos.y < 0f) return;                   // fell out before landing
        }
        if (strikeAt < 0f) return;

        plan.strikeTime = Math.max(strikeAt, profile.reactionDelay);

        // intended contact: slight lateral variety, slight topspin by aggression
        float intendedX = (random.nextFloat() - 0.5f) * 0.5f;
        float intendedY = profile.aggression * 0.5f;
        plan.ndx = intendedX + (float) random.nextGaussian() * profile.aimSigma;
        plan.ndy = intendedY + (float) random.nextGaussian() * profile.aimSigma;
        plan.whiff = plan.ndx * plan.ndx + plan.ndy * plan.ndy > 1f;
        if (!plan.whiff) {
            plan.ndx = MathUtils.clamp(plan.ndx, -1f, 1f);
            plan.ndy = MathUtils.clamp(plan.ndy, -1f, 1f);
        }
    }
}
```

- [ ] **Step 4: Run and calibrate σ**

Run: `./gradlew :sim:test --tests 'io.github.some_example_name.world.physics.BotPlannerTest'`
Expected: PASS. Two calibration loops, in order:
- If the `swings > 400` assertion fails, the *shot generator* is at fault (too many test shots miss the table) — narrow the random offset range in `playerShot` from `±1.2` toward `±0.8` until most shots land. Don't touch the planner for this.
- If `defaultProfileReturnsAboutLikeToday` fails, adjust `Profile.aimSigma` (bigger σ → lower rate; the relationship is monotone) until the rate lands in 0.68–0.78, then re-run all four tests. Record the final σ in the Profile Javadoc.

- [ ] **Step 5: Wire into MatchWorld3D, delete dice + freeze**

In `MatchWorld3D.java`:

1. Imports: add `io.github.some_example_name.world.physics.BotPlanner`.
2. Fields — delete `pendingBotReturnChance`, `lastClickAccuracy`, `currentApproachDuration`; add:
```java
    private final BotPlanner botPlanner;
    private final BotPlanner.Profile botProfile = new BotPlanner.Profile();
    private final BotPlanner.Plan botPlan = new BotPlanner.Plan();
    private float botPlanClock;
    private boolean botPlanArmed;
    private float rallySpeedup; // 0..1, grows each successful bot return
```
   In the constructor delete the `currentApproachDuration = ...` line and add `botPlanner = new BotPlanner(config.getPhysics());`.
3. Delete methods: `applyBotImpulse()`, `computeBotReturnChance()`, `botReturn()`.
4. Add the rally pace helper:
```java
    /** Grows toward 1 as the rally heats up; reuses the legacy approach-duration keys. */
    private float rallyPaceMultiplier() {
        return 1f + 0.6f * rallySpeedup;
    }
```
5. In `tryHitBall`, delete the `lastClickAccuracy` and `pendingBotReturnChance` lines, and after `paddleHitEvent = true;` add:
```java
        if (matchMode != MatchMode.PVP) {
            botPlanner.plan(ball, botProfile, random, botPlan);
            botPlanClock = 0f;
            botPlanArmed = true;
        }
```
   In the same method, the `applyReturn` call's pace multiplier becomes `rallyPaceMultiplier() * p2Effects.incomingSpeedMultiplier()`.
6. In `updateOutgoing`, remove the BOT-mode freeze block (`ball.vel.x = 0f; ball.vel.z = 0f;`) and the `phaseTimer = ... BOT_RESPONSE_DELAY` distinction:
```java
                phase = Phase.BOT_RESOLVE;
                phaseTimer = matchMode == MatchMode.PVP
                    ? GameConfig.NET_CLIENT_MISS_TIMEOUT
                    : GameConfig.NET_CLIENT_MISS_TIMEOUT; // BOT: planner decides; timer is a safety net
```
   (Keep one branchless assignment: `phaseTimer = GameConfig.NET_CLIENT_MISS_TIMEOUT;`.)
7. Replace the BOT half of `updateBotResolve` (everything from `phaseTimer -= delta;` down) with:
```java
        phaseTimer -= delta;

        if (matchMode == MatchMode.PVP) {
            if (phaseTimer <= 0f) clientMiss();
            return;
        }

        // BOT mode: the planner committed to a swing time at the player's hit.
        botPlanClock += delta;
        if (!botPlanArmed) {
            if (phaseTimer <= 0f) botMissedShot(); // safety net: plan never armed
            return;
        }
        if (botPlanClock < botPlan.strikeTime) return;
        botPlanArmed = false;
        if (botPlan.whiff || botPlan.strikeTime < 0f) {
            botMissedShot();
            return;
        }
        rallySpeedup = Math.min(1f, rallySpeedup
            + config.getApproachDurationDecay() / Math.max(0.001f,
              config.getInitialApproachDuration() - config.getMinimumApproachDuration()));
        PaddleContact.applyReturn(ball, config.getPhysics(), botPlan.ndx, botPlan.ndy,
            bot.getReturnPowerMultiplier(),
            rallyPaceMultiplier() * p1Effects.incomingSpeedMultiplier()
                * (1f / Math.max(0.5f, player.getIncomingTimeMultiplier())),
            false, config.getPhysics().basePaceSI, config.getPhysics().baseArcSI);
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.INCOMING;
        statusText = "Bot gets it back. Click the ball as it comes through.";
        paddleHitEvent = true;
```
   Also remove the now-dead settle block (`if (contacts.tableBounce && Math.abs(ball.vel.y) < 0.5f)`) — the ball stays live.
8. `botServe()` — replace the `applyBotImpulse()` call with a planner-flavoured serve:
```java
        ball.spin.setZero();
        ballPhysics.resetAccumulator();
        float ndx = (random.nextFloat() - 0.5f) * 0.6f
                  + (float) random.nextGaussian() * botProfile.aimSigma * 0.5f;
        float ndy = botProfile.aggression * 0.4f;
        PaddleContact.applyReturn(ball, config.getPhysics(),
            MathUtils.clamp(ndx, -1f, 1f), MathUtils.clamp(ndy, -1f, 1f),
            1f, p1Effects.incomingSpeedMultiplier()
                * (1f / Math.max(0.5f, player.getIncomingTimeMultiplier())),
            false, config.getPhysics().servePaceSI, config.getPhysics().serveArcSI);
        paddleHitEvent = true;
```
   (`botServe` keeps its existing ball placement lines and phase/status bookkeeping.)
9. In `prepareServe(...)` disarm any stale plan:
```java
        botPlanArmed = false;
```
   (`rallySpeedup` deliberately persists across points — the old approach-duration ramp grew over the whole round, and resetting per point would soften the difficulty curve.)

- [ ] **Step 6: Run everything + feel check**

Run: `./gradlew :sim:test`
Expected: PASS — including `MatchWorld3DRallyTest` (PVP path untouched by the planner).
Run: `./gradlew lwjgl3:run` → VS BOT: the ball never freezes mid-table; the bot visibly swings at the post-bounce apex; heavy spin/edge shots make it whiff more; its returns vary (sometimes with spin). Confirm a full match is winnable and losable.

- [ ] **Step 7: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/physics/BotPlanner.java \
        sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java \
        sim/src/test/java/io/github/some_example_name/world/physics/BotPlannerTest.java
git commit -m "feat(physics): prediction-based BotPlanner replaces dice roll and freeze hack"
```

---

### Task 8: STATE carries spin; client extrapolates with the shared integrator

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/network/PacketType.java:20-28` (STATE doc)
- Modify: `sim/src/main/java/io/github/some_example_name/network/GameConnection.java` (Listener.onState ~line 42, readLoop STATE case ~line 172, sendState ~line 268)
- Modify: `sim/src/main/java/io/github/some_example_name/server/GameServer.java:238-255` (broadcastState)
- Modify: `core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java` (fields ~42-44, updateSimulation ~240-247, onState ~387-407, delete extrapolateBallY ~285-303)
- Modify: `sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java` (delete the now-unused `GRAVITY` constant)

There is no headless test for the wire change (it's I/O plumbing); the protection is that client and server compile against the same `GameConnection` signatures in one build, plus the LAN smoke test.

- [ ] **Step 1: Extend the protocol**

`PacketType.java` — update the STATE Javadoc payload list to:

```java
    /**
     * Full game-state snapshot at 30 Hz.
     * Payload:
     * {@code float px, py, pz} (ball position),
     * {@code float vx, vy, vz} (ball velocity),
     * {@code float sx, sy, sz} (ball spin, world angular velocity),
     * {@code int p1lives, p2lives},
     * {@code byte ballVisible} (0/1),
     * {@code byte activePlayer} (0 = nobody, 1 = P1, 2 = P2).
     */
```

`GameConnection.java`:
- `Listener.onState` becomes:
```java
        default void onState(float px, float py, float pz,
                             float vx, float vy, float vz,
                             float sx, float sy, float sz,
                             int p1lives, int p2lives,
                             boolean ballVisible, int activePlayer)                {}
```
- The `readLoop` STATE case reads three more floats after the velocity and passes them through:
```java
                    case PacketType.STATE -> {
                        float px = in.readFloat(), py = in.readFloat(), pz = in.readFloat();
                        float vx = in.readFloat(), vy = in.readFloat(), vz = in.readFloat();
                        float sx = in.readFloat(), sy = in.readFloat(), sz = in.readFloat();
                        int   p1l = in.readInt(),  p2l = in.readInt();
                        boolean bv = in.readByte() != 0;
                        int   ap  = in.readByte() & 0xFF;
                        dispatch.execute(
                            () -> listener.onState(px, py, pz, vx, vy, vz, sx, sy, sz,
                                                   p1l, p2l, bv, ap));
                    }
```
- `sendState` gains `float sx, float sy, float sz` after the velocity params and writes them in the same order:
```java
    public void sendState(float px, float py, float pz,
                          float vx, float vy, float vz,
                          float sx, float sy, float sz,
                          int p1lives, int p2lives,
                          boolean ballVisible, int activePlayer) {
        write(() -> {
            out.writeByte(PacketType.STATE);
            out.writeFloat(px); out.writeFloat(py); out.writeFloat(pz);
            out.writeFloat(vx); out.writeFloat(vy); out.writeFloat(vz);
            out.writeFloat(sx); out.writeFloat(sy); out.writeFloat(sz);
            out.writeInt(p1lives);
            out.writeInt(p2lives);
            out.writeByte(ballVisible ? 1 : 0);
            out.writeByte(activePlayer);
        });
    }
```

`GameServer.broadcastState` passes the spin:

```java
    private void broadcastState(MatchWorld3D w) {
        GameConnection c1 = p1, c2 = p2;   // capture before shutdown() can null them
        Vector3 pos = w.getBallPos();
        Vector3 vel = w.getBallVel();
        Vector3 spin = w.getBallSpin();
        boolean vis = w.isBallVisible();
        int ap = w.getActivePlayer();
        int p1l = w.getPlayerLives();
        int p2l = w.getP2Lives();
        if (c1 != null) {
            c1.sendState(pos.x, pos.y, pos.z, vel.x, vel.y, vel.z,
                         spin.x, spin.y, spin.z, p1l, p2l, vis, ap);
        }
        if (c2 != null) {
            c2.sendState(pos.x, pos.y, pos.z, vel.x, vel.y, vel.z,
                         spin.x, spin.y, spin.z, p1l, p2l, vis, ap);
        }
    }
```

- [ ] **Step 2: Swap the client extrapolation**

`NetMatchScreen.java`:
- Imports: add `io.github.some_example_name.world.physics.BallPhysics`, `...BallState`, `...PhysicsConfig`, `...StepContacts`.
- Next to `snapBallPos`/`snapBallVel` (lines 42-44) add:
```java
    private final Vector3 snapBallSpin = new Vector3();
    private final BallPhysics clientPhysics = new BallPhysics(PhysicsConfig.createDefault());
    private final BallState extrapState = new BallState();
    private final StepContacts extrapContacts = new StepContacts();
```
- In `updateSimulation` replace the extrapolation block (lines 242-248) with:
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
- `onState` (line 387): extend the signature with `float sx, float sy, float sz` after the velocity and add `snapBallSpin.set(sx, sy, sz);` next to the existing snapshot assignments.
- Delete the whole `extrapolateBallY` method (lines 285-303).

- [ ] **Step 3: Remove the dead legacy constant**

In `MatchWorld3D.java` delete `public static final float GRAVITY = 9.8f;` then verify nothing references it:

Run: `grep -rn "MatchWorld3D.GRAVITY\|extrapolateBallY" sim core`
Expected: no matches.

- [ ] **Step 4: Full build + LAN smoke test**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL (all modules compile, sim tests pass).

Manual: start two instances (`./gradlew lwjgl3:run` twice). Instance A: MULTIPLAYER → HOST. Instance B: MULTIPLAYER → JOIN with the room code. Play several points each way. Verify: curving shots render smoothly on both clients between snapshots (no zig-zag corrections), net dribbles look the same on both screens, no disconnects.

- [ ] **Step 5: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/network/PacketType.java \
        sim/src/main/java/io/github/some_example_name/network/GameConnection.java \
        sim/src/main/java/io/github/some_example_name/server/GameServer.java \
        sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java \
        core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java
git commit -m "feat(net): STATE carries ball spin; client extrapolates with shared BallPhysics"
```

---

### Task 9: Item audit, docs, final verification

**Files:**
- Test: `sim/src/test/java/io/github/some_example_name/world/physics/ItemPaceAuditTest.java`
- Modify: `docs/architecture.md` (physics + protocol sections)

- [ ] **Step 1: Write the item-interaction audit test**

```java
package io.github.some_example_name.world.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SLOW_MO (×0.7) and FAST_SERVE (×1.3) enter PaddleContact as the pace
 * multiplier, which scales the whole outgoing pace including the carry term —
 * the items must stay clearly felt now that returns carry incoming speed.
 */
class ItemPaceAuditTest {

    private static float returnSpeed(float paceMultiplier, float incomingVz) {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BallState s = new BallState();
        s.pos.set(0f, 3f, 4f);
        s.vel.set(0f, -2f, incomingVz);
        PaddleContact.applyReturn(s, cfg, 0f, 0f, 1f, paceMultiplier, true,
                                  cfg.basePaceSI, cfg.baseArcSI);
        return Math.abs(s.vel.z);
    }

    @Test void slowMoSlowsTheWholeReturn() {
        assertTrue(returnSpeed(0.7f, 9f) < returnSpeed(1f, 9f) * 0.75f,
            "slow-mo must cut at least 25% even on fast incoming balls");
    }

    @Test void fastServeSpeedsTheWholeReturn() {
        assertTrue(returnSpeed(1.3f, 9f) > returnSpeed(1f, 9f) * 1.25f);
    }

    @Test void itemMultipliersCannotEscapeTheClamp() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BallState s = new BallState();
        s.pos.set(0f, 3f, 4f);
        s.vel.set(0f, -2f, 30f);
        PaddleContact.applyReturn(s, cfg, 1f, 1f, 1.6f, 1.3f * 1.6f, true,
                                  cfg.basePaceSI, cfg.baseArcSI);
        assertTrue(s.vel.len() <= cfg.maxSpeedW() + 1e-3f);
    }
}
```

- [ ] **Step 2: Run it**

Run: `./gradlew :sim:test --tests 'io.github.some_example_name.world.physics.ItemPaceAuditTest'`
Expected: PASS (the pace formula multiplies the carry too — that was decided in Task 6). If a threshold misses by a hair, adjust the assertion factor toward the actual item multiplier (0.7/1.3), not the physics.

- [ ] **Step 3: Update docs/architecture.md**

Make these edits (prose, adapt to surrounding style):
1. In the module table, `sim` row: replace `HitVelocity` with `BallPhysics`, `PaddleContact`, `BotPlanner`, `PhysicsConfig`.
2. Replace the "Click-based protocol" paragraph's mention of `HitVelocity.computeFromRay` with `PaddleContact` and delete the sentence about `sanitizeNetworkReturn`/legacy `HIT` (it's gone).
3. In §"Server-authoritative architecture", note that `MatchWorld3D` delegates motion to `BallPhysics` (drag + Magnus + spin bounce + net collider, paper-constant SI config with `timeScale`).
4. In the STATE packet description (§7 or the README pointer), add the spin floats.
5. In "Things that hurt": delete the "HIT-velocity sanitizer is no longer reachable" bullet; update the "Snapshot interpolation" bullet to say extrapolation now runs the shared `BallPhysics` (interpolation buffering still a future improvement).
6. In the match-loop section, replace the bot description (freeze + `botBaseReturnChance`) with the planner (forward-simulation, Gaussian aim error σ, reaction delay); note `botBaseReturnChance` is now unused config.

- [ ] **Step 4: Final verification**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :sim:test`
Expected: PASS — full suite: config, flight, bounce, net, paddle, planner, rally, fly, item audit.

Manual sweep (VS BOT): one full best-of-3 including an item phase; use SLOW_MO and FAST_SERVE and confirm they feel changed-but-working; swat a fly; win and lose a round.

- [ ] **Step 5: Commit**

```bash
git add sim/src/test/java/io/github/some_example_name/world/physics/ItemPaceAuditTest.java \
        docs/architecture.md
git commit -m "test+docs: item pace audit; architecture notes for the physics rebuild"
```

---

## Spec → task coverage map

| Spec section | Tasks |
|---|---|
| §1 flight (gravity/drag/Magnus/decay, SI + timeScale) | 1, 2 |
| §1 bounce (e = 0.92, spin–friction coupling) | 3 |
| §1 net (swept collider, dribble/fall-back, fault check deleted) | 4, 5 |
| §2 paddle mapping (aim/spin/pace/carry/transfer/clamps, serves, serveControl) | 6 |
| §3 components (PhysicsConfig, BallState, BallPhysics, PaddleContact, BotPlanner) | 1, 2, 6, 7 |
| §3 MatchWorld3D delegation, events, rally ramp reinterpretation | 5, 7 |
| §3 STATE spin + shared client extrapolation | 8 |
| §4 tests (ballistics, drag, Magnus, timeScale shape, bounce, net, paddle property, Monte-Carlo, determinism) | 2, 3, 4, 6, 7 |
| §4 calibration order (timeScale → paddle → σ) | defaults in 1; property tuning in 6; σ in 7 |
| §4 item audit + docs | 9 |
