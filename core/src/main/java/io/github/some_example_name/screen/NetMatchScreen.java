package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import io.github.some_example_name.Main;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;
import io.github.some_example_name.core.GameSession;
import io.github.some_example_name.model.ItemType;
import io.github.some_example_name.network.GameConnection;
import io.github.some_example_name.network.PacketType;
import io.github.some_example_name.render.ItemPhaseRenderer;
import io.github.some_example_name.render.MatchArenaRenderer;
import io.github.some_example_name.world.FlyState;
import io.github.some_example_name.world.ImpactParticle3D;
import io.github.some_example_name.world.MatchWorld3D;
import java.util.ArrayList;
import java.util.List;

/**
 * Server-authoritative networked match screen.
 *
 * <p>Both players (P1 and P2) are pure clients.  The authoritative
 * {@link io.github.some_example_name.server.GameServer} runs all physics and
 * broadcasts STATE at ~30 Hz.  This screen only draws, dead-reckons the ball
 * between snapshots, and sends {@link PacketType#CLICK} (screen coordinates) to the server.</p>
 */
public final class NetMatchScreen extends BaseScreen implements GameConnection.Listener {

    private GameConnection conn;
    private int            playerNumber;

    private final Vector3 snapBallPos  = new Vector3();
    private final Vector3 snapBallVel  = new Vector3();
    private float   snapAge;
    private boolean ballVisible;
    private int     activePlayer;
    private int     p1lives = GameConfig.DEFAULT_LIVES;
    private int     p2lives = GameConfig.DEFAULT_LIVES;

    private final Vector3 renderedBallPos = new Vector3();

    private boolean matchOver;
    private int     winnerPlayer;

    private boolean disconnected;
    private boolean waitingForOpponent;

    // ── Item phase ─────────────────────────────────────────────────────────────
    private boolean inItemPhase;
    private ItemPhaseRenderer itemPhaseRenderer;
    private final List<ItemType> myItems  = new ArrayList<>();
    private final List<ItemType> oppItems = new ArrayList<>();
    private boolean itemReadySent;

    // ── Flies ──────────────────────────────────────────────────────────────────
    private final List<FlyState> myFlies  = new ArrayList<>();
    private final List<FlyState> oppFlies = new ArrayList<>();

    // ── Punch blur ─────────────────────────────────────────────────────────────
    private float punchTimer;

    // ── Round overlay ──────────────────────────────────────────────────────────
    private String roundOverlayText;
    private float  roundOverlayTimer;

    private MatchArenaRenderer arena;

    private final Array<ImpactParticle3D> particles = new Array<>();
    private final Pool<ImpactParticle3D> particlePool = new Pool<>() {
        @Override protected ImpactParticle3D newObject() { return new ImpactParticle3D(); }
    };
    private final Vector3 tmpParticleVel = new Vector3();
    private static final int MAX_PARTICLES = 64;

    private Sound paddleHitSfx;
    private Sound tableHitSfx;
    private Music backgroundMusic;

    private NetInput netInput;
    private RandomXS128 rng;

    // ── Screen shake ──────────────────────────────────────────────────────────
    /** Seconds remaining on the current shake; 0 when not shaking. */
    private float shakeTime;
    /** Initial duration of the current shake (used to decay intensity). */
    private float shakeDuration;
    /** Peak offset in world units. */
    private float shakeAmplitude;
    /**
     * Previous-frame lives counters — used to detect when the local player
     * just lost a life (big shake) versus when the opponent did.
     */
    private int prevP1Lives = GameConfig.DEFAULT_LIVES;
    private int prevP2Lives = GameConfig.DEFAULT_LIVES;

    public NetMatchScreen(Main game) { super(game); }

    @Override
    public void show() {
        GameSession session = context.getSession();
        conn         = session.getGameConnection();
        playerNumber = session.getPlayerNumber();
        rng          = session.getRandom();

        if (conn != null) conn.setListener(this);

        if (arena == null) {
            arena = new MatchArenaRenderer(playerNumber == 1);
            paddleHitSfx = context.getAssets().getBallHitSfx();
            tableHitSfx  = context.getAssets().getTableHitSfx();
            waitingForOpponent = true;
        }

        arena.ensureInitialized();
        if (itemPhaseRenderer == null) {
            itemPhaseRenderer = new ItemPhaseRenderer();
        }
        backgroundMusic = context.getAssets().getBackgroundMusic();
        backgroundMusic.setLooping(true);
        // Respect Master × Music settings on (re)entry.  The base 0.25 keeps
        // the track unobtrusive when the player leaves everything at 100%.
        backgroundMusic.setVolume(0.25f * context.getSettings().getMusicGain());
        backgroundMusic.play();

        netInput = new NetInput();
        Gdx.input.setInputProcessor(netInput);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        if (backgroundMusic != null) backgroundMusic.stop();
        if (itemPhaseRenderer != null) { itemPhaseRenderer.dispose(); itemPhaseRenderer = null; }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (arena != null) arena.resize(width, height);
        context.getPostProcess().resize(width, height);
    }

