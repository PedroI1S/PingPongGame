package io.github.some_example_name.assets;

import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import java.util.EnumMap;

/** Loader that plugs generated textures into AssetManager like any other asset bundle. */
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
        Texture ballTexture = assetManager.get(ProceduralAssets.BALL_SPRITE_SHEET, Texture.class);
        EnumMap<TableVariation, Texture> tableTextures = new EnumMap<>(TableVariation.class);
        for (TableVariation variation : TableVariation.values()) {
            tableTextures.put(variation, assetManager.get(variation.getSpriteSheetPath(), Texture.class));
        }
        return ProceduralAssets.create(ballTexture, tableTextures);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Array<AssetDescriptor> getDependencies(
        String fileName,
        FileHandle file,
        AssetLoaderParameters<ProceduralAssets> parameter
    ) {
        Array<AssetDescriptor> dependencies = new Array<>();
        dependencies.add(new AssetDescriptor<>(ProceduralAssets.BALL_SPRITE_SHEET, Texture.class));
        for (TableVariation variation : TableVariation.values()) {
            dependencies.add(new AssetDescriptor<>(variation.getSpriteSheetPath(), Texture.class));
        }
        return dependencies;
    }
}
