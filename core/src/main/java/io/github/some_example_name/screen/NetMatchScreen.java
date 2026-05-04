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
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.some_example_name.Main;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;
import io.github.some_example_name.core.GameSession;
import io.github.some_example_name.network.GameConnection;
import io.github.some_example_name.network.PacketType;
import io.github.some_example_name.world.ImpactParticle3D;
import io.github.some_example_name.world.MatchWorld3D;

/**
 * Server-authoritative networked match screen.
 *
 * <p>Both players (P1 and P2) are pure clients.  The authoritative
 * {@link io.github.some_example_name.server.GameServer} runs all physics and
 * broadcasts STATE at ~30 Hz.  This screen only draws, dead-reckons the ball
 * between snapshots, and sends input events (SERVE / HIT) to the server.</p>
 *
 * <h3>Coordinate convention</h3>
 * <p>World coordinates are received as-is from the server (no axis flip).
 * P1's camera sits at +z and P2's camera sits at −z so both look at the
 * same table; the camera perspective naturally handles the left/right mirror.</p>
 *
 * <h3>Active-player encoding</h3>
 * <ul>
 *   <li>{@code activePlayer == 0} — ball in transit, nobody acts</li>
 *   <li>{@code activePlayer == 1} — P1 must serve or return</li>
 *   <li>{@code activePlayer == 2} — P2 must return</li>
 * </ul>
 * Each client checks {@code activePlayer == myPlayerNumber} to decide whether
 * to show the "your turn" prompt and accept click input.
 */
public final class NetMatchScreen extends BaseScreen implements GameConnection.Listener {

    // ── Session state ─────────────────────────────────────────────────────────

    private GameConnection conn;
    private int            playerNumber; // 1 or 2

    // ── Server state (updated on every STATE packet) ───────────────────────────

    private final Vector3 snapBallPos  = new Vector3();
    private final Vector3 snapBallVel  = new Vector3();
    private float   snapAge; // seconds since last STATE arrived
    private boolean ballVisible;
    private int     activePlayer;      // 0/1/2
    private int     p1lives = GameConfig.DEFAULT_LIVES;
    private int     p2lives = GameConfig.DEFAULT_LIVES;

    // ── Dead-reckoned ball ─────────────────────────────────────────────────────

    private final Vector3 renderedBallPos = new Vector3();

    // ── Match end ─────────────────────────────────────────────────────────────

    private boolean matchOver;
    private int     winnerPlayer;  // 1 or 2

    // ── Connection ────────────────────────────────────────────────────────────

    private boolean disconnected;
    private boolean waitingForOpponent; // server sent WAITING; P2 not yet connected

    // ── 3D rendering ─────────────────────────────────────────────────────────

    private PerspectiveCamera camera3D;
    private ModelBatch        modelBatch;
    private Environment       environment;
    private Model             tableModel, netModel, ballModel, floorModel;
    private ModelInstance     tableInstance, netInstance, ballInstance, floorInstance;

    // ── Fixed camera ──────────────────────────────────────────────────────────

    private final Vector3 cameraTarget = new Vector3();
    private final Vector3 cameraOffset = new Vector3();

    // ── Cursor projection ─────────────────────────────────────────────────────

    private final Vector3 worldToScreen = new Vector3();
    private final Vector3 screenToWorld = new Vector3();
    private final Vector2 cursorOnTable = new Vector2();

    // ── Click hit detection ───────────────────────────────────────────────────

    private static final float CLICK_HIT_PADDING = 3.5f;
    private final Vector3 hitPoint = new Vector3();

    // ── Local particles (cosmetic — spawned on SFX_TABLE events) ─────────────

    private final Array<ImpactParticle3D> particles = new Array<>();
    private final Pool<ImpactParticle3D> particlePool = new Pool<>() {
        @Override protected ImpactParticle3D newObject() { return new ImpactParticle3D(); }
    };
    private final Vector3 tmpParticleVel = new Vector3();

    // ── Audio ─────────────────────────────────────────────────────────────────

    private Sound paddleHitSfx;
    private Sound tableHitSfx;
    private Music backgroundMusic;

    // ── Input ────────────────────────────────────────────────────────────────

    private NetInput netInput;

    // ── Constructor ───────────────────────────────────────────────────────────

