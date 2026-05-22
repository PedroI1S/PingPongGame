package io.github.some_example_name.server;

import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import io.github.some_example_name.model.ItemType;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.network.GameConnection;
import io.github.some_example_name.network.PacketType;
import io.github.some_example_name.world.MatchWorld3D;
import io.github.some_example_name.world.MatchWorld3D.Phase;
import io.github.some_example_name.world.ServerPickRay;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.LockSupport;

/**
 * Persistent authoritative server — stays running and hosts many matches.
 *
 * <p>Clients connect, send {@link PacketType#JOIN} with a mode, then play by
 * sending {@link PacketType#CLICK} (screen coordinates).  The server runs all
 * physics and broadcasts {@link PacketType#STATE} snapshots for rendering.</p>
 */
public final class GameServer {

    public static final String LISTENING_LOG_MARKER = "[GameServer] Listening on";

    private static final float TICK_HZ  = 60f;
    private static final float TICK_DT  = 1f / TICK_HZ;
    private static final float STATE_HZ = 30f;
    private static final float STATE_DT = 1f / STATE_HZ;
    private static final long  TICK_NS  = (long) (TICK_DT * 1_000_000_000L);

    private volatile boolean shutdown;
    private ServerSocket serverSocket;

    private GameConnection p1;
    private GameConnection p2;
    private volatile MatchWorld3D world;
    private volatile boolean matchRunning;

    private final LinkedBlockingQueue<Runnable> actions = new LinkedBlockingQueue<>();

    public void run(ServerOptions options) {
        run(options, null);
    }

    /**
     * Same as {@link #run(ServerOptions)} but fires {@code onListening} the
     * moment {@code bind()} returns successfully.  Used by the in-process
     * server (VS BOT / local HOST) so the GL thread knows it can connect.
     */
    public void run(ServerOptions options, Runnable onListening) {
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(options.bindAddress, options.port));
            System.out.println(LISTENING_LOG_MARKER + " " + options.bindAddress
                + ":" + options.port + " (persistent — send JOIN to play)");
            if (onListening != null) onListening.run();

