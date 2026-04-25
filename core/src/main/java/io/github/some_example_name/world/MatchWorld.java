package io.github.some_example_name.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import io.github.some_example_name.assets.ProceduralAssets;
import io.github.some_example_name.assets.TableVariation;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.input.MatchInputController;
import io.github.some_example_name.model.ArenaSide;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchOutcome;

/** Encapsulates the player-POV click-to-return duel rules, trajectory physics, and pooled VFX. */
public final class MatchWorld {

    // ── timing ───────────────────────────────────────────────────────────────
    private static final float TABLE_FRAME_DURATION = 1f / 22f;

    // ── rendering layout ─────────────────────────────────────────────────────
    private static final float TABLE_RENDER_X             = 58f;
    private static final float TABLE_RENDER_Y             = 74f;
    private static final float TABLE_RENDER_CANVAS_WIDTH  = 1164f;
    private static final float TABLE_RENDER_CANVAS_HEIGHT = 592f;

    // ── colours ───────────────────────────────────────────────────────────────
    private static final Color PLAYER_COLOR           = Color.valueOf("76F7D1");
    private static final Color BOT_COLOR              = Color.valueOf("FF8EA8");
    private static final Color UI_COLOR               = Color.valueOf("BDECF3");
    private static final Color WARNING_COLOR          = Color.valueOf("FFDB8A");
    private static final Color ARENA_BORDER_COLOR     = Color.valueOf("071015");
    private static final Color ARENA_PANEL_COLOR      = Color.valueOf("0E2026");
    private static final Color CROSSHAIR_COLOR        = Color.valueOf("C4F7EE");
    private static final Color CROSSHAIR_CENTER_COLOR = Color.valueOf("74DCCD");
    private static final Color NET_LINE_COLOR         = Color.valueOf("E8C06A");
    private static final Color TABLE_OUTLINE_COLOR    = Color.valueOf("2A9A8A");

    // ── status strings ────────────────────────────────────────────────────────
    private static final String STATUS_OPENING       = "Bot is preparing the opening shot.";
    private static final String STATUS_REACT         = "Click the ball as it comes through the table lane.";
    private static final String STATUS_RETURN        = "Clean return. Ball is travelling back to the bot.";
    private static final String STATUS_BOT_ANSWERING = "Clean return. Bot is trying to answer.";
    private static final String STATUS_BOT_RETURNED  = "Bot gets it back. Another shot is already coming.";
    private static final String STATUS_BOT_MISSED    = "Bot missed. A new serve is coming.";
    private static final String STATUS_PLAYER_MISSED = "You missed the shot. Reset and react to the next one.";
    private static final String STATUS_PLAYER_WIN    = "The bot could not handle the pressure.";
    private static final String STATUS_BOT_WIN       = "The shot got through.";

    // ── phase / playback enums ────────────────────────────────────────────────
    private enum Phase { PREPARE_INCOMING, PLAYER_REACT, PLAYER_RETURN, BOT_RESOLVE }
    private enum TablePlayback { LOOP_FORWARD, REVERSE_TO_FIRST, FROZEN_FIRST }

    // ── core state ────────────────────────────────────────────────────────────
    private final MatchConfig  config;
    private final RandomXS128  random;
    private final DuelistState player;
    private final DuelistState bot;
    private final IncomingBall incomingBall  = new IncomingBall();
    private final Vector2      reusableClick = new Vector2();

    private final Array<ImpactParticle> particles    = new Array<>();
    private final Pool<ImpactParticle>  particlePool = new Pool<>() {
        @Override protected ImpactParticle newObject() { return new ImpactParticle(); }
    };

    private Phase         phase         = Phase.PREPARE_INCOMING;
    private MatchOutcome  outcome       = MatchOutcome.NONE;

