package io.github.some_example_name.world;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import io.github.some_example_name.config.GameConfig;

/** Handles both incoming (bot → player) and outgoing (player → bot) ball animation. */
public final class IncomingBall {
    private final Vector2 startPosition = new Vector2();
    private final Vector2 endPosition   = new Vector2();
    private final Vector2 position      = new Vector2();

    private float elapsed;
    private float duration;

    public void start(float startX, float startY, float endX, float endY, float duration) {
        startPosition.set(startX, startY);
        endPosition.set(endX, endY);
        position.set(startPosition);
        elapsed       = 0f;
        this.duration = duration;
    }

    /** Returns true when the animation is complete. */
    public boolean update(float delta) {
        elapsed = Math.min(duration, elapsed + delta);
        float progress = Interpolation.pow2In.apply(getProgress());
        position.set(startPosition).lerp(endPosition, progress);
        return elapsed >= duration;
    }

    public float getRadius() {
        float progress = Interpolation.pow2Out.apply(getProgress());
        return MathUtils.lerp(GameConfig.SHOT_START_RADIUS, GameConfig.SHOT_END_RADIUS, progress);
    }

    public float getProgress() {
        if (duration <= 0f) return 1f;
        return MathUtils.clamp(elapsed / duration, 0f, 1f);
    }

    public float getDuration()        { return duration; }
    public Vector2 getStartPosition() { return startPosition; }
    public Vector2 getEndPosition()   { return endPosition; }
    public Vector2 getPosition()      { return position; }

    public boolean contains(Vector2 point, float scaleMultiplier) {
        float radius = getRadius() * scaleMultiplier;
        return point.dst2(position) <= radius * radius;
    }
}
