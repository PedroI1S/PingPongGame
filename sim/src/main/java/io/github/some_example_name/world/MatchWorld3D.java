package io.github.some_example_name.world;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.Pool;
import io.github.some_example_name.network.PacketType;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.model.ArenaSide;
import io.github.some_example_name.model.ItemEffects;
import io.github.some_example_name.model.ItemType;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.model.PlayerInventory;
import io.github.some_example_name.world.physics.BallPhysics;
import io.github.some_example_name.world.physics.BallState;
import io.github.some_example_name.world.physics.BotPlanner;
import io.github.some_example_name.world.physics.PaddleContact;
import io.github.some_example_name.world.physics.PhysicsConfig;
import io.github.some_example_name.world.physics.StepContacts;

/**
 * 3D POC version of the duel — physics-driven for both sides.
 *
 * Coordinate convention:
 *   x — lateral, centered at 0
 *   y — vertical, gravity is -y
 *   z — depth: bot side is -z, player side is +z, net at z=0
 */
public final class MatchWorld3D {

    public static final float TABLE_HALF_WIDTH  = PhysicsConfig.TABLE_HALF_WIDTH;
    public static final float TABLE_HALF_LENGTH = PhysicsConfig.TABLE_HALF_LENGTH;
    public static final float TABLE_TOP_Y       = PhysicsConfig.TABLE_TOP_Y;
    public static final float NET_TOP_Y         = PhysicsConfig.NET_TOP_Y;
    public static final float NET_HEIGHT        = NET_TOP_Y - TABLE_TOP_Y;
    public static final float BALL_RADIUS       = PhysicsConfig.BALL_RADIUS;

    private static final int MAX_PARTICLES = 64;

    public enum Phase { PREPARE_SERVE, INCOMING, OUTGOING, BOT_RESOLVE, ITEM_PHASE }

    private MatchMode matchMode = MatchMode.BOT;

    private final MatchConfig config;
    private final RandomXS128 random;
    private final DuelistState player;
    private final DuelistState bot;

    private final BallState ball = new BallState();
    private final BallPhysics ballPhysics;
    private final StepContacts contacts = new StepContacts();
    private boolean netHitEvent;

    private final Vector3 prevBallPos = new Vector3();
    private final Vector3 hitPoint    = new Vector3();
    private final Vector3 tmpVel      = new Vector3();

    private final Array<ImpactParticle3D> particles = new Array<>();
    private final Pool<ImpactParticle3D>  particlePool = new Pool<>() {
        @Override protected ImpactParticle3D newObject() { return new ImpactParticle3D(); }
    };

    private final BotPlanner botPlanner;
    private final BotPlanner.Profile botProfile = new BotPlanner.Profile();
    private final BotPlanner.Plan botPlan = new BotPlanner.Plan();
    private float botPlanClock;
    private boolean botPlanArmed;
    private float rallySpeedup; // 0..1, grows each successful bot return

    private Phase phase = Phase.PREPARE_SERVE;
    private MatchOutcome outcome = MatchOutcome.NONE;
    private String statusText = "P1 to serve.";
    private float phaseTimer;
    private boolean ballVisible;
    private boolean crossedNet;
    private int bouncesOnPlayerSide;

    /**
     * Who serves the next point.  1 = P1 (player), 2 = P2 (bot in single-player,
     * remote opponent in network mode).  Whoever scores the previous point
     * serves the next one; P1 serves the very first point.
     */
    private int nextServer = 1;

    private static final float ITEM_PHASE_TIMEOUT = 15f;
    /** PVP: grace window (after the prep delay) for the serving human to click; then the serve is forfeited. */
    private static final float PVP_SERVE_TIMEOUT = 20f;
    private static final int ITEMS_PER_DEAL = 2;

    private final PlayerInventory p1Inventory = new PlayerInventory();
    private final PlayerInventory p2Inventory = new PlayerInventory();
    private final ItemEffects p1Effects = new ItemEffects();
    private final ItemEffects p2Effects = new ItemEffects();

    private boolean p1Ready;
    private boolean p2Ready;

    private byte[] lastDealtP1 = new byte[0];
    private byte[] lastDealtP2 = new byte[0];

    private boolean itemUsedEvent;
    private int itemUsedPlayer;
    private byte itemUsedId;

    private boolean flySpawnEvent;
    /** Player (1/2) on whose side the last batch of flies spawned. */
    private int flySpawnOwner;
    private float[] flySpawnXs = new float[0];
    private float[] flySpawnZs = new float[0];

