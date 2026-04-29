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
import io.github.some_example_name.network.NetClient;
import io.github.some_example_name.network.NetHost;
import io.github.some_example_name.network.NetPeer;
import io.github.some_example_name.network.PeerRouter;
import io.github.some_example_name.network.Protocol;
import io.github.some_example_name.network.RoomCode;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Phase 1 lobby: pick HOST or JOIN, exchange HELLO, both READY, host hits ENTER to launch.
 * Phase 2 will swap the placeholder match transition for an actual networked duel.
 */
public final class MultiplayerLobbyScreen extends BaseScreen {

    private enum Phase { IDLE, AWAITING_IP, HOSTING_WAIT, CONNECTING, LOBBY, ERROR }

    private final InputHandler input = new InputHandler();
    private Phase phase = Phase.IDLE;
    private float clock;

    private NetPeer    peer;
    private PeerRouter peerRouter;
    private boolean    isHost;
    private String localIpHint;
    private String remoteAddress = "127.0.0.1";

    private boolean localReady, remoteReady;
    private String  remoteName = "?";
    private String  errorText;
    /** Stores the room-code characters as the player types (max {@link RoomCode#LENGTH}). */
    private String  codeBuffer = "";
    private String  localRoomCode = "??????";

    public MultiplayerLobbyScreen(Main game) {
        super(game);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
        localIpHint = discoverLocalIp();
        localRoomCode = RoomCode.encode(localIpHint != null ? localIpHint : "127.0.0.1");
        // Reset state in case we re-enter the screen.
        phase = Phase.IDLE;
        clock = 0f;
        localReady = remoteReady = false;
        remoteName = "?";
        errorText = null;
        codeBuffer = "";
        if (peer != null) { peer.close(); peer = null; }
        peerRouter = null;
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void render(float delta) {
        clock += delta;

        beginFrame(Palette.BG.r, Palette.BG.g, Palette.BG.b);
        SpriteBatch batch = context.getBatch();
        ProceduralAssets visuals = context.getAssets().getProceduralAssets();
        Texture pixel = visuals.getPixel();
        BitmapFont title = context.getTitleFont();
        BitmapFont body  = context.getBodyFont();

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
            bottomLeftHint(), bottomRightHint(), Palette.TEXT_DIM);

        float cx = GameConfig.WORLD_WIDTH * 0.5f;

        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "===  LAN DUEL  ===", cx, 660f, Palette.RED);

        title.getData().setScale(2.2f);
        UIDraw.centered(batch, title, context.getGlyphLayout(),
            "MULTIPLAYER LOBBY", cx, 615f, Palette.TEXT);

        switch (phase) {
            case IDLE          -> drawIdle(batch, pixel, body, cx);
            case AWAITING_IP   -> drawAwaitingIp(batch, pixel, body, cx);
            case HOSTING_WAIT  -> drawHostingWait(batch, pixel, body, title, cx);
            case CONNECTING    -> drawConnecting(batch, pixel, body, cx);
            case LOBBY         -> drawLobby(batch, pixel, body, cx);
            case ERROR         -> drawError(batch, pixel, body, cx);
        }

