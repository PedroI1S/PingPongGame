package io.github.some_example_name.world;

import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import io.github.some_example_name.network.PacketType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Shot-validation rules: exactly one bounce on the returner's side is required. */
class MatchWorld3DRulesTest {

    private static final float TOP = MatchWorld3D.TABLE_TOP_Y;

    /** Drive a bot serve until the ball is airborne on P1's side, pre-bounce. */
    private static MatchWorld3D incomingOnP1Side(long seed) {
        MatchWorld3D w = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(seed));
        w.setMatchMode(MatchMode.BOT); // bot opens, serving toward P1
        for (int i = 0; i < 60 * 8; i++) {
            w.update(1f / 60f);
            if (w.getPhase() == MatchWorld3D.Phase.INCOMING
                && w.getBallPos().z > 0.1f
                && w.getBallPos().y > TOP + 0.3f) {
                return w; // just crossed the net, not yet bounced
            }
        }
        return null;
    }

    private static Ray rayDownThroughBall(MatchWorld3D w) {
        Vector3 b = w.getBallPos();
        return new Ray(new Vector3(b.x, 10f, b.z), new Vector3(0f, -1f, 0f));
    }

    @Test void p1VolleyLosesThePoint() {
        MatchWorld3D w = incomingOnP1Side(7);
        assertNotNull(w, "ball should reach P1's side airborne");
        int before = w.getPlayerLives();
        w.handlePlayerClick(rayDownThroughBall(w)); // strike pre-bounce
        assertEquals(before - 1, w.getPlayerLives(), "a volley must cost P1 a life");
        assertTrue(w.hasLogEvent(), "a volley must enqueue a log event");
        int packed = w.pollLogEvent();
        assertEquals(PacketType.LOG_VOLLEY, (packed >> 8) & 0xFF);
        assertEquals(1, packed & 0xFF, "subject is P1");
    }

    @Test void p1ReturnAfterOneBounceIsLegal() {
        MatchWorld3D w = incomingOnP1Side(7);
        assertNotNull(w);
        // park the ball low over P1's side so it bounces exactly once, then click
        w.getBallPos().set(0f, TOP + 0.25f, 3f);
        w.getBallVel().set(0f, -2f, 0f);
        w.getBallSpin().setZero();
        boolean bounced = false;
        for (int i = 0; i < 60 && !bounced; i++) {
            w.update(1f / 60f);
            bounced = w.getBallVel().y > 0f && w.getBallPos().y > TOP; // rising after the bounce
        }
        assertTrue(bounced, "ball should bounce once on P1's side");
        int before = w.getPlayerLives();
        assertTrue(w.handlePlayerClick(rayDownThroughBall(w)), "a post-bounce click is a legal return");
        assertEquals(MatchWorld3D.Phase.OUTGOING, w.getPhase());
        assertEquals(before, w.getPlayerLives(), "a legal return must not cost a life");
    }

    @Test void p1DoubleBounceLosesThePoint() {
        MatchWorld3D w = incomingOnP1Side(7);
        assertNotNull(w);
        // park it low and still over P1's side: it bounces, rises, falls, bounces again
        w.getBallPos().set(0f, TOP + 0.25f, 3f);
        w.getBallVel().set(0f, -2f, 0f);
        w.getBallSpin().setZero();
        int before = w.getPlayerLives();
        for (int i = 0; i < 60 * 3; i++) {
            w.update(1f / 60f);
            if (w.getPlayerLives() < before) break;
        }
        assertEquals(before - 1, w.getPlayerLives(), "two bounces on P1's side must cost a life");
        boolean sawDouble = false;
        while (w.hasLogEvent()) if (((w.pollLogEvent() >> 8) & 0xFF) == PacketType.LOG_DOUBLE_BOUNCE) sawDouble = true;
        assertTrue(sawDouble, "double bounce must enqueue LOG_DOUBLE_BOUNCE");
    }
}
