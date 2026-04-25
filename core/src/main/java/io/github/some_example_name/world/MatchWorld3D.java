package io.github.some_example_name.world;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.model.ArenaSide;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchOutcome;

/**
 * 3D POC version of the duel.
 *
 * Coordinate convention:
 *   x — lateral, centered at 0
 *   y — vertical, gravity is -y
 *   z — depth: bot side is negative z, player side is positive z, net at z=0
 */
public final class MatchWorld3D {

    public static final float TABLE_HALF_WIDTH  = 3f;
    public static final float TABLE_HALF_LENGTH = 7f;
    public static final float TABLE_TOP_Y       = 2f;
    public static final float NET_HEIGHT        = 0.5f;
    public static final float NET_TOP_Y         = TABLE_TOP_Y + NET_HEIGHT;
    public static final float BALL_RADIUS       = 0.18f;
    public static final float GRAVITY           = 9.8f;

    private static final float CLICK_HIT_PADDING = 3.5f;

    public enum Phase { PREPARE_INCOMING, PLAYER_REACT, PLAYER_RETURN, BOT_RESOLVE }

    private final MatchConfig config;
    private final RandomXS128 random;
    private final DuelistState player;
    private final DuelistState bot;

    private final Vector3 ballPos        = new Vector3();
    private final Vector3 ballVel        = new Vector3();
    private final Vector3 incomingStart  = new Vector3();
    private final Vector3 incomingEnd    = new Vector3();
    private final Vector3 hitPoint       = new Vector3();

    private float incomingDuration;
    private float incomingElapsed;
    private float currentApproachDuration;

    private Phase phase = Phase.PREPARE_INCOMING;
    private MatchOutcome outcome = MatchOutcome.NONE;
    private String statusText = "Bot is preparing the opening shot.";
    private float phaseTimer;
    private float pendingBotReturnChance;
    private float lastClickAccuracy;
    private int rallyCount;
    private boolean ballVisible;
    private boolean crossedNet;

    public MatchWorld3D(MatchConfig config, RandomXS128 random) {
        this.config = config;
        this.random = random;
        player = new DuelistState(ArenaSide.PLAYER, "You", config.getFighter(ArenaSide.PLAYER));
        bot    = new DuelistState(ArenaSide.BOT,    "Bot", config.getFighter(ArenaSide.BOT));
        currentApproachDuration = config.getInitialApproachDuration();
        queueIncoming(GameConfig.OPENING_DELAY, "Bot is preparing the opening shot.");
    }

    public void update(float delta) {
        if (outcome != MatchOutcome.NONE) return;
        switch (phase) {
            case PREPARE_INCOMING -> updatePrepare(delta);
            case PLAYER_REACT     -> updatePlayerReact(delta);
            case PLAYER_RETURN    -> updatePlayerReturn(delta);
            case BOT_RESOLVE      -> updateBotResolve(delta);
        }
    }

    private void updatePrepare(float delta) {
        phaseTimer -= delta;
        if (phaseTimer > 0f) return;
        startIncoming();
    }

    private void startIncoming() {
        float startX = (random.nextFloat() - 0.5f) * TABLE_HALF_WIDTH * 1.4f;
        float startZ = -TABLE_HALF_LENGTH + 0.5f;
        float startY = TABLE_TOP_Y + 1.6f;

        float endX = (random.nextFloat() - 0.5f) * TABLE_HALF_WIDTH * 1.4f;
        float endZ = TABLE_HALF_LENGTH - 1.4f;
        float endY = TABLE_TOP_Y + 0.7f;

        incomingStart.set(startX, startY, startZ);
        incomingEnd.set(endX, endY, endZ);
        incomingElapsed = 0f;
        incomingDuration = currentApproachDuration * player.getIncomingTimeMultiplier();
        ballPos.set(incomingStart);
        ballVel.setZero();
        ballVisible = true;
        crossedNet = false;
        phase = Phase.PLAYER_REACT;
        statusText = "Click the ball as it comes through the table lane.";
    }

