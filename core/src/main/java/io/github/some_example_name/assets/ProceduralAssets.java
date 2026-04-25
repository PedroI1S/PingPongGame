package io.github.some_example_name.assets;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Generated placeholder art so the project can stay self-contained while still using AssetManager. */
public final class ProceduralAssets implements Disposable {
    public static final String BALL_SPRITE_SHEET = "Ball.png";
    private static final int BALL_SHEET_COLUMNS = 3;
    private static final int BALL_SHEET_ROWS = 3;
    private static final int BALL_ANIMATION_FRAMES = 8;
    private static final TableVariation DEFAULT_TABLE_VARIATION = TableVariation.CLASSIC;

    private final Texture pixel;
    private final Texture ball;
    private final TextureRegion[] ballFrames;
    private final EnumMap<TableVariation, Texture> tableTextures;
    private final EnumMap<TableVariation, TextureRegion[]> tableFramesByVariation;
    private final TableVariation defaultTableVariation;
    private final Texture panel;
    private final Texture background;
    private final Texture glow;
    private final Texture aimRing;
    private final boolean ownsBallTexture;
    private final boolean ownsTableTextures;

    private ProceduralAssets(
        Texture pixel,
        Texture ball,
        TextureRegion[] ballFrames,
        EnumMap<TableVariation, Texture> tableTextures,
        EnumMap<TableVariation, TextureRegion[]> tableFramesByVariation,
        TableVariation defaultTableVariation,
        Texture panel,
        Texture background,
        Texture glow,
        Texture aimRing,
        boolean ownsBallTexture,
        boolean ownsTableTextures
    ) {
        this.pixel = pixel;
        this.ball = ball;
        this.ballFrames = ballFrames;
        this.tableTextures = tableTextures;
        this.tableFramesByVariation = tableFramesByVariation;
        this.defaultTableVariation = defaultTableVariation;
        this.panel = panel;
        this.background = background;
        this.glow = glow;
        this.aimRing = aimRing;
        this.ownsBallTexture = ownsBallTexture;
        this.ownsTableTextures = ownsTableTextures;
    }

    public static ProceduralAssets create() {
        Texture ballTexture = createBallTexture();
        Texture tableTexture = createTablePlaceholderTexture();

        EnumMap<TableVariation, Texture> tableTextures = new EnumMap<>(TableVariation.class);
        tableTextures.put(DEFAULT_TABLE_VARIATION, tableTexture);

        EnumMap<TableVariation, TextureRegion[]> tableFramesByVariation = new EnumMap<>(TableVariation.class);
        tableFramesByVariation.put(DEFAULT_TABLE_VARIATION, createTableFramesFromSheet(tableTexture, DEFAULT_TABLE_VARIATION));

        return new ProceduralAssets(
            createPixelTexture(),
            ballTexture,
            new TextureRegion[] { new TextureRegion(ballTexture) },
            tableTextures,
            tableFramesByVariation,
            DEFAULT_TABLE_VARIATION,
            createPanelTexture(),
            createBackgroundTexture(),
            createGlowTexture(),
            createAimRingTexture(),
            true,
            true
        );
    }

    public static ProceduralAssets create(Texture ballTexture, EnumMap<TableVariation, Texture> tableTextures) {
        Texture externalBall = Objects.requireNonNull(ballTexture, "ballTexture");
        EnumMap<TableVariation, Texture> resolvedTableTextures = new EnumMap<>(TableVariation.class);
        EnumMap<TableVariation, TextureRegion[]> tableFramesByVariation = new EnumMap<>(TableVariation.class);

        for (TableVariation variation : TableVariation.values()) {
            Texture tableTexture = Objects.requireNonNull(
                tableTextures.get(variation),
                "Missing table texture for variation " + variation.name()
            );
            tableTexture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            resolvedTableTextures.put(variation, tableTexture);
            tableFramesByVariation.put(variation, createTableFramesFromSheet(tableTexture, variation));
        }

        externalBall.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return new ProceduralAssets(
            createPixelTexture(),
            externalBall,
            createBallFramesFromSheet(externalBall),
            resolvedTableTextures,
            tableFramesByVariation,
            DEFAULT_TABLE_VARIATION,
            createPanelTexture(),
            createBackgroundTexture(),
            createGlowTexture(),
            createAimRingTexture(),
            false,
            false
        );
    }

