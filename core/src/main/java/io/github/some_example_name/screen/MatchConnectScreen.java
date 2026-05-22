package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.some_example_name.Main;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;
import io.github.some_example_name.model.MatchMode;
import io.github.some_example_name.network.GameConnection;
import io.github.some_example_name.network.PacketType;

/**
 * Bridges menu → match: connects to the dedicated {@link GameConnection server} and
 * sends {@link PacketType#JOIN}.
 *
 * <p>The dedicated server is auto-launched at startup by {@link Main} as a subprocess.
 * This screen never spawns a server itself — it only dials {@code serverHost:PORT}.</p>
 *
 * <p>Two constructor variants:</p>
 * <ul>
 *   <li>{@link #MatchConnectScreen(Main, Kind)} — connects to
 *       {@code PINGPONG_SERVER_HOST} (default {@code 127.0.0.1}).</li>
 *   <li>{@link #MatchConnectScreen(Main, Kind, String)} — same, custom host.</li>
 * </ul>
 */
public final class MatchConnectScreen extends BaseScreen {

    public enum Kind {
        BOT(PacketType.MODE_BOT, MatchMode.BOT),
        PVP(PacketType.MODE_PVP, MatchMode.PVP);

        final int modeWire;
        final MatchMode matchMode;

        Kind(int modeWire, MatchMode matchMode) {
            this.modeWire = modeWire;
            this.matchMode = matchMode;
        }
    }

    private final Kind kind;
    private final String serverHost;
    private String statusText = "Connecting...";
    private boolean failed;
    private GameConnection pendingConn;

    public MatchConnectScreen(Main game, Kind kind) {
        this(game, kind, GameConfig.resolveServerHost());
    }

    public MatchConnectScreen(Main game, Kind kind, String serverHost) {
        super(game);
        this.kind = kind;
        this.serverHost = serverHost;
    }

    private final InputAdapter input = new InputAdapter() {
        @Override
        public boolean keyDown(int keycode) {
            return MatchConnectScreen.this.keyDown(keycode);
        }
    };

    @Override
    public void show() {
        failed = false;
        context.getSession().clearMultiplayer();
        Gdx.input.setInputProcessor(input);
        statusText = "Connecting to " + serverHost + "...";
        openConnection();
    }

    private void openConnection() {
        pendingConn = GameConnection.connect(
            serverHost, PacketType.PORT, Gdx.app::postRunnable, new ConnectListener());
    }

    @Override
    public void hide() {
        if (pendingConn != null) {
            pendingConn.close();
            pendingConn = null;
        }
    }

    @Override
    public void render(float delta) {
        beginFrame(Palette.BG.r, Palette.BG.g, Palette.BG.b);
        SpriteBatch batch = context.getBatch();
        batch.begin();
        drawCentered(batch, context.getTitleFont(),
            kind == Kind.BOT ? "VS BOT" : "Connecting",
            GameConfig.WORLD_WIDTH * 0.5f, 400f, Palette.TEXT);
        drawCentered(batch, context.getBodyFont(), statusText,
            GameConfig.WORLD_WIDTH * 0.5f, 340f, failed ? Palette.RED : Palette.TEXT_DIM);
        if (failed) {
            drawCentered(batch, context.getBodyFont(),
                "Server not running. Build: ./gradlew server:jar",
                GameConfig.WORLD_WIDTH * 0.5f, 300f, Palette.TEXT_DIM);
            drawCentered(batch, context.getBodyFont(), "Press ESC for menu.",
                GameConfig.WORLD_WIDTH * 0.5f, 268f, Palette.TEXT_DIM);
        } else {
            drawCentered(batch, context.getBodyFont(), "ESC = cancel",
                GameConfig.WORLD_WIDTH * 0.5f, 268f, Palette.TEXT_DIM);
        }
        batch.end();
    }

    boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            context.getSession().clearMultiplayer();
            game.openMenu();
            return true;
        }
        return false;
    }

    private void fail(String reason) {
        if (pendingConn != null) {
            pendingConn.close();
            pendingConn = null;
        }
        context.getSession().clearMultiplayer();
        failed = true;
        statusText = reason;
    }

    private final class ConnectListener implements GameConnection.Listener {
        private int assignedPlayer;
        private boolean welcomeReceived;
        private boolean matchReadyReceived;

        @Override
        public void onConnected() {
            if (pendingConn != null) {
                pendingConn.sendJoin(kind.modeWire);
            }
        }

        @Override
        public void onWelcome(int playerNumber) {
            assignedPlayer = playerNumber;
            welcomeReceived = true;
            statusText = "Joined as P" + playerNumber + ". Waiting for match...";
            tryEnterMatch();
        }

        @Override
        public void onMatchReady(int matchModeWire) {
            matchReadyReceived = true;
            if (matchModeWire == PacketType.MODE_BOT) {
                context.getSession().setRemoteName("Bot");
            }
            tryEnterMatch();
        }

        private void tryEnterMatch() {
            if (!welcomeReceived || !matchReadyReceived || pendingConn == null) {
                return;
            }
            context.getSession().setMultiplayerConnection(
                pendingConn, assignedPlayer, kind.matchMode);
            pendingConn = null;
            game.openNetMatch();
        }

        @Override
        public void onDisconnected() {
            fail("Connection lost — is the server running?");
        }

        @Override
        public void onError(String reason) {
            fail(reason);
        }
    }
}
