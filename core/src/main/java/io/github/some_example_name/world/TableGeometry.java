package io.github.some_example_name.world;

import com.badlogic.gdx.math.MathUtils;
import io.github.some_example_name.config.GameConfig;

/** Centralised table geometry — all trapezoid and net calculations go here. */
public final class TableGeometry {

    /** Y depth of the net centre-line in screen space. */
    public static final float NET_Y =
        (GameConfig.TABLE_NET_TOP_Y + GameConfig.TABLE_NET_BOTTOM_Y) * 0.5f;

    /** How tall the net is in fake-Z units (screen pixels equivalent). */
    public static final float NET_HEIGHT_Z = 38f;

    private TableGeometry() {}

    // ── basic trapezoid helpers ──────────────────────────────────────────────

    public static float leftX(float y) {
        float p = depthProgress(y);
        return GameConfig.WORLD_WIDTH * 0.5f
            - MathUtils.lerp(GameConfig.TABLE_FAR_HALF_WIDTH, GameConfig.TABLE_NEAR_HALF_WIDTH, p);
    }

    public static float rightX(float y) {
        float p = depthProgress(y);
        return GameConfig.WORLD_WIDTH * 0.5f
            + MathUtils.lerp(GameConfig.TABLE_FAR_HALF_WIDTH, GameConfig.TABLE_NEAR_HALF_WIDTH, p);
    }

    /** Screen X for a normalised lane (0 = left edge, 1 = right edge) at depth y. */
    public static float xAt(float y, float lane) {
        return MathUtils.lerp(leftX(y), rightX(y), lane);
    }

    /** Normalised lane [0,1] for a screen-X position at depth y. */
    public static float laneOf(float x, float y) {
        float left  = leftX(y);
        float right = rightX(y);
        if (right <= left) return 0.5f;
        return MathUtils.clamp((x - left) / (right - left), 0f, 1f);
    }

    public static boolean insideTable(float x, float y) {
        if (y < GameConfig.TABLE_NEAR_Y || y > GameConfig.TABLE_FAR_Y) return false;
        return x >= leftX(y) && x <= rightX(y);
    }

    public static boolean onPlayerSide(float y) {
        return y >= GameConfig.TABLE_NEAR_Y && y <= NET_Y;
    }

    public static boolean onBotSide(float y) {
        return y > NET_Y && y <= GameConfig.TABLE_FAR_Y;
    }

    /**
     * Projects world-y + fake-Z height into a screen-Y value.
     * A positive z lifts the ball visually upward in screen space.
     */
    public static float screenY(float worldY, float z) {
        return worldY + z;
    }

    // ── private ─────────────────────────────────────────────────────────────

    /**
     * 0 at TABLE_FAR_Y (top of screen, bot side),
     * 1 at TABLE_NEAR_Y (bottom of screen, player side).
     */
    private static float depthProgress(float y) {
        return MathUtils.clamp(
            (y - GameConfig.TABLE_FAR_Y) / (GameConfig.TABLE_NEAR_Y - GameConfig.TABLE_FAR_Y),
            0f, 1f
        );
    }
}
