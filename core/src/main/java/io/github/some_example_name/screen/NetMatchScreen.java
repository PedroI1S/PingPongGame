package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.some_example_name.Main;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;
import io.github.some_example_name.core.GameSession;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.network.NetPeer;
import io.github.some_example_name.network.Protocol;
import io.github.some_example_name.world.ImpactParticle3D;
import io.github.some_example_name.world.MatchWorld3D;

import java.util.Locale;

/**
 * Phase 2 networked match.
 *
 * <p>Host role: runs the authoritative {@link MatchWorld3D}, accepts click input,
 * broadcasts STATE at ~30 Hz, and relays SFX events.</p>
 *
 * <p>Client role: receives STATE snapshots and dead-reckons ball position between
 * them, sends HIT impulses on click, plays back SFX on demand.</p>
 *
 * <p>Camera is flipped 180° for the client so they see the table from their own end.</p>
 */
public final class NetMatchScreen extends BaseScreen implements NetPeer.Listener {

    // ── network ──────────────────────────────────────────────────────────────
    private NetPeer peer;
    private boolean isHost;

    // ── host-only ────────────────────────────────────────────────────────────
    private MatchWorld3D world;
    private static final float STATE_INTERVAL = 1f / 30f;
    private float stateTimer;
    private boolean skipPaddleSfxBroadcast;
    private boolean outcomeReported;

    // ── client-only: latest snapshot + dead-reckoning ────────────────────────
    private final Vector3 clientBallPos     = new Vector3();
    private final Vector3 clientBallVel     = new Vector3();
    private final Vector3 renderedBallPos   = new Vector3();
    private float   clientTimeSinceState;
    private boolean clientBallVisible;
    private boolean clientCanHit;
    private int     clientP1Lives  = GameConfig.DEFAULT_LIVES;
    private int     clientP2Lives  = GameConfig.DEFAULT_LIVES;
    private boolean clientMatchOver;
    private int     clientOutcomeCode; // 1 = host wins, 2 = client wins

    // ── 3D rendering (shared) ────────────────────────────────────────────────
    private PerspectiveCamera camera3D;
    private ModelBatch        modelBatch;
    private Environment       environment;
    private Model         tableModel, netModel, ballModel, floorModel;
    private ModelInstance tableInstance, netInstance, ballInstance, floorInstance;

    // ── camera control ───────────────────────────────────────────────────────
    private static final float CAM_SPEED    = 6f;
    private static final float CAM_MIN_DIST = 6f;
    private static final float CAM_MAX_DIST = 18f;
    private final Vector3 cameraTarget = new Vector3();
    private final Vector3 cameraOffset = new Vector3();

    // ── project/unproject helpers ────────────────────────────────────────────
    private final Vector3 worldToScreen = new Vector3();
    private final Vector3 screenToWorld = new Vector3();
    private final Vector2 cursorOnTable = new Vector2();

    // ── input / audio ────────────────────────────────────────────────────────
    private NetInput netInput;
    private Sound    paddleHitSfx;
    private Sound    tableHitSfx;
    private Music    backgroundMusic;

    // ── connection state ─────────────────────────────────────────────────────
    private boolean disconnected;

    // ── click detection ──────────────────────────────────────────────────────
    private static final float CLICK_HIT_PADDING = 3.5f;
    private final Vector3 hitPoint = new Vector3();

