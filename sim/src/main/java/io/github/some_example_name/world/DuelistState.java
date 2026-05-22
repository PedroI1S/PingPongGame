package io.github.some_example_name.world;

import io.github.some_example_name.model.ArenaSide;
import io.github.some_example_name.model.FighterConfig;

/** Runtime state for one duelist in the reaction loop. */
public final class DuelistState {
    private final ArenaSide side;
    private final String label;
    private final float targetScaleMultiplier;
    private final float incomingTimeMultiplier;
    private final float returnPowerMultiplier;

    private int lives;

    private float punchTimer;

    public DuelistState(ArenaSide side, String label, FighterConfig config) {
        this.side = side;
        this.label = label;
        this.lives = config.getLives();
        this.targetScaleMultiplier = config.getTargetScaleMultiplier();
        this.incomingTimeMultiplier = config.getIncomingTimeMultiplier();
        this.returnPowerMultiplier = config.getReturnPowerMultiplier();
    }

    public ArenaSide getSide() {
        return side;
    }

    public String getLabel() {
        return label;
    }

    public int getLives() {
        return lives;
    }

    public int loseLife() {
        lives = Math.max(0, lives - 1);
        return lives;
    }

    public void addLife(int maxLives) {
        lives = Math.min(lives + 1, maxLives);
    }

    public float getPunchTimer() {
        return punchTimer;
    }

    public void setPunchTimer(float seconds) {
        punchTimer = seconds;
    }

    public void tickPunchTimer(float delta) {
        punchTimer = Math.max(0f, punchTimer - delta);
    }

    public float getTargetScaleMultiplier() {
        return targetScaleMultiplier;
    }

    public float getIncomingTimeMultiplier() {
        return incomingTimeMultiplier;
    }

    public float getReturnPowerMultiplier() {
        return returnPowerMultiplier;
    }
}