    public Texture getPixel() {
        return pixel;
    }

    public Texture getBall() {
        return ball;
    }

    public TextureRegion getBallFrame(float normalizedProgress, boolean incoming) {
        if (ballFrames.length == 1) {
            return ballFrames[0];
        }

        float progress = MathUtils.clamp(normalizedProgress, 0f, 1f);
        float frameProgress = incoming ? (1f - progress) : progress;
        int lastFrameIndex = ballFrames.length - 1;
        int frameIndex = Math.round(frameProgress * lastFrameIndex);
        frameIndex = MathUtils.clamp(frameIndex, 0, lastFrameIndex);
        return ballFrames[frameIndex];
    }

    public TextureRegion getTableFrame(TableVariation variation, int frameIndex) {
        TextureRegion[] frames = getTableFrames(variation);
        int clampedIndex = MathUtils.clamp(frameIndex, 0, frames.length - 1);
        return frames[clampedIndex];
    }

    public TextureRegion getTableFrame(int frameIndex) {
        return getTableFrame(defaultTableVariation, frameIndex);
    }

    public int getTableFrameCount(TableVariation variation) {
        return getTableFrames(variation).length;
    }

    public int getTableFrameCount() {
        return getTableFrameCount(defaultTableVariation);
    }

    public Texture getPanel() {
        return panel;
    }

    public Texture getBackground() {
        return background;
    }

    public Texture getGlow() {
        return glow;
    }

    /** Ring outline used for the trajectory landing-spot indicator. */
    public Texture getAimRing() {
        return aimRing;
    }

    private static Texture createPixelTexture() {
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        return buildTexture(pixmap);
    }

    private static Texture createBallTexture() {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        for (int x = 0; x < pixmap.getWidth(); x++) {
            for (int y = 0; y < pixmap.getHeight(); y++) {
                float dx = x - pixmap.getWidth() * 0.5f;
                float dy = y - pixmap.getHeight() * 0.5f;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance <= 28f) {
                    float alpha = 1f - Math.max(0f, distance - 20f) / 8f;
                    pixmap.setColor(0.95f, 0.99f, 1f, alpha);
                    pixmap.drawPixel(x, y);
                }
            }
        }
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

