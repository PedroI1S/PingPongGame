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
    /** {@code CLICK <x> <y> <z>} — client sends pick-ray-derived hit point. */
    public static final String CLICK = "CLICK";
    /** {@code STATE <x> <y> <z> <visible>} — host snapshots ball position. */
    public static final String STATE = "STATE";
    /** {@code BYE} — graceful disconnect. */
    public static final String BYE = "BYE";

    private Protocol() {}
}
