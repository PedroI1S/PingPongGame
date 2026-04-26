package io.github.some_example_name.config;

/** Shared constants for the prototype foundation. */
public final class GameConfig {
    public static final String GAME_TITLE = "Aim Roulette Pong -- Menu";

    public static final float WORLD_WIDTH = 1280f;
    public static final float WORLD_HEIGHT = 720f;

    public static final int DEFAULT_LIVES = 5;
    public static final int LOADOUT_OPTIONS = 3;

    public static final float TABLE_FAR_Y = 640f;
    public static final float TABLE_NET_TOP_Y = 430f;
    public static final float TABLE_NET_BOTTOM_Y = 356f;
    public static final float TABLE_NEAR_Y = 150f;

    public static final float TABLE_FAR_HALF_WIDTH = 210f;
    public static final float TABLE_NET_HALF_WIDTH = 420f;
    public static final float TABLE_NEAR_HALF_WIDTH = 590f;

    public static final float SHOT_START_RADIUS = 16f;
    public static final float SHOT_END_RADIUS = 118f;

    public static final float BASE_APPROACH_DURATION = 1.55f;
    public static final float APPROACH_DURATION_DECAY = 0.07f;
    public static final float MIN_APPROACH_DURATION = 0.55f;

    public static final float OPENING_DELAY = 0.85f;
    public static final float BOT_RESPONSE_DELAY = 0.7f;
    public static final float BETWEEN_POINTS_DELAY = 0.95f;
    public static final float BOT_BASE_RETURN_CHANCE = 0.73f;
    public static final float LOADING_MIN_SHOW_TIME = 0.8f;

    public static final float HUD_PADDING = 40f;

    private GameConfig() {
    }
}
