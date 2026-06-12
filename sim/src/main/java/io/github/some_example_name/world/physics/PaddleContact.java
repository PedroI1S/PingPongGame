package io.github.some_example_name.world.physics;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

/**
 * Click-to-return contact model: where the click ray meets the (padded) ball
 * decides aim, pace and spin. Replaces the old HitVelocity recipe. The same
 * mapping serves player returns, serves, and bot returns (offsets chosen
 * directly by the bot planner).
 *
 * <p>Sign conventions (world axes): for travel toward −z (P1 returning),
 * topspin is ωx &lt; 0; toward +z it is ωx &gt; 0 — handled by the travel
 * sign. Sidespin ωy is signed so the Magnus curve continues toward the aimed
 * side; the corkscrew tilt ωz adds a same-direction kick at the bounce.</p>
 */
public final class PaddleContact {

    /** Generous ray-vs-ball padding so clicks near the ball still count. */
    public static final float CLICK_HIT_PADDING = 3.5f;

    private PaddleContact() {}

    /** Effective click radius (base × stat/item multipliers × padding). */
    public static float hitRadius(float scaleMultiplier) {
        return PhysicsConfig.BALL_RADIUS * scaleMultiplier * CLICK_HIT_PADDING;
    }

    /**
     * Ray-sphere return. Returns false if the ray misses the padded ball;
     * on success overwrites {@code ball.vel} and {@code ball.spin}.
     */
    public static boolean returnFromRay(Ray ray, BallState ball, PhysicsConfig cfg,
                                        float scaleMultiplier, float powerMultiplier,
                                        float paceMultiplier, boolean towardNegativeZ) {
        float radius = hitRadius(scaleMultiplier);
        // local scratch: clicks are rare (not per-frame), and statelessness keeps
        // the class safe for any future parallel worlds
        Vector3 hit = new Vector3();
        if (!Intersector.intersectRaySphere(ray, ball.pos, radius, hit)) return false;
        float ndx = MathUtils.clamp((hit.x - ball.pos.x) / radius, -1f, 1f);
        float ndy = MathUtils.clamp((hit.y - ball.pos.y) / radius, -1f, 1f);
        applyReturn(ball, cfg, ndx, ndy, powerMultiplier, paceMultiplier, towardNegativeZ,
                    cfg.basePaceSI, cfg.baseArcSI);
        return true;
    }

    /**
     * The shared offset→velocity+spin mapping. {@code ndx}/{@code ndy} ∈ [−1, 1]:
     * where the paddle brushed the ball (+ndy = above centre = topspin).
     */
    public static void applyReturn(BallState ball, PhysicsConfig cfg,
                                   float ndx, float ndy,
                                   float powerMultiplier, float paceMultiplier,
                                   boolean towardNegativeZ,
                                   float paceSI, float arcSI) {
        float travelSign = towardNegativeZ ? -1f : 1f;
        float offset = Math.min((float) Math.sqrt(ndx * ndx + ndy * ndy), 1f);

        float carry = cfg.paceCarry * Math.abs(ball.vel.z);
        float pace = (cfg.velW(paceSI) * powerMultiplier * (1f + cfg.paceOffsetGain * offset)
                      + carry) * paceMultiplier;

        float vx = cfg.velW(cfg.aimGainSI) * ndx;
        float vy = cfg.velW(arcSI) * (1f - 0.35f * ndy);

        float spinX = travelSign * cfg.spinW(cfg.topspinGainSI) * ndy;
        float spinY = travelSign * cfg.spinW(cfg.sidespinGainSI) * ndx;
        float spinZ = -cfg.spinW(cfg.sidespinGainSI) * cfg.corkscrewTilt * ndx;

        ball.spin.scl(cfg.spinTransfer); // reversed fraction of the incoming spin
        ball.spin.add(spinX, spinY, spinZ);
        ball.vel.set(vx, vy, travelSign * pace);
        clamp(ball, cfg);
    }

    /**
     * Serve variant — never fails. The click's nearest-approach offset to the
     * ball (clamped to the unit disc, damped by serveControl) shapes the serve,
     * so "click anywhere to serve" still works while aimed clicks matter.
     * Pass a null ray for a neutral centre serve.
     */
    public static void serveFromRay(Ray ray, BallState ball, PhysicsConfig cfg,
                                    float paceMultiplier, boolean towardNegativeZ) {
        float radius = hitRadius(1f);
        float ndx = 0f, ndy = 0f;
        if (ray != null) {
            Vector3 nearest = new Vector3(ball.pos).sub(ray.origin);
            float t = Math.max(0f, nearest.dot(ray.direction));
            nearest.set(ray.direction).scl(t).add(ray.origin).sub(ball.pos);
            ndx = MathUtils.clamp(nearest.x / radius, -1f, 1f) * cfg.serveControl;
            ndy = MathUtils.clamp(nearest.y / radius, -1f, 1f) * cfg.serveControl;
        }
        ball.vel.setZero();  // serves start from rest: no carry
        ball.spin.setZero(); // and no spin transfer
        applyReturn(ball, cfg, ndx, ndy, 1f, paceMultiplier, towardNegativeZ,
                    cfg.servePaceSI, cfg.serveArcSI);
    }

    /** Hard caps after all multipliers — the anti-cheat envelope. */
    public static void clamp(BallState ball, PhysicsConfig cfg) {
        ball.vel.clamp(0f, cfg.maxSpeedW());
        ball.spin.clamp(0f, cfg.maxSpinW());
    }
}
