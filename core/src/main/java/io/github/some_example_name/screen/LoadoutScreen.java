package io.github.some_example_name.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import io.github.some_example_name.Main;
import io.github.some_example_name.assets.ProceduralAssets;
import io.github.some_example_name.config.GameConfig;
import io.github.some_example_name.config.Palette;
import io.github.some_example_name.input.LoadoutInputProcessor;
import io.github.some_example_name.model.ItemDefinition;

public final class LoadoutScreen extends BaseScreen {
    private static final float CARD_WIDTH  = 280f;
    private static final float CARD_HEIGHT = 320f;
    private static final float CARD_GAP    = 32f;
    private static final float CARD_Y      = 195f;

    private final LoadoutInputProcessor inputProcessor;
    private int selectedIndex;
    private float clock;
    private float entered;

    public LoadoutScreen(Main game) {
        super(game);
        this.inputProcessor = new LoadoutInputProcessor(this::moveLeft, this::moveRight, this::confirm, game::openMenu);
    }

    @Override
    public void show() {
        selectedIndex = 0;
        entered = 0f;
        Gdx.input.setInputProcessor(inputProcessor);
    }

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

        batch.setColor(Color.WHITE);
        batch.draw(visuals.getBackground(), 0f, 0f, GameConfig.WORLD_WIDTH, GameConfig.WORLD_HEIGHT);
        UIDraw.redGrid(batch, pixel, 0.05f);
        UIDraw.scanlines(batch, pixel);
        UIDraw.movingScanline(batch, pixel, clock, 6f);
        UIDraw.filmGrain(batch, visuals.getNoise(), clock, 0.05f);
        UIDraw.cornerMarks(batch, pixel, 24f);

        UIDraw.topBar(batch, pixel, body, context.getGlyphLayout(),
            "<- BACK TO MENU (ESC)", "PHASE // LOADOUT", Palette.TEXT_DIM);
        UIDraw.bottomBar(batch, pixel, body, context.getGlyphLayout(),
            "LEFT/RIGHT TO CHANGE CARD -- ENTER TO LOCK IN",
            "BOT ITEM: [REDACTED]", Palette.TEXT_DIM);

        float cx = GameConfig.WORLD_WIDTH * 0.5f;

