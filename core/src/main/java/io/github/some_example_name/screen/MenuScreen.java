package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.some_example_name.Main;
import io.github.some_example_name.assets.ProceduralAssets;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.input.MenuInputProcessor;
import io.github.some_example_name.model.MatchOutcome;

public final class MenuScreen extends BaseScreen {
    private final MenuInputProcessor inputProcessor;

    public MenuScreen(Main game) {
        super(game);
        this.inputProcessor = new MenuInputProcessor(game::openLoadout);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(inputProcessor);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void render(float delta) {
        beginFrame(0.03f, 0.06f, 0.08f);
        SpriteBatch batch = context.getBatch();
        ProceduralAssets visuals = context.getAssets().getProceduralAssets();
        Texture logo = context.getAssets().getLogo();

        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(visuals.getBackground(), 0f, 0f, GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT);

        batch.setColor(Color.valueOf("143A42"));
        batch.draw(visuals.getPanel(), 90f, 105f, 1100f, 510f);
        batch.setColor(Color.WHITE);

        batch.draw(logo, 140f, 492f, 120f, 120f);
        drawCentered(batch, context.getTitleFont(), GameConfig.GAME_TITLE, GameConfig.WORLD_WIDTH * 0.5f, 600f, Color.valueOf("ECFFFC"));
        drawCentered(
            batch,
            context.getBodyFont(),
            "Aim-trainer reaction clicks meet a Buckshot Roulette-style duel structure.",
            GameConfig.WORLD_WIDTH * 0.5f,
            560f,
            Color.valueOf("8CC7D0")
        );

        context.getBodyFont().setColor(Color.valueOf("D5F1F0"));
        context.getBodyFont().draw(batch, "Foundation included in this version:", 150f, 485f);
        context.getBodyFont().draw(batch, "- Screen flow: loading -> menu -> loadout -> match", 170f, 450f);
        context.getBodyFont().draw(batch, "- Shared game context with AssetManager and session state", 170f, 418f);
        context.getBodyFont().draw(batch, "- Custom generated asset bundle managed by AssetManager", 170f, 386f);
        context.getBodyFont().draw(batch, "- Player-POV duel: click incoming balls before they reach you", 170f, 354f);
        context.getBodyFont().draw(batch, "- Input processors and pooled collision particles", 170f, 322f);

        context.getBodyFont().setColor(Color.valueOf("A4E6D7"));
        context.getBodyFont().draw(batch, "Controls in match: mouse to aim, click to return, R to restart, ESC to menu.", 150f, 250f);
        context.getBodyFont().draw(batch, "Press ENTER or click to draft your pre-match item.", 150f, 212f);

        MatchOutcome lastOutcome = context.getSession().getLastOutcome();
        if (lastOutcome != MatchOutcome.NONE) {
            String result = lastOutcome == MatchOutcome.PLAYER_WIN ? "Last result: you won the duel." : "Last result: the bot won.";
            context.getBodyFont().setColor(Color.valueOf("FFD68A"));
            context.getBodyFont().draw(batch, result, 150f, 165f);
        }

        batch.end();
    }
}
