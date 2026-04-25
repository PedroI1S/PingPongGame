package io.github.some_example_name.model;

import com.badlogic.gdx.graphics.Color;

/** Describes one pre-match modifier card. */
public final class ItemDefinition {
    private final String id;
    private final String name;
    private final String summary;
    private final String detail;
    private final Color accent;
    private final MatchModifier modifier;

    public ItemDefinition(
        String id,
        String name,
        String summary,
        String detail,
        Color accent,
        MatchModifier modifier
    ) {
        this.id = id;
        this.name = name;
        this.summary = summary;
        this.detail = detail;
        this.accent = new Color(accent);
        this.modifier = modifier;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSummary() {
        return summary;
    }

    public String getDetail() {
        return detail;
    }

    public Color getAccent() {
        return new Color(accent);
    }

    public void apply(MatchConfig config, ArenaSide side) {
        modifier.apply(config, side);
    }
}
