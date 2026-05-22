package io.github.some_example_name.world;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

/**
 * Shared click-to-return velocity math for local and networked play.
 *
 * <p>Single-player uses {@link #computeFromRay}; the server uses
 * {@link #sanitizeNetworkReturn} to reject or clamp client-supplied vectors.</p>
 */
public final class HitVelocity {

    public static final float CLICK_HIT_PADDING = 3.5f;

    private static final float MIN_VZ = 5f;
    private static final float MAX_VZ = 11f;
    private static final float MIN_VY = 3f;
    private static final float MAX_VY = 9f;
    private static final float MAX_VX = 3.2f;

    private HitVelocity() {}

    /**
     * Ray-sphere hit test and return impulse (same tuning as {@link MatchWorld3D#tryHitBall}).
     *
     * @param returnTowardNegativeZ {@code true} for P1 (+z side → −z), {@code false} for P2
     * @param returnPowerMultiplier duelist return-power stat (1 = default)
     * @param outVel                written on success
     * @param hitPoint              scratch (written)
     * @param offset                scratch (written)
     * @return {@code true} if the ray hit the ball
     */
    public static boolean computeFromRay(Ray pickRay, Vector3 ballPos, float targetScaleMultiplier,
                                         float returnPowerMultiplier, boolean returnTowardNegativeZ,
                                         Vector3 outVel, Vector3 hitPoint, Vector3 offset) {
        float hitRadius = MatchWorld3D.BALL_RADIUS * targetScaleMultiplier * CLICK_HIT_PADDING;
        if (!Intersector.intersectRaySphere(pickRay, ballPos, hitRadius, hitPoint)) {
            return false;
        }

        offset.set(hitPoint).sub(ballPos);
        float ndx = MathUtils.clamp(offset.x / hitRadius, -1f, 1f);
        float ndy = MathUtils.clamp(offset.y / hitRadius, -1f, 1f);
        float power = (float) Math.sqrt(ndx * ndx + ndy * ndy);

        float zSign = returnTowardNegativeZ ? -1f : 1f;
        outVel.set(
            ndx * 3.2f,
            5.0f + ndy * 2.0f,
            zSign * (7.5f + power * 2.0f) * returnPowerMultiplier
        );
        return true;
    }

    /**
     * Validates and clamps a client HIT packet before the server applies it.
     *
     * @param playerNumber 1 (toward −z) or 2 (toward +z)
     * @param out            receives the sanitized velocity
     * @return {@code false} if the vector is unusable (wrong direction or out of range)
     */
    public static boolean sanitizeNetworkReturn(int playerNumber,
                                                float vx, float vy, float vz,
                                                Vector3 out) {
        float zDir = (playerNumber == 1) ? -1f : 1f;
        if (vz * zDir >= 0f) {
            return false;
        }
        float vzMag = Math.abs(vz);
        if (vzMag < MIN_VZ || vzMag > MAX_VZ) {
            return false;
        }
        if (vy < MIN_VY || vy > MAX_VY) {
            return false;
        }
        if (Math.abs(vx) > MAX_VX + 0.5f) {
            return false;
        }

        out.set(
            MathUtils.clamp(vx, -MAX_VX, MAX_VX),
            MathUtils.clamp(vy, MIN_VY, MAX_VY),
            zDir * MathUtils.clamp(vzMag, 7.5f, 9.5f)
        );
        return true;
    }
}