    @Override
    public void dispose() {
        particlePool.freeAll(particles);
        particles.clear();
        if (arena != null) arena.dispose();
    }

    @Override
    public void render(float delta) {
        if (netInput.consumeMenu()) { game.openPauseMenu(this, this::returnToMenu); return; }

        updateSimulation(delta);
        updateCameraShake(delta);
        arena.unprojectCursorOntoTable(netInput.lastMouseX, netInput.lastMouseY);

        context.getPostProcess().begin();
        arena.render3DScene(ballVisible);

        // Render item cubes in 3D (uses a fresh modelBatch begin/end)
        if (inItemPhase && itemPhaseRenderer != null) {
            itemPhaseRenderer.update(delta);
            arena.getModelBatch().begin(arena.getCamera());
            itemPhaseRenderer.render(arena.getModelBatch(), arena.getEnvironment());
            arena.getModelBatch().end();
        }
        // Fly buzz animation
        if (arena != null) arena.tickFlyBuzz(delta);

        context.getViewport().apply(true);

        // Punch blur
        if (punchTimer > 0f) {
            punchTimer -= delta;
            context.getPostProcess().setPunchBlur(punchTimer / 10f);
        } else {
            context.getPostProcess().setPunchBlur(0f);
        }

        SpriteBatch batch = context.getBatch();
        batch.setProjectionMatrix(context.getViewport().getCamera().combined);
        batch.begin();
        arena.drawCursorMarker(batch, context.getAssets().getProceduralAssets().getAimRing());
        arena.drawParticles(batch, context.getAssets().getProceduralAssets().getGlow(), particles);
        drawHud(batch);
        if (matchOver)    drawOutcomeOverlay(batch);
        if (disconnected) drawDisconnectOverlay(batch);
        // Item phase READY button
        if (inItemPhase) {
            String readyLabel = itemReadySent ? "WAITING..." : "[ READY ]";
            context.getBodyFont().setColor(Palette.TEXT);
            drawCentered(batch, context.getBodyFont(), readyLabel,
                GameConfig.WORLD_WIDTH * 0.5f, 60f, Palette.TEXT);
        }
        // Round overlay
        if (roundOverlayTimer > 0f) {
            roundOverlayTimer -= delta;
            context.getTitleFont().setColor(Palette.TEXT);
            drawCentered(batch, context.getTitleFont(), roundOverlayText,
                GameConfig.WORLD_WIDTH * 0.5f, GameConfig.WORLD_HEIGHT * 0.5f, Palette.TEXT);
        }
        batch.end();

        context.getPostProcess().endAndBlit();
    }

    private void updateSimulation(float delta) {
        snapAge += delta;

        if (ballVisible) {
            float t  = snapAge;
            float ex = snapBallPos.x + snapBallVel.x * t;
            float ez = snapBallPos.z + snapBallVel.z * t;
            float ey = extrapolateBallY(snapBallPos.y, snapBallVel.y, t);
            renderedBallPos.set(ex, ey, ez);
            arena.setBallPosition(ex, ey, ez);
        }

        for (int i = particles.size - 1; i >= 0; i--) {
            if (particles.get(i).update(delta)) {
                particlePool.free(particles.removeIndex(i));
            }
        }

        if (netInput.consumeClick() && !matchOver && !disconnected) {
            handleClick();
        }
    }

    /**
     * Integrates Y position under gravity with table-bounce reflections.
     * Mirrors MatchWorld3D physics so the ball looks correct between 30 Hz snapshots.
     */
    private static float extrapolateBallY(float y0, float vy0, float t) {
        final float floor = MatchWorld3D.TABLE_TOP_Y + MatchWorld3D.BALL_RADIUS;
        final float restitution = 0.7f; // matches MatchWorld3D.BOUNCE_RESTITUTION
        float y  = y0;
        float vy = vy0;
        float remaining = t;
        while (remaining > 0.001f) {
            float dt = Math.min(remaining, 0.005f);
            vy -= MatchWorld3D.GRAVITY * dt;
            y  += vy * dt;
            if (y < floor && vy < 0f) {
                y  = floor;
                vy = -vy * restitution;
                if (Math.abs(vy) < 0.5f) vy = 0f;
            }
            remaining -= dt;
        }
        return y;
    }

