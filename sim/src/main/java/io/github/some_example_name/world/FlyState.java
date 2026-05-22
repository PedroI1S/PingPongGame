package io.github.some_example_name.world;

public final class FlyState {
    public static final float FLY_RADIUS = 0.3f;

    public final float x;
    public final float z;
    public boolean alive = true;

    public FlyState(float x, float z) {
        this.x = x;
        this.z = z;
    }
}
