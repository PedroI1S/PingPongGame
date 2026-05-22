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

    /**
     * Match is about to start (both players connected, or bot mode ready).
     * Payload: {@code byte matchMode} — {@code 0} = PVP, {@code 1} = BOT.
     */
    public static final byte MATCH_READY = 6;

    /** {@link io.github.some_example_name.model.MatchMode#PVP} wire value. */
    public static final byte MODE_PVP = 0;
    /** {@link io.github.some_example_name.model.MatchMode#BOT} wire value. */
    public static final byte MODE_BOT = 1;

    // ── Client → Server ───────────────────────────────────────────────────────

    /** Initial greeting after connection. No payload. */
    public static final byte HELLO     = 10;

    /**
     * Join the next available match.
     * Payload: {@code byte mode} — {@link #MODE_PVP} or {@link #MODE_BOT}.
     */
    public static final byte JOIN      = 11;

    /**
     * Screen click (server builds pick ray and runs hit test).
     * Payload: {@code int screenX, screenY, viewportWidth, viewportHeight}.
     */
    public static final byte CLICK     = 12;

    /** @deprecated Legacy serve — use {@link #CLICK}. */
    public static final byte SERVE     = 13;

    /** @deprecated Legacy hit — use {@link #CLICK}. */
    public static final byte HIT       = 14;

    /** Clean disconnect notice. No payload. */
    public static final byte BYE       = 15;

    // ── SFX sub-types ─────────────────────────────────────────────────────────

    public static final byte SFX_PADDLE = 1;
    public static final byte SFX_TABLE  = 2;

    // ── Network config ────────────────────────────────────────────────────────

    /** TCP port used by the game server. */
    public static final int PORT = 7777;

    private PacketType() {}
}
