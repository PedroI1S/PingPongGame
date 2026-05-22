package io.github.some_example_name.model;

public enum ArenaSide {
    PLAYER(1f),
    BOT(-1f);

    private final float horizontalDirection;

    ArenaSide(float horizontalDirection) {
        this.horizontalDirection = horizontalDirection;
    }

    public ArenaSide opposite() {
        return this == PLAYER ? BOT : PLAYER;
    }

    public float horizontalDirection() {
        return horizontalDirection;
    }
}
