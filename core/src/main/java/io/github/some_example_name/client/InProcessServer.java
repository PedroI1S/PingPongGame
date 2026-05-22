package io.github.some_example_name.client;

import io.github.some_example_name.server.GameServer;
import io.github.some_example_name.server.ServerOptions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs the headless {@link GameServer} on a daemon thread inside the client
 * process, so VS BOT and HOST work out-of-the-box without first building or
 * launching the {@code server} fat jar.
 *
 * <p>The TCP listen socket is opened by the same code path
 * {@link io.github.some_example_name.server.ServerMain} uses, so clients can
 * connect to {@code 127.0.0.1:7777} (or {@code 0.0.0.0:7777} when hosting LAN)
 * exactly as if it were a dedicated server.</p>
 *
 * <p>Compared to {@link LocalServerProcess} this:</p>
 * <ul>
 *   <li>Needs no built fat jar.</li>
 *   <li>Starts in &lt; 10 ms (no JVM fork).</li>
 *   <li>Dies with the client when the process exits.</li>
 * </ul>
 *
 * <p>{@code LocalServerProcess} stays in the codebase for the scenario where
 * you actually want an isolated subprocess.</p>
 */
public final class InProcessServer {

    private GameServer server;
    private Thread     thread;

    /**
     * Starts the server and blocks (briefly) until the listen socket is bound.
     *
     * @param port        TCP port to listen on
     * @param bindAddress {@code 127.0.0.1} for VS BOT, {@code 0.0.0.0} for LAN host
     * @param timeoutMs   how long to wait for the bind to succeed
     * @return {@code true} if the server is accepting connections
     */
    public boolean start(int port, String bindAddress, long timeoutMs) {
        stop();
        server = new GameServer();
        ServerOptions options = new ServerOptions(port, bindAddress);
        CountDownLatch ready = new CountDownLatch(1);
        thread = new Thread(
            () -> server.run(options, ready::countDown),
            "in-process-game-server");
        thread.setDaemon(true);
        thread.start();
        try {
            return ready.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Shuts the server down.  Idempotent. */
    public void stop() {
        if (server != null) {
            server.shutdown();
            server = null;
        }
        thread = null;
    }
}
