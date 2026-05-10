package io.github.some_example_name.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import io.github.some_example_name.config.Palette;

/**
 * Clickable rectangular UI button.  Worn-metal-plate look:
 * dark fill, brighter accent on hover, rivet-thick border that
 * thickens when hovered.
 *
 * <p>Coordinates are in viewport world units (1280 × 720).</p>
 *
 * <pre>{@code
 * private final Button hostBtn = new Button(
 *     cx - 200, 380, 180, 90, "[ HOST ]", Palette.RED, this::startHosting);
 *
 * // each frame:
 * hostBtn.updateHover(mouseWorldX, mouseWorldY);
 * hostBtn.draw(batch, pixel, font, glyph);
 *
 * // on touch-down:
 * if (hostBtn.tryClick(mouseWorldX, mouseWorldY)) return true;
 * }</pre>
 */
public final class Button {

    public final Rectangle bounds = new Rectangle();
    public String   label;
    public Color    accent;
    public Runnable onClick;
    public boolean  hovered;
    public boolean  enabled = true;

    public Button(float x, float y, float w, float h,
                  String label, Color accent, Runnable onClick) {
        bounds.set(x, y, w, h);
        this.label   = label;
        this.accent  = accent;
        this.onClick = onClick;
    }

    /** Updates {@link #hovered} for the current cursor position. */
    public void updateHover(float worldX, float worldY) {
        hovered = enabled && bounds.contains(worldX, worldY);
    }

    /** Returns true and runs the action if the click hits this button. */
    public boolean tryClick(float worldX, float worldY) {
        if (!enabled || !bounds.contains(worldX, worldY)) return false;
        if (onClick != null) onClick.run();
        return true;
    }

    /** Draws the button into the supplied (already-begun) SpriteBatch. */
    public void draw(SpriteBatch batch, Texture pixel, BitmapFont font, GlyphLayout glyph) {
        // Background tint — accent at low alpha, brighter when hovered.
        float bgAlpha = !enabled ? 0.04f : (hovered ? 0.22f : 0.08f);
        UIDraw.fill(batch, pixel, accent, bgAlpha,
            bounds.x, bounds.y, bounds.width, bounds.height);
        UIDraw.fill(batch, pixel, Palette.BG, 0.45f,
            bounds.x + 1f, bounds.y + 1f, bounds.width - 2f, bounds.height - 2f);

        // Riveted-plate border.
        float thickness = !enabled ? 1f : (hovered ? 2.5f : 1.5f);
        UIDraw.border(batch, pixel, accent,
            bounds.x, bounds.y, bounds.width, bounds.height, thickness);

        // Top accent strip.
        UIDraw.fill(batch, pixel, accent,
            bounds.x, bounds.y + bounds.height - thickness,
            bounds.width, thickness);

        // Label.
        Color labelColor = !enabled ? Palette.TEXT_DIM : (hovered ? Palette.TEXT : accent);
        UIDraw.centered(batch, font, glyph, label,
            bounds.x + bounds.width * 0.5f,
            bounds.y + bounds.height * 0.5f + font.getCapHeight() * 0.5f,
            labelColor);
    }
}
