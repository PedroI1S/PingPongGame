package io.github.some_example_name.world;

import com.badlogic.gdx.math.RandomXS128;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end rules wiring over the new physics: a P1 serve must fly, clear the
 * net, bounce once on P2's side and hand the rally to P2 (BOT_RESOLVE), with
 * the bounce reported on the correct side.
 */
class MatchWorld3DRallyTest {

    @Test void p1ServeReachesOpponentAndHandsOverTheRally() {
        MatchWorld3D world = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(3));
        world.setMatchMode(MatchMode.PVP);
        assertTrue(world.tryPlayerServe(null), "P1 should be allowed to serve");
        assertEquals(MatchWorld3D.Phase.OUTGOING, world.getPhase());

        boolean reachedResolve = false;
        for (int i = 0; i < 60 * 6 && !reachedResolve; i++) {
            world.update(1f / 60f);
            reachedResolve = world.getPhase() == MatchWorld3D.Phase.BOT_RESOLVE;
        }
        assertTrue(reachedResolve, "serve should land on P2 side; phase=" + world.getPhase()
            + " status=" + world.getStatusText());
        assertTrue(world.getBallPos().z < 0f, "ball should be on P2's side");
        assertEquals(5, world.getPlayerLives());
        assertEquals(5, world.getP2Lives());
    }

    @Test void ballSpinAccessorExists() {
        MatchWorld3D world = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(3));
        assertNotNull(world.getBallSpin());
    }

    /**
     * The reason the old fault-line rule died: a net clip must be scored by
     * where the ball ends up. Fall-back onto the shooter's side costs the
     * shooter; a dribble-over lands legally and the rally continues. Both
     * outcomes are reachable (net jitter), deterministic per seed.
     */
    @Test void netClipIsScoredByWhereTheBallLands() {
        boolean sawFallBackLoss = false, sawDribbleRally = false;
        for (long seed = 0; seed < 40 && !(sawFallBackLoss && sawDribbleRally); seed++) {
            MatchWorld3D world = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(seed));
            world.setMatchMode(MatchMode.PVP);
            assertTrue(world.tryPlayerServe(null));
            // redirect the serve into a low net-bound ball via the live references
            // (close enough that it reaches the net before gravity drops it
            // below table-contact height)
            world.getBallPos().set(0f, 2.3f, 0.5f);
            world.getBallVel().set(0f, 0f, -8f);
            world.getBallSpin().setZero();
            int livesBefore = world.getPlayerLives();
            for (int i = 0; i < 60 * 4; i++) {
                world.update(1f / 60f);
                if (world.getPhase() != MatchWorld3D.Phase.OUTGOING) break;
            }
            if (world.getPhase() == MatchWorld3D.Phase.BOT_RESOLVE) {
                assertEquals(livesBefore, world.getPlayerLives(),
                    "a dribble-over that lands legally must not cost a life");
                sawDribbleRally = true;
            } else if (world.getPlayerLives() < livesBefore) {
                sawFallBackLoss = true;
            }
        }
        assertTrue(sawFallBackLoss, "some net clips must fall back and cost the shooter");
        assertTrue(sawDribbleRally, "some net clips must dribble over and continue the rally");
    }
}
