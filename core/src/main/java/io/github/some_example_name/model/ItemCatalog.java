package io.github.some_example_name.model;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.RandomXS128;
import io.github.some_example_name.config.GameConfig;

import java.util.ArrayList;
import java.util.List;

/** Small catalog that can later grow into a data-driven system. */
public final class ItemCatalog {
    private final List<ItemDefinition> items = List.of(
        new ItemDefinition(
            "patch_kit",
            "Patch Kit",
            "+1 life before the duel starts.",
            "Safe pick when you want a little more room for mistakes.",
            Color.valueOf("76F7B8"),
            (config, side) -> config.getFighter(side).addLives(1)
        ),
        new ItemDefinition(
            "focus_lens",
            "Focus Lens",
            "Incoming balls are easier to click.",
            "The target grows more generously, which fits the aim-trainer side of the idea.",
            Color.valueOf("FFD36B"),
            (config, side) -> config.getFighter(side).addTargetScaleMultiplier(0.22f)
        ),
        new ItemDefinition(
            "time_buffer",
            "Time Buffer",
            "Balls take longer to reach you.",
            "Useful for keeping the reaction window readable as the tempo climbs.",
            Color.valueOf("78D9FF"),
            (config, side) -> config.getFighter(side).addIncomingTimeMultiplier(0.15f)
        ),
        new ItemDefinition(
            "smash_core",
            "Smash Core",
            "Your successful clicks pressure the bot more.",
            "A cleaner offensive option for a duel where returning is about reaction strength, not paddle angle.",
            Color.valueOf("FF8FB3"),
            (config, side) -> config.getFighter(side).addReturnPowerMultiplier(0.22f)
        ),
        new ItemDefinition(
            "heat_sink",
            "Heat Sink",
            "The pace ramps up more slowly over the whole match.",
            "Good for a foundation build where readability matters more than chaos.",
            Color.valueOf("B0FFEE"),
            (config, side) -> config.addApproachDurationDecay(-0.02f)
        )
    );

    public List<ItemDefinition> drawOptions(RandomXS128 random) {
        List<ItemDefinition> shuffled = new ArrayList<>(items);
        for (int i = shuffled.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            ItemDefinition tmp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, tmp);
        }
        return new ArrayList<>(shuffled.subList(0, Math.min(GameConfig.LOADOUT_OPTIONS, shuffled.size())));
    }

    public ItemDefinition drawRandom(RandomXS128 random) {
        return items.get(random.nextInt(items.size()));
    }
}