    public NetMatchScreen(Main game) { super(game); }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void show() {
        GameSession session = context.getSession();
        conn         = session.getGameConnection();
        playerNumber = session.getPlayerNumber();

        // Redirect live callbacks to this screen.
        if (conn != null) conn.setListener(this);

        // Camera: P1 at +z looking toward −z, P2 at −z looking toward +z.
        boolean isP1 = (playerNumber == 1);
        cameraTarget.set(0f, MatchWorld3D.TABLE_TOP_Y, 0f);
        cameraOffset.set(0f, 2.5f, isP1 ? 11f : -11f);

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

        // If server hasn't sent anything yet, we show "waiting for opponent".
        waitingForOpponent = true;

        // Greet the server.
        if (conn != null) conn.sendHello();
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

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(float delta) {
        if (netInput.consumeMenu()) { returnToMenu(); return; }

        updateSimulation(delta);
        unprojectCursorOntoTable();

        // 3-D pass
        ScreenUtils.clear(0.02f, 0.05f, 0.07f, 1f, true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        modelBatch.begin(camera3D);
        modelBatch.render(floorInstance,  environment);
        modelBatch.render(tableInstance,  environment);
        modelBatch.render(netInstance,    environment);
        if (ballVisible) modelBatch.render(ballInstance, environment);
        modelBatch.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        // 2-D HUD pass
        context.getViewport().apply(true);
        SpriteBatch batch = context.getBatch();
        batch.setProjectionMatrix(context.getViewport().getCamera().combined);
        batch.begin();
        drawParticles(batch);
        drawCursorMarker(batch);
        drawHud(batch);
        if (matchOver)    drawOutcomeOverlay(batch);
        if (disconnected) drawDisconnectOverlay(batch);
        batch.end();
    }

    // ── Simulation update ─────────────────────────────────────────────────────

    private void updateSimulation(float delta) {
        snapAge += delta;

        // Dead-reckon ball position using server snapshot + gravity.
        if (ballVisible) {
            float t  = snapAge;
            float ex = snapBallPos.x + snapBallVel.x * t;
            float ey = snapBallPos.y + snapBallVel.y * t
                       - 0.5f * MatchWorld3D.GRAVITY * t * t;
            float ez = snapBallPos.z + snapBallVel.z * t;
            ey = Math.max(ey, MatchWorld3D.TABLE_TOP_Y + MatchWorld3D.BALL_RADIUS);
            renderedBallPos.set(ex, ey, ez);
            ballInstance.transform.setToTranslation(ex, ey, ez);
        }

        // Update local particles.
        for (int i = particles.size - 1; i >= 0; i--) {
            if (particles.get(i).update(delta)) {
                particlePool.free(particles.removeIndex(i));
            }
        }

        // Process click input.
        if (netInput.consumeClick() && !matchOver && !disconnected) {
            handleClick();
        }
    }

    private void handleClick() {
        if (activePlayer != playerNumber) return; // not my turn

        // Either player can serve when it's their turn and ball is hidden.
        if (!ballVisible) {
            if (conn != null) conn.sendServe();
            return;
        }

        // Otherwise, hit the in-flight ball.
        Ray ray = camera3D.getPickRay(netInput.lastClickX, netInput.lastClickY);
        tryHit(ray);
    }

    private void tryHit(Ray ray) {
        float hitRadius = MatchWorld3D.BALL_RADIUS * CLICK_HIT_PADDING;
        if (!Intersector.intersectRaySphere(ray, renderedBallPos, hitRadius, hitPoint)) return;

        Vector3 offset = hitPoint.cpy().sub(renderedBallPos);
        float ndx   = MathUtils.clamp(offset.x / hitRadius, -1f, 1f);
        float ndy   = MathUtils.clamp(offset.y / hitRadius, -1f, 1f);
        float power = (float) Math.sqrt(ndx * ndx + ndy * ndy);

        // P1 returns toward −z (P2's side); P2 returns toward +z (P1's side).
        float zDir = (playerNumber == 1) ? -1f : 1f;
        float vx = ndx  * 3.2f;
        float vy = 5.0f + ndy * 2.0f;
        float vz = zDir * (7.5f + power * 2.0f);

        if (conn != null) {
            conn.sendHit(vx, vy, vz);
            paddleHitSfx.play(0.7f); // optimistic local sound
        }
    }

    // ── GameConnection.Listener (GL thread) ───────────────────────────────────

    @Override
    public void onWaiting() {
        waitingForOpponent = true;
    }

    @Override
    public void onState(float px, float py, float pz,
                        float vx, float vy, float vz,
                        int p1l, int p2l,
                        boolean visible, int ap) {
        waitingForOpponent = false;
        snapBallPos.set(px, py, pz);
        snapBallVel.set(vx, vy, vz);
        snapAge      = 0f;
        ballVisible  = visible;
        activePlayer = ap;
        p1lives      = p1l;
        p2lives      = p2l;
    }

    @Override
    public void onGameOver(int winner) {
        matchOver    = true;
        winnerPlayer = winner;
    }

    @Override
    public void onSfx(int sfxType) {
        if (sfxType == PacketType.SFX_PADDLE) {
            paddleHitSfx.play(0.7f);
        } else if (sfxType == PacketType.SFX_TABLE) {
            tableHitSfx.play(0.6f);
            // Spawn cosmetic bounce particles at the ball's current rendered position.
            if (ballVisible) spawnBounceSparks(renderedBallPos.x,
                                               renderedBallPos.y,
                                               renderedBallPos.z);
        }
    }

    @Override
    public void onDisconnected() {
        disconnected = true;
        shutdownConn();
    }

    @Override
    public void onError(String reason) {
        disconnected = true;
        shutdownConn();
    }

    @Override
    public void onBye() {
        disconnected = true;
        shutdownConn();
    }

    // ── Particles ─────────────────────────────────────────────────────────────

    private void spawnBounceSparks(float x, float y, float z) {
        for (int i = 0; i < 10; i++) {
            float angle = MathUtils.random(MathUtils.PI2);
            float speed = 1.2f + MathUtils.random(1.8f);
            tmpParticleVel.set(MathUtils.cos(angle) * speed,
                               1.5f + MathUtils.random(1.5f),
                               MathUtils.sin(angle) * speed);
            ImpactParticle3D p = particlePool.obtain();
            p.init(x, y, z, tmpParticleVel, 0.35f + MathUtils.random(0.25f));
            particles.add(p);
        }
    }

    // ── Camera ────────────────────────────────────────────────────────────────

    private void applyCameraTransform() {
        camera3D.position.set(cameraTarget).add(cameraOffset);
        camera3D.lookAt(cameraTarget);
        camera3D.up.set(0f, 1f, 0f);
        camera3D.update();
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

    // ── Drawing ───────────────────────────────────────────────────────────────

    private void drawParticles(SpriteBatch batch) {
        for (ImpactParticle3D p : particles) {
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
        // "My" lives are always on the left; opponent's on the right.
        int myLives   = (playerNumber == 1) ? p1lives : p2lives;
        int oppLives  = (playerNumber == 1) ? p2lives : p1lives;
        String oppName = context.getSession().getRemoteName();

        context.getBodyFont().setColor(Palette.TEXT);
        context.getBodyFont().draw(batch, "YOU  " + myLives,
            GameConfig.HUD_PADDING, 680f);
        context.getBodyFont().draw(batch, oppName + "  " + oppLives,
            GameConfig.WORLD_WIDTH - 260f, 680f);

        if (!matchOver && !disconnected) {
            drawCentered(batch, context.getBodyFont(), deriveStatus(),
                GameConfig.WORLD_WIDTH * 0.5f, 134f, Palette.RED);
            drawCentered(batch, context.getBodyFont(),
                "Click to serve / return.  ESC = menu.",
                GameConfig.WORLD_WIDTH * 0.5f, 102f, Palette.TEXT_DIM);
        }
    }

    private String deriveStatus() {
        if (waitingForOpponent) return "Waiting for opponent to connect...";
        if (!ballVisible) {
            if (activePlayer == playerNumber) return "Click anywhere to serve.";
            return "Waiting for opponent to serve...";
        }
        if (activePlayer == playerNumber) return "Your turn — click the ball to return!";
        return "Ball heading your way — get ready!";
    }

    private void drawOutcomeOverlay(SpriteBatch batch) {
        boolean localWon = (winnerPlayer == playerNumber);
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void returnToMenu() {
        if (conn != null) conn.sendBye();
        shutdownConn();
        game.openMenu();
    }

    private void shutdownConn() {
        if (conn != null) { conn.close(); conn = null; }
        context.getSession().clearMultiplayer();
    }

    // ── Input ────────────────────────────────────────────────────────────────

    private static final class NetInput extends InputAdapter {
        int lastClickX, lastClickY, lastMouseX, lastMouseY;
        private boolean clickReq, menuReq;

        boolean consumeClick()  { boolean v = clickReq;  clickReq  = false; return v; }
        boolean consumeMenu()   { boolean v = menuReq;   menuReq   = false; return v; }

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
    }
}
