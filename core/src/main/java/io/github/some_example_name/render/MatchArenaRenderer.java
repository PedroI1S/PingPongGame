package io.github.some_example_name.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.DepthTestAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.environment.PointLight;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.world.FlyState;
import io.github.some_example_name.world.ImpactParticle3D;
import io.github.some_example_name.world.MatchWorld3D;

/**
 * Shared 3D arena (table, net, ball, floor) and HUD helpers used by both
 * {@link io.github.some_example_name.screen.NetMatchScreen}.
 */
public final class MatchArenaRenderer implements Disposable {

    /** Low segment count — ball is small on screen. */
    private static final int BALL_SEGMENTS = 8;

    private static final Vector3 CAMERA_TARGET = new Vector3(0f, MatchWorld3D.TABLE_TOP_Y, 0f);

    private final boolean cameraOnPositiveZ;
    private final Vector3 cameraOffset = new Vector3(0f, 2.5f, 0f);
    private final Vector3 cameraShake  = new Vector3(0f, 0f, 0f);

    private PerspectiveCamera camera3D;
    private ModelBatch modelBatch;
    private Environment environment;

    private Model tableModel, netModel, ballModel, floorModel;
    private Model flyModel;

    /** Optional OBJ ball model (generated voxel ball). Null ⇒ procedural sphere. */
    private Model ballObjModel;
    private boolean usingObjBall;
    private float ballObjScale = 1f;
    private final Vector3 ballObjCenter = new Vector3();

    // ── Bunker arena (generated voxel room, authored in world units) ─────────
    private Model arenaModel, arenaPropsModel, signModel, clutterModel;
    private ModelInstance arenaInstance, arenaPropsInstance, signInstance, clutterInstance;
    /** Fake-volumetric dust shaft under the hanging lamp (additive blend). */
    private Model lightConeModel;
    private ModelInstance lightConeInstance;
    private PointLight neonLight;
    private float envTime;

    // ── Diegetic lives: bulb racks over each table half ──────────────────────
    /**
     * Bulb rack layout — mirrored in tools/voxel/generate_arena.py.
     * P1's rack hangs on the RIGHT of their half, P2's on the LEFT, so the
     * two racks never overlap on screen (and each client sees their own
     * lives on the right, since the P2 camera mirrors the world).
     */
    private static final float P1_BULB_X0 = 1.0f, P2_BULB_X0 = -3.0f;
    private static final float BULB_DX = 0.5f, BULB_Y = 4.75f, BULB_Z = 3.5f;
    private static final int MAX_LIVES_SHOWN = 5;
    private Model bulbModel;
    private final ModelInstance[] p1Bulbs = new ModelInstance[MAX_LIVES_SHOWN];
    private final ModelInstance[] p2Bulbs = new ModelInstance[MAX_LIVES_SHOWN];
    private int shownP1Lives = -1, shownP2Lives = -1;

    /** Optional OBJ table model (with its own net). Null ⇒ procedural box. */
    private Model tableObjModel;
    /** True when the loaded table model includes its own net — skip the procedural one. */
    private boolean tableHasOwnNet;
    /** Vertical nudge for the table surface (world units, +up). Tunable. */
    private static final float TABLE_MODEL_Y_TRIM = 0f;
    /**
     * Local-space y of the play surface in {@code models/table/table.obj}.
     * The voxel table (tools/voxel/generate_table.py) is authored directly in
     * gameplay units — surface at 2.0, net top at 2.5, 14×6 footprint — so
     * the per-axis fit below resolves to an identity transform (plus the
     * 90° length-axis rotation).  If the asset is ever replaced, set this to
     * the new model's local surface height (the fit assumes the bbox top is
     * the NET top).
     */
    private static final float TABLE_SURFACE_LOCAL_Y = 2.0f;
    private final com.badlogic.gdx.utils.Array<ModelInstance> flyInstances = new com.badlogic.gdx.utils.Array<>();
    private final com.badlogic.gdx.utils.Array<FlyState> flyStates = new com.badlogic.gdx.utils.Array<>();
    private float flyBuzzTimer;

    private ModelInstance tableInstance, netInstance, ballInstance, floorInstance;

    private final Vector3 worldToScreen = new Vector3();
    private final Vector3 screenToWorld = new Vector3();
    private final Vector2 cursorOnTable = new Vector2();

