package io.github.some_example_name.config;

/** Shared constants for the prototype foundation. */
public final class GameConfig {
    public static final String GAME_TITLE = "Aim Roulette Pong";

    public static final float WORLD_WIDTH = 1280f;
    public static final float WORLD_HEIGHT = 720f;

    public static final int DEFAULT_LIVES = 5;
    public static final int LOADOUT_OPTIONS = 3;

    public static final float BASE_APPROACH_DURATION = 1.55f;
    public static final float APPROACH_DURATION_DECAY = 0.07f;
    public static final float MIN_APPROACH_DURATION = 0.55f;

    public static final float OPENING_DELAY = 0.85f;
    public static final float BOT_RESPONSE_DELAY = 0.7f;
    public static final float BETWEEN_POINTS_DELAY = 0.95f;
    public static final float BOT_BASE_RETURN_CHANCE = 0.73f;
    public static final float LOADING_MIN_SHOW_TIME = 0.8f;

    /** Network mode: how long host waits for client hit before scoring a miss. */
    public static final float NET_CLIENT_MISS_TIMEOUT = 4.5f;

    public static final float HUD_PADDING = 40f;

    /** Default host for the persistent dedicated server (override with env {@code PINGPONG_SERVER_HOST}). */
    public static final String DEFAULT_SERVER_HOST = "127.0.0.1";

    public static String resolveServerHost() {
        String env = System.getenv("PINGPONG_SERVER_HOST");
        return (env != null && !env.isBlank()) ? env.trim() : DEFAULT_SERVER_HOST;
    }

    private GameConfig() {
    }
}
