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
