package io.github.some_example_name.screen;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.some_example_name.Main;
import io.github.some_example_name.assets.ProceduralAssets;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.ui.Button;
import io.github.some_example_name.ui.UIDraw;

import java.util.Arrays;
import java.util.List;

/**
 * Entry-screen menu, redone for the Buckshot-Roulette-style bunker look.
 *
 * <p>Two clickable buttons stacked centre-screen: VS BOT and MULTIPLAYER.
 * Keyboard shortcuts (ENTER / SPACE / M) still work as fallbacks.</p>
 */
public final class MenuScreen extends MenuBaseScreen {

    private float clock;

    private final Button vsBotBtn;
    private final Button multiplayerBtn;
    private final Button configBtn;
    private final List<Button> buttons;

    public MenuScreen(Main game) {
        super(game);
        float cx = GameConfig.WORLD_WIDTH * 0.5f;

        // Layout (y-axis):
        //   620  "=== AIM ROULETTE ==="
        //   560  big PONG title
        //   470  one-line tagline
        //   ─── breathing room ───
        //   320..420  the two action buttons (VS BOT / MULTIPLAYER), 100 tall
        //   295   single-line caption explaining the buttons
        //   ─── breathing room ───
        //   200..256  CONFIGURATION button, 56 tall
        // No second caption was needed — the CONFIGURATION button labels itself.
        vsBotBtn = new Button(
            cx - 220f, 320f, 200f, 100f,
            "[ VS BOT ]", Palette.RED, game::openMatch);
        multiplayerBtn = new Button(
            cx +  20f, 320f, 200f, 100f,
            "[ MULTIPLAYER ]", Palette.WARM, game::openMultiplayerLobby);
        configBtn = new Button(
            cx - 100f, 200f, 200f, 56f,
            "CONFIGURATION", Palette.TEXT_DIM, game::openConfig);

        buttons = Arrays.asList(vsBotBtn, multiplayerBtn, configBtn);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void show() {
        super.show();
        clock = 0f;
    }

    @Override
    protected List<Button> activeButtons() {
        return buttons;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(float delta) {
        clock += delta;
        updateButtonHover();

        // Menus render straight to the back buffer.  The retro post-process
        // is applied only inside NetMatchScreen so the menu
        // text never gets crushed by palette quantization.
        beginFrame(Palette.BG.r, Palette.BG.g, Palette.BG.b);
        SpriteBatch      batch   = context.getBatch();
        ProceduralAssets visuals = context.getAssets().getProceduralAssets();
        Texture          pixel   = visuals.getPixel();
        BitmapFont       title   = context.getTitleFont();
        BitmapFont       body    = context.getBodyFont();

        batch.begin();
        batch.setColor(Color.WHITE);
        UIDraw.fill(batch, pixel, Palette.BG, 0f, 0f,
            GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT);
        UIDraw.cornerMarks(batch, pixel, 24f);

        UIDraw.topBar(batch, pixel, body, context.getGlyphLayout(),
            "ARP // BOOT 0.1.0", "STATUS: LIVE", Palette.WARM);
        UIDraw.bottomBar(batch, pixel, body, context.getGlyphLayout(),
            "CLICK A BUTTON  --  OR PRESS  ENTER / M / C",
            lastOutcomeText(), lastOutcomeColor());

        float cx = GameConfig.WORLD_WIDTH * 0.5f;

        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "===  AIM ROULETTE  ===", cx, 620f, Palette.RED);

        title.getData().setScale(5.0f);
        UIDraw.centered(batch, title, context.getGlyphLayout(),
            "PONG", cx, 560f, Palette.TEXT);
        title.getData().setScale(2.2f);

        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "REACTION CLICKS.  BUNKER TABLE.  ONE OF YOU LEAVES.",
            cx, 470f, Palette.TEXT_DIM);

        for (Button b : buttons) b.draw(batch, pixel, body, context.getGlyphLayout());

        // Single caption row sits below both action buttons (which end at
        // y = 320) and above the CONFIGURATION button (top at y = 256).
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "VS BOT — RUN A LOCAL DUEL.    MULTIPLAYER — HOST OR JOIN A LAN MATCH.",
            cx, 295f, Palette.TEXT_DIM);

        // Pulsing live-dot in the bulb-warm corner of the top bar.
        boolean blink = ((int)(clock * 1.4f)) % 2 == 0;
        if (blink) {
            UIDraw.fill(batch, pixel, Palette.WARM, 0.85f,
                GameConfig.WORLD_WIDTH - 56f, GameConfig.WORLD_HEIGHT - 24f, 6f, 6f);
        }

        batch.end();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String lastOutcomeText() {
        MatchOutcome o = context.getSession().getLastOutcome();
        return switch (o) {
            case PLAYER_WIN -> "LAST DUEL: VICTORY";
            case BOT_WIN    -> "LAST DUEL: DEFEAT";
            default         -> "NO DUEL ON RECORD";
        };
    }

    private Color lastOutcomeColor() {
        MatchOutcome o = context.getSession().getLastOutcome();
        return switch (o) {
            case PLAYER_WIN -> Palette.GREEN;
            case BOT_WIN    -> Palette.RED;
            default         -> Palette.TEXT_DIM;
        };
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    protected boolean onKeyDown(int keycode) {
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            game.openMatch();
            return true;
        }
        if (keycode == Input.Keys.M) {
            game.openMultiplayerLobby();
            return true;
        }
        if (keycode == Input.Keys.C) {
            game.openConfig();
            return true;
        }
        return false;
    }
}
