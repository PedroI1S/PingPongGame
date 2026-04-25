package io.github.some_example_name.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pool;

/** Pooled flash particle for collisions and goals. */
public final class ImpactParticle implements Pool.Poolable {
    private final Vector2 position = new Vector2();
    private final Vector2 velocity = new Vector2();
    private final Color color = new Color();

    private float size;
    private float life;
    private float maxLife;

    public void init(float x, float y, float velocityX, float velocityY, float size, float life, Color tint) {
        position.set(x, y);
        velocity.set(velocityX, velocityY);
        this.size = size;
        this.life = life;
        this.maxLife = life;
        color.set(tint);
    }

    public boolean update(float delta) {
        life -= delta;
        position.mulAdd(velocity, delta);
        size *= 0.985f;
        return life <= 0f;
    }

    public Vector2 getPosition() {
        return position;
    }

    public Color getColor() {
        return color;
    }

    public float getSize() {
        return size;
    }

    public float getAlpha() {
        if (maxLife <= 0f) {
            return 0f;
        }
        return life / maxLife;
    }

    @Override
    public void reset() {
        position.setZero();
        velocity.setZero();
        color.set(Color.CLEAR);
        size = 0f;
        life = 0f;
        maxLife = 0f;
    }
}
