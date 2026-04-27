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
import io.github.some_example_name.network.Protocol;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Phase 1 lobby: pick HOST or JOIN, exchange HELLO, both READY, host hits ENTER to launch.
 * Phase 2 will swap the placeholder match transition for an actual networked duel.
 */
public final class MultiplayerLobbyScreen extends BaseScreen {

    private enum Phase { IDLE, HOSTING_WAIT, CONNECTING, LOBBY, ERROR }

    private final InputHandler input = new InputHandler();
    private Phase phase = Phase.IDLE;
    private float clock;

    private NetPeer peer;
    private boolean isHost;
    private String localIpHint;
    private String remoteAddress = "127.0.0.1";

    private boolean localReady, remoteReady;
    private String  remoteName = "?";
    private String  errorText;

    public MultiplayerLobbyScreen(Main game) {
        super(game);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
        localIpHint = discoverLocalIp();
        // Reset state in case we re-enter the screen.
        phase = Phase.IDLE;
        clock = 0f;
        localReady = remoteReady = false;
        remoteName = "?";
        errorText = null;
        if (peer != null) { peer.close(); peer = null; }
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
            case HOSTING_WAIT  -> drawHostingWait(batch, pixel, body, cx);
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
            "YOUR LOCAL IP: " + (localIpHint == null ? "?" : localIpHint),
            cx, 220f, Palette.TEXT_DIM);
    }

    private void drawHostingWait(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        boolean blink = ((int) (clock * 1.6f)) % 2 == 0;
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "WAITING FOR OPPONENT" + (blink ? " ..." : "    "),
            cx, 480f, Palette.RED);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "TELL THEM TO JOIN:  " + (localIpHint == null ? "?" : localIpHint)
                + "   PORT  " + Protocol.DEFAULT_PORT,
            cx, 420f, Palette.TEXT);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "ESC TO CANCEL", cx, 320f, Palette.TEXT_DIM);
    }

    private void drawConnecting(SpriteBatch batch, Texture pixel, BitmapFont body, float cx) {
        boolean blink = ((int) (clock * 1.6f)) % 2 == 0;
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "CONNECTING TO " + remoteAddress + (blink ? " ..." : "    "),
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
                phase = Phase.IDLE;
                localReady = remoteReady = false;
            }
        }
    }

    private void startHosting() {
        isHost = true;
        phase = Phase.HOSTING_WAIT;
        peer = new NetHost(Protocol.DEFAULT_PORT, new PeerListener());
        peer.start();
    }

    private void startJoining() {
        // System dialog — easiest IP entry on desktop without a TextField rig.
        Gdx.input.getTextInput(new Input.TextInputListener() {
            @Override public void input(String text) {
                if (text == null || text.trim().isEmpty()) return;
                remoteAddress = text.trim();
                isHost = false;
                phase = Phase.CONNECTING;
                peer = new NetClient(remoteAddress, Protocol.DEFAULT_PORT, new PeerListener());
                peer.start();
            }
            @Override public void canceled() { /* stay in IDLE */ }
        }, "JOIN GAME — host IP", remoteAddress, "");
    }

    private void toggleReady() {
        localReady = !localReady;
        if (peer != null) peer.send(Protocol.READY + " " + (localReady ? "1" : "0"));
    }

    private void startMatch() {
        if (peer != null) peer.send(Protocol.START);
        // Phase 1: handshake proves the connection works. The networked
        // match itself lands in Phase 2 — for now close the socket and
        // route both peers through the existing single-player flow so we
        // validate lobby -> match transition end-to-end.
        if (peer != null) { peer.close(); peer = null; }
        context.getSession().setRemoteName(remoteName);
        game.openLoadout();
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
        @Override public boolean keyDown(int keycode) { onKeyPressed(keycode); return true; }
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
