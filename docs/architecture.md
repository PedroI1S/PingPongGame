# Architecture Notes

## Goal

The project is organized as a reusable LibGDX foundation for a reaction-based ping-pong duel, not as a one-file prototype.

The important change in this version is conceptual:

- the player no longer controls a side-view paddle
- the player sees incoming balls in a player-facing view
- the core action is clicking the incoming ball before impact

## Main Technical Decisions

### 1. `Main` owns screen navigation

`Main` extends `Game`, which gives the project a clean state flow:

- loading
- menu
- loadout
- match

That is the right base for a project that will likely add more phases later.

### 2. `GameContext` holds shared lifetime objects

`GameContext` owns:

- `SpriteBatch`
- `FitViewport`
- fonts
- `GameAssets`
- `GameSession`

This keeps screens lightweight and avoids recreating important systems repeatedly.

### 3. `GameSession` stores cross-screen match setup

`GameSession` persists:

- offered item cards
- selected player item
- hidden bot item
- last match result
- random generator

This lets the loadout phase prepare a duel cleanly before the match starts.

### 4. The asset layer already behaves like a real project

`GameAssets` wraps LibGDX `AssetManager`.

It loads:

- normal file assets like `assets/ui/libgdx.png`
- `ProceduralAssets` through a custom loader

That means the asset lifecycle is already centralized even though the visuals are still generated placeholders.

The table pipeline is now variation-ready through `TableVariation` metadata:

- each variation declares sheet path, grid, and valid frame count
- the custom loader resolves dependencies for all declared variations
- `ProceduralAssets` exposes table frames by variation and frame index

The loading screen now keeps the boot flow visible for a short minimum time, so `AssetManager` startup is an actual screen in the project instead of an instant transition.

### 5. Input is separated by responsibility

The project uses dedicated processors:

- `MenuInputProcessor`
- `LoadoutInputProcessor`
- `MatchInputController`

The match processor is now pointer-based. It tracks cursor position, click requests, and global shortcuts like restart and return-to-menu.

### 6. `MatchWorld` owns the reaction duel simulation

`MatchWorld` now encapsulates the full player-POV loop:

- preparing the next incoming shot
- spawning the clickable incoming ball
- growing the ball over time to imply depth
- moving the ball along a perspective lane so the 2D depth cue reads better
- resolving player click success or failure
- playing an outgoing shot phase after a successful player click
- resolving the bot response
- applying life loss and win conditions
- managing pooled impact particles

The shot sequence is currently:

1. table loops normally
2. table animation rewinds to frame 0
3. frame 0 stays frozen while the incoming ball approaches
4. player reacts
5. ball plays an outgoing animation toward the bot
6. table loop resumes from frame 0

This keeps `MatchScreen` focused on rendering the HUD and transition flow.

### 7. The runtime model was simplified around the real mechanic

The previous side-view entities like moving paddles are gone.

The runtime model now uses:

- `IncomingBall`
- `DuelistState`
- `MatchWorld`

That is a much better fit for the concept you described.

### 8. Object pooling is still part of the base

`ImpactParticle` remains managed through a LibGDX `Pool`.

That keeps repeated clicks and impacts lightweight and demonstrates the allocation-saving pattern you asked for.

## Package Responsibilities

### `assets`

- `GameAssets`: central asset gateway
- `ProceduralAssets`: generated placeholder art bundle
- `ProceduralAssetsLoader`: custom `AssetManager` loader

### `config`

- `GameConfig`: shared tuning constants for timings, playfield, and UI

### `core`

- `GameContext`: app-wide resources
- `GameSession`: cross-screen setup state

### `input`

- screen-specific input processors

### `model`

- duel configuration
- item definitions
- side/result enums

### `screen`

- `BaseScreen`: screen helpers
- `LoadingScreen`: asset boot
- `MenuScreen`: overview and entry point
- `LoadoutScreen`: pre-match item selection
- `MatchScreen`: reaction duel presentation

### `world`

- `IncomingBall`: the clickable ball that grows over time
- `DuelistState`: runtime state for player and bot
- `ImpactParticle`: pooled feedback effect
- `MatchWorld`: duel simulation and rules

## Match Loop

1. The player selects one modifier card.
2. The bot secretly receives one modifier too.
3. `GameSession` converts those choices into a `MatchConfig`.
4. `MatchWorld` starts an incoming-shot loop.
5. The player clicks the ball before impact to return it.
6. The bot either answers or misses based on duel state and modifiers.
7. Lives are removed when a side fails to return.
8. The approach window tightens as the match keeps going.

## Why The Ball Grows Instead Of Using Full 3D

The current version keeps the game 2D on purpose.

The depth cue comes from:

- scale growth
- perspective travel across the table lane
- timing pressure
- player-facing presentation

Recent readability adjustments:

- the blue trajectory helper line was removed from the shot presentation
- the white aura around the ball was softened
- table frames now render with preserved frame aspect ratio (important for 128x128 sprite frames)

That gives you the right core interaction without pulling the project into camera, physics, and 3D art complexity too early.

## Good Next Refactors

- move item definitions to JSON
- add event types like `SHOT_SPAWNED`, `PLAYER_RETURN`, `PLAYER_MISS`, `BOT_MISS`
- add audio and stronger screen feedback
- add bot difficulty profiles and serve patterns
- add more explicit visual depth cues if the reaction loop feels good
