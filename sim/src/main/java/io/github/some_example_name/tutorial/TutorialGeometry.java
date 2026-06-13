package io.github.some_example_name.tutorial;

import io.github.some_example_name.world.physics.PhysicsConfig;

/** The drill-course geometry constants (single source of truth) + pole sweep. */
public final class TutorialGeometry {

    private TutorialGeometry() {}

    public static final ZoneRect STRIP         = new ZoneRect(-PhysicsConfig.TABLE_HALF_WIDTH, PhysicsConfig.TABLE_HALF_WIDTH, 3.5f, 6.0f);
    public static final ZoneRect AIM_L         = new ZoneRect(-2.6f, -0.8f, -5.8f, -1.2f);
    public static final ZoneRect AIM_R         = new ZoneRect( 0.8f,  2.6f, -5.8f, -1.2f);
    public static final ZoneRect TOPSPIN_NEAR  = new ZoneRect(-PhysicsConfig.TABLE_HALF_WIDTH, PhysicsConfig.TABLE_HALF_WIDTH, -3.4f, -0.8f);
    public static final ZoneRect BACKSPIN_DEEP = new ZoneRect(-PhysicsConfig.TABLE_HALF_WIDTH, PhysicsConfig.TABLE_HALF_WIDTH, -6.6f, -3.8f);
    public static final ZoneRect CURVE_ZONE    = new ZoneRect(-1.2f, 1.2f, -6.2f, -4.2f);
    public static final ZoneRect SERVE_SHORT_L = new ZoneRect(-2.6f, -0.6f, -3.2f, -0.8f);
    public static final ZoneRect SERVE_DEEP_R  = new ZoneRect( 0.6f,  2.6f, -6.4f, -4.0f);

    public static final float POLE_X = 0f;
    public static final float POLE_Z = -3f;
    public static final float POLE_RADIUS = 0.45f;
    public static final float POLE_HEIGHT = 1.4f; // above the table top

    /** Raw WORLD spin units, compared against ball.spin directly — do NOT wrap
     *  in spinW(); trips at a click ~19% off center, well below the ideal. */
    public static final float SPIN_MIN      = 10f;
    public static final float CONTACT_MIN_Z = 3.5f;
    public static final float REFEED_DELAY  = 0.8f;
    public static final float DEMO_SLOWMO   = 0.5f;

    /**
     * Swept ball-vs-pole test for one frame of travel, in the x/z plane with a
     * height gate: a hit needs the 2D segment within (poleRadius + ballRadius)
     * of the pole axis AND the ball below the pole top at the nearest point.
     *
     * <p>Approximation: the height is gated at the 2D-nearest point only, so a
     * steeply descending segment can graze the rim undetected. The blind band
     * is bounded by one substep of travel (≤ ~0.13 u) — immaterial at drill
     * speeds; revisit before reusing this at full match pace.</p>
     */
    public static boolean segmentHitsPole(float ax, float ay, float az,
                                          float bx, float by, float bz) {
        float reach = POLE_RADIUS + PhysicsConfig.BALL_RADIUS;
        float abx = bx - ax, abz = bz - az;
        float apx = POLE_X - ax, apz = POLE_Z - az;
        float len2 = abx * abx + abz * abz;
        float t = len2 > 1e-8f ? (apx * abx + apz * abz) / len2 : 0f;
        t = Math.max(0f, Math.min(1f, t));
        float cx = ax + abx * t, cz = az + abz * t;
        float dx = POLE_X - cx, dz = POLE_Z - cz;
        if (dx * dx + dz * dz > reach * reach) return false;
        float yAt = ay + (by - ay) * t;
        return yAt - PhysicsConfig.BALL_RADIUS < PhysicsConfig.TABLE_TOP_Y + POLE_HEIGHT;
    }
}
