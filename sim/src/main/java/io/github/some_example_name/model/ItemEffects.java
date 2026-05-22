package io.github.some_example_name.model;

import io.github.some_example_name.world.FlyState;
import java.util.ArrayList;
import java.util.List;

public final class ItemEffects {
    public boolean wideClick;
    public boolean slowIncoming;
    public boolean fastIncoming;
    public boolean tinyPaddleActive;
    public final List<FlyState> flies = new ArrayList<>(3);

    /** Effective hit-radius multiplier to apply on top of DuelistState's base. */
    public float hitScaleMultiplier() {
        if (wideClick)  return 1.5f;
        if (tinyPaddleActive) return 0.6f;
        return 1f;
    }

    /** Effective incoming-ball speed multiplier. */
    public float incomingSpeedMultiplier() {
        if (slowIncoming) return 0.7f;
        if (fastIncoming) return 1.3f;
        return 1f;
    }

    public void clear() {
        wideClick = false;
        slowIncoming = false;
        fastIncoming = false;
        tinyPaddleActive = false;
        flies.clear();
    }
}
