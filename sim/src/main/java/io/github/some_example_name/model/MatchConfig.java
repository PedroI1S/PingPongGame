package io.github.some_example_name.model;

import io.github.some_example_name.config.GameConfig;

/** Full rules snapshot for one match instance. */
public final class MatchConfig {
    private final FighterConfig player = new FighterConfig();
    private final FighterConfig bot = new FighterConfig();

    private float initialApproachDuration = GameConfig.BASE_APPROACH_DURATION;
    private float approachDurationDecay = GameConfig.APPROACH_DURATION_DECAY;
    private float minimumApproachDuration = GameConfig.MIN_APPROACH_DURATION;
    private float botBaseReturnChance = GameConfig.BOT_BASE_RETURN_CHANCE;

    public static MatchConfig createDefault() {
        return new MatchConfig();
    }

    public FighterConfig getFighter(ArenaSide side) {
        return side == ArenaSide.PLAYER ? player : bot;
    }

    public float getInitialApproachDuration() {
        return initialApproachDuration;
    }

    public float getApproachDurationDecay() {
        return approachDurationDecay;
    }

    public float getMinimumApproachDuration() {
        return minimumApproachDuration;
    }

    public float getBotBaseReturnChance() {
        return botBaseReturnChance;
    }
}
