package io.github.some_example_name.model;

import io.github.some_example_name.world.FlyState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemEffectsTest {

    @Test void clearResetsAllFlags() {
        ItemEffects e = new ItemEffects();
        e.wideClick = true;
        e.slowIncoming = true;
        e.fastIncoming = true;
        e.tinyPaddleActive = true;
        e.flies.add(new FlyState(1f, 2f));
        e.clear();
        assertFalse(e.wideClick);
        assertFalse(e.slowIncoming);
        assertFalse(e.fastIncoming);
        assertFalse(e.tinyPaddleActive);
        assertTrue(e.flies.isEmpty());
    }

    @Test void hitScaleReturnsWideWhenSet() {
        ItemEffects e = new ItemEffects();
        e.wideClick = true;
        assertEquals(1.5f, e.hitScaleMultiplier(), 0.001f);
    }

    @Test void hitScaleReturnsTinyWhenSet() {
        ItemEffects e = new ItemEffects();
        e.tinyPaddleActive = true;
        assertEquals(0.6f, e.hitScaleMultiplier(), 0.001f);
    }

    @Test void hitScaleDefaultIsOne() {
        assertEquals(1f, new ItemEffects().hitScaleMultiplier(), 0.001f);
    }

    @Test void speedMultiplierSlowMo() {
        ItemEffects e = new ItemEffects();
        e.slowIncoming = true;
        assertEquals(0.7f, e.incomingSpeedMultiplier(), 0.001f);
    }

    @Test void speedMultiplierFast() {
        ItemEffects e = new ItemEffects();
        e.fastIncoming = true;
        assertEquals(1.3f, e.incomingSpeedMultiplier(), 0.001f);
    }
}
