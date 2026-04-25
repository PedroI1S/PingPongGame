package io.github.some_example_name.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.some_example_name.Main;
import io.github.some_example_name.config.GameConfig;

public final class LoadingScreen extends BaseScreen {
    private float elapsed;

    public LoadingScreen(Main game) {
        super(game);
    }

    @Override
    public void render(float delta) {
        elapsed += delta;
        boolean ready = context.getAssets().update() && context.getAssets().isReady();
        if (ready && elapsed >= GameConfig.LOADING_MIN_SHOW_TIME) {
            game.openMenu();
            return;
        }

        beginFrame(0.03f, 0.07f, 0.09f);
        SpriteBatch batch = context.getBatch();
        batch.begin();

        float progress = context.getAssets().getProgress();
        int loaded = context.getAssets().getLoadedAssetsCount();
        int queued = Math.max(1, context.getAssets().getQueuedAssetsCount());

        batch.setColor(Color.valueOf("0D1F26"));
        batch.draw(context.getLoadingPixel(), 120f, 118f, 1040f, 470f);
        batch.setColor(Color.valueOf("16343C"));
        batch.draw(context.getLoadingPixel(), 120f, 118f, 1040f, 8f);
        batch.draw(context.getLoadingPixel(), 120f, 580f, 1040f, 8f);

        drawCentered(
            batch,
            context.getTitleFont(),
            "AssetManager Boot",
            GameConfig.WORLD_WIDTH * 0.5f,
            530f,
            Color.valueOf("E9FFFD")
        );
        drawCentered(
            batch,
            context.getBodyFont(),
            context.getAssets().getLoadingStageLabel(),
            GameConfig.WORLD_WIDTH * 0.5f,
            488f,
            Color.valueOf("9CC9CF")
        );

        batch.setColor(Color.valueOf("123741"));
        batch.draw(context.getLoadingPixel(), 220f, 270f, 840f, 28f);
        batch.setColor(Color.valueOf("123A40"));
        batch.draw(context.getLoadingPixel(), 220f, 270f, 840f, 28f);
        batch.setColor(Color.valueOf("76F7D1"));
        batch.draw(context.getLoadingPixel(), 220f, 270f, 840f * progress, 28f);
        batch.setColor(Color.WHITE);

        drawCentered(
            batch,
            context.getBodyFont(),
            loaded + " / " + queued + " assets ready",
            GameConfig.WORLD_WIDTH * 0.5f,
            238f,
            Color.valueOf("B9F5E8")
        );

        context.getBodyFont().setColor(Color.valueOf("A7D8DD"));
        context.getBodyFont().draw(batch, "Queued Pack", 228f, 382f);
        context.getBodyFont().draw(batch, "Progress", 228f, 350f);
        context.getBodyFont().draw(batch, "Transition", 228f, 318f);
        context.getBodyFont().setColor(Color.valueOf("ECFFFC"));
        context.getBodyFont().draw(batch, "core/ui + procedural gameplay visuals", 410f, 382f);
        context.getBodyFont().draw(batch, Math.round(progress * 100f) + "%", 410f, 350f);
        context.getBodyFont().draw(batch, ready ? "Menu unlock pending" : "Waiting for AssetManager", 410f, 318f);

        drawCentered(
            batch,
            context.getBodyFont(),
            "Boot flow stays visible now, even when the assets are small.",
            GameConfig.WORLD_WIDTH * 0.5f,
            180f,
            Color.valueOf("86B9C0")
        );
        batch.end();
    }
}
