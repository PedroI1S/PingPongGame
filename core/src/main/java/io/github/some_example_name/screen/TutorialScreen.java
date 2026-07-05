package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.collision.Ray;
import io.github.some_example_name.Main;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;
import io.github.some_example_name.model.ArenaSide;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.render.MatchArenaRenderer;
import io.github.some_example_name.tutorial.DrillCourse;
import io.github.some_example_name.tutorial.TutorialGeometry;
import io.github.some_example_name.tutorial.ZoneRect;
import io.github.some_example_name.world.MatchWorld3D;
import io.github.some_example_name.world.physics.PhysicsConfig;

/**
 * The guided tutorial: six local drills ({@link DrillCourse}) and a graduation
 * rally against a softened bot (a real local {@link MatchWorld3D}). Everything
 * runs client-side — no server, no connection.
 */
public final class TutorialScreen extends BaseScreen {

    private static final float ZONE_Y = MatchWorld3D.TABLE_TOP_Y + 0.03f;

    private final PhysicsConfig cfg = PhysicsConfig.createDefault();
    private final DrillCourse course = new DrillCourse(cfg, new RandomXS128());

    /** Non-null once the course completes: the graduation rally world. */
    private MatchWorld3D graduation;
    private boolean finished;   // graduation decided (either way)
    private boolean won;

    private MatchArenaRenderer arena;
    private Model zoneModel, poleModel;
    private ModelInstance zoneA, zoneB, pole;
    private TutInput input;

    public TutorialScreen(Main game) { super(game); }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void show() {
        if (arena == null) {
            arena = new MatchArenaRenderer(true); // P1 camera, same as a match
        }
        arena.ensureInitialized();
        if (zoneModel == null) {
            ModelBuilder mb = new ModelBuilder();
            zoneModel = mb.createBox(1f, 0.05f, 1f,
                new Material(ColorAttribute.createDiffuse(new Color(0.2f, 0.75f, 0.6f, 1f)),
                             new BlendingAttribute(0.35f)),
                Usage.Position | Usage.Normal);
            poleModel = mb.createBox(TutorialGeometry.POLE_RADIUS * 2f,
                TutorialGeometry.POLE_HEIGHT, TutorialGeometry.POLE_RADIUS * 2f,
                new Material(ColorAttribute.createDiffuse(new Color(0.85f, 0.35f, 0.19f, 1f)),
                             new BlendingAttribute(0.85f)),
                Usage.Position | Usage.Normal);
            zoneA = new ModelInstance(zoneModel);
            zoneB = new ModelInstance(zoneModel);
            pole  = new ModelInstance(poleModel);
        }
        input = new TutInput();
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (arena != null) arena.resize(width, height);
        context.getPostProcess().resize(width, height);
    }

    @Override
    public void dispose() {
        if (arena != null) { arena.dispose(); arena = null; }
        if (zoneModel != null) { zoneModel.dispose(); zoneModel = null; }
        if (poleModel != null) { poleModel.dispose(); poleModel = null; }
    }

    // ── frame ─────────────────────────────────────────────────────────────────

    @Override
    public void render(float delta) {
        if (input.consumeMenu()) {
            game.openPauseMenu(this, this::leave);
            return;
        }

        update(delta);

        context.getPostProcess().begin();
        boolean ballVisible = graduation != null
            ? graduation.isBallVisible()
            : course.isBallVisible();
        if (graduation != null) {
            arena.spinBall(graduation.getBallSpin(), delta);
            arena.setBallPosition(graduation.getBallPos().x,
                graduation.getBallPos().y, graduation.getBallPos().z);
            arena.setLivesDisplay(graduation.getPlayerLives(), graduation.getP2Lives());
        } else {
            arena.spinBall(course.ball().spin, delta);
            arena.setBallPosition(course.ball().pos.x,
                course.ball().pos.y, course.ball().pos.z);
            arena.setLivesDisplay(0, 0);
        }
        arena.unprojectCursorOntoTable(input.lastMouseX, input.lastMouseY);
        arena.render3DScene(ballVisible);
        if (graduation == null) drawZonesAndPole();
        context.getViewport().apply(true);

        SpriteBatch batch = context.getBatch();
        batch.setProjectionMatrix(context.getViewport().getCamera().combined);
        batch.begin();
        arena.drawCursorMarker(batch, context.getAssets().getProceduralAssets().getAimRing());
        batch.end();

        context.getPostProcess().endAndBlit();

        // Crisp UI pass.
        context.getViewport().apply(true);
        batch.setProjectionMatrix(context.getViewport().getCamera().combined);
        batch.begin();
        drawHud(batch);
        batch.end();
    }

