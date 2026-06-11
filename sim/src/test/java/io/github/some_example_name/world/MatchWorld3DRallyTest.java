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
}
