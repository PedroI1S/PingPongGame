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
            "PATCH KIT",
            "+1 LIFE BEFORE THE DUEL",
            "Safe pick when you want extra room for mistakes. Forgiveness costs you nothing -- until it does.",
            Color.valueOf("4ADE80"),
            (config, side) -> config.getFighter(side).addLives(1)
        ),
        new ItemDefinition(
            "focus_lens",
            "FOCUS LENS",
            "WIDER HIT WINDOW",
            "Target grows more generously. The aim-trainer becomes kinder. Kindness is a trap.",
            Color.valueOf("FBBF24"),
            (config, side) -> config.getFighter(side).addTargetScaleMultiplier(0.22f)
        ),
        new ItemDefinition(
            "time_buffer",
            "TIME BUFFER",
            "BALLS ARRIVE SLOWER",
            "Extends the reaction window before tempo climbs. Buys time. Time always runs out.",
            Color.valueOf("60A5FA"),
            (config, side) -> config.getFighter(side).addIncomingTimeMultiplier(0.15f)
        ),
        new ItemDefinition(
            "smash_core",
            "SMASH CORE",
            "MORE PRESSURE ON THE BOT",
            "Each successful click hits harder. Offense first, consequences second.",
            Color.valueOf("F472B6"),
            (config, side) -> config.getFighter(side).addReturnPowerMultiplier(0.22f)
        ),
        new ItemDefinition(
            "heat_sink",
            "HEAT SINK",
            "PACE RAMPS UP SLOWER",
            "Keeps chaos at bay a little longer. Good for when readability matters more than speed.",
            Color.valueOf("2DD4BF"),
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