    private void update(float delta) {
        if (graduation != null) {
            if (!finished) {
                graduation.update(delta);
                // tutorial has no item selection: ready both sides instantly
                if (graduation.getPhase() == MatchWorld3D.Phase.ITEM_PHASE) {
                    graduation.playerReady(1);
                    graduation.playerReady(2);
                }
                pumpWorldSfx();
                if (graduation.isMatchOver()) {
                    finished = true;
                    won = graduation.getOutcome() == MatchOutcome.PLAYER_WIN;
                    if (won) {
                        context.getSettings().setTutorialCompleted(true);
                        context.getAssets().getUiClickSfx().play(getSfxGain() * 0.8f);
                    } else {
                        context.getAssets().getLifeLostSfx().play(getSfxGain() * 0.5f);
                    }
                }
            }
            if (input.consumeClick()) {
                if (finished) {
                    if (won) leave();
                    else restartGraduation();
                } else {
                    Ray ray = arena.getCamera().getPickRay(input.lastClickX, input.lastClickY);
                    graduation.handlePlayerClick(ray);
                }
            }
            return;
        }

        course.update(delta);
        pumpCourseSfx();
        if (input.consumeClick()) {
            Ray ray = arena.getCamera().getPickRay(input.lastClickX, input.lastClickY);
            course.click(ray);
        }
        if (course.isComplete()) startGraduation();
    }

    private void startGraduation() {
        MatchConfig mc = MatchConfig.createDefault();
        mc.getFighter(ArenaSide.PLAYER).addLives(3 - GameConfig.DEFAULT_LIVES);
        mc.getFighter(ArenaSide.BOT).addLives(3 - GameConfig.DEFAULT_LIVES);
        graduation = new MatchWorld3D(mc, new RandomXS128());
        graduation.setMatchMode(MatchMode.BOT);
        graduation.getBotProfile().aimSigma = 0.95f;
        graduation.getBotProfile().reactionDelay = 0.8f;
    }

    private void restartGraduation() {
        finished = false;
        won = false;
        startGraduation();
    }

    private void leave() {
        game.openMenu();
        // libGDX never calls Screen.dispose() on its own — without this the
        // arena's models/textures and the zone/pole boxes leak once per visit.
        dispose();
    }

    // ── sfx pumps ─────────────────────────────────────────────────────────────

    private void pumpCourseSfx() {
        if (course.consumePaddleHitEvent())
            context.getAssets().getBallHitSfx().play(getSfxGain() * 0.7f);
        if (course.consumeTableBounceEvent())
            context.getAssets().getTableHitSfx().play(getSfxGain() * 0.6f);
        if (course.consumeSuccessEvent())
            context.getAssets().getUiClickSfx().play(getSfxGain() * 0.6f);
        if (course.consumePoleHitEvent())
            context.getAssets().getTableHitSfx().play(getSfxGain() * 0.8f, 0.55f, 0f);
        course.consumeFailEvent(); // feedback text carries the miss; no sound
    }

    private void pumpWorldSfx() {
        if (graduation.consumePaddleHitEvent())
            context.getAssets().getBallHitSfx().play(getSfxGain() * 0.7f);
        if (graduation.consumeTableBounceEvent())
            context.getAssets().getTableHitSfx().play(getSfxGain() * 0.6f);
        if (graduation.consumeNetHitEvent())
            context.getAssets().getTableHitSfx().play(getSfxGain() * 0.5f, 0.7f, 0f);
    }

    private float getSfxGain() {
        return context.getSettings().getSfxGain();
    }

    // ── 3D zone/pole drawing ──────────────────────────────────────────────────

