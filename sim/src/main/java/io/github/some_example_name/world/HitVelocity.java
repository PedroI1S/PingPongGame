package io.github.some_example_name.world;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

/**
 * Click-to-return velocity math.  The server computes every return from a
 * pick ray ({@link #computeFromRay}), so clients never submit raw velocities.
 */
public final class HitVelocity {

    public static final float CLICK_HIT_PADDING = 3.5f;

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
}
