package io.github.some_example_name.world;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.model.ArenaSide;
import io.github.some_example_name.model.ItemEffects;
import io.github.some_example_name.model.ItemType;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.model.PlayerInventory;

/**
 * 3D POC version of the duel — physics-driven for both sides.
 *
 * Coordinate convention:
 *   x — lateral, centered at 0
 *   y — vertical, gravity is -y
 *   z — depth: bot side is -z, player side is +z, net at z=0
 */
public final class MatchWorld3D {

    public static final float TABLE_HALF_WIDTH  = 3f;
    public static final float TABLE_HALF_LENGTH = 7f;
    public static final float TABLE_TOP_Y       = 2f;
    public static final float NET_HEIGHT        = 0.5f;
    public static final float NET_TOP_Y         = TABLE_TOP_Y + NET_HEIGHT;
    public static final float BALL_RADIUS       = 0.18f;
    public static final float GRAVITY           = 9.8f;

    private static final float BOUNCE_RESTITUTION = 0.7f;
    private static final int MAX_PARTICLES = 64;

    private final Vector3 sanitizedVel = new Vector3();

    public enum Phase { PREPARE_SERVE, INCOMING, OUTGOING, BOT_RESOLVE, ITEM_PHASE }

    private MatchMode matchMode = MatchMode.BOT;

    private final MatchConfig config;
    private final RandomXS128 random;
    private final DuelistState player;
    private final DuelistState bot;

    private final Vector3 ballPos     = new Vector3();
    private final Vector3 ballVel     = new Vector3();
    private final Vector3 prevBallPos = new Vector3();
    private final Vector3 hitPoint    = new Vector3();
    private final Vector3 tmpVel      = new Vector3();

    private final Array<ImpactParticle3D> particles = new Array<>();
    private final Pool<ImpactParticle3D>  particlePool = new Pool<>() {
        @Override protected ImpactParticle3D newObject() { return new ImpactParticle3D(); }
    };

    private float currentApproachDuration;

    private Phase phase = Phase.PREPARE_SERVE;
    private MatchOutcome outcome = MatchOutcome.NONE;
    private String statusText = "P1 to serve.";
    private float phaseTimer;
    private float pendingBotReturnChance;
    private float lastClickAccuracy;
    private int rallyCount;
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
    private float[] flySpawnXs = new float[0];
    private float[] flySpawnZs = new float[0];

    private int flyKilledIndex = -1;

    // Audio events — set true on relevant frames, consumed by the screen.
    private boolean paddleHitEvent;
    private boolean tableBounceEvent;

    public MatchWorld3D(MatchConfig config, RandomXS128 random) {
        this.config = config;
        this.random = random;
        player = new DuelistState(ArenaSide.PLAYER, "You", config.getFighter(ArenaSide.PLAYER));
        bot    = new DuelistState(ArenaSide.BOT,    "Bot", config.getFighter(ArenaSide.BOT));
        currentApproachDuration = config.getInitialApproachDuration();
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
        // PVP: humans click to serve; no server-side auto-serve.
        if (matchMode == MatchMode.PVP) return;
        // BOT: server bot auto-serves on a timer when it is P2's turn.
        if (nextServer != 2) return;
        phaseTimer -= delta;
        if (phaseTimer > 0f) return;
        botServe();
    }

    /** Spawns a fresh ball at the bot's end and gives it the initial impulse. */
    private void botServe() {
        float startX = (random.nextFloat() - 0.5f) * TABLE_HALF_WIDTH * 0.9f;
        float startZ = -TABLE_HALF_LENGTH + 0.4f;
        float startY = TABLE_TOP_Y + 1.2f;
        ballPos.set(startX, startY, startZ);

        applyBotImpulse();

        ballVisible = true;
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.INCOMING;
        statusText = "Click the ball as it comes through the table lane.";
    }

    /** Bot returns the ball from wherever it currently sits on the bot's side. */
    private void botReturn() {
        applyBotImpulse();
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.INCOMING;
        statusText = "Bot gets it back. Click the ball as it comes through.";
    }

