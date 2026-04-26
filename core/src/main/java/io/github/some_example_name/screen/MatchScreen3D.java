package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.some_example_name.Main;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;
import io.github.some_example_name.model.ItemDefinition;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.world.ImpactParticle3D;
import io.github.some_example_name.world.MatchWorld3D;

public final class MatchScreen3D extends BaseScreen {
    private static final Color HUD_TEXT_COLOR   = Palette.TEXT;
    private static final Color HUD_STATS_COLOR  = Palette.TEXT_DIM;
    private static final Color HUD_STATUS_COLOR = Palette.RED;
    private static final Color HUD_HINT_COLOR   = Palette.TEXT_DIM;
    private static final Color OUTCOME_TITLE    = Palette.TEXT;
    private static final Color OUTCOME_SUBTITLE = Palette.TEXT_DIM;

    private MatchWorld3D world;
    private PerspectiveCamera camera3D;
    private ModelBatch modelBatch;
    private Environment environment;

    private Model tableModel;
    private Model netModel;
    private Model ballModel;
    private Model floorModel;
    private ModelInstance tableInstance;
    private ModelInstance netInstance;
    private ModelInstance ballInstance;
    private ModelInstance floorInstance;

    private Input3D input;
    private boolean outcomeRecorded;

    private Sound paddleHitSfx;
    private Sound tableHitSfx;
    private Music backgroundMusic;

    // ── camera control state ────────────────────────────────────────────────
    private static final float CAMERA_TRANSLATE_SPEED = 6f;        // units per second
    private static final float CAMERA_MIN_DISTANCE    = 6f;
    private static final float CAMERA_MAX_DISTANCE    = 18f;
    private final Vector3 cameraTarget = new Vector3(0f, MatchWorld3D.TABLE_TOP_Y, 0f);
    private final Vector3 cameraOffset = new Vector3(0f, 2.5f, 11f);

    // ── reusable buffers for project()/unproject() ──────────────────────────
    private final Vector3 worldToScreen = new Vector3();
    private final Vector3 screenToWorld = new Vector3();
    private final Vector2 cursorOnTable = new Vector2();

    public MatchScreen3D(Main game) {
        super(game);
    }

    @Override
    public void show() {
        world = new MatchWorld3D(context.getSession().buildMatchConfig(), context.getSession().getRandom());
        outcomeRecorded = false;

        camera3D = new PerspectiveCamera(60f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera3D.near = 0.1f;
        camera3D.far  = 100f;
        cameraTarget.set(0f, MatchWorld3D.TABLE_TOP_Y, 0f);
        cameraOffset.set(0f, 2.5f, 11f);
        applyCameraTransform();

        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.45f, 0.5f, 0.55f, 1f));
        environment.add(new DirectionalLight().set(0.9f, 0.95f, 1f, -0.4f, -0.8f, -0.3f));

        buildModels();
        input = new Input3D();
        Gdx.input.setInputProcessor(input);

