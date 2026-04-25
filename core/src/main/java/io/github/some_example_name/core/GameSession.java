package io.github.some_example_name.core;

import com.badlogic.gdx.math.RandomXS128;
import com.badlogic.gdx.utils.Array;
import io.github.some_example_name.model.ArenaSide;
import io.github.some_example_name.model.ItemCatalog;
import io.github.some_example_name.model.ItemDefinition;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchOutcome;

/** Persists menu-to-match state between screens. */
public final class GameSession {
    private final RandomXS128 random = new RandomXS128();
    private final ItemCatalog itemCatalog = new ItemCatalog();
    private final Array<ItemDefinition> offeredItems = new Array<>();

    private ItemDefinition playerItem;
    private ItemDefinition botItem;
    private MatchOutcome lastOutcome = MatchOutcome.NONE;

    public void rollNewLoadout() {
        offeredItems.clear();
        for (ItemDefinition item : itemCatalog.drawOptions(random)) {
            offeredItems.add(item);
        }
        playerItem = null;
        botItem = itemCatalog.drawRandom(random);
    }

    public Array<ItemDefinition> getOfferedItems() {
        return offeredItems;
    }

    public void selectPlayerItem(int index) {
        if (index >= 0 && index < offeredItems.size) {
            playerItem = offeredItems.get(index);
        }
    }

    public ItemDefinition getPlayerItem() {
        return playerItem;
    }

    public ItemDefinition getBotItem() {
        return botItem;
    }

    public MatchOutcome getLastOutcome() {
        return lastOutcome;
    }

    public void setLastOutcome(MatchOutcome lastOutcome) {
        this.lastOutcome = lastOutcome;
    }

    public RandomXS128 getRandom() {
        return random;
    }

    public MatchConfig buildMatchConfig() {
        MatchConfig config = MatchConfig.createDefault();
        if (playerItem != null) {
            playerItem.apply(config, ArenaSide.PLAYER);
        }
        if (botItem != null) {
            botItem.apply(config, ArenaSide.BOT);
        }
        return config;
    }
}