    private int flyKilledIndex = -1;
    /** Player (1/2) whose fly list {@link #flyKilledIndex} points into. */
    private int flyKilledOwner;

    // Audio events — set true on relevant frames, consumed by the screen.
    private boolean paddleHitEvent;
    private boolean tableBounceEvent;

    /** Packed (code<<8 | subject) gameplay events for the client log, drained each tick by the server. */
    private final IntArray logEvents = new IntArray();

    private void logEvent(byte code, int subjectPlayer) {
        logEvents.add(((code & 0xFF) << 8) | (subjectPlayer & 0xFF));
    }

    public MatchWorld3D(MatchConfig config, RandomXS128 random) {
        this.config = config;
        this.random = random;
        ballPhysics = new BallPhysics(config.getPhysics());
        player = new DuelistState(ArenaSide.PLAYER, "You", config.getFighter(ArenaSide.PLAYER));
        bot    = new DuelistState(ArenaSide.BOT,    "Bot", config.getFighter(ArenaSide.BOT));
        botPlanner = new BotPlanner(config.getPhysics());
        // Sensible default — overwritten by setMatchMode() which the server
        // calls before the first tick.  Keeps single-player feeling natural
        // if the world is ever constructed outside a server.
        nextServer = 2;
        prepareServe(GameConfig.OPENING_DELAY, "Match starting...");
    }

    // ── update ────────────────────────────────────────────────────────────────

    public void update(float delta) {
        updateParticles(delta);
        if (outcome != MatchOutcome.NONE) return;
        switch (phase) {
            case PREPARE_SERVE -> updatePrepareServe(delta);
            case INCOMING      -> updateIncoming(delta);
            case OUTGOING      -> updateOutgoing(delta);
            case BOT_RESOLVE   -> updateBotResolve(delta);
            case ITEM_PHASE    -> updateItemPhase(delta);
        }
    }

    private void updateParticles(float delta) {
        for (int i = particles.size - 1; i >= 0; i--) {
            if (particles.get(i).update(delta)) {
                particlePool.free(particles.removeIndex(i));
            }
        }
    }

