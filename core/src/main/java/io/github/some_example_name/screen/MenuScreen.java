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

    public MenuScreen(Main game) {
        super(game);
        this.inputProcessor = new MenuInputProcessor(game::openLoadout);
    }

    @Override public void show() { Gdx.input.setInputProcessor(inputProcessor); }
    @Override public void hide() { Gdx.input.setInputProcessor(null); }

    @Override
    public void render(float delta) {
        clock += delta;
        beginFrame(Palette.BG.r, Palette.BG.g, Palette.BG.b);
        SpriteBatch batch = context.getBatch();
        ProceduralAssets visuals = context.getAssets().getProceduralAssets();
        Texture pixel = visuals.getPixel();
        BitmapFont title = context.getTitleFont();
        BitmapFont body  = context.getBodyFont();

        batch.begin();

        // Background gradient + faint red grid + scanlines
        batch.setColor(Color.WHITE);
        batch.draw(visuals.getBackground(), 0f, 0f, GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT);
        UIDraw.redGrid(batch, pixel, 0.04f);
        UIDraw.scanlines(batch, pixel);
        UIDraw.cornerMarks(batch, pixel, 24f);

        // Top / bottom system bars
        boolean blink = ((int) (clock * 1.4f)) % 2 == 0;
        UIDraw.topBar(batch, pixel, body, context.getGlyphLayout(),
            "SYS://ARP.EXE -- BUILD 0.1.0",
            (blink ? "* " : "  ") + "LIVE",
            Palette.RED);

        MatchOutcome lastOutcome = context.getSession().getLastOutcome();
        String bottomRight = null;
        Color bottomRightColor = Palette.TEXT_DIM;
        if (lastOutcome == MatchOutcome.PLAYER_WIN) { bottomRight = "LAST DUEL: VICTORY"; bottomRightColor = Palette.GREEN; }
        else if (lastOutcome == MatchOutcome.BOT_WIN) { bottomRight = "LAST DUEL: DEFEAT"; bottomRightColor = Palette.RED; }
        UIDraw.bottomBar(batch, pixel, body, context.getGlyphLayout(),
            "PRESS ENTER OR CLICK TO BEGIN", bottomRight, bottomRightColor);

        // ── Centered stack ───────────────────────────────────────────────
        float cx = GameConfig.WORLD_WIDTH * 0.5f;

        // Eyebrow
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "===  AIM ROULETTE  ===", cx, 600f, Palette.RED);

        // Big "PONG" title
        title.getData().setScale(3.6f);
        UIDraw.centered(batch, title, context.getGlyphLayout(), "PONG", cx, 540f, Palette.TEXT);
        title.getData().setScale(2.2f);

        // Red gradient line (segmented to fake the gradient)
        drawRedGradientLine(batch, pixel, cx, 458f, 360f);

        // Tagline
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "AIM-TRAINER REACTION CLICKS.   ROULETTE-STYLE DUEL STRUCTURE.",
            cx, 432f, Color.valueOf("888888"));

        // Log panel
        float panelX = cx - 380f;
        float panelY = 240f;
        float panelW = 760f;
        float panelH = 156f;
        UIDraw.redLeftPanel(batch, pixel, panelX, panelY, panelW, panelH);

        body.setColor(Palette.TEXT_DIM);
        body.draw(batch, "> SCREEN FLOW   : loading -> menu -> loadout -> match",   panelX + 20f, panelY + 134f);
        body.draw(batch, "> INPUT SYSTEM  : mouse aim, click to return, R, ESC",    panelX + 20f, panelY + 110f);
        body.draw(batch, "> DUEL ENGINE   : incoming ball, react, bot answers",     panelX + 20f, panelY + 86f);
        body.draw(batch, "> LIVES         : [#][#][#][#][#]   default 5 per side", panelX + 20f, panelY + 62f);
        body.draw(batch, "> MODIFIERS     : 1 item per side, bot pick stays hidden", panelX + 20f, panelY + 38f);

        // CTA: ENTER THE DUEL — drawn as a hollow red box with text
        drawDangerButton(batch, pixel, body, "ENTER THE DUEL", cx, 168f);

        // Ghost line below
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "> PRESS ENTER", cx, 118f, Palette.TEXT_DIM);

        batch.end();
    }

    private void drawRedGradientLine(SpriteBatch batch, Texture pixel, float cx, float y, float width) {
        // Approximate the linear-gradient by stacking faded segments.
        int segments = 24;
        float segW = width / segments;
        for (int i = 0; i < segments; i++) {
            float t = (i + 0.5f) / segments;
            float fade = 1f - Math.abs(t - 0.5f) * 2f; // peak in middle
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
