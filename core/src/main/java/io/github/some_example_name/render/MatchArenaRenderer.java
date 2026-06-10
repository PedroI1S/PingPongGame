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
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
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

    /** Optional OBJ ball model (Meshy fingerprint egg). Null ⇒ procedural sphere. */
    private Model eggBallModel;
    private boolean usingEggBall;
    private float eggScale = 1f;
    private final Vector3 eggCenter = new Vector3();

    /** Optional OBJ table model (Meshy abandoned table). Null ⇒ procedural box. */
    private Model tableObjModel;
    /** Vertical nudge for the table surface (world units, +up). Tunable. */
    private static final float TABLE_MODEL_Y_TRIM = 0f;
    private final com.badlogic.gdx.utils.Array<ModelInstance> flyInstances = new com.badlogic.gdx.utils.Array<>();
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
        camera3D.far = 100f;
        applyCameraTransform();

        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.45f, 0.5f, 0.55f, 1f));
        environment.add(new DirectionalLight().set(0.9f, 0.95f, 1f, -0.4f, -0.8f, -0.3f));

        buildModels();
    }

    public PerspectiveCamera getCamera() {
        ensureInitialized();
        return camera3D;
    }

    public ModelBatch getModelBatch() { ensureInitialized(); return modelBatch; }
    public Environment getEnvironment() { ensureInitialized(); return environment; }

    public ModelInstance getBallInstance() {
        ensureInitialized();
        return ballInstance;
    }

    public void resize(int width, int height) {
        if (camera3D == null) {
            return;
        }
        camera3D.viewportWidth = width;
        camera3D.viewportHeight = height;
        camera3D.update();
    }

    /** Clears depth buffer, renders floor → table → net → optional ball. */
    public void render3DScene(boolean ballVisible) {
        ensureInitialized();
        ScreenUtils.clear(0.02f, 0.05f, 0.07f, 1f, true);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        modelBatch.begin(camera3D);
        modelBatch.render(floorInstance, environment);
        modelBatch.render(tableInstance, environment);
        modelBatch.render(netInstance, environment);
        if (ballVisible) {
            modelBatch.render(ballInstance, environment);
        }
        for (ModelInstance fi : flyInstances) modelBatch.render(fi, environment);
        modelBatch.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    public void setBallPosition(float x, float y, float z) {
        ensureInitialized();
        if (usingEggBall) {
            // translate to ball pos, scale to ball size, then re-centre the mesh
            ballInstance.transform.idt();
            ballInstance.transform.translate(x, y, z);
            ballInstance.transform.scale(eggScale, eggScale, eggScale);
            ballInstance.transform.translate(-eggCenter.x, -eggCenter.y, -eggCenter.z);
        } else {
            ballInstance.transform.setToTranslation(x, y, z);
        }
    }

    public void setFlies(java.util.List<FlyState> playerFlies, java.util.List<FlyState> oppFlies) {
        flyInstances.clear();
        addFlyInstances(playerFlies);
        addFlyInstances(oppFlies);
    }

    private void addFlyInstances(java.util.List<FlyState> flies) {
        for (FlyState fly : flies) {
            if (!fly.alive) continue;
            ModelInstance inst = new ModelInstance(flyModel);
            float buzz = com.badlogic.gdx.math.MathUtils.sin(flyBuzzTimer * 8f) * 0.08f;
            inst.transform.setToTranslation(fly.x, MatchWorld3D.TABLE_TOP_Y + 0.4f + buzz, fly.z);
            flyInstances.add(inst);
        }
    }

    public void tickFlyBuzz(float delta) { flyBuzzTimer += delta; }

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
        if (eggBallModel != null) eggBallModel.dispose();
        if (floorModel != null) floorModel.dispose();
        if (flyModel != null) flyModel.dispose();
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
        loadTableModel(); // swaps tableInstance for the Meshy model when available

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
        loadEggBall(); // swaps ballInstance for the Meshy egg mesh when available

        float fd = FlyState.FLY_RADIUS * 1.5f;
        flyModel = mb.createSphere(fd, fd, fd, 6, 6,
            new Material(ColorAttribute.createDiffuse(new Color(0.15f, 0.15f, 0.05f, 1f))),
            Usage.Position | Usage.Normal);

        floorModel = mb.createBox(60f, 0.4f, 60f,
            new Material(ColorAttribute.createDiffuse(Color.valueOf("0E2026"))),
            attrs
        );
        floorInstance = new ModelInstance(floorModel);
        floorInstance.transform.setToTranslation(0f, -0.2f, 0f);
    }

    /**
     * Tries to load the Meshy fingerprint-egg OBJ and use it as the ball. The
     * mesh is auto-scaled so its longest axis matches the ball diameter and
     * re-centred on the ball position. On any failure the procedural sphere
     * built above stays in place, so the game always has a ball.
     */
    private void loadEggBall() {
        try {
            Model loaded = new ObjLoader().loadModel(Gdx.files.internal("models/egg/egg.obj"));
            if (loaded == null || loaded.meshes.size == 0) {
                if (loaded != null) loaded.dispose();
                return;
            }
            eggBallModel = loaded;
            // The .mtl carries no Kd line; force diffuse to white so the baked
            // map_Kd texture shows (texture × white) instead of × default black.
            for (Material mat : eggBallModel.materials) {
                ColorAttribute diff = (ColorAttribute) mat.get(ColorAttribute.Diffuse);
                if (diff != null) diff.color.set(Color.WHITE);
                else mat.set(ColorAttribute.createDiffuse(Color.WHITE));
            }
            BoundingBox bb = new BoundingBox();
            eggBallModel.calculateBoundingBox(bb);
            bb.getCenter(eggCenter);
            float maxDim = Math.max(bb.getWidth(), Math.max(bb.getHeight(), bb.getDepth()));
            eggScale = (MatchWorld3D.BALL_RADIUS * 2f) / Math.max(1e-4f, maxDim);
            ballInstance = new ModelInstance(eggBallModel);
            usingEggBall = true;
        } catch (Throwable t) {
            // Corrupt/unsupported OBJ, missing texture, etc. — keep the sphere.
            Gdx.app.error("MatchArenaRenderer", "Egg ball model failed to load; using sphere", t);
            if (eggBallModel != null) { eggBallModel.dispose(); eggBallModel = null; }
            usingEggBall = false;
        }
    }

    /**
     * Tries to load the Meshy abandoned-table OBJ and use it instead of the
     * procedural box. The model's native long axis is X, while the playfield is
     * long on Z, so it is turned 90° about Y and anisotropically fit to the
     * 6×14 playfield with its top surface at {@link MatchWorld3D#TABLE_TOP_Y}.
     * Any failure keeps the procedural box. The procedural net is left in place.
     */
    private void loadTableModel() {
        try {
            Model loaded = new ObjLoader().loadModel(Gdx.files.internal("models/table/table.obj"));
            if (loaded == null || loaded.meshes.size == 0) {
                if (loaded != null) loaded.dispose();
                return;
            }
            tableObjModel = loaded;
            for (Material mat : tableObjModel.materials) {
                ColorAttribute d = (ColorAttribute) mat.get(ColorAttribute.Diffuse);
                if (d != null) d.color.set(Color.WHITE);
                else mat.set(ColorAttribute.createDiffuse(Color.WHITE));
            }
            BoundingBox bb = new BoundingBox();
            tableObjModel.calculateBoundingBox(bb);
            Vector3 center = bb.getCenter(new Vector3());
            float modelW = Math.max(1e-4f, bb.getWidth());   // local x — long axis → world length (z)
            float modelH = Math.max(1e-4f, bb.getHeight());  // local y — height
            float modelD = Math.max(1e-4f, bb.getDepth());   // local z → world width (x)
            // UNIFORM scale keeps the model's real volume/proportions. Fit to
            // whichever playfield axis needs the larger blow-up so the table at
            // least covers the 6×14 surface (the rest overhangs, reads fine).
            float s = Math.max(
                (MatchWorld3D.TABLE_HALF_LENGTH * 2f) / modelW,
                (MatchWorld3D.TABLE_HALF_WIDTH  * 2f) / modelD);
            float halfH = modelH * s * 0.5f;
            ModelInstance inst = new ModelInstance(tableObjModel);
            inst.transform.idt();
            // bbox top (the play surface) sits at TABLE_TOP_Y; body/legs hang below
            inst.transform.translate(0f, MatchWorld3D.TABLE_TOP_Y - halfH + TABLE_MODEL_Y_TRIM, 0f);
            inst.transform.rotate(0f, 1f, 0f, 90f);
            inst.transform.scale(s, s, s);
            inst.transform.translate(-center.x, -center.y, -center.z);
            tableInstance = inst;
        } catch (Throwable t) {
            Gdx.app.error("MatchArenaRenderer", "Table model failed to load; using procedural box", t);
            if (tableObjModel != null) { tableObjModel.dispose(); tableObjModel = null; }
        }
    }
}