    private void spawnBounceSparks(float x, float y, float z) {
        if (particles.size >= MAX_PARTICLES) {
            return;
        }
        int count = Math.min(8 + random.nextInt(4), MAX_PARTICLES - particles.size);
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * MathUtils.PI2;
            float speed = 1.2f + random.nextFloat() * 1.8f;
            tmpVel.set(MathUtils.cos(angle) * speed,
                       1.5f + random.nextFloat() * 1.5f,
                       MathUtils.sin(angle) * speed);
            ImpactParticle3D p = particlePool.obtain();
            p.init(x, y, z, tmpVel, 0.35f + random.nextFloat() * 0.25f);
            particles.add(p);
        }
    }

    private void updatePrepareServe(float delta) {
        phaseTimer -= delta;
        // PVP: humans click to serve, but not on an open-ended clock —
        // otherwise a player who walks away traps the opponent forever.
        // phaseTimer counts down through the prep delay and keeps going
        // negative; past -PVP_SERVE_TIMEOUT the serve is forfeited.
        if (matchMode == MatchMode.PVP) {
            if (phaseTimer <= -PVP_SERVE_TIMEOUT) {
                if (nextServer == 1) handlePlayerMiss(PacketType.LOG_TIMEOUT);
                else                 botMissedShot(PacketType.LOG_TIMEOUT);
            }
            return;
        }
        // BOT: server bot auto-serves on a timer when it is P2's turn.
        if (nextServer != 2) return;
        if (phaseTimer > 0f) return;
        botServe();
    }

    /** Spawns a fresh ball at the bot's end and gives it the initial impulse. */
    private void botServe() {
        float startX = (random.nextFloat() - 0.5f) * TABLE_HALF_WIDTH * 0.9f;
        float startZ = -TABLE_HALF_LENGTH + 0.4f;
        float startY = TABLE_TOP_Y + 1.2f;
        ball.pos.set(startX, startY, startZ);
        ball.spin.setZero();
        ballPhysics.resetAccumulator();

        float ndx = (random.nextFloat() - 0.5f) * 0.6f
                  + (float) random.nextGaussian() * botProfile.aimSigma * 0.5f;
        float ndy = botProfile.aggression * 0.4f;
        PaddleContact.applyReturn(ball, config.getPhysics(),
            MathUtils.clamp(ndx, -1f, 1f), MathUtils.clamp(ndy, -1f, 1f),
            1f, p1Effects.incomingSpeedMultiplier()
                * (1f / Math.max(0.5f, player.getIncomingTimeMultiplier())),
            false, config.getPhysics().servePaceSI, config.getPhysics().serveArcSI);
        paddleHitEvent = true;

        ballVisible = true;
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.INCOMING;
        statusText = "Click the ball as it comes through the table lane.";
    }

    /** Grows toward 1 as the rally heats up; reuses the legacy approach-duration keys. */
    private float rallyPaceMultiplier() {
        return 1f + 0.6f * rallySpeedup;
    }

    /** One physics step + shared bookkeeping. Rules stay in the per-phase updates. */
    private void stepBall(float delta) {
        prevBallPos.set(ball.pos);
        ballPhysics.step(ball, delta, random, contacts);
        if (contacts.netHit) netHitEvent = true;
        if (contacts.tableBounce && Math.abs(ball.vel.y) > 0.8f) {
            tableBounceEvent = true;
            spawnBounceSparks(contacts.bounceX, TABLE_TOP_Y, contacts.bounceZ);
        }
    }

    private void updateIncoming(float delta) {
        float prevZ = ball.pos.z;
        stepBall(delta);

        if (!crossedNet && prevZ < 0f && ball.pos.z >= 0f) crossedNet = true;

        if (contacts.tableBounce) {
            if (contacts.bounceZ > 0f && crossedNet) {
                bouncesOnPlayerSide++;
                if (bouncesOnPlayerSide >= 2) { handlePlayerMiss(PacketType.LOG_DOUBLE_BOUNCE); return; }
            } else {
                botMissedShot(PacketType.LOG_OUT_OF_BOUNDS); // landed on its own side (incl. net fall-back)
                return;
            }
        }

        // Fly collision — ball hits an unswatted fly on P1's side
        if (crossedNet && ball.pos.z > 0f) {
            int hit = ballHitsFly(p1Effects.flies);
            if (hit >= 0) {
                p1Effects.flies.get(hit).alive = false;
                flyKilledIndex = hit;
                flyKilledOwner = 1;
                handlePlayerFlyHit();
                return;
            }
        }

        if (ball.pos.z > TABLE_HALF_LENGTH + 1.5f) { handlePlayerMiss(PacketType.LOG_MISS); return; }
        if (ball.pos.y < TABLE_TOP_Y) {
            // fell below table level: long/wide shot is the bot's fault unless it
            // already bounced legally on P1's side (then P1 let it drop)
            if (bouncesOnPlayerSide == 0) botMissedShot(PacketType.LOG_OUT_OF_BOUNDS);
            else handlePlayerMiss(PacketType.LOG_MISS);
        }
    }

    private void updateOutgoing(float delta) {
        float prevZ = ball.pos.z;
        stepBall(delta);

        // The bot's strikeTime is measured from the player's hit, so its clock
        // must tick through the whole flight, not just BOT_RESOLVE.
        if (botPlanArmed) botPlanClock += delta;

        if (!crossedNet && prevZ > 0f && ball.pos.z <= 0f) crossedNet = true;

        if (contacts.tableBounce) {
            boolean valid = crossedNet && contacts.bounceZ < 0f;
            if (valid) {
                phase = Phase.BOT_RESOLVE;
                phaseTimer = GameConfig.NET_CLIENT_MISS_TIMEOUT;
                statusText = matchMode == MatchMode.PVP
                    ? "P2 — return the ball!"
                    : "Clean return. Bot is trying to answer.";
            } else {
                handlePlayerMiss(PacketType.LOG_OUT_OF_BOUNDS); // bounced on own side (incl. net fall-back)
            }
            return;
        }

        // Fly collision — ball hits an unswatted fly on P2's side
        if (crossedNet && ball.pos.z < 0f) {
            int hit = ballHitsFly(p2Effects.flies);
            if (hit >= 0) {
                p2Effects.flies.get(hit).alive = false;
                flyKilledIndex = hit;
                flyKilledOwner = 2;
                handleBotFlyHit();
                return;
            }
        }

        if (ball.pos.y < TABLE_TOP_Y
            || ball.pos.z < -TABLE_HALF_LENGTH - 4f
            || Math.abs(ball.pos.x) > TABLE_HALF_WIDTH + 6f) {
            handlePlayerMiss(PacketType.LOG_OUT_OF_BOUNDS); // went long/wide or fell — P1's shot failed
        }
    }

    private void updateBotResolve(float delta) {
        stepBall(delta);

        // PVP: score immediately if the ball leaves the playable area.
        if (matchMode == MatchMode.PVP) {
            if (ball.pos.z < -TABLE_HALF_LENGTH - 2f
                || Math.abs(ball.pos.x) > TABLE_HALF_WIDTH + 4f
                || ball.pos.y < 0f) {
                clientMiss(PacketType.LOG_OUT_OF_BOUNDS);
                return;
            }
        }

        phaseTimer -= delta;

        if (matchMode == MatchMode.PVP) {
            if (phaseTimer <= 0f) clientMiss(PacketType.LOG_TIMEOUT);
            return;
        }

        // BOT mode: the planner committed to a swing time at the player's hit.
        botPlanClock += delta;
        if (!botPlanArmed) {
            if (phaseTimer <= 0f) botMissedShot(PacketType.LOG_MISS); // safety net: plan never armed
            return;
        }
        if (botPlanClock < botPlan.strikeTime) return;
        botPlanArmed = false;
        // strikeTime < 0 means the plan predicted no legal landing. The armed flag
        // is set unconditionally at the player's hit, so THIS guard (not the flag)
        // is what turns a "won't land" prediction into a miss — keep both checks.
        if (botPlan.whiff || botPlan.strikeTime < 0f) {
            botMissedShot(PacketType.LOG_MISS);
            return;
        }
        // Real-ball sanity at the swing moment: if prediction and reality ever
        // diverge (item-stacked pace, edge cases), never "save" a dead ball from
        // below the table or outside the play volume.
        if (ball.pos.y < TABLE_TOP_Y
            || ball.pos.z < -TABLE_HALF_LENGTH - 2f
            || Math.abs(ball.pos.x) > TABLE_HALF_WIDTH + 4f) {
            botMissedShot(PacketType.LOG_OUT_OF_BOUNDS);
            return;
        }
        float rampStep = config.getApproachDurationDecay() / Math.max(0.001f,
            config.getInitialApproachDuration() - config.getMinimumApproachDuration());
        rallySpeedup = Math.min(1f, rallySpeedup + rampStep);
        PaddleContact.applyReturn(ball, config.getPhysics(), botPlan.ndx, botPlan.ndy,
            bot.getReturnPowerMultiplier(),
            rallyPaceMultiplier() * p1Effects.incomingSpeedMultiplier()
                * (1f / Math.max(0.5f, player.getIncomingTimeMultiplier())),
            false, config.getPhysics().basePaceSI, config.getPhysics().baseArcSI);
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.INCOMING;
        statusText = "Bot gets it back. Click the ball as it comes through.";
        paddleHitEvent = true;
    }

    private void updateItemPhase(float delta) {
        // Punch timers must NOT tick during item selection — the effect should
        // only count down while the rally is actually in progress.
        phaseTimer -= delta;
        if ((p1Ready && p2Ready) || phaseTimer <= 0f) {
            p1Ready = false;
            p2Ready = false;
            prepareServe(GameConfig.BETWEEN_POINTS_DELAY, buildServeStatusText());
        }
    }

    private String buildServeStatusText() {
        if (matchMode == MatchMode.PVP) {
            return nextServer == 1 ? "P1 to serve." : "P2 to serve.";
        }
        return nextServer == 1 ? "Click anywhere to serve." : "Bot is preparing the opening shot.";
    }

    // ── server match API ──────────────────────────────────────────────────────

    public void setMatchMode(MatchMode mode) {
        matchMode = mode;
        // Pick a serve policy that matches the mode.  Must be called before
        // the first {@link #update} — once a phase advances past
        // PREPARE_SERVE this is a no-op.
        if (phase == Phase.PREPARE_SERVE) {
            if (mode == MatchMode.PVP) {
                nextServer = 1;
                statusText = "Match starting. P1 to serve.";
            } else {
                nextServer = 2; // bot opens — auto-serves on phaseTimer
                statusText = "Bot is preparing the opening shot.";
            }
        }
    }

    public MatchMode getMatchMode() {
        return matchMode;
    }

    /**
     * Whether P1 may currently hit the ball:
     * ball is in {@link Phase#INCOMING}, has crossed the net, and is on P1's side (z&nbsp;&gt;&nbsp;0).
     */
    public boolean isPlayerCanHit() {
        return phase == Phase.INCOMING && crossedNet && ball.pos.z > 0f;
    }

    /**
     * Returns which player is the "active" one right now.
     * <ul>
     *   <li>1 — P1 should act (serve or return)</li>
     *   <li>2 — P2 should act (return)</li>
     *   <li>0 — ball in transit, nobody acts</li>
     * </ul>
     */
    public int getActivePlayer() {
        if (phase == Phase.PREPARE_SERVE)  return nextServer;
        if (isPlayerCanHit())              return 1;
        if (isClientCanHit())              return 2;
        return 0;
    }

    /** Client timed out without hitting — score a point for the host. */
    public void clientMiss() { clientMiss(PacketType.LOG_MISS); }

    public void clientMiss(byte reason) {
        logEvent(reason, 2);
        bot.loseLife();
        if (bot.getLives() <= 0) {
            outcome = MatchOutcome.PLAYER_WIN;
            statusText = "Opponent couldn't keep up.";
            ballVisible = false;
            return;
        }
        nextServer = 1;
        enterItemPhase();
    }

    /**
     * Whether the client is allowed to hit right now: ball has crossed the
     * net to their side (OUTGOING) or bounced there and is waiting (BOT_RESOLVE).
     */
    public boolean isClientCanHit() {
        if (phase == Phase.OUTGOING && crossedNet && ball.pos.z < 0f) return true;
        return phase == Phase.BOT_RESOLVE;
    }

    // ── player hit ───────────────────────────────────────────────────────────

    /** Returns true if the click hit the incoming ball and triggered a return. */
    public boolean tryHitBall(Ray pickRay) {
        if (phase != Phase.INCOMING) return false;
        if (ball.pos.z < 0f || ball.pos.z > TABLE_HALF_LENGTH + 1.5f) return false;
        float scale = player.getTargetScaleMultiplier() * p1Effects.hitScaleMultiplier();
        float hitRadius = PaddleContact.hitRadius(scale);
        if (!Intersector.intersectRaySphere(pickRay, ball.pos, hitRadius, hitPoint)) return false;
        // A legal return requires exactly one prior bounce on P1's side.
        if (bouncesOnPlayerSide == 0) { handlePlayerMiss(PacketType.LOG_VOLLEY); return true; }

        float ndx = MathUtils.clamp((hitPoint.x - ball.pos.x) / hitRadius, -1f, 1f);
        float ndy = MathUtils.clamp((hitPoint.y - ball.pos.y) / hitRadius, -1f, 1f);
        PaddleContact.applyReturn(ball, config.getPhysics(), ndx, ndy,
            player.getReturnPowerMultiplier(),
            rallyPaceMultiplier() * p2Effects.incomingSpeedMultiplier(), true,
            config.getPhysics().basePaceSI, config.getPhysics().baseArcSI);

        crossedNet = false;
        phase = Phase.OUTGOING;
        statusText = "Clean return. Ball is travelling back to the bot.";
        paddleHitEvent = true;
        if (matchMode != MatchMode.PVP) {
            botPlanner.plan(ball, botProfile, random, botPlan);
            botPlanClock = 0f;
            botPlanArmed = true;
        }
        return true;
    }

    public int trySwatFly(Ray pickRay, int playerNumber) {
        java.util.List<FlyState> flies = playerNumber == 1 ? p1Effects.flies : p2Effects.flies;
        for (int i = 0; i < flies.size(); i++) {
            FlyState fly = flies.get(i);
            if (!fly.alive) continue;
            float flyY = TABLE_TOP_Y + 0.4f;
            float ox = pickRay.origin.x - fly.x, oy = pickRay.origin.y - flyY, oz = pickRay.origin.z - fly.z;
            float dx = pickRay.direction.x, dy = pickRay.direction.y, dz = pickRay.direction.z;
            float b = 2f * (dx*ox + dy*oy + dz*oz);
            float c = ox*ox + oy*oy + oz*oz - FlyState.FLY_RADIUS * FlyState.FLY_RADIUS;
            float disc = b*b - 4f*c;
            // Require the intersection to be in front of the ray origin.
            if (disc >= 0f && (-b + (float) Math.sqrt(disc)) >= 0f) {
                fly.alive = false;
                flyKilledIndex = i;
                flyKilledOwner = playerNumber;
                return i;
            }
        }
        return -1;
    }

    /**
     * P1's serve (works in both single-player and network mode).
     * Launches the ball from the +z end toward P2 (-z), entering OUTGOING so
     * P2 (client or bot) becomes the active player. The click ray now aims the
     * serve: center-of-ball clicks give a neutral serve, off-center clicks curve
     * it. Pass {@code null} for a neutral center serve.
     */
    public boolean tryPlayerServe(Ray pickRay) {
        if (phase != Phase.PREPARE_SERVE) return false;
        if (nextServer != 1) return false;
        float startX = (random.nextFloat() - 0.5f) * TABLE_HALF_WIDTH * 0.6f;
        ball.pos.set(startX, TABLE_TOP_Y + 1.2f, TABLE_HALF_LENGTH - 0.5f);
        ballPhysics.resetAccumulator();
        PaddleContact.serveFromRay(pickRay, ball, config.getPhysics(),
            p2Effects.incomingSpeedMultiplier(), true);
        ballVisible = true;
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.OUTGOING;
        statusText = "P1 serves. P2 must return.";
        paddleHitEvent = true;
        // Every ball launched toward the bot needs a plan — serves included,
        // exactly like tryHitBall, or the bot idles out the BOT_RESOLVE timer.
        if (matchMode != MatchMode.PVP) {
            botPlanner.plan(ball, botProfile, random, botPlan);
            botPlanClock = 0f;
            botPlanArmed = true;
        }
        return true;
    }

    /**
     * Authoritative click for P1: serve when allowed, otherwise return the ball.
     */
    public boolean handlePlayerClick(Ray pickRay) {
        // Always let P1 swat flies on their side first.
        if (!p1Effects.flies.isEmpty() && trySwatFly(pickRay, 1) >= 0) return true;
        if (phase == Phase.PREPARE_SERVE && nextServer == 1) {
            return tryPlayerServe(pickRay);
        }
        return tryHitBall(pickRay);
    }

    /**
     * Authoritative click for P2 (human PvP only).
     */
    public boolean handleOpponentClick(Ray pickRay) {
        if (matchMode != MatchMode.PVP) return false;
        // Always let P2 swat flies on their side first.
        if (!p2Effects.flies.isEmpty() && trySwatFly(pickRay, 2) >= 0) return true;
        if (phase == Phase.PREPARE_SERVE && nextServer == 2) {
            return tryClientServe(pickRay);
        }
        if (!isClientCanHit()) return false;
        if (!PaddleContact.returnFromRay(pickRay, ball, config.getPhysics(),
                bot.getTargetScaleMultiplier() * p2Effects.hitScaleMultiplier(),
                bot.getReturnPowerMultiplier(), p1Effects.incomingSpeedMultiplier(), false)) {
            return false;
        }
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.INCOMING;
        statusText = "P2 returns! Ball heading to P1.";
        paddleHitEvent = true;
        return true;
    }

    /**
     * P2's serve in network mode. Launches the ball from the −z end toward P1 (+z),
     * entering INCOMING (P1's perspective) so P1 becomes the active player. The
     * click ray now aims the serve; pass {@code null} for a neutral center serve.
     */
    public boolean tryClientServe(Ray pickRay) {
        if (phase != Phase.PREPARE_SERVE) return false;
        if (nextServer != 2) return false;
        if (matchMode != MatchMode.PVP) return false; // BOT: P2 serves via server AI timer
        float startX = (random.nextFloat() - 0.5f) * TABLE_HALF_WIDTH * 0.6f;
        ball.pos.set(startX, TABLE_TOP_Y + 1.2f, -TABLE_HALF_LENGTH + 0.5f);
        ballPhysics.resetAccumulator();
        PaddleContact.serveFromRay(pickRay, ball, config.getPhysics(),
            p1Effects.incomingSpeedMultiplier(), false);
        ballVisible = true;
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.INCOMING;
        statusText = "P2 serves. P1 must return.";
        paddleHitEvent = true;
        return true;
    }

    private void handlePlayerMiss(byte reason) {
        logEvent(reason, 1);
        player.loseLife();
        if (player.getLives() <= 0) {
            outcome = MatchOutcome.BOT_WIN;
            statusText = "The shot got through.";
            ballVisible = false;
            return;
        }
        nextServer = 2;
        enterItemPhase();
    }

    private void botMissedShot(byte reason) {
        logEvent(reason, 2);
        bot.loseLife();
        if (bot.getLives() <= 0) {
            outcome = MatchOutcome.PLAYER_WIN;
            statusText = "The bot could not handle the pressure.";
            ballVisible = false;
            return;
        }
        nextServer = 1;
        enterItemPhase();
    }

    private void handlePlayerFlyHit() {
        player.loseLife();
        if (player.getLives() <= 0) {
            outcome = MatchOutcome.BOT_WIN; ballVisible = false; return;
        }
        nextServer = 2;
        enterItemPhase();
    }

    private void handleBotFlyHit() {
        bot.loseLife();
        if (bot.getLives() <= 0) {
            outcome = MatchOutcome.PLAYER_WIN; ballVisible = false; return;
        }
        nextServer = 1;
        enterItemPhase();
    }

    /**
     * Swept ball-vs-fly test for this frame. Returns the index of the first live
     * fly the ball's travel segment ({@link #prevBallPos} → {@link #ball}{@code .pos})
     * passes within {@code FLY_RADIUS + BALL_RADIUS} of, or {@code -1} for none.
     *
     * <p>Using the swept segment (not just the endpoint) stops a fast ball from
     * tunnelling between 60 Hz steps, and the combined radius makes a visual
     * overlap actually register as a hit.</p>
     */
    private int ballHitsFly(java.util.List<FlyState> flies) {
        final float flyY = TABLE_TOP_Y + 0.4f;
        final float reach = FlyState.FLY_RADIUS + BALL_RADIUS;
        final float reach2 = reach * reach;
        for (int i = 0; i < flies.size(); i++) {
            FlyState fly = flies.get(i);
            if (!fly.alive) continue;
            float d2 = distSqPointToSegment(fly.x, flyY, fly.z,
                prevBallPos.x, prevBallPos.y, prevBallPos.z,
                ball.pos.x, ball.pos.y, ball.pos.z);
            if (d2 < reach2) return i;
        }
        return -1;
    }

    /** Squared distance from point P to segment AB (clamped to the segment). */
    static float distSqPointToSegment(float px, float py, float pz,
                                      float ax, float ay, float az,
                                      float bx, float by, float bz) {
        float abx = bx - ax, aby = by - ay, abz = bz - az;
        float apx = px - ax, apy = py - ay, apz = pz - az;
        float abLen2 = abx * abx + aby * aby + abz * abz;
        float t = abLen2 > 1e-8f ? (apx * abx + apy * aby + apz * abz) / abLen2 : 0f;
        t = MathUtils.clamp(t, 0f, 1f);
        float cx = ax + abx * t, cy = ay + aby * t, cz = az + abz * t;
        float dx = px - cx, dy = py - cy, dz = pz - cz;
        return dx * dx + dy * dy + dz * dz;
    }

    private void prepareServe(float delay, String status) {
        phase = Phase.PREPARE_SERVE;
        phaseTimer = delay;
        statusText = status;
        ballVisible = false;
        botPlanArmed = false;
    }

    public void enterItemPhase() {
        p1Effects.clear();
        p2Effects.clear();
        ItemType[] pool = ItemType.values();
        lastDealtP1 = dealItems(p1Inventory, pool, ITEMS_PER_DEAL);
        lastDealtP2 = dealItems(p2Inventory, pool, ITEMS_PER_DEAL);
        p1Ready = false;
        p2Ready = false;
        phase = Phase.ITEM_PHASE;
        ballVisible = false;
        phaseTimer = ITEM_PHASE_TIMEOUT;
        statusText = "Use your items, then press READY.";
    }

    private byte[] dealItems(PlayerInventory inv, ItemType[] pool, int count) {
        java.util.List<Byte> dealt = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            ItemType item = pool[random.nextInt(pool.length)];
            if (inv.add(item)) dealt.add(item.getId());
        }
        byte[] arr = new byte[dealt.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = dealt.get(i);
        return arr;
    }

    public void playerReady(int playerNumber) {
        if (phase != Phase.ITEM_PHASE) return;
        if (playerNumber == 1) p1Ready = true;
        else p2Ready = true;
        if (p1Ready && p2Ready) {
            p1Ready = false;
            p2Ready = false;
            prepareServe(GameConfig.BETWEEN_POINTS_DELAY, buildServeStatusText());
        }
    }

    public boolean applyItem(int playerNumber, ItemType item) {
        if (phase != Phase.ITEM_PHASE) return false;
        PlayerInventory inv    = playerNumber == 1 ? p1Inventory : p2Inventory;
        PlayerInventory oppInv = playerNumber == 1 ? p2Inventory : p1Inventory;
        DuelistState self      = playerNumber == 1 ? player : bot;
        DuelistState opp       = playerNumber == 1 ? bot    : player;
        ItemEffects selfFx     = playerNumber == 1 ? p1Effects : p2Effects;
        ItemEffects oppFx      = playerNumber == 1 ? p2Effects : p1Effects;

        if (!inv.remove(item)) return false;

        switch (item) {
            case PATCH_KIT   -> self.addLife(GameConfig.DEFAULT_LIVES);
            case WIDE_PADDLE -> selfFx.wideClick    = true;
            case SLOW_MO     -> selfFx.slowIncoming = true;
            case STEAL       -> { ItemType stolen = oppInv.steal(random);
                                  if (stolen != null) inv.add(stolen); }
            case FAST_SERVE  -> oppFx.fastIncoming  = true;
            case TINY_PADDLE -> oppFx.tinyPaddleActive = true;
            // PUNCH is purely visual: clients apply a 10s blur when the
            // ITEM_USED broadcast arrives — no server-side state needed.
            case PUNCH       -> { }
            case FLY_BAIT    -> spawnFlies(oppFx, playerNumber == 1 ? 2 : 1);
            case COIN_FLIP   -> { if (random.nextFloat() < 0.5f) self.loseLife();
                                  else opp.loseLife(); checkMatchOver(); }
        }

        itemUsedEvent  = true;
        itemUsedPlayer = playerNumber;
        itemUsedId     = item.getId();
        return true;
    }

    private void spawnFlies(ItemEffects targetFx, int ownerPlayer) {
        int sideSign = ownerPlayer == 1 ? 1 : -1; // P1 side is +z, P2 side is -z
        int count = 2 + (int)(random.nextFloat() * 2); // 2 or 3
        float[] xs = new float[count];
        float[] zs = new float[count];
        for (int i = 0; i < count; i++) {
            xs[i] = (random.nextFloat() - 0.5f) * TABLE_HALF_WIDTH * 1.6f;
            zs[i] = sideSign * (2f + random.nextFloat() * 4f);
            targetFx.flies.add(new FlyState(xs[i], zs[i]));
        }
        flySpawnEvent = true;
        flySpawnOwner = ownerPlayer;
        flySpawnXs = xs;
        flySpawnZs = zs;
    }

    private void checkMatchOver() {
        if (player.getLives() <= 0) { outcome = MatchOutcome.BOT_WIN; ballVisible = false; }
        else if (bot.getLives() <= 0) { outcome = MatchOutcome.PLAYER_WIN; ballVisible = false; }
    }

    public boolean consumeItemUsedEvent() {
        boolean v = itemUsedEvent; itemUsedEvent = false; return v;
    }
    public int getItemUsedPlayer() { return itemUsedPlayer; }
    public byte getItemUsedId()    { return itemUsedId; }

    public boolean consumeFlySpawnEvent() {
        boolean v = flySpawnEvent; flySpawnEvent = false; return v;
    }
    public int     getFlySpawnOwner() { return flySpawnOwner; }
    public float[] getFlySpawnXs()    { return flySpawnXs; }
    public float[] getFlySpawnZs()    { return flySpawnZs; }

    public int consumeFlyKilledIndex() {
        int v = flyKilledIndex; flyKilledIndex = -1; return v;
    }
    public int getFlyKilledOwner() { return flyKilledOwner; }

    public byte[] getLastDealtItems(int playerNumber) {
        return playerNumber == 1 ? lastDealtP1 : lastDealtP2;
    }

    public PlayerInventory getP1Inventory() { return p1Inventory; }
    public PlayerInventory getP2Inventory() { return p2Inventory; }
    public ItemEffects getP1Effects() { return p1Effects; }
    public ItemEffects getP2Effects() { return p2Effects; }

    // ── accessors ────────────────────────────────────────────────────────────

    public Vector3 getBallPos()                 { return ball.pos; }
    public Vector3 getBallVel()                 { return ball.vel; }
    public Vector3 getBallSpin()                { return ball.spin; }
    public float   getBallRadius()              { return BALL_RADIUS; }
    /** The bot's live difficulty knobs (mutable — the tutorial softens them). */
    public BotPlanner.Profile getBotProfile()  { return botProfile; }
    public boolean isBallVisible()              { return ballVisible; }
    public int     getPlayerLives()             { return player.getLives(); }
    public int     getBotLives()                { return bot.getLives(); }
    public int     getP2Lives()                 { return bot.getLives(); }
    public String  getStatusText()              { return statusText; }
    public MatchOutcome getOutcome()            { return outcome; }
    public boolean isMatchOver()                { return outcome != MatchOutcome.NONE; }
    public Phase   getPhase()                   { return phase; }

    public Array<ImpactParticle3D> getParticles() { return particles; }

    public boolean consumePaddleHitEvent() {
        boolean v = paddleHitEvent;
        paddleHitEvent = false;
        return v;
    }

    public boolean consumeTableBounceEvent() {
        boolean v = tableBounceEvent;
        tableBounceEvent = false;
        return v;
    }

    public boolean consumeNetHitEvent() {
        boolean v = netHitEvent;
        netHitEvent = false;
        return v;
    }

    public boolean hasLogEvent() { return logEvents.size > 0; }
    /** Removes and returns the oldest packed event: {@code (code<<8) | subject}. */
    public int pollLogEvent() { return logEvents.removeIndex(0); }
}
