package io.github.some_example_name.tutorial;

import com.badlogic.gdx.math.RandomXS128;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import io.github.some_example_name.world.MatchWorld3D;
import io.github.some_example_name.world.physics.BallState;
import io.github.some_example_name.world.physics.BotPlanner;
import io.github.some_example_name.world.physics.PaddleContact;
import io.github.some_example_name.world.physics.PhysicsConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The graduation bot must be reachable for tuning and measurably gentler. */
class SparringProfileTest {

    /** The profile the TutorialScreen applies for the graduation rally. */
    public static void soften(BotPlanner.Profile p) {
        p.aimSigma = 0.95f;
        p.reactionDelay = 0.8f;
    }

    @Test void worldExposesItsBotProfile() {
        MatchWorld3D world = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(1));
        world.setMatchMode(MatchMode.BOT);
        assertNotNull(world.getBotProfile());
        soften(world.getBotProfile());
        assertEquals(0.95f, world.getBotProfile().aimSigma, 1e-6f);
    }

    @Test void gentleProfileReturnsStrictlyLessThanDefault() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BotPlanner planner = new BotPlanner(cfg);
        BotPlanner.Profile normal = new BotPlanner.Profile();
        BotPlanner.Profile gentle = new BotPlanner.Profile();
        soften(gentle);
        BotPlanner.Plan plan = new BotPlanner.Plan();
        RandomXS128 r1 = new RandomXS128(42), r2 = new RandomXS128(42);
        int normalHits = 0, gentleHits = 0;
        for (int i = 0; i < 600; i++) {
            BallState b1 = shot(r1, cfg);
            planner.plan(b1, normal, r1, plan);
            if (plan.strikeTime >= 0f && !plan.whiff) normalHits++;
            BallState b2 = shot(r2, cfg);
            planner.plan(b2, gentle, r2, plan);
            if (plan.strikeTime >= 0f && !plan.whiff) gentleHits++;
        }
        assertTrue(gentleHits < normalHits,
            "gentle profile must whiff more: " + gentleHits + " vs " + normalHits);
    }

    private static BallState shot(RandomXS128 random, PhysicsConfig cfg) {
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
}
