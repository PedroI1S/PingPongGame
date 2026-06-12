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

    /**
     * Regression: the bot must also engage when the PLAYER serves. The planner
     * is armed wherever a ball is launched toward the bot — a P1 serve included.
     * (Bug: only tryHitBall armed it, so on a player serve the bot idled in the
     * BOT_RESOLVE safety net for the full 4.5 s timeout.) An armed plan always
     * strikes within the 4 s horizon, so the rally must resolve by ~4.2 s.
     */
    @Test void playerServeEngagesTheBot() {
        boolean sawReturn = false, sawBotMiss = false;
        int served = 0;
        for (long seed = 0; seed < 40 && !(sawReturn && sawBotMiss); seed++) {
            MatchWorld3D world = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(seed));
            world.setMatchMode(MatchMode.BOT);
            if (!winOnePointForP1(world)) continue;

            // P1 won → item phase → ready both → P1 holds the serve
            world.playerReady(1);
            world.playerReady(2);
            assertEquals(MatchWorld3D.Phase.PREPARE_SERVE, world.getPhase());
            int p1Lives = world.getPlayerLives();
            int p2Lives = world.getP2Lives();
            assertTrue(world.tryPlayerServe(null), "P1 should be allowed to serve, seed " + seed);
            served++;

            boolean resolved = false;
            for (int i = 0; i < (int) (4.2f * 60) && !resolved; i++) {
                world.update(1f / 60f);
                if (world.getPhase() == MatchWorld3D.Phase.INCOMING) {
                    sawReturn = true;
                    resolved = true;
                } else if (world.getP2Lives() < p2Lives) {
                    sawBotMiss = true;
                    resolved = true;
                } else if (world.getPlayerLives() < p1Lives) {
                    resolved = true; // serve fault — not the bot's rally
                }
            }
            assertTrue(resolved,
                "bot must act on a player serve within the planner horizon, seed " + seed);
        }
        // the loop exits early once both outcomes are seen, so these two
        // assertions are also the guard that the point-winning helper worked
        assertTrue(sawReturn, "the bot must return some player serves (served " + served + ")");
        assertTrue(sawBotMiss, "the bot must miss some player serves (served " + served + ")");
    }

    /**
     * Plays bot-serve points (P1 center-clicking every hittable ball) until P1
     * wins one, leaving the world in ITEM_PHASE with the serve passing to P1.
     */
    private static boolean winOnePointForP1(MatchWorld3D world) {
        for (int point = 0; point < 8; point++) {
            int p1Lives = world.getPlayerLives();
            int p2Lives = world.getP2Lives();
            for (int i = 0; i < 60 * 20; i++) {
                world.update(1f / 60f);
                if (world.getPhase() == MatchWorld3D.Phase.ITEM_PHASE) break;
                if (world.isPlayerCanHit() && world.getBallPos().z > 3.5f) {
                    Vector3 bp = world.getBallPos();
                    world.tryHitBall(new Ray(new Vector3(bp.x, bp.y, bp.z + 5f),
                                             new Vector3(0f, 0f, -1f)));
                }
            }
            if (world.getPhase() != MatchWorld3D.Phase.ITEM_PHASE) return false; // stuck
            if (world.getP2Lives() < p2Lives) return true;   // P1 won the point
            if (world.getPlayerLives() == p1Lives) return false; // no life moved — bail
            world.playerReady(1); // P1 lost — ready up and try the next point
            world.playerReady(2);
        }
        return false;
    }
}
