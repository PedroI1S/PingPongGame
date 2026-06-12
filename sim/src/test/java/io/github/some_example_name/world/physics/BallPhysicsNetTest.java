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

    /** Pins the clearance rule: the ball clears only if its BOTTOM is above the net top. */
    @Test void clearanceBoundaryUsesBallBottom() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        cfg.gravitySI = 0f; // straight-line flight: crossing height == launch height
        cfg.dragKSI = 0f;
        StepContacts c = new StepContacts();

        BallState clears = toward(PhysicsConfig.NET_TOP_Y + PhysicsConfig.BALL_RADIUS + 0.001f, -8f);
        new BallPhysics(cfg).step(clears, 0.1f, null, c);
        assertFalse(c.netHit, "bottom above net top must clear");

        BallState hits = toward(PhysicsConfig.NET_TOP_Y + PhysicsConfig.BALL_RADIUS - 0.001f, -8f);
        new BallPhysics(cfg).step(hits, 0.1f, null, c);
        assertTrue(c.netHit, "bottom below net top must hit");
    }

    /** The post-hit nudge lands over the table; the table contact must catch the drop. */
    @Test void netHitThenTableSettlesOnTable() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BallPhysics phys = new BallPhysics(cfg);
        BallState s = toward(2.25f, -8f);
        StepContacts c = new StepContacts();
        boolean sawNet = false, sawTable = false;
        float minY = Float.MAX_VALUE;
        for (int i = 0; i < 240; i++) {
            phys.step(s, 1f / 60f, null, c);
            sawNet |= c.netHit;
            sawTable |= c.tableBounce;
            minY = Math.min(minY, s.pos.y);
        }
        assertTrue(sawNet, "low ball must clip the net");
        assertTrue(sawTable, "dribbled ball must reach the table");
        assertTrue(minY >= PhysicsConfig.TABLE_TOP_Y - 1e-3f,
            "ball must never sink below the table top, got " + minY);
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
