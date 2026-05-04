package io.github.some_example_name.assets;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;

/**
 * Plugs {@link ProceduralAssets} into {@link AssetManager} like any other bundle.
 * All textures are generated in-memory — no external image files are needed.
 */
public final class ProceduralAssetsLoader
    extends SynchronousAssetLoader<ProceduralAssets, AssetLoaderParameters<ProceduralAssets>> {

    public ProceduralAssetsLoader(FileHandleResolver resolver) {
        super(resolver);
    }

    @Override
    public ProceduralAssets load(
        AssetManager assetManager,
        String fileName,
        FileHandle file,
        AssetLoaderParameters<ProceduralAssets> parameter
    ) {
        return ProceduralAssets.create();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Array<AssetDescriptor> getDependencies(
        String fileName,
        FileHandle file,
        AssetLoaderParameters<ProceduralAssets> parameter
    ) {
        return new Array<>();  // no external file dependencies
    }
}
