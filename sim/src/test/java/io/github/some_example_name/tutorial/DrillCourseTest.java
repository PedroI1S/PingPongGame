package io.github.some_example_name.tutorial;

import com.badlogic.gdx.math.RandomXS128;
import io.github.some_example_name.world.physics.PhysicsConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Timing + aim drills, driven headlessly. Helpers here are reused by the
 * later drill tests: stepUntilHittable simulates the feed flight, then the
 * scripted "ideal click" goes through the same attemptReturn the screen uses.
 */
class DrillCourseTest {

    static DrillCourse course(long seed) {
        return new DrillCourse(PhysicsConfig.createDefault(), new RandomXS128(seed));
    }

    /** Steps until the live ball is in the timing strip on P1's side. */
    static boolean stepUntilHittable(DrillCourse c) {
        for (int i = 0; i < 60 * 8; i++) {
            c.update(1f / 60f);
            if (c.isBallVisible() && !c.isDemoBall()
                && c.ball().pos.z > TutorialGeometry.CONTACT_MIN_Z
                && c.ball().pos.z < 6.5f) {
                return true;
            }
        }
        return false;
    }

    /** Steps until the current attempt resolves (success or fail event). */
    static int resolve(DrillCourse c) {
        for (int i = 0; i < 60 * 8; i++) {
            c.update(1f / 60f);
            if (c.consumeSuccessEvent()) return +1;
            if (c.consumeFailEvent())    return -1;
        }
        return 0;
    }

    @Test void courseStartsOnTimingWithAFeedComing() {
        DrillCourse c = course(1);
        assertEquals(DrillCourse.DrillId.TIMING, c.drill());
        assertEquals(1, c.drillNumber());
        assertEquals(6, DrillCourse.drillCount());
        assertTrue(stepUntilHittable(c), "a feed must arrive on P1's side");
    }

    @Test void beatable_timingDrillPassesWithCenterClicksInTheStrip() {
        DrillCourse c = course(2);
        int successes = 0;
        for (int attempt = 0; attempt < 12 && successes < c.required(); attempt++) {
            assertTrue(stepUntilHittable(c));
            assertTrue(c.attemptReturn(0f, 0f), "center click in the strip must connect");
            if (resolve(c) > 0) successes++;
        }
        assertEquals(c.required(), successes, "timing drill must be beatable with center clicks");
        assertEquals(DrillCourse.DrillId.AIM, c.drill(), "course advances to AIM");
    }

    @Test void earlyClickFailsWithTheTimingLesson() {
        DrillCourse c = course(3);
        // catch the ball BEFORE the strip (z < 3.5, but past the net)
        boolean early = false;
        for (int i = 0; i < 60 * 8 && !early; i++) {
            c.update(1f / 60f);
            early = c.isBallVisible() && c.ball().pos.z > 0.5f
                 && c.ball().pos.z < 2.5f;
        }
        assertTrue(early, "must find an early-click window");
        assertTrue(c.attemptReturn(0f, 0f));
        assertEquals(-1, resolve(c), "early contact must fail the attempt");
        assertTrue(c.feedback().toLowerCase().contains("early"),
            "feedback should teach the timing lesson, got: " + c.feedback());
        assertEquals(0, c.progress());
    }

    @Test void beatable_aimDrillHitsAlternatingZones() {
        DrillCourse c = course(4);
        // fast-forward through TIMING
        int guard = 0;
        while (c.drill() == DrillCourse.DrillId.TIMING && guard++ < 12) {
            assertTrue(stepUntilHittable(c));
            c.attemptReturn(0f, 0f);
            resolve(c);
        }
        assertEquals(DrillCourse.DrillId.AIM, c.drill());
        int successes = 0;
        for (int attempt = 0; attempt < 16 && !c.drill().equals(DrillCourse.DrillId.TOPSPIN); attempt++) {
            assertTrue(stepUntilHittable(c));
            ZoneRect target = c.activeZone();
            assertNotNull(target, "aim drill always has an active zone");
            float ndx = target.centerX() > 0f ? 0.55f : -0.55f;
            assertTrue(c.attemptReturn(ndx, 0f));
            if (resolve(c) > 0) successes++;
        }
        assertEquals(DrillCourse.DrillId.TOPSPIN, c.drill(),
            "aim drill must be beatable with edge-aimed clicks (got " + successes + " hits)");
    }

    @Test void missedBallRefeedsWithoutProgress() {
        DrillCourse c = course(5);
        assertTrue(stepUntilHittable(c));
        // never click: ball passes, fail event fires, a new feed comes
        assertEquals(-1, resolve(c));
        assertEquals(0, c.progress());
        assertTrue(stepUntilHittable(c), "a fresh ball must be fed");
    }

    /** Re-entrancy guard: once the ball is returned, further hits are rejected. */
    @Test void secondClickDuringAReturnIsRejected() {
        DrillCourse c = course(6);
        assertTrue(stepUntilHittable(c));
        assertTrue(c.attemptReturn(0f, 0f));
        assertTrue(c.consumePaddleHitEvent());
        assertFalse(c.attemptReturn(0f, 0f), "double-hit must be rejected");
        assertFalse(c.consumePaddleHitEvent(), "no second paddle event may fire");
    }
}
