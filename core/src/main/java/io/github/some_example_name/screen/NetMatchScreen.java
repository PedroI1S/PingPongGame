package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
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
import io.github.some_example_name.render.ItemCopy;
import io.github.some_example_name.render.ItemPhaseRenderer;
import io.github.some_example_name.render.MatchArenaRenderer;
import io.github.some_example_name.ui.Button;
import io.github.some_example_name.ui.UIDraw;
import io.github.some_example_name.world.FlyState;
import io.github.some_example_name.world.ImpactParticle3D;
import io.github.some_example_name.world.physics.BallPhysics;
import io.github.some_example_name.world.physics.BallState;
import io.github.some_example_name.world.physics.PhysicsConfig;
import io.github.some_example_name.world.physics.StepContacts;
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
    private final Vector3 snapBallSpin = new Vector3();
    private final BallPhysics clientPhysics = new BallPhysics(PhysicsConfig.createDefault());
    private final BallState extrapState = new BallState();
    private final StepContacts extrapContacts = new StepContacts();
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
    private Button readyButton;
    private final Vector3 tmpUiWorld = new Vector3();
    /**
     * True when an opponent PUNCH was applied while in item selection.
     * The blur is deferred so it only starts when the rally resumes.
     */
    private boolean pendingPunchBlur;

    // ── Flies ──────────────────────────────────────────────────────────────────
    private final List<FlyState> myFlies  = new ArrayList<>();
    private final List<FlyState> oppFlies = new ArrayList<>();

    // ── Punch blur ─────────────────────────────────────────────────────────────
    private float punchTimer;

    // ── Round overlay ──────────────────────────────────────────────────────────
    private String roundOverlayText;
    private float  roundOverlayTimer;

    // ── Event log (upper-left, fading) ──────────────────────────────────────────
    private static final float LOG_LIFETIME = 6f;
    private static final float LOG_FADE_SECS = 1f; // alpha ramps to 0 over the final second
    private static final int   LOG_MAX      = 6;
    private final java.util.ArrayDeque<LogLine> logLines = new java.util.ArrayDeque<>();

    private static final class LogLine {
        final String text; float age;
        LogLine(String t) { this.text = t; }
    }

    private void pushLog(String text) {
        logLines.addFirst(new LogLine(text));
        while (logLines.size() > LOG_MAX) logLines.removeLast();
    }

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

    // ── Fly buzz loop — runs while any fly is alive on either side ────────────
    private boolean flyBuzzPlaying;
    private long flyBuzzId;

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
            // P1 views from +z, P2 from −z — keep "my items" on the local side.
            itemPhaseRenderer = new ItemPhaseRenderer(playerNumber == 1);
        }
        if (readyButton == null) {
            float w = 240f, h = 56f;
            // Lower-left, clear of the centred status text and the upper-left event log.
            readyButton = new Button(GameConfig.HUD_PADDING, 40f, w, h,
                "END SELECTION", Palette.RED, this::confirmReady);
        }
        backgroundMusic = context.getAssets().getBackgroundMusic();
        backgroundMusic.setLooping(true);
        // Respect Master × Music settings on (re)entry.  The base 0.35 keeps
        // the track present but under the SFX when everything is left at 100%.
        backgroundMusic.setVolume(0.35f * context.getSettings().getMusicGain());
        backgroundMusic.play();

        netInput = new NetInput();
        Gdx.input.setInputProcessor(netInput);
    }

    @Override
    public void hide() {
        // Keep arena + itemPhaseRenderer alive: hide() also fires when the
        // pause menu opens, and resume must not lose the loaded models.
        // Real teardown happens in dispose(), called from returnToMenu().
        Gdx.input.setInputProcessor(null);
        if (backgroundMusic != null) backgroundMusic.stop();
        stopFlyBuzz();
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
        if (arena != null) { arena.dispose(); arena = null; }
        if (itemPhaseRenderer != null) { itemPhaseRenderer.dispose(); itemPhaseRenderer = null; }
    }

    @Override
    public void render(float delta) {
        if (netInput.consumeMenu()) { game.openPauseMenu(this, this::returnToMenu); return; }

        updateSimulation(delta);
        updateCameraShake(delta);
        arena.unprojectCursorOntoTable(netInput.lastMouseX, netInput.lastMouseY);
        arena.tickFlyBuzz(delta); // before render3DScene so this frame's bob is drawn
        arena.setLivesDisplay(p1lives, p2lives); // diegetic bulb racks

        context.getPostProcess().begin();
        arena.render3DScene(ballVisible);

        // Render item cubes in 3D (uses a fresh modelBatch begin/end)
        if (inItemPhase && itemPhaseRenderer != null) {
            com.badlogic.gdx.math.collision.Ray hoverRay =
                arena.getCamera().getPickRay(netInput.lastMouseX, netInput.lastMouseY);
            if (itemPhaseRenderer.updateHover(hoverRay, 1)) {
                context.getAssets().getUiHoverSfx().play(getSfxGain() * 0.5f);
            }
            itemPhaseRenderer.update(delta);
            arena.getModelBatch().begin(arena.getCamera());
            itemPhaseRenderer.render(arena.getModelBatch(), arena.getEnvironment());
            arena.getModelBatch().end();
        }

        // END SELECTION button — independent of the item shelf so it always tracks.
        if (inItemPhase && readyButton != null) {
            toUiWorld(netInput.lastMouseX, netInput.lastMouseY);
            readyButton.enabled = !itemReadySent;
            readyButton.label = itemReadySent ? "WAITING..." : "END SELECTION";
            boolean wasHovered = readyButton.hovered;
            readyButton.updateHover(tmpUiWorld.x, tmpUiWorld.y);
            if (!wasHovered && readyButton.hovered) {
                context.getAssets().getUiHoverSfx().play(getSfxGain() * 0.5f);
            }
        }

        context.getViewport().apply(true);

        // Punch blur must be set before endAndBlit() reads the uniform.
        if (punchTimer > 0f) {
            punchTimer -= delta;
            context.getPostProcess().setPunchBlur(punchTimer / 10f);
        } else {
            context.getPostProcess().setPunchBlur(0f);
        }

        SpriteBatch batch = context.getBatch();
        batch.setProjectionMatrix(context.getViewport().getCamera().combined);
        // World-space 2D that SHOULD stay stylized — drawn inside the FBO.
        batch.begin();
        arena.drawCursorMarker(batch, context.getAssets().getProceduralAssets().getAimRing());
        arena.drawParticles(batch, context.getAssets().getProceduralAssets().getGlow(), particles);
        batch.end();

        context.getPostProcess().endAndBlit();

        // ── Crisp UI pass — untouched by the shader or punch blur ──
        context.getViewport().apply(true);
        batch.setProjectionMatrix(context.getViewport().getCamera().combined);
        batch.begin();
        drawHud(batch);
        drawEventLog(batch, delta);
        if (matchOver)    drawOutcomeOverlay(batch);
        if (disconnected) drawDisconnectOverlay(batch);
        if (inItemPhase) {
            if (readyButton != null) {
                readyButton.draw(batch, context.getAssets().getProceduralAssets().getPixel(),
                    context.getBodyFont(), context.getGlyphLayout());
            }
            ItemType hov = itemPhaseRenderer != null ? itemPhaseRenderer.hoveredType(1) : null;
            if (hov != null) drawItemTooltip(batch, hov);
        }
        if (roundOverlayTimer > 0f) {
            roundOverlayTimer -= delta;
            drawCentered(batch, context.getTitleFont(), roundOverlayText,
                GameConfig.WORLD_WIDTH * 0.5f, GameConfig.WORLD_HEIGHT * 0.5f, Palette.TEXT);
        }
        batch.end();
    }

    private void updateSimulation(float delta) {
        snapAge += delta;

        if (ballVisible) {
            extrapState.pos.set(snapBallPos);
            extrapState.vel.set(snapBallVel);
            extrapState.spin.set(snapBallSpin);
            clientPhysics.resetAccumulator();
            // cap so packet loss can't run physics far past the last snapshot
            clientPhysics.step(extrapState, Math.min(snapAge, 0.25f), null, extrapContacts);
            renderedBallPos.set(extrapState.pos);
            arena.setBallPosition(extrapState.pos.x, extrapState.pos.y, extrapState.pos.z);
        }

        for (int i = particles.size - 1; i >= 0; i--) {
            if (particles.get(i).update(delta)) {
                particlePool.free(particles.removeIndex(i));
            }
        }

        updateFlyBuzz();

        if (netInput.consumeClick() && !matchOver && !disconnected) {
            handleClick();
        }
    }

    /** Starts/stops the looping wing buzz to match living flies on the table. */
    private void updateFlyBuzz() {
        int alive = 0;
        for (FlyState f : myFlies)  if (f.alive) alive++;
        for (FlyState f : oppFlies) if (f.alive) alive++;
        if (alive > 0 && !flyBuzzPlaying) {
            flyBuzzId = context.getAssets().getFlyBuzzSfx().loop(getSfxGain() * 0.35f);
            flyBuzzPlaying = true;
        } else if (alive == 0 && flyBuzzPlaying) {
            stopFlyBuzz();
        }
    }

    private void stopFlyBuzz() {
        if (flyBuzzPlaying) {
            context.getAssets().getFlyBuzzSfx().stop(flyBuzzId);
            flyBuzzPlaying = false;
        }
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

    private void confirmReady() {
        if (itemReadySent) return;
        itemReadySent = true; // stay in the item phase; the button now shows WAITING
        context.getAssets().getUiClickSfx().play(getSfxGain() * 0.8f);
        if (conn != null) conn.sendItemReady();
    }

    /** Unprojects a raw screen point to viewport world (1280×720) coordinates into {@link #tmpUiWorld}. */
    private void toUiWorld(int screenX, int screenY) {
        tmpUiWorld.set(screenX, screenY, 0f);
        context.getViewport().unproject(tmpUiWorld);
    }

    private void handleClick() {
        // During item phase: intercept all clicks
        if (inItemPhase) {
            if (itemReadySent) return; // locked in — waiting for the opponent, ignore clicks
            // END SELECTION button first (2D UI, world coords).
            toUiWorld(netInput.lastClickX, netInput.lastClickY);
            if (readyButton != null && readyButton.tryClick(tmpUiWorld.x, tmpUiWorld.y)) {
                return; // confirmReady() ran via the button action
            }
            // Then the 3D item shelf.
            if (itemPhaseRenderer != null && arena != null) {
                com.badlogic.gdx.math.collision.Ray ray = arena.getCamera()
                    .getPickRay(netInput.lastClickX, netInput.lastClickY);
                ItemType picked = itemPhaseRenderer.pickItem(ray, 1);
                if (picked != null) {
                    context.getAssets().getUiClickSfx().play(getSfxGain() * 0.8f);
                    if (conn != null) conn.sendUseItem(picked.getId());
                }
            }
            return; // never sent to the server during ITEM_PHASE; empty space does nothing
        }

        // Normal gameplay click — always forward to the server, which is the
        // authority: it decides whether the click swats a fly, serves, returns
        // the ball, or is a no-op. Gating on activePlayer here would suppress
        // fly-swat clicks, which are valid even when it is not your turn to hit.
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
                        float sx, float sy, float sz,
                        int p1l, int p2l,
                        boolean visible, int ap) {
        waitingForOpponent = false;
        // ap==0 only during ITEM_PHASE; any other value means server has moved on.
        // Queue on GL thread so it runs AFTER any pending onItemDealt postRunnables,
        // ensuring the clear wins the race against a late-arriving deal notification.
        if (ap != 0) {
            Gdx.app.postRunnable(() -> {
                if (inItemPhase && pendingPunchBlur) {
                    punchTimer = 10f;
                    pendingPunchBlur = false;
                }
                inItemPhase = false;
                itemReadySent = false;
            });
        }
        snapBallPos.set(px, py, pz);
        snapBallVel.set(vx, vy, vz);
        snapBallSpin.set(sx, sy, sz);
        snapAge      = 0f;
        ballVisible  = visible;
        activePlayer = ap;

        // Detect a score event by comparing against the previous snapshot.
        // Bigger shake when WE lost a life, smaller when the opponent did.
        int myPrev   = (playerNumber == 1) ? prevP1Lives : prevP2Lives;
        int myCurr   = (playerNumber == 1) ? p1l         : p2l;
        int themPrev = (playerNumber == 1) ? prevP2Lives : prevP1Lives;
        int themCurr = (playerNumber == 1) ? p2l         : p1l;
        if (myCurr < myPrev) {
            triggerShake(0.40f, 0.55f);
            context.getAssets().getLifeLostSfx().play(getSfxGain() * 1.0f);
        } else if (themCurr < themPrev) {
            triggerShake(0.20f, 0.20f);
            context.getAssets().getLifeLostSfx().play(getSfxGain() * 0.6f);
        }

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
            paddleHitSfx.play(getSfxGain() * 0.95f);
        } else if (sfxType == PacketType.SFX_TABLE) {
            tableHitSfx.play(getSfxGain() * 0.85f);
            if (ballVisible) {
                spawnBounceSparks(renderedBallPos.x, renderedBallPos.y, renderedBallPos.z);
            }
            triggerShake(0.10f, 0.10f); // tiny chunk on the bounce thud
        }
    }

    @Override
    public void onRoundOver(int winner, int p1Wins, int p2Wins) {
        Gdx.app.postRunnable(() -> {
            String who = (playerNumber == winner) ? "YOU WIN THE ROUND" : "OPPONENT WINS THE ROUND";
            roundOverlayText  = who + "  (" + p1Wins + " - " + p2Wins + ")";
            roundOverlayTimer = 2.5f;
            p1lives = GameConfig.DEFAULT_LIVES;
            p2lives = GameConfig.DEFAULT_LIVES;
            inItemPhase      = false;
            itemReadySent    = false;
            pendingPunchBlur = false;
            myItems.clear();
            oppItems.clear();
            myFlies.clear();
            oppFlies.clear();
            punchTimer = 0f;
            pushLog(playerNumber == winner ? "You win the round" : "Opponent wins the round");
        });
    }

    @Override
    public void onItemDealt(int forPlayer, byte[] itemIds) {
        Gdx.app.postRunnable(() -> {
            // A new item phase means the server cleared all effects — drop
            // any leftover fly visuals from the previous rally.
            if (!inItemPhase && (!myFlies.isEmpty() || !oppFlies.isEmpty())) {
                myFlies.clear();
                oppFlies.clear();
                if (arena != null) arena.setFlies(myFlies, oppFlies);
            }
            List<ItemType> target = (forPlayer == playerNumber) ? myItems : oppItems;
            for (byte id : itemIds) {
                ItemType t = ItemType.fromId(id);
                if (t != null) target.add(t);
            }
            inItemPhase   = true;
            itemReadySent = false;
            if (itemPhaseRenderer != null) itemPhaseRenderer.load(myItems, oppItems);
        });
    }

    @Override
    public void onItemUsed(int byPlayer, int itemId) {
        Gdx.app.postRunnable(() -> {
            ItemType t = ItemType.fromId((byte) itemId);
            if (t == null) return;
            context.getAssets().getItemUseSfx().play(getSfxGain() * 0.9f);
            List<ItemType> inv = (byPlayer == playerNumber) ? myItems : oppItems;
            inv.remove(t);
            if (itemPhaseRenderer != null) {
                // Remap to 1=my-items, 2=opp-items so P2 clients address the right entry array.
                itemPhaseRenderer.markUsed(byPlayer == playerNumber ? 1 : 2, t);
            }
            if (t == ItemType.PUNCH && byPlayer != playerNumber) {
                // Defer the punch blur so it only starts when the rally actually begins.
                if (inItemPhase) {
                    pendingPunchBlur = true;
                } else {
                    punchTimer = 10f;
                }
            }
            String who = (byPlayer == playerNumber) ? "You" : "Opponent";
            if (t == ItemType.FLY_BAIT) {
                pushLog(byPlayer == playerNumber
                    ? "You set Fly Bait — flies on opponent's side"
                    : "Opponent set Fly Bait — flies on your side!");
            } else {
                pushLog(who + " used " + ItemCopy.name(t));
            }
        });
    }

    @Override
    public void onFlySpawn(int owner, float[] xs, float[] zs) {
        Gdx.app.postRunnable(() -> {
            // Each batch replaces only the owner's side, so both players can
            // have flies at once (e.g. both used FLY_BAIT this item phase).
            List<FlyState> target = (owner == playerNumber) ? myFlies : oppFlies;
            target.clear();
            for (int i = 0; i < xs.length; i++) target.add(new FlyState(xs[i], zs[i]));
            if (arena != null) arena.setFlies(myFlies, oppFlies);
        });
    }

    @Override
    public void onFlyKilled(int owner, int flyIndex) {
        Gdx.app.postRunnable(() -> {
            List<FlyState> target = (owner == playerNumber) ? myFlies : oppFlies;
            if (flyIndex < target.size()) target.get(flyIndex).alive = false;
            if (arena != null) arena.setFlies(myFlies, oppFlies);
        });
    }

    /** Master × SFX volume in [0..1]. */
    private float getSfxGain() {
        return context.getSettings().getSfxGain();
    }

    @Override
    public void onDisconnected() {
        // The server closes connections right after GAME_OVER — that teardown
        // is expected, so don't stack the disconnect overlay on the outcome one.
        if (!matchOver) disconnected = true;
        shutdownConn();
    }

    @Override
    public void onError(String reason) {
        if (!matchOver) disconnected = true;
        shutdownConn();
    }

    @Override
    public void onBye() {
        if (!matchOver) disconnected = true;
        shutdownConn();
    }

    @Override
    public void onLogEvent(int code, int subject) {
        Gdx.app.postRunnable(() -> {
            String line = describeLog(code, subject);
            if (line != null) pushLog(line);
        });
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

    private String describeLog(int code, int subject) {
        String who = (subject == playerNumber) ? "You" : "Opponent";
        return switch (code) {
            case PacketType.LOG_VOLLEY         -> who + ": volley — point lost";
            case PacketType.LOG_DOUBLE_BOUNCE  -> who + ": double bounce — point lost";
            case PacketType.LOG_OUT_OF_BOUNDS  -> who + ": shot out of bounds — point lost";
            case PacketType.LOG_MISS           -> who + ": missed the return";
            case PacketType.LOG_TIMEOUT        -> who + ": too slow — point lost";
            case PacketType.LOG_FLY_HIT        -> who + ": hit a fly — point lost";
            case PacketType.LOG_COIN_FLIP_LOSS -> "Coin flip — " + (subject == playerNumber ? "you lose" : "opponent loses");
            default -> null;
        };
    }

    private static final float LOG_LINE_H = 24f;
    private static final float LOG_TOP_Y  = 610f; // top edge of the newest line

    private void drawEventLog(SpriteBatch batch, float delta) {
        // Age/evict every frame (even when hidden) so re-enabling doesn't replay stale lines.
        // First pass: advance ages, drop expired, and measure the widest surviving line.
        float maxW = 0f;
        int n = 0;
        java.util.Iterator<LogLine> it = logLines.iterator();
        while (it.hasNext()) {
            LogLine line = it.next();
            line.age += delta;
            if (line.age >= LOG_LIFETIME) { it.remove(); continue; }
            context.getGlyphLayout().setText(context.getBodyFont(), line.text);
            if (context.getGlyphLayout().width > maxW) maxW = context.getGlyphLayout().width;
            n++;
        }
        if (!context.getSettings().isEventLogEnabled() || n == 0) return;

        // Opaque backing box sized to the content, so the text reads over the busy 3D scene.
        float padX = 8f, padY = 6f;
        float boxW = maxW + padX * 2f;
        float boxH = n * LOG_LINE_H + padY * 2f;
        float boxX = GameConfig.HUD_PADDING - padX;
        float boxBottom = LOG_TOP_Y + padY - boxH;
        Texture pixel = context.getAssets().getProceduralAssets().getPixel();
        UIDraw.fill(batch, pixel, Color.BLACK, 0.85f, boxX, boxBottom, boxW, boxH);
        UIDraw.border(batch, pixel, Palette.BORDER, boxX, boxBottom, boxW, boxH, 1f);

        // Second pass: draw newest-on-top, each line fading over its final second.
        int i = 0;
        for (LogLine line : logLines) {
            float alpha = Math.min(1f, (LOG_LIFETIME - line.age) / LOG_FADE_SECS);
            context.getBodyFont().setColor(Palette.TEXT.r, Palette.TEXT.g, Palette.TEXT.b, alpha);
            context.getBodyFont().draw(batch, line.text, GameConfig.HUD_PADDING, LOG_TOP_Y - i * LOG_LINE_H);
            i++;
        }
        context.getBodyFont().setColor(Palette.TEXT);
    }

    /** Floating tooltip near the cursor describing the hovered item, over an opaque box. */
    private void drawItemTooltip(SpriteBatch batch, ItemType item) {
        String name = ItemCopy.name(item);
        String desc = ItemCopy.description(item);
        context.getGlyphLayout().setText(context.getBodyFont(), name);
        float maxW = context.getGlyphLayout().width;
        context.getGlyphLayout().setText(context.getBodyFont(), desc);
        maxW = Math.max(maxW, context.getGlyphLayout().width);

        float lineH = 24f, padX = 10f, padY = 8f;
        float boxW = maxW + padX * 2f;
        float boxH = 2f * lineH + padY * 2f;

        // Anchor down-right of the cursor, clamped inside the viewport.
        toUiWorld(netInput.lastMouseX, netInput.lastMouseY);
        float bx = Math.max(8f, Math.min(tmpUiWorld.x + 18f, GameConfig.WORLD_WIDTH - boxW - 8f));
        float byTop = Math.max(boxH + 8f, Math.min(tmpUiWorld.y - 18f, GameConfig.WORLD_HEIGHT - 8f));
        float boxBottom = byTop - boxH;

        Texture pixel = context.getAssets().getProceduralAssets().getPixel();
        UIDraw.fill(batch, pixel, Color.BLACK, 0.85f, bx, boxBottom, boxW, boxH);
        UIDraw.border(batch, pixel, Palette.BORDER, bx, boxBottom, boxW, boxH, 1f);
        context.getBodyFont().setColor(Palette.TEXT);
        context.getBodyFont().draw(batch, name, bx + padX, byTop - padY);
        context.getBodyFont().setColor(Palette.TEXT_DIM);
        context.getBodyFont().draw(batch, desc, bx + padX, byTop - padY - lineH);
        context.getBodyFont().setColor(Palette.TEXT);
    }

    private String deriveStatus() {
        if (waitingForOpponent) return "Waiting for opponent to connect...";
        if (inItemPhase) {
            if (itemReadySent) return "Waiting for opponent...";
            return "Pick your items, then press END SELECTION.";
        }
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
        // libGDX never calls Screen.dispose() on its own — without this the
        // arena's models and textures leak once per match.
        dispose();
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
