package io.github.some_example_name.world.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SLOW_MO (×0.7) and FAST_SERVE (×1.3) enter PaddleContact as the pace
 * multiplier, which scales the whole outgoing pace including the carry term —
 * the items must stay clearly felt now that returns carry incoming speed.
 */
class ItemPaceAuditTest {

    private static float returnSpeed(float paceMultiplier, float incomingVz) {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BallState s = new BallState();
        s.pos.set(0f, 3f, 4f);
        s.vel.set(0f, -2f, incomingVz);
        PaddleContact.applyReturn(s, cfg, 0f, 0f, 1f, paceMultiplier, true,
                                  cfg.basePaceSI, cfg.baseArcSI);
        return Math.abs(s.vel.z);
    }

    @Test void slowMoSlowsTheWholeReturn() {
        assertTrue(returnSpeed(0.7f, 9f) < returnSpeed(1f, 9f) * 0.75f,
            "slow-mo must cut at least 25% even on fast incoming balls");
    }

    @Test void fastServeSpeedsTheWholeReturn() {
        assertTrue(returnSpeed(1.3f, 9f) > returnSpeed(1f, 9f) * 1.25f);
    }

    @Test void itemMultipliersCannotEscapeTheClamp() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        BallState s = new BallState();
        s.pos.set(0f, 3f, 4f);
        s.vel.set(0f, -2f, 30f);
        PaddleContact.applyReturn(s, cfg, 1f, 1f, 1.6f, 1.3f * 1.6f, true,
                                  cfg.basePaceSI, cfg.baseArcSI);
        assertTrue(s.vel.len() <= cfg.maxSpeedW() + 1e-3f);
    }
}
