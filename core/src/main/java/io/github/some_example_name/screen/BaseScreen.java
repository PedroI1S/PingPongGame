package io.github.some_example_name.screen;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.some_example_name.Main;
import io.github.some_example_name.core.GameContext;

/** Common helpers for all game screens. */
public abstract class BaseScreen extends ScreenAdapter {
    protected final Main game;
    protected final GameContext context;

    protected BaseScreen(Main game) {
        this.game = game;
        this.context = game.getContext();
    }

    protected void beginFrame(float r, float g, float b) {
        ScreenUtils.clear(r, g, b, 1f);
        context.getViewport().apply(true);
        context.getBatch().setProjectionMatrix(context.getViewport().getCamera().combined);
    }

    protected void drawCentered(SpriteBatch batch, BitmapFont font, String text, float centerX, float y, Color color) {
        context.getGlyphLayout().setText(font, text);
        font.setColor(color);
        font.draw(batch, text, centerX - context.getGlyphLayout().width * 0.5f, y);
    }

    @Override
    public void resize(int width, int height) {
        context.getViewport().update(width, height, true);
    }
}
