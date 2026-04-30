package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.some_example_name.Main;
import io.github.some_example_name.assets.ProceduralAssets;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;
import io.github.some_example_name.network.GameConnection;
import io.github.some_example_name.network.PacketType;
import io.github.some_example_name.network.RoomCode;
import io.github.some_example_name.server.GameServer;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Lobby screen for the server-authoritative multiplayer mode.
 *
 * <h3>HOST flow</h3>
 * <ol>
 *   <li>Press H → {@link GameServer} starts on a background thread.</li>
 *   <li>Once the server socket is listening, a {@link GameConnection} connects to
 *       {@code localhost:}{@link PacketType#PORT}.</li>
 *   <li>Server sends {@code WELCOME(1)} → lobby stores connection + server in the
 *       session and transitions to {@link NetMatchScreen} as Player 1.</li>
 *   <li>Match screen shows "waiting for opponent" until a second player joins
 *       and the server starts broadcasting STATE.</li>
 * </ol>
 *
 * <h3>JOIN flow</h3>
 * <ol>
 *   <li>Press J → enter the host's 7-character room code.</li>
 *   <li>{@link GameConnection} connects to the decoded IP.</li>
 *   <li>Server sends {@code WELCOME(2)} → transition to {@link NetMatchScreen}.</li>
 * </ol>
 */
public final class MultiplayerLobbyScreen extends BaseScreen {

    private enum Phase { IDLE, AWAITING_CODE, HOSTING_WAIT, CONNECTING, ERROR }

    private final InputHandler input = new InputHandler();
    private Phase phase = Phase.IDLE;
    private float clock;

    private String localRoomCode = "???????";
    private String codeBuffer    = "";
    private String errorText;

    /**
     * In-flight connection — held until {@code WELCOME} arrives, then handed to
     * {@link io.github.some_example_name.core.GameSession} with ownership transferred.
     */
    private GameConnection pendingConn;

    /**
     * Local server — non-null only when this machine is the host.
     * Handed to the session alongside {@code pendingConn}.
     */
    private GameServer pendingServer;

    public MultiplayerLobbyScreen(Main game) { super(game); }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
        String localIp = discoverLocalIp();
        localRoomCode  = RoomCode.encode(localIp != null ? localIp : "127.0.0.1");
        phase      = Phase.IDLE;
        clock      = 0f;
        codeBuffer = "";
        errorText  = null;
        cleanupPending();
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(float delta) {
        clock += delta;

        beginFrame(Palette.BG.r, Palette.BG.g, Palette.BG.b);
        SpriteBatch      batch   = context.getBatch();
        ProceduralAssets visuals = context.getAssets().getProceduralAssets();
        Texture          pixel   = visuals.getPixel();
        BitmapFont       title   = context.getTitleFont();
        BitmapFont       body    = context.getBodyFont();

        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(visuals.getBackground(), 0f, 0f, GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT);
        UIDraw.redGrid(batch, pixel, 0.04f);
        UIDraw.scanlines(batch, pixel);
        UIDraw.movingScanline(batch, pixel, clock, 6f);
        UIDraw.filmGrain(batch, visuals.getNoise(), clock, 0.05f);
        UIDraw.cornerMarks(batch, pixel, 24f);

        UIDraw.topBar(batch, pixel, body, context.getGlyphLayout(),
            "<- BACK TO MENU (ESC)", "PHASE // LOBBY", Palette.TEXT_DIM);
        UIDraw.bottomBar(batch, pixel, body, context.getGlyphLayout(),
            bottomHint(), "PORT " + PacketType.PORT, Palette.TEXT_DIM);

        float cx = GameConfig.WORLD_WIDTH * 0.5f;

        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "===  LAN DUEL  ===", cx, 660f, Palette.RED);
        title.getData().setScale(2.2f);
        UIDraw.centered(batch, title, context.getGlyphLayout(),
            "MULTIPLAYER LOBBY", cx, 615f, Palette.TEXT);

        switch (phase) {
            case IDLE         -> drawIdle(batch, pixel, body, cx);
            case AWAITING_CODE -> drawAwaitingCode(batch, pixel, body, cx);
            case HOSTING_WAIT -> drawHostingWait(batch, pixel, body, title, cx);
            case CONNECTING   -> drawConnecting(batch, pixel, body, cx);
            case ERROR        -> drawError(batch, pixel, body, cx);
        }

        batch.end();
    }

    // ── Phase drawing ─────────────────────────────────────────────────────────

    private void drawIdle(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "PRESS H TO HOST    OR    J TO JOIN A GAME", cx, 540f, Palette.TEXT_DIM);
        drawSquareBtn(batch, pixel, body, "[H] HOST", cx - 200f, 380f, Palette.RED);
        drawSquareBtn(batch, pixel, body, "[J] JOIN", cx +  40f, 380f, Palette.BLUE);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "YOUR ROOM CODE:", cx, 265f, Palette.TEXT_DIM);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            localRoomCode,    cx, 230f, Palette.GREEN);
    }

    private void drawAwaitingCode(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "ENTER HOST'S ROOM CODE  (" + RoomCode.LENGTH
                + " CHARS — BACKSPACE / ENTER / ESC)",
            cx, 540f, Palette.TEXT_DIM);

        float w = 320f, h = 64f, x = cx - w * 0.5f, y = 420f;
        UIDraw.fill(batch, pixel, Palette.SURFACE, x, y, w, h);
        UIDraw.border(batch, pixel, Palette.BLUE, x, y, w, h, 2f);

        boolean cursorOn = ((int)(clock * 2f)) % 2 == 0;
        StringBuilder spaced = new StringBuilder();
        for (int i = 0; i < codeBuffer.length(); i++) {
            if (i > 0) spaced.append(' ');
            spaced.append(codeBuffer.charAt(i));
        }
        if (cursorOn) { if (spaced.length() > 0) spaced.append(' '); spaced.append('_'); }
        body.setColor(Palette.TEXT);
        body.draw(batch, spaced.toString(), x + 16f, y + 40f);

        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "LOCAL TESTING CODE: " + RoomCode.encode("127.0.0.1"),
            cx, 340f, Palette.TEXT_DIM);
    }

    private void drawHostingWait(SpriteBatch batch, Texture pixel, BitmapFont body,
                                 BitmapFont title, float cx) {
        boolean blink = ((int)(clock * 1.6f)) % 2 == 0;
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "SERVER RUNNING — WAITING FOR OPPONENT" + (blink ? " ..." : "    "),
            cx, 510f, Palette.RED);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "SHARE YOUR ROOM CODE:", cx, 460f, Palette.TEXT_DIM);
        title.getData().setScale(3.2f);
        UIDraw.centered(batch, title, context.getGlyphLayout(),
            localRoomCode, cx, 400f, Palette.GREEN);
        title.getData().setScale(2.2f);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "ESC TO CANCEL", cx, 310f, Palette.TEXT_DIM);
    }

    private void drawConnecting(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        boolean blink = ((int)(clock * 1.6f)) % 2 == 0;
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "CONNECTING" + (blink ? " ..." : "    "), cx, 480f, Palette.BLUE);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "ESC TO CANCEL", cx, 320f, Palette.TEXT_DIM);
    }

    private void drawError(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "ERROR: " + (errorText == null ? "UNKNOWN" : errorText.toUpperCase()),
            cx, 480f, Palette.RED);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "PRESS ESC OR ENTER TO RETURN", cx, 380f, Palette.TEXT_DIM);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    private void onKeyPressed(int key) {
        if (key == Input.Keys.ESCAPE) { handleEscape(); return; }
        switch (phase) {
            case IDLE -> {
                if      (key == Input.Keys.H) startHosting();
                else if (key == Input.Keys.J) startJoining();
            }
            case AWAITING_CODE -> {
                if (key == Input.Keys.ENTER) confirmCode();
                else if (key == Input.Keys.BACKSPACE && !codeBuffer.isEmpty())
                    codeBuffer = codeBuffer.substring(0, codeBuffer.length() - 1);
            }
            case ERROR -> {
                if (key == Input.Keys.ENTER) phase = Phase.IDLE;
            }
            default -> {}
        }
    }

    private void onCharTyped(char ch) {
        if (phase != Phase.AWAITING_CODE) return;
        if (codeBuffer.length() >= RoomCode.LENGTH) return;
        if (RoomCode.isValidChar(ch)) codeBuffer += Character.toUpperCase(ch);
    }

    private void handleEscape() {
        switch (phase) {
            case IDLE  -> game.openMenu();
            case ERROR -> phase = Phase.IDLE;
            default    -> { cleanupPending(); phase = Phase.IDLE; }
        }
    }

    // ── Connection logic ──────────────────────────────────────────────────────

    private void startHosting() {
        phase = Phase.HOSTING_WAIT;
        GameServer server = new GameServer();
        pendingServer = server;
        // Start server; its callback fires on the server thread, we dispatch to GL.
        server.start(
            context.getSession().buildMatchConfig(),
            context.getSession().getRandom(),
            () -> Gdx.app.postRunnable(this::onServerReady),
            () -> Gdx.app.postRunnable(() -> showError("server failed to start"))
        );
    }

    /** GL-thread callback: server socket is open, connect as P1. */
    private void onServerReady() {
        if (phase != Phase.HOSTING_WAIT) return; // user cancelled
        openConnection("127.0.0.1");
    }

    private void startJoining() {
        codeBuffer = "";
        phase = Phase.AWAITING_CODE;
    }

    private void confirmCode() {
        if (codeBuffer.length() != RoomCode.LENGTH) return;
        String ip = RoomCode.decode(codeBuffer);
        if (ip == null) { showError("invalid room code"); return; }
        phase = Phase.CONNECTING;
        openConnection(ip);
    }

    /**
     * Creates a {@link GameConnection} to {@code host:PORT} and stores it in
     * {@code pendingConn} immediately — before any callback can fire on the GL
     * thread.  The lobby listener transitions to {@link NetMatchScreen} on
     * {@code WELCOME}.
     */
    private void openConnection(String host) {
        pendingConn = GameConnection.connect(
            host, PacketType.PORT, Gdx.app::postRunnable, new LobbyListener());
        // pendingConn is set before any GL callback fires; safe.
    }

    private void showError(String text) {
        errorText = text;
        phase = Phase.ERROR;
        cleanupPending();
    }

    /** Closes and nulls in-progress connection / server without crashing. */
    private void cleanupPending() {
        if (pendingConn   != null) { pendingConn.close();  pendingConn   = null; }
        if (pendingServer != null) { pendingServer.stop(); pendingServer = null; }
    }

    // ── Lobby listener (always on GL thread) ──────────────────────────────────

    private final class LobbyListener implements GameConnection.Listener {
        @Override
        public void onWaiting() {
            // P1 received this: server is open, waiting for P2.
            // Already showing HOSTING_WAIT — no UI change needed.
        }

        @Override
        public void onWelcome(int playerNumber) {
            if (pendingConn == null) return;
            // Transfer ownership to the session.
            context.getSession().setMultiplayerConnection(pendingConn, playerNumber, pendingServer);
            pendingConn   = null; // transferred
            pendingServer = null;
            game.openNetMatch();
        }

        @Override
        public void onDisconnected() {
            showError("connection lost before game started");
        }

        @Override
        public void onError(String reason) {
            showError(reason);
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void drawSquareBtn(SpriteBatch batch, Texture pixel, BitmapFont font,
                               String text, float x, float y, Color accent) {
        float w = 160f, h = 90f;
        UIDraw.fill(batch, pixel, accent, 0.08f, x, y, w, h);
        UIDraw.border(batch, pixel, accent, x, y, w, h, 2f);
        UIDraw.centered(batch, font, context.getGlyphLayout(),
            text, x + w * 0.5f, y + 56f, accent);
    }

    private String bottomHint() {
        return switch (phase) {
            case IDLE          -> "H = HOST    J = JOIN    ESC = BACK";
            case AWAITING_CODE -> "TYPE ROOM CODE    ENTER = CONNECT    ESC = CANCEL";
            case HOSTING_WAIT  -> "WAITING FOR SECOND PLAYER...";
            case CONNECTING    -> "CONNECTING...";
            case ERROR         -> "ESC = BACK";
        };
    }

    // ── Network utility ───────────────────────────────────────────────────────

    private static String discoverLocalIp() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0)
                        return addr.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    private final class InputHandler extends InputAdapter {
        @Override public boolean keyDown(int k)  { onKeyPressed(k); return true; }
        @Override public boolean keyTyped(char c) { onCharTyped(c); return true; }
    }
}
