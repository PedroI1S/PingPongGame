# Visible Ball Spin — Design

**Date:** 2026-07-04
**Status:** Approved

## Goal

Make the ball's simulated spin visible: the rendered ball rotates at exactly the
physical angular velocity `ω` (`BallState.spin`, world-time rad/s) that the sim
already integrates. No new physics constants, no fake animation — the visual
rotation is the real spin state, which itself derives from real SI table-tennis
constants through `PhysicsConfig` (topspin gain 120 rad/s SI, max spin
180 rad/s SI, scaled by `timeScale`).

## Non-goals

- No change to physics, netcode, or spin constants — spin already flows through
  snapshots (`snapBallSpin`) and client extrapolation.
- No visual-rate clamping: at max spin (~76°/frame at 60 fps) the ball strobes;
  that is what real fast spin looks like sampled at 60 Hz. Accepted.
- No markings on the fallback procedural sphere. It stays featureless white;
  rotation is invisible on it, which is fine — it only appears when the voxel
  OBJ ball fails to load. The voxel ball's multi-color texture makes rotation
  read clearly.

## Design

### Renderer-integrated orientation (chosen approach)

`MatchArenaRenderer` owns a persistent `Quaternion ballOrientation` plus scratch
objects. New method:

```java
/** Integrates spin (world rad/s) into the ball's visual orientation. */
public void spinBall(Vector3 spin, float delta)
```

Each call rotates the accumulated quaternion by angle `|ω|·delta` (degrees for
libGDX API) around axis `ω̂`, skipping when `|ω|` is negligible (< 1e-4).
Left-multiplied so the rotation happens in world axes:
`ballOrientation = Δq · ballOrientation` (normalized periodically to avoid
drift).

`setBallPosition(x, y, z)` composes the orientation into the ball transform for
both render paths:

- **OBJ voxel ball:** translate to pos → rotate by `ballOrientation` → scale →
  re-center offset (rotation inserted between translate and scale so the mesh
  spins about the ball centre).
- **Procedural sphere fallback:** `set(position, ballOrientation)` — harmless
  on the featureless sphere.

Orientation persists across serves (a ball has no canonical "reset"
orientation; carrying it over is physically honest and avoids visible snaps).

### Callers (one line each)

- `NetMatchScreen`: after extrapolation, `arena.spinBall(extrapState.spin, delta)`
  before `setBallPosition`. Spin arrives in snapshots already.
- `TutorialScreen`: `arena.spinBall(graduation.getBallSpin(), delta)` in the
  graduation branch, `arena.spinBall(course.ball().spin, delta)` in the drill
  branch.

### Why not alternatives

- Screen-owned orientation duplicates the integration in every screen.
- Faked spin (UV scroll / fixed rate on hit) contradicts the requirement that
  the visual reflect the real spin state.

## Testing

- The quaternion integration lives in the renderer (core module, needs GL for
  full instantiation), so automated coverage is limited; if the integration is
  factored as a small static helper (`Quaternion integrate(Quaternion q,
  Vector3 spin, float dt)`), it gets a plain JUnit test in core: axis/angle
  correctness, zero-spin no-op, normalization.
- Manual feel check: tutorial spin drill (topspin/backspin visibly rolling
  forward/backward), curve serve (sidespin about the vertical axis), max-spin
  strobe accepted.
