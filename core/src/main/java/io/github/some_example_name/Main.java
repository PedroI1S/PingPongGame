package io.github.some_example_name;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Screen;
import io.github.some_example_name.client.InProcessServer;
import io.github.some_example_name.client.LocalServerProcess;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.core.GameContext;
import io.github.some_example_name.network.PacketType;
import io.github.some_example_name.screen.ConfigScreen;
import io.github.some_example_name.screen.LoadingScreen;
import io.github.some_example_name.screen.MatchConnectScreen;
import io.github.some_example_name.screen.MenuScreen;
import io.github.some_example_name.screen.PauseMenuScreen;
import io.github.some_example_name.screen.MultiplayerLobbyScreen;
import io.github.some_example_name.screen.NetMatchScreen;

/**
 * Game bootstrap.
 *
 * <p>This keeps the launcher thin and centralizes screen navigation for the whole project.</p>
 */
public class Main extends Game {
    private GameContext context;
    private LocalServerProcess serverProcess;
    private InProcessServer    inProcessServer;

    @Override
    public void create() {
        // Keep libGDX's default HdpiMode.Logical.  Viewport.apply() then
        // auto-converts logical glViewport calls to backbuffer pixels (2× on
        // Retina), which is what every screen needs to fill the window.
        // RetroPostProcess sizes its FBO at backbuffer pixels so the
        // converted viewport matches the FBO.
        context = new GameContext();
        context.getAssets().queueCoreAssets();
        context.getSettings().applyWindowMode();
        context.applySettings();
        autoLaunchServer();
        setScreen(new LoadingScreen(this));
    }

    /**
     * Starts the game server on a background thread so it runs concurrently with
     * the LoadingScreen asset pass.
     *
     * <p>Strategy (first success wins):</p>
     * <ol>
     *   <li><strong>External server</strong> — if {@code PINGPONG_SERVER_HOST} points
     *       somewhere other than localhost, skip local launch entirely.</li>
     *   <li><strong>Subprocess</strong> ({@link LocalServerProcess}) — forks the fat jar
     *       when it exists.  Binds {@code 0.0.0.0} so LAN guests can reach it.</li>
     *   <li><strong>In-process</strong> ({@link InProcessServer}) — fallback when the jar
     *       hasn't been built yet (e.g. {@code ./gradlew :lwjgl3:run} during development).
     *       No fat jar needed; starts in &lt;10 ms.</li>
     * </ol>
     *
     * <p>If port 7777 is already occupied the in-process bind will fail too, and the
     * player's first VS BOT / HOST attempt will show a connection-error screen.</p>
     */
    private void autoLaunchServer() {
        String host = GameConfig.resolveServerHost();
        if (!"127.0.0.1".equals(host) && !"localhost".equals(host)) {
            System.out.println("[Main] External server configured (" + host + ") — skipping local launch.");
            return;
        }

        Thread t = new Thread(() -> {
            // 1. Try subprocess (needs a pre-built fat jar).
            LocalServerProcess proc = new LocalServerProcess();
            boolean ok = proc.start(null, "0.0.0.0", PacketType.PORT, 8000);
            if (ok) {
                synchronized (Main.this) { serverProcess = proc; }
                System.out.println("[Main] Dedicated server subprocess is ready.");
                return;
            }

            // 2. Fall back to in-process server (always available, no jar needed).
            System.out.println("[Main] Subprocess unavailable — starting in-process server.");
            InProcessServer inProc = new InProcessServer();
            boolean started = inProc.start(PacketType.PORT, "0.0.0.0", 5000);
            if (started) {
                synchronized (Main.this) { inProcessServer = inProc; }
                System.out.println("[Main] In-process server is ready.");
            } else {
                System.out.println("[Main] Server startup failed (port in use?). "
                    + "VS BOT / HOST will show a connection error.");
            }
        }, "server-auto-launch");
        t.setDaemon(true);
        t.start();
    }

    public GameContext getContext() {
        return context;
    }

    public void openMenu() {
        setScreen(new MenuScreen(this));
    }

    public void openConfig() {
        setScreen(new ConfigScreen(this));
    }

    public void openConfig(Runnable onBack) {
        setScreen(new ConfigScreen(this, onBack));
    }

    public void openMultiplayerLobby() {
        setScreen(new MultiplayerLobbyScreen(this));
    }

    public void openNetMatch() {
        setScreen(new NetMatchScreen(this));
    }

    /** VS BOT — connects to the dedicated server, then transitions to {@link NetMatchScreen}. */
    public void openMatch() {
        setScreen(new MatchConnectScreen(this, MatchConnectScreen.Kind.BOT, "127.0.0.1"));
    }

    public void openPauseMenu(Screen resumeScreen, Runnable quitAction) {
        setScreen(new PauseMenuScreen(this, resumeScreen, quitAction));
    }

    @Override
    public void dispose() {
        if (context != null) {
            context.getSession().clearMultiplayer();
        }
        super.dispose();
        if (context != null) {
            context.dispose();
        }
        synchronized (this) {
            if (serverProcess != null) {
                serverProcess.stop();
                serverProcess = null;
            }
            if (inProcessServer != null) {
                inProcessServer.stop();
                inProcessServer = null;
            }
        }
    }
}
