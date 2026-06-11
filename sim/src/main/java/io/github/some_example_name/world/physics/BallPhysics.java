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
}
