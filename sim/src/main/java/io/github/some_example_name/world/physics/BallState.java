package io.github.some_example_name.world.physics;

import com.badlogic.gdx.math.Vector3;

/** Ball motion state: position, velocity, spin (angular velocity), world units. */
public final class BallState {
    public final Vector3 pos  = new Vector3();
    public final Vector3 vel  = new Vector3();
    public final Vector3 spin = new Vector3();

    public BallState set(BallState other) {
        pos.set(other.pos);
        vel.set(other.vel);
        spin.set(other.spin);
        return this;
    }

    public BallState reset() {
        pos.setZero();
        vel.setZero();
        spin.setZero();
        return this;
    }
}