    private float         currentApproachDuration;
    private float         phaseTimer;
    private float         pendingBotReturnChance;
    private float         lastClickAccuracy;
    private float         tableFrameTimer;
    private int           tableFrameIndex;
    private TablePlayback tablePlayback  = TablePlayback.LOOP_FORWARD;
    private TableVariation tableVariation = TableVariation.CLASSIC;
    private int           rallyCount;
    private String        statusText    = STATUS_OPENING;

    // ── constructor ───────────────────────────────────────────────────────────

    public MatchWorld(MatchConfig config, RandomXS128 random) {
        this.config = config;
        this.random = random;
        player = new DuelistState(ArenaSide.PLAYER, "You", config.getFighter(ArenaSide.PLAYER));
        bot    = new DuelistState(ArenaSide.BOT,    "Bot", config.getFighter(ArenaSide.BOT));
        currentApproachDuration = config.getInitialApproachDuration();
        queueIncoming(GameConfig.OPENING_DELAY, STATUS_OPENING);
    }

    // ── update ────────────────────────────────────────────────────────────────

    public void update(float delta, MatchInputController input) {
        updateParticles(delta);
        updateTableAnimation(delta);
        if (outcome != MatchOutcome.NONE) return;

        switch (phase) {
            case PREPARE_INCOMING -> updatePrepareIncoming(delta);
            case PLAYER_REACT     -> updatePlayerReact(delta, input);
            case PLAYER_RETURN    -> updatePlayerReturn(delta);
            case BOT_RESOLVE      -> updateBotResolve(delta);
        }
    }

    // ── render ────────────────────────────────────────────────────────────────

    public void render(SpriteBatch batch, ProceduralAssets visuals, MatchInputController inputController) {
        Texture       pixel   = visuals.getPixel();
        Texture       glow    = visuals.getGlow();
        Texture       aimRing = visuals.getAimRing();
        TextureRegion table   = visuals.getTableFrame(tableVariation, tableFrameIndex);

        batch.setColor(Color.WHITE);
        batch.draw(visuals.getBackground(), 0f, 0f, GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT);

        drawArenaPerspective(batch, pixel, visuals.getPanel(), table);
        drawTableOutline(batch, pixel);
        drawNetLine(batch, pixel);

        switch (phase) {
            case PLAYER_REACT  -> drawIncomingBall(batch, pixel, glow, aimRing);
            case PLAYER_RETURN -> drawOutgoingBall(batch, glow);
            default            -> {}
        }

        drawCrosshair(batch, pixel, inputController);
        drawParticles(batch, glow);
        batch.setColor(Color.WHITE);
    }

    // ── public accessors ──────────────────────────────────────────────────────

    public int    getPlayerLives()             { return player.getLives(); }
    public int    getBotLives()                { return bot.getLives(); }
    public float  getCurrentApproachDuration() { return currentApproachDuration; }
    public int    getRallyCount()              { return rallyCount; }
    public String getStatusText()              { return statusText; }
    public MatchOutcome getOutcome()           { return outcome; }
    public boolean isMatchOver()              { return outcome != MatchOutcome.NONE; }

    public float getDisplayedReadWindow() {
        if (phase == Phase.PLAYER_REACT) return incomingBall.getDuration();
        return currentApproachDuration * player.getIncomingTimeMultiplier();
    }

    public void setTableVariation(TableVariation tv) {
        this.tableVariation = tv == null ? TableVariation.CLASSIC : tv;
        tableFrameIndex     = 0;
        tableFrameTimer     = 0f;
        tablePlayback       = TablePlayback.LOOP_FORWARD;
    }

    public TableVariation getTableVariation() { return tableVariation; }

    // ── phase updates ─────────────────────────────────────────────────────────

    private void updatePrepareIncoming(float delta) {
        phaseTimer -= delta;
        if (phaseTimer > 0f) return;
        if (tablePlayback == TablePlayback.LOOP_FORWARD)  { tablePlayback = TablePlayback.REVERSE_TO_FIRST; return; }
        if (tablePlayback == TablePlayback.REVERSE_TO_FIRST) return;
        startIncomingShot();
    }