        paddleHitSfx    = context.getAssets().getBallHitSfx();
        tableHitSfx     = context.getAssets().getTableHitSfx();
        backgroundMusic = context.getAssets().getBackgroundMusic();
        backgroundMusic.setLooping(true);
        backgroundMusic.setVolume(0.25f);
        backgroundMusic.play();
    }

    private void buildModels() {
        ModelBuilder mb = new ModelBuilder();
        long attrs = Usage.Position | Usage.Normal;

        tableModel = mb.createBox(
            MatchWorld3D.TABLE_HALF_WIDTH * 2f,
            0.2f,
            MatchWorld3D.TABLE_HALF_LENGTH * 2f,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("1A6E5F"))),
            attrs
        );
        tableInstance = new ModelInstance(tableModel);
        tableInstance.transform.setToTranslation(0f, MatchWorld3D.TABLE_TOP_Y - 0.1f, 0f);

        netModel = mb.createBox(
            MatchWorld3D.TABLE_HALF_WIDTH * 2f + 0.6f,
            MatchWorld3D.NET_HEIGHT,
            0.06f,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("E8C06A"))),
            attrs
        );
        netInstance = new ModelInstance(netModel);
        netInstance.transform.setToTranslation(0f, MatchWorld3D.TABLE_TOP_Y + MatchWorld3D.NET_HEIGHT * 0.5f, 0f);

        ballModel = mb.createSphere(
            MatchWorld3D.BALL_RADIUS * 2f,
            MatchWorld3D.BALL_RADIUS * 2f,
            MatchWorld3D.BALL_RADIUS * 2f,
            16, 16,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("F4FBFF"))),
            attrs
        );
        ballInstance = new ModelInstance(ballModel);

        floorModel = mb.createBox(
            60f, 0.4f, 60f,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("0E2026"))),
            attrs
        );
        floorInstance = new ModelInstance(floorModel);
        floorInstance.transform.setToTranslation(0f, -0.2f, 0f);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        if (backgroundMusic != null) backgroundMusic.stop();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (camera3D != null) {
            camera3D.viewportWidth  = width;
            camera3D.viewportHeight = height;
            camera3D.update();
        }
    }

    @Override
    public void render(float delta) {
        if (input.consumeMenu())    { game.openMenu();  return; }
        if (input.consumeRestart()) { game.openMatch(); return; }

        updateCameraControl(delta);

        if (input.consumeClick()) {
            Ray ray = camera3D.getPickRay(input.lastClickX, input.lastClickY);
            world.tryHitBall(ray);
        }

        // Use unproject to project the cursor onto the table plane (y = TABLE_TOP_Y).
        // The result is a 2D world-space coordinate stored in cursorOnTable —
        // used to draw a debug aim crosshair on the table surface in the HUD.
        unprojectCursorOntoTable();

        world.update(delta);
        if (world.consumePaddleHitEvent())   paddleHitSfx.play(0.7f);
        if (world.consumeTableBounceEvent()) tableHitSfx.play(0.6f);

        if (world.isMatchOver() && !outcomeRecorded) {
            context.getSession().setLastOutcome(world.getOutcome());
            outcomeRecorded = true;
        }

        if (world.isBallVisible()) {
            Vector3 p = world.getBallPos();
            ballInstance.transform.setToTranslation(p.x, p.y, p.z);
        }

        ScreenUtils.clear(0.02f, 0.05f, 0.07f, 1f, true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        modelBatch.begin(camera3D);
        modelBatch.render(floorInstance, environment);
        modelBatch.render(tableInstance, environment);
        modelBatch.render(netInstance, environment);
        if (world.isBallVisible()) {
            modelBatch.render(ballInstance, environment);
        }
        modelBatch.end();

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        context.getViewport().apply(true);
        SpriteBatch batch = context.getBatch();
        batch.setProjectionMatrix(context.getViewport().getCamera().combined);
        batch.begin();
        drawCursorOnTableMarker(batch);
        drawParticles(batch);
        drawHud(batch);
        if (world.isMatchOver()) {
            drawOutcomeOverlay(batch);
        }
        batch.end();
    }

    /** Recomputes camera.position = target + offset and refreshes the matrices. */
    private void applyCameraTransform() {
        camera3D.position.set(cameraTarget).add(cameraOffset);
        camera3D.lookAt(cameraTarget);
        camera3D.up.set(0f, 1f, 0f);
        camera3D.update();
    }

    /** WASD translates the look target on XZ; mouse wheel zooms by scaling cameraOffset. */
    private void updateCameraControl(float delta) {
        float dx = 0f, dz = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) dx -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) dx += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) dz -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) dz += 1f;

        if (dx != 0f || dz != 0f) {
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            cameraTarget.x += (dx / len) * CAMERA_TRANSLATE_SPEED * delta;
            cameraTarget.z += (dz / len) * CAMERA_TRANSLATE_SPEED * delta;
            cameraTarget.x = MathUtils.clamp(cameraTarget.x, -8f, 8f);
            cameraTarget.z = MathUtils.clamp(cameraTarget.z, -10f, 14f);
        }

        float scroll = input.consumeScroll();
        if (scroll != 0f) {
            float currentDist = cameraOffset.len();
            float newDist = MathUtils.clamp(currentDist + scroll * 1.2f,
                                            CAMERA_MIN_DISTANCE, CAMERA_MAX_DISTANCE);
            cameraOffset.scl(newDist / currentDist);
        }

        applyCameraTransform();
    }

    /**
     * Demonstrates {@code camera.unproject(...)}: takes the current cursor in
     * screen pixels, builds a world-space ray, and intersects it analytically
     * with the table-top plane (y = TABLE_TOP_Y). The XZ result lands in
     * {@link #cursorOnTable} for the HUD draw.
     */
    private void unprojectCursorOntoTable() {
        screenToWorld.set(input.lastMouseX, input.lastMouseY, 0f);
        camera3D.unproject(screenToWorld);

        Vector3 origin = camera3D.position;
        float dirX = screenToWorld.x - origin.x;
        float dirY = screenToWorld.y - origin.y;
        float dirZ = screenToWorld.z - origin.z;
        if (Math.abs(dirY) < 0.0001f) { cursorOnTable.set(Float.NaN, Float.NaN); return; }
        float t = (MatchWorld3D.TABLE_TOP_Y - origin.y) / dirY;
        if (t <= 0f) { cursorOnTable.set(Float.NaN, Float.NaN); return; }
        cursorOnTable.set(origin.x + dirX * t, origin.z + dirZ * t);
    }

    /**
     * Draws each {@link ImpactParticle3D} as a small 2D dot in HUD space —
     * uses {@code camera.project(...)} to convert the particle's 3D world
     * position into screen pixels, then maps those pixels back into the
     * 2D viewport's world coordinates so the SpriteBatch draws them correctly.
     */
    private void drawParticles(SpriteBatch batch) {
        for (ImpactParticle3D p : world.getParticles()) {
            worldToScreen.set(p.getPosition());
            camera3D.project(worldToScreen);
            if (worldToScreen.z < 0f || worldToScreen.z > 1f) continue;

            float screenPxX = worldToScreen.x;
            float screenPxY = worldToScreen.y;
            float hudX = screenPxX / Gdx.graphics.getWidth() * GameConfig.WORLD_WIDTH;
            float hudY = screenPxY / Gdx.graphics.getHeight() * GameConfig.WORLD_HEIGHT;

            float alpha = p.getAlpha();
            float size = 4f + alpha * 6f;
            batch.setColor(1f, 1f, 0.85f, alpha * 0.85f);
            batch.draw(context.getAssets().getProceduralAssets().getGlow(),
                       hudX - size * 0.5f, hudY - size * 0.5f, size, size);
        }
        batch.setColor(Color.WHITE);
    }

    /**
     * Round-trip demonstration: takes the world-space XZ point produced by
     * {@link #unprojectCursorOntoTable()} (already an unproject result),
     * lifts it back into 3D, and uses {@code camera.project(...)} to map it
     * onto the HUD plane. Renders a small ring marking where the cursor
     * "lands" on the virtual table surface.
     */
    private void drawCursorOnTableMarker(SpriteBatch batch) {
        if (Float.isNaN(cursorOnTable.x)) return;

        worldToScreen.set(cursorOnTable.x, MatchWorld3D.TABLE_TOP_Y, cursorOnTable.y);
        camera3D.project(worldToScreen);
        if (worldToScreen.z < 0f || worldToScreen.z > 1f) return;

        float hudX = worldToScreen.x / Gdx.graphics.getWidth()  * GameConfig.WORLD_WIDTH;
        float hudY = worldToScreen.y / Gdx.graphics.getHeight() * GameConfig.WORLD_HEIGHT;
        float size = 28f;
        batch.setColor(0.45f, 0.95f, 0.85f, 0.55f);
        batch.draw(context.getAssets().getProceduralAssets().getAimRing(),
                   hudX - size * 0.5f, hudY - size * 0.5f, size, size);
        batch.setColor(Color.WHITE);
    }

    private void drawHud(SpriteBatch batch) {
        ItemDefinition playerItem = context.getSession().getPlayerItem();
        ItemDefinition botItem    = context.getSession().getBotItem();

        context.getBodyFont().setColor(HUD_TEXT_COLOR);
        context.getBodyFont().draw(batch, "YOU  " + world.getPlayerLives(), GameConfig.HUD_PADDING, 680f);
        context.getBodyFont().draw(batch, "BOT  " + world.getBotLives(),    1120f, 680f);

        if (playerItem != null) {
            context.getBodyFont().setColor(playerItem.getAccent());
            context.getBodyFont().draw(batch, "Item: " + playerItem.getName(), GameConfig.HUD_PADDING, 646f);
        }
        if (botItem != null) {
            context.getBodyFont().setColor(botItem.getAccent());
            context.getBodyFont().draw(batch, "Bot item: " + botItem.getName(), 1000f, 646f);
        }

        context.getBodyFont().setColor(HUD_STATS_COLOR);
        context.getBodyFont().draw(batch,
            String.format("Read window: %.2fs", world.getDisplayedReadWindow()),
            500f, 680f);
        context.getBodyFont().draw(batch, "Rally: " + world.getRallyCount(), 600f, 646f);

        if (!world.isMatchOver()) {
            drawCentered(batch, context.getBodyFont(), world.getStatusText(),
                GameConfig.WORLD_WIDTH * 0.5f, 134f, HUD_STATUS_COLOR);
            drawCentered(batch, context.getBodyFont(),
                "Click the ball to return. WASD pans the camera, scroll to zoom. R restarts. ESC menu.",
                GameConfig.WORLD_WIDTH * 0.5f, 102f, HUD_HINT_COLOR);
        }
    }

    private void drawOutcomeOverlay(SpriteBatch batch) {
        MatchOutcome outcome = world.getOutcome();
        String title    = outcome == MatchOutcome.PLAYER_WIN ? "Duel won" : "Bot wins this round";
        String subtitle = outcome == MatchOutcome.PLAYER_WIN
            ? "3D physics return loop is alive. Click → arc → land in."
            : "The shot got through. Try again with a tighter centre click.";

        drawCentered(batch, context.getTitleFont(), title,
            GameConfig.WORLD_WIDTH * 0.5f, 420f, OUTCOME_TITLE);
        drawCentered(batch, context.getBodyFont(), subtitle,
            GameConfig.WORLD_WIDTH * 0.5f, 372f, OUTCOME_SUBTITLE);
        drawCentered(batch, context.getBodyFont(),
            "Press R to replay this setup or ESC to go back to the menu.",
            GameConfig.WORLD_WIDTH * 0.5f, 330f, HUD_STATUS_COLOR);
    }

    @Override
    public void dispose() {
        if (modelBatch  != null) modelBatch.dispose();
        if (tableModel  != null) tableModel.dispose();
        if (netModel    != null) netModel.dispose();
        if (ballModel   != null) ballModel.dispose();
        if (floorModel  != null) floorModel.dispose();
    }

    private static final class Input3D extends InputAdapter {
        int lastClickX, lastClickY;
        int lastMouseX, lastMouseY;
        private boolean clickRequested;
        private boolean menuRequested;
        private boolean restartRequested;
        private float scrollAccumulator;

        boolean consumeClick()   { boolean v = clickRequested;   clickRequested   = false; return v; }
        boolean consumeMenu()    { boolean v = menuRequested;    menuRequested    = false; return v; }
        boolean consumeRestart() { boolean v = restartRequested; restartRequested = false; return v; }
        float   consumeScroll()  { float v = scrollAccumulator;  scrollAccumulator = 0f;   return v; }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.ESCAPE) { menuRequested = true; return true; }
            if (keycode == Input.Keys.R)      { restartRequested = true; return true; }
            return false;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            lastClickX = screenX;
            lastClickY = screenY;
            clickRequested = true;
            return true;
        }

        @Override
        public boolean mouseMoved(int screenX, int screenY) {
            lastMouseX = screenX;
            lastMouseY = screenY;
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            scrollAccumulator += amountY;
            return true;
        }
    }
}