    /** Decays any active shake and applies the current offset to the camera. */
    private void updateCameraShake(float delta) {
        if (arena == null) return;
        if (shakeTime <= 0f) {
            arena.setCameraShake(0f, 0f, 0f);
            return;
        }
        shakeTime = Math.max(0f, shakeTime - delta);
        // Quadratic falloff so the first few frames are punchy, the tail is soft.
        float decay = shakeDuration > 0f ? (shakeTime / shakeDuration) : 0f;
        float jitter = shakeAmplitude * decay * decay;
        float dx = (MathUtils.random() - 0.5f) * 2f * jitter;
        float dy = (MathUtils.random() - 0.5f) * 2f * jitter;
        arena.setCameraShake(dx, dy, 0f);
    }

    private void triggerShake(float duration, float amplitude) {
        if (!context.getSettings().isScreenShakeEnabled()) return;
        // Don't shorten an in-progress harder shake.
        if (amplitude * duration < shakeAmplitude * shakeTime) return;
        shakeTime      = duration;
        shakeDuration  = duration;
        shakeAmplitude = amplitude;
    }

    private void handleClick() {
        // During item phase: intercept all clicks
        if (inItemPhase) {
            // READY zone: bottom 15% of screen
            if (netInput.lastClickY > Gdx.graphics.getHeight() * 0.85f && !itemReadySent) {
                itemReadySent = true;
                inItemPhase = false;
                if (conn != null) conn.sendItemReady();
            } else if (itemPhaseRenderer != null && arena != null) {
                // Ray-test against item cubes
                com.badlogic.gdx.math.collision.Ray ray = arena.getCamera()
                    .getPickRay(netInput.lastClickX,
                                Gdx.graphics.getHeight() - netInput.lastClickY);
                ItemType picked = itemPhaseRenderer.pickItem(ray, playerNumber);
                if (picked != null && conn != null) conn.sendUseItem(picked.getId());
            }
            return; // consume — no CLICK sent to server during ITEM_PHASE
        }

        // Normal gameplay click
        if (activePlayer != playerNumber) return;
        if (conn == null) return;
        conn.sendClick(
            netInput.lastClickX,
            netInput.lastClickY,
            Gdx.graphics.getWidth(),
            Gdx.graphics.getHeight()
        );
    }

    @Override
    public void onWaiting() {
        waitingForOpponent = true;
    }

    @Override
    public void onMatchReady(int matchModeWire) {
        waitingForOpponent = false;
        if (matchModeWire == PacketType.MODE_BOT) {
            context.getSession().setRemoteName("Bot");
        }
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

        // Detect a score event by comparing against the previous snapshot.
        // Bigger shake when WE lost a life, smaller when the opponent did.
        int myPrev   = (playerNumber == 1) ? prevP1Lives : prevP2Lives;
        int myCurr   = (playerNumber == 1) ? p1l         : p2l;
        int themPrev = (playerNumber == 1) ? prevP2Lives : prevP1Lives;
        int themCurr = (playerNumber == 1) ? p2l         : p1l;
        if (myCurr < myPrev)        triggerShake(0.40f, 0.55f);
        else if (themCurr < themPrev) triggerShake(0.20f, 0.20f);

        p1lives   = p1l;
        p2lives   = p2l;
        prevP1Lives = p1l;
        prevP2Lives = p2l;
    }

    @Override
    public void onGameOver(int winner) {
        matchOver    = true;
        winnerPlayer = winner;
    }

    @Override
    public void onSfx(int sfxType) {
        if (sfxType == PacketType.SFX_PADDLE) {
            paddleHitSfx.play(getSfxGain() * 0.7f);
        } else if (sfxType == PacketType.SFX_TABLE) {
            tableHitSfx.play(getSfxGain() * 0.6f);
            if (ballVisible) {
                spawnBounceSparks(renderedBallPos.x, renderedBallPos.y, renderedBallPos.z);
            }
            triggerShake(0.10f, 0.10f); // tiny chunk on the bounce thud
        }
    }

