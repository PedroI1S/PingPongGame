package io.github.some_example_name.world.physics;

import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

/**
 * Integrates an angular velocity into an orientation quaternion. The visual
 * companion to {@link BallState#spin}: the renderer accumulates the ball's
 * real spin (world rad/s) here so the drawn ball rotates at exactly the
 * simulated rate.
 *
 * <p>Allocation-free and stateless, matching the physics classes' rule that
 * shared code must stay safe for parallel worlds.</p>
 */
public final class SpinOrientation {

    private SpinOrientation() {}

    /**
     * Rotates {@code orientation} by {@code spin}·{@code dt}. The spin axis is
     * in world space, so the delta is applied on the left. Normalizes to keep
     * float drift from accumulating across frames. No-op for negligible spin.
     */
    public static void integrate(Quaternion orientation, Vector3 spin, float dt) {
        float omega = spin.len();
        if (omega < 1e-4f || dt <= 0f) return;
        float half = 0.5f * omega * dt;
        // Δq = (sin(θ/2)·ω̂, cos(θ/2)) composed without allocating
        float s = (float) Math.sin(half) / omega;
        orientation.mulLeft(spin.x * s, spin.y * s, spin.z * s, (float) Math.cos(half));
        orientation.nor();
    }
}
