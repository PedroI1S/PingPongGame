package io.github.some_example_name.tutorial;

/** Axis-aligned target rectangle on the table plane (x/z, world units). */
public final class ZoneRect {
    public final float x0, x1, z0, z1;

    public ZoneRect(float x0, float x1, float z0, float z1) {
        this.x0 = x0; this.x1 = x1; this.z0 = z0; this.z1 = z1;
    }

    public boolean contains(float x, float z) {
        return x >= x0 && x <= x1 && z >= z0 && z <= z1;
    }

    public float centerX() { return (x0 + x1) * 0.5f; }
    public float centerZ() { return (z0 + z1) * 0.5f; }
    public float width()   { return x1 - x0; }
    public float depth()   { return z1 - z0; }
}