    private void startIncomingShot() {
        freezeTableOnFirstFrame();

        float lane   = 0.12f + random.nextFloat() * 0.76f;
        float startY = MathUtils.lerp(GameConfig.TABLE_FAR_Y - 42f,      GameConfig.TABLE_NET_TOP_Y + 12f, 0.55f + random.nextFloat() * 0.3f);
        float endY   = MathUtils.lerp(GameConfig.TABLE_NET_BOTTOM_Y - 12f, GameConfig.TABLE_NEAR_Y + 64f,  0.35f + random.nextFloat() * 0.55f);
        float endLane = MathUtils.clamp(lane + (random.nextFloat() - 0.5f) * 0.12f, 0.08f, 0.92f);

        incomingBall.start(
            TableGeometry.xAt(startY, lane), startY,
            TableGeometry.xAt(endY, endLane), endY,
            currentApproachDuration * player.getIncomingTimeMultiplier()
        );
        phase      = Phase.PLAYER_REACT;
        statusText = STATUS_REACT;
    }

    private void updatePlayerReact(float delta, MatchInputController input) {
        if (input != null && input.consumeClick(reusableClick)) handlePlayerClick(reusableClick);
        if (phase != Phase.PLAYER_REACT) return;
        if (incomingBall.update(delta)) handlePlayerMiss();
    }

    private void updatePlayerReturn(float delta) {
        if (!incomingBall.update(delta)) return;
        resumeTableLoopFromFirstFrame();
        phase      = Phase.BOT_RESOLVE;
        phaseTimer = GameConfig.BOT_RESPONSE_DELAY;
        statusText = STATUS_BOT_ANSWERING;
    }

    private void updateBotResolve(float delta) {
        phaseTimer -= delta;
        if (phaseTimer > 0f) return;

        if (random.nextFloat() <= pendingBotReturnChance) {
            currentApproachDuration = Math.max(
                config.getMinimumApproachDuration(),
                currentApproachDuration - config.getApproachDurationDecay()
            );
            queueIncoming(GameConfig.BOT_RESPONSE_DELAY, STATUS_BOT_RETURNED);
            spawnImpactBurst(TableGeometry.xAt(GameConfig.TABLE_NET_TOP_Y - 8f, 0.5f + (random.nextFloat() - 0.5f) * 0.18f),
                GameConfig.TABLE_NET_TOP_Y + 18f, 12, BOT_COLOR);
        } else {
            bot.loseLife();
            spawnImpactBurst(TableGeometry.xAt(GameConfig.TABLE_NET_TOP_Y - 8f, 0.5f + (random.nextFloat() - 0.5f) * 0.22f),
                GameConfig.TABLE_NET_TOP_Y + 6f, 20, PLAYER_COLOR);
            if (bot.getLives() <= 0) { outcome = MatchOutcome.PLAYER_WIN; statusText = STATUS_PLAYER_WIN; return; }
            queueIncoming(GameConfig.BETWEEN_POINTS_DELAY, STATUS_BOT_MISSED);
        }
    }

    // ── player interaction ────────────────────────────────────────────────────

    private void handlePlayerClick(Vector2 clickPosition) {
        if (!incomingBall.contains(clickPosition, player.getTargetScaleMultiplier())) {
            spawnImpactBurst(clickPosition.x, clickPosition.y, 4, UI_COLOR);
            return;
        }

        float hitRadius       = incomingBall.getRadius() * player.getTargetScaleMultiplier();
        lastClickAccuracy     = 1f - MathUtils.clamp(clickPosition.dst(incomingBall.getPosition()) / hitRadius, 0f, 1f);
        rallyCount++;

        spawnImpactBurst(incomingBall.getPosition().x, incomingBall.getPosition().y, 16, PLAYER_COLOR);
        pendingBotReturnChance = computeBotReturnChance();

        float destLane = 0.12f + random.nextFloat() * 0.76f;
        float destY    = MathUtils.lerp(GameConfig.TABLE_NET_TOP_Y + 12f, GameConfig.TABLE_FAR_Y - 42f,
                                        0.4f + random.nextFloat() * 0.5f);
        incomingBall.start(
            incomingBall.getPosition().x, incomingBall.getPosition().y,
            TableGeometry.xAt(destY, destLane), destY,
            currentApproachDuration * 0.7f
        );
        phase      = Phase.PLAYER_RETURN;
        statusText = STATUS_RETURN;
    }

