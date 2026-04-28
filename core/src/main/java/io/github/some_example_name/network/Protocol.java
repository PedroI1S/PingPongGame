package io.github.some_example_name.network;

/** Wire protocol constants — every message is a single text line. */
public final class Protocol {
    public static final int DEFAULT_PORT = 7777;

    /** {@code HELLO <name>} — sent immediately after socket connect. */
    public static final String HELLO = "HELLO";
    /** {@code READY 0|1} — toggle ready state in the lobby. */
    public static final String READY = "READY";
    /** {@code START} — host commands both peers to enter the match. */
    public static final String START = "START";
    /** {@code HIT <vx> <vy> <vz>} — client sends computed return-velocity impulse. */
    public static final String HIT = "HIT";
    /**
     * {@code STATE <bx> <by> <bz> <vx> <vy> <vz> <p1lives> <p2lives> <visible> <clientCanHit>}
     * — host snapshots authoritative physics state at ~30 Hz.
     */
    public static final String STATE = "STATE";
    /** {@code GAME_OVER <1|2>} — host signals match end; 1 = host wins, 2 = client wins. */
    public static final String GAME_OVER = "GAME_OVER";
    /** {@code SFX <P|T>} — relay a sound trigger to the remote (P = paddle, T = table). */
    public static final String SFX = "SFX";
    /** {@code BYE} — graceful disconnect. */
    public static final String BYE = "BYE";

    private Protocol() {}
}
