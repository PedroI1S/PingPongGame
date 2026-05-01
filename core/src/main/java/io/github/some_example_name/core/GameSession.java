package io.github.some_example_name.core;

import com.badlogic.gdx.math.RandomXS128;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.network.GameConnection;
import io.github.some_example_name.server.GameServer;

/** Persists state shared between screens. */
public final class GameSession {
    private final RandomXS128 random = new RandomXS128();

    private MatchOutcome lastOutcome = MatchOutcome.NONE;

    // ── Multiplayer session — null / 0 in single-player ───────────────────────

    private GameConnection gameConnection;
    private int            playerNumber;  // 1 = P1, 2 = P2, 0 = none
    private GameServer     gameServer;    // non-null only on machine that hosts

    private String localName  = "P1";
    private String remoteName = "P2";

    // ── Match outcome ─────────────────────────────────────────────────────────

    public MatchOutcome getLastOutcome()        { return lastOutcome; }
    public void setLastOutcome(MatchOutcome o)  { this.lastOutcome = o; }
    public RandomXS128  getRandom()             { return random; }

    // ── Multiplayer accessors ─────────────────────────────────────────────────

    public GameConnection getGameConnection() { return gameConnection; }
    public int            getPlayerNumber()   { return playerNumber; }
    public GameServer     getGameServer()     { return gameServer; }
    public String         getLocalName()      { return localName; }
    public String         getRemoteName()     { return remoteName; }

    public boolean isMultiplayer() { return gameConnection != null; }
    public boolean isHost()        { return playerNumber == 1; }

    // ── Session setup / teardown ──────────────────────────────────────────────

    /**
     * Stores the live connection and assigned role after the server's
     * {@code WELCOME} packet arrives.
     *
     * @param conn         live binary connection to the server
     * @param playerNumber 1 (P1) or 2 (P2)
     * @param server       local {@link GameServer} if this client also hosts;
     *                     {@code null} for the joining player
     */
    public void setMultiplayerConnection(GameConnection conn, int playerNumber, GameServer server) {
        this.gameConnection = conn;
        this.playerNumber   = playerNumber;
        this.gameServer     = server;
        this.localName      = playerNumber == 1 ? "P1" : "P2";
        this.remoteName     = playerNumber == 1 ? "P2" : "P1";
    }

    public void setLocalName(String name)  { this.localName  = name; }
    public void setRemoteName(String name) { this.remoteName = name; }

    /** Closes the connection and stops the local server (if any). Idempotent. */
    public void clearMultiplayer() {
        if (gameConnection != null) { gameConnection.close(); gameConnection = null; }
        if (gameServer     != null) { gameServer.stop();      gameServer     = null; }
        playerNumber = 0;
    }

    // ── Match config ──────────────────────────────────────────────────────────

    /**
     * Returns the default match configuration. In-match item pickups (planned)
     * will mutate this directly during play.
     */
    public MatchConfig buildMatchConfig() {
        return MatchConfig.createDefault();
    }
}
