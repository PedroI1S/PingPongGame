package io.github.some_example_name.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import io.github.some_example_name.config.Palette;

/**
 * Setting-row widgets for the tabbed config screen — Toggle, Slider, Radio.
 *
 * <p>Each widget is a small clickable component used inside a {@code Row()}.
 * They paint themselves into a {@link SpriteBatch} that is already begun, and
 * expose a {@code tryClick(...)} for the screen's input handler.</p>
 */
public final class SettingsWidgets {

    private SettingsWidgets() {}

    // ── Toggle ────────────────────────────────────────────────────────────────

    /**
     * 48 × 24 toggle.  Red/dim outline, sliding 16 × 16 dot.  Bound to a
     * boolean supplier/setter so the widget is stateless.
     */
    public static final class Toggle {
        public final Rectangle bounds = new Rectangle();
        public final java.util.function.BooleanSupplier reader;
        public final java.util.function.Consumer<Boolean> writer;
        public boolean hovered;

        public Toggle(float x, float y,
                      java.util.function.BooleanSupplier reader,
                      java.util.function.Consumer<Boolean> writer) {
            bounds.set(x, y, 48f, 24f);
            this.reader = reader;
            this.writer = writer;
        }

        public void updateHover(float wx, float wy) {
            hovered = bounds.contains(wx, wy);
        }

        public boolean tryClick(float wx, float wy) {
            if (!bounds.contains(wx, wy)) return false;
            writer.accept(!reader.getAsBoolean());
            return true;
        }

        public void draw(SpriteBatch batch, Texture pixel) {
            boolean on = reader.getAsBoolean();
            Color outline = on ? Palette.RED : Palette.BORDER_HI;
            // Faint background tint when on.
            if (on) UIDraw.fill(batch, pixel, Palette.RED, 0.12f,
                bounds.x, bounds.y, bounds.width, bounds.height);
            UIDraw.border(batch, pixel, outline,
                bounds.x, bounds.y, bounds.width, bounds.height,
                hovered ? 1.5f : 1f);

            // Sliding dot.
            float dotX = bounds.x + (on ? 26f : 3f);
            float dotY = bounds.y + 3f;
            UIDraw.fill(batch, pixel, on ? Palette.RED : Palette.TEXT_OFF,
                dotX, dotY, 16f, 16f);
        }
    }

    // ── Slider ────────────────────────────────────────────────────────────────

    /**
     * 220 × 20 slider with a value label rendered to its left.  The drag
     * surface accepts both clicks (snap-to-position) and drag.
     */
    public static final class Slider {
        public final Rectangle bounds = new Rectangle();
        private final float min, max, step;
        private final java.util.function.IntSupplier reader;
        private final java.util.function.IntConsumer writer;
        private final java.util.function.IntFunction<String> formatter;
        public boolean hovered;
        public boolean dragging;

        public Slider(float x, float y, float width,
                      int min, int max, int step,
                      java.util.function.IntSupplier reader,
                      java.util.function.IntConsumer writer,
                      java.util.function.IntFunction<String> formatter) {
            bounds.set(x, y, width, 20f);
            this.min = min;
            this.max = max;
            this.step = step <= 0 ? 1 : step;
            this.reader = reader;
            this.writer = writer;
            this.formatter = formatter;
        }

        public void updateHover(float wx, float wy) {
            hovered = bounds.contains(wx, wy) || dragging;
        }

        public boolean tryClick(float wx, float wy) {
            if (!bounds.contains(wx, wy)) return false;
            applyAt(wx);
            dragging = true;
            return true;
        }

        public boolean tryDrag(float wx, float wy) {
            if (!dragging) return false;
            applyAt(wx);
            return true;
        }

        public void release() {
            dragging = false;
        }

        private void applyAt(float wx) {
            float t = (wx - bounds.x) / bounds.width;
            t = Math.max(0f, Math.min(1f, t));
            float raw = min + t * (max - min);
            int snapped = (int) (Math.round(raw / step) * step);
            snapped = Math.max((int) min, Math.min((int) max, snapped));
            writer.accept(snapped);
        }

