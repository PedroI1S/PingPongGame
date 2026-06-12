package io.github.some_example_name.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.math.MathUtils;
import io.github.some_example_name.config.Palette;

/**
 * Buckshot-Roulette-style retro post-process pass.
 *
 * <p>Wraps a window-sized {@link FrameBuffer} and the
 * {@code assets/shaders/retro.{vert,frag}} program.  Each screen wraps
 * its draw code in {@link #begin()} / {@link #endAndBlit()}; everything
 * inside lands in the offscreen buffer, then the fragment shader applies
 * pixelation, palette quantization, dithering, vignette, and chromatic
 * aberration on the way to the back buffer.</p>
 *
 * <pre>{@code
 * @Override public void render(float delta) {
 *     postProcess.begin();
 *     // ... 3D pass ...
 *     // ... 2D HUD pass ...
 *     postProcess.endAndBlit();
 * }
 * }</pre>
 *
 * <p>The shader's virtual resolution is configurable via
 * {@link #setLowRes(float, float)} — lower means chunkier pixels.</p>
 */
public final class RetroPostProcess implements Disposable {

    /** Default virtual pixel grid (chunkiness). Buckshot-ish at 1080p. */
    public static final float DEFAULT_LOW_W = 680f;
    public static final float DEFAULT_LOW_H = 380f;

    /** Shader palette — built from {@link Palette#toShaderRgb()}. */
    public static final float[] PALETTE_RGB = Palette.toShaderRgb();

    // ── Effect knobs (tweak live during dev) ──────────────────────────────────

    private float lowResX     = DEFAULT_LOW_W;
    private float lowResY     = DEFAULT_LOW_H;
    // When true, lowResX/Y are derived from screen size * lowResPctX/Y
    private boolean lowResPercentMode = false;
    private float lowResPctX = 1f;
    private float lowResPctY = 1f;
    private boolean enabled = true;
    private float aberration  = 0.0025f;
    private float dither      = 0.06f;
    private float vignette    = 0.55f;
    private float warm        = 0.18f;
    private float punchBlur;

    // ── Internals ─────────────────────────────────────────────────────────────

    private final SpriteBatch        batch = new SpriteBatch();
    private final OrthographicCamera blitCamera = new OrthographicCamera();
    private final ShaderProgram      shader;
    private FrameBuffer              fbo;
    private int                      fboW = -1;
    private int                      fboH = -1;
    private boolean                  active;

