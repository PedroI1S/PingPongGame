package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import io.github.some_example_name.Main;
import io.github.some_example_name.assets.ProceduralAssets;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.input.LoadoutInputProcessor;
import io.github.some_example_name.model.ItemDefinition;

public final class LoadoutScreen extends BaseScreen {
    private static final float CARD_WIDTH = 300f;
    private static final float CARD_HEIGHT = 330f;
    private static final float CARD_Y = 210f;
    private static final float CARD_GAP = 40f;

    private final LoadoutInputProcessor inputProcessor;
    private int selectedIndex;

    public LoadoutScreen(Main game) {
        super(game);
        this.inputProcessor = new LoadoutInputProcessor(this::moveLeft, this::moveRight, this::confirm, game::openMenu);
    }

    @Override
    public void show() {
        selectedIndex = 0;
        Gdx.input.setInputProcessor(inputProcessor);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void render(float delta) {
        beginFrame(0.02f, 0.06f, 0.08f);
        SpriteBatch batch = context.getBatch();
        ProceduralAssets visuals = context.getAssets().getProceduralAssets();

        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(visuals.getBackground(), 0f, 0f, GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT);

        drawCentered(batch, context.getTitleFont(), "Choose Your Opening Edge", GameConfig.WORLD_WIDTH * 0.5f, 640f, Color.valueOf("ECFFFC"));
        drawCentered(
            batch,
            context.getBodyFont(),
            "The bot also draws one modifier. Pick the tool you want before the reaction duel starts.",
            GameConfig.WORLD_WIDTH * 0.5f,
            596f,
            Color.valueOf("96CDD0")
        );

        for (int i = 0; i < context.getSession().getOfferedItems().size; i++) {
            float x = 150f + i * (CARD_WIDTH + CARD_GAP);
            drawCard(batch, visuals, context.getSession().getOfferedItems().get(i), x, CARD_Y, i == selectedIndex);
        }

        context.getBodyFont().setColor(Color.valueOf("A4E6D7"));
        context.getBodyFont().draw(batch, "LEFT / RIGHT to change card, ENTER to lock in, ESC to go back.", 170f, 150f);
        context.getBodyFont().draw(batch, "The bot item stays hidden so the first incoming shots still have some tension.", 170f, 118f);
        batch.end();
    }

    private void drawCard(SpriteBatch batch, ProceduralAssets visuals, ItemDefinition item, float x, float y, boolean selected) {
        Color accent = item.getAccent();
        batch.setColor(selected ? Color.valueOf("17474E") : Color.valueOf("113137"));
        batch.draw(visuals.getPanel(), x, y, CARD_WIDTH, CARD_HEIGHT);

        batch.setColor(accent);
        batch.draw(visuals.getPixel(), x, y + CARD_HEIGHT - 10f, CARD_WIDTH, 10f);
        batch.draw(visuals.getPixel(), x, y, selected ? 6f : 3f, CARD_HEIGHT);
        batch.draw(visuals.getPixel(), x + CARD_WIDTH - (selected ? 6f : 3f), y, selected ? 6f : 3f, CARD_HEIGHT);

        context.getBodyFont().setColor(Color.valueOf("E9FFFC"));
        context.getBodyFont().draw(batch, item.getName(), x + 24f, y + CARD_HEIGHT - 36f);

        context.getBodyFont().setColor(accent);
        context.getBodyFont().draw(batch, item.getSummary(), x + 24f, y + CARD_HEIGHT - 76f, CARD_WIDTH - 48f, Align.left, true);

        context.getBodyFont().setColor(Color.valueOf("C6E2E5"));
        context.getBodyFont().draw(batch, item.getDetail(), x + 24f, y + 150f, CARD_WIDTH - 48f, Align.left, true);

        if (selected) {
            context.getBodyFont().setColor(Color.valueOf("FFF0B8"));
            context.getBodyFont().draw(batch, "SELECTED", x + 24f, y + 44f);
        }

        batch.setColor(Color.WHITE);
    }

    private void moveLeft() {
        selectedIndex--;
        if (selectedIndex < 0) {
            selectedIndex = context.getSession().getOfferedItems().size - 1;
        }
    }

    private void moveRight() {
        selectedIndex++;
        if (selectedIndex >= context.getSession().getOfferedItems().size) {
            selectedIndex = 0;
        }
    }

    private void confirm() {
        context.getSession().selectPlayerItem(selectedIndex);
        game.openMatch();
    }
}
