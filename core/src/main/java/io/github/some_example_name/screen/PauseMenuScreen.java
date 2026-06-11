package io.github.some_example_name.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.Screen;
import io.github.some_example_name.Main;
import io.github.some_example_name.assets.ProceduralAssets;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;
import io.github.some_example_name.ui.Button;
import io.github.some_example_name.ui.UIDraw;

import java.util.Arrays;
import java.util.List;

/** Pause overlay for match screens. */
public final class PauseMenuScreen extends MenuBaseScreen {
    private final Screen resumeScreen;
    private final Runnable quitAction;

    private final Button resumeBtn;
    private final Button settingsBtn;
    private final Button menuBtn;
    private final List<Button> buttons;

    public PauseMenuScreen(Main game, Screen resumeScreen, Runnable quitAction) {
        super(game);
        this.resumeScreen = resumeScreen;
        this.quitAction = quitAction;

        float cx = GameConfig.WORLD_WIDTH * 0.5f;
        resumeBtn = new Button(cx - 140f, 360f, 280f, 56f, "RESUME", Palette.RED, () -> game.setScreen(resumeScreen));
        settingsBtn = new Button(cx - 140f, 290f, 280f, 56f, "SETTINGS", Palette.WARM, () -> game.openConfig(() -> game.setScreen(this)));
        menuBtn = new Button(cx - 140f, 220f, 280f, 56f, "QUIT TO MENU", Palette.TEXT_DIM, this::quit);
        buttons = Arrays.asList(resumeBtn, settingsBtn, menuBtn);
    }

    @Override
    protected List<Button> activeButtons() {
        return buttons;
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
    }

    @Override
    public void render(float delta) {
        updateButtonHover();

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

    private void quit() {
        if (quitAction != null) quitAction.run();
        else game.openMenu();
    }

    @Override
    protected boolean onKeyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) { game.setScreen(resumeScreen); return true; }
        if (keycode == Input.Keys.Q) { quit(); return true; }
        return false;
    }
}