    private void drawZonesAndPole() {
        java.util.List<ZoneRect> zones = course.zones();
        ZoneRect active = course.activeZone();
        arena.getModelBatch().begin(arena.getCamera());
        for (int i = 0; i < zones.size() && i < 2; i++) {
            ZoneRect z = zones.get(i);
            boolean lit = active == null || active == z;
            if (!lit) continue; // only the zone that counts is shown
            ModelInstance inst = i == 0 ? zoneA : zoneB;
            inst.transform.idt()
                .translate(z.centerX(), ZONE_Y, z.centerZ())
                .scale(z.width(), 1f, z.depth());
            arena.getModelBatch().render(inst, arena.getEnvironment());
        }
        if (course.hasPole()) {
            pole.transform.idt().translate(TutorialGeometry.POLE_X,
                MatchWorld3D.TABLE_TOP_Y + TutorialGeometry.POLE_HEIGHT * 0.5f,
                TutorialGeometry.POLE_Z);
            arena.getModelBatch().render(pole, arena.getEnvironment());
        }
        arena.getModelBatch().end();
    }

    // ── HUD ───────────────────────────────────────────────────────────────────

    private void drawHud(SpriteBatch batch) {
        float cx = GameConfig.WORLD_WIDTH * 0.5f;
        if (graduation != null) {
            drawCentered(batch, context.getBodyFont(),
                "GRADUATION — FIRST TO 3", cx, GameConfig.WORLD_HEIGHT - 40f, Palette.WARM);
            drawCentered(batch, context.getBodyFont(),
                graduation.getStatusText(), cx, GameConfig.WORLD_HEIGHT - 70f, Palette.TEXT_DIM);
            if (finished) {
                drawCentered(batch, context.getTitleFont(),
                    won ? "COURSE COMPLETE" : "SO CLOSE",
                    cx, GameConfig.WORLD_HEIGHT * 0.55f, won ? Palette.GREEN : Palette.RED);
                drawCentered(batch, context.getBodyFont(),
                    won ? "CLICK TO RETURN TO THE MENU" : "CLICK TO REMATCH THE BOT",
                    cx, GameConfig.WORLD_HEIGHT * 0.45f, Palette.TEXT);
            }
            return;
        }

        drawCentered(batch, context.getBodyFont(),
            "DRILL " + course.drillNumber() + "/" + (DrillCourse.drillCount() + 1)
                + " — " + course.drill(),
            cx, GameConfig.WORLD_HEIGHT - 40f, Palette.WARM);
        drawCentered(batch, context.getBodyFont(),
            course.instruction(), cx, GameConfig.WORLD_HEIGHT - 70f, Palette.TEXT);

        StringBuilder pips = new StringBuilder();
        for (int i = 0; i < course.required(); i++) {
            pips.append(i < course.progress() ? '●' : '○').append(' ');
        }
        drawCentered(batch, context.getBodyFont(), pips.toString().trim(),
            cx, GameConfig.WORLD_HEIGHT - 100f, Palette.GREEN);

        if (course.isDemoBall()) {
            drawCentered(batch, context.getBodyFont(), ">> WATCH <<",
                cx, GameConfig.WORLD_HEIGHT * 0.62f, Palette.WARM);
        }
        if (!course.feedback().isEmpty()) {
            drawCentered(batch, context.getBodyFont(), course.feedback(),
                cx, 80f, Palette.TEXT_DIM);
        }
    }

    // ── input ─────────────────────────────────────────────────────────────────

    private static final class TutInput extends InputAdapter {
        int lastClickX, lastClickY, lastMouseX, lastMouseY;
        private boolean clickReq, menuReq;

        boolean consumeClick() { boolean v = clickReq; clickReq = false; return v; }
        boolean consumeMenu()  { boolean v = menuReq;  menuReq  = false; return v; }

        @Override public boolean keyDown(int k) {
            if (k == Input.Keys.ESCAPE) { menuReq = true; return true; }
            return false;
        }
        @Override public boolean touchDown(int sx, int sy, int ptr, int btn) {
            lastClickX = sx; lastClickY = sy; clickReq = true; return true;
        }
        @Override public boolean mouseMoved(int sx, int sy) {
            lastMouseX = sx; lastMouseY = sy; return false;
        }
    }
}
