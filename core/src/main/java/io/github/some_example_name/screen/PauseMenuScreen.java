package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.Screen;
import io.github.some_example_name.Main;
import io.github.some_example_name.assets.ProceduralAssets;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;

/** Pause overlay for match screens. */
public final class PauseMenuScreen extends BaseScreen {
    private final Screen resumeScreen;
    private final Runnable quitAction;
    private final InputHandler input = new InputHandler();
    private final Vector3 cursorWorld = new Vector3();

    private final Button resumeBtn;
    private final Button settingsBtn;
    private final Button menuBtn;

    public PauseMenuScreen(Main game, Screen resumeScreen, Runnable quitAction) {
        super(game);
        this.resumeScreen = resumeScreen;
        this.quitAction = quitAction;

        float cx = GameConfig.WORLD_WIDTH * 0.5f;
        resumeBtn = new Button(cx - 140f, 360f, 280f, 56f, "RESUME", Palette.RED, () -> game.setScreen(resumeScreen));
        settingsBtn = new Button(cx - 140f, 290f, 280f, 56f, "SETTINGS", Palette.WARM, () -> game.openConfig(() -> game.setScreen(this)));
        menuBtn = new Button(cx - 140f, 220f, 280f, 56f, "QUIT TO MENU", Palette.TEXT_DIM, this::quit);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
    }

    @Override
    public void render(float delta) {
        if (input.consumeResume()) { game.setScreen(resumeScreen); return; }
        if (input.consumeQuit()) { quit(); return; }

        updateCursorWorld();
        resumeBtn.updateHover(cursorWorld.x, cursorWorld.y);
        settingsBtn.updateHover(cursorWorld.x, cursorWorld.y);
        menuBtn.updateHover(cursorWorld.x, cursorWorld.y);

        // Pause overlay is a menu — render to back buffer, no retro filter.
        beginFrame(Palette.BG.r, Palette.BG.g, Palette.BG.b);
        SpriteBatch batch = context.getBatch();
        ProceduralAssets visuals = context.getAssets().getProceduralAssets();
        Texture pixel = visuals.getPixel();
        BitmapFont title = context.getTitleFont();
        BitmapFont body = context.getBodyFont();

        batch.begin();
        batch.setColor(Color.WHITE);
        UIDraw.fill(batch, pixel, Palette.BG, 0f, 0f, GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT);
        UIDraw.cornerMarks(batch, pixel, 24f);
        UIDraw.topBar(batch, pixel, body, context.getGlyphLayout(), "PAUSED", context.getSettings().getSummary(), Palette.WARM);

        float cx = GameConfig.WORLD_WIDTH * 0.5f;
        UIDraw.centered(batch, body, context.getGlyphLayout(), "===  PAUSE MENU  ===", cx, 620f, Palette.RED);
        title.getData().setScale(3.1f);
        UIDraw.centered(batch, title, context.getGlyphLayout(), "PAUSED", cx, 565f, Palette.TEXT);
        title.getData().setScale(2.2f);
        UIDraw.centered(batch, body, context.getGlyphLayout(), "THE MATCH IS FROZEN IN PLACE.", cx, 500f, Palette.TEXT_DIM);

        resumeBtn.draw(batch, pixel, body, context.getGlyphLayout());
        settingsBtn.draw(batch, pixel, body, context.getGlyphLayout());
        menuBtn.draw(batch, pixel, body, context.getGlyphLayout());

        UIDraw.centered(batch, body, context.getGlyphLayout(), "ESC = RESUME", cx, 170f, Palette.TEXT_DIM);
        batch.end();
    }

    private void updateCursorWorld() {
        cursorWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        context.getViewport().unproject(cursorWorld);
    }

    private void quit() {
        if (quitAction != null) quitAction.run();
        else game.openMenu();
    }

    private final class InputHandler extends InputAdapter {
        private boolean resumeRequested;
        private boolean quitRequested;

        boolean consumeResume() { boolean value = resumeRequested; resumeRequested = false; return value; }
        boolean consumeQuit() { boolean value = quitRequested; quitRequested = false; return value; }

        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.ESCAPE) { resumeRequested = true; return true; }
            if (keycode == Input.Keys.Q) { quitRequested = true; return true; }
            return false;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            updateCursorWorld();
            if (resumeBtn.tryClick(cursorWorld.x, cursorWorld.y)) return true;
            if (settingsBtn.tryClick(cursorWorld.x, cursorWorld.y)) return true;
            if (menuBtn.tryClick(cursorWorld.x, cursorWorld.y)) return true;
            return false;
        }
    }
}