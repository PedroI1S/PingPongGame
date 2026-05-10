package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Lobby screen for server-authoritative LAN multiplayer.
 *
 * <p>Buckshot-Roulette-style bunker UI with everything reachable by mouse.
 * Keyboard shortcuts (H / J / ENTER / ESC) still work.</p>
 *
 * <h3>HOST</h3>
 * Click [HOST] → embedded {@link GameServer} starts → game connects to
 * {@code localhost:}{@link PacketType#PORT} as Player 1 → on
 * {@code WELCOME(1)} the lobby transitions to {@link NetMatchScreen}.
 *
 * <h3>JOIN</h3>
 * Click [JOIN] → type the host's 7-char room code → click [CONNECT] →
 * connection opens to the decoded IP → on {@code WELCOME(2)} the lobby
 * transitions to {@link NetMatchScreen}.
 */
public final class MultiplayerLobbyScreen extends BaseScreen {

    private enum Phase { IDLE, AWAITING_CODE, HOSTING_WAIT, CONNECTING, ERROR }

    private final InputHandler input = new InputHandler();
    private final Vector3 cursorWorld = new Vector3();
    private Phase phase = Phase.IDLE;
    private float clock;

    private String localRoomCode = "???????";
    private String codeBuffer    = "";
    private String errorText;

    private GameConnection pendingConn;
    private GameServer     pendingServer;

    // ── Buttons ───────────────────────────────────────────────────────────────

    private final Button hostBtn;
    private final Button joinBtn;
    private final Button backToMenuBtn;
    private final Button connectBtn;
    private final Button cancelCodeBtn;
    private final Button cancelHostBtn;
    private final Button cancelConnectBtn;
    private final Button errorBackBtn;

    public MultiplayerLobbyScreen(Main game) {
        super(game);
        float cx = GameConfig.WORLD_WIDTH * 0.5f;

        hostBtn       = new Button(cx - 220f, 320f, 200f, 110f, "[ HOST ]",  Palette.RED,  this::startHosting);
        joinBtn       = new Button(cx +  20f, 320f, 200f, 110f, "[ JOIN ]",  Palette.WARM, this::startJoining);
        backToMenuBtn = new Button(cx -  90f, 170f, 180f,  56f, "BACK TO MENU", Palette.TEXT_DIM, game::openMenu);

        connectBtn    = new Button(cx -  90f, 320f, 180f,  56f, "[ CONNECT ]", Palette.WARM, this::confirmCode);
        cancelCodeBtn = new Button(cx -  90f, 240f, 180f,  44f, "CANCEL", Palette.TEXT_DIM, () -> phase = Phase.IDLE);

        cancelHostBtn    = new Button(cx -  90f, 220f, 180f,  56f, "CANCEL", Palette.RED, this::cancelInProgress);
        cancelConnectBtn = new Button(cx -  90f, 320f, 180f,  56f, "CANCEL", Palette.RED, this::cancelInProgress);
        errorBackBtn     = new Button(cx -  90f, 320f, 180f,  56f, "BACK", Palette.WARM, () -> phase = Phase.IDLE);
    }

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

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(float delta) {
        clock += delta;
        updateCursorWorld();
        for (Button b : activeButtons()) b.updateHover(cursorWorld.x, cursorWorld.y);

        // Lobby is a menu — no retro post-process here.
        beginFrame(Palette.BG.r, Palette.BG.g, Palette.BG.b);
        SpriteBatch      batch   = context.getBatch();
        ProceduralAssets visuals = context.getAssets().getProceduralAssets();
        Texture          pixel   = visuals.getPixel();
        BitmapFont       title   = context.getTitleFont();
        BitmapFont       body    = context.getBodyFont();

        batch.begin();
        batch.setColor(Color.WHITE);
        UIDraw.fill(batch, pixel, Palette.BG, 0f, 0f,
            GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT);
        UIDraw.cornerMarks(batch, pixel, 24f);

        UIDraw.topBar(batch, pixel, body, context.getGlyphLayout(),
            "MULTIPLAYER LOBBY", "PORT " + PacketType.PORT, Palette.WARM);
        UIDraw.bottomBar(batch, pixel, body, context.getGlyphLayout(),
            bottomHint(), "CLICK ANYTHING — KEYBOARD ALSO WORKS", Palette.TEXT_DIM);

        float cx = GameConfig.WORLD_WIDTH * 0.5f;
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "===  LAN DUEL  ===", cx, 660f, Palette.RED);
        title.getData().setScale(2.6f);
        UIDraw.centered(batch, title, context.getGlyphLayout(),
            "WHO IS HOSTING", cx, 605f, Palette.TEXT);
        title.getData().setScale(2.2f);

        switch (phase) {
            case IDLE          -> drawIdle(batch, pixel, body, cx);
            case AWAITING_CODE -> drawAwaitingCode(batch, pixel, body, cx);
            case HOSTING_WAIT  -> drawHostingWait(batch, pixel, body, title, cx);
            case CONNECTING    -> drawConnecting(batch, pixel, body, cx);
            case ERROR         -> drawError(batch, pixel, body, cx);
        }

        for (Button b : activeButtons()) b.draw(batch, pixel, body, context.getGlyphLayout());

        batch.end();
    }

    // ── Phase drawing ─────────────────────────────────────────────────────────

    private void drawIdle(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "ONE OF YOU OPENS A SERVER.  THE OTHER TYPES YOUR ROOM CODE.",
            cx, 540f, Palette.TEXT_DIM);

        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "YOUR ROOM CODE:", cx, 270f, Palette.TEXT_DIM);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            localRoomCode, cx, 240f, Palette.WARM);
    }

    private void drawAwaitingCode(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "ENTER THE HOST'S " + RoomCode.LENGTH + "-CHARACTER CODE.",
            cx, 540f, Palette.TEXT_DIM);

        float w = 360f, h = 70f, x = cx - w * 0.5f, y = 430f;
        UIDraw.fill(batch, pixel, Palette.SURFACE, x, y, w, h);
        UIDraw.border(batch, pixel, Palette.WARM, x, y, w, h, 2f);

        boolean cursorOn = ((int)(clock * 2f)) % 2 == 0;
        StringBuilder spaced = new StringBuilder();
        for (int i = 0; i < codeBuffer.length(); i++) {
            if (i > 0) spaced.append(' ');
            spaced.append(codeBuffer.charAt(i));
        }
        if (cursorOn) { if (spaced.length() > 0) spaced.append(' '); spaced.append('_'); }
        body.setColor(Palette.TEXT);
        body.draw(batch, spaced.toString(), x + 18f, y + 44f);

        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "LOCAL TEST CODE: " + RoomCode.encode("127.0.0.1"),
            cx, 405f, Palette.TEXT_DIM);
    }

    private void drawHostingWait(SpriteBatch batch, Texture pixel, BitmapFont body,
                                 BitmapFont title, float cx) {
        boolean blink = ((int)(clock * 1.6f)) % 2 == 0;
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "SERVER ONLINE — WAITING FOR THE OTHER SIDE" + (blink ? " ..." : "    "),
            cx, 540f, Palette.RED);

        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "SHARE YOUR ROOM CODE:", cx, 480f, Palette.TEXT_DIM);
        title.getData().setScale(3.4f);
        UIDraw.centered(batch, title, context.getGlyphLayout(),
            localRoomCode, cx, 410f, Palette.WARM);
        title.getData().setScale(2.2f);
    }

    private void drawConnecting(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        boolean blink = ((int)(clock * 1.6f)) % 2 == 0;
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "DIALING IN" + (blink ? " ..." : "    "), cx, 480f, Palette.WARM);
    }

    private void drawError(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "ERROR: " + (errorText == null ? "UNKNOWN" : errorText.toUpperCase()),
            cx, 480f, Palette.RED);
    }

    // ── Active buttons per phase ──────────────────────────────────────────────

    private List<Button> activeButtons() {
        List<Button> out = new ArrayList<>();
        switch (phase) {
            case IDLE          -> { out.add(hostBtn); out.add(joinBtn); out.add(backToMenuBtn); }
            case AWAITING_CODE -> { out.add(connectBtn); out.add(cancelCodeBtn); }
            case HOSTING_WAIT  -> out.add(cancelHostBtn);
            case CONNECTING    -> out.add(cancelConnectBtn);
            case ERROR         -> out.add(errorBackBtn);
        }
        connectBtn.enabled = codeBuffer.length() == RoomCode.LENGTH;
        return out;
    }

    // ── Connection logic ──────────────────────────────────────────────────────

    private void startHosting() {
        phase = Phase.HOSTING_WAIT;
        GameServer server = new GameServer();
        pendingServer = server;
        server.start(
            context.getSession().buildMatchConfig(),
            context.getSession().getRandom(),
            () -> Gdx.app.postRunnable(this::onServerReady),
            () -> Gdx.app.postRunnable(() -> showError("server failed to start"))
        );
    }

    private void onServerReady() {
        if (phase != Phase.HOSTING_WAIT) return;
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

    private void openConnection(String host) {
        pendingConn = GameConnection.connect(
            host, PacketType.PORT, Gdx.app::postRunnable, new LobbyListener());
    }

    private void cancelInProgress() {
        cleanupPending();
        phase = Phase.IDLE;
    }

    private void showError(String text) {
        errorText = text;
        phase = Phase.ERROR;
        cleanupPending();
    }

    private void cleanupPending() {
        if (pendingConn   != null) { pendingConn.close();  pendingConn   = null; }
        if (pendingServer != null) { pendingServer.stop(); pendingServer = null; }
    }

    // ── Lobby listener (GL thread) ────────────────────────────────────────────

    private final class LobbyListener implements GameConnection.Listener {
        @Override public void onWaiting() { /* already in HOSTING_WAIT */ }

        @Override
        public void onWelcome(int playerNumber) {
            if (pendingConn == null) return;
            context.getSession().setMultiplayerConnection(pendingConn, playerNumber, pendingServer);
            pendingConn   = null;
            pendingServer = null;
            game.openNetMatch();
        }

        @Override public void onDisconnected()  { showError("connection lost before match started"); }
        @Override public void onError(String r) { showError(r); }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateCursorWorld() {
        cursorWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0f);
        context.getViewport().unproject(cursorWorld);
    }

    private String bottomHint() {
        return switch (phase) {
            case IDLE          -> "[H] HOST    [J] JOIN    [ESC] BACK";
            case AWAITING_CODE -> "TYPE THE ROOM CODE  --  [ENTER] CONNECT  --  [ESC] CANCEL";
            case HOSTING_WAIT  -> "WAITING...  --  [ESC] CANCEL";
            case CONNECTING    -> "DIALING...  --  [ESC] CANCEL";
            case ERROR         -> "[ENTER / ESC] BACK";
        };
    }

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

    // ── Input ─────────────────────────────────────────────────────────────────

    private final class InputHandler extends InputAdapter {
        @Override
        public boolean keyDown(int k) {
            if (k == Input.Keys.ESCAPE) { handleEscape(); return true; }
            switch (phase) {
                case IDLE -> {
                    if (k == Input.Keys.H) startHosting();
                    else if (k == Input.Keys.J) startJoining();
                }
                case AWAITING_CODE -> {
                    if (k == Input.Keys.ENTER) confirmCode();
                    else if (k == Input.Keys.BACKSPACE && !codeBuffer.isEmpty())
                        codeBuffer = codeBuffer.substring(0, codeBuffer.length() - 1);
                }
                case ERROR -> {
                    if (k == Input.Keys.ENTER) phase = Phase.IDLE;
                }
                default -> {}
            }
            return true;
        }

        @Override
        public boolean keyTyped(char ch) {
            if (phase != Phase.AWAITING_CODE) return false;
            if (codeBuffer.length() >= RoomCode.LENGTH) return false;
            if (RoomCode.isValidChar(ch)) {
                codeBuffer += Character.toUpperCase(ch);
                return true;
            }
            return false;
        }

        @Override
        public boolean touchDown(int sx, int sy, int pointer, int btn) {
            updateCursorWorld();
            for (Button b : activeButtons()) {
                if (b.tryClick(cursorWorld.x, cursorWorld.y)) return true;
            }
            return false;
        }
    }

    private void handleEscape() {
        switch (phase) {
            case IDLE  -> game.openMenu();
            case ERROR -> phase = Phase.IDLE;
            default    -> { cleanupPending(); phase = Phase.IDLE; }
        }
    }
}