    /**
     * Tuned mirror of the player's return: lands roughly mid player-side and arrives
     * at a comfortable click height. Slowed by the player's incoming-time multiplier.
     */
    private void applyBotImpulse() {
        float speedScale = (1f / Math.max(0.5f, player.getIncomingTimeMultiplier())) * p1Effects.incomingSpeedMultiplier();
        float aim = (random.nextFloat() - 0.5f) * 1.4f;
        ballVel.set(aim, 5.0f, 7.5f * speedScale);
        paddleHitEvent = true;
    }

    private void updateIncoming(float delta) {
        prevBallPos.set(ballPos);
        float prevZ = ballPos.z;
        ballVel.y -= GRAVITY * delta;
        ballPos.mulAdd(ballVel, delta);

        if (!crossedNet && prevZ < 0f && ballPos.z >= 0f) {
            crossedNet = true;
            if (ballPos.y < NET_TOP_Y) { botMissedShot(); return; } // clipped the net
        }

        if (ballPos.y <= TABLE_TOP_Y + BALL_RADIUS && ballVel.y < 0f) {
            boolean inTable = ballPos.x >= -TABLE_HALF_WIDTH && ballPos.x <= TABLE_HALF_WIDTH
                && ballPos.z >= -TABLE_HALF_LENGTH && ballPos.z <= TABLE_HALF_LENGTH;
            if (inTable && crossedNet && ballPos.z > 0f) {
                bouncesOnPlayerSide++;
                ballPos.y = TABLE_TOP_Y + BALL_RADIUS;
                ballVel.y = -ballVel.y * BOUNCE_RESTITUTION;
                tableBounceEvent = true;
                spawnBounceSparks(ballPos.x, TABLE_TOP_Y, ballPos.z);
                if (bouncesOnPlayerSide >= 2) { handlePlayerMiss(); return; }
            } else {
                botMissedShot();
                return;
            }
        }

        // Fly collision — ball hits an unswatted fly on P1's side
        if (crossedNet && ballPos.z > 0f) {
            int hit = ballHitsFly(p1Effects.flies);
            if (hit >= 0) {
                p1Effects.flies.get(hit).alive = false;
                flyKilledIndex = hit;
                handlePlayerFlyHit();
                return;
            }
        }

        if (ballPos.z > TABLE_HALF_LENGTH + 1.5f) { handlePlayerMiss(); return; }
        if (ballPos.y < 0f)                       { handlePlayerMiss(); }
    }

    private void updateOutgoing(float delta) {
        prevBallPos.set(ballPos);
        float prevZ = ballPos.z;
        ballVel.y -= GRAVITY * delta;
        ballPos.mulAdd(ballVel, delta);

        if (!crossedNet && prevZ > 0f && ballPos.z <= 0f) {
            crossedNet = true;
            if (ballPos.y < NET_TOP_Y) { handlePlayerMiss(); return; } // into the net
        }

        if (ballPos.y <= TABLE_TOP_Y + BALL_RADIUS && ballVel.y < 0f) {
            ballPos.y = TABLE_TOP_Y + BALL_RADIUS;
            boolean valid = crossedNet
                && ballPos.x >= -TABLE_HALF_WIDTH && ballPos.x <= TABLE_HALF_WIDTH
                && ballPos.z >= -TABLE_HALF_LENGTH && ballPos.z < 0f;
            if (valid) {
                ballVel.y = -ballVel.y * BOUNCE_RESTITUTION;
                tableBounceEvent = true;
                spawnBounceSparks(ballPos.x, TABLE_TOP_Y, ballPos.z);
                if (matchMode != MatchMode.PVP) {
                    // BOT mode: freeze horizontal movement so the AI can "settle" the ball.
                    ballVel.x = 0f;
                    ballVel.z = 0f;
                }
                phase = Phase.BOT_RESOLVE;
                phaseTimer = matchMode == MatchMode.PVP
                    ? GameConfig.NET_CLIENT_MISS_TIMEOUT
                    : GameConfig.BOT_RESPONSE_DELAY;
                statusText = matchMode == MatchMode.PVP
                    ? "P2 — return the ball!"
                    : "Clean return. Bot is trying to answer.";
            } else {
                handlePlayerMiss(); // bounced out of the valid landing zone
            }
            return;
        }

        // Fly collision — ball hits an unswatted fly on P2's side
        if (crossedNet && ballPos.z < 0f) {
            int hit = ballHitsFly(p2Effects.flies);
            if (hit >= 0) {
                p2Effects.flies.get(hit).alive = false;
                flyKilledIndex = hit;
                handleBotFlyHit();
                return;
            }
        }

        if (ballPos.y < 0f
            || ballPos.z < -TABLE_HALF_LENGTH - 4f
            || Math.abs(ballPos.x) > TABLE_HALF_WIDTH + 6f) {
            handlePlayerMiss(); // left the playable volume
        }
    }

