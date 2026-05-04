package io.github.some_example_name.server;

import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.network.GameConnection;
import io.github.some_example_name.network.PacketType;
import io.github.some_example_name.world.MatchWorld3D;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Headless authoritative game server.
 *
 * <p>Accepts exactly two TCP clients on {@link PacketType#PORT}, assigns player
 * roles (first = P1, second = P2), then runs {@link MatchWorld3D} at ~60 Hz and
 * broadcasts STATE snapshots to both clients at ~30 Hz.</p>
 *
 * <p>All game physics, collision, and scoring happen here; clients are purely
 * visual and send only input events (SERVE, HIT).</p>
 *
 * <h3>Coordinate convention (broadcast to both clients)</h3>
 * <p>World coordinates are sent as-is to both players.  P1's camera sits at +z
 * and P2's camera sits at −z, so both cameras look at the same table; the
 * perspective handles left/right mirroring automatically.</p>
 */
public final class GameServer {

    private static final float TICK_HZ  = 60f;
    private static final float TICK_DT  = 1f / TICK_HZ;
    private static final float STATE_HZ = 30f;
    private static final float STATE_DT = 1f / STATE_HZ;
    private static final long  TICK_NS  = (long) (TICK_DT * 1_000_000_000L);

    // ── State ─────────────────────────────────────────────────────────────────

    private volatile boolean   running;
    private ServerSocket       serverSocket;
    private GameConnection     p1, p2;
    private volatile MatchWorld3D world; // set after game loop begins

    /** Physics actions posted by reader threads, consumed by the game loop. */
    private final LinkedBlockingQueue<Runnable> actions = new LinkedBlockingQueue<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Starts the server on a daemon thread.
     *
     * @param config     match configuration (items, difficulty, etc.)
     * @param random     shared RNG
     * @param onListening Runnable invoked (on the server thread) once the
     *                    {@link ServerSocket} is open and accepting connections.
     *                    The caller typically wraps this with
     *                    {@code Gdx.app::postRunnable} to kick off the client
     *                    connection on the GL thread.
     * @param onError     Runnable invoked if startup fails.
     */
    public void start(MatchConfig config, RandomXS128 random,
                      Runnable onListening, Runnable onError) {
        Thread t = new Thread(() -> runServer(config, random, onListening, onError),
                              "game-server");
        t.setDaemon(true);
        t.start();
    }

    /** Stops the game loop and closes all sockets. Safe to call from any thread. */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (Exception ignored) {}
        if (p1 != null) p1.close();
        if (p2 != null) p2.close();
    }

    // ── Server loop ───────────────────────────────────────────────────────────

    private void runServer(MatchConfig config, RandomXS128 random,
                           Runnable onListening, Runnable onError) {
        try {
            serverSocket = new ServerSocket(PacketType.PORT);
            serverSocket.setReuseAddress(true);

            // Notify caller the port is open so P1 can connect.
            onListening.run();

            // ── Accept P1 ────────────────────────────────────────────────────
            Socket s1 = serverSocket.accept();
            p1 = GameConnection.wrap(s1, Runnable::run, new PlayerListener(1));
            p1.sendWelcome(1);
            p1.sendWaiting(); // let P1 know we're waiting for P2

            // ── Accept P2 ────────────────────────────────────────────────────
            Socket s2 = serverSocket.accept();
            p2 = GameConnection.wrap(s2, Runnable::run, new PlayerListener(2));
            p2.sendWelcome(2);

            // Close the accept socket — we have our two players.
            serverSocket.close();

            // ── Build world and run ───────────────────────────────────────────
            MatchWorld3D w = new MatchWorld3D(config, random);
            w.setNetworkMode(true);
            world = w; // make visible to listeners

            running = true;
            long lastNs   = System.nanoTime();
            float stateAcc = 0f;

            while (running && !w.isMatchOver()) {
                long now   = System.nanoTime();
                float delta = Math.min((now - lastNs) / 1_000_000_000f, 0.1f);
                lastNs = now;

                // Process all queued player inputs.
                Runnable action;
                while ((action = actions.poll()) != null) action.run();

                // Advance physics.
                w.update(delta);

                // SFX events — broadcast to both clients simultaneously.
                if (w.consumePaddleHitEvent()) {
                    p1.sendSfx(PacketType.SFX_PADDLE);
                    p2.sendSfx(PacketType.SFX_PADDLE);
                }
                if (w.consumeTableBounceEvent()) {
                    p1.sendSfx(PacketType.SFX_TABLE);
                    p2.sendSfx(PacketType.SFX_TABLE);
                }

                // STATE broadcast at 30 Hz.
                stateAcc += delta;
                if (stateAcc >= STATE_DT) {
                    stateAcc -= STATE_DT;
                    broadcastState(w);
                }

                // Sleep to pace the tick rate.
                long elapsed = System.nanoTime() - now;
                long sleepNs = TICK_NS - elapsed;
                if (sleepNs > 1_000_000L) {
                    Thread.sleep(sleepNs / 1_000_000L);
                }
            }

            // Send outcome to both players.
            if (w.isMatchOver()) {
                int winner = w.getOutcome() == MatchOutcome.PLAYER_WIN ? 1 : 2;
                p1.sendGameOver(winner);
                p2.sendGameOver(winner);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            System.err.println("[GameServer] Fatal: " + e);
            onError.run();
        } finally {
            if (p1 != null) p1.close();
            if (p2 != null) p2.close();
        }
    }

    private void broadcastState(MatchWorld3D w) {
        Vector3 pos = w.getBallPos();
        Vector3 vel = w.getBallVel();
        boolean vis = w.isBallVisible();
        int ap  = w.getActivePlayer();
        int p1l = w.getPlayerLives();
        int p2l = w.getBotLives();

        // Both clients receive the same world-coordinate snapshot.
        // P1 camera is at +z, P2 camera is at −z — the perspective handles mirroring.
        p1.sendState(pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, p1l, p2l, vis, ap);
        p2.sendState(pos.x, pos.y, pos.z, vel.x, vel.y, vel.z, p1l, p2l, vis, ap);
    }

    // ── Player listener ───────────────────────────────────────────────────────

    /**
     * Listener attached to each client connection.
     * Callbacks run on the reader thread and immediately post
     * physics lambdas to the {@link #actions} queue.
     */
    private final class PlayerListener implements GameConnection.Listener {
        private final int playerNumber;

        PlayerListener(int playerNumber) { this.playerNumber = playerNumber; }

        @Override
        public void onServe() {
            MatchWorld3D w = world;
            if (w == null) return;
            if (playerNumber == 1) {
                actions.offer(w::tryPlayerServe);
            } else {
                actions.offer(w::tryClientServe);
            }
        }

        @Override
        public void onHit(float vx, float vy, float vz) {
            MatchWorld3D w = world;
            if (w == null) return;
            if (playerNumber == 1) {
                // P1 hits during INCOMING — ball must be on P1's side (z > 0).
                actions.offer(() -> w.playerHit(vx, vy, vz));
            } else {
                // P2 hits during OUTGOING / BOT_RESOLVE — ball on P2's side (z < 0).
                actions.offer(() -> w.acceptClientHit(vx, vy, vz));
            }
        }

        @Override
        public void onBye() {
            stop();
        }

        @Override
        public void onDisconnected() {
            stop();
        }
    }
}