    @Override
    public void onRoundOver(int winner, int p1Wins, int p2Wins) {
        String who = (playerNumber == winner) ? "YOU WIN THE ROUND" : "OPPONENT WINS THE ROUND";
        roundOverlayText  = who + "  (" + p1Wins + " - " + p2Wins + ")";
        roundOverlayTimer = 2.5f;
        p1lives = GameConfig.DEFAULT_LIVES;
        p2lives = GameConfig.DEFAULT_LIVES;
        inItemPhase   = false;
        itemReadySent = false;
        myItems.clear();
        oppItems.clear();
        myFlies.clear();
        oppFlies.clear();
        punchTimer = 0f;
    }

    @Override
    public void onItemDealt(int forPlayer, byte[] itemIds) {
        List<ItemType> target = (forPlayer == playerNumber) ? myItems : oppItems;
        for (byte id : itemIds) {
            ItemType t = ItemType.fromId(id);
            if (t != null) target.add(t);
        }
        inItemPhase   = true;
        itemReadySent = false;
        if (itemPhaseRenderer != null) itemPhaseRenderer.load(myItems, oppItems);
    }

    @Override
    public void onItemUsed(int byPlayer, int itemId) {
        ItemType t = ItemType.fromId((byte) itemId);
        if (t == null) return;
        List<ItemType> inv = (byPlayer == playerNumber) ? myItems : oppItems;
        inv.remove(t);
        if (itemPhaseRenderer != null) itemPhaseRenderer.markUsed(byPlayer, t);
        if (t == ItemType.PUNCH && byPlayer != playerNumber) punchTimer = 10f;
    }

    @Override
    public void onFlySpawn(float[] xs, float[] zs) {
        myFlies.clear();
        for (int i = 0; i < xs.length; i++) myFlies.add(new FlyState(xs[i], zs[i]));
        if (arena != null) arena.setFlies(myFlies, oppFlies);
    }

    @Override
    public void onFlyKilled(int flyIndex) {
        if (flyIndex < myFlies.size()) myFlies.get(flyIndex).alive = false;
        else {
            int oppIdx = flyIndex - myFlies.size();
            if (oppIdx < oppFlies.size()) oppFlies.get(oppIdx).alive = false;
        }
        if (arena != null) arena.setFlies(myFlies, oppFlies);
    }

    /** Master × SFX volume in [0..1]. */
    private float getSfxGain() {
        return context.getSettings().getSfxGain();
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

    private void spawnBounceSparks(float x, float y, float z) {
        if (particles.size >= MAX_PARTICLES) {
            return;
        }
        int count = Math.min(10, MAX_PARTICLES - particles.size);
        for (int i = 0; i < count; i++) {
            float angle = rng.nextFloat() * MathUtils.PI2;
            float speed = 1.2f + rng.nextFloat() * 1.8f;
            tmpParticleVel.set(MathUtils.cos(angle) * speed,
                               1.5f + rng.nextFloat() * 1.5f,
                               MathUtils.sin(angle) * speed);
            ImpactParticle3D p = particlePool.obtain();
            p.init(x, y, z, tmpParticleVel, 0.35f + rng.nextFloat() * 0.25f);
            particles.add(p);
        }
    }

    private void drawHud(SpriteBatch batch) {
        int myLives   = (playerNumber == 1) ? p1lives : p2lives;
        int oppLives  = (playerNumber == 1) ? p2lives : p1lives;
        String oppName = context.getSession().getRemoteName();

        context.getBodyFont().setColor(Palette.TEXT);
        context.getBodyFont().draw(batch, "YOU  " + myLives,
            GameConfig.HUD_PADDING, 680f);
        context.getBodyFont().draw(batch, oppName + "  " + oppLives,
            GameConfig.WORLD_WIDTH - 260f, 680f);

        // Optional FPS overlay — settings → GAME tab.
        if (context.getSettings().isShowFpsCounter()) {
            context.getBodyFont().setColor(Palette.TEXT_DIM);
            context.getBodyFont().draw(batch,
                "FPS " + Gdx.graphics.getFramesPerSecond(),
                GameConfig.HUD_PADDING, 644f);
        }

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

    private void returnToMenu() {
        if (conn != null) conn.sendBye();
        shutdownConn();
        game.openMenu();
    }

    public void exitToMenu() {
        returnToMenu();
    }

    private void shutdownConn() {
        if (conn != null) { conn.close(); conn = null; }
        context.getSession().clearMultiplayer();
    }

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
