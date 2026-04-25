package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.some_example_name.Main;
import io.github.some_example_name.assets.ProceduralAssets;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.input.MatchInputController;
import io.github.some_example_name.model.ItemDefinition;
import io.github.some_example_name.model.MatchOutcome;
import io.github.some_example_name.world.MatchWorld;

public final class MatchScreen extends BaseScreen {
    private static final Color HUD_TEXT_COLOR = Color.valueOf("E9FFFC");
    private static final Color HUD_STATS_COLOR = Color.valueOf("A9DCE1");
    private static final Color HUD_STATUS_COLOR = Color.valueOf("FFF0B8");
    private static final Color HUD_HINT_COLOR = Color.valueOf("A4E6D7");
    private static final Color OUTCOME_TITLE_COLOR = Color.valueOf("ECFFFC");
    private static final Color OUTCOME_SUBTITLE_COLOR = Color.valueOf("B7DFE4");

    private MatchWorld world;
    private MatchInputController inputController;
    private boolean outcomeRecorded;

    public MatchScreen(Main game) {
        super(game);
    }

    @Override
    public void show() {
        world = new MatchWorld(context.getSession().buildMatchConfig(), context.getSession().getRandom());
        inputController = new MatchInputController(context.getViewport());
        outcomeRecorded = false;
        Gdx.input.setInputProcessor(inputController);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void render(float delta) {
        if (inputController.consumeMenuRequest()) {
            game.openMenu();
            return;
        }
        if (inputController.consumeRestartRequest()) {
            game.openMatch();
            return;
        }

        world.update(delta, inputController);
        if (world.isMatchOver() && !outcomeRecorded) {
            context.getSession().setLastOutcome(world.getOutcome());
            outcomeRecorded = true;
        }

        beginFrame(0.02f, 0.05f, 0.07f);
        SpriteBatch batch = context.getBatch();
        ProceduralAssets visuals = context.getAssets().getProceduralAssets();

        batch.begin();
        world.render(batch, visuals, inputController);
        drawHud(batch);
        if (world.isMatchOver()) {
            drawOutcomeOverlay(batch, visuals);
        }
        batch.end();
    }

    private void drawHud(SpriteBatch batch) {
        ItemDefinition playerItem = context.getSession().getPlayerItem();
        ItemDefinition botItem = context.getSession().getBotItem();

        context.getBodyFont().setColor(HUD_TEXT_COLOR);
        context.getBodyFont().draw(batch, "YOU  " + world.getPlayerLives(), GameConfig.HUD_PADDING, 680f);
        context.getBodyFont().draw(batch, "BOT  " + world.getBotLives(), 1120f, 680f);

        if (playerItem != null) {
            context.getBodyFont().setColor(playerItem.getAccent());
            context.getBodyFont().draw(batch, "Item: " + playerItem.getName(), GameConfig.HUD_PADDING, 646f);
        }
        if (botItem != null) {
            context.getBodyFont().setColor(botItem.getAccent());
            context.getBodyFont().draw(batch, "Bot item: " + botItem.getName(), 1000f, 646f);
        }

        context.getBodyFont().setColor(HUD_STATS_COLOR);
        context.getBodyFont().draw(
            batch,
            String.format("Read window: %.2fs", world.getDisplayedReadWindow()),
            500f,
            680f
        );
        context.getBodyFont().draw(batch, "Rally: " + world.getRallyCount(), 600f, 646f);

        if (!world.isMatchOver()) {
            drawCentered(batch, context.getBodyFont(), world.getStatusText(), GameConfig.WORLD_WIDTH * 0.5f, 134f, HUD_STATUS_COLOR);
            drawCentered(
                batch,
                context.getBodyFont(),
                "Move the cursor and click the ball before it fills the hit window. R restarts. ESC goes to menu.",
                GameConfig.WORLD_WIDTH * 0.5f,
                102f,
                HUD_HINT_COLOR
            );
        }
    }

    private void drawOutcomeOverlay(SpriteBatch batch, ProceduralAssets visuals) {
        batch.setColor(0f, 0f, 0f, 0.45f);
        batch.draw(visuals.getPixel(), 0f, 0f, GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT);
        batch.setColor(Color.WHITE);

        MatchOutcome outcome = world.getOutcome();
        String title = outcome == MatchOutcome.PLAYER_WIN ? "Duel won" : "Bot wins this round";
        String subtitle = outcome == MatchOutcome.PLAYER_WIN
            ? "The reaction loop works: incoming shot, click return, bot answer, repeat."
            : "The foundation is there. Next tuning pass can focus on difficulty and feedback.";

        drawCentered(batch, context.getTitleFont(), title, GameConfig.WORLD_WIDTH * 0.5f, 420f, OUTCOME_TITLE_COLOR);
        drawCentered(batch, context.getBodyFont(), subtitle, GameConfig.WORLD_WIDTH * 0.5f, 372f, OUTCOME_SUBTITLE_COLOR);
        drawCentered(
            batch,
            context.getBodyFont(),
            "Press R to replay this setup or ESC to go back to the menu.",
            GameConfig.WORLD_WIDTH * 0.5f,
            330f,
            HUD_STATUS_COLOR
        );
    }
}