    public NetMatchScreen(Main game) {
        super(game);
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void show() {
        GameSession session = context.getSession();
        peer   = session.getNetPeer();
        isHost = session.isHost();

        // Redirect the live socket's callbacks to this screen.
        session.getPeerRouter().setDelegate(this);

        if (isHost) {
            world = new MatchWorld3D(session.buildMatchConfig(), session.getRandom());
            world.setNetworkMode(true);
        }

        // Camera — client sits at the far (-z) end to see their side near.
        cameraTarget.set(0f, MatchWorld3D.TABLE_TOP_Y, 0f);
        cameraOffset.set(0f, 2.5f, isHost ? 11f : -11f);

        camera3D = new PerspectiveCamera(60f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera3D.near = 0.1f;
        camera3D.far  = 100f;
        applyCameraTransform();

        modelBatch  = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.45f, 0.5f, 0.55f, 1f));
        environment.add(new DirectionalLight().set(0.9f, 0.95f, 1f, -0.4f, -0.8f, -0.3f));
        buildModels();

        paddleHitSfx    = context.getAssets().getBallHitSfx();
        tableHitSfx     = context.getAssets().getTableHitSfx();
        backgroundMusic = context.getAssets().getBackgroundMusic();
        backgroundMusic.setLooping(true);
        backgroundMusic.setVolume(0.25f);
        backgroundMusic.play();

        netInput = new NetInput();
        Gdx.input.setInputProcessor(netInput);
    }

