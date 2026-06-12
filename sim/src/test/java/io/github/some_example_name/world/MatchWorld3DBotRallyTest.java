package io.github.some_example_name.world;

import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end BOT mode through MatchWorld3D (the planner alone is covered by
 * BotPlannerTest): bot serves, P1 returns with a center click, and the armed
 * plan must resolve — either the bot swings (phase returns to INCOMING) or it
 * misses and loses a life. Across seeds both outcomes must occur, and every
 * engaged rally must resolve well inside the BOT_RESOLVE safety window (pins
 * the horizon-vs-timeout interplay and the swing-time OOB guard).
 */
class MatchWorld3DBotRallyTest {

    @Test void botEitherReturnsOrLosesTheLife_neverStalls() {
        boolean sawReturn = false, sawBotMiss = false;
        int engaged = 0;
        for (long seed = 0; seed < 30; seed++) {
            MatchWorld3D world = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(seed));
            world.setMatchMode(MatchMode.BOT);

            // bot auto-serves; wait until the ball is properly on P1's side —
            // clicking the instant it crosses the net launches the return from
            // z≈0 and it overshoots the whole table (a real "too early" swing)
            boolean canHit = false;
            for (int i = 0; i < 60 * 5 && !canHit; i++) {
                world.update(1f / 60f);
                if (world.getPhase() == MatchWorld3D.Phase.ITEM_PHASE) break; // serve fault
                canHit = world.isPlayerCanHit() && world.getBallPos().z > 3.5f;
            }
            if (!canHit) continue;

            // center-ish click: horizontal ray from behind P1 through the ball
            Vector3 ballPos = world.getBallPos();
            Ray click = new Ray(new Vector3(ballPos.x, ballPos.y, ballPos.z + 5f),
                                new Vector3(0f, 0f, -1f));
            int p1Lives = world.getPlayerLives();
            int p2Lives = world.getP2Lives();
            if (!world.tryHitBall(click)) continue;
            engaged++;

            boolean resolved = false;
            for (int i = 0; i < 60 * 10 && !resolved; i++) {
                world.update(1f / 60f);
                if (world.getPhase() == MatchWorld3D.Phase.INCOMING) {
                    sawReturn = true;
                    resolved = true;
                } else if (world.getP2Lives() < p2Lives) {
                    sawBotMiss = true;
                    resolved = true;
                } else if (world.getPlayerLives() < p1Lives) {
                    resolved = true; // P1's own return went out — not the bot's rally
                }
            }
            assertTrue(resolved, "rally must resolve within 10s (no stalled armed plan), seed " + seed);
        }
        assertTrue(engaged >= 10, "most seeds should reach a playable P1 return, got " + engaged);
        assertTrue(sawReturn, "the bot must return some balls through the world path");
        assertTrue(sawBotMiss, "the bot must miss some balls through the world path");
    }
}
