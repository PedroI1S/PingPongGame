package io.github.some_example_name.tutorial;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import io.github.some_example_name.world.physics.BallPhysics;
import io.github.some_example_name.world.physics.BallState;
import io.github.some_example_name.world.physics.PaddleContact;
import io.github.some_example_name.world.physics.PhysicsConfig;
import io.github.some_example_name.world.physics.StepContacts;

import java.util.List;

/**
 * The guided drill course: owns its ball + physics, feeds practice balls,
 * evaluates attempts against the drill's zones, and narrates one instruction
 * line at a time. Drills 1-6; the graduation rally (7) is a real local
 * MatchWorld3D owned by the screen.
 *
 * <p>The zone is the lesson: each drill's target is only comfortably reachable
 * with the technique being taught. Success predicates read real physics state
 * (post-contact spin signs, landing coordinates) — the same state a match
 * plays. Pure logic: no rendering, fully headless-testable.</p>
 */
public final class DrillCourse {

    public enum DrillId { TIMING, AIM, TOPSPIN, BACKSPIN, CURVE, SERVE }

    private enum BallMode { NONE, WAIT_FEED, INCOMING, DEMO, RETURNED, WAIT_SERVE, SERVED }

    private final PhysicsConfig cfg;
    private final BallPhysics physics;
    private final BallState ball = new BallState();
    private final StepContacts contacts = new StepContacts();
    private final RandomXS128 random;
    private final float baseTimeScale;

    private DrillId drill = DrillId.TIMING;
    private int progress;
    private boolean complete;

    private BallMode mode = BallMode.WAIT_FEED;
    private float feedTimer = 0.6f;
    private boolean demoDoneForDrill;
    private int feedSide = 1;          // curve drill alternates ±1
    private int aimZoneIndex;          // 0 = L, 1 = R (aim + serve drills)

    private float contactZ;            // ball z at the player's contact
    private float contactSpinX, contactSpinY;  // spin at contact time (Task 3/4 evaluation)
    private String feedback = "";
    private float feedbackTimer;

    private boolean successEvent, failEvent, paddleHitEvent, tableBounceEvent, poleHitEvent;

    public DrillCourse(PhysicsConfig cfg, RandomXS128 random) {
        this.cfg = cfg;
        this.random = random;
        this.physics = new BallPhysics(cfg);
        this.baseTimeScale = cfg.timeScale;
    }

    // ── frame update ─────────────────────────────────────────────────────────

    public void update(float delta) {
        if (complete) return;
        if (feedbackTimer > 0f) {
            feedbackTimer -= delta;
            if (feedbackTimer <= 0f) feedback = "";
        }
        switch (mode) {
            case WAIT_FEED -> {
                feedTimer -= delta;
                if (feedTimer <= 0f) feed();
            }
            case INCOMING, DEMO, RETURNED, SERVED -> stepBall(delta);
            case WAIT_SERVE, NONE -> { }
        }
    }

    private void stepBall(float delta) {
        float prevX = ball.pos.x, prevY = ball.pos.y, prevZ = ball.pos.z;
        physics.step(ball, delta, random, contacts);
        if (contacts.tableBounce && Math.abs(ball.vel.y) > 0.8f) tableBounceEvent = true;

        if ((mode == BallMode.RETURNED || mode == BallMode.SERVED) && drill == DrillId.CURVE
            && TutorialGeometry.segmentHitsPole(prevX, prevY, prevZ,
                                                ball.pos.x, ball.pos.y, ball.pos.z)) {
            poleHitEvent = true;
            fail("Clipped the pole — bend it wider.");
            return;
        }

        switch (mode) {
            case INCOMING, DEMO -> {
                // ball heading to the player; nothing to evaluate until it dies
                if (ball.pos.z > PhysicsConfig.TABLE_HALF_LENGTH + 1.5f
                    || ball.pos.y < PhysicsConfig.TABLE_TOP_Y) {
                    if (mode == BallMode.DEMO) endAttempt(0.4f);
                    else fail("Missed — free ball, go again.");
                }
            }
            case RETURNED, SERVED -> {
                if (contacts.netHit) { fail("Into the net — a little higher."); return; }
                if (contacts.tableBounce) {
                    if (contacts.bounceZ < 0f) { evaluateLanding(contacts.bounceX, contacts.bounceZ); }
                    else fail("It came back on your side — hit it forward.");
                    return;
                }
                if (ball.pos.y < PhysicsConfig.TABLE_TOP_Y
                    || Math.abs(ball.pos.x) > PhysicsConfig.TABLE_HALF_WIDTH + 6f
                    || ball.pos.z < -PhysicsConfig.TABLE_HALF_LENGTH - 4f) {
                    fail("Out. Free ball — again.");
                }
            }
            default -> { }
        }
    }

