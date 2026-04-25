package io.github.some_example_name.model;

import io.github.some_example_name.config.GameConfig;

/** Mutable setup for one duelist. */
public final class FighterConfig {
    private int lives = GameConfig.DEFAULT_LIVES;
    private float targetScaleMultiplier = 1f;
    private float incomingTimeMultiplier = 1f;
    private float returnPowerMultiplier = 1f;

    public int getLives() {
        return lives;
    }

    public void addLives(int amount) {
        lives += amount;
    }

    public float getTargetScaleMultiplier() {
        return targetScaleMultiplier;
    }

    public void addTargetScaleMultiplier(float amount) {
        targetScaleMultiplier += amount;
    }

    public float getIncomingTimeMultiplier() {
        return incomingTimeMultiplier;
    }

    public void addIncomingTimeMultiplier(float amount) {
        incomingTimeMultiplier += amount;
    }

    public float getReturnPowerMultiplier() {
        return returnPowerMultiplier;
    }

    public void addReturnPowerMultiplier(float amount) {
        returnPowerMultiplier += amount;
    }
}
