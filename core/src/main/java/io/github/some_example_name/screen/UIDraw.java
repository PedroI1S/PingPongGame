package io.github.some_example_name.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;

/** Reusable widgets for the dark-apocalyptic UI: bars, borders, corner marks. */
public final class UIDraw {
    private UIDraw() {}

    public static void fill(SpriteBatch batch, Texture pixel, Color c, float x, float y, float w, float h) {
        batch.setColor(c);
        batch.draw(pixel, x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    public static void fill(SpriteBatch batch, Texture pixel, Color c, float alpha,
                            float x, float y, float w, float h) {
        batch.setColor(c.r, c.g, c.b, alpha);
        batch.draw(pixel, x, y, w, h);
        batch.setColor(Color.WHITE);
    }

    /** Hollow border around (x,y,w,h) with given thickness and color. */
    public static void border(SpriteBatch batch, Texture pixel, Color c,
                              float x, float y, float w, float h, float thickness) {
        batch.setColor(c);
        batch.draw(pixel, x, y, w, thickness);                       // bottom
        batch.draw(pixel, x, y + h - thickness, w, thickness);       // top
        batch.draw(pixel, x, y, thickness, h);                       // left
        batch.draw(pixel, x + w - thickness, y, thickness, h);       // right
        batch.setColor(Color.WHITE);
    }

    /** Top status bar with left/right mono labels and a 1px bottom border. */
    public static void topBar(SpriteBatch batch, Texture pixel, BitmapFont font,
                              GlyphLayout layout, String left, String right, Color rightColor) {
        float h = 36f;
        float y = GameConfig.WORLD_HEIGHT - h;
        fill(batch, pixel, Palette.BG2, 0f, y, GameConfig.WORLD_WIDTH, h);
        fill(batch, pixel, Palette.BORDER, 0f, y, GameConfig.WORLD_WIDTH, 1f);
        font.setColor(Palette.TEXT_DIM);
        font.draw(batch, left, 28f, y + 24f);
        if (right != null) {
            font.setColor(rightColor != null ? rightColor : Palette.TEXT_DIM);
            layout.setText(font, right);
            font.draw(batch, right, GameConfig.WORLD_WIDTH - 28f - layout.width, y + 24f);
        }
    }

    /** Bottom status bar mirroring the top. */
    public static void bottomBar(SpriteBatch batch, Texture pixel, BitmapFont font,
                                 GlyphLayout layout, String left, String right, Color rightColor) {
        float h = 36f;
        fill(batch, pixel, Palette.BG2, 0f, 0f, GameConfig.WORLD_WIDTH, h);
        fill(batch, pixel, Palette.BORDER, 0f, h, GameConfig.WORLD_WIDTH, 1f);
        font.setColor(Palette.TEXT_DIM);
        font.draw(batch, left, 28f, 24f);
        if (right != null) {
            font.setColor(rightColor != null ? rightColor : Palette.TEXT_DIM);
            layout.setText(font, right);
            font.draw(batch, right, GameConfig.WORLD_WIDTH - 28f - layout.width, 24f);
        }
    }

    /** Four 20px L-shaped corner marks framing the playable area. */
    public static void cornerMarks(SpriteBatch batch, Texture pixel, float inset) {
        float W = GameConfig.WORLD_WIDTH;
        float H = GameConfig.WORLD_HEIGHT;
        float L = 20f;
        // Top-left
        fill(batch, pixel, Palette.BORDER, inset, H - inset - 1f, L, 1f);
        fill(batch, pixel, Palette.BORDER, inset, H - inset - L, 1f, L);
        // Top-right
        fill(batch, pixel, Palette.BORDER, W - inset - L, H - inset - 1f, L, 1f);
        fill(batch, pixel, Palette.BORDER, W - inset - 1f, H - inset - L, 1f, L);
        // Bottom-left
        fill(batch, pixel, Palette.BORDER, inset, inset, L, 1f);
        fill(batch, pixel, Palette.BORDER, inset, inset, 1f, L);
        // Bottom-right
        fill(batch, pixel, Palette.BORDER, W - inset - L, inset, L, 1f);
        fill(batch, pixel, Palette.BORDER, W - inset - 1f, inset, 1f, L);
    }

    /** Faint horizontal scanlines drawn every 4 px across the whole frame. */
    public static void scanlines(SpriteBatch batch, Texture pixel) {
        batch.setColor(0f, 0f, 0f, 0.18f);
        for (float y = 0f; y < GameConfig.WORLD_HEIGHT; y += 4f) {
            batch.draw(pixel, 0f, y, GameConfig.WORLD_WIDTH, 2f);
        }
        batch.setColor(Color.WHITE);
    }

    /** Faint 80x80 red grid covering the world. */
    public static void redGrid(SpriteBatch batch, Texture pixel, float alpha) {
        batch.setColor(Palette.RED.r, Palette.RED.g, Palette.RED.b, alpha);
        for (float x = 0f; x < GameConfig.WORLD_WIDTH;  x += 80f) batch.draw(pixel, x, 0f, 1f, GameConfig.WORLD_HEIGHT);
        for (float y = 0f; y < GameConfig.WORLD_HEIGHT; y += 80f) batch.draw(pixel, 0f, y, GameConfig.WORLD_WIDTH, 1f);
        batch.setColor(Color.WHITE);
    }

    /** Panel with red-dim left edge — the design's signature container. */
    public static void redLeftPanel(SpriteBatch batch, Texture pixel,
                                    float x, float y, float w, float h) {
        fill(batch, pixel, Palette.BG2, 0.92f, x, y, w, h);
        border(batch, pixel, Palette.BORDER, x, y, w, h, 1f);
        fill(batch, pixel, Palette.RED_DIM, x, y, 2f, h);
    }

    /** Centered title-cased line. */
    public static void centered(SpriteBatch batch, BitmapFont font, GlyphLayout layout,
                                String text, float centerX, float y, Color color) {
        font.setColor(color);
        layout.setText(font, text);
        font.draw(batch, text, centerX - layout.width * 0.5f, y);
    }

    public static float textWidth(BitmapFont font, GlyphLayout layout, String text) {
        layout.setText(font, text);
        return layout.width;
    }
}
