package io.github.some_example_name.world;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Geometry tests for the swept ball-vs-fly collision
 * ({@link MatchWorld3D#distSqPointToSegment}).
 *
 * <p>The ball is {@value MatchWorld3D#BALL_RADIUS} and a fly is
 * {@link FlyState#FLY_RADIUS}, so a hit must register whenever the centres come
 * within {@code FLY_RADIUS + BALL_RADIUS} — and must do so across the whole
 * frame-travel segment, not just at the endpoint, to stop fast balls tunnelling.</p>
 */
class FlyCollisionTest {

    private static final float REACH  = FlyState.FLY_RADIUS + MatchWorld3D.BALL_RADIUS; // 0.48
    private static final float REACH2 = REACH * REACH;
    private static final float FLY_ONLY2 = FlyState.FLY_RADIUS * FlyState.FLY_RADIUS;   // old test, 0.09

    @Test void pointOnSegmentHasZeroDistance() {
        float d2 = MatchWorld3D.distSqPointToSegment(0f, 0f, 0f,  -1f, 0f, 0f,  1f, 0f, 0f);
        assertEquals(0f, d2, 1e-6f);
    }

    @Test void perpendicularDistanceIsClampedCorrectly() {
        // Fly 0.4 to the side of a segment lying on the x-axis.
        float d2 = MatchWorld3D.distSqPointToSegment(0f, 0.4f, 0f,  -1f, 0f, 0f,  1f, 0f, 0f);
        assertEquals(0.16f, d2, 1e-5f);
    }

    @Test void beyondSegmentEndClampsToEndpoint() {
        // Fly past point B=(1,0,0): nearest point is the endpoint, dist = 1.
        float d2 = MatchWorld3D.distSqPointToSegment(2f, 0f, 0f,  -1f, 0f, 0f,  1f, 0f, 0f);
        assertEquals(1f, d2, 1e-5f);
    }

    @Test void combinedRadiusRegistersVisualOverlap() {
        // Stationary ball whose centre sits 0.4 from the fly: the sprites visibly
        // overlap (0.4 < 0.48) and must count as a hit — but would have been
        // missed by the old FLY_RADIUS-only check (0.4 > 0.3).
        float d2 = MatchWorld3D.distSqPointToSegment(0.4f, 0f, 0f,  0f, 0f, 0f,  0f, 0f, 0f);
        assertTrue(d2 < REACH2,    "0.4 overlap should register with combined radius");
        assertFalse(d2 < FLY_ONLY2, "old FLY_RADIUS-only check would have missed it");
    }

    @Test void sweptSegmentCatchesTunnellingBall() {
        // A fast ball jumps from z=-0.5 to z=+0.5 in one frame, straddling a fly
        // at z=0. Both endpoints are 0.5 away (> 0.48), so an endpoint-only test
        // misses — but the travel segment passes straight through the fly.
        float endpointA2 = 0.5f * 0.5f;
        assertFalse(endpointA2 < REACH2, "endpoint-only check would miss the tunnelling ball");

        float swept = MatchWorld3D.distSqPointToSegment(0f, 0f, 0f,
            0f, 0f, -0.5f,   0f, 0f, 0.5f);
        assertEquals(0f, swept, 1e-6f);
        assertTrue(swept < REACH2, "swept test must catch the pass-through");
    }
}
