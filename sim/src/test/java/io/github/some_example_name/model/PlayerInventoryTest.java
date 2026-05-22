package io.github.some_example_name.model;

import org.junit.jupiter.api.Test;
import com.badlogic.gdx.math.RandomXS128;
import static org.junit.jupiter.api.Assertions.*;

class PlayerInventoryTest {

    @Test void addUpToFourItems() {
        PlayerInventory inv = new PlayerInventory();
        assertTrue(inv.add(ItemType.PATCH_KIT));
        assertTrue(inv.add(ItemType.SLOW_MO));
        assertTrue(inv.add(ItemType.WIDE_PADDLE));
        assertTrue(inv.add(ItemType.STEAL));
        assertFalse(inv.add(ItemType.PUNCH)); // 5th item rejected
        assertEquals(4, inv.size());
    }

    @Test void removeExistingItem() {
        PlayerInventory inv = new PlayerInventory();
        inv.add(ItemType.PATCH_KIT);
        assertTrue(inv.remove(ItemType.PATCH_KIT));
        assertEquals(0, inv.size());
    }

    @Test void removeAbsentItemReturnsFalse() {
        PlayerInventory inv = new PlayerInventory();
        assertFalse(inv.remove(ItemType.COIN_FLIP));
    }

    @Test void stealReturnsRandomItemAndRemovesIt() {
        PlayerInventory inv = new PlayerInventory();
        inv.add(ItemType.PATCH_KIT);
        inv.add(ItemType.SLOW_MO);
        ItemType stolen = inv.steal(new RandomXS128(42L));
        assertNotNull(stolen);
        assertEquals(1, inv.size());
    }

    @Test void stealFromEmptyReturnsNull() {
        assertNull(new PlayerInventory().steal(new RandomXS128()));
    }

    @Test void clearEmptiesInventory() {
        PlayerInventory inv = new PlayerInventory();
        inv.add(ItemType.WIDE_PADDLE);
        inv.clear();
        assertEquals(0, inv.size());
    }
}
