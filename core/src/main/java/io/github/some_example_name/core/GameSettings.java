package io.github.some_example_name.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.Graphics;

/** Runtime settings for the local app session. */
public final class GameSettings {
    private static final String PREFS_NAME = "aim-roulette-settings";

    /**
     * Virtual-pixel scale presets — fraction of the current window size used
     * as the shader's chunky-pixel grid.  Smaller = chunkier.  These err on
     * the readable side; the {@code BUNKER} extreme is still legible at
     * 1280×720.  The post-process can also be turned off entirely via
     * {@link #setPostProcessingEnabled}.
     */
    private static final float[] RETRO_RESOLUTION_PRESETS = { 0.85f, 0.65f, 0.50f, 0.38f, 0.28f };
    private static final String[] RETRO_RESOLUTION_LABELS = { "SOFT", "MEDIUM", "RETRO", "CHUNKY", "BUNKER" };
    private static final int[][] WINDOW_RESOLUTION_PRESETS = {
        { 960,  540 },
        { 1024, 576 },
        { 1280, 720 },
        { 1366, 768 },
        { 1600, 900 },
        { 1920, 1080 },
        { 2560, 1440 }
    };

    private static final String KEY_POST_FX        = "postFx";
    private static final String KEY_RETRO_INDEX    = "retroIndex";
    private static final String KEY_FULLSCREEN     = "fullscreen";
    private static final String KEY_WINDOW_INDEX   = "windowIndex";
    private static final String KEY_MASTER_VOL     = "masterVol";
    private static final String KEY_MUSIC_VOL      = "musicVol";
    private static final String KEY_SFX_VOL        = "sfxVol";
    private static final String KEY_FPS_COUNTER    = "fpsCounter";
    private static final String KEY_SCREEN_SHAKE   = "screenShake";

    /**
     * Default OFF.  The retro pixel filter is a stylistic choice the player
     * opts in to from the settings screen — leaving it on by default makes
     * fonts hard to read on first launch.
     */
    private boolean postProcessingEnabled = false;
    private int retroResolutionPresetIndex = 1; // MEDIUM — gentle when first turned on
    private boolean fullscreen;
    private int windowResolutionPresetIndex = 2; // 1280×720 — matches the world space

    // ── Audio (0–100) ─────────────────────────────────────────────────────────
    private int masterVolume = 80;
    private int musicVolume  = 60;
    private int sfxVolume    = 90;

    // ── Game preferences ──────────────────────────────────────────────────────
    private boolean showFpsCounter = false;
    private boolean screenShake    = true;

    public void load() {
        if (Gdx.app == null) return;
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        postProcessingEnabled = prefs.getBoolean(KEY_POST_FX, postProcessingEnabled);
        retroResolutionPresetIndex = clampIndex(
            prefs.getInteger(KEY_RETRO_INDEX, retroResolutionPresetIndex),
            RETRO_RESOLUTION_PRESETS.length
        );
        fullscreen = prefs.getBoolean(KEY_FULLSCREEN, fullscreen);
        windowResolutionPresetIndex = clampIndex(
            prefs.getInteger(KEY_WINDOW_INDEX, windowResolutionPresetIndex),
            WINDOW_RESOLUTION_PRESETS.length
        );
        masterVolume   = clampVol(prefs.getInteger(KEY_MASTER_VOL, masterVolume));
        musicVolume    = clampVol(prefs.getInteger(KEY_MUSIC_VOL,  musicVolume));
        sfxVolume      = clampVol(prefs.getInteger(KEY_SFX_VOL,    sfxVolume));
        showFpsCounter = prefs.getBoolean(KEY_FPS_COUNTER, showFpsCounter);
        screenShake    = prefs.getBoolean(KEY_SCREEN_SHAKE, screenShake);
    }

    public void save() {
        if (Gdx.app == null) return;
        Preferences prefs = Gdx.app.getPreferences(PREFS_NAME);
        prefs.putBoolean(KEY_POST_FX, postProcessingEnabled);
        prefs.putInteger(KEY_RETRO_INDEX, retroResolutionPresetIndex);
        prefs.putBoolean(KEY_FULLSCREEN, fullscreen);
        prefs.putInteger(KEY_WINDOW_INDEX, windowResolutionPresetIndex);
        prefs.putInteger(KEY_MASTER_VOL, masterVolume);
        prefs.putInteger(KEY_MUSIC_VOL,  musicVolume);
        prefs.putInteger(KEY_SFX_VOL,    sfxVolume);
        prefs.putBoolean(KEY_FPS_COUNTER, showFpsCounter);
        prefs.putBoolean(KEY_SCREEN_SHAKE, screenShake);
        prefs.flush();
    }