    // ── feeding ──────────────────────────────────────────────────────────────

    private void feed() {
        cfg.timeScale = baseTimeScale;
        ball.reset();
        physics.resetAccumulator();
        switch (drill) {
            case TIMING, AIM, TOPSPIN, BACKSPIN -> {
                boolean demo = !demoDoneForDrill
                    && (drill == DrillId.TOPSPIN || drill == DrillId.BACKSPIN);
                feedFromFarSide(0f, demo);
                if (demo) {
                    // pre-spun, slow-motion demonstration ball
                    cfg.timeScale = baseTimeScale * TutorialGeometry.DEMO_SLOWMO;
                    ball.spin.set(drill == DrillId.TOPSPIN ? 28f : -28f, 0f, 0f);
                    mode = BallMode.DEMO;
                    demoDoneForDrill = true;
                } else {
                    mode = BallMode.INCOMING;
                }
            }
            case CURVE -> {
                if (!demoDoneForDrill) {
                    // Demonstrate the EXACT shot to copy: a slow-mo ideal
                    // inner-edge return from the player's contact spot, bending
                    // around the pole into the zone. (An incoming pre-spun demo
                    // would curve with MIRRORED handedness on screen — Magnus
                    // flips with travel direction — teaching the wrong picture.)
                    ball.pos.set(feedSide * 1.5f, PhysicsConfig.TABLE_TOP_Y + 0.8f, 4.5f);
                    PaddleContact.applyReturn(ball, cfg, -feedSide * 0.85f, -0.5f,
                                              1f, 1f, true,
                                              cfg.basePaceSI, cfg.baseArcSI);
                    cfg.timeScale = baseTimeScale * TutorialGeometry.DEMO_SLOWMO;
                    mode = BallMode.DEMO;
                    demoDoneForDrill = true;
                } else {
                    feedFromFarSide(feedSide * 1.5f, false);
                    mode = BallMode.INCOMING;
                }
            }
            case SERVE -> {
                ball.pos.set(0f, PhysicsConfig.TABLE_TOP_Y + 1.2f,
                             PhysicsConfig.TABLE_HALF_LENGTH - 0.5f);
                mode = BallMode.WAIT_SERVE; // frozen until the click
            }
        }
    }

    /** A coach feed: the same paddle model the player uses, from the far side. */
    private void feedFromFarSide(float startX, boolean demo) {
        ball.pos.set(startX + (random.nextFloat() - 0.5f) * 0.8f,
                     PhysicsConfig.TABLE_TOP_Y + 1.0f,
                     -PhysicsConfig.TABLE_HALF_LENGTH + 0.5f);
        float aim = (random.nextFloat() - 0.5f) * 0.2f
                  + (drill == DrillId.CURVE ? feedSide * 0.2f : 0f);
        PaddleContact.applyReturn(ball, cfg, aim, -0.1f, 1f, 0.95f, false,
                                  cfg.basePaceSI, cfg.baseArcSI);
    }

    // ── player input ─────────────────────────────────────────────────────────

    /** Routes a click ray to the right attempt; returns true if it connected. */
    public boolean click(Ray pickRay) {
        if (mode == BallMode.DEMO) {
            setFeedback("Watch first — your ball is next.", 1.2f);
            return false;
        }
        if (mode == BallMode.WAIT_SERVE) {
            float radius = PaddleContact.hitRadius(1f);
            Vector3 n = new Vector3(ball.pos).sub(pickRay.origin);
            float t = Math.max(0f, n.dot(pickRay.direction));
            n.set(pickRay.direction).scl(t).add(pickRay.origin).sub(ball.pos);
            return attemptServe(MathUtils.clamp(n.x / radius, -1f, 1f),
                                MathUtils.clamp(n.y / radius, -1f, 1f));
        }
        if (mode != BallMode.INCOMING || ball.pos.z <= 0f) return false;
        float radius = PaddleContact.hitRadius(1f);
        Vector3 hit = new Vector3();
        if (!Intersector.intersectRaySphere(pickRay, ball.pos, radius, hit)) return false;
        return attemptReturn(MathUtils.clamp((hit.x - ball.pos.x) / radius, -1f, 1f),
                             MathUtils.clamp((hit.y - ball.pos.y) / radius, -1f, 1f));
    }

    /** The beatability hook: a return contact at the given offsets. */
    public boolean attemptReturn(float ndx, float ndy) {
        if (mode != BallMode.INCOMING || ball.pos.z <= 0f) return false;
        contactZ = ball.pos.z;
        PaddleContact.applyReturn(ball, cfg, ndx, ndy, 1f, 1f, true,
                                  cfg.basePaceSI, cfg.baseArcSI);
        contactSpinX = ball.spin.x;
        contactSpinY = ball.spin.y;
        paddleHitEvent = true;
        mode = BallMode.RETURNED;
        return true;
    }

