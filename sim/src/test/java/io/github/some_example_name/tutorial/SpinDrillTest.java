package io.github.some_example_name.tutorial;

import com.badlogic.gdx.math.RandomXS128;
import io.github.some_example_name.world.physics.PhysicsConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.some_example_name.tutorial.DrillCourseTest.*;

/**
 * Drills 3-4: the technique is forced by the band — topspin's flat dip lands
 * near, backspin's float lands deep, and a flat click lands in the gap
 * between the bands and passes neither.
 */
class SpinDrillTest {

    /** Plays timing + aim with scripted ideals until the wanted drill is reached. */
    static DrillCourse courseAt(DrillCourse.DrillId target, long seed) {
        DrillCourse c = course(seed);
        int guard = 0;
        while (c.drill() != target && guard++ < 40) {
            if (!stepUntilHittable(c)) break;
            if (c.isDemoBall()) continue;
            float ndx = 0f;
            float ndy = 0f;
            switch (c.drill()) {
                case AIM -> ndx = c.activeZone().centerX() > 0f ? 0.55f : -0.55f;
                case TOPSPIN -> ndy = 0.75f;
                case BACKSPIN -> ndy = -0.8f;
                case CURVE -> {
                    float side = c.ball().pos.x >= 0f ? 1f : -1f;
                    ndx = side * 0.85f;
                    ndy = 0.1f;
                }
                default -> { }
            }
            c.attemptReturn(ndx, ndy);
            resolve(c);
        }
        assertEquals(target, c.drill(), "could not script my way to " + target);
        return c;
    }

    @Test void demoBallPlaysFirstInSlowMotionAndIsUnclickable() {
        DrillCourse c = courseAt(DrillCourse.DrillId.TOPSPIN, 7);
        boolean demoSeen = false;
        for (int i = 0; i < 60 * 10 && !demoSeen; i++) {
            c.update(1f / 60f);
            demoSeen = c.isDemoBall();
        }
        assertTrue(demoSeen, "first topspin feed must be a demo");
        assertFalse(c.attemptReturn(0f, 0.7f), "demo balls are not returnable");
        // demo resolves on its own, then a normal feed comes
        assertTrue(stepUntilHittable(c));
        assertFalse(c.isDemoBall());
    }

    @Test void beatable_topspinLandsInTheNearBand() {
        DrillCourse c = courseAt(DrillCourse.DrillId.TOPSPIN, 8);
        int successes = 0;
        for (int attempt = 0; attempt < 14 && c.drill() == DrillCourse.DrillId.TOPSPIN; attempt++) {
            if (!stepUntilHittable(c)) break;
            if (c.isDemoBall()) continue;
            assertTrue(c.attemptReturn(0f, 0.75f));
            if (resolve(c) > 0) successes++;
        }
        assertEquals(DrillCourse.DrillId.BACKSPIN, c.drill(),
            "topspin drill must be beatable with top clicks (successes=" + successes + ")");
    }

    @Test void flatClickPassesNeitherSpinDrill() {
        DrillCourse c = courseAt(DrillCourse.DrillId.TOPSPIN, 9);
        // skip the demo
        stepUntilHittable(c);
        if (c.isDemoBall()) stepUntilHittable(c);
        assertTrue(c.attemptReturn(0f, 0f));
        assertEquals(-1, resolve(c), "a flat click must not pass the topspin drill");
    }

    @Test void beatable_backspinFloatsIntoTheDeepBand() {
        DrillCourse c = courseAt(DrillCourse.DrillId.BACKSPIN, 10);
        for (int attempt = 0; attempt < 14 && c.drill() == DrillCourse.DrillId.BACKSPIN; attempt++) {
            if (!stepUntilHittable(c)) break;
            if (c.isDemoBall()) continue;
            assertTrue(c.attemptReturn(0f, -0.8f));
            resolve(c);
        }
        assertEquals(DrillCourse.DrillId.CURVE, c.drill(),
            "backspin drill must be beatable with bottom clicks");
    }

    /**
     * Feedback must name the side that was actually missed. The reachable
     * wrong-band case is backspin: a moderate bottom click is spun but doesn't
     * float far enough, landing shallow of the deep band — the message must say
     * "too short", never "too deep". (For topspin the symmetric short-miss is
     * physically swallowed by the net — verified empirically — so the
     * evaluateLanding direction branch is the honest guard for both.)
     */
    @Test void spunButShallowBackspinIsCalledShortNotDeep() {
        DrillCourse c = courseAt(DrillCourse.DrillId.BACKSPIN, 14);
        boolean verified = false;
        for (int attempt = 0; attempt < 20 && !verified
                && c.drill() == DrillCourse.DrillId.BACKSPIN; attempt++) {
            if (!stepUntilHittable(c)) break;
            if (c.isDemoBall()) continue;
            if (!c.attemptReturn(0f, -0.35f)) continue; // spun, but not enough float
            int r = 0;
            float zAtResolve = 0f;
            for (int i = 0; i < 60 * 8 && r == 0; i++) {
                c.update(1f / 60f);
                zAtResolve = c.ball().pos.z;
                if (c.consumeSuccessEvent()) r = 1;
                if (c.consumeFailEvent()) r = -1;
            }
            if (r == -1 && zAtResolve > TutorialGeometry.BACKSPIN_DEEP.z1 && zAtResolve < 0f) {
                String fb = c.feedback().toLowerCase();
                assertTrue(fb.contains("too short"),
                    "shallow landing should be named short: " + c.feedback());
                assertFalse(fb.contains("too deep"),
                    "shallow landing must not be called deep: " + c.feedback());
                verified = true;
            }
        }
        assertTrue(verified,
            "never produced a spun shallow-of-band landing to verify the feedback direction");
    }
}
