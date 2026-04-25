# Gameplay Roadmap

## Current Translation Of The Idea

The concept now being followed is:

- a ping-pong duel from the player point of view
- a ball that visually comes toward the player
- quick aim-lab-like clicking to return it
- a bot opponent that also has lives and modifiers

The current 2D version fakes depth with ball growth instead of full 3D movement.

## What Was Implemented Now

### Core loop

The current loop is:

- choose one pre-match modifier
- wait for the bot to send an incoming shot
- click the ball before impact
- let the bot answer or fail
- keep trading lives until one side is out

### Ball presentation

The ball currently communicates approach through:

- increasing size
- timed pressure
- a player-facing playfield layout

It now also supports the return path:

- incoming animation: far to near (growing)
- outgoing animation: near to far (shrinking)

The current visual pass intentionally removed the blue helper trail and reduced the white aura strength to keep focus on timing/click readability.

### Table presentation

The table now runs as a sprite-sheet animation workflow:

- normal looping frames while idle
- reverse playback before a bot shot reaches the player
- freeze on frame 0 during player reaction
- resume looping only after the player return animation finishes

The rendering keeps frame aspect ratio, which is important when using 128x128 table frames.

This is intentionally simple, because it proves the interaction before extra complexity is added.

### Bot structure

The bot is not rendered as a full mirrored player yet.

Instead, the bot is resolved through:

- hidden item modifier
- return chance logic
- life loss when it fails to answer

That keeps the duel structure while letting the reaction mechanic stay center stage.

### Item structure

The current passive item system already supports this new loop.

Items now affect things like:

- lives
- click target size
- reaction time window
- return pressure
- match tempo ramp

## Best Next Gameplay Steps

### Step 1. Improve the feeling of depth

If the click loop feels right, the best next jump is better approach feedback:

- moving the ball slightly along a trajectory
- stronger glow and shadow
- impact flashes near the camera
- screen shake on late returns

### Step 2. Add readable serve patterns

To make the bot feel less abstract, give it recognizable shot types:

- fast straight shots
- slow fakeout shots
- wide-angle shots
- repeated pattern bursts

### Step 3. Add active items

Once the passive system feels good, move toward real decisions:

- emergency heal
- one-shot slow motion
- oversized click window for one ball
- a forced hard-to-read return against the bot

### Step 4. Tune difficulty like an aim trainer

The best direction is not just "faster."

Tune through:

- reaction window size
- spawn spread
- shot rhythm
- visual noise
- bot consistency

### Step 5. Add richer duel pacing

To bring back more of the Buckshot Roulette tension, add phases like:

- loadout reveal
- bot tell or bluff
- short rally burst
- point resolution

## Suggested Future Systems

- `ServePattern`
- `ShotSpawner`
- `ActiveItemController`
- `ShotEventBus`
- `DifficultyProfile`
- `PostMatchSummary`

## Design Advice

The strongest next move is to keep the game focused on one sentence:

"A ping-pong ball comes at you, and you must click it in time."

If every future feature makes that sentence feel sharper, it belongs.

