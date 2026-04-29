package io.github.some_example_name.assets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

/**
 * In-memory textures used by menus and the 3D match HUD.
 *
 * <p>Everything is generated procedurally at startup — no external image files
 * are loaded. The 3D ball, table, and net are solid-color {@code ModelBuilder}
 * geometry and do not appear here.</p>
 *
 * <p>Textures kept:</p>
 * <ul>
 *   <li>{@code pixel}      — 2×2 white fill, used for solid rectangles and UI primitives</li>
 *   <li>{@code panel}      — dark bordered tile for card/slot backgrounds</li>
 *   <li>{@code background} — dark gradient with faint scanlines for menu backdrops</li>
 *   <li>{@code glow}       — soft radial falloff, overlaid on the 3D ball projection</li>
 *   <li>{@code aimRing}    — ring outline for the landing-spot indicator in the 3D match</li>
 *   <li>{@code noise}      — tileable monochrome noise for the film-grain effect on menus</li>
 * </ul>
 */
public final class ProceduralAssets implements Disposable {

    private final Texture pixel;
    private final Texture panel;
    private final Texture background;
    private final Texture glow;
    private final Texture aimRing;
    private final Texture noise;

    private ProceduralAssets(
        Texture pixel,
        Texture panel,
        Texture background,
        Texture glow,
        Texture aimRing,
        Texture noise
    ) {
        this.pixel      = pixel;
        this.panel      = panel;
        this.background = background;
        this.glow       = glow;
        this.aimRing    = aimRing;
        this.noise      = noise;
    }

    /** Build all procedural textures. Called by {@link ProceduralAssetsLoader}. */
    public static ProceduralAssets create() {
        return new ProceduralAssets(
            createPixelTexture(),
            createPanelTexture(),
            createBackgroundTexture(),
            createGlowTexture(),
            createAimRingTexture(),
            createNoiseTexture()
        );
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    /** 2×2 white pixel — use for solid fills and UI border lines. */
    public Texture getPixel()      { return pixel; }

    /** Dark-bordered card tile for UI panels. */
    public Texture getPanel()      { return panel; }

    /** Full-screen dark gradient background for menus. */
    public Texture getBackground() { return background; }

    /** Soft radial glow — overlaid around the ball projection in the 3D HUD. */
    public Texture getGlow()       { return glow; }

    /** Ring outline drawn at the ball's projected landing spot. */
    public Texture getAimRing()    { return aimRing; }

    /** Tileable monochrome noise for the film-grain overlay on menus. */
    public Texture getNoise()      { return noise; }

    // ── generators ────────────────────────────────────────────────────────────

    private static Texture createPixelTexture() {
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        return buildTexture(pixmap);
    }

    private static Texture createPanelTexture() {
        Pixmap pixmap = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.valueOf("17363D"));
        pixmap.fill();
        pixmap.setColor(Color.valueOf("86F6D6"));
        pixmap.drawRectangle(0, 0, pixmap.getWidth(), pixmap.getHeight());
        pixmap.setColor(1f, 1f, 1f, 0.08f);
        for (int y = 3; y < pixmap.getHeight(); y += 4) {
            pixmap.drawLine(1, y, pixmap.getWidth() - 2, y);
        }
        return buildTexture(pixmap);
    }

    private static Texture createBackgroundTexture() {
        Pixmap pixmap = new Pixmap(64, 256, Pixmap.Format.RGBA8888);
        Color top    = Color.valueOf("080808");
        Color bottom = Color.valueOf("050505");
        Color line   = Color.valueOf("3A0F18");

        for (int y = 0; y < pixmap.getHeight(); y++) {
            float t = y / (float)(pixmap.getHeight() - 1);
            pixmap.setColor(
                top.r + (bottom.r - top.r) * t,
                top.g + (bottom.g - top.g) * t,
                top.b + (bottom.b - top.b) * t,
                1f
            );
            pixmap.drawLine(0, y, pixmap.getWidth() - 1, y);
            // Faint red scanline every 12 px for the apocalyptic feel.
            if (y % 12 == 0) {
                pixmap.setColor(line.r, line.g, line.b, 0.35f);
                pixmap.drawLine(0, y, pixmap.getWidth() - 1, y);
            }
        }
        return buildTexture(pixmap);
    }

    private static Texture createGlowTexture() {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        float cx = pixmap.getWidth()  * 0.5f;
        float cy = pixmap.getHeight() * 0.5f;
        for (int x = 0; x < pixmap.getWidth(); x++) {
            for (int y = 0; y < pixmap.getHeight(); y++) {
                float d = (float)Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (d <= 30f) {
                    pixmap.setColor(1f, 1f, 1f, (1f - d / 30f) * 0.9f);
                    pixmap.drawPixel(x, y);
                }
            }
        }
        return buildTexture(pixmap);
    }

    private static Texture createAimRingTexture() {
        int size = 64;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();
        float cx = size * 0.5f, cy = size * 0.5f;
        float outerR = 28f, innerR = 20f;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float d = (float)Math.sqrt((x - cx) * (x - cx) + (y - cy) * (y - cy));
                if (d >= innerR && d <= outerR) {
                    float alpha = MathUtils.clamp(Math.min(d - innerR, outerR - d) / 4f, 0f, 1f);
                    pixmap.setColor(1f, 1f, 1f, alpha);
                    pixmap.drawPixel(x, y);
                }
            }
        }
        return buildTexture(pixmap);
    }

    private static Texture createNoiseTexture() {
        int size = 256;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        java.util.Random rng = new java.util.Random(0xCAFEBABEL);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float v = rng.nextFloat();
                pixmap.setColor(v, v, v, 1f);
                pixmap.drawPixel(x, y);
            }
        }
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        pixmap.dispose();
        return texture;
    }

    private static Texture buildTexture(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void dispose() {
        pixel.dispose();
        panel.dispose();
        background.dispose();
        glow.dispose();
        aimRing.dispose();
        noise.dispose();
    }
}
