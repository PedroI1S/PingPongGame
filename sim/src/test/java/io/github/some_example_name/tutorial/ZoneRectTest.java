package io.github.some_example_name.tutorial;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Zone containment and the swept ball-vs-pole test (2D, x/z plane). */
class ZoneRectTest {

    @Test void containsIsInclusiveInsideExclusiveOutside() {
        ZoneRect z = new ZoneRect(-1f, 1f, -5f, -3f);
        assertTrue(z.contains(0f, -4f));
        assertTrue(z.contains(-1f, -5f));   // edges count
        assertTrue(z.contains(1f, -3f));
        assertFalse(z.contains(1.01f, -4f));
        assertFalse(z.contains(0f, -2.99f));
    }

    @Test void accessorsExposeCenterAndSize() {
        ZoneRect z = new ZoneRect(0.8f, 2.6f, -5.8f, -1.2f);
        assertEquals(1.7f,  z.centerX(), 1e-5f);
        assertEquals(-3.5f, z.centerZ(), 1e-5f);
        assertEquals(1.8f,  z.width(),  1e-5f);
        assertEquals(4.6f,  z.depth(),  1e-5f);
    }

    @Test void poleSweepCatchesCrossingAndIgnoresMissesAndOverflights() {
        // pole at (0,-3), r 0.45, top at tableTop+1.4 = 3.4; ball radius 0.18
        assertTrue(TutorialGeometry.segmentHitsPole(0f, 2.5f, -2f, 0f, 2.5f, -4f),
            "straight through the pole at body height must hit");
        assertFalse(TutorialGeometry.segmentHitsPole(2f, 2.5f, -2f, 2f, 2.5f, -4f),
            "passing 2 units wide must miss");
        assertFalse(TutorialGeometry.segmentHitsPole(0f, 3.8f, -2f, 0f, 3.8f, -4f),
            "flying over the pole top must miss");
        assertTrue(TutorialGeometry.segmentHitsPole(-1f, 2.4f, -3f, 1f, 2.4f, -3f),
            "crossing laterally through the axis must hit");
    }
}