    private void updatePlayerReact(float delta) {
        incomingElapsed = Math.min(incomingDuration, incomingElapsed + delta);
        float t = incomingDuration <= 0f ? 1f : incomingElapsed / incomingDuration;
        float eased = t * t;
        ballPos.set(incomingStart).lerp(incomingEnd, eased);
        ballPos.y += 1.4f * eased * (1f - eased) * 4f;
        if (incomingElapsed >= incomingDuration) {
            handlePlayerMiss();
        }
    }

    private void updatePlayerReturn(float delta) {
        float prevZ = ballPos.z;
        ballVel.y -= GRAVITY * delta;
        ballPos.mulAdd(ballVel, delta);

        if (!crossedNet && prevZ > 0f && ballPos.z <= 0f) {
            crossedNet = true;
            if (ballPos.y < NET_TOP_Y) {
                handlePlayerFault("Into the net! Hit with more lift.");
                return;
            }
        }

        if (ballPos.y <= TABLE_TOP_Y + BALL_RADIUS) {
            ballPos.y = TABLE_TOP_Y + BALL_RADIUS;
            boolean valid = crossedNet
                && ballPos.x >= -TABLE_HALF_WIDTH && ballPos.x <= TABLE_HALF_WIDTH
                && ballPos.z >= -TABLE_HALF_LENGTH && ballPos.z < 0f;
            if (valid) {
                ballVisible = false;
                phase = Phase.BOT_RESOLVE;
                phaseTimer = GameConfig.BOT_RESPONSE_DELAY;
                statusText = "Clean return. Bot is trying to answer.";
            } else {
                handlePlayerFault("Out of bounds! Try a more centred shot.");
            }
            return;
        }

        if (ballPos.z < -TABLE_HALF_LENGTH - 4f || Math.abs(ballPos.x) > TABLE_HALF_WIDTH + 6f) {
            handlePlayerFault("Out of bounds! Try a more centred shot.");
        }
    }

    private void updateBotResolve(float delta) {
        phaseTimer -= delta;
        if (phaseTimer > 0f) return;
        if (random.nextFloat() <= pendingBotReturnChance) {
            currentApproachDuration = Math.max(
                config.getMinimumApproachDuration(),
                currentApproachDuration - config.getApproachDurationDecay()
            );
            queueIncoming(GameConfig.BOT_RESPONSE_DELAY, "Bot gets it back. Another shot is already coming.");
        } else {
            bot.loseLife();
            if (bot.getLives() <= 0) {
                outcome = MatchOutcome.PLAYER_WIN;
                statusText = "The bot could not handle the pressure.";
                ballVisible = false;
                return;
            }
            queueIncoming(GameConfig.BETWEEN_POINTS_DELAY, "Bot missed. A new serve is coming.");
        }
    }

    /** Returns true if the click hit the incoming ball and triggered a return. */
    public boolean tryHitBall(Ray pickRay) {
        if (phase != Phase.PLAYER_REACT) return false;
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
            ndx * 4.5f,
            5.5f + ndy * 3.5f,
            -(11f + power * 2.5f) * returnPower
        );
        crossedNet = false;
        phase = Phase.PLAYER_RETURN;
        statusText = "Clean return. Ball is travelling back to the bot.";
        return true;
    }

    private void handlePlayerMiss() {
        player.loseLife();
        ballVisible = false;
        if (player.getLives() <= 0) {
            outcome = MatchOutcome.BOT_WIN;
            statusText = "The shot got through.";
            return;
        }
        queueIncoming(GameConfig.BETWEEN_POINTS_DELAY, "You missed the shot. Reset and react to the next one.");
    }

    private void handlePlayerFault(String text) {
        player.loseLife();
        ballVisible = false;
        if (player.getLives() <= 0) {
            outcome = MatchOutcome.BOT_WIN;
            statusText = "The shot got through.";
            return;
        }
        queueIncoming(GameConfig.BETWEEN_POINTS_DELAY, text);
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

    private void queueIncoming(float delay, String status) {
        phase = Phase.PREPARE_INCOMING;
        phaseTimer = delay;
        statusText = status;
        ballVisible = false;
    }

    public Vector3 getBallPos()                 { return ballPos; }
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
        if (phase == Phase.PLAYER_REACT) return incomingDuration;
        return currentApproachDuration * player.getIncomingTimeMultiplier();
    }
}