    /**
     * The serve counterpart (offsets are RAW; serveControl is applied here).
     *
     * <p>This method hand-rolls the serve rather than delegating to
     * {@code PaddleContact.serveFromRay} for two reasons:
     * <ol>
     *   <li>It is the beatability test seam — tests inject offsets (ndx/ndy)
     *       directly; {@code serveFromRay} computes those offsets from a Ray,
     *       which requires rendering-side geometry.</li>
     *   <li>The {@code serveControl} pre-multiplication here MUST stay in
     *       lockstep with {@code serveFromRay}'s identical scaling so that
     *       the match and the drill course produce the same ball physics.</li>
     * </ol>
     * The feed already called {@code resetAccumulator()} when placing the frozen
     * ball (WAIT_SERVE), so there is no need to reset it again here.
     */
    public boolean attemptServe(float ndx, float ndy) {
        if (mode != BallMode.WAIT_SERVE) return false;
        ball.vel.setZero();
        ball.spin.setZero();
        PaddleContact.applyReturn(ball, cfg,
            ndx * cfg.serveControl, ndy * cfg.serveControl, 1f, 1f, true,
            cfg.servePaceSI, cfg.serveArcSI);
        contactSpinX = ball.spin.x;
        contactSpinY = ball.spin.y;
        paddleHitEvent = true;
        mode = BallMode.SERVED;
        return true;
    }

    // ── evaluation ───────────────────────────────────────────────────────────

    private void evaluateLanding(float x, float z) {
        switch (drill) {
            case TIMING -> {
                if (contactZ >= TutorialGeometry.CONTACT_MIN_Z) succeed("Clean.");
                else fail("Too early — let it cross the glow.");
            }
            case AIM -> {
                if (activeZone().contains(x, z)) {
                    aimZoneIndex = 1 - aimZoneIndex;
                    succeed("On target.");
                } else fail("Off target — aim with the click side.");
            }
            case TOPSPIN -> {
                boolean spun = contactSpinX < -TutorialGeometry.SPIN_MIN; // −z travel topspin
                boolean inBand = TutorialGeometry.TOPSPIN_NEAR.contains(x, z);
                if (spun && inBand) succeed("That dive — that's topspin.");
                else if (!spun) fail("No topspin — click the TOP half of the ball.");
                // name the side that was actually missed — never guess the direction
                else if (z > TutorialGeometry.TOPSPIN_NEAR.z1) fail("Too short — ease up, land it past the line.");
                else fail("Too deep — the dive should drop it in short.");
            }
            case BACKSPIN -> {
                boolean spun = contactSpinX > TutorialGeometry.SPIN_MIN;
                boolean inBand = TutorialGeometry.BACKSPIN_DEEP.contains(x, z);
                if (spun && inBand) succeed("Floated deep — that's backspin.");
                else if (!spun) fail("No backspin — click the BOTTOM half.");
                else if (z > TutorialGeometry.BACKSPIN_DEEP.z1) fail("Too short — let the float carry it deep.");
                else fail("Too deep — nearly off the end. A touch softer.");
            }
            case CURVE -> {
                boolean spun = Math.abs(contactSpinY) > TutorialGeometry.SPIN_MIN;
                boolean inZone = TutorialGeometry.CURVE_ZONE.contains(x, z);
                if (spun && inZone) succeed("Bent it — beautiful.");
                else if (!spun) fail("No curve — click the side edge of the ball.");
                else fail("Around, but off the zone — start the aim wider.");
            }
            case SERVE -> {
                ZoneRect zone = activeZone();
                if (zone.contains(x, z)) {
                    aimZoneIndex = 1 - aimZoneIndex;
                    succeed(aimZoneIndex == 1 ? "Short and tight. Now go deep." : "Served.");
                } else if (z >= zone.z0 && z <= zone.z1) {
                    // depth is right — the miss is purely lateral; coaching click
                    // height here would mislead (the depth messages below assume
                    // a depth miss)
                    fail(x < zone.x0 ? "Right depth — aim it further RIGHT."
                                     : "Right depth — aim it further LEFT.");
                } else {
                    // Name the side that was ACTUALLY missed — never guess the
                    // direction. Both zones have meaningful z0 (near edge) and z1
                    // (far edge); a bounce past z0 is "too deep", past z1 "too short".
                    // For SERVE_SHORT_L: z0=−3.2, z1=−0.8 (closer to player).
                    // For SERVE_DEEP_R:  z0=−6.4, z1=−4.0 (closer to far end).
                    // "Too deep" (z < z0) on SHORT means the serve went long.
                    // "Too deep" (z < z0) on DEEP means it sailed past the far
                    //   edge — still too long relative to the zone's far side.
                    // Serves that go OOB (below table or past z=−11) never reach
                    // here; they're caught by the "Out" branch in stepBall.
                    if (z < zone.z0) fail(zone == TutorialGeometry.SERVE_SHORT_L
                        ? "Long — click HIGHER on the ball for a short serve."
                        : "Too deep — click HIGHER to bring it back in the zone.");
                    else fail(zone == TutorialGeometry.SERVE_SHORT_L
                        ? "Too short — click LOWER to push it deeper."
                        : "Short — click LOWER to float it deep.");
                }
            }
        }
    }