            while (!shutdown) {
                try {
                    runBestOf3();
                } catch (java.net.SocketException stopped) {
                    // Server socket was closed via shutdown() — exit cleanly.
                    if (!shutdown) {
                        System.err.println("[GameServer] Socket error: " + stopped);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            if (!shutdown) {
                System.err.println("[GameServer] Fatal: " + e);
                e.printStackTrace(System.err);
            }
        } finally {
            closeListenSocket();
        }
    }

    public void shutdown() {
        shutdown = true;
        matchRunning = false;
        closeListenSocket();
        endMatchConnections();
    }

    private void runBestOf3() throws Exception {
        MatchLobby lobby = new MatchLobby();
        System.out.println("[GameServer] Waiting for JOIN...");
        serverSocket.setSoTimeout(200);
        try {
            while (!lobby.isReady() && !shutdown) {
                try {
                    Socket socket = serverSocket.accept();
                    GameConnection[] connRef = new GameConnection[1];
                    connRef[0] = GameConnection.wrap(socket, Runnable::run,
                        new LobbyListener(lobby, connRef));
                } catch (java.net.SocketTimeoutException ignored) {}
            }
        } finally {
            serverSocket.setSoTimeout(0);
        }
        if (shutdown) return;

        p1 = lobby.p1;
        p2 = lobby.p2;
        MatchMode mode = lobby.mode;
        byte modeWire = mode == MatchMode.BOT ? PacketType.MODE_BOT : PacketType.MODE_PVP;
        p1.sendMatchReady(modeWire);
        if (p2 != null) p2.sendMatchReady(modeWire);

        p1.setListener(new MatchPlayerListener(1));
        if (p2 != null) p2.setListener(new MatchPlayerListener(2));

        int p1Wins = 0, p2Wins = 0;
        while (p1Wins < 2 && p2Wins < 2 && !shutdown) {
            int roundWinner = runOneRound(mode);
            if (roundWinner == 1) p1Wins++;
            else p2Wins++;
            sendRoundOverToAll(roundWinner, p1Wins, p2Wins);
            System.out.println("[GameServer] Round over — P" + roundWinner
                + " wins  (" + p1Wins + "-" + p2Wins + ")");
            if (p1Wins < 2 && p2Wins < 2) {
                LockSupport.parkNanos(2_000_000_000L);
            }
        }

        int matchWinner = p1Wins >= 2 ? 1 : 2;
        sendGameOverToAll(matchWinner);
        System.out.println("[GameServer] Match over — winner P" + matchWinner);

        endMatchConnections();
        world = null;
        p1 = null;
        p2 = null;
        System.out.println("[GameServer] Waiting for next JOIN...");
    }

    private int runOneRound(MatchMode mode) throws Exception {
        MatchWorld3D w = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128());
        w.setMatchMode(mode);
        world = w;
        matchRunning = true;

        long lastNs = System.nanoTime();
        float stateAcc = 0f;
        Phase prevPhase = w.getPhase();

        while (matchRunning && !w.isMatchOver() && !shutdown) {
            long now = System.nanoTime();
            float delta = Math.min((now - lastNs) / 1_000_000_000f, 0.1f);
            lastNs = now;

            Runnable action;
            while ((action = actions.poll()) != null) action.run();

            w.update(delta);

            Phase curPhase = w.getPhase();
            if (prevPhase != Phase.ITEM_PHASE && curPhase == Phase.ITEM_PHASE) {
                broadcastItemDealt(w);
            }
            prevPhase = curPhase;

            if (w.consumeItemUsedEvent()) {
                broadcastItemUsed(w.getItemUsedPlayer(), w.getItemUsedId());
            }
            if (w.consumeFlySpawnEvent()) {
                broadcastFlySpawn(w.getFlySpawnXs(), w.getFlySpawnZs());
            }
            int killed = w.consumeFlyKilledIndex();
            if (killed >= 0) broadcastFlyKilled(killed);

            if (w.consumePaddleHitEvent())  sendSfxToAll(PacketType.SFX_PADDLE);
            if (w.consumeTableBounceEvent()) sendSfxToAll(PacketType.SFX_TABLE);

            stateAcc += delta;
            if (stateAcc >= STATE_DT) {
                stateAcc -= STATE_DT;
                broadcastState(w);
            }

            long sleepNs = TICK_NS - (System.nanoTime() - now);
            if (sleepNs > 100_000L) LockSupport.parkNanos(sleepNs);
        }

        world = null;
        return w.getOutcome() == MatchOutcome.PLAYER_WIN ? 1 : 2;
    }

    private void endMatchConnections() {
        matchRunning = false;
        if (p1 != null) { p1.close(); }
        if (p2 != null) { p2.close(); }
    }

