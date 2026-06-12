package io.github.some_example_name.world.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The unit scheme: world = SI scaled by unitsPerMeter in space, slowed by
 * timeScale. Derived accessors must implement the exact conversions
 * (v_w = U·σ·v, a_w = U·σ²·a, ω_w = σ·ω, k_drag_w = k_drag/U) so that the
 * default config reproduces today's gravity feel (≈ 9.8 world units/s²).
 */
class PhysicsConfigTest {

    @Test void defaultGravityMatchesLegacyFeel() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        assertEquals(9.79f, cfg.gravityW(), 0.05f);
    }

    @Test void conversionsAreExact() {
        PhysicsConfig cfg = PhysicsConfig.createDefault();
        cfg.unitsPerMeter = 2f;
        cfg.timeScale = 0.5f;
        assertEquals(9.81f * 2f * 0.25f, cfg.gravityW(), 1e-5f);
        assertEquals(10f * 2f * 0.5f,    cfg.velW(10f),  1e-5f);
        assertEquals(100f * 0.5f,        cfg.spinW(100f),1e-5f);
        assertEquals(0.155f / 2f,        cfg.dragKW(),   1e-6f);
        assertEquals(4f / 0.5f,          cfg.spinDecayTauW(), 1e-5f);
    }

    @Test void ballStateCopies() {
        BallState a = new BallState();
        a.pos.set(1f, 2f, 3f); a.vel.set(4f, 5f, 6f); a.spin.set(7f, 8f, 9f);
        BallState b = new BallState().set(a);
        a.pos.x = 99f;
        assertEquals(1f, b.pos.x, 0f);
        assertEquals(6f, b.vel.z, 0f);
        assertEquals(8f, b.spin.y, 0f);
    }
}
