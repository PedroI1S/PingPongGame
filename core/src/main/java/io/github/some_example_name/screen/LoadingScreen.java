package io.github.some_example_name.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.some_example_name.Main;
import io.github.some_example_name.assets.ProceduralAssets;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;

public final class LoadingScreen extends BaseScreen {
    private static final String[] BOOT_LOG = {
        "> MOUNTING ASSET PACK: core/ui + procedural gameplay visuals",
        "> LOADING SPRITE SHEETS...",
        "  -> table_variation_0   [128x128 x 8 frames]   OK",
        "  -> ball_sprite         [64x64]                OK",
        "  -> impact_particles    [pooled x32]           OK",
        "> LOADING PROCEDURAL ASSET BUNDLE...",
        "  -> background_gradient                        OK",
        "  -> panel_nine_patch                           OK",
        "  -> pixel_texture                              OK",
        "> BUILDING GAME CONTEXT...",
        "  -> SpriteBatch + FitViewport                  OK",
        "  -> fonts (title / body)                       OK",
        "  -> GameSession + RNG seed                     OK",
        "> ASSET MANAGER READY. BOOT COMPLETE.",
    };

    private float elapsed;
    private float clock;

    public LoadingScreen(Main game) { super(game); }

    @Override
    public void render(float delta) {
        elapsed += delta;
        clock   += delta;
        boolean ready = context.getAssets().update() && context.getAssets().isReady();
        if (ready && elapsed >= GameConfig.LOADING_MIN_SHOW_TIME) {
            game.openMenu();
            return;
        }

        // The loading screen renders straight to the back buffer — no
        // post-process pass.  The boot text is small and the retro shader's
        // palette quantization makes it unreadable; we want this screen
        // crisp.  The pixelated look kicks in from the menu onward.
        beginFrame(0f, 0f, 0f);
        SpriteBatch batch = context.getBatch();
        Texture pixel = context.getLoadingPixel();
        BitmapFont title = context.getTitleFont();
        BitmapFont body  = context.getBodyFont();
        ProceduralAssets visuals = context.getAssets().isReady()
            ? context.getAssets().getProceduralAssets() : null;

        batch.begin();

        // Subtle red grid + scanlines on pure black + film grain (once assets exist).
        UIDraw.redGrid(batch, pixel, 0.025f);
        UIDraw.scanlines(batch, pixel);
        UIDraw.movingScanline(batch, pixel, clock, 6f);
        if (visuals != null) UIDraw.filmGrain(batch, visuals.getNoise(), clock, 0.05f);

        // Top bar
        boolean blink = ((int) (clock * 2.5f)) % 2 == 0;
        UIDraw.topBar(batch, pixel, body, context.getGlyphLayout(),
            "SYS://ARP.EXE -- BOOT SEQUENCE",
            (blink ? "* " : "  ") + "LOADING",
            Palette.RED_DIM);

        // Main panel
        float pw = 760f;
        float ph = 460f;
        float px = (GameConfig.WORLD_WIDTH - pw) * 0.5f;
        float py = (GameConfig.WORLD_HEIGHT - ph) * 0.5f;
        UIDraw.redLeftPanel(batch, pixel, px, py, pw, ph);

        // Header row
        float headerY = py + ph - 30f;
        UIDraw.fill(batch, pixel, Palette.BORDER, px, py + ph - 70f, pw, 1f);
        title.getData().setScale(1.5f);
        title.setColor(Palette.TEXT);
        title.draw(batch, "ASSETMANAGER BOOT", px + 24f, headerY);
        title.getData().setScale(2.2f);

        body.setColor(Palette.TEXT_DIM);
        body.draw(batch, context.getAssets().getLoadingStageLabel().toUpperCase(),
            px + 24f, headerY - 28f);

        float progress = context.getAssets().getProgress();
        String pct = Math.round(progress * 100f) + "%";
        title.getData().setScale(1.8f);
        title.setColor(progress >= 1f ? Palette.GREEN : Palette.RED_DIM);
        float pctW = UIDraw.textWidth(title, context.getGlyphLayout(), pct);
        title.draw(batch, pct, px + pw - 24f - pctW, headerY);
        title.getData().setScale(2.2f);

        // Progress bar
        float barX = px + 24f, barY = py + ph - 110f, barW = pw - 48f, barH = 8f;
        UIDraw.fill(batch, pixel, Color.valueOf("1A1A1A"), barX, barY, barW, barH);
        // gradient fill (faux): solid red-dim base + bright red tip
        UIDraw.fill(batch, pixel, Palette.RED_DIM, barX, barY, barW * progress, barH);
        UIDraw.fill(batch, pixel, Palette.RED, 0.85f,
            Math.max(barX, barX + barW * progress - 4f), barY, 4f, barH);

        body.setColor(Palette.TEXT_DIM);
        int loaded = context.getAssets().getLoadedAssetsCount();
        int total  = Math.max(1, context.getAssets().getQueuedAssetsCount());
        body.draw(batch, loaded + " / " + total + " assets ready",
            barX, barY - 12f);
        String rightLabel = ready ? "MENU UNLOCK PENDING" : "WAITING FOR ASSETMANAGER";
        body.setColor(ready ? Palette.GREEN : Palette.TEXT_DIM);
        float rightLabelW = UIDraw.textWidth(body, context.getGlyphLayout(), rightLabel);
        body.draw(batch, rightLabel, barX + barW - rightLabelW, barY - 12f);

        // Boot log — typewriter reveal proportional to elapsed time
        UIDraw.fill(batch, pixel, Palette.BORDER, px, py + ph - 144f, pw, 1f);
        int lineCount = Math.min(BOOT_LOG.length,
            Math.max(0, (int) Math.floor(elapsed * 7f)));
        float logY = py + 264f;
        body.setColor(Palette.TEXT_DIM);
        for (int i = 0; i < lineCount; i++) {
            String line = BOOT_LOG[i];
            boolean accent = line.startsWith("  ->");
            body.setColor(accent ? Color.valueOf("444444") : Palette.TEXT_DIM);
            body.draw(batch, line, px + 24f, logY - i * 18f);
        }
        // Blinking cursor
        if (((int) (clock * 4f)) % 2 == 0) {
            body.setColor(Palette.TEXT_DIM);
            body.draw(batch, "|", px + 24f, logY - lineCount * 18f);
        }

        // Footer note
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "Boot flow stays visible for min. " + GameConfig.LOADING_MIN_SHOW_TIME +
            "s -- GameConfig.LOADING_MIN_SHOW_TIME",
            GameConfig.WORLD_WIDTH * 0.5f, py - 28f, Color.valueOf("333333"));

        batch.end();
    }
}