    private void handlePlayerMiss() {
        player.loseLife();
        spawnImpactBurst(incomingBall.getPosition().x, incomingBall.getPosition().y, 26, WARNING_COLOR);
        if (player.getLives() <= 0) { outcome = MatchOutcome.BOT_WIN; statusText = STATUS_BOT_WIN; return; }
        resumeTableLoopFromFirstFrame();
        queueIncoming(GameConfig.BETWEEN_POINTS_DELAY, STATUS_PLAYER_MISSED);
    }

    // ── bot return chance ─────────────────────────────────────────────────────

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

    // ── table animation ───────────────────────────────────────────────────────

    private void updateTableAnimation(float delta) {
        int last = Math.max(0, tableVariation.getFrameCount() - 1);
        if (tablePlayback == TablePlayback.FROZEN_FIRST || last == 0) { tableFrameIndex = 0; tableFrameTimer = 0f; return; }
        tableFrameTimer += delta;
        while (tableFrameTimer >= TABLE_FRAME_DURATION) {
            tableFrameTimer -= TABLE_FRAME_DURATION;
            if (tablePlayback == TablePlayback.LOOP_FORWARD) {
                tableFrameIndex = (tableFrameIndex + 1) % (last + 1);
            } else if (tablePlayback == TablePlayback.REVERSE_TO_FIRST) {
                if (tableFrameIndex > 0) tableFrameIndex--;
                else { freezeTableOnFirstFrame(); return; }
            }
        }
    }

    private void freezeTableOnFirstFrame()     { tablePlayback = TablePlayback.FROZEN_FIRST; tableFrameIndex = 0; tableFrameTimer = 0f; }
    private void resumeTableLoopFromFirstFrame(){ tablePlayback = TablePlayback.LOOP_FORWARD;  tableFrameIndex = 0; tableFrameTimer = 0f; }

    private void queueIncoming(float delay, String status) {
        phase = Phase.PREPARE_INCOMING; phaseTimer = delay; tablePlayback = TablePlayback.LOOP_FORWARD; statusText = status;
    }

    // ── rendering — arena ─────────────────────────────────────────────────────

    private void drawArenaPerspective(SpriteBatch batch, Texture pixel, Texture panel, TextureRegion tableFrame) {
        batch.setColor(ARENA_BORDER_COLOR);
        batch.draw(pixel, 40f, 56f, 1200f, 628f);
        batch.setColor(ARENA_PANEL_COLOR);
        batch.draw(panel, 58f, 74f, 1164f, 592f);

        float sw = Math.max(1f, tableFrame.getRegionWidth());
        float sh = Math.max(1f, tableFrame.getRegionHeight());
        float sc = Math.min(TABLE_RENDER_CANVAS_WIDTH / sw, TABLE_RENDER_CANVAS_HEIGHT / sh);
        float dw = sw * sc, dh = sh * sc;
        float dx = TABLE_RENDER_X + (TABLE_RENDER_CANVAS_WIDTH  - dw) * 0.5f;
        float dy = TABLE_RENDER_Y + (TABLE_RENDER_CANVAS_HEIGHT - dh) * 0.5f;

        batch.setColor(Color.WHITE);
        batch.draw(tableFrame, dx, dy, dw, dh);
    }

