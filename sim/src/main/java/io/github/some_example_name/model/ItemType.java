package io.github.some_example_name.model;

public enum ItemType {
    PATCH_KIT(1),
    WIDE_PADDLE(2),
    SLOW_MO(3),
    STEAL(4),
    FAST_SERVE(5),
    TINY_PADDLE(6),
    PUNCH(7),
    FLY_BAIT(8),
    COIN_FLIP(9);

    private final byte id;

    ItemType(int id) { this.id = (byte) id; }

    public byte getId() { return id; }

    public static ItemType fromId(byte id) {
        for (ItemType t : values()) {
            if (t.id == id) return t;
        }
        return null;
    }
}
