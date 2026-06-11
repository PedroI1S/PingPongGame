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
