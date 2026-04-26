package io.github.some_example_name.assets;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Disposable;

/** Central asset gateway. */
public final class GameAssets implements Disposable {
    public static final String PROCEDURAL_VISUALS = "generated/procedural-visuals";
    public static final String LOGO = "ui/libgdx.png";
    public static final String SFX_BALL_HIT  = "Sounds/Effects/Ball Hit Audio.mp3";
    public static final String SFX_TABLE_HIT = "Sounds/Effects/Table Hit.mp3";
    public static final String MUSIC_BG      = "Sounds/Music/Temporary Music from AudioMass.mp3";

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
        assetManager.load(SFX_BALL_HIT,  Sound.class);
        assetManager.load(SFX_TABLE_HIT, Sound.class);
        assetManager.load(MUSIC_BG,      Music.class);
    }

    public boolean update() {
        return assetManager.update();
    }

    public float getProgress() {
        return assetManager.getProgress();
    }

    public boolean isReady() {
        return queued
            && assetManager.isLoaded(PROCEDURAL_VISUALS)
            && assetManager.isLoaded(LOGO)
            && assetManager.isLoaded(SFX_BALL_HIT)
            && assetManager.isLoaded(SFX_TABLE_HIT)
            && assetManager.isLoaded(MUSIC_BG);
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

    public Sound getBallHitSfx()    { return assetManager.get(SFX_BALL_HIT,  Sound.class); }
    public Sound getTableHitSfx()   { return assetManager.get(SFX_TABLE_HIT, Sound.class); }
    public Music getBackgroundMusic() { return assetManager.get(MUSIC_BG,    Music.class); }

    @Override
    public void dispose() {
        assetManager.dispose();
    }
}
