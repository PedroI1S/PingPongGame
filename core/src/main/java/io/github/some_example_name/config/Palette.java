package io.github.some_example_name.config;

import com.badlogic.gdx.graphics.Color;

/**
 * Visual design tokens for the dark-apocalyptic UI pass.
 * Mirrors the palette defined in the design handoff (Game Menu.html).
 */
public final class Palette {
    public static final Color BG       = Color.valueOf("080808");
    public static final Color BG2      = Color.valueOf("0D0D0D");
    public static final Color SURFACE  = Color.valueOf("111111");
    public static final Color BORDER   = Color.valueOf("2A2A2A");
    public static final Color BORDER_HI = Color.valueOf("3A3A3A");

    public static final Color RED       = Color.valueOf("D2384C");
    public static final Color RED_DIM   = Color.valueOf("6E2532");
    public static final Color RED_GLOW  = Color.valueOf("3A1018");

    public static final Color TEXT     = Color.valueOf("D4D4D4");
    public static final Color TEXT_DIM = Color.valueOf("555555");
    public static final Color TEXT_OFF = Color.valueOf("333333");

    public static final Color GREEN  = Color.valueOf("4ADE80");
    public static final Color YELLOW = Color.valueOf("FBBF24");
    public static final Color BLUE   = Color.valueOf("60A5FA");
    public static final Color PINK   = Color.valueOf("F472B6");
    public static final Color TEAL   = Color.valueOf("2DD4BF");

    private Palette() {}
}
