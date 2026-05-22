package io.github.some_example_name.world;

import com.badlogic.gdx.math.RandomXS128;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchWorld3DItemTest {

    private MatchWorld3D world;

    @BeforeEach void setup() {
        world = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(1L));
        world.setMatchMode(MatchMode.BOT);
    }

    @Test void enterItemPhaseDealsItems() {
        world.enterItemPhase();
        assertEquals(MatchWorld3D.Phase.ITEM_PHASE, world.getPhase());
        assertEquals(2, world.getP1Inventory().size());
        assertEquals(2, world.getP2Inventory().size());
    }

    @Test void playerReadyBothAdvancesToPrepareServe() {
        world.enterItemPhase();
        world.playerReady(1);
        assertEquals(MatchWorld3D.Phase.ITEM_PHASE, world.getPhase()); // still waiting
        world.playerReady(2);
        assertEquals(MatchWorld3D.Phase.PREPARE_SERVE, world.getPhase());
    }

    @Test void itemPhaseTimeoutAdvancesToPrepareServe() {
        world.enterItemPhase();
        world.update(16f); // advance past 15s timeout
        assertEquals(MatchWorld3D.Phase.PREPARE_SERVE, world.getPhase());
    }

    @Test void lastDealtItemsAvailableAfterEnterItemPhase() {
        world.enterItemPhase();
        assertNotNull(world.getLastDealtItems(1));
        assertNotNull(world.getLastDealtItems(2));
        assertEquals(2, world.getLastDealtItems(1).length);
        assertEquals(2, world.getLastDealtItems(2).length);
    }
}