        // Heading — slide down
        float eyebrowP = UIDraw.entranceProgress(entered, 0f, 0.4f);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "===  PRE-MATCH DRAFT  ===",
            cx, 660f - UIDraw.slideDown(eyebrowP), Palette.RED);

        float titleP = UIDraw.entranceProgress(entered, 0.05f, 0.4f);
        title.getData().setScale(2.2f);
        UIDraw.centered(batch, title, context.getGlyphLayout(),
            "CHOOSE YOUR EDGE",
            cx, 615f - UIDraw.slideDown(titleP), Palette.TEXT);
        title.getData().setScale(2.2f);

        float subP = UIDraw.entranceProgress(entered, 0.1f, 0.4f);
        UIDraw.centered(batch, body, context.getGlyphLayout(),
            "THE BOT DRAWS ONE TOO -- YOU WON'T SEE WHICH.",
            cx, 555f - UIDraw.slideDown(subP), Color.valueOf("555555"));

        // Cards — staggered slide-in from the right
        var items = context.getSession().getOfferedItems();
        float totalW = items.size * CARD_WIDTH + (items.size - 1) * CARD_GAP;
        float startX = cx - totalW * 0.5f;
        for (int i = 0; i < items.size; i++) {
            float cardP = UIDraw.entranceProgress(entered, 0.1f + i * 0.08f, 0.4f);
            float slideX = (1f - cardP) * 30f;
            float x = startX + i * (CARD_WIDTH + CARD_GAP) + slideX;
            drawCard(batch, pixel, title, body, items.get(i), x, CARD_Y, i == selectedIndex);
        }

        // Confirm row — selected label sits between cards bottom (y=195)
        // and confirm button top (y=120). Body font is ~16 px tall so y=160
        // gives ~25 px of breathing room from the cards above and ~25 px
        // from the button border below.
        if (items.size > 0) {
            ItemDefinition sel = items.get(selectedIndex);
            String label = sel.getName().toUpperCase() + " -- " + sel.getSummary().toUpperCase();
            UIDraw.centered(batch, body, context.getGlyphLayout(),
                label, cx, 160f, sel.getAccent());
            drawConfirmButton(batch, pixel, body, "ENTER THE DUEL ->", cx, 70f, sel.getAccent());
        }

        batch.end();
    }

    private void drawCard(SpriteBatch batch, Texture pixel, BitmapFont title, BitmapFont body,
                          ItemDefinition item, float x, float y, boolean selected) {
        Color accent = item.getAccent();

        // Card surface
        UIDraw.fill(batch, pixel, Palette.SURFACE, x, y, CARD_WIDTH, CARD_HEIGHT);
        if (selected) {
            UIDraw.fill(batch, pixel, accent, 0.06f, x, y, CARD_WIDTH, CARD_HEIGHT);
            UIDraw.border(batch, pixel, accent, x, y, CARD_WIDTH, CARD_HEIGHT, 1f);
        } else {
            UIDraw.border(batch, pixel, Palette.BORDER, x, y, CARD_WIDTH, CARD_HEIGHT, 1f);
        }

        // Top accent bar
        UIDraw.fill(batch, pixel, accent, x, y + CARD_HEIGHT - (selected ? 4f : 2f),
            CARD_WIDTH, selected ? 4f : 2f);

        // Item name
        title.getData().setScale(1.6f);
        title.setColor(selected ? Palette.TEXT : Color.valueOf("666666"));
        title.draw(batch, item.getName().toUpperCase(), x + 20f, y + CARD_HEIGHT - 32f);
        title.getData().setScale(2.2f);

        // Summary in accent color
        body.setColor(accent.r, accent.g, accent.b, selected ? 1f : 0.55f);
        body.draw(batch, item.getSummary().toUpperCase(),
            x + 20f, y + CARD_HEIGHT - 80f, CARD_WIDTH - 40f, Align.left, true);

        // Divider
        UIDraw.fill(batch, pixel, selected ? accent : Palette.BORDER,
            selected ? 0.27f : 1f, x + 20f, y + CARD_HEIGHT - 138f, CARD_WIDTH - 40f, 1f);

        // Detail text
        body.setColor(selected ? Color.valueOf("999999") : Color.valueOf("444444"));
        body.draw(batch, item.getDetail(),
            x + 20f, y + CARD_HEIGHT - 154f, CARD_WIDTH - 40f, Align.left, true);

        // "LOCKED IN" footer when selected
        if (selected) {
            UIDraw.fill(batch, pixel, accent, 0.08f, x, y, CARD_WIDTH, 30f);
            UIDraw.fill(batch, pixel, accent, 0.33f, x, y + 30f, CARD_WIDTH, 1f);
            body.setColor(accent);
            UIDraw.centered(batch, body, context.getGlyphLayout(),
                "[ LOCKED IN ]", x + CARD_WIDTH * 0.5f, y + 20f, accent);
        }
    }

    private void drawConfirmButton(SpriteBatch batch, Texture pixel, BitmapFont font,
                                   String text, float centerX, float y, Color accent) {
        float w = 380f, h = 48f;
        float x = centerX - w * 0.5f;
        UIDraw.fill(batch, pixel, accent, 0.10f, x, y, w, h);
        UIDraw.border(batch, pixel, accent, x, y, w, h, 2f);
        UIDraw.centered(batch, font, context.getGlyphLayout(), text, centerX, y + 30f, accent);
    }

    private void moveLeft() {
        var items = context.getSession().getOfferedItems();
        if (items.size == 0) return;
        selectedIndex = (selectedIndex - 1 + items.size) % items.size;
    }

    private void moveRight() {
        var items = context.getSession().getOfferedItems();
        if (items.size == 0) return;
        selectedIndex = (selectedIndex + 1) % items.size;
    }

    private void confirm() {
        context.getSession().selectPlayerItem(selectedIndex);
        game.openMatch();
    }
}