    private void updateBotResolve(float delta) {
        // Keep simulating the small residual bounce so the ball doesn't snap.
        ballVel.y -= GRAVITY * delta;
        ballPos.mulAdd(ballVel, delta);

        // PVP: ball keeps its horizontal velocity (not frozen). Score the point
        // immediately if it leaves the playable area rather than waiting for the timer.
        if (matchMode == MatchMode.PVP) {
            if (ballPos.z < -TABLE_HALF_LENGTH - 2f
                || Math.abs(ballPos.x) > TABLE_HALF_WIDTH + 4f
                || ballPos.y < 0f) {
                clientMiss();
                return;
            }
        }

        if (ballPos.y <= TABLE_TOP_Y + BALL_RADIUS && ballVel.y < 0f) {
            ballPos.y = TABLE_TOP_Y + BALL_RADIUS;
            if (Math.abs(ballVel.y) < 0.5f) {
                ballVel.y = 0f;
            } else {
                ballVel.y = -ballVel.y * BOUNCE_RESTITUTION;
                if (ballVel.y >= 1.0f) {
                    tableBounceEvent = true;
                    spawnBounceSparks(ballPos.x, TABLE_TOP_Y, ballPos.z);
                }
            }
        }

        phaseTimer -= delta;
        if (phaseTimer > 0f) return;

        if (matchMode == MatchMode.PVP) {
            clientMiss();
            return;
        }

        if (random.nextFloat() <= pendingBotReturnChance) {
            currentApproachDuration = Math.max(
                config.getMinimumApproachDuration(),
                currentApproachDuration - config.getApproachDurationDecay()
            );
            botReturn();
        } else {
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
     * P1 (player) hits the ball during {@link Phase#INCOMING}.
     * Accepted only when the ball has already crossed the net to P1's side (z&nbsp;&gt;&nbsp;0).
     *
     * <p>Symmetric counterpart of {@link #acceptClientHit} for P2.</p>
     *
     * @return {@code true} if the hit was valid and applied
     */
    public boolean playerHit(float vx, float vy, float vz) {
        if (phase != Phase.INCOMING) return false;
        if (!crossedNet || ballPos.z <= 0f) return false;
        if (!HitVelocity.sanitizeNetworkReturn(1, vx, vy, vz, sanitizedVel)) return false;
        rallyCount++;
        pendingBotReturnChance = computeBotReturnChance();
        ballVel.set(sanitizedVel);
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.OUTGOING;
        statusText = "P1 returns! Ball heading to P2.";
        paddleHitEvent = true;
        return true;
    }

    /**
     * Whether P1 may currently hit the ball:
     * ball is in {@link Phase#INCOMING}, has crossed the net, and is on P1's side (z&nbsp;&gt;&nbsp;0).
     */
    public boolean isPlayerCanHit() {
        return phase == Phase.INCOMING && crossedNet && ballPos.z > 0f;
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

    /**
     * Called by the host when a HIT message arrives from the client.
     * Accepted during OUTGOING (after ball crosses net to client's side)
     * or BOT_RESOLVE (ball settled on client's side).
     */
    public boolean acceptClientHit(float vx, float vy, float vz) {
        if (phase == Phase.OUTGOING) {
            if (!crossedNet || ballPos.z > 0f) return false;
        } else if (phase != Phase.BOT_RESOLVE) {
            return false;
        }
        if (!HitVelocity.sanitizeNetworkReturn(2, vx, vy, vz, sanitizedVel)) return false;
        rallyCount++;
        ballVel.set(sanitizedVel);
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.INCOMING;
        statusText = "Opponent gets it back!";
        paddleHitEvent = true;
        return true;
    }

    /** Client timed out without hitting — score a point for the host. */
    public void clientMiss() {
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
        if (phase == Phase.OUTGOING && crossedNet && ballPos.z < 0f) return true;
        return phase == Phase.BOT_RESOLVE;
    }

    // ── player hit ───────────────────────────────────────────────────────────

    /** Returns true if the click hit the incoming ball and triggered a return. */
    public boolean tryHitBall(Ray pickRay) {
        if (phase != Phase.INCOMING) return false;
        if (ballPos.z < 0f || ballPos.z > TABLE_HALF_LENGTH + 1.5f) return false;
        if (!HitVelocity.computeFromRay(pickRay, ballPos, player.getTargetScaleMultiplier() * p1Effects.hitScaleMultiplier(),
                player.getReturnPowerMultiplier(), true, ballVel, hitPoint, tmpVel)) {
            return false;
        }

        tmpVel.set(hitPoint).sub(ballPos);
        float hitRadius = BALL_RADIUS * player.getTargetScaleMultiplier() * p1Effects.hitScaleMultiplier() * HitVelocity.CLICK_HIT_PADDING;
        float ndx = MathUtils.clamp(tmpVel.x / hitRadius, -1f, 1f);
        float ndy = MathUtils.clamp(tmpVel.y / hitRadius, -1f, 1f);
        float power = (float) Math.sqrt(ndx * ndx + ndy * ndy);
        lastClickAccuracy = 1f - MathUtils.clamp(power, 0f, 1f);
        rallyCount++;
        pendingBotReturnChance = computeBotReturnChance();
        crossedNet = false;
        phase = Phase.OUTGOING;
        statusText = "Clean return. Ball is travelling back to the bot.";
        paddleHitEvent = true;
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
            if (b*b - 4f*c >= 0f) {
                fly.alive = false;
                flyKilledIndex = i;
                return i;
            }
        }
        return -1;
    }

    /**
     * P1's serve (works in both single-player and network mode).
     * Launches the ball from the +z end toward P2 (-z), entering OUTGOING so
     * P2 (client or bot) becomes the active player.
     */
    public boolean tryPlayerServe() {
        if (phase != Phase.PREPARE_SERVE) return false;
        if (nextServer != 1) return false;
        float startX = (random.nextFloat() - 0.5f) * TABLE_HALF_WIDTH * 0.6f;
        ballPos.set(startX, TABLE_TOP_Y + 1.2f, TABLE_HALF_LENGTH - 0.5f);
        ballVel.set(0f, 5.0f, -10f * p2Effects.incomingSpeedMultiplier()); // toward P2 (−z); lands ~z=−5.4 (deep serve)
        ballVisible = true;
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.OUTGOING;
        statusText = "P1 serves. P2 must return.";
        paddleHitEvent = true;
        return true;
    }

    /**
     * Authoritative click for P1: serve when allowed, otherwise return the ball.
     */
    public boolean handlePlayerClick(Ray pickRay) {
        // Always let P1 swat flies on their side first.
        if (!p1Effects.flies.isEmpty() && trySwatFly(pickRay, 1) >= 0) return true;
        if (phase == Phase.PREPARE_SERVE && nextServer == 1) {
            return tryPlayerServe();
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
            return tryClientServe();
        }
        if (!isClientCanHit()) return false;
        if (!HitVelocity.computeFromRay(pickRay, ballPos, bot.getTargetScaleMultiplier() * p2Effects.hitScaleMultiplier(),
                bot.getReturnPowerMultiplier(), false, ballVel, hitPoint, tmpVel)) {
            return false;
        }
        rallyCount++;
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.INCOMING;
        statusText = "P2 returns! Ball heading to P1.";
        paddleHitEvent = true;
        return true;
    }

    /**
     * P2's serve in network mode. Launches the ball from the −z end toward P1 (+z),
     * entering INCOMING (P1's perspective) so P1 becomes the active player.
     */
    public boolean tryClientServe() {
        if (phase != Phase.PREPARE_SERVE) return false;
        if (nextServer != 2) return false;
        if (matchMode != MatchMode.PVP) return false; // BOT: P2 serves via server AI timer
        float startX = (random.nextFloat() - 0.5f) * TABLE_HALF_WIDTH * 0.6f;
        ballPos.set(startX, TABLE_TOP_Y + 1.2f, -TABLE_HALF_LENGTH + 0.5f);
        ballVel.set(0f, 5.0f, 10f * p1Effects.incomingSpeedMultiplier()); // toward P1 (+z)
        ballVisible = true;
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.INCOMING;
        statusText = "P2 serves. P1 must return.";
        paddleHitEvent = true;
        return true;
    }

    private void handlePlayerMiss() {
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

    private void botMissedShot() {
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
     * fly the ball's travel segment ({@link #prevBallPos} → {@link #ballPos})
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
                ballPos.x, ballPos.y, ballPos.z);
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

    private float computeBotReturnChance() {
        float speedPressure = 1f - MathUtils.clamp(
            (currentApproachDuration - config.getMinimumApproachDuration())
                / Math.max(0.001f, config.getInitialApproachDuration() - config.getMinimumApproachDuration()),
            0f, 1f);
        float chance = config.getBotBaseReturnChance();
        chance += (bot.getTargetScaleMultiplier()    - 1f) * 0.24f;
        chance += (bot.getIncomingTimeMultiplier()   - 1f) * 0.35f;
        chance -= (player.getReturnPowerMultiplier() - 1f) * 0.42f;
        chance -= speedPressure  * 0.22f;
        chance += lastClickAccuracy * 0.08f;
        return MathUtils.clamp(chance, 0.16f, 0.94f);
    }

    private void prepareServe(float delay, String status) {
        phase = Phase.PREPARE_SERVE;
        phaseTimer = delay;
        statusText = status;
        ballVisible = false;
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
            case PUNCH       -> opp.setPunchTimer(10f);
            case FLY_BAIT    -> spawnFlies(oppFx, playerNumber == 1 ? -1 : 1);
            case COIN_FLIP   -> { if (random.nextFloat() < 0.5f) self.loseLife();
                                  else opp.loseLife(); checkMatchOver(); }
        }

        itemUsedEvent  = true;
        itemUsedPlayer = playerNumber;
        itemUsedId     = item.getId();
        return true;
    }

    private void spawnFlies(ItemEffects targetFx, int sideSign) {
        int count = 2 + (int)(random.nextFloat() * 2); // 2 or 3
        float[] xs = new float[count];
        float[] zs = new float[count];
        for (int i = 0; i < count; i++) {
            xs[i] = (random.nextFloat() - 0.5f) * TABLE_HALF_WIDTH * 1.6f;
            zs[i] = sideSign * (2f + random.nextFloat() * 4f);
            targetFx.flies.add(new FlyState(xs[i], zs[i]));
        }
        flySpawnEvent = true;
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
    public float[] getFlySpawnXs() { return flySpawnXs; }
    public float[] getFlySpawnZs() { return flySpawnZs; }

    public int consumeFlyKilledIndex() {
        int v = flyKilledIndex; flyKilledIndex = -1; return v;
    }

    public byte[] getLastDealtItems(int playerNumber) {
        return playerNumber == 1 ? lastDealtP1 : lastDealtP2;
    }

    public PlayerInventory getP1Inventory() { return p1Inventory; }
    public PlayerInventory getP2Inventory() { return p2Inventory; }
    public ItemEffects getP1Effects() { return p1Effects; }
    public ItemEffects getP2Effects() { return p2Effects; }

    // ── accessors ────────────────────────────────────────────────────────────

    public Vector3 getBallPos()                 { return ballPos; }
    public Vector3 getBallVel()                 { return ballVel; }
    public float   getBallRadius()              { return BALL_RADIUS; }
    public boolean isBallVisible()              { return ballVisible; }
    public int     getPlayerLives()             { return player.getLives(); }
    public int     getBotLives()                { return bot.getLives(); }
    public int     getP2Lives()                 { return bot.getLives(); }
    public int     getRallyCount()              { return rallyCount; }
    public String  getStatusText()              { return statusText; }
    public MatchOutcome getOutcome()            { return outcome; }
    public boolean isMatchOver()                { return outcome != MatchOutcome.NONE; }
    public float   getCurrentApproachDuration() { return currentApproachDuration; }
    public Phase   getPhase()                   { return phase; }

    public float getDisplayedReadWindow() {
        return currentApproachDuration * player.getIncomingTimeMultiplier();
    }

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
}