        batch.end();
    }

    // ── per-phase rendering ─────────────────────────────────────────────

    private void drawIdle(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "PRESS H TO HOST    OR    J TO JOIN A GAME",
            cx, 540f, Palette.TEXT_DIM);

        // Two big buttons side by side
        drawSquareBtn(batch, pixel, body, "[H] HOST",  cx - 200f, 380f, Palette.RED);
        drawSquareBtn(batch, pixel, body, "[J] JOIN",  cx +  40f, 380f, Palette.BLUE);

        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "YOUR ROOM CODE:",
            cx, 265f, Palette.TEXT_DIM);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            localRoomCode,
            cx, 230f, Palette.GREEN);
    }

    private void drawAwaitingIp(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "ENTER ROOM CODE   (7 CHARS — BACKSPACE TO ERASE, ENTER TO JOIN, ESC TO CANCEL)",
            cx, 540f, Palette.TEXT_DIM);

        // Input box — sized for 6 characters
        float w = 320f, h = 64f;
        float x = cx - w * 0.5f, y = 420f;
        UIDraw.fill(batch, pixel, Palette.SURFACE, x, y, w, h);
        UIDraw.border(batch, pixel, Palette.BLUE, x, y, w, h, 2f);

        boolean cursorOn = ((int) (clock * 2f)) % 2 == 0;
        // Space out the characters so they read like a code
        StringBuilder spaced = new StringBuilder();
        for (int i = 0; i < codeBuffer.length(); i++) {
            if (i > 0) spaced.append(' ');
            spaced.append(codeBuffer.charAt(i));
        }
        if (cursorOn) { if (spaced.length() > 0) spaced.append(' '); spaced.append('_'); }
        body.setColor(Palette.TEXT);
        body.draw(batch, spaced.toString(), x + 16f, y + 40f);

        // Show local-test code so devs can test with two instances on one machine
        String localCode = RoomCode.encode("127.0.0.1");
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "LOCAL TESTING CODE: " + localCode,
            cx, 340f, Palette.TEXT_DIM);
    }

    private void drawHostingWait(SpriteBatch batch, Texture pixel, BitmapFont body, BitmapFont title, float cx) {
        boolean blink = ((int) (clock * 1.6f)) % 2 == 0;
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "WAITING FOR OPPONENT" + (blink ? " ..." : "    "),
            cx, 510f, Palette.RED);

        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "SHARE YOUR ROOM CODE:",
            cx, 460f, Palette.TEXT_DIM);

        // Display the room code large and prominent
        title.getData().setScale(3.2f);
        UIDraw.centered(batch, title, context.getGlyphLayout(),
            localRoomCode, cx, 400f, Palette.GREEN);
        title.getData().setScale(2.2f); // restore scale set in render()

        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "ESC TO CANCEL", cx, 310f, Palette.TEXT_DIM);
    }

    private void drawConnecting(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        boolean blink = ((int) (clock * 1.6f)) % 2 == 0;
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "CONNECTING" + (blink ? " ..." : "    "),
            cx, 480f, Palette.BLUE);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "ESC TO CANCEL", cx, 320f, Palette.TEXT_DIM);
    }

    private void drawLobby(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        // Two slot panels
        drawSlot(batch, pixel, body, cx - 280f, 380f,
            isHost ? "P1 (YOU)" : "P1 (HOST)",
            isHost ? context.getSession().getLocalName() : remoteName,
            isHost ? localReady : remoteReady,
            Palette.RED);
        drawSlot(batch, pixel, body, cx +  40f, 380f,
            isHost ? "P2 (OPPONENT)" : "P2 (YOU)",
            isHost ? remoteName : context.getSession().getLocalName(),
            isHost ? remoteReady : localReady,
            Palette.BLUE);

        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "PRESS R TO TOGGLE READY", cx, 320f, Palette.TEXT_DIM);

        if (localReady && remoteReady) {
            String hint = isHost
                ? "BOTH READY -- PRESS ENTER TO START"
                : "BOTH READY -- WAITING FOR HOST TO START";
            UIDraw.centered(batch, body, context.getGlyphLayout(),
                hint, cx, 280f, Palette.GREEN);
        }
    }

    private void drawError(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "ERROR: " + (errorText == null ? "unknown" : errorText.toUpperCase()),
            cx, 480f, Palette.RED);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "PRESS ESC OR ENTER TO RETURN",
            cx, 380f, Palette.TEXT_DIM);
    }

    private void drawSquareBtn(SpriteBatch batch, Texture pixel, BitmapFont font,
                               String text, float x, float y, Color accent) {
        float w = 160f, h = 90f;
        UIDraw.fill(batch, pixel, accent, 0.08f, x, y, w, h);
        UIDraw.border(batch, pixel, accent, x, y, w, h, 2f);
        UIDraw.centered(batch, font, context.getGlyphLayout(),
            text, x + w * 0.5f, y + 56f, accent);
    }

    private void drawSlot(SpriteBatch batch, Texture pixel, BitmapFont font,
                          float x, float y, String role, String name, boolean ready, Color accent) {
        float w = 240f, h = 160f;
        UIDraw.fill(batch, pixel, Palette.SURFACE, x, y, w, h);
        UIDraw.border(batch, pixel, ready ? Palette.GREEN : accent, x, y, w, h, ready ? 2f : 1f);
        UIDraw.fill(batch, pixel, accent, x, y + h - 3f, w, 3f);

        font.setColor(Palette.TEXT_DIM);
        font.draw(batch, role, x + 16f, y + h - 18f);
        font.setColor(Palette.TEXT);
        font.draw(batch, name == null ? "?" : name, x + 16f, y + h - 50f);

        font.setColor(ready ? Palette.GREEN : Palette.TEXT_DIM);
        font.draw(batch, ready ? "[ READY ]" : "[ NOT READY ]", x + 16f, y + 28f);
    }

    private String bottomLeftHint() {
        return switch (phase) {
            case IDLE         -> "H = HOST    J = JOIN    ESC = BACK";
            case AWAITING_IP  -> "TYPE HOST IP    ENTER = CONNECT    ESC = CANCEL";
            case HOSTING_WAIT -> "WAITING FOR CLIENT...";
            case CONNECTING   -> "CONNECTING...";
            case LOBBY        -> "R = TOGGLE READY    " + (isHost ? "ENTER = START    " : "") + "ESC = DISCONNECT";
            case ERROR        -> "ESC = BACK TO LOBBY";
        };
    }

    private String bottomRightHint() {
        return switch (phase) {
            case LOBBY -> "PORT " + Protocol.DEFAULT_PORT + "    ROLE: " + (isHost ? "HOST" : "CLIENT");
            default    -> "PORT " + Protocol.DEFAULT_PORT;
        };
    }

    // ── input handling ──────────────────────────────────────────────────

    private void onKeyPressed(int key) {
        if (key == Input.Keys.ESCAPE) {
            handleEscape();
            return;
        }
        switch (phase) {
            case IDLE -> {
                if (key == Input.Keys.H) startHosting();
                else if (key == Input.Keys.J) startJoining();
            }
            case AWAITING_IP -> {
                if (key == Input.Keys.ENTER) confirmIp();
                else if (key == Input.Keys.BACKSPACE && !codeBuffer.isEmpty())
                    codeBuffer = codeBuffer.substring(0, codeBuffer.length() - 1);
            }
            case LOBBY -> {
                if (key == Input.Keys.R) toggleReady();
                else if (key == Input.Keys.ENTER && isHost && localReady && remoteReady) startMatch();
            }
            case ERROR -> {
                if (key == Input.Keys.ENTER) phase = Phase.IDLE;
            }
            default -> {}
        }
    }

    private void onCharTyped(char ch) {
        if (phase != Phase.AWAITING_IP) return;
        if (codeBuffer.length() >= RoomCode.LENGTH) return;
        if (RoomCode.isValidChar(ch)) {
            codeBuffer += Character.toUpperCase(ch);
        }
    }

    private void handleEscape() {
        switch (phase) {
            case IDLE -> game.openMenu();
            case ERROR -> phase = Phase.IDLE;
            default -> {
                if (peer != null) {
                    peer.send(Protocol.BYE);
                    peer.close();
                    peer = null;
                }
                peerRouter = null;
                phase = Phase.IDLE;
                localReady = remoteReady = false;
            }
        }
    }

    private void startHosting() {
        isHost = true;
        phase = Phase.HOSTING_WAIT;
        peerRouter = new PeerRouter();
        peerRouter.setDelegate(new PeerListener());
        peer = new NetHost(Protocol.DEFAULT_PORT, peerRouter);
        peer.start();
    }

    private void startJoining() {
        codeBuffer = "";   // fresh entry every time
        phase = Phase.AWAITING_IP;
    }

    private void confirmIp() {
        if (codeBuffer.length() != RoomCode.LENGTH) return;
        String ip = RoomCode.decode(codeBuffer);
        if (ip == null) {
            errorText = "invalid room code";
            phase = Phase.ERROR;
            return;
        }
        remoteAddress = ip;
        isHost = false;
        phase = Phase.CONNECTING;
        peerRouter = new PeerRouter();
        peerRouter.setDelegate(new PeerListener());
        peer = new NetClient(remoteAddress, Protocol.DEFAULT_PORT, peerRouter);
        peer.start();
    }

    private void toggleReady() {
        localReady = !localReady;
        if (peer != null) peer.send(Protocol.READY + " " + (localReady ? "1" : "0"));
    }

    private void startMatch() {
        if (isHost && peer != null) peer.send(Protocol.START);
        context.getSession().setRemoteName(remoteName);
        context.getSession().setNetPeer(peer, isHost, peerRouter);
        peer = null;       // ownership transferred to GameSession
        peerRouter = null;
        game.openNetMatch();
    }

    // ── peer listener: every callback runs on the GL thread ─────────────

    private final class PeerListener implements NetPeer.Listener {
        @Override
        public void onConnected() {
            phase = Phase.LOBBY;
            // Send our HELLO with a default name (UI for editing comes later).
            String name = isHost ? "P1" : "P2";
            context.getSession().setLocalName(name);
            if (peer != null) peer.send(Protocol.HELLO + " " + name);
        }

        @Override
        public void onMessage(String line) {
            if (line.startsWith(Protocol.HELLO + " ")) {
                remoteName = line.substring(Protocol.HELLO.length() + 1);
            } else if (line.startsWith(Protocol.READY + " ")) {
                remoteReady = "1".equals(line.substring(Protocol.READY.length() + 1));
            } else if (line.equals(Protocol.START)) {
                if (!isHost) startMatch(); // client follows host's lead
            } else if (line.equals(Protocol.BYE)) {
                if (peer != null) { peer.close(); peer = null; }
                phase = Phase.IDLE;
                localReady = remoteReady = false;
            }
        }

        @Override
        public void onDisconnected() {
            if (phase == Phase.LOBBY || phase == Phase.HOSTING_WAIT || phase == Phase.CONNECTING) {
                errorText = "opponent disconnected";
                phase = Phase.ERROR;
            }
            if (peer != null) { peer.close(); peer = null; }
            localReady = remoteReady = false;
        }

        @Override
        public void onError(String reason) {
            errorText = reason;
            phase = Phase.ERROR;
            if (peer != null) { peer.close(); peer = null; }
        }
    }

    private final class InputHandler extends InputAdapter {
        @Override public boolean keyDown(int keycode)   { onKeyPressed(keycode); return true; }
        @Override public boolean keyTyped(char ch)      { onCharTyped(ch); return true; }
    }

    /** Find the first non-loopback IPv4 address — best-effort. */
    private static String discoverLocalIp() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') < 0) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}
