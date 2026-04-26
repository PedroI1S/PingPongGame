package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.some_example_name.Main;
import io.github.some_example_name.assets.ProceduralAssets;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;
import io.github.some_example_name.input.MenuInputProcessor;
import io.github.some_example_name.model.MatchOutcome;

public final class MenuScreen extends BaseScreen {
    private final MenuInputProcessor inputProcessor;
    private float clock;
    private float entered;

    public MenuScreen(Main game) {
        super(game);
        this.inputProcessor = new MenuInputProcessor(game::openLoadout);
    }

    @Override public void show() { Gdx.input.setInputProcessor(inputProcessor); entered = 0f; }
    @Override public void hide() { Gdx.input.setInputProcessor(null); }

    @Override
    public void render(float delta) {
        clock   += delta;
        entered += delta;

        beginFrame(Palette.BG.r, Palette.BG.g, Palette.BG.b);
        SpriteBatch batch = context.getBatch();
        ProceduralAssets visuals = context.getAssets().getProceduralAssets();
        Texture pixel = visuals.getPixel();
        BitmapFont title = context.getTitleFont();
        BitmapFont body  = context.getBodyFont();

        batch.begin();

        // Layered backdrop: gradient → red grid → moving scanline → static scanlines → grain.
        batch.setColor(Color.WHITE);
        batch.draw(visuals.getBackground(), 0f, 0f, GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT);
        UIDraw.redGrid(batch, pixel, 0.04f);
        UIDraw.scanlines(batch, pixel);
        UIDraw.movingScanline(batch, pixel, clock, 6f);
        UIDraw.filmGrain(batch, visuals.getNoise(), clock, 0.05f);
        UIDraw.cornerMarks(batch, pixel, 24f);

        // Top / bottom system bars
        boolean blink = ((int) (clock * 1.4f)) % 2 == 0;
        UIDraw.topBar(batch, pixel, body, context.getGlyphLayout(),
            "SYS://ARP.EXE -- BUILD 0.1.0",
            (blink ? "* " : "  ") + "LIVE",
            Palette.RED);

        MatchOutcome lastOutcome = context.getSession().getLastOutcome();
        String bottomRight = null;
        Color  bottomRightColor = Palette.TEXT_DIM;
        if (lastOutcome == MatchOutcome.PLAYER_WIN) { bottomRight = "LAST DUEL: VICTORY"; bottomRightColor = Palette.GREEN; }
        else if (lastOutcome == MatchOutcome.BOT_WIN) { bottomRight = "LAST DUEL: DEFEAT"; bottomRightColor = Palette.RED; }
        UIDraw.bottomBar(batch, pixel, body, context.getGlyphLayout(),
            "PRESS ENTER OR CLICK TO BEGIN", bottomRight, bottomRightColor);

        // ── Centered stack with entrance animations ──────────────────────
        float cx = GameConfig.WORLD_WIDTH * 0.5f;

        // Eyebrow — slide down from above
        float eyebrowP = UIDraw.entranceProgress(entered, 0f, 0.5f);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "===  AIM ROULETTE  ===",
            cx, 600f - UIDraw.slideDown(eyebrowP), Palette.RED);

        // Glitch PONG title — slide down (delay 0.1s)
        float titleP = UIDraw.entranceProgress(entered, 0.1f, 0.5f);
        UIDraw.glitchTitle(batch, title, context.getGlyphLayout(),
            "PONG", cx, 540f - UIDraw.slideDown(titleP), 5.0f, clock);

        // Red gradient line — slide up (delay 0.2s)
        float lineP = UIDraw.entranceProgress(entered, 0.2f, 0.5f);
        drawRedGradientLine(batch, pixel, cx, 358f + UIDraw.slideUp(lineP), 360f);

        // Tagline — slide up (delay 0.25s)
        float taglineP = UIDraw.entranceProgress(entered, 0.25f, 0.5f);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "AIM-TRAINER REACTION CLICKS.   ROULETTE-STYLE DUEL STRUCTURE.",
            cx, 332f + UIDraw.slideUp(taglineP), Color.valueOf("888888"));

        // Log panel — slide up (delay 0.35s)
        float panelP = UIDraw.entranceProgress(entered, 0.35f, 0.5f);
        float panelOffset = UIDraw.slideUp(panelP);
        float panelX = cx - 380f;
        float panelY = 200f + panelOffset;
        float panelW = 760f;
        float panelH = 156f;
        UIDraw.redLeftPanel(batch, pixel, panelX, panelY, panelW, panelH);

        String[] logLines = {
            "> SCREEN FLOW   : loading -> menu -> loadout -> match",
            "> INPUT SYSTEM  : mouse aim, click to return, R, ESC",
            "> DUEL ENGINE   : incoming ball, react, bot answers",
            "> LIVES         : [#][#][#][#][#]   default 5 per side",
            "> MODIFIERS     : 1 item per side, bot pick stays hidden",
        };
        for (int i = 0; i < logLines.length; i++) {
            float lineP_ = UIDraw.entranceProgress(entered, 0.4f + i * 0.06f, 0.4f);
            body.setColor(Palette.TEXT_DIM.r, Palette.TEXT_DIM.g, Palette.TEXT_DIM.b, lineP_);
            body.draw(batch, logLines[i],
                panelX + 20f, panelY + 134f - i * 24f + UIDraw.slideUp(lineP_));
        }

        // CTA — slide up (delay 0.7s)
        float ctaP = UIDraw.entranceProgress(entered, 0.7f, 0.5f);
        float ctaOffset = UIDraw.slideUp(ctaP);
        drawDangerButton(batch, pixel, body, "ENTER THE DUEL", cx, 130f + ctaOffset);

        // Ghost "settings" button below
        float ghostP = UIDraw.entranceProgress(entered, 0.8f, 0.4f);
        body.setColor(Palette.TEXT_DIM.r, Palette.TEXT_DIM.g, Palette.TEXT_DIM.b, ghostP);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "> SETTINGS", cx, 96f + UIDraw.slideUp(ghostP), Palette.TEXT_DIM);

        batch.end();
    }

    private void drawRedGradientLine(SpriteBatch batch, Texture pixel, float cx, float y, float width) {
        int segments = 24;
        float segW = width / segments;
        for (int i = 0; i < segments; i++) {
            float t = (i + 0.5f) / segments;
            float fade = 1f - Math.abs(t - 0.5f) * 2f;
            batch.setColor(Palette.RED.r, Palette.RED.g, Palette.RED.b, fade * 0.75f);
            batch.draw(pixel, cx - width * 0.5f + i * segW, y, segW, 2f);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawDangerButton(SpriteBatch batch, Texture pixel, BitmapFont font,
                                  String text, float centerX, float y) {
        float w = 360f, h = 52f;
        float x = centerX - w * 0.5f;
        UIDraw.fill(batch, pixel, Palette.RED, 0.08f, x, y, w, h);
        UIDraw.border(batch, pixel, Palette.RED, x, y, w, h, 2f);
        UIDraw.centered(batch, font, context.getGlyphLayout(), text, centerX, y + 32f, Palette.RED);
    }
}
