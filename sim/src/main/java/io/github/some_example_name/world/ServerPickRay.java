package io.github.some_example_name.world;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

/**
 * Builds the same pick rays the client uses ({@code MatchArenaRenderer} camera)
 * so the server can validate clicks from screen coordinates.
 *
 * <p><strong>Pure-Java math only</strong> — no {@code PerspectiveCamera},
 * no {@code Matrix4.prj}, no native JNI calls — so this compiles and runs
 * correctly in the headless server JVM where {@code gdx-natives} are not loaded.</p>
 */
public final class ServerPickRay {

    /** Must match {@code MatchArenaRenderer} FOV. */
    private static final float FOV_DEGREES  = 60f;
    private static final float CAM_Y_OFFSET = 2.5f;
    private static final float CAM_Z_OFFSET = 11f;

    /** Constant world-up used to build the camera basis. */
    private static final Vector3 WORLD_UP = new Vector3(0f, 1f, 0f);

    private ServerPickRay() {}

    /**
     * Returns a world-space pick ray for a mouse click on the given player's viewport.
     *
     * <p>Camera setup mirrors {@code MatchArenaRenderer} exactly:</p>
     * <ul>
     *   <li>FOV 60°, position {@code (0, TABLE_TOP_Y+2.5, ±11)}, target {@code (0, TABLE_TOP_Y, 0)}</li>
     *   <li>P1 at {@code +z}, P2 at {@code −z}</li>
     * </ul>
     */
    public static Ray fromScreen(int playerNumber, int screenX, int screenY,
                                 int viewportWidth, int viewportHeight) {
        int w = Math.max(1, viewportWidth);
        int h = Math.max(1, viewportHeight);

        // ── Camera position (matches MatchArenaRenderer) ──────────────────────
        float zOffset = (playerNumber == 1) ? CAM_Z_OFFSET : -CAM_Z_OFFSET;
        Vector3 pos = new Vector3(0f, MatchWorld3D.TABLE_TOP_Y + CAM_Y_OFFSET, zOffset);

        // ── Camera basis vectors ──────────────────────────────────────────────
        Vector3 target  = new Vector3(0f, MatchWorld3D.TABLE_TOP_Y, 0f);
        Vector3 forward = new Vector3(target).sub(pos).nor();
        Vector3 right   = new Vector3(forward).crs(WORLD_UP).nor();
        Vector3 camUp   = new Vector3(right).crs(forward).nor();

        // ── NDC: x ∈ [−1, 1] left→right, y ∈ [−1, 1] bottom→top ─────────────
        // libGDX screen origin is top-left; y increases downward.
        float ndcX =  (2f * screenX / w) - 1f;
        float ndcY = 1f - (2f * screenY / h);

        // ── Frustum half-extents at unit depth ────────────────────────────────
        float tanHalfFov = MathUtils.tan(FOV_DEGREES * 0.5f * MathUtils.degreesToRadians);
        float aspect     = (float) w / h;
        float rx = ndcX * tanHalfFov * aspect;
        float ry = ndcY * tanHalfFov;

        // ── Ray direction = forward + right*rx + camUp*ry, normalised ─────────
        Vector3 dir = new Vector3(
            forward.x + right.x * rx + camUp.x * ry,
            forward.y + right.y * rx + camUp.y * ry,
            forward.z + right.z * rx + camUp.z * ry
        ).nor();

        return new Ray(new Vector3(pos), dir);
    }
}
