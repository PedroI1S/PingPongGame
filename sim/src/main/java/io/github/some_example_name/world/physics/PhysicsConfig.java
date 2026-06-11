package io.github.some_example_name.world.physics;

/**
 * All physics constants in one place.
 *
 * <p>SI values (meters, seconds, radians): drag and restitution are from
 * Lin, Yu &amp; Huang, "Ball Tracking and Trajectory Prediction for
 * Table-Tennis Robots", Sensors 2020, §4.2. The sim runs in world units; the
 * {@code *W()} accessors convert exactly, with U = unitsPerMeter and
 * σ = timeScale:</p>
 *
 * <pre>
 *   x_w(τ) = U·x_si(σ·τ)   v_w = U·σ·v_si   a_w = U·σ²·a_si
 *   ω_w = σ·ω_si           k_drag_w = k_drag_si / U   (magnus k unchanged)
 * </pre>
 *
 * <p>σ is exact slow motion: it never changes a trajectory's shape, only how
 * fast it plays. σ = 1 is real table-tennis tempo; the default ≈ today's pace.</p>
 */
public final class PhysicsConfig {

    // ── table geometry (world units, fixed) ─────────────────────────────────
    public static final float TABLE_HALF_WIDTH  = 3f;
    public static final float TABLE_HALF_LENGTH = 7f;
    public static final float TABLE_TOP_Y       = 2f;
    public static final float NET_TOP_Y         = 2.5f;
    public static final float BALL_RADIUS       = 0.18f;

    // ── world mapping ────────────────────────────────────────────────────────
    /** 14-unit game table / 2.74 m real table. */
    public float unitsPerMeter = 5.11f;
    /** Slow-motion factor; 1 = real tempo. Default reproduces gravity_w ≈ 9.8. */
    public float timeScale = 0.442f;

    // ── flight (SI) ──────────────────────────────────────────────────────────
    public float gravitySI = 9.81f;
    /** Cd·ρ·A/2m = 0.5·1.29·1.3e-3/(2·2.7e-3) [1/m] (paper). */
    public float dragKSI = 0.155f;
    /** Magnus a = k·(ω × v); standard smooth-sphere value, dimensionless. */
    public float magnusK = 0.006f;
    /** Spin decay time constant in flight [s]. */
    public float spinDecayTauSI = 4f;

    // ── table contact ────────────────────────────────────────────────────────
    /** Restitution measured over 330 real trajectories (paper). */
    public float restitution = 0.92f;
    /** Sliding friction coefficient at the bounce. */
    public float bounceFriction = 0.25f;
    /** Spin kept after each table contact. */
    public float spinKeptOnBounce = 0.7f;

    // ── net contact ──────────────────────────────────────────────────────────
    /** Forward-speed fraction kept on a net hit (center of jitter range). */
    public float netForwardKeep = 0.1f;
    /** Jitter half-range; a negative draw means the ball falls back. */
    public float netJitter = 0.18f;
    public float netVerticalKeep = 0.6f;
    public float netLateralKeep  = 0.8f;
    public float netSpinKeep     = 0.5f;

    // ── paddle gains (SI; PaddleContact converts) ────────────────────────────
    public float basePaceSI = 4.0f;
    public float baseArcSI  = 2.2f;
    public float aimGainSI  = 1.4f;
    public float paceOffsetGain = 0.25f;
    public float paceCarry      = 0.35f;
    public float topspinGainSI  = 120f;
    public float sidespinGainSI = 100f;
    public float corkscrewTilt  = 0.5f;
    public float spinTransfer   = -0.3f;
    public float servePaceSI    = 4.4f;
    public float serveArcSI     = 2.4f;
    /** Serve offsets are scaled by this — serves are more controlled. */
    public float serveControl   = 0.6f;

    // ── clamps (anti-cheat envelope) ─────────────────────────────────────────
    public float maxSpeedSI = 14f;
    public float maxSpinSI  = 180f;

    // ── derived world-unit accessors ─────────────────────────────────────────
    public float gravityW()      { return gravitySI * unitsPerMeter * timeScale * timeScale; }
    public float dragKW()        { return dragKSI / unitsPerMeter; }
    public float spinDecayTauW() { return spinDecayTauSI / timeScale; }
    public float velW(float vSI)   { return vSI * unitsPerMeter * timeScale; }
    public float spinW(float wSI)  { return wSI * timeScale; }
    public float maxSpeedW()     { return velW(maxSpeedSI); }
    public float maxSpinW()      { return spinW(maxSpinSI); }

    public static PhysicsConfig createDefault() { return new PhysicsConfig(); }
}
