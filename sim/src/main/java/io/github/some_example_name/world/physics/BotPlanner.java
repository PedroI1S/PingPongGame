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
        public float aimSigma = 0.60f;
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
