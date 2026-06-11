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
        // y tolerance covers the integrator's O(dt) discretization gap: the slow run
        // samples the same SI trajectory at half the SI step, so the Euler position
        // offsets differ by ~½·g·Δ(dt_si)·t ≈ 0.026 world units. Shape is identical
        // in the continuum limit; x/z carry no gravity term and stay tighter.
        assertEquals(a.pos.y, b.pos.y, 0.03f);
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
