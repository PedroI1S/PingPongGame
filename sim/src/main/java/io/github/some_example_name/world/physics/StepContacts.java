package io.github.some_example_name.world.physics;

/** What the ball touched during one {@link BallPhysics#step}. Reused, never allocated per frame. */
public final class StepContacts {
    public boolean tableBounce;
    /** Where the (last) table bounce of the step happened, world units. */
    public float bounceX, bounceZ;
    public boolean netHit;

    public void clear() {
        tableBounce = false;
        netHit = false;
    }
}
