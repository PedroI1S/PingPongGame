package io.github.some_example_name.network;

/** Binary protocol constants for the server-authoritative game. */
public final class PacketType {

    // ── Server → Client ───────────────────────────────────────────────────────

    /** Server is waiting for the second player. No payload. */
    public static final byte WAITING   = 1;

    /**
     * Player identity assigned by the server.
     * Payload: {@code byte playerNumber} (1 or 2).
     */
    public static final byte WELCOME   = 2;

    /**
     * Full game-state snapshot at 30 Hz.
     * Payload:
     * {@code float px, py, pz} (ball position),
     * {@code float vx, vy, vz} (ball velocity),
     * {@code int p1lives, p2lives},
     * {@code byte ballVisible} (0/1),
     * {@code byte activePlayer} (0 = nobody, 1 = P1, 2 = P2).
     */
    public static final byte STATE     = 3;

    /**
     * Match concluded.
     * Payload: {@code byte winnerPlayer} (1 or 2).
     */
    public static final byte GAME_OVER = 4;

    /**
     * Sound-effect trigger.
     * Payload: {@code byte sfxType} ({@link #SFX_PADDLE} or {@link #SFX_TABLE}).
     */
    public static final byte SFX       = 5;

    // ── Client → Server ───────────────────────────────────────────────────────

    /** Initial greeting after connection. No payload. */
    public static final byte HELLO     = 10;

    /** P1 requests a serve. No payload. */
    public static final byte SERVE     = 11;

    /**
     * Player hit the ball.
     * Payload: {@code float vx, vy, vz} (return velocity in world coordinates).
     */
    public static final byte HIT       = 12;

    /** Clean disconnect notice. No payload. */
    public static final byte BYE       = 13;

    // ── SFX sub-types ─────────────────────────────────────────────────────────

    public static final byte SFX_PADDLE = 1;
    public static final byte SFX_TABLE  = 2;

    // ── Network config ────────────────────────────────────────────────────────

    /** TCP port used by the game server. */
    public static final int PORT = 7777;

    private PacketType() {}
}
