package io.github.some_example_name.world;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.model.ArenaSide;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchOutcome;

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

    private static final float CLICK_HIT_PADDING  = 3.5f;
    private static final float BOUNCE_RESTITUTION = 0.7f;

    public enum Phase { PREPARE_SERVE, INCOMING, OUTGOING, BOT_RESOLVE }

    private boolean networkMode;

    private final MatchConfig config;
    private final RandomXS128 random;
    private final DuelistState player;
    private final DuelistState bot;

    private final Vector3 ballPos  = new Vector3();
    private final Vector3 ballVel  = new Vector3();
    private final Vector3 hitPoint = new Vector3();
    private final Vector3 tmpVel   = new Vector3();

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

    // Audio events — set true on relevant frames, consumed by the screen.
    private boolean paddleHitEvent;
    private boolean tableBounceEvent;

    public MatchWorld3D(MatchConfig config, RandomXS128 random) {
        this.config = config;
        this.random = random;
        player = new DuelistState(ArenaSide.PLAYER, "You", config.getFighter(ArenaSide.PLAYER));
        bot    = new DuelistState(ArenaSide.BOT,    "Bot", config.getFighter(ArenaSide.BOT));
        currentApproachDuration = config.getInitialApproachDuration();
        nextServer = 2; // start with bot serving to match opening message
        prepareServe(GameConfig.OPENING_DELAY, "Bot is preparing the opening shot.");
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
        int count = 8 + random.nextInt(4);
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
        // In network mode both players click to serve — no auto-serve.
        if (networkMode) return;
        // Single-player: only the bot auto-serves on a timer.  The human
        // player must click to send their serve via {@link #tryPlayerServe()}.
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
        float speedScale = 1f / Math.max(0.5f, player.getIncomingTimeMultiplier());
        float aim = (random.nextFloat() - 0.5f) * 1.4f;
        ballVel.set(aim, 5.0f, 7.5f * speedScale);
        paddleHitEvent = true;
    }

    private void updateIncoming(float delta) {
        float prevZ = ballPos.z;
        ballVel.y -= GRAVITY * delta;
        ballPos.mulAdd(ballVel, delta);

        if (!crossedNet && prevZ < 0f && ballPos.z >= 0f) {
            crossedNet = true;
            if (ballPos.y < NET_TOP_Y) { botMissedShot("Bot clips the net. Free point."); return; }
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
                botMissedShot("Bot's shot missed the table. Free point.");
                return;
            }
        }

        if (ballPos.z > TABLE_HALF_LENGTH + 1.5f) { handlePlayerMiss(); return; }
        if (ballPos.y < 0f)                       { handlePlayerMiss(); }
    }

    private void updateOutgoing(float delta) {
        float prevZ = ballPos.z;
        ballVel.y -= GRAVITY * delta;
        ballPos.mulAdd(ballVel, delta);

        if (!crossedNet && prevZ > 0f && ballPos.z <= 0f) {
            crossedNet = true;
            if (ballPos.y < NET_TOP_Y) { handlePlayerFault("Into the net! Hit with more lift."); return; }
        }

        if (ballPos.y <= TABLE_TOP_Y + BALL_RADIUS && ballVel.y < 0f) {
            ballPos.y = TABLE_TOP_Y + BALL_RADIUS;
            boolean valid = crossedNet
                && ballPos.x >= -TABLE_HALF_WIDTH && ballPos.x <= TABLE_HALF_WIDTH
                && ballPos.z >= -TABLE_HALF_LENGTH && ballPos.z < 0f;
            if (valid) {
                // Ball settles on the bot's side and waits there for the bot's reply.
                ballVel.x = 0f;
                ballVel.z = 0f;
                ballVel.y = -ballVel.y * BOUNCE_RESTITUTION;
                tableBounceEvent = true;
                spawnBounceSparks(ballPos.x, TABLE_TOP_Y, ballPos.z);
                phase = Phase.BOT_RESOLVE;
                phaseTimer = networkMode ? GameConfig.NET_CLIENT_MISS_TIMEOUT : GameConfig.BOT_RESPONSE_DELAY;
                statusText = "Clean return. Bot is trying to answer.";
            } else {
                handlePlayerFault("Out of bounds! Try a more centred shot.");
            }
            return;
        }

        if (ballPos.y < 0f
            || ballPos.z < -TABLE_HALF_LENGTH - 4f
            || Math.abs(ballPos.x) > TABLE_HALF_WIDTH + 6f) {
            handlePlayerFault("Out of bounds! Try a more centred shot.");
        }
    }

    private void updateBotResolve(float delta) {
        // Keep simulating the small residual bounce so the ball doesn't snap.
        ballVel.y -= GRAVITY * delta;
        ballPos.mulAdd(ballVel, delta);
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

        if (networkMode) {
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
            nextServer = 1; // player scored — player serves next
            prepareServe(GameConfig.BETWEEN_POINTS_DELAY,
                "Bot missed. Click anywhere to serve.");
        }
    }

    // ── network-mode API ─────────────────────────────────────────────────────

    public void setNetworkMode(boolean nm) {
        networkMode = nm;
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
        rallyCount++;
        pendingBotReturnChance = computeBotReturnChance();
        ballVel.set(vx, vy, vz);
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
        rallyCount++;
        ballVel.set(vx, vy, vz);
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
        nextServer = 1; // P1 scored, P1 serves next
        prepareServe(GameConfig.BETWEEN_POINTS_DELAY, "Opponent missed. P1 serves next.");
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
        float hitRadius = BALL_RADIUS * player.getTargetScaleMultiplier() * CLICK_HIT_PADDING;
        if (!Intersector.intersectRaySphere(pickRay, ballPos, hitRadius, hitPoint)) return false;

        Vector3 offset = hitPoint.cpy().sub(ballPos);
        float ndx = MathUtils.clamp(offset.x / hitRadius, -1f, 1f);
        float ndy = MathUtils.clamp(offset.y / hitRadius, -1f, 1f);
        float power = (float) Math.sqrt(ndx * ndx + ndy * ndy);

        lastClickAccuracy = 1f - MathUtils.clamp(power, 0f, 1f);
        rallyCount++;
        pendingBotReturnChance = computeBotReturnChance();

        float returnPower = player.getReturnPowerMultiplier();
        ballVel.set(
            ndx * 3.2f,
            5.0f + ndy * 2.0f,
            -(7.5f + power * 2.0f) * returnPower
        );
        crossedNet = false;
        phase = Phase.OUTGOING;
        statusText = "Clean return. Ball is travelling back to the bot.";
        paddleHitEvent = true;
        return true;
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
        ballVel.set(0f, 5.0f, -10f); // toward P2 (−z); lands ~z=−5.4 (deep serve)
        ballVisible = true;
        crossedNet = false;
        bouncesOnPlayerSide = 0;
        phase = Phase.OUTGOING;
        statusText = "P1 serves. P2 must return.";
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
        if (!networkMode) return false; // single-player: bot auto-serves on a timer
        float startX = (random.nextFloat() - 0.5f) * TABLE_HALF_WIDTH * 0.6f;
        ballPos.set(startX, TABLE_TOP_Y + 1.2f, -TABLE_HALF_LENGTH + 0.5f);
        ballVel.set(0f, 5.0f, 10f); // toward P1 (+z)
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
        nextServer = 2; // P2 (bot) scored — they serve next
        prepareServe(GameConfig.BETWEEN_POINTS_DELAY,
            "You missed the shot. Opponent serves next.");
    }

    private void handlePlayerFault(String text) {
        player.loseLife();
        if (player.getLives() <= 0) {
            outcome = MatchOutcome.BOT_WIN;
            statusText = "The shot got through.";
            ballVisible = false;
            return;
        }
        nextServer = 2; // P2 (bot) scored
        prepareServe(GameConfig.BETWEEN_POINTS_DELAY, text + " Opponent serves next.");
    }

    private void botMissedShot(String text) {
        bot.loseLife();
        if (bot.getLives() <= 0) {
            outcome = MatchOutcome.PLAYER_WIN;
            statusText = "The bot could not handle the pressure.";
            ballVisible = false;
            return;
        }
        nextServer = 1; // P1 scored
        prepareServe(GameConfig.BETWEEN_POINTS_DELAY, text + " Click anywhere to serve.");
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

    // ── accessors ────────────────────────────────────────────────────────────

    public Vector3 getBallPos()                 { return ballPos; }
    public Vector3 getBallVel()                 { return ballVel; }
    public float   getBallRadius()              { return BALL_RADIUS; }
    public boolean isBallVisible()              { return ballVisible; }
    public int     getPlayerLives()             { return player.getLives(); }
    public int     getBotLives()                { return bot.getLives(); }
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
