package io.github.some_example_name.assets;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

/** Central asset gateway. */
public final class GameAssets implements Disposable {
    public static final String PROCEDURAL_VISUALS = "generated/procedural-visuals";
    public static final String LOGO = "ui/libgdx.png";

    private final AssetManager assetManager;
    private boolean queued;

    public GameAssets() {
        InternalFileHandleResolver resolver = new InternalFileHandleResolver();
        assetManager = new AssetManager(resolver);
        assetManager.setLoader(ProceduralAssets.class, new ProceduralAssetsLoader(resolver));
    }

    public void queueCoreAssets() {
        if (queued) {
            return;
        }
        queued = true;
        assetManager.load(PROCEDURAL_VISUALS, ProceduralAssets.class);
        assetManager.load(LOGO, Texture.class);
    }

    public boolean update() {
        return assetManager.update();
    }

    public float getProgress() {
        return assetManager.getProgress();
    }

    public boolean isReady() {
        return queued && assetManager.isLoaded(PROCEDURAL_VISUALS) && assetManager.isLoaded(LOGO);
    }

    public int getLoadedAssetsCount() {
        return assetManager.getLoadedAssets();
    }

    public int getQueuedAssetsCount() {
        return assetManager.getQueuedAssets();
    }

    public String getLoadingStageLabel() {
        if (!assetManager.isLoaded(PROCEDURAL_VISUALS)) {
            return "Building procedural world visuals";
        }
        if (!assetManager.isLoaded(LOGO)) {
            return "Loading UI pack";
        }
        return "Finalizing startup";
    }

    public ProceduralAssets getProceduralAssets() {
        return assetManager.get(PROCEDURAL_VISUALS, ProceduralAssets.class);
    }

    public Texture getLogo() {
        return assetManager.get(LOGO, Texture.class);
    }

    @Override
    public void dispose() {
        assetManager.dispose();
    }
}
