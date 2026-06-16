package io.github.some_example_name.render;

import io.github.some_example_name.model.ItemType;

/** Display name + one-line description per item, shown during the item phase. */
public final class ItemCopy {
    private ItemCopy() {}

    public static String name(ItemType t) {
        return switch (t) {
            case PATCH_KIT   -> "Patch Kit";
            case WIDE_PADDLE -> "Wide Paddle";
            case SLOW_MO     -> "Slow-Mo";
            case STEAL       -> "Steal";
            case FAST_SERVE  -> "Fast Serve";
            case TINY_PADDLE -> "Tiny Paddle";
            case PUNCH       -> "Punch";
            case FLY_BAIT    -> "Fly Bait";
            case COIN_FLIP   -> "Coin Flip";
        };
    }

    public static String description(ItemType t) {
        return switch (t) {
            case PATCH_KIT   -> "Restores one of your lives.";
            case WIDE_PADDLE -> "Your hit area is larger this rally.";
            case SLOW_MO     -> "The ball comes at you slower this rally.";
            case STEAL       -> "Take a random item from your opponent.";
            case FAST_SERVE  -> "The ball comes at your opponent faster.";
            case TINY_PADDLE -> "Shrinks your opponent's hit area.";
            case PUNCH       -> "Blurs your opponent's view for 10 seconds.";
            case FLY_BAIT    -> "Spawns flies on your opponent's side — ball hits cost a point.";
            case COIN_FLIP   -> "50/50 — someone loses a life.";
        };
    }
}
