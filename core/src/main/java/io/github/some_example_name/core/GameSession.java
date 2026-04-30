package io.github.some_example_name.core;

import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Array;
import io.github.some_example_name.model.ArenaSide;
import io.github.some_example_name.model.ItemCatalog;
import io.github.some_example_name.model.ItemDefinition;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.network.GameConnection;
import io.github.some_example_name.server.GameServer;

/** Persists menu-to-match state between screens. */
public final class GameSession {
    private final RandomXS128 random = new RandomXS128();
    private final ItemCatalog itemCatalog = new ItemCatalog();
    private final Array<ItemDefinition> offeredItems = new Array<>();

    private ItemDefinition playerItem;
    private ItemDefinition botItem;
    private MatchOutcome lastOutcome = MatchOutcome.NONE;

    // ── Multiplayer session — null / 0 in single-player ───────────────────────

    /** Live binary connection to the authoritative game server. */
    private GameConnection gameConnection;

    /**
     * This client's player number (1 = P1 / server host side, 2 = P2 / joining side).
     * 0 when not in a multiplayer session.
     */
    private int playerNumber;

    /**
     * The local {@link GameServer} instance; non-null only on the machine that
     * started the server (player 1).  {@code null} on the joining client.
     */
    private GameServer gameServer;

    private String localName  = "P1";
    private String remoteName = "P2";

    // ── Loadout ───────────────────────────────────────────────────────────────

    public void rollNewLoadout() {
        offeredItems.clear();
        for (ItemDefinition item : itemCatalog.drawOptions(random)) {
            offeredItems.add(item);
        }
        playerItem = null;
        botItem = itemCatalog.drawRandom(random);
    }

    public Array<ItemDefinition> getOfferedItems() { return offeredItems; }

    public void selectPlayerItem(int index) {
        if (index >= 0 && index < offeredItems.size) {
            playerItem = offeredItems.get(index);
        }
    }

    public ItemDefinition getPlayerItem()   { return playerItem; }
    public ItemDefinition getBotItem()      { return botItem; }
    public MatchOutcome   getLastOutcome()  { return lastOutcome; }
    public void setLastOutcome(MatchOutcome o) { this.lastOutcome = o; }
    public RandomXS128    getRandom()       { return random; }

    // ── Multiplayer accessors ─────────────────────────────────────────────────

    public GameConnection getGameConnection() { return gameConnection; }
    public int            getPlayerNumber()   { return playerNumber; }
    public GameServer     getGameServer()     { return gameServer; }
    public String         getLocalName()      { return localName; }
    public String         getRemoteName()     { return remoteName; }

    public boolean isMultiplayer() { return gameConnection != null; }

    /** P1 is the player who started the server on their machine. */
    public boolean isHost() { return playerNumber == 1; }

    // ── Session setup / teardown ──────────────────────────────────────────────

    /**
     * Stores the connection and role after the server sends {@code WELCOME}.
     *
     * @param conn         live binary connection to the server
     * @param playerNumber 1 (P1/host) or 2 (P2/join)
     * @param server       local {@link GameServer} if this client is also the host,
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

    /**
     * Closes the connection and stops the local server (if any).
     * Safe to call multiple times.
     */
    public void clearMultiplayer() {
        if (gameConnection != null) { gameConnection.close(); gameConnection = null; }
        if (gameServer     != null) { gameServer.stop();      gameServer     = null; }
        playerNumber = 0;
    }

    // ── Match config ──────────────────────────────────────────────────────────

    public MatchConfig buildMatchConfig() {
        MatchConfig config = MatchConfig.createDefault();
        if (playerItem != null) playerItem.apply(config, ArenaSide.PLAYER);
        if (botItem    != null) botItem.apply(config, ArenaSide.BOT);
        return config;
    }
}
