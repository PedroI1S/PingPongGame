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
     * {@code float sx, sy, sz} (ball spin, world angular velocity),
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

    /**
     * A round has concluded (ball out of play).
     * Payload: {@code byte winnerPlayer} (1 or 2),
     * {@code byte p1Wins}, {@code byte p2Wins} (running best-of-3 tally).
     */
    public static final byte ROUND_OVER = 20;

    /**
     * Items dealt to a player at the start of an item phase.
     * Payload: {@code byte playerNumber} (1 or 2), {@code byte count},
     * then {@code count} × {@code byte itemId}.
     */
    public static final byte ITEM_DEALT = 21;

    /**
     * A player has used an item.
     * Payload: {@code byte playerNumber} (1 or 2), {@code byte itemId}.
     */
    public static final byte ITEM_USED  = 22;

    /**
     * Item effect has resolved and is ready for next round.
     * Payload: {@code byte playerNumber} (1 or 2).
     */
    public static final byte ITEM_READY = 23;

    /**
     * A loggable gameplay event for the client-side event log.
     * Payload: {@code byte eventCode} (one of the LOG_* codes below),
     * {@code byte subjectPlayer} (1 or 2 — the player the event concerns).
     */
    public static final byte LOG_EVENT  = 27;

    // ── LOG_EVENT codes ───────────────────────────────────────────────────────
    public static final byte LOG_VOLLEY         = 1; // struck the ball before it bounced on their side
    public static final byte LOG_DOUBLE_BOUNCE  = 2; // let it bounce twice on their side
    public static final byte LOG_OUT_OF_BOUNDS  = 3; // a struck shot left the play area / own half
    public static final byte LOG_MISS           = 4; // failed to return in time / let it pass
    public static final byte LOG_TIMEOUT        = 5; // serve clock expired
    public static final byte LOG_FLY_HIT        = 6; // ball hit an unswatted fly
    public static final byte LOG_COIN_FLIP_LOSS = 7; // lost the coin flip

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

    // 13 (SERVE) and 14 (HIT) were the legacy pre-CLICK protocol — retired.

    /** Clean disconnect notice. No payload. */
    public static final byte BYE       = 15;

    /**
     * Client requests to use an item.
     * Payload: {@code byte itemId}.
     */
    public static final byte USE_ITEM   = 24;

    /**
     * Flies spawned on a player's side as an item effect.
     * Payload: {@code byte owner} (player 1/2 whose side the flies are on),
     * {@code byte count}, then {@code count} × ({@code float x}, {@code float z}).
     * Positions are absolute table coordinates; y is fixed, so it is not sent.
     */
    public static final byte FLY_SPAWN  = 25;

    /**
     * A fly has been swatted or hit by the ball.
     * Payload: {@code byte owner} (player 1/2 whose fly list is addressed),
     * {@code byte flyIndex} (index into that player's fly array).
     */
    public static final byte FLY_KILLED = 26;

    // ── SFX sub-types ─────────────────────────────────────────────────────────

    public static final byte SFX_PADDLE = 1;
    public static final byte SFX_TABLE  = 2;

    // ── Network config ────────────────────────────────────────────────────────

    /** TCP port used by the game server. */
    public static final int PORT = 7777;

    private PacketType() {}
}
