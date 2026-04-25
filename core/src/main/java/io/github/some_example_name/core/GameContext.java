package io.github.some_example_name.core;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import io.github.some_example_name.assets.GameAssets;
import io.github.some_example_name.config.GameConfig;

/** Shared objects that live for the whole application lifetime. */
public final class GameContext implements Disposable {
    private final SpriteBatch batch = new SpriteBatch();
    private final OrthographicCamera camera = new OrthographicCamera();
    private final FitViewport viewport = new FitViewport(GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT, camera);
    private final BitmapFont bodyFont = new BitmapFont();
    private final BitmapFont titleFont = new BitmapFont();
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final Texture loadingPixel;
    private final GameAssets assets = new GameAssets();
    private final GameSession session = new GameSession();

    public GameContext() {
        titleFont.getData().setScale(2.2f);
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pixmap.setColor(1f, 1f, 1f, 1f);
        pixmap.fill();
        loadingPixel = new Texture(pixmap);
        pixmap.dispose();
        viewport.apply(true);
    }

    public SpriteBatch getBatch() {
        return batch;
    }

    public FitViewport getViewport() {
        return viewport;
    }

    public BitmapFont getBodyFont() {
        return bodyFont;
    }

    public BitmapFont getTitleFont() {
        return titleFont;
    }

    public GlyphLayout getGlyphLayout() {
        return glyphLayout;
    }

    public Texture getLoadingPixel() {
        return loadingPixel;
    }

    public GameAssets getAssets() {
        return assets;
    }

    public GameSession getSession() {
        return session;
    }

    @Override
    public void dispose() {
        batch.dispose();
        bodyFont.dispose();
        titleFont.dispose();
        loadingPixel.dispose();
        assets.dispose();
    }
}