    private void succeed(String msg) {
        progress++;
        successEvent = true;
        setFeedback(msg, 1.0f);
        if (progress >= required()) advance();
        endAttempt(TutorialGeometry.REFEED_DELAY);
    }

    private void fail(String msg) {
        failEvent = true;
        setFeedback(msg, 1.6f);
        endAttempt(TutorialGeometry.REFEED_DELAY);
    }

    private void endAttempt(float delay) {
        cfg.timeScale = baseTimeScale;
        // demos don't flip the side: the first real ball must arrive on the
        // same side the demonstration just showed
        if (drill == DrillId.CURVE && mode != BallMode.DEMO) feedSide = -feedSide;
        mode = BallMode.WAIT_FEED;
        feedTimer = delay;
    }

    private void advance() {
        progress = 0;
        demoDoneForDrill = false;
        aimZoneIndex = 0;
        DrillId[] order = DrillId.values();
        int next = drill.ordinal() + 1;
        if (next >= order.length) complete = true;
        else drill = order[next];
    }

    private void setFeedback(String msg, float seconds) {
        feedback = msg;
        feedbackTimer = seconds;
    }

    // ── accessors for the screen ─────────────────────────────────────────────

    public BallState ball()          { return ball; }
    public boolean isBallVisible()   { return mode != BallMode.WAIT_FEED && mode != BallMode.NONE; }
    public boolean isDemoBall()      { return mode == BallMode.DEMO; }
    public DrillId drill()           { return drill; }
    public int drillNumber()         { return drill.ordinal() + 1; }
    public static int drillCount()   { return DrillId.values().length; }
    public boolean isComplete()      { return complete; }
    public int progress()            { return progress; }
    public String feedback()         { return feedback; }

    public int required() {
        return switch (drill) {
            case TIMING, TOPSPIN, BACKSPIN -> 3;
            case AIM -> 4;       // 2 per side, alternating
            case CURVE, SERVE -> 2;
        };
    }

    public String instruction() {
        return switch (drill) {
            case TIMING   -> "Let the ball reach the glowing strip, then click it.";
            case AIM      -> "Land it in the lit zone — click that side of the ball.";
            case TOPSPIN  -> "Click the TOP of the ball — the dive drops it in short.";
            case BACKSPIN -> "Click the BOTTOM — the float carries it deep.";
            case CURVE    -> "Bend it around the pole into the zone — click the side edge.";
            case SERVE    -> "Serve into the lit zone — high click = short, low click = deep.";
        };
    }

    public List<ZoneRect> zones() {
        return switch (drill) {
            case TIMING   -> List.of(TutorialGeometry.STRIP);
            case AIM      -> List.of(TutorialGeometry.AIM_L, TutorialGeometry.AIM_R);
            case TOPSPIN  -> List.of(TutorialGeometry.TOPSPIN_NEAR);
            case BACKSPIN -> List.of(TutorialGeometry.BACKSPIN_DEEP);
            case CURVE    -> List.of(TutorialGeometry.CURVE_ZONE);
            case SERVE    -> List.of(TutorialGeometry.SERVE_SHORT_L, TutorialGeometry.SERVE_DEEP_R);
        };
    }

    /** The single zone that counts right now; null when every listed zone counts. */
    public ZoneRect activeZone() {
        return switch (drill) {
            case AIM   -> aimZoneIndex == 0 ? TutorialGeometry.AIM_L : TutorialGeometry.AIM_R;
            case SERVE -> aimZoneIndex == 0 ? TutorialGeometry.SERVE_SHORT_L : TutorialGeometry.SERVE_DEEP_R;
            case TIMING, TOPSPIN, BACKSPIN, CURVE -> null;
        };
    }

    public boolean hasPole() { return drill == DrillId.CURVE; }

    public boolean consumeSuccessEvent()     { boolean v = successEvent;     successEvent = false;     return v; }
    public boolean consumeFailEvent()        { boolean v = failEvent;        failEvent = false;        return v; }
    public boolean consumePaddleHitEvent()   { boolean v = paddleHitEvent;   paddleHitEvent = false;   return v; }
    public boolean consumeTableBounceEvent() { boolean v = tableBounceEvent; tableBounceEvent = false; return v; }
    public boolean consumePoleHitEvent()     { boolean v = poleHitEvent;     poleHitEvent = false;     return v; }
}
