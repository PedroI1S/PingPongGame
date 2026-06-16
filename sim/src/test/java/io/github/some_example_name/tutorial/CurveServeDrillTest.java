package io.github.some_example_name.tutorial;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.some_example_name.tutorial.DrillCourseTest.*;
import static io.github.some_example_name.tutorial.SpinDrillTest.courseAt;

/** Drill 5 (pole) and drill 6 (aimed serves). */
class CurveServeDrillTest {

    @Test void beatable_curveBendsAroundThePole() {
        DrillCourse c = courseAt(DrillCourse.DrillId.CURVE, 11);
        for (int attempt = 0; attempt < 16 && c.drill() == DrillCourse.DrillId.CURVE; attempt++) {
            if (!stepUntilHittable(c)) break;
            if (c.isDemoBall()) continue;
            // ball arrives offset to one side; click the INNER edge (toward center)
            // so the curve hooks around the pole back into the centre zone.
            // Physics: travelSign=-1 for player returns, so ndx<0 gives spinY>0,
            // and Magnus = spin x vel curves the ball toward -x (left for right-side
            // ball). ndy=-0.5 (low click) adds float for hang time on the bend.
            float side = c.ball().pos.x >= 0f ? 1f : -1f;
            assertTrue(c.attemptReturn(-side * 0.85f, -0.5f));
            resolve(c);
        }
        assertEquals(DrillCourse.DrillId.SERVE, c.drill(),
            "curve drill must be beatable with inner-edge curve clicks");
    }

    @Test void straightShotThroughTheMiddleHitsThePole() {
        DrillCourse c = courseAt(DrillCourse.DrillId.CURVE, 12);
        stepUntilHittable(c);
        if (c.isDemoBall()) stepUntilHittable(c);
        // aim INTO the pole line: no sidespin, straight toward centre
        float side = c.ball().pos.x >= 0f ? 1f : -1f;
        assertTrue(c.attemptReturn(-side * 0.25f, 0f));
        int result = resolve(c);
        assertEquals(-1, result, "a straight centre shot must fail (pole or off-zone)");
    }

    @Test void beatable_servesHitShortThenDeep() {
        DrillCourse c = courseAt(DrillCourse.DrillId.SERVE, 13);
        for (int attempt = 0; attempt < 16 && !c.isComplete(); attempt++) {
            // serve drill: ball waits at the serve spot
            for (int i = 0; i < 60 * 3 && !c.isBallVisible(); i++) c.update(1f / 60f);
            assertTrue(c.isBallVisible(), "serve ball must be presented");
            ZoneRect target = c.activeZone();
            boolean shortLeft = target == TutorialGeometry.SERVE_SHORT_L;
            // high click (+ndy) = flat dip = short; low click (−ndy) = float = deep
            assertTrue(c.attemptServe(shortLeft ? -0.7f : 0.8f, shortLeft ? 0.8f : -0.8f));
            resolve(c);
        }
        assertTrue(c.isComplete(), "serve drill must complete the course");
    }

    /**
     * A serve at the right depth but down the middle (lateral miss) must be
     * coached on AIM, not click height — the depth messages would mislead.
     */
    @Test void lateralServeMissIsCoachedOnAimNotHeight() {
        DrillCourse c = courseAt(DrillCourse.DrillId.SERVE, 15);
        boolean verified = false;
        for (int attempt = 0; attempt < 12 && !verified; attempt++) {
            for (int i = 0; i < 60 * 3 && !c.isBallVisible(); i++) c.update(1f / 60f);
            assertTrue(c.isBallVisible(), "serve ball must be presented");
            // correct height for the short zone, zero lateral aim → lands mid-table
            assertTrue(c.attemptServe(0f, 0.8f));
            if (resolve(c) == -1 && c.feedback().toLowerCase().contains("right depth")) {
                assertTrue(c.feedback().contains("LEFT"),
                    "centered serve missing SHORT_L must be coached LEFT: " + c.feedback());
                verified = true;
            }
        }
        assertTrue(verified, "never produced a right-depth lateral miss to verify the coaching");
    }
}