    public RetroPostProcess() {
        shader = ShaderManager.instance().get("retro");
        // Use a percentage-based default that's milder than the previous
        // absolute defaults so the pixelation isn't overly aggressive on
        // large displays.
        setLowResPercent(0.7f, 0.7f);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Binds the offscreen framebuffer.  Anything drawn until
     * {@link #endAndBlit()} lands in the FBO instead of the back buffer.
     */
    public void begin() {
        // The punch-blur item must hit even when the retro filter is switched
        // off in settings — otherwise disabling the filter grants immunity.
        if (!enabled && punchBlur <= 0f) {
            active = false;
            return;
        }
        ensureFbo();
        fbo.begin();
        // FrameBuffer.begin() sets the GL viewport to the FBO's full size.
        // The screen's subsequent Viewport.apply() will narrow it back to
        // the play area inside the FBO.
        ScreenUtils.clear(0f, 0f, 0f, 1f, true);
        active = true;
    }

    /**
     * Unbinds the FBO and blits its contents to the back buffer through the
     * retro shader.  Safe to call once per frame after {@link #begin()}.
     */
    public void endAndBlit() {
        if (!active) return;
        fbo.end();
        active = false;

        // Camera + draw quad live in LOGICAL pixels (same coordinate space the
        // FBO was rendered with thanks to HdpiMode.Pixels in HdpiUtils).
        // glViewport must be in PHYSICAL pixels — the back buffer is sized in
        // physical pixels regardless of HdpiMode, so we use getBackBuffer*().
        // Without this distinction the blit only paints the bottom-left
        // quadrant of the window on Retina displays.
        int w  = Gdx.graphics.getWidth();
        int h  = Gdx.graphics.getHeight();
        int bw = Gdx.graphics.getBackBufferWidth();
        int bh = Gdx.graphics.getBackBufferHeight();
        Gdx.gl.glViewport(0, 0, bw, bh);
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        blitCamera.setToOrtho(false, w, h);
        batch.setProjectionMatrix(blitCamera.combined);
        batch.setColor(Color.WHITE);
        batch.setShader(shader);
        batch.begin();
        // Bind uniforms after begin() — SpriteBatch only flushes (and binds the
        // shader) on the first draw.  Setting them now keeps them attached.
        // Compute effective low-res values. If percent-mode is enabled the
        // values are derived from the current (logical) window size so the
        // effect scales across resolutions.
        float effLowX = lowResX;
        float effLowY = lowResY;
        if (lowResPercentMode) {
            effLowX = Math.max(1f, Gdx.graphics.getWidth() * lowResPctX);
            effLowY = Math.max(1f, Gdx.graphics.getHeight() * lowResPctY);
        }
        if (!enabled) {
            // Punch-only pass: neutralize the retro stylization so the player
            // who turned the filter off just gets the blur, not the pixel look.
            // (The shader's punch branch bypasses palette/dither on its own.)
            effLowX = Math.max(1f, Gdx.graphics.getBackBufferWidth());
            effLowY = Math.max(1f, Gdx.graphics.getBackBufferHeight());
        }
        boolean styled = enabled;
        shader.setUniformf("u_lowRes",     effLowX, effLowY);
        shader.setUniformf("u_aberration", styled ? aberration : 0f);
        shader.setUniformf("u_dither",     styled ? dither     : 0f);
        shader.setUniformf("u_vignette",   styled ? vignette   : 0f);
        shader.setUniformf("u_warm",       styled ? warm       : 0f);
        shader.setUniformf("u_blur",       punchBlur);
        shader.setUniform3fv("u_palette[0]", PALETTE_RGB, 0, PALETTE_RGB.length);

        Texture tex = fbo.getColorBufferTexture();
        // FBO Y is flipped relative to screen — pass srcY=h, srcHeight negative
        // via the explicit overload to flip during the blit.
        batch.draw(tex, 0f, 0f, w, h, 0, 0, tex.getWidth(), tex.getHeight(), false, true);
        batch.end();
        batch.setShader(null);
    }

    /** Window resized → recreate the FBO at the new size. */
    public void resize(int width, int height) {
        if (fbo != null && (fbo.getWidth() != width || fbo.getHeight() != height)) {
            fbo.dispose();
            fbo = null;
        }
    }

    // ── Tunables ──────────────────────────────────────────────────────────────

    /** Set absolute low-res virtual resolution (smaller -> chunkier). */
    public void setLowRes(float w, float h) { this.lowResPercentMode = false; this.lowResX = w; this.lowResY = h; }

    /**
     * Set low-res as a fraction of the current window size. Values are in
     * [0,1], where 1.0 means full-resolution (no downscale) and smaller
     * values produce chunkier pixels. The effective low-res is recomputed
     * each frame so it adapts to resizes.
     */
    public void setLowResPercent(float fracX, float fracY) {
        this.lowResPercentMode = true;
        this.lowResPctX = MathUtils.clamp(fracX, 0.01f, 1f);
        this.lowResPctY = MathUtils.clamp(fracY, 0.01f, 1f);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
    public void setAberration(float a)      { this.aberration = a; }
    public void setDither(float d)          { this.dither = d; }
    public void setVignette(float v)        { this.vignette = v; }
    public void setWarm(float w)            { this.warm = w; }
    public void setPunchBlur(float t)       { this.punchBlur = Math.max(0f, Math.min(1f, t)); }

    @Override
    public void dispose() {
        if (fbo != null) fbo.dispose();
        // shader is owned (and disposed) by ShaderManager
        batch.dispose();
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void ensureFbo() {
        // libGDX is in default HdpiMode.Logical, so Viewport.apply() ends up
        // calling glViewport at PHYSICAL (backbuffer) pixels via HdpiUtils'
        // auto-conversion.  The FBO must therefore live at backbuffer size
        // too — using getWidth() (logical) here would leave the screen
        // painting only the bottom-left quadrant on Retina displays.
        int w = Math.max(1, Gdx.graphics.getBackBufferWidth());
        int h = Math.max(1, Gdx.graphics.getBackBufferHeight());
        if (fbo == null || fboW != w || fboH != h) {
            if (fbo != null) fbo.dispose();
            fbo = new FrameBuffer(Pixmap.Format.RGB888, w, h, true);
            fbo.getColorBufferTexture().setFilter(
                Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            fboW = w;
            fboH = h;
        }
    }
}