        public void draw(SpriteBatch batch, Texture pixel,
                         BitmapFont font, GlyphLayout glyph) {
            int value = reader.getAsInt();
            String label = formatter.apply(value);
            float t = (value - min) / (max - min);

            // Value label, right-aligned 12px to the left of the bar.
            float yMid = bounds.y + bounds.height * 0.5f + font.getCapHeight() * 0.5f;
            font.setColor(Palette.TEXT);
            glyph.setText(font, label);
            font.draw(batch, label, bounds.x - 12f - glyph.width, yMid);

            // Track + fill.
            float trackY = bounds.y + bounds.height * 0.5f - 1f;
            UIDraw.fill(batch, pixel, Palette.BORDER, bounds.x, trackY, bounds.width, 2f);
            UIDraw.fill(batch, pixel, Palette.RED, bounds.x, trackY, bounds.width * t, 2f);

            // Knob.
            float knobX = bounds.x + bounds.width * t - 5f;
            float knobY = bounds.y + bounds.height * 0.5f - 5f;
            UIDraw.fill(batch, pixel, Palette.RED, knobX, knobY, 10f, 10f);
        }
    }

    // ── Radio ─────────────────────────────────────────────────────────────────

    /**
     * Pill-button group.  The selected option fills with the accent color;
     * unselected sit on a thin border.
     */
    public static final class Radio<T> {
        public final Rectangle bounds = new Rectangle();
        public final T[] options;
        private final java.util.function.Supplier<T> reader;
        private final java.util.function.Consumer<T> writer;
        private final java.util.function.Function<T, String> labeler;
        private final float pillWidth;
        public int hoverIndex = -1;

        public Radio(float x, float y, float pillWidth, T[] options,
                     java.util.function.Supplier<T> reader,
                     java.util.function.Consumer<T> writer,
                     java.util.function.Function<T, String> labeler) {
            this.options   = options;
            this.reader    = reader;
            this.writer    = writer;
            this.labeler   = labeler;
            this.pillWidth = pillWidth;
            bounds.set(x, y, pillWidth * options.length, 28f);
        }

        public void updateHover(float wx, float wy) {
            hoverIndex = -1;
            if (!bounds.contains(wx, wy)) return;
            int idx = (int) ((wx - bounds.x) / pillWidth);
            if (idx >= 0 && idx < options.length) hoverIndex = idx;
        }

        public boolean tryClick(float wx, float wy) {
            if (!bounds.contains(wx, wy)) return false;
            int idx = (int) ((wx - bounds.x) / pillWidth);
            if (idx < 0 || idx >= options.length) return false;
            writer.accept(options[idx]);
            return true;
        }

        public void draw(SpriteBatch batch, Texture pixel,
                         BitmapFont font, GlyphLayout glyph) {
            T current = reader.get();
            for (int i = 0; i < options.length; i++) {
                T option = options[i];
                boolean sel = option != null && option.equals(current);
                float x = bounds.x + i * pillWidth;
                float y = bounds.y;

                Color outline = sel ? Palette.RED : Palette.BORDER_HI;
                if (sel) UIDraw.fill(batch, pixel, Palette.RED,
                    x, y, pillWidth, bounds.height);
                else if (i == hoverIndex)
                    UIDraw.fill(batch, pixel, Palette.RED, 0.12f,
                        x, y, pillWidth, bounds.height);
                UIDraw.border(batch, pixel, outline,
                    x, y, pillWidth, bounds.height, 1f);

                font.setColor(sel ? Palette.BG : (i == hoverIndex ? Palette.TEXT : Palette.TEXT_DIM));
                String label = labeler.apply(option);
                glyph.setText(font, label);
                font.draw(batch, label,
                    x + (pillWidth - glyph.width) * 0.5f,
                    y + bounds.height * 0.5f + font.getCapHeight() * 0.5f);
            }
        }
    }
}
