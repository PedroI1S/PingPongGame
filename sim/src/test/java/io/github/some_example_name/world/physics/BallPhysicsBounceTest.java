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

    /**
     * Pins the friction torque and its ordering: the forward kick's reaction
     * torque must reduce topspin BEFORE the contact decay multiplies it.
     * Numbers: slipZ = 4 − 30·0.18 = −1.4 → grip-capped j = 0.4 →
     * Δωx = −0.4/(0.4·0.18) ≈ −5.56; (30 − 5.56)·0.7 ≈ 17.11. Without the
     * torque this would be 21.0; with decay-before-torque ≈ 15.44.
     */
    @Test void bounceTorqueReducesTopspinBeforeDecay() {
        BallState s = falling(0f, 4f, 30f, 0f, 0f);
        oneBounce(pureBounceCfg(), s);
        assertEquals(17.11f, s.spin.x, 0.3f);
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