    private void buildModels() {
        ModelBuilder mb    = new ModelBuilder();
        long         attrs = Usage.Position | Usage.Normal;

        tableModel = mb.createBox(
            MatchWorld3D.TABLE_HALF_WIDTH * 2f, 0.2f, MatchWorld3D.TABLE_HALF_LENGTH * 2f,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("1A6E5F"))), attrs);
        tableInstance = new ModelInstance(tableModel);
        tableInstance.transform.setToTranslation(0f, MatchWorld3D.TABLE_TOP_Y - 0.1f, 0f);

        netModel = mb.createBox(
            MatchWorld3D.TABLE_HALF_WIDTH * 2f + 0.6f, MatchWorld3D.NET_HEIGHT, 0.06f,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("E8C06A"))), attrs);
        netInstance = new ModelInstance(netModel);
        netInstance.transform.setToTranslation(
            0f, MatchWorld3D.TABLE_TOP_Y + MatchWorld3D.NET_HEIGHT * 0.5f, 0f);

        ballModel = mb.createSphere(
            MatchWorld3D.BALL_RADIUS * 2f, MatchWorld3D.BALL_RADIUS * 2f,
            MatchWorld3D.BALL_RADIUS * 2f, 16, 16,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("F4FBFF"))), attrs);
        ballInstance = new ModelInstance(ballModel);

        floorModel = mb.createBox(60f, 0.4f, 60f,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("0E2026"))), attrs);
        floorInstance = new ModelInstance(floorModel);
        floorInstance.transform.setToTranslation(0f, -0.2f, 0f);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        if (backgroundMusic != null) backgroundMusic.stop();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (camera3D != null) {
            camera3D.viewportWidth  = width;
            camera3D.viewportHeight = height;
            camera3D.update();
        }
    }

    @Override
    public void dispose() {
        if (modelBatch  != null) modelBatch.dispose();
        if (tableModel  != null) tableModel.dispose();
        if (netModel    != null) netModel.dispose();
        if (ballModel   != null) ballModel.dispose();
        if (floorModel  != null) floorModel.dispose();
    }

    // ── render ────────────────────────────────────────────────────────────────

    @Override
    public void render(float delta) {
        if (netInput.consumeMenu()) { returnToMenu(); return; }

        updateCameraControl(delta);

        if (isHost) updateHost(delta);
        else        updateClient(delta);

        unprojectCursorOntoTable();

        // 3-D pass
        ScreenUtils.clear(0.02f, 0.05f, 0.07f, 1f, true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        modelBatch.begin(camera3D);
        modelBatch.render(floorInstance, environment);
        modelBatch.render(tableInstance, environment);
        modelBatch.render(netInstance,   environment);
        boolean ballVis = isHost ? world.isBallVisible() : clientBallVisible;
        if (ballVis) modelBatch.render(ballInstance, environment);
        modelBatch.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        // 2-D HUD pass
        context.getViewport().apply(true);
        SpriteBatch batch = context.getBatch();
        batch.setProjectionMatrix(context.getViewport().getCamera().combined);
        batch.begin();
        drawCursorMarker(batch);
        if (isHost) drawParticles(batch);
        drawHud(batch);
        boolean matchOver = isHost ? world.isMatchOver() : clientMatchOver;
        if (matchOver)    drawOutcomeOverlay(batch);
        if (disconnected) drawDisconnectOverlay(batch);
        batch.end();
    }

    // ── host update ───────────────────────────────────────────────────────────

    private void updateHost(float delta) {
        if (netInput.consumeClick()) {
            if (world.getPhase() == MatchWorld3D.Phase.PREPARE_SERVE) {
                world.tryPlayerServe(); // click anywhere to serve
            } else {
                Ray ray = camera3D.getPickRay(netInput.lastClickX, netInput.lastClickY);
                world.tryHitBall(ray);  // click ball during INCOMING to return
            }
        }

        world.update(delta);

        if (world.consumePaddleHitEvent()) {
            paddleHitSfx.play(0.7f);
            if (!skipPaddleSfxBroadcast) safeSend(Protocol.SFX + " P");
            skipPaddleSfxBroadcast = false;
        }
        if (world.consumeTableBounceEvent()) {
            tableHitSfx.play(0.6f);
            safeSend(Protocol.SFX + " T");
        }

        if (world.isBallVisible()) {
            Vector3 p = world.getBallPos();
            ballInstance.transform.setToTranslation(p.x, p.y, p.z);
        }

        // Broadcast state at ~30 Hz.
        stateTimer -= delta;
        if (stateTimer <= 0f) {
            stateTimer = STATE_INTERVAL;
            broadcastState();
        }

        if (world.isMatchOver() && !outcomeReported) {
            outcomeReported = true;
            int code = world.getOutcome() == MatchOutcome.PLAYER_WIN ? 1 : 2;
            safeSend(Protocol.GAME_OVER + " " + code);
        }
    }

    private void broadcastState() {
        if (peer == null) return;
        Vector3 p = world.getBallPos();
        Vector3 v = world.getBallVel();
        peer.send(String.format(Locale.US,
            "%s %.3f %.3f %.3f %.3f %.3f %.3f %d %d %d %d",
            Protocol.STATE,
            p.x, p.y, p.z,
            v.x, v.y, v.z,
            world.getPlayerLives(), world.getBotLives(),
            world.isBallVisible()   ? 1 : 0,
            world.isClientCanHit()  ? 1 : 0));
    }

    // ── client update ─────────────────────────────────────────────────────────

    private void updateClient(float delta) {
        clientTimeSinceState += delta;

        if (clientBallVisible) {
            // Dead-reckoning: extrapolate with gravity between snapshots.
            float t  = clientTimeSinceState;
            float ex = clientBallPos.x + clientBallVel.x * t;
            float ey = clientBallPos.y + clientBallVel.y * t
                       - 0.5f * MatchWorld3D.GRAVITY * t * t;
            float ez = clientBallPos.z + clientBallVel.z * t;
            ey = Math.max(ey, MatchWorld3D.TABLE_TOP_Y + MatchWorld3D.BALL_RADIUS);
            renderedBallPos.set(ex, ey, ez);
            ballInstance.transform.setToTranslation(ex, ey, ez);
        }

        if (netInput.consumeClick() && clientCanHit && clientBallVisible && !clientMatchOver) {
            Ray ray = camera3D.getPickRay(netInput.lastClickX, netInput.lastClickY);
            tryClientHit(ray);
        }
    }

    private void tryClientHit(Ray ray) {
        float hitRadius = MatchWorld3D.BALL_RADIUS * CLICK_HIT_PADDING;
        if (!Intersector.intersectRaySphere(ray, renderedBallPos, hitRadius, hitPoint)) return;

        Vector3 offset = hitPoint.cpy().sub(renderedBallPos);
        float ndx   = MathUtils.clamp(offset.x / hitRadius, -1f, 1f);
        float ndy   = MathUtils.clamp(offset.y / hitRadius, -1f, 1f);
        float power = (float) Math.sqrt(ndx * ndx + ndy * ndy);

        // +z is toward the host's end — the correct return direction from client.
        float vx = ndx  * 3.2f;
        float vy = 5.0f + ndy * 2.0f;
        float vz = 7.5f + power * 2.0f;

        safeSend(String.format(Locale.US, "%s %.3f %.3f %.3f", Protocol.HIT, vx, vy, vz));
        paddleHitSfx.play(0.7f);
        clientCanHit = false; // debounce until next STATE confirms the hit was processed
    }

    // ── NetPeer.Listener (GL thread) ──────────────────────────────────────────

    @Override
    public void onConnected() {}

    @Override
    public void onMessage(String line) {
        String[] parts = line.split(" ", -1);
        if (parts.length == 0) return;
        switch (parts[0]) {
            case Protocol.STATE     -> handleState(parts);
            case Protocol.HIT       -> handleHit(parts);
            case Protocol.GAME_OVER -> handleGameOver(parts);
            case Protocol.SFX       -> handleSfx(parts);
            case Protocol.BYE       -> handleBye();
        }
    }

    private void handleState(String[] parts) {
        // STATE bx by bz vx vy vz p1lives p2lives visible clientCanHit  (11 tokens)
        if (parts.length < 11) return;
        try {
            clientBallPos.set(
                Float.parseFloat(parts[1]),
                Float.parseFloat(parts[2]),
                Float.parseFloat(parts[3]));
            clientBallVel.set(
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5]),
                Float.parseFloat(parts[6]));
            clientP1Lives     = Integer.parseInt(parts[7]);
            clientP2Lives     = Integer.parseInt(parts[8]);
            clientBallVisible = "1".equals(parts[9]);
            clientCanHit      = "1".equals(parts[10]);
            clientTimeSinceState = 0f;
        } catch (NumberFormatException ignored) {}
    }

    private void handleHit(String[] parts) {
        // HOST only. HIT vx vy vz
        if (!isHost || parts.length < 4) return;
        try {
            float vx = Float.parseFloat(parts[1]);
            float vy = Float.parseFloat(parts[2]);
            float vz = Float.parseFloat(parts[3]);
            if (world.acceptClientHit(vx, vy, vz)) {
                // Client already played an optimistic sound; skip re-broadcasting SFX P.
                skipPaddleSfxBroadcast = true;
            }
        } catch (NumberFormatException ignored) {}
    }

    private void handleGameOver(String[] parts) {
        // CLIENT only.
        if (parts.length < 2) return;
        try {
            clientMatchOver   = true;
            clientOutcomeCode = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ignored) {}
    }

    private void handleSfx(String[] parts) {
        if (parts.length < 2) return;
        if ("P".equals(parts[1])) paddleHitSfx.play(0.7f);
        else if ("T".equals(parts[1])) tableHitSfx.play(0.6f);
    }

    private void handleBye() {
        disconnected = true;
        shutdownPeer();
    }

    @Override
    public void onDisconnected() {
        disconnected = true;
        shutdownPeer();
    }

    @Override
    public void onError(String reason) {
        disconnected = true;
        shutdownPeer();
    }

    private void shutdownPeer() {
        if (peer != null) { peer.close(); peer = null; }
        context.getSession().clearNetPeer();
    }

    // ── camera ────────────────────────────────────────────────────────────────

    private void applyCameraTransform() {
        camera3D.position.set(cameraTarget).add(cameraOffset);
        camera3D.lookAt(cameraTarget);
        camera3D.up.set(0f, 1f, 0f);
        camera3D.update();
    }

    private void updateCameraControl(float delta) {
        float dx = 0f, dz = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.A)) dx -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.D)) dx += 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) dz -= 1f;
        if (Gdx.input.isKeyPressed(Input.Keys.S)) dz += 1f;
        if (dx != 0f || dz != 0f) {
            float len = (float) Math.sqrt(dx * dx + dz * dz);
            cameraTarget.x += (dx / len) * CAM_SPEED * delta;
            cameraTarget.z += (dz / len) * CAM_SPEED * delta;
            cameraTarget.x = MathUtils.clamp(cameraTarget.x, -8f, 8f);
            cameraTarget.z = MathUtils.clamp(cameraTarget.z, -10f, 14f);
        }
        float scroll = netInput.consumeScroll();
        if (scroll != 0f) {
            float cur = cameraOffset.len();
            float nxt = MathUtils.clamp(cur + scroll * 1.2f, CAM_MIN_DIST, CAM_MAX_DIST);
            cameraOffset.scl(nxt / cur);
        }
        applyCameraTransform();
    }

    private void unprojectCursorOntoTable() {
        screenToWorld.set(netInput.lastMouseX, netInput.lastMouseY, 0f);
        camera3D.unproject(screenToWorld);
        Vector3 origin = camera3D.position;
        float dirY = screenToWorld.y - origin.y;
        if (Math.abs(dirY) < 0.0001f) { cursorOnTable.set(Float.NaN, Float.NaN); return; }
        float t = (MatchWorld3D.TABLE_TOP_Y - origin.y) / dirY;
        if (t <= 0f) { cursorOnTable.set(Float.NaN, Float.NaN); return; }
        cursorOnTable.set(
            origin.x + (screenToWorld.x - origin.x) * t,
            origin.z + (screenToWorld.z - origin.z) * t);
    }

    // ── drawing ───────────────────────────────────────────────────────────────

    private void drawParticles(SpriteBatch batch) {
        for (ImpactParticle3D p : world.getParticles()) {
            worldToScreen.set(p.getPosition());
            camera3D.project(worldToScreen);
            if (worldToScreen.z < 0f || worldToScreen.z > 1f) continue;
            float hudX = worldToScreen.x / Gdx.graphics.getWidth()  * GameConfig.WORLD_WIDTH;
            float hudY = worldToScreen.y / Gdx.graphics.getHeight() * GameConfig.WORLD_HEIGHT;
            float alpha = p.getAlpha();
            float size  = 4f + alpha * 6f;
            batch.setColor(1f, 1f, 0.85f, alpha * 0.85f);
            batch.draw(context.getAssets().getProceduralAssets().getGlow(),
                       hudX - size * 0.5f, hudY - size * 0.5f, size, size);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawCursorMarker(SpriteBatch batch) {
        if (Float.isNaN(cursorOnTable.x)) return;
        worldToScreen.set(cursorOnTable.x, MatchWorld3D.TABLE_TOP_Y, cursorOnTable.y);
        camera3D.project(worldToScreen);
        if (worldToScreen.z < 0f || worldToScreen.z > 1f) return;
        float hudX = worldToScreen.x / Gdx.graphics.getWidth()  * GameConfig.WORLD_WIDTH;
        float hudY = worldToScreen.y / Gdx.graphics.getHeight() * GameConfig.WORLD_HEIGHT;
        float size = 28f;
        batch.setColor(0.45f, 0.95f, 0.85f, 0.55f);
        batch.draw(context.getAssets().getProceduralAssets().getAimRing(),
                   hudX - size * 0.5f, hudY - size * 0.5f, size, size);
        batch.setColor(Color.WHITE);
    }

    private void drawHud(SpriteBatch batch) {
        String remoteName = context.getSession().getRemoteName();
        int myLives    = isHost ? world.getPlayerLives() : clientP2Lives;
        int theirLives = isHost ? world.getBotLives()    : clientP1Lives;

        context.getBodyFont().setColor(Palette.TEXT);
        context.getBodyFont().draw(batch, "YOU  " + myLives,
            GameConfig.HUD_PADDING, 680f);
        context.getBodyFont().draw(batch, remoteName + "  " + theirLives,
            GameConfig.WORLD_WIDTH - 260f, 680f);

        boolean over = isHost ? world.isMatchOver() : clientMatchOver;
        if (!over && !disconnected) {
            String status;
            if (isHost) {
                status = world.getPhase() == MatchWorld3D.Phase.PREPARE_SERVE
                    ? "Click anywhere to serve."
                    : world.getStatusText();
            } else {
                status = deriveClientStatus();
            }
            drawCentered(batch, context.getBodyFont(), status,
                GameConfig.WORLD_WIDTH * 0.5f, 134f, Palette.RED);
            String hint = isHost
                ? "Click to serve / return.  WASD pans  |  scroll zooms  |  ESC = menu."
                : "Click the ball to return.  WASD pans  |  scroll zooms  |  ESC = menu.";
            drawCentered(batch, context.getBodyFont(), hint,
                GameConfig.WORLD_WIDTH * 0.5f, 102f, Palette.TEXT_DIM);
        }
    }

    private String deriveClientStatus() {
        if (!clientBallVisible) return "Waiting for opponent to serve...";
        if (clientCanHit)       return "Your turn — click the ball to return!";
        return "Ball heading your way — get ready!";
    }

    private void drawOutcomeOverlay(SpriteBatch batch) {
        boolean localWon;
        if (isHost) {
            localWon = world.getOutcome() == MatchOutcome.PLAYER_WIN;
        } else {
            localWon = clientOutcomeCode == 2;
        }
        String title    = localWon ? "You win!" : "Opponent wins this round";
        String subtitle = localWon ? "Solid return loop." : "They outclassed you.";
        drawCentered(batch, context.getTitleFont(), title,
            GameConfig.WORLD_WIDTH * 0.5f, 420f, Palette.TEXT);
        drawCentered(batch, context.getBodyFont(), subtitle,
            GameConfig.WORLD_WIDTH * 0.5f, 372f, Palette.TEXT_DIM);
        drawCentered(batch, context.getBodyFont(), "Press ESC to return to the menu.",
            GameConfig.WORLD_WIDTH * 0.5f, 330f, Palette.RED);
    }

    private void drawDisconnectOverlay(SpriteBatch batch) {
        drawCentered(batch, context.getTitleFont(), "Opponent disconnected",
            GameConfig.WORLD_WIDTH * 0.5f, 420f, Palette.RED);
        drawCentered(batch, context.getBodyFont(), "Press ESC to return to the menu.",
            GameConfig.WORLD_WIDTH * 0.5f, 372f, Palette.TEXT_DIM);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void returnToMenu() {
        safeSend(Protocol.BYE);
        shutdownPeer();
        game.openMenu();
    }

    private void safeSend(String line) {
        if (peer != null) peer.send(line);
    }

    // ── input handler ─────────────────────────────────────────────────────────

    private static final class NetInput extends InputAdapter {
        int    lastClickX, lastClickY, lastMouseX, lastMouseY;
        private boolean clickReq, menuReq;
        private float   scrollAcc;

        boolean consumeClick()  { boolean v = clickReq;  clickReq  = false; return v; }
        boolean consumeMenu()   { boolean v = menuReq;   menuReq   = false; return v; }
        float   consumeScroll() { float   v = scrollAcc; scrollAcc = 0f;    return v; }

        @Override public boolean keyDown(int k) {
            if (k == Input.Keys.ESCAPE) { menuReq = true; return true; }
            return false;
        }
        @Override public boolean touchDown(int sx, int sy, int ptr, int btn) {
            lastClickX = sx; lastClickY = sy; clickReq = true; return true;
        }
        @Override public boolean mouseMoved(int sx, int sy) {
            lastMouseX = sx; lastMouseY = sy; return false;
        }
        @Override public boolean scrolled(float ax, float ay) {
            scrollAcc += ay; return true;
        }
    }
}
