package io.github.some_example_name.config;

import com.badlogic.gdx.graphics.Color;

/**
 * Buckshot-Roulette-style muddy bunker palette.
 *
 * <p>Every visible surface in the game is quantized to these 16 tones by
 * the retro post-process shader (see
 * {@link io.github.some_example_name.render.RetroPostProcess#PALETTE_RGB}).
 * Authoring outside this set is fine, but expect colors to snap to the
 * nearest match.</p>
 */
public final class Palette {
    private Palette() {}

    // ── Backgrounds / surfaces ────────────────────────────────────────────────
    public static final Color BG        = Color.valueOf("0A0608");
    public static final Color BG2       = Color.valueOf("1A1014");
    public static final Color SURFACE   = Color.valueOf("1A1014");
    public static final Color BORDER    = Color.valueOf("3B342E");
    public static final Color BORDER_HI = Color.valueOf("4D4036");

    // ── Reds (rust / dried blood) ─────────────────────────────────────────────
    public static final Color RED       = Color.valueOf("A03B3B");
    public static final Color RED_DIM   = Color.valueOf("5E1F1F");
    public static final Color RED_GLOW  = Color.valueOf("7A2A2A");

    // ── Greens (sickly felt / paint) ──────────────────────────────────────────
    public static final Color GREEN     = Color.valueOf("7B8C5A");
    public static final Color GREEN_DIM = Color.valueOf("4A5A3C");

    // ── Warm overhead-bulb tones (CTAs, "live" indicators) ────────────────────
    public static final Color WARM      = Color.valueOf("D89F66");
    public static final Color WARM_DIM  = Color.valueOf("8C5C36");

    // ── Text ──────────────────────────────────────────────────────────────────
    public static final Color TEXT      = Color.valueOf("E8DCC0");
    public static final Color TEXT_DIM  = Color.valueOf("6B5B4B");
    public static final Color TEXT_OFF  = Color.valueOf("3B342E");

    // ── Compatibility aliases for older code paths ────────────────────────────
    // Kept so any UIDraw call that referenced these still compiles; they all
    // point at palette-faithful neighbours.
    public static final Color YELLOW    = WARM;
    public static final Color BLUE      = WARM_DIM;
    public static final Color PINK      = RED;
    public static final Color TEAL      = GREEN_DIM;
}
