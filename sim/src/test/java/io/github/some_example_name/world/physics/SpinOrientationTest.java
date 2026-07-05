package io.github.some_example_name.world.physics;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link SpinOrientation}: angular velocity → orientation, world axes. */
class SpinOrientationTest {

    private static final float EPS = 1e-4f;

    @Test
    void zeroSpinLeavesOrientationUntouched() {
        Quaternion q = new Quaternion(0.1f, 0.2f, 0.3f, 0.9f).nor();
        Quaternion before = new Quaternion(q);
        SpinOrientation.integrate(q, new Vector3(0f, 0f, 0f), 1f / 60f);
        assertEquals(before.x, q.x, EPS);
        assertEquals(before.y, q.y, EPS);
        assertEquals(before.z, q.z, EPS);
        assertEquals(before.w, q.w, EPS);
    }

    @Test
    void spinAboutYRotatesByOmegaTimesDt() {
        // ω = π rad/s about +Y for 1 s → 180°: +X maps to −X
        Quaternion q = new Quaternion();
        SpinOrientation.integrate(q, new Vector3(0f, MathUtils.PI, 0f), 1f);
        Vector3 v = q.transform(new Vector3(1f, 0f, 0f));
        assertEquals(-1f, v.x, EPS);
        assertEquals(0f, v.y, EPS);
        assertEquals(0f, v.z, EPS);
    }

    @Test
    void topspinAxisFollowsRightHandRule() {
        // ω about +X for 90°: +Z maps to −Y (Rx: y' = −sinθ·z, z' = cosθ·z)
        Quaternion q = new Quaternion();
        SpinOrientation.integrate(q, new Vector3(MathUtils.HALF_PI, 0f, 0f), 1f);
        Vector3 v = q.transform(new Vector3(0f, 0f, 1f));
        assertEquals(0f, v.x, EPS);
        assertEquals(-1f, v.y, EPS);
        assertEquals(0f, v.z, EPS);
    }

    @Test
    void manySmallStepsMatchOneBigStepAndStayNormalized() {
        Vector3 spin = new Vector3(40f, 25f, -60f); // realistic world rad/s
        Quaternion many = new Quaternion();
        for (int i = 0; i < 240; i++) {
            SpinOrientation.integrate(many, spin, 1f / 240f);
        }
        Quaternion one = new Quaternion();
        SpinOrientation.integrate(one, spin, 1f);
        // constant ω ⇒ integration is exact regardless of step size
        Vector3 a = many.transform(new Vector3(1f, 2f, 3f));
        Vector3 b = one.transform(new Vector3(1f, 2f, 3f));
        assertEquals(b.x, a.x, 1e-3f);
        assertEquals(b.y, a.y, 1e-3f);
        assertEquals(b.z, a.z, 1e-3f);
        assertTrue(Math.abs(many.len() - 1f) < EPS, "quaternion must stay normalized");
    }
}
