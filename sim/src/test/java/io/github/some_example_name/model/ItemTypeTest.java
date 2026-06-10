package io.github.some_example_name.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemTypeTest {
    @Test void fromIdRoundTrips() {
        for (ItemType t : ItemType.values()) {
            assertSame(t, ItemType.fromId(t.getId()));
        }
    }

    @Test void fromIdUnknownReturnsNull() {
        assertNull(ItemType.fromId((byte) 99));
    }

    @Test void hasNineItems() {
        assertEquals(9, ItemType.values().length);
    }
}
