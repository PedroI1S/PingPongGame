package io.github.some_example_name.core;

import com.badlogic.gdx.math.RandomXS128;
import io.github.some_example_name.model.MatchMode;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.network.GameConnection;

/** Persists state shared between screens. */
public final class GameSession {
    private final RandomXS128 random = new RandomXS128();

    private MatchOutcome lastOutcome = MatchOutcome.NONE;

    private GameConnection gameConnection;
    private int playerNumber;
    private MatchMode matchMode = MatchMode.PVP;

    /**
     * Stop hook for an embedded game server (in-process or subprocess) that
     * this client started for VS BOT or HOST.  Run on {@link #clearMultiplayer}
     * so the server dies with the match.  {@code null} when this client joined
     * an externally-run server.
     */
    private Runnable localServerStop;

    private String remoteName = "P2";

    public MatchOutcome getLastOutcome()        { return lastOutcome; }
    public void setLastOutcome(MatchOutcome o)  { this.lastOutcome = o; }
    public RandomXS128  getRandom()             { return random; }

    public GameConnection getGameConnection() { return gameConnection; }
    public int            getPlayerNumber()   { return playerNumber; }
    public MatchMode      getMatchMode()      { return matchMode; }
    public String         getRemoteName()     { return remoteName; }

    public boolean isMultiplayer() { return gameConnection != null; }
    public boolean isBotMatch()    { return matchMode == MatchMode.BOT; }

    /**
     * Attaches a stop hook that will be invoked when the multiplayer session
     * is torn down.  Pass {@code InProcessServer::stop} or
     * {@code LocalServerProcess::stop} as a method reference.
     */
    public void attachLocalServer(Runnable stopHook) {
        this.localServerStop = stopHook;
    }

    public void setMultiplayerConnection(GameConnection conn, int playerNumber, MatchMode mode) {
        this.gameConnection = conn;
        this.playerNumber   = playerNumber;
        this.matchMode      = mode;
        this.remoteName     = mode == MatchMode.BOT
            ? "Bot"
            : (playerNumber == 1 ? "P2" : "P1");
    }

    public void setRemoteName(String name) { this.remoteName = name; }

    public void clearMultiplayer() {
        if (gameConnection != null) {
            gameConnection.close();
            gameConnection = null;
        }
        if (localServerStop != null) {
            try { localServerStop.run(); } catch (Exception ignored) {}
            localServerStop = null;
        }
        playerNumber = 0;
        matchMode = MatchMode.PVP;
    }
}
