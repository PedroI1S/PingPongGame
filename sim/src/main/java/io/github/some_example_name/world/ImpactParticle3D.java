package io.github.some_example_name.world;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Pool;

/**
 * Tiny visual spark that lives for a fraction of a second and then dies.
 * Each instance owns its own delta-based timer ({@code life}) — the world
 * just calls {@link #update(float)} once per frame and frees expired ones.
 */
public final class ImpactParticle3D implements Pool.Poolable {
    private static final float SOFT_GRAVITY = 4.5f;

    private final Vector3 position = new Vector3();
    private final Vector3 velocity = new Vector3();
    private float life;
    private float maxLife;

    public void init(float x, float y, float z, Vector3 vel, float lifeSeconds) {
        position.set(x, y, z);
        velocity.set(vel);
        this.life = lifeSeconds;
        this.maxLife = lifeSeconds;
    }

    /** Advances the particle's internal timer. Returns true once expired. */
    public boolean update(float delta) {
        life -= delta;
        position.mulAdd(velocity, delta);
        velocity.y -= SOFT_GRAVITY * delta;
        return life <= 0f;
    }

    public Vector3 getPosition() { return position; }

    /** 1.0 when freshly spawned, 0.0 at end of life — used for fade-out. */
    public float getAlpha() {
        return MathUtils.clamp(life / Math.max(0.001f, maxLife), 0f, 1f);
    }

    @Override
    public void reset() {
        position.setZero();
        velocity.setZero();
        life = 0f;
        maxLife = 0f;
    }
}