    /**
     * @param cameraOnPositiveZ {@code true} for P1 view (+z behind player), {@code false} for P2
     */
    public MatchArenaRenderer(boolean cameraOnPositiveZ) {
        this.cameraOnPositiveZ = cameraOnPositiveZ;
        cameraOffset.z = cameraOnPositiveZ ? 11f : -11f;
    }

    public void ensureInitialized() {
        if (camera3D != null) {
            return;
        }

        camera3D = new PerspectiveCamera(60f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera3D.near = 0.1f;
        // Short far plane: DefaultShader fog is (distance/far)², so a tight
        // far makes the bunker's far end genuinely sink into darkness. The
        // farthest room corner sits ~30 from the camera — keep far above it.
        camera3D.far = 34f;
        applyCameraTransform();

        modelBatch = new ModelBatch();
        environment = new Environment();
        // Moody bunker lighting.  Ambient/directional are distance-independent
        // and wash the fog gradient flat (the retro palette then erases what's
        // left) — so keep them MINIMAL and let the point lights carry the
        // scene: their falloff plus (dist/far)² fog is what creates depth.
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.19f, 0.18f, 0.19f, 1f));
        // Warm dust-haze fog: distant surfaces wash toward a VISIBLE gray-brown
        // (fog to pure black just reads as a dark room).
        environment.set(ColorAttribute.createFog(0.105f, 0.088f, 0.078f, 1f));
        environment.add(new DirectionalLight().set(0.22f, 0.22f, 0.24f, -0.4f, -0.8f, -0.3f));
        environment.add(new PointLight().set(1.0f, 0.78f, 0.5f, 0f, 6.1f, 0f, 38f));
        // Red wash from the neon sign on the left wall.
        neonLight = new PointLight().set(0.8f, 0.18f, 0.18f, -9.4f, 6.6f, -0.8f, 9f);
        environment.add(neonLight);
        // Dim warm display light over the arsenal on the back wall.
        environment.add(new PointLight().set(0.85f, 0.6f, 0.4f, -4.5f, 5.6f, -11.5f, 12f));

