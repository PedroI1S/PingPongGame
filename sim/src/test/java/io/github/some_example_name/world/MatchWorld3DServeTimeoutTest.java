package io.github.some_example_name.world;

import com.badlogic.gdx.math.RandomXS128;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchWorld3DServeTimeoutTest {

    private MatchWorld3D world;

    @BeforeEach void setup() {
        world = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(1L));
        world.setMatchMode(MatchMode.PVP);
    }

    @Test void pvpServeForfeitsAfterTimeout() {
        assertEquals(MatchWorld3D.Phase.PREPARE_SERVE, world.getPhase());
        // Run well past the prep delay plus the 20s grace window.
        for (int i = 0; i < 30 * 10; i++) world.update(0.1f);
        assertEquals(GameConfig.DEFAULT_LIVES - 1, world.getPlayerLives(),
            "P1 (the serving player) forfeits the point");
        assertEquals(GameConfig.DEFAULT_LIVES, world.getP2Lives());
        assertEquals(MatchWorld3D.Phase.ITEM_PHASE, world.getPhase());
    }

    @Test void pvpServeWithinWindowStillWorks() {
        for (int i = 0; i < 50; i++) world.update(0.1f); // 5s — inside the window
        assertEquals(MatchWorld3D.Phase.PREPARE_SERVE, world.getPhase());
        assertTrue(world.tryPlayerServe(null));
        assertEquals(MatchWorld3D.Phase.OUTGOING, world.getPhase());
    }
}