    private static int clampVol(int v) { return Math.max(0, Math.min(100, v)); }

    // ── Audio accessors ───────────────────────────────────────────────────────

    public int  getMasterVolume()         { return masterVolume; }
    public void setMasterVolume(int v)    { masterVolume = clampVol(v); save(); }
    public int  getMusicVolume()          { return musicVolume; }
    public void setMusicVolume(int v)     { musicVolume = clampVol(v); save(); }
    public int  getSfxVolume()            { return sfxVolume; }
    public void setSfxVolume(int v)       { sfxVolume = clampVol(v); save(); }
    /** Composite gain for music — master × music. */
    public float getMusicGain()           { return masterVolume / 100f * musicVolume / 100f; }
    /** Composite gain for SFX — master × sfx. */
    public float getSfxGain()             { return masterVolume / 100f * sfxVolume / 100f; }

    // ── Game-preference accessors ─────────────────────────────────────────────

    public boolean isShowFpsCounter()     { return showFpsCounter; }
    public void    setShowFpsCounter(boolean v) { showFpsCounter = v; save(); }
    public boolean isScreenShakeEnabled() { return screenShake; }
    public void    setScreenShakeEnabled(boolean v) { screenShake = v; save(); }

    public boolean isPostProcessingEnabled() {
        return postProcessingEnabled;
    }

    public void togglePostProcessing() {
        postProcessingEnabled = !postProcessingEnabled;
        save();
    }

    public void setPostProcessingEnabled(boolean enabled) {
        postProcessingEnabled = enabled;
        save();
    }

    public float getRetroResolutionScale() {
        return RETRO_RESOLUTION_PRESETS[retroResolutionPresetIndex];
    }

    public void cycleRetroResolution(int delta) {
        retroResolutionPresetIndex = wrapIndex(retroResolutionPresetIndex + delta, RETRO_RESOLUTION_PRESETS.length);
        save();
    }

    public String getRetroResolutionLabel() {
        return RETRO_RESOLUTION_LABELS[retroResolutionPresetIndex];
    }

    public int getRetroResolutionIndex() {
        return retroResolutionPresetIndex;
    }

    public int getRetroResolutionPresetCount() {
        return RETRO_RESOLUTION_PRESETS.length;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void toggleFullscreen() {
        fullscreen = !fullscreen;
        save();
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        save();
    }

    public void cycleWindowResolution(int delta) {
        windowResolutionPresetIndex = wrapIndex(windowResolutionPresetIndex + delta, WINDOW_RESOLUTION_PRESETS.length);
        save();
    }

    public int getWindowWidth() {
        return WINDOW_RESOLUTION_PRESETS[windowResolutionPresetIndex][0];
    }

    public int getWindowHeight() {
        return WINDOW_RESOLUTION_PRESETS[windowResolutionPresetIndex][1];
    }

    public String getWindowResolutionLabel() {
        return getWindowWidth() + "x" + getWindowHeight();
    }

    public String getWindowModeLabel() {
        return fullscreen ? "FULLSCREEN" : "WINDOWED";
    }

    public int getWindowResolutionIndex() {
        return windowResolutionPresetIndex;
    }

    public int getWindowResolutionPresetCount() {
        return WINDOW_RESOLUTION_PRESETS.length;
    }

    public void applyWindowMode() {
        if (Gdx.graphics == null) return;

        if (fullscreen) {
            Graphics.DisplayMode displayMode = Gdx.graphics.getDisplayMode();
            Gdx.graphics.setFullscreenMode(displayMode);
        } else {
            Gdx.graphics.setWindowedMode(getWindowWidth(), getWindowHeight());
        }
    }

    public void resetWindowResolutionToDefault() {
        windowResolutionPresetIndex = 0;
        save();
    }

    public String getSummary() {
        return (postProcessingEnabled ? "FX ON" : "FX OFF")
            + " • " + getRetroResolutionLabel()
            + " • " + getWindowResolutionLabel()
            + " • " + getWindowModeLabel();
    }

    private static int clampIndex(int index, int length) {
        if (length <= 0) return 0;
        return Math.max(0, Math.min(index, length - 1));
    }

    private static int wrapIndex(int index, int length) {
        if (length <= 0) return 0;
        int value = index % length;
        return value < 0 ? value + length : value;
    }
}