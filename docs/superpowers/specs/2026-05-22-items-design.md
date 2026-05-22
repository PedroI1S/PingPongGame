# Item System & Best-of-3 Rounds — Design Spec

**Date:** 2026-05-22
**Branch:** experiment/multiplayer-lan

---

## Overview

Adds a Buckshot-Roulette-inspired item system to the between-serves phase, and restructures the match into a best-of-3 rounds format with 5 lives per round. After every point, both players receive random items which appear as 3D objects on the table. Players use items freely, then both signal READY before the next serve begins.

---

## 1. Match & Round Structure

| Concept | Value |
|---|---|
| Lives per round | 5 |
| Rounds per match | Best-of-3 (first to 2 round wins) |
| Item inventory reset | Per round (wiped at round start) |
| Item carry-over | Yes — within a round, unused items persist across serves |

**Flow:**

```
runBestOf3():
  p1Wins = 0, p2Wins = 0
  while (p1Wins < 2 && p2Wins < 2):
    winner = runOneRound()   // lives start at 5
    winner == 1 ? p1Wins++ : p2Wins++
    wipe item inventories
    broadcast ROUND_OVER(winner, p1Wins, p2Wins)
    pause 2s
  broadcast GAME_OVER(overall winner)
```

Between rounds, lives reset to 5 and inventories are cleared. The brief 2-second pause lets clients show a "Round X — P1/P2 wins" overlay.

---

## 2. Item Phase Mechanics

Triggered after every point (a player hits 0 on that rally).

### 2.1 Server steps

1. **Deal** — server draws 2 random `ItemType`s per player and appends to their `PlayerInventory` (max 4 slots; excess discarded).
2. **Broadcast `ITEM_DEALT`** — one packet per player listing newly received items.
3. **Enter `Phase.ITEM_PHASE`** — server stops accepting CLICK packets.
4. **Process `USE_ITEM`** packets from both players:
   - Validates item is in the sender's inventory.
   - Applies effect to `ItemEffects` (or live state for instant effects).
   - Removes item from inventory.
   - Broadcasts `ITEM_USED { playerNumber, itemId }` to both clients.
5. **Wait for both `ITEM_READY`** packets (or 15s timeout — whichever comes first).
6. Transition to `Phase.PREPARE_SERVE`.

### 2.2 Client visuals

- During `ITEM_PHASE`, `ItemPhaseRenderer` draws each player's inventory items as small **3D objects on the table** — each player's items on their own side of the net.
- When a player uses an item (server broadcasts `ITEM_USED`), that item's 3D object **rises and hovers** above the others (~0.3 units up, smooth lerp). Unused items stay flat.
- After the phase ends, all item objects are removed from the scene.
- Both players see both inventories in real time (opponent's items are visible but not selectable).

---

## 3. Item Catalog

| # | Name | Effect | Scope | Duration |
|---|---|---|---|---|
| 1 | **Patch Kit** | +1 life (capped at 5) | Self | Instant |
| 2 | **Wide Paddle** | 1.5× hit radius | Self | Next rally |
| 3 | **Slow-mo** | Incoming ball 30% slower | Self | Next rally |
| 4 | **Steal** | Takes 1 random item from opponent's inventory | Opponent | Instant |
| 5 | **Fast Serve** | Opponent's incoming ball 30% faster | Opponent | Next rally |
| 6 | **Tiny Paddle** | Opponent's hit radius 0.6× | Opponent | Next rally |
| 7 | **Punch** | Opponent screen blurry for first 10s of next rally | Opponent | 10s timer |
| 8 | **Fly Bait** | Spawns 2–3 flies on opponent's table side. Opponent clicks to swat; ball hitting an unswatted fly = opponent loses 1 life | Opponent | Until killed or rally ends |
| 9 | **Coin Flip** | 50/50: removes 1 life from self OR opponent | Self/Opponent | Instant |

### Effect consumption

- **Next-rally effects** (`Wide Paddle`, `Slow-mo`, `Fast Serve`, `Tiny Paddle`) are stored in `ItemEffects` and consumed (cleared) at the end of the rally they apply to, regardless of hit outcome.
- **Instant effects** (`Patch Kit`, `Steal`, `Coin Flip`) resolve immediately on the server when `USE_ITEM` is received.
- **Timed effects** (`Punch`) store a countdown (`punchTimer`) in `DuelistState`; the client receives it via `STATE` and applies blur accordingly.
- **Fly Bait** flies persist until swatted (CLICK ray-sphere hit on the fly) or until the rally ends — whichever comes first.

---

## 4. Architecture

### 4.1 `sim/` changes

**New classes:**

- `ItemType` (enum, 9 values) — `sim/model/ItemType.java`
- `PlayerInventory` — list of up to 4 `ItemType`, with add/remove/steal helpers — `sim/model/PlayerInventory.java`
- `ItemEffects` — one-rally flags for each effect: `wideClick`, `slowIncoming`, `fastIncoming`, `tinyPaddleActive`, `punchTimer`, `flies: List<FlyState>` — `sim/model/ItemEffects.java`
- `FlyState` — position (x, z on table), alive flag — `sim/world/FlyState.java`

**Modified classes:**

- `MatchWorld3D`:
  - New `Phase.ITEM_PHASE` state.
  - Holds `PlayerInventory` for P1 and P2.
  - Holds `ItemEffects` for P1 and P2 (applied to incoming/outgoing calc).
  - `applyItem(int player, ItemType)` — validates and dispatches effect.
  - Ball-fly collision check in `update()` — if ball sphere intersects any fly's sphere, fly dies and opponent loses 1 life.
  - `roundWins[2]` and `currentRound` counters.
- `DuelistState`:
  - Add `punchTimer` float.
  - Remove static multipliers from constructor (now driven by `ItemEffects`).
- `GameServer`:
  - `runBestOf3()` outer loop.
  - `runOneRound()` — existing match loop, returns winner.
  - Handles `USE_ITEM` and `ITEM_READY` packets in the action queue.
- `PacketType` — new constants (see §4.3).

### 4.2 `core/` changes

**New classes:**

- `ItemPhaseRenderer` — manages 3D item objects on the table during `ITEM_PHASE`: spawn, hover animation, cleanup.

**Modified classes:**

- `NetMatchScreen`:
  - Handles `ITEM_DEALT`, `ITEM_USED`, `ITEM_READY`, `ROUND_OVER`, `FLY_SPAWN`, `FLY_KILLED` packets.
  - Click-tests flies during rally (ray-sphere, same path as ball hit-test) — sends `USE_ITEM` equivalent click.
  - Shows READY button during `ITEM_PHASE`.
  - Shows round-win overlay on `ROUND_OVER`.
- `RetroPostProcess`:
  - `setPunchBlur(float t)` — radial blur pass scaled by `t` (0..1), fades over 10s. Applied only when `punchTimer > 0`.
- `MatchArenaRenderer`:
  - Renders fly objects (small sphere, buzzing Y oscillation) when `FlyState` list is non-empty.

### 4.3 New packets

| Constant | Direction | Payload |
|---|---|---|
| `ROUND_OVER = 20` | S→C | `byte winner, byte p1Wins, byte p2Wins` |
| `ITEM_DEALT = 21` | S→C | `byte playerNumber, byte count, byte[] itemIds` |
| `ITEM_USED = 22` | S→C | `byte playerNumber, byte itemId` |
| `ITEM_READY = 23` | C→S | _(none)_ |
| `USE_ITEM = 24` | C→S | `byte itemId` |
| `FLY_SPAWN = 25` | S→C | `byte count, float[] x, float[] z` |
| `FLY_KILLED = 26` | S→C | `byte flyIndex` |
| `STATE` extended | S→C | Append `byte p1ItemCount, byte[] p1Items, byte p2ItemCount, byte[] p2Items, float punchTimer` |

---

## 5. Error handling & edge cases

- **Timeout:** If 15s elapse and a player hasn't sent `ITEM_READY`, server auto-advances. This prevents a disconnected player from freezing the match.
- **Steal with empty inventory:** No-op — item is consumed but nothing transferred.
- **Coin Flip at 1 life (self):** Can kill the user. This is intentional high-risk behaviour.
- **Patch Kit at 5 lives:** Capped — item is consumed, no life gained (feedback flash shown).
- **Fly Bait with no flies left:** If all flies are swatted before the ball arrives, effect is neutralised. Flies are cleared automatically at rally end.
- **Multiple next-rally effects:** Stack independently (e.g., Wide Paddle + Slow-mo both apply next rally).
- **Inventory full (4 items):** Newly dealt items beyond slot 4 are silently discarded — client shows a "full" flash.

---

## 6. Out of scope

- Item animations beyond hover/float (no particle bursts for now).
- Fly sound effects (can be added in the feedback layer pass).
- Item unlock progression — all 9 items always in the pool.
- AI (bot) using items strategically — bot sends `ITEM_READY` immediately without using any items (simplest correct behaviour for now).
