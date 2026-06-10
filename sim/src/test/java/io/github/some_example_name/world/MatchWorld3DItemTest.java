package io.github.some_example_name.world;

import com.badlogic.gdx.math.RandomXS128;
import io.github.some_example_name.model.ItemType;
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

    @Test void applyPatchKitAddsLife() {
        world.enterItemPhase();
        world.getP1Inventory().clear();
        world.getP1Inventory().add(ItemType.PATCH_KIT);
        // At full lives (5), addLife is capped — item still consumed
        assertTrue(world.applyItem(1, ItemType.PATCH_KIT));
        assertFalse(world.getP1Inventory().getItems().contains(ItemType.PATCH_KIT));
    }

    @Test void applyWideClickSetsEffect() {
        world.enterItemPhase();
        world.getP1Inventory().clear();
        world.getP1Inventory().add(ItemType.WIDE_PADDLE);
        world.applyItem(1, ItemType.WIDE_PADDLE);
        assertEquals(1.5f, world.getP1Effects().hitScaleMultiplier(), 0.001f);
    }

    @Test void applyItemNotInInventoryReturnsFalse() {
        world.enterItemPhase();
        world.getP1Inventory().clear();
        assertFalse(world.applyItem(1, ItemType.COIN_FLIP));
    }

    @Test void applyTinyPaddleAffectsOpponentEffects() {
        world.enterItemPhase();
        world.getP1Inventory().clear();
        world.getP1Inventory().add(ItemType.TINY_PADDLE);
        world.applyItem(1, ItemType.TINY_PADDLE);
        assertEquals(0.6f, world.getP2Effects().hitScaleMultiplier(), 0.001f);
    }
}
