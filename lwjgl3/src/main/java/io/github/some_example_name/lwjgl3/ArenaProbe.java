package io.github.some_example_name.lwjgl3;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.some_example_name.model.ItemType;
import io.github.some_example_name.render.ItemPhaseRenderer;
import io.github.some_example_name.render.MatchArenaRenderer;
import io.github.some_example_name.world.FlyState;

import java.util.List;

/**
 * Dev tool — renders either the in-game arena ("arena", default) or the raw
 * table OBJ with no fitting transform ("raw") and writes a PNG, then exits.
 *
 * <pre>./gradlew :lwjgl3:arenaProbe [--args=raw]   →  /tmp/arena_probe.png</pre>
 */
public final class ArenaProbe {
    public static void main(String[] args) {
        boolean raw = args.length > 0 && "raw".equals(args[0]);
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("arena-probe");
        cfg.setWindowedMode(1280, 720);
        cfg.setInitialVisible(false);
        new Lwjgl3Application(raw ? new RawModelProbe() : new ArenaSceneProbe(), cfg);
    }

    /** The in-game arena exactly as NetMatchScreen renders it (P1 view),
     *  plus the item shelf and a few flies so every prop is reviewable. */
    private static final class ArenaSceneProbe extends ApplicationAdapter {
        private MatchArenaRenderer arena;
        private ItemPhaseRenderer items;
        private int frames;

        @Override
        public void create() {
            arena = new MatchArenaRenderer(true);
            arena.ensureInitialized();
            arena.setBallPosition(0f, 2.6f, 4f);
            items = new ItemPhaseRenderer(true);
            items.load(
                List.of(ItemType.PATCH_KIT, ItemType.WIDE_PADDLE, ItemType.SLOW_MO,
                        ItemType.STEAL, ItemType.FAST_SERVE),
                List.of(ItemType.TINY_PADDLE, ItemType.PUNCH, ItemType.FLY_BAIT,
                        ItemType.COIN_FLIP));
            items.update(0.6f); // settle the hover/spin animation a bit
            arena.setFlies(
                List.of(new FlyState(-1.6f, 2.6f), new FlyState(1.3f, 2.2f)),
                List.of(new FlyState(0.4f, -3.4f)));
            arena.tickFlyBuzz(0.1f);
            arena.setLivesDisplay(3, 5); // preview the bulb racks mid-match
        }

        @Override
        public void render() {
            arena.render3DScene(true);
            arena.getModelBatch().begin(arena.getCamera());
            items.render(arena.getModelBatch(), arena.getEnvironment());
            arena.getModelBatch().end();
            capture(++frames);
        }

        @Override
        public void dispose() {
            items.dispose();
            arena.dispose();
        }
    }

    /** The raw OBJ, identity transform, viewer-style three-quarter camera. */
    private static final class RawModelProbe extends ApplicationAdapter {
        private Model model;
        private ModelBatch batch;
        private Environment env;
        private PerspectiveCamera cam;
        private int frames;

        @Override
        public void create() {
            model = new ObjLoader().loadModel(
                Gdx.files.internal("models/table/table.obj"),
                new TextureProvider.FileTextureProvider(
                    Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear,
                    Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat, true));
            for (Material mat : model.materials) {
                ColorAttribute d = (ColorAttribute) mat.get(ColorAttribute.Diffuse);
                if (d != null) d.color.set(Color.WHITE);
                else mat.set(ColorAttribute.createDiffuse(Color.WHITE));
                mat.set(IntAttribute.createCullFace(GL20.GL_NONE));
            }
            batch = new ModelBatch();
            env = new Environment();
            env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.8f, 0.8f, 0.8f, 1f));
            env.add(new DirectionalLight().set(0.9f, 0.9f, 0.9f, -0.4f, -0.8f, -0.3f));
            cam = new PerspectiveCamera(50f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            cam.position.set(-1.6f, 1.4f, 1.8f);   // three-quarter view like a model viewer
            cam.lookAt(0f, 0f, 0f);
            cam.near = 0.05f;
            cam.far = 50f;
            cam.update();
        }

        @Override
        public void render() {
            ScreenUtils.clear(0.12f, 0.12f, 0.14f, 1f, true);
            batch.begin(cam);
            batch.render(new ModelInstance(model), env);
            batch.end();
            capture(++frames);
        }

        @Override
        public void dispose() {
            batch.dispose();
            model.dispose();
        }
    }

    private static void capture(int frame) {
        if (frame != 3) return;
        Pixmap shot = Pixmap.createFromFrameBuffer(
            0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        PixmapIO.writePNG(new FileHandle("/tmp/arena_probe.png"), shot, -1, true);
        shot.dispose();
        System.out.println("[ArenaProbe] wrote /tmp/arena_probe.png");
        Gdx.app.exit();
    }

    private ArenaProbe() {}
}