    private void closeListenSocket() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {}
        serverSocket = null;
    }

    private void broadcastState(MatchWorld3D w) {
        GameConnection c1 = p1, c2 = p2;   // capture before shutdown() can null them
        Vector3 pos = w.getBallPos();
        Vector3 vel = w.getBallVel();
        boolean vis = w.isBallVisible();
        int ap = w.getActivePlayer();
        int p1l = w.getPlayerLives();
        int p2l = w.getP2Lives();

        if (c1 != null) {
            c1.sendState(pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, p1l, p2l, vis, ap);
        }
        if (c2 != null) {
            c2.sendState(pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, p1l, p2l, vis, ap);
        }
    }

    private void sendSfxToAll(int sfxType) {
        GameConnection c1 = p1, c2 = p2;
        if (c1 != null) c1.sendSfx(sfxType);
        if (c2 != null) c2.sendSfx(sfxType);
    }

    private void sendGameOverToAll(int winner) {
        GameConnection c1 = p1, c2 = p2;
        if (c1 != null) c1.sendGameOver(winner);
        if (c2 != null) c2.sendGameOver(winner);
    }

    private void broadcastItemDealt(MatchWorld3D w) {
        GameConnection c1 = p1, c2 = p2;
        if (c1 != null) c1.sendItemDealt(1, w.getLastDealtItems(1));
        if (c1 != null) c1.sendItemDealt(2, w.getLastDealtItems(2));
        if (c2 != null) c2.sendItemDealt(1, w.getLastDealtItems(1));
        if (c2 != null) c2.sendItemDealt(2, w.getLastDealtItems(2));
    }

    private void broadcastItemUsed(int playerNumber, byte itemId) {
        GameConnection c1 = p1, c2 = p2;
        if (c1 != null) c1.sendItemUsed(playerNumber, itemId);
        if (c2 != null) c2.sendItemUsed(playerNumber, itemId);
    }

    private void broadcastFlySpawn(float[] xs, float[] zs) {
        GameConnection c1 = p1, c2 = p2;
        if (c1 != null) c1.sendFlySpawn(xs, zs);
        if (c2 != null) c2.sendFlySpawn(xs, zs);
    }

    private void broadcastFlyKilled(int idx) {
        GameConnection c1 = p1, c2 = p2;
        if (c1 != null) c1.sendFlyKilled(idx);
        if (c2 != null) c2.sendFlyKilled(idx);
    }

    private void sendRoundOverToAll(int winner, int p1Wins, int p2Wins) {
        GameConnection c1 = p1, c2 = p2;
        if (c1 != null) c1.sendRoundOver(winner, p1Wins, p2Wins);
        if (c2 != null) c2.sendRoundOver(winner, p1Wins, p2Wins);
    }

    // ── Lobby ─────────────────────────────────────────────────────────────────

    private static final class MatchLobby {
        MatchMode mode;
        boolean modeLocked;
        GameConnection p1;
        GameConnection p2;

        synchronized boolean onJoin(GameConnection conn, MatchMode requested) {
            if (modeLocked && mode != requested) {
                System.out.println("[GameServer] JOIN rejected — mode mismatch");
                conn.close();
                return false;
            }
            if (!modeLocked) {
                mode = requested;
                modeLocked = true;
            }

            if (p1 == null) {
                p1 = conn;
                conn.sendWelcome(1);
                if (mode == MatchMode.BOT) {
                    return true;
                }
                conn.sendWaiting();
                return false;
            }
            if (mode == MatchMode.PVP && p2 == null) {
                p2 = conn;
                conn.sendWelcome(2);
                return true;
            }
            conn.close();
            return false;
        }

        synchronized boolean isReady() {
            if (mode == MatchMode.BOT) return p1 != null;
            return p1 != null && p2 != null;
        }
    }

    private final class LobbyListener implements GameConnection.Listener {
        private final MatchLobby lobby;
        private final GameConnection[] self;

        LobbyListener(MatchLobby lobby, GameConnection[] self) {
            this.lobby = lobby;
            this.self = self;
        }

        @Override
        public void onJoin(int matchModeWire) {
            MatchMode m = matchModeWire == PacketType.MODE_BOT ? MatchMode.BOT : MatchMode.PVP;
            lobby.onJoin(self[0], m);
        }
    }

    private final class MatchPlayerListener implements GameConnection.Listener {
        private final int playerNumber;

        MatchPlayerListener(int playerNumber) {
            this.playerNumber = playerNumber;
        }

        @Override
        public void onClick(int screenX, int screenY, int viewportW, int viewportH) {
            MatchWorld3D w = world;
            if (w == null || !matchRunning) return;
            actions.offer(() -> {
                Ray ray = ServerPickRay.fromScreen(playerNumber, screenX, screenY, viewportW, viewportH);
                if (playerNumber == 1) {
                    w.handlePlayerClick(ray);
                } else {
                    w.handleOpponentClick(ray);
                }
            });
        }

        @Override
        public void onServe() {
            MatchWorld3D w = world;
            if (w == null || !matchRunning) return;
            actions.offer(() -> {
                if (playerNumber == 1) w.tryPlayerServe();
                else w.tryClientServe();
            });
        }

        @Override
        public void onBye() {
            matchRunning = false;
        }

        @Override
        public void onDisconnected() {
            matchRunning = false;
        }
    }
}