    private void drawTableOutline(SpriteBatch batch, Texture pixel) {
        float nearL = TableGeometry.leftX(GameConfig.TABLE_NEAR_Y);
        float nearR = TableGeometry.rightX(GameConfig.TABLE_NEAR_Y);
        float farL  = TableGeometry.leftX(GameConfig.TABLE_FAR_Y);
        float farR  = TableGeometry.rightX(GameConfig.TABLE_FAR_Y);
        batch.setColor(TABLE_OUTLINE_COLOR.r, TABLE_OUTLINE_COLOR.g, TABLE_OUTLINE_COLOR.b, 0.5f);
        batch.draw(pixel, nearL, GameConfig.TABLE_NEAR_Y - 1f, nearR - nearL, 2f);
        batch.draw(pixel, farL,  GameConfig.TABLE_FAR_Y  - 1f, farR  - farL,  2f);
        drawLine(batch, pixel, nearL, GameConfig.TABLE_NEAR_Y, farL, GameConfig.TABLE_FAR_Y, 1.5f);
        drawLine(batch, pixel, nearR, GameConfig.TABLE_NEAR_Y, farR, GameConfig.TABLE_FAR_Y, 1.5f);
    }

    private void drawNetLine(SpriteBatch batch, Texture pixel) {
        float netY = TableGeometry.NET_Y;
        float netL = TableGeometry.leftX(netY);
        float netR = TableGeometry.rightX(netY);
        float netH = TableGeometry.NET_HEIGHT_Z;
        batch.setColor(NET_LINE_COLOR.r, NET_LINE_COLOR.g, NET_LINE_COLOR.b, 0.7f);
        batch.draw(pixel, netL, netY - 1f,      netR - netL, 2f);
        batch.setColor(NET_LINE_COLOR.r, NET_LINE_COLOR.g, NET_LINE_COLOR.b, 0.4f);
        batch.draw(pixel, netL, netY + netH - 1f, netR - netL, 2f);
        batch.setColor(NET_LINE_COLOR.r, NET_LINE_COLOR.g, NET_LINE_COLOR.b, 0.35f);
        batch.draw(pixel, netL - 2f, netY, 3f, netH);
        batch.draw(pixel, netR,      netY, 3f, netH);
    }

    // ── rendering — incoming ball (player must click this) ────────────────────
    //
    // Design goal: the player must always be able to answer three questions instantly:
    //   1. Where is the centre?    → crosshair + centre dot
    //   2. What is the hit area?   → aimRing at exact hitRadius
    //   3. Am I on time?           → ring colour shifts cool→warm as ball closes in

    private void drawIncomingBall(SpriteBatch batch, Texture pixel, Texture glow, Texture aimRing) {
        float radius    = incomingBall.getRadius();
        float hitRadius = radius * player.getTargetScaleMultiplier();
        float progress  = incomingBall.getProgress();   // 0 = far/small, 1 = close/large
        float x         = incomingBall.getPosition().x;
        float y         = incomingBall.getPosition().y;

        // Soft body — communicates the ball is a physical object approaching.
        float bodySize = radius * 2.4f;
        batch.setColor(0.75f, 0.92f, 1f, 0.18f + progress * 0.22f);
        batch.draw(glow, x - bodySize * 0.5f, y - bodySize * 0.5f, bodySize, bodySize);

        float coreSize = radius * 1.5f;
        batch.setColor(0.92f, 0.97f, 1f, 0.80f + progress * 0.15f);
        batch.draw(glow, x - coreSize * 0.5f, y - coreSize * 0.5f, coreSize, coreSize);

        // Hit-boundary ring — sharp edge so the player knows exactly where clicks register.
        // Colour: cool teal when far (safe to miss-time) → warm amber when close (urgent).
        float ringR = 0.35f + progress * 0.60f;
        float ringG = 0.88f - progress * 0.18f;
        float ringB = 0.58f - progress * 0.48f;
        batch.setColor(ringR, ringG, ringB, 0.72f + progress * 0.24f);
        float ringSize = hitRadius * 2f;
        batch.draw(aimRing, x - ringSize * 0.5f, y - ringSize * 0.5f, ringSize, ringSize);

        // Crosshair — always 2 px wide, spans the interior of the ring.
        float crossLen = hitRadius * 0.42f;
        batch.setColor(1f, 1f, 1f, 0.60f);
        batch.draw(pixel, x - crossLen, y - 1f, crossLen * 2f, 2f);
        batch.draw(pixel, x - 1f, y - crossLen, 2f, crossLen * 2f);

        // Centre dot — small, bright, always readable even when the ball is tiny.
        float dotSize = 10f;
        batch.setColor(1f, 1f, 1f, 0.95f);
        batch.draw(glow, x - dotSize * 0.5f, y - dotSize * 0.5f, dotSize, dotSize);
    }

