package io.github.some_example_name.world.physics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The off-center mapping: top = topspin, bottom = backspin, sides = aim +
 * sidespin (+ corkscrew tilt for the bounce kick), pace carries from the
 * incoming ball, incoming spin transfers reversed, clamps always hold.
 * The landing property tests double as the tuning harness.
 */
class PaddleContactTest {

    private static final PhysicsConfig CFG = PhysicsConfig.createDefault();

    private static BallState incomingForP1() {
        BallState s = new BallState();
        s.pos.set(0f, 3f, 4f);
        s.vel.set(0f, -2f, 7f); // arriving toward P1 (+z)
        return s;
    }

    @Test void topClickGivesTopspinAndFlatterArc() {
        BallState top = incomingForP1();
        PaddleContact.applyReturn(top, CFG, 0f, 0.8f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        BallState bottom = incomingForP1();
        PaddleContact.applyReturn(bottom, CFG, 0f, -0.8f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        assertTrue(top.spin.x < -1f, "P1 topspin (−z travel) needs ωx < 0, got " + top.spin.x);
        assertTrue(bottom.spin.x > 1f, "backspin flips the sign");
        assertTrue(top.vel.y < bottom.vel.y, "top click must fly flatter than bottom click");
    }

    @Test void sideClickAimsCurvesAndTiltsTheBounceKick() {
        BallState s = incomingForP1();
        PaddleContact.applyReturn(s, CFG, 0.8f, 0f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        assertTrue(s.vel.x > 0.5f, "click right of centre aims right");
        assertTrue(s.spin.y < -1f, "P1 sidespin (−z travel) needs ωy < 0 to keep curving right");
        assertTrue(s.spin.z < -1f, "corkscrew tilt kicks the bounce outward");
    }

    @Test void mirroredForP2() {
        BallState s = new BallState();
        s.pos.set(0f, 3f, -4f);
        s.vel.set(0f, -2f, -7f);
        PaddleContact.applyReturn(s, CFG, 0.8f, 0.8f, 1f, 1f, false, CFG.basePaceSI, CFG.baseArcSI);
        assertTrue(s.vel.z > 0f, "P2 returns toward +z");
        assertTrue(s.spin.x > 1f, "P2 topspin (+z travel) needs ωx > 0");
        assertTrue(s.spin.y > 1f, "P2 sidespin sign mirrors");
    }

    @Test void fasterIncomingComesBackFaster() {
        BallState slow = incomingForP1();
        slow.vel.z = 6f;
        BallState fast = incomingForP1();
        fast.vel.z = 12f;
        PaddleContact.applyReturn(slow, CFG, 0f, 0f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        PaddleContact.applyReturn(fast, CFG, 0f, 0f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        float expected = CFG.paceCarry * 6f;
        assertEquals(expected, Math.abs(fast.vel.z) - Math.abs(slow.vel.z), 0.05f);
    }

    @Test void incomingSpinTransfersReversed() {
        BallState s = incomingForP1();
        s.spin.set(0f, 30f, 0f);
        PaddleContact.applyReturn(s, CFG, 0f, 0f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        assertEquals(CFG.spinTransfer * 30f, s.spin.y, 0.5f);
    }

    @Test void clampsHoldUnderStackedMultipliers() {
        BallState s = incomingForP1();
        s.vel.z = 40f;
        PaddleContact.applyReturn(s, CFG, 1f, 1f, 10f, 10f, true, CFG.basePaceSI, CFG.baseArcSI);
        assertTrue(s.vel.len() <= CFG.maxSpeedW() + 1e-3f);
        assertTrue(s.spin.len() <= CFG.maxSpinW() + 1e-3f);
    }

    /**
     * Transfer + fresh spin stacking: a max-spin incoming ball returned with a
     * hard edge click pushes the reversed transfer (−0.3·ω_in) and the fresh
     * sidespin in the SAME direction — they must accumulate (then clamp), not
     * cancel. This is the combination the bot hits constantly when returning
     * its own spun shots.
     */
    @Test void heavyIncomingSpinPlusEdgeClickAccumulatesAndClamps() {
        BallState s = incomingForP1();
        s.spin.set(0f, CFG.maxSpinW(), 0f); // heavy incoming sidespin
        PaddleContact.applyReturn(s, CFG, 0.9f, 0.9f, 1f, 1f, true, CFG.basePaceSI, CFG.baseArcSI);
        assertTrue(s.spin.len() <= CFG.maxSpinW() + 1e-3f, "clamp must hold");
        assertTrue(s.spin.y < -30f,
            "reversed transfer and fresh P1 sidespin both push ωy negative, got " + s.spin.y);
    }

    /** Property: center-ish return clicks land on the opponent's half ≥ 95%. */
    @Test void centerishReturnsLandOnOpponentHalf() {
        int total = 0, landed = 0;
        for (float ndx = -0.4f; ndx <= 0.41f; ndx += 0.2f) {
            for (float ndy = -0.4f; ndy <= 0.41f; ndy += 0.2f) {
                for (float vin = 5f; vin <= 11f; vin += 3f) {
                    for (float x0 = -2f; x0 <= 2.1f; x0 += 2f) {
                        total++;
                        BallState s = new BallState();
                        s.pos.set(x0, 2.8f, 4.5f);
                        s.vel.set(0f, -2f, vin);
                        PaddleContact.applyReturn(s, CFG, ndx, ndy, 1f, 1f, true,
                                                  CFG.basePaceSI, CFG.baseArcSI);
                        if (landsOnOpponentHalf(s)) landed++;
                    }
                }
            }
        }
        assertTrue(landed / (float) total >= 0.95f,
            "legal-landing rate too low: " + landed + "/" + total);
    }

    /** Property: serves from any clamped click offset land legally ≥ 95%. */
    @Test void servesLandOnOpponentHalf() {
        int total = 0, landed = 0;
        for (float ndx = -1f; ndx <= 1.01f; ndx += 0.4f) {
            for (float ndy = -1f; ndy <= 1.01f; ndy += 0.4f) {
                total++;
                BallState s = new BallState();
                s.pos.set(0f, PhysicsConfig.TABLE_TOP_Y + 1.2f, PhysicsConfig.TABLE_HALF_LENGTH - 0.5f);
                PaddleContact.applyReturn(s, CFG, ndx * CFG.serveControl, ndy * CFG.serveControl,
                                          1f, 1f, true, CFG.servePaceSI, CFG.serveArcSI);
                if (landsOnOpponentHalf(s)) landed++;
            }
        }
        assertTrue(landed / (float) total >= 0.95f,
            "serve legality too low: " + landed + "/" + total);
    }

    private static boolean landsOnOpponentHalf(BallState s) {
        BallPhysics phys = new BallPhysics(CFG);
        StepContacts c = new StepContacts();
        for (int i = 0; i < 60 * 4; i++) {
            phys.step(s, 1f / 60f, null, c);
            if (c.netHit) return false;
            if (c.tableBounce) return c.bounceZ < 0f;
            if (s.pos.y < 0f) return false;
        }
        return false;
    }
}
