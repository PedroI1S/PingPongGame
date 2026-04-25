# Aim Roulette Pong Foundation

A LibGDX foundation for a 2D first-person-style ping-pong reaction game.

The match concept in this version is:

- you are in the player POV
- the bot sends balls at you
- each incoming ball grows in size as it gets closer
- you must click it before impact to return it
- both sides still use lives and pre-match item modifiers

This is meant to be a clean foundation, not the final game. The focus is on structure, systems, and a playable core loop that matches the idea more closely.

## Current Flow

1. Loading screen initializes assets.
2. Menu screen presents the current prototype loop.
3. Loadout screen lets the player choose one pre-match modifier.
4. Match screen runs the player-POV reaction duel.
5. Result returns to the menu.

## Current Controls

- `ENTER` or mouse click: continue from menu
- `LEFT` / `RIGHT`: choose a loadout card
- `ENTER`: confirm card
- `Mouse move`: aim your cursor during the match
- `Mouse click`: return the incoming ball
- `R`: restart the current duel setup
- `ESC`: return to menu

## Technical Features Included

- class-based architecture with separated packages
- screen flow with `Game` + multiple screens
- centralized `GameContext` and `GameSession`
- visible `AssetManager` loading screen with boot-phase feedback
- `AssetManager` wrapper plus custom procedural asset loading
- table animation pipeline with variation-ready sprite-sheet metadata
- `InputProcessor` usage for menu, loadout, and gameplay
- object pooling through reusable impact particles
- a duel model with items, lives, tempo ramp, and bot response logic
- a table shot workflow: table reverse animation -> frozen contact frame -> incoming ball -> outgoing ball -> table resumes

## How To Run

```bash
./gradlew lwjgl3:run
```

To verify the project builds:

```bash
./gradlew build
```

## Project Structure

- `core/src/main/java/io/github/some_example_name/Main.java`
  Central game bootstrap and navigation.
- `core/src/main/java/io/github/some_example_name/core`
  Shared lifetime objects and cross-screen state.
- `core/src/main/java/io/github/some_example_name/assets`
  Asset pipeline, generated placeholder visuals, and variation-ready table sprite-sheet descriptors.
- `core/src/main/java/io/github/some_example_name/input`
  Screen-specific input processors.
- `core/src/main/java/io/github/some_example_name/model`
  Match configuration, item definitions, and enums.
- `core/src/main/java/io/github/some_example_name/screen`
  Loading, menu, loadout, and match screens.
- `core/src/main/java/io/github/some_example_name/world`
  Incoming-ball simulation, duel runtime state, and pooled VFX.
- `assets/ui`
  UI asset subfolder used by the loading/menu flow.
- `docs/architecture.md`
  Technical breakdown of the foundation.
- `docs/gameplay-roadmap.md`
  Design translation and next-step ideas.

## What Changed Compared To The Earlier Side-View Draft

The gameplay foundation is no longer side-view Pong with paddle movement.

It is now built around the correct interaction:

- incoming threat on screen
- increasing visual size to imply depth
- quick click reaction
- abstract bot answer after your return

That keeps the project simpler while matching the aim-lab-like direction much better.

## Recommended Next Expansions

- add stronger depth cues like shadows, trail lines, and impact shake
- give the bot visible serve patterns or fakeouts
- turn passive pre-match cards into active item uses
- add sound, combo feedback, and score breakdowns
- add difficulty profiles for bot timing and error rates