    private static Texture createTablePlaceholderTexture() {
        Pixmap pixmap = new Pixmap(128, 128, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        pixmap.setColor(Color.valueOf("6A5B24"));
        pixmap.fillTriangle(12, 8, 116, 8, 96, 120);
        pixmap.fillTriangle(12, 8, 96, 120, 32, 120);

        pixmap.setColor(Color.valueOf("A38C3A"));
        pixmap.fillTriangle(36, 8, 92, 8, 82, 38);
        pixmap.fillTriangle(36, 8, 82, 38, 46, 38);

        pixmap.setColor(Color.valueOf("9D2A15"));
        pixmap.fillRectangle(28, 46, 72, 10);

        Texture texture = buildTexture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    private static TextureRegion[] createBallFramesFromSheet(Texture sheet) {
        int frameWidth = Math.max(1, sheet.getWidth() / BALL_SHEET_COLUMNS);
        int frameHeight = Math.max(1, sheet.getHeight() / BALL_SHEET_ROWS);
        TextureRegion[][] grid = TextureRegion.split(sheet, frameWidth, frameHeight);

        TextureRegion[] frames = new TextureRegion[BALL_ANIMATION_FRAMES];
        int index = 0;
        for (int row = 0; row < BALL_SHEET_ROWS && index < BALL_ANIMATION_FRAMES; row++) {
            for (int col = 0; col < BALL_SHEET_COLUMNS && index < BALL_ANIMATION_FRAMES; col++) {
                // Uses 3x3 sheet and skips the last (empty) slot by collecting only 8 frames.
                frames[index++] = grid[row][col];
            }
        }
        return frames;
    }

    private static TextureRegion[] createTableFramesFromSheet(Texture sheet, TableVariation variation) {
        int frameWidth = Math.max(1, sheet.getWidth() / variation.getColumns());
        int frameHeight = Math.max(1, sheet.getHeight() / variation.getRows());
        TextureRegion[][] grid = TextureRegion.split(sheet, frameWidth, frameHeight);

        TextureRegion[] frames = new TextureRegion[variation.getFrameCount()];
        int index = 0;
        for (int row = 0; row < variation.getRows() && index < variation.getFrameCount(); row++) {
            for (int col = 0; col < variation.getColumns() && index < variation.getFrameCount(); col++) {
                frames[index++] = grid[row][col];
            }
        }
        return frames;
    }

    private TextureRegion[] getTableFrames(TableVariation variation) {
        TableVariation resolvedVariation = variation == null ? defaultTableVariation : variation;
        TextureRegion[] frames = tableFramesByVariation.get(resolvedVariation);
        if (frames != null) {
            return frames;
        }

        TextureRegion[] defaultFrames = tableFramesByVariation.get(defaultTableVariation);
        if (defaultFrames == null || defaultFrames.length == 0) {
            throw new IllegalStateException("No table animation frames available");
        }
        return defaultFrames;
    }

    private static Texture createBackgroundTexture() {
        Pixmap pixmap = new Pixmap(64, 256, Pixmap.Format.RGBA8888);
        Color top = Color.valueOf("06131A");
        Color bottom = Color.valueOf("102932");
        Color line = Color.valueOf("173D44");

        for (int y = 0; y < pixmap.getHeight(); y++) {
            float t = y / (float) (pixmap.getHeight() - 1);
            float r = top.r + (bottom.r - top.r) * t;
            float g = top.g + (bottom.g - top.g) * t;
            float b = top.b + (bottom.b - top.b) * t;
            pixmap.setColor(r, g, b, 1f);
            pixmap.drawLine(0, y, pixmap.getWidth() - 1, y);
            if (y % 12 == 0) {
                pixmap.setColor(line.r, line.g, line.b, 0.55f);
                pixmap.drawLine(0, y, pixmap.getWidth() - 1, y);
            }
        }
        return buildTexture(pixmap);
    }

    private static Texture createAimRingTexture() {
        int size = 64;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        float cx = size * 0.5f;
        float cy = size * 0.5f;
        float outerR = 28f;
        float innerR = 20f;

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float dx = x - cx;
                float dy = y - cy;
                float d  = (float) Math.sqrt(dx * dx + dy * dy);
                if (d >= innerR && d <= outerR) {
                    float edge  = Math.min(d - innerR, outerR - d) / 4f;
                    float alpha = MathUtils.clamp(edge, 0f, 1f);
                    pixmap.setColor(1f, 1f, 1f, alpha);
                    pixmap.drawPixel(x, y);
                }
            }
        }
        return buildTexture(pixmap);
    }

    private static Texture createGlowTexture() {
        Pixmap pixmap = new Pixmap(64, 64, Pixmap.Format.RGBA8888);
        pixmap.setColor(0f, 0f, 0f, 0f);
        pixmap.fill();

        for (int x = 0; x < pixmap.getWidth(); x++) {
            for (int y = 0; y < pixmap.getHeight(); y++) {
                float dx = x - pixmap.getWidth() * 0.5f;
                float dy = y - pixmap.getHeight() * 0.5f;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                if (distance <= 30f) {
                    float alpha = 1f - (distance / 30f);
                    pixmap.setColor(1f, 1f, 1f, alpha * 0.9f);
                    pixmap.drawPixel(x, y);
                }
            }
        }
        return buildTexture(pixmap);
    }

    private static Texture buildTexture(Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return texture;
    }

    @Override
    public void dispose() {
        pixel.dispose();
        if (ownsBallTexture) {
            ball.dispose();
        }
        if (ownsTableTextures) {
            Set<Texture> disposed = new HashSet<>();
            for (Texture texture : tableTextures.values()) {
                if (disposed.add(texture)) {
                    texture.dispose();
                }
            }
        }
        panel.dispose();
        background.dispose();
        glow.dispose();
        aimRing.dispose();
    }
}