    // ── rendering — outgoing ball (travels back along the confirmed trajectory) ──
    //
    // Intentionally minimal: the trajectory arc is already the main visual.
    // The dot just lets the player track the ball moving away.

    private void drawOutgoingBall(SpriteBatch batch, Texture glow) {
        float progress = incomingBall.getProgress();
        float radius   = MathUtils.lerp(GameConfig.SHOT_END_RADIUS * 0.65f, GameConfig.SHOT_START_RADIUS, progress);
        float x        = incomingBall.getPosition().x;
        float y        = incomingBall.getPosition().y;

        batch.setColor(PLAYER_COLOR.r, PLAYER_COLOR.g, PLAYER_COLOR.b, 0.70f - progress * 0.20f);
        float size = radius * 2f;
        batch.draw(glow, x - size * 0.5f, y - size * 0.5f, size, size);

        float dot = Math.max(5f, size * 0.28f);
        batch.setColor(1f, 1f, 1f, 0.90f);
        batch.draw(glow, x - dot * 0.5f, y - dot * 0.5f, dot, dot);
    }

    // ── rendering — crosshair & particles ────────────────────────────────────

    private void drawCrosshair(SpriteBatch batch, Texture pixel, MatchInputController inputController) {
        if (inputController == null) return;
        Vector2 p = inputController.getPointer();
        batch.setColor(CROSSHAIR_COLOR);
        batch.draw(pixel, p.x - 1f, p.y - 18f, 2f, 36f);
        batch.draw(pixel, p.x - 18f, p.y - 1f, 36f, 2f);
        batch.setColor(CROSSHAIR_CENTER_COLOR);
        batch.draw(pixel, p.x - 5f, p.y - 5f, 10f, 10f);
    }

    private void drawParticles(SpriteBatch batch, Texture glow) {
        for (ImpactParticle p : particles) {
            Color c = p.getColor();
            batch.setColor(c.r, c.g, c.b, p.getAlpha());
            float sz = p.getSize();
            batch.draw(glow, p.getPosition().x - sz * 0.5f, p.getPosition().y - sz * 0.5f, sz, sz);
        }
    }

    // ── particles ─────────────────────────────────────────────────────────────

    private void updateParticles(float delta) {
        for (int i = particles.size - 1; i >= 0; i--) {
            if (particles.get(i).update(delta)) { particlePool.free(particles.removeIndex(i)); }
        }
    }

    private void spawnImpactBurst(float x, float y, int count, Color color) {
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * MathUtils.PI2;
            float speed = 38f + random.nextFloat() * 180f;
            float sz    = 10f + random.nextFloat() * 22f;
            float life  = 0.16f + random.nextFloat() * 0.38f;
            ImpactParticle p = particlePool.obtain();
            p.init(x, y, MathUtils.cos(angle) * speed, MathUtils.sin(angle) * speed, sz, life, color);
            particles.add(p);
        }
    }

    // ── geometry helper ───────────────────────────────────────────────────────

    private static void drawLine(SpriteBatch batch, Texture pixel,
                                  float x1, float y1, float x2, float y2, float width) {
        float dx = x2 - x1, dy = y2 - y1;
        float len   = (float) Math.sqrt(dx * dx + dy * dy);
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        batch.draw(pixel, x1, y1 - width * 0.5f, 0f, width * 0.5f,
            len, width, 1f, 1f, angle, 0, 0, 2, 2, false, false);
    }
}
