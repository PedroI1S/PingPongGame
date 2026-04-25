package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.some_example_name.Main;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.model.ItemDefinition;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.world.MatchWorld3D;

public final class MatchScreen3D extends BaseScreen {
    private static final Color HUD_TEXT_COLOR     = Color.valueOf("E9FFFC");
    private static final Color HUD_STATS_COLOR    = Color.valueOf("A9DCE1");
    private static final Color HUD_STATUS_COLOR   = Color.valueOf("FFF0B8");
    private static final Color HUD_HINT_COLOR     = Color.valueOf("A4E6D7");
    private static final Color OUTCOME_TITLE      = Color.valueOf("ECFFFC");
    private static final Color OUTCOME_SUBTITLE   = Color.valueOf("B7DFE4");

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

    public MatchScreen3D(Main game) {
        super(game);
    }

    @Override
    public void show() {
        world = new MatchWorld3D(context.getSession().buildMatchConfig(), context.getSession().getRandom());
        outcomeRecorded = false;

        camera3D = new PerspectiveCamera(60f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera3D.position.set(0f, 4.5f, 11f);
        camera3D.lookAt(0f, MatchWorld3D.TABLE_TOP_Y, 0f);
        camera3D.near = 0.1f;
        camera3D.far  = 100f;
        camera3D.update();

        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.45f, 0.5f, 0.55f, 1f));
        environment.add(new DirectionalLight().set(0.9f, 0.95f, 1f, -0.4f, -0.8f, -0.3f));

        buildModels();
        input = new Input3D();
        Gdx.input.setInputProcessor(input);
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

        if (input.consumeClick()) {
            Ray ray = camera3D.getPickRay(input.lastClickX, input.lastClickY);
            world.tryHitBall(ray);
        }

        world.update(delta);
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
        drawHud(batch);
        if (world.isMatchOver()) {
            drawOutcomeOverlay(batch);
        }
        batch.end();
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
                "Click the ball when it crosses the table. R restarts. ESC goes to menu.",
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
        private boolean clickRequested;
        private boolean menuRequested;
        private boolean restartRequested;

        boolean consumeClick()   { boolean v = clickRequested;   clickRequested   = false; return v; }
        boolean consumeMenu()    { boolean v = menuRequested;    menuRequested    = false; return v; }
        boolean consumeRestart() { boolean v = restartRequested; restartRequested = false; return v; }

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
    }
}