        buildModels();
    }

    public PerspectiveCamera getCamera() {
        ensureInitialized();
        return camera3D;
    }

    public ModelBatch getModelBatch() { ensureInitialized(); return modelBatch; }
    public Environment getEnvironment() { ensureInitialized(); return environment; }

    public void resize(int width, int height) {
        if (camera3D == null) {
            return;
        }
        camera3D.viewportWidth = width;
        camera3D.viewportHeight = height;
        camera3D.update();
    }

    /** Clears depth buffer, renders room → table → net → bulbs → optional ball. */
    public void render3DScene(boolean ballVisible) {
        ensureInitialized();
        // Neon-sign flicker: slow pulse with an occasional dropout.
        envTime += Gdx.graphics.getDeltaTime();
        float flick = 0.85f + 0.15f * com.badlogic.gdx.math.MathUtils.sin(envTime * 7f);
        if (((int) (envTime * 1.7f)) % 9 == 0) flick *= 0.35f;  // dying tube
        neonLight.intensity = 9f * flick;

        ScreenUtils.clear(0.045f, 0.036f, 0.032f, 1f, true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        modelBatch.begin(camera3D);
        if (arenaInstance != null) {
            modelBatch.render(arenaInstance, environment);
            modelBatch.render(arenaPropsInstance, environment);
            if (signInstance != null) {
                // Neon letters glow on their own, synced with the light.
                for (Material mat : signInstance.materials) {
                    ColorAttribute em = (ColorAttribute) mat.get(ColorAttribute.Emissive);
                    if (em != null) em.color.set(0.85f * flick, 0.16f * flick, 0.14f * flick, 1f);
                }
                modelBatch.render(signInstance, environment);
            }
            if (clutterInstance != null) modelBatch.render(clutterInstance, environment);
            for (ModelInstance b : p1Bulbs) if (b != null) modelBatch.render(b, environment);
            for (ModelInstance b : p2Bulbs) if (b != null) modelBatch.render(b, environment);
            // Blended geometry — ModelBatch sorts it after the opaque pass.
            if (lightConeInstance != null) modelBatch.render(lightConeInstance, environment);
        } else {
            modelBatch.render(floorInstance, environment);
        }
        modelBatch.render(tableInstance, environment);
        if (!tableHasOwnNet) modelBatch.render(netInstance, environment);
        if (ballVisible) {
            modelBatch.render(ballInstance, environment);
        }
        for (ModelInstance fi : flyInstances) modelBatch.render(fi, environment);
        modelBatch.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    /**
     * Diegetic life display — lights {@code p1Lives} bulbs on the rack over
     * P1's half (+z) and {@code p2Lives} over P2's half. Cheap to call every
     * frame; materials are only touched when a value changes.
     */
    public void setLivesDisplay(int p1Lives, int p2Lives) {
        ensureInitialized();
        if (bulbModel == null) return;
        if (p1Lives == shownP1Lives && p2Lives == shownP2Lives) return;
        shownP1Lives = p1Lives;
        shownP2Lives = p2Lives;
        for (int i = 0; i < MAX_LIVES_SHOWN; i++) {
            setBulbLit(p1Bulbs[i], i < p1Lives);
            setBulbLit(p2Bulbs[i], i < p2Lives);
        }
    }

    private static void setBulbLit(ModelInstance bulb, boolean lit) {
        if (bulb == null) return;
        for (Material mat : bulb.materials) {
            ColorAttribute d = (ColorAttribute) mat.get(ColorAttribute.Diffuse);
            if (d == null) {
                d = ColorAttribute.createDiffuse(Color.WHITE);
                mat.set(d);
            }
            if (lit) {
                d.color.set(1.0f, 0.82f, 0.5f, 1f);
                mat.set(ColorAttribute.createEmissive(0.8f, 0.55f, 0.2f, 1f));
            } else {
                d.color.set(0.16f, 0.14f, 0.13f, 1f);
                mat.set(ColorAttribute.createEmissive(0f, 0f, 0f, 1f));
            }
        }
    }

    public void setBallPosition(float x, float y, float z) {
        ensureInitialized();
        if (usingObjBall) {
            // translate to ball pos, scale to ball size, then re-centre the mesh
            ballInstance.transform.idt();
            ballInstance.transform.translate(x, y, z);
            ballInstance.transform.scale(ballObjScale, ballObjScale, ballObjScale);
            ballInstance.transform.translate(-ballObjCenter.x, -ballObjCenter.y, -ballObjCenter.z);
        } else {
            ballInstance.transform.setToTranslation(x, y, z);
        }
    }

    public void setFlies(java.util.List<FlyState> playerFlies, java.util.List<FlyState> oppFlies) {
        flyInstances.clear();
        flyStates.clear();
        addFlyInstances(playerFlies);
        addFlyInstances(oppFlies);
    }

    private void addFlyInstances(java.util.List<FlyState> flies) {
        for (FlyState fly : flies) {
            if (!fly.alive) continue;
            ModelInstance inst = new ModelInstance(flyModel);
            inst.transform.setToTranslation(fly.x, MatchWorld3D.TABLE_TOP_Y + 0.4f, fly.z);
            flyInstances.add(inst);
            flyStates.add(fly);
        }
    }

    /** Advances the buzz clock and re-applies each fly's bobbing transform. */
    public void tickFlyBuzz(float delta) {
        flyBuzzTimer += delta;
        for (int i = 0; i < flyInstances.size; i++) {
            FlyState fly = flyStates.get(i);
            // Per-fly phase offset so the swarm doesn't bob in lockstep.
            float buzz = com.badlogic.gdx.math.MathUtils.sin(flyBuzzTimer * 8f + i * 1.7f) * 0.08f;
            flyInstances.get(i).transform.setToTranslation(
                fly.x, MatchWorld3D.TABLE_TOP_Y + 0.4f + buzz, fly.z);
        }
    }

    /**
     * Maps screen cursor to table-top (x, z). Returns {@link Vector2} with NaN if miss.
     */
    public Vector2 unprojectCursorOntoTable(int screenX, int screenY) {
        ensureInitialized();
        screenToWorld.set(screenX, screenY, 0f);
        camera3D.unproject(screenToWorld);

        Vector3 origin = camera3D.position;
        float dirX = screenToWorld.x - origin.x;
        float dirY = screenToWorld.y - origin.y;
        float dirZ = screenToWorld.z - origin.z;
        if (Math.abs(dirY) < 0.0001f) {
            cursorOnTable.set(Float.NaN, Float.NaN);
            return cursorOnTable;
        }
        float t = (MatchWorld3D.TABLE_TOP_Y - origin.y) / dirY;
        if (t <= 0f) {
            cursorOnTable.set(Float.NaN, Float.NaN);
            return cursorOnTable;
        }
        cursorOnTable.set(origin.x + dirX * t, origin.z + dirZ * t);
        return cursorOnTable;
    }

    public void drawCursorMarker(SpriteBatch batch, Texture aimRing) {
        if (Float.isNaN(cursorOnTable.x)) {
            return;
        }
        projectToHud(cursorOnTable.x, MatchWorld3D.TABLE_TOP_Y, cursorOnTable.y);
        if (worldToScreen.z < 0f || worldToScreen.z > 1f) {
            return;
        }
        float hudX = worldToScreen.x / Gdx.graphics.getWidth() * GameConfig.WORLD_WIDTH;
        float hudY = worldToScreen.y / Gdx.graphics.getHeight() * GameConfig.WORLD_HEIGHT;
        float size = 28f;
        batch.setColor(0.45f, 0.95f, 0.85f, 0.55f);
        batch.draw(aimRing, hudX - size * 0.5f, hudY - size * 0.5f, size, size);
        batch.setColor(Color.WHITE);
    }

    public void drawParticles(SpriteBatch batch, Texture glow,
                              Array<ImpactParticle3D> particles) {
        for (ImpactParticle3D p : particles) {
            projectToHud(p.getPosition().x, p.getPosition().y, p.getPosition().z);
            if (worldToScreen.z < 0f || worldToScreen.z > 1f) {
                continue;
            }
            float hudX = worldToScreen.x / Gdx.graphics.getWidth() * GameConfig.WORLD_WIDTH;
            float hudY = worldToScreen.y / Gdx.graphics.getHeight() * GameConfig.WORLD_HEIGHT;
            float alpha = p.getAlpha();
            float size = 4f + alpha * 6f;
            batch.setColor(1f, 1f, 0.85f, alpha * 0.85f);
            batch.draw(glow, hudX - size * 0.5f, hudY - size * 0.5f, size, size);
        }
        batch.setColor(Color.WHITE);
    }

    @Override
    public void dispose() {
        if (modelBatch != null) modelBatch.dispose();
        if (tableModel != null) tableModel.dispose();
        if (tableObjModel != null) tableObjModel.dispose();
        if (netModel != null) netModel.dispose();
        if (ballModel != null) ballModel.dispose();
        if (ballObjModel != null) ballObjModel.dispose();
        if (floorModel != null) floorModel.dispose();
        if (flyModel != null) flyModel.dispose();
        if (arenaModel != null) arenaModel.dispose();
        if (arenaPropsModel != null) arenaPropsModel.dispose();
        if (signModel != null) signModel.dispose();
        if (clutterModel != null) clutterModel.dispose();
        if (lightConeModel != null) lightConeModel.dispose();
        if (bulbModel != null) bulbModel.dispose();
    }

    private void projectToHud(float wx, float wy, float wz) {
        worldToScreen.set(wx, wy, wz);
        camera3D.project(worldToScreen);
    }

    private void applyCameraTransform() {
        camera3D.position.set(CAMERA_TARGET).add(cameraOffset).add(cameraShake);
        camera3D.lookAt(CAMERA_TARGET);
        camera3D.up.set(0f, 1f, 0f);
        camera3D.update();
    }

    /**
     * Per-frame camera-shake offset.  Set to zero when no shake is active.
     * Re-applied to the camera matrix immediately so the screen reacts the
     * same frame the caller asks for it.
     */
    public void setCameraShake(float dx, float dy, float dz) {
        ensureInitialized();
        cameraShake.set(dx, dy, dz);
        applyCameraTransform();
    }

    private void buildModels() {
        ModelBuilder mb = new ModelBuilder();
        long attrs = Usage.Position | Usage.Normal;

        tableModel = mb.createBox(
            MatchWorld3D.TABLE_HALF_WIDTH * 2f, 0.2f, MatchWorld3D.TABLE_HALF_LENGTH * 2f,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("1A6E5F"))),
            attrs
        );
        tableInstance = new ModelInstance(tableModel);
        tableInstance.transform.setToTranslation(0f, MatchWorld3D.TABLE_TOP_Y - 0.1f, 0f);
        loadTableModel(); // swaps tableInstance for the voxel table when available

        netModel = mb.createBox(
            MatchWorld3D.TABLE_HALF_WIDTH * 2f + 0.6f,
            MatchWorld3D.NET_HEIGHT,
            0.06f,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("E8C06A"))),
            attrs
        );
        netInstance = new ModelInstance(netModel);
        netInstance.transform.setToTranslation(
            0f, MatchWorld3D.TABLE_TOP_Y + MatchWorld3D.NET_HEIGHT * 0.5f, 0f);

        float d = MatchWorld3D.BALL_RADIUS * 2f;
        ballModel = mb.createSphere(d, d, d, BALL_SEGMENTS, BALL_SEGMENTS,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("F4FBFF"))),
            attrs
        );
        ballInstance = new ModelInstance(ballModel);
        loadBallModel(); // swaps ballInstance for the voxel ball when available
        loadArenaModels();
        loadBulbModel();

        float fd = FlyState.FLY_RADIUS * 1.5f;
        flyModel = mb.createSphere(fd, fd, fd, 6, 6,
            new Material(ColorAttribute.createDiffuse(new Color(0.15f, 0.15f, 0.05f, 1f))),
            Usage.Position | Usage.Normal);
        loadFlyModel(); // swaps flyModel for the voxel fly when available

        floorModel = mb.createBox(60f, 0.4f, 60f,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("0E2026"))),
            attrs
        );
        floorInstance = new ModelInstance(floorModel);
        floorInstance.transform.setToTranslation(0f, -0.2f, 0f);
    }

    /**
     * Tries to load the voxel ball OBJ and use it as the ball. The mesh is
     * auto-scaled so its longest axis matches the ball diameter and
     * re-centred on the ball position. On any failure the procedural sphere
     * built above stays in place, so the game always has a ball.
     */
    private void loadBallModel() {
        try {
            Model loaded = new ObjLoader().loadModel(
                Gdx.files.internal("models/ball/ball.obj"), mipmappedTextures());
            if (loaded == null || loaded.meshes.size == 0) {
                if (loaded != null) loaded.dispose();
                return;
            }
            ballObjModel = loaded;
            applyObjMaterialDefaults(ballObjModel);
            BoundingBox bb = new BoundingBox();
            ballObjModel.calculateBoundingBox(bb);
            bb.getCenter(ballObjCenter);
            float maxDim = Math.max(bb.getWidth(), Math.max(bb.getHeight(), bb.getDepth()));
            ballObjScale = (MatchWorld3D.BALL_RADIUS * 2f) / Math.max(1e-4f, maxDim);
            ballInstance = new ModelInstance(ballObjModel);
            usingObjBall = true;
        } catch (Throwable t) {
            // Corrupt/unsupported OBJ, missing texture, etc. — keep the sphere.
            Gdx.app.error("MatchArenaRenderer", "Ball model failed to load; using sphere", t);
            if (ballObjModel != null) { ballObjModel.dispose(); ballObjModel = null; }
            usingObjBall = false;
        }
    }

    /**
     * Tries to load the voxel fly OBJ.  It is authored at exact world scale
     * and centred at the origin (the per-frame fly transforms only translate),
     * so on success it simply replaces the procedural sphere.
     */
    private void loadFlyModel() {
        try {
            Model loaded = new ObjLoader().loadModel(
                Gdx.files.internal("models/fly/fly.obj"), mipmappedTextures());
            if (loaded == null || loaded.meshes.size == 0) {
                if (loaded != null) loaded.dispose();
                return;
            }
            applyObjMaterialDefaults(loaded);
            if (flyModel != null) flyModel.dispose();
            flyModel = loaded;
        } catch (Throwable t) {
            Gdx.app.error("MatchArenaRenderer", "Fly model failed to load; using sphere", t);
        }
    }

    /**
     * Loads the bunker room (two meshes — shell + props — to stay inside
     * 16-bit index limits).  Both are authored in world units with identity
     * transforms; on failure the plain procedural floor stays.
     */
    private void loadArenaModels() {
        try {
            Model shell = new ObjLoader().loadModel(
                Gdx.files.internal("models/arena/arena.obj"), mipmappedTextures());
            Model props = new ObjLoader().loadModel(
                Gdx.files.internal("models/arena/props.obj"), mipmappedTextures());
            if (shell == null || props == null
                || shell.meshes.size == 0 || props.meshes.size == 0) {
                if (shell != null) shell.dispose();
                if (props != null) props.dispose();
                return;
            }
            arenaModel = shell;
            arenaPropsModel = props;
            applyObjMaterialDefaults(arenaModel);
            applyObjMaterialDefaults(arenaPropsModel);
            arenaInstance = new ModelInstance(arenaModel);
            arenaPropsInstance = new ModelInstance(arenaPropsModel);
            signModel = new ObjLoader().loadModel(
                Gdx.files.internal("models/arena/sign.obj"), mipmappedTextures());
            if (signModel != null && signModel.meshes.size > 0) {
                applyObjMaterialDefaults(signModel);
                signInstance = new ModelInstance(signModel);
                for (Material mat : signInstance.materials) {
                    mat.set(ColorAttribute.createEmissive(0.85f, 0.16f, 0.14f, 1f));
                }
            }
            clutterModel = new ObjLoader().loadModel(
                Gdx.files.internal("models/arena/clutter.obj"), mipmappedTextures());
            if (clutterModel != null && clutterModel.meshes.size > 0) {
                applyObjMaterialDefaults(clutterModel);
                clutterInstance = new ModelInstance(clutterModel);
            }
            buildLightCone();
        } catch (Throwable t) {
            Gdx.app.error("MatchArenaRenderer", "Arena model failed to load; plain floor", t);
            if (arenaModel != null) { arenaModel.dispose(); arenaModel = null; }
            if (arenaPropsModel != null) { arenaPropsModel.dispose(); arenaPropsModel = null; }
            arenaInstance = null;
            arenaPropsInstance = null;
        }
    }

    /**
     * Fake-volumetric light shaft under the hanging lamp: an additive-blended
     * emissive cone (no depth write) — reads as dusty air in the lamp beam.
     */
    private void buildLightCone() {
        ModelBuilder mb = new ModelBuilder();
        lightConeModel = mb.createCone(6.6f, 4.2f, 6.6f, 18,
            new Material(
                ColorAttribute.createDiffuse(0f, 0f, 0f, 1f),
                ColorAttribute.createEmissive(0.5f, 0.38f, 0.22f, 1f),
                new BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE, 0.16f),
                new DepthTestAttribute(GL20.GL_LEQUAL, false),
                IntAttribute.createCullFace(GL20.GL_NONE)),
            Usage.Position | Usage.Normal);
        lightConeInstance = new ModelInstance(lightConeModel);
        // Apex at the lamp bulb (~6.1), base washing over the table surface.
        lightConeInstance.transform.setToTranslation(0f, 4.05f, 0f);
    }

    /** Loads the life bulb and hangs 5 instances over each table half. */
    private void loadBulbModel() {
        if (arenaInstance == null) return; // racks are part of the arena props
        try {
            Model loaded = new ObjLoader().loadModel(
                Gdx.files.internal("models/arena/bulb.obj"), mipmappedTextures());
            if (loaded == null || loaded.meshes.size == 0) {
                if (loaded != null) loaded.dispose();
                return;
            }
            bulbModel = loaded;
            applyObjMaterialDefaults(bulbModel);
            for (int i = 0; i < MAX_LIVES_SHOWN; i++) {
                p1Bulbs[i] = new ModelInstance(bulbModel);
                p1Bulbs[i].transform.setToTranslation(P1_BULB_X0 + i * BULB_DX, BULB_Y, BULB_Z);
                p2Bulbs[i] = new ModelInstance(bulbModel);
                p2Bulbs[i].transform.setToTranslation(P2_BULB_X0 + i * BULB_DX, BULB_Y, -BULB_Z);
            }
            shownP1Lives = shownP2Lives = -1;
            setLivesDisplay(MAX_LIVES_SHOWN, MAX_LIVES_SHOWN);
        } catch (Throwable t) {
            Gdx.app.error("MatchArenaRenderer", "Bulb model failed to load", t);
            if (bulbModel != null) { bulbModel.dispose(); bulbModel = null; }
        }
    }

    /** White diffuse (so map_Kd shows) + double-sided rendering for OBJ props. */
    private static void applyObjMaterialDefaults(Model model) {
        for (Material mat : model.materials) {
            ColorAttribute diff = (ColorAttribute) mat.get(ColorAttribute.Diffuse);
            if (diff != null) diff.color.set(Color.WHITE);
            else mat.set(ColorAttribute.createDiffuse(Color.WHITE));
            mat.set(IntAttribute.createCullFace(GL20.GL_NONE));
        }
    }

    /**
     * Tries to load the table OBJ (the generated voxel table) and use it
     * instead of the procedural box. The model's native long axis is X, while
     * the playfield is long on Z, so it is turned 90° about Y.  The model
     * ships with its OWN net at the centre of its long axis, so the fit is
     * per-axis:
     * <ul>
     *   <li>length / width exactly cover the 14×6 playfield,</li>
     *   <li>height is scaled so the model's net (surface plane → bbox top)
     *       compresses to the gameplay {@link MatchWorld3D#NET_HEIGHT}, and</li>
     *   <li>the surface plane is pinned to {@link MatchWorld3D#TABLE_TOP_Y} —
     *       the model's net top then lands exactly on NET_TOP_Y.</li>
     * </ul>
     * The procedural net is hidden while the model's own net is in use.
     * Any failure keeps the procedural box + net.
     */
    private void loadTableModel() {
        try {
            Model loaded = new ObjLoader().loadModel(
                Gdx.files.internal("models/table/table.obj"), mipmappedTextures());
            if (loaded == null || loaded.meshes.size == 0) {
                if (loaded != null) loaded.dispose();
                return;
            }
            tableObjModel = loaded;
            for (Material mat : tableObjModel.materials) {
                ColorAttribute d = (ColorAttribute) mat.get(ColorAttribute.Diffuse);
                if (d != null) d.color.set(Color.WHITE);
                else mat.set(ColorAttribute.createDiffuse(Color.WHITE));
                // Render imported OBJs double-sided — winding-proof.
                mat.set(IntAttribute.createCullFace(GL20.GL_NONE));
            }
            BoundingBox bb = new BoundingBox();
            tableObjModel.calculateBoundingBox(bb);
            Vector3 center = bb.getCenter(new Vector3());
            float modelW = Math.max(1e-4f, bb.getWidth());   // local x — long axis → world length (z)
            float modelD = Math.max(1e-4f, bb.getDepth());   // local z → world width (x)
            float sLen = (MatchWorld3D.TABLE_HALF_LENGTH * 2f) / modelW;
            float sWid = (MatchWorld3D.TABLE_HALF_WIDTH  * 2f) / modelD;
            float netLocal = Math.max(1e-4f, bb.max.y - TABLE_SURFACE_LOCAL_Y);
            float sHgt = MatchWorld3D.NET_HEIGHT / netLocal;
            // World y of a local point p is posY + sHgt * (p.y - center.y);
            // solve so the surface plane lands on TABLE_TOP_Y.
            float posY = MatchWorld3D.TABLE_TOP_Y
                - sHgt * (TABLE_SURFACE_LOCAL_Y - center.y) + TABLE_MODEL_Y_TRIM;
            ModelInstance inst = new ModelInstance(tableObjModel);
            inst.transform.idt();
            inst.transform.translate(0f, posY, 0f);
            inst.transform.rotate(0f, 1f, 0f, 90f);
            inst.transform.scale(sLen, sHgt, sWid);
            inst.transform.translate(-center.x, -center.y, -center.z);
            tableInstance = inst;
            tableHasOwnNet = true;
        } catch (Throwable t) {
            Gdx.app.error("MatchArenaRenderer", "Table model failed to load; using procedural box", t);
            if (tableObjModel != null) { tableObjModel.dispose(); tableObjModel = null; }
            tableHasOwnNet = false;
        }
    }

    /**
     * Texture provider with mipmaps + trilinear filtering — keeps OBJ
     * textures stable at glancing angles and small on-screen sizes.
     */
    private static TextureProvider mipmappedTextures() {
        return new TextureProvider.FileTextureProvider(
            Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear,
            Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat, true);
    }
}
