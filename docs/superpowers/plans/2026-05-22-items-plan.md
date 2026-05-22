# Item System & Best-of-3 Rounds — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Buckshot-Roulette-inspired between-serves item system and restructure the match into best-of-3 rounds with 5 lives each.

**Architecture:** Server-authoritative — the server owns item state, deals items, validates `USE_ITEM` packets, and broadcasts effects. Clients render item objects on the table and send `USE_ITEM`/`ITEM_READY` packets. Each round is a fresh `MatchWorld3D` instance; `GameServer.runBestOf3()` loops over them.

**Tech Stack:** Java 17, LibGDX 1.14.0, JUnit Jupiter 5.10.0, Gradle

**Spec:** `docs/superpowers/specs/2026-05-22-items-design.md`

---

## File Map

| File | Action | Responsibility |
|---|---|---|
| `sim/build.gradle` | Modify | Add JUnit 5 test dependency |
| `sim/src/main/java/.../model/ItemType.java` | **Create** | 9-item enum with wire IDs |
| `sim/src/main/java/.../model/PlayerInventory.java` | **Create** | Per-player item list (max 4) |
| `sim/src/main/java/.../model/ItemEffects.java` | **Create** | One-rally effect flags + fly list |
| `sim/src/main/java/.../world/FlyState.java` | **Create** | Fly position + alive flag |
| `sim/src/main/java/.../world/DuelistState.java` | Modify | Add `punchTimer` float |
| `sim/src/main/java/.../network/PacketType.java` | Modify | Add constants 20–26 |
| `sim/src/main/java/.../network/GameConnection.java` | Modify | New send/receive for item packets |
| `sim/src/main/java/.../world/MatchWorld3D.java` | Modify | ITEM_PHASE, applyItem, fly collision |
| `sim/src/main/java/.../server/GameServer.java` | Modify | runBestOf3, ITEM_PHASE broadcast |
| `sim/src/test/java/.../model/PlayerInventoryTest.java` | **Create** | Unit tests for inventory |
| `sim/src/test/java/.../model/ItemEffectsTest.java` | **Create** | Unit tests for effects |
| `sim/src/test/java/.../world/MatchWorld3DItemTest.java` | **Create** | Item phase + applyItem tests |
| `core/src/main/java/.../render/ItemPhaseRenderer.java` | **Create** | 3D item objects on table |
| `core/src/main/java/.../screen/NetMatchScreen.java` | Modify | Item phase UI, new packet handlers |
| `core/src/main/java/.../render/MatchArenaRenderer.java` | Modify | Fly sphere rendering |
| `core/src/main/java/.../render/RetroPostProcess.java` | Modify | `setPunchBlur(float t)` |
| `lwjgl3/src/main/resources/shaders/retro.frag` | Modify | `u_blur` uniform for Punch effect |

---

## Task 1: Add JUnit 5 to sim module

**Files:**
- Modify: `sim/build.gradle`

- [ ] **Step 1: Add test dependency**

Open `sim/build.gradle` and replace its full contents with:

```gradle
[compileJava, compileTestJava]*.options*.encoding = 'UTF-8'
eclipse.project.name = appName + '-sim'

dependencies {
  api "com.badlogicgames.gdx:gdx:$gdxVersion"
  testImplementation 'org.junit.jupiter:junit-jupiter:5.10.0'
  testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

jar {
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

test {
  useJUnitPlatform()
}
```

- [ ] **Step 2: Create test source directory**

```bash
mkdir -p sim/src/test/java/io/github/some_example_name/model
mkdir -p sim/src/test/java/io/github/some_example_name/world
```

- [ ] **Step 3: Verify Gradle picks it up**

```bash
cd /Users/pedro_mariano/Documents/7_semestre/DesenvolvimentoJogosSim/versoes_libgdx/PingPongGame
./gradlew :sim:dependencies --configuration testRuntimeClasspath 2>&1 | grep junit
```

Expected output: lines containing `junit-jupiter`

- [ ] **Step 4: Commit**

```bash
git add sim/build.gradle
git commit -m "chore: add JUnit 5 to sim test dependencies"
```

---

## Task 2: ItemType enum

**Files:**
- Create: `sim/src/main/java/io/github/some_example_name/model/ItemType.java`
- Create: `sim/src/test/java/io/github/some_example_name/model/ItemTypeTest.java`

- [ ] **Step 1: Write the failing test**

```java
// sim/src/test/java/io/github/some_example_name/model/ItemTypeTest.java
package io.github.some_example_name.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemTypeTest {
    @Test void fromIdRoundTrips() {
        for (ItemType t : ItemType.values()) {
            assertSame(t, ItemType.fromId(t.getId()));
        }
    }

    @Test void fromIdUnknownReturnsNull() {
        assertNull(ItemType.fromId((byte) 99));
    }

    @Test void hasNineItems() {
        assertEquals(9, ItemType.values().length);
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :sim:test --tests "io.github.some_example_name.model.ItemTypeTest" 2>&1 | tail -20
```

Expected: compilation error `ItemType does not exist`

- [ ] **Step 3: Create ItemType**

```java
// sim/src/main/java/io/github/some_example_name/model/ItemType.java
package io.github.some_example_name.model;

public enum ItemType {
    PATCH_KIT(1),
    WIDE_PADDLE(2),
    SLOW_MO(3),
    STEAL(4),
    FAST_SERVE(5),
    TINY_PADDLE(6),
    PUNCH(7),
    FLY_BAIT(8),
    COIN_FLIP(9);

    private final byte id;

    ItemType(int id) { this.id = (byte) id; }

    public byte getId() { return id; }

    public static ItemType fromId(byte id) {
        for (ItemType t : values()) {
            if (t.id == id) return t;
        }
        return null;
    }
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew :sim:test --tests "io.github.some_example_name.model.ItemTypeTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/model/ItemType.java \
        sim/src/test/java/io/github/some_example_name/model/ItemTypeTest.java
git commit -m "feat: add ItemType enum with 9 item types"
```

---

## Task 3: PlayerInventory

**Files:**
- Create: `sim/src/main/java/io/github/some_example_name/model/PlayerInventory.java`
- Create: `sim/src/test/java/io/github/some_example_name/model/PlayerInventoryTest.java`

- [ ] **Step 1: Write the failing tests**

```java
// sim/src/test/java/io/github/some_example_name/model/PlayerInventoryTest.java
package io.github.some_example_name.model;

import org.junit.jupiter.api.Test;
import com.badlogic.gdx.math.RandomXS128;
import static org.junit.jupiter.api.Assertions.*;

class PlayerInventoryTest {

    @Test void addUpToFourItems() {
        PlayerInventory inv = new PlayerInventory();
        assertTrue(inv.add(ItemType.PATCH_KIT));
        assertTrue(inv.add(ItemType.SLOW_MO));
        assertTrue(inv.add(ItemType.WIDE_PADDLE));
        assertTrue(inv.add(ItemType.STEAL));
        assertFalse(inv.add(ItemType.PUNCH)); // 5th item rejected
        assertEquals(4, inv.size());
    }

    @Test void removeExistingItem() {
        PlayerInventory inv = new PlayerInventory();
        inv.add(ItemType.PATCH_KIT);
        assertTrue(inv.remove(ItemType.PATCH_KIT));
        assertEquals(0, inv.size());
    }

    @Test void removeAbsentItemReturnsFalse() {
        PlayerInventory inv = new PlayerInventory();
        assertFalse(inv.remove(ItemType.COIN_FLIP));
    }

    @Test void stealReturnsRandomItemAndRemovesIt() {
        PlayerInventory inv = new PlayerInventory();
        inv.add(ItemType.PATCH_KIT);
        inv.add(ItemType.SLOW_MO);
        ItemType stolen = inv.steal(new RandomXS128(42L));
        assertNotNull(stolen);
        assertEquals(1, inv.size());
    }

    @Test void stealFromEmptyReturnsNull() {
        assertNull(new PlayerInventory().steal(new RandomXS128()));
    }

    @Test void clearEmptiesInventory() {
        PlayerInventory inv = new PlayerInventory();
        inv.add(ItemType.WIDE_PADDLE);
        inv.clear();
        assertEquals(0, inv.size());
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :sim:test --tests "io.github.some_example_name.model.PlayerInventoryTest" 2>&1 | tail -10
```

Expected: compilation error `PlayerInventory does not exist`

- [ ] **Step 3: Create PlayerInventory**

```java
// sim/src/main/java/io/github/some_example_name/model/PlayerInventory.java
package io.github.some_example_name.model;

import com.badlogic.gdx.math.RandomXS128;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PlayerInventory {
    private static final int MAX_SLOTS = 4;
    private final List<ItemType> items = new ArrayList<>(MAX_SLOTS);

    /** Returns false if inventory is full (4 items). */
    public boolean add(ItemType item) {
        if (items.size() >= MAX_SLOTS) return false;
        items.add(item);
        return true;
    }

    /** Returns false if item not in inventory. Removes first occurrence. */
    public boolean remove(ItemType item) {
        return items.remove(item);
    }

    /** Removes and returns a random item, or null if empty. */
    public ItemType steal(RandomXS128 rng) {
        if (items.isEmpty()) return null;
        int idx = (int) (rng.nextLong() & Integer.MAX_VALUE) % items.size();
        return items.remove(idx);
    }

    public List<ItemType> getItems() { return Collections.unmodifiableList(items); }

    public int size() { return items.size(); }

    public void clear() { items.clear(); }
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
./gradlew :sim:test --tests "io.github.some_example_name.model.PlayerInventoryTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/model/PlayerInventory.java \
        sim/src/test/java/io/github/some_example_name/model/PlayerInventoryTest.java
git commit -m "feat: add PlayerInventory with 4-slot limit and steal"
```

---

## Task 4: ItemEffects and FlyState

**Files:**
- Create: `sim/src/main/java/io/github/some_example_name/world/FlyState.java`
- Create: `sim/src/main/java/io/github/some_example_name/model/ItemEffects.java`
- Create: `sim/src/test/java/io/github/some_example_name/model/ItemEffectsTest.java`

- [ ] **Step 1: Write the failing test**

```java
// sim/src/test/java/io/github/some_example_name/model/ItemEffectsTest.java
package io.github.some_example_name.model;

import io.github.some_example_name.world.FlyState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemEffectsTest {

    @Test void clearResetsAllFlags() {
        ItemEffects e = new ItemEffects();
        e.wideClick = true;
        e.slowIncoming = true;
        e.fastIncoming = true;
        e.tinyPaddle = true;
        e.flies.add(new FlyState(1f, 2f));
        e.clear();
        assertFalse(e.wideClick);
        assertFalse(e.slowIncoming);
        assertFalse(e.fastIncoming);
        assertFalse(e.tinyPaddle);
        assertTrue(e.flies.isEmpty());
    }

    @Test void hitScaleReturnsWideWhenSet() {
        ItemEffects e = new ItemEffects();
        e.wideClick = true;
        assertEquals(1.5f, e.hitScaleMultiplier(), 0.001f);
    }

    @Test void hitScaleReturnsTinyWhenSet() {
        ItemEffects e = new ItemEffects();
        e.tinyPaddle = true;
        assertEquals(0.6f, e.hitScaleMultiplier(), 0.001f);
    }

    @Test void hitScaleDefaultIsOne() {
        assertEquals(1f, new ItemEffects().hitScaleMultiplier(), 0.001f);
    }

    @Test void speedMultiplierSlowMo() {
        ItemEffects e = new ItemEffects();
        e.slowIncoming = true;
        assertEquals(0.7f, e.incomingSpeedMultiplier(), 0.001f);
    }

    @Test void speedMultiplierFast() {
        ItemEffects e = new ItemEffects();
        e.fastIncoming = true;
        assertEquals(1.3f, e.incomingSpeedMultiplier(), 0.001f);
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :sim:test --tests "io.github.some_example_name.model.ItemEffectsTest" 2>&1 | tail -10
```

Expected: compilation error

- [ ] **Step 3: Create FlyState**

```java
// sim/src/main/java/io/github/some_example_name/world/FlyState.java
package io.github.some_example_name.world;

public final class FlyState {
    public static final float FLY_RADIUS = 0.3f;

    public final float x;
    public final float z;
    public boolean alive = true;

    public FlyState(float x, float z) {
        this.x = x;
        this.z = z;
    }
}
```

- [ ] **Step 4: Create ItemEffects**

```java
// sim/src/main/java/io/github/some_example_name/model/ItemEffects.java
package io.github.some_example_name.model;

import io.github.some_example_name.world.FlyState;
import java.util.ArrayList;
import java.util.List;

public final class ItemEffects {
    public boolean wideClick;
    public boolean slowIncoming;
    public boolean fastIncoming;
    public boolean tinyPaddle;
    public final List<FlyState> flies = new ArrayList<>(3);

    /** Effective hit-radius multiplier to apply on top of DuelistState's base. */
    public float hitScaleMultiplier() {
        if (wideClick)  return 1.5f;
        if (tinyPaddle) return 0.6f;
        return 1f;
    }

    /** Effective incoming-ball speed multiplier. */
    public float incomingSpeedMultiplier() {
        if (slowIncoming) return 0.7f;
        if (fastIncoming) return 1.3f;
        return 1f;
    }

    public void clear() {
        wideClick = false;
        slowIncoming = false;
        fastIncoming = false;
        tinyPaddle = false;
        flies.clear();
    }
}
```

- [ ] **Step 5: Run — expect PASS**

```bash
./gradlew :sim:test --tests "io.github.some_example_name.model.ItemEffectsTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/FlyState.java \
        sim/src/main/java/io/github/some_example_name/model/ItemEffects.java \
        sim/src/test/java/io/github/some_example_name/model/ItemEffectsTest.java
git commit -m "feat: add ItemEffects flags and FlyState"
```

---

## Task 5: DuelistState — add punchTimer

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/world/DuelistState.java`

- [ ] **Step 1: Add punchTimer field and methods**

In `DuelistState.java`, after `private int lives;` add:

```java
private float punchTimer;
```

After `public int loseLife()` add:

```java
public float getPunchTimer() { return punchTimer; }
public void setPunchTimer(float seconds) { punchTimer = seconds; }
public void tickPunchTimer(float delta) { punchTimer = Math.max(0f, punchTimer - delta); }
```

- [ ] **Step 2: Verify it compiles**

```bash
./gradlew :sim:compileJava 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/DuelistState.java
git commit -m "feat: add punchTimer to DuelistState"
```

---

## Task 6: New PacketType constants

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/network/PacketType.java`

- [ ] **Step 1: Add new constants**

In `PacketType.java`, after the `MATCH_READY = 6` constant block, add:

```java
/**
 * A round has ended.
 * Payload: {@code byte winner (1 or 2), byte p1Wins, byte p2Wins}.
 */
public static final byte ROUND_OVER = 20;

/**
 * Items dealt to a player at the start of the item phase.
 * Payload: {@code byte playerNumber, byte count, byte[] itemIds}.
 */
public static final byte ITEM_DEALT = 21;

/**
 * A player used an item (broadcast to both clients).
 * Payload: {@code byte playerNumber, byte itemId}.
 */
public static final byte ITEM_USED  = 22;

/**
 * Client signals it is done using items and ready to serve.
 * No payload.
 */
public static final byte ITEM_READY = 23;

/**
 * Client wants to use an item from its inventory.
 * Payload: {@code byte itemId}.
 */
public static final byte USE_ITEM   = 24;

/**
 * Server spawns flies on a player's side (opponent used FLY_BAIT).
 * Payload: {@code byte count, float x1, float z1, ...}.
 */
public static final byte FLY_SPAWN  = 25;

/**
 * A fly was killed (swatted or rally ended).
 * Payload: {@code byte flyIndex}.
 */
public static final byte FLY_KILLED = 26;
```

- [ ] **Step 2: Compile check**

```bash
./gradlew :sim:compileJava 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/network/PacketType.java
git commit -m "feat: add ROUND_OVER, ITEM_DEALT/USED/READY, USE_ITEM, FLY_SPAWN/KILLED packets"
```

---

## Task 7: GameConnection — new send/receive methods

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/network/GameConnection.java`

- [ ] **Step 1: Add listener interface methods**

In `GameConnection.Listener`, add new default methods after `onMatchReady`:

```java
// Item phase — Server → Client
default void onRoundOver(int winner, int p1Wins, int p2Wins) {}
default void onItemDealt(int playerNumber, byte[] itemIds)   {}
default void onItemUsed(int playerNumber, int itemId)        {}
default void onFlySpawn(float[] xs, float[] zs)              {}
default void onFlyKilled(int flyIndex)                       {}

// Item phase — Client → Server
default void onItemReady()                                   {}
default void onUseItem(int itemId)                           {}
```

- [ ] **Step 2: Add readLoop cases**

In `readLoop()`, inside the `switch (type)` block, after the `case PacketType.MATCH_READY` case, add:

```java
case PacketType.ROUND_OVER -> {
    int winner = in.readByte() & 0xFF;
    int p1w    = in.readByte() & 0xFF;
    int p2w    = in.readByte() & 0xFF;
    dispatch.execute(() -> listener.onRoundOver(winner, p1w, p2w));
}
case PacketType.ITEM_DEALT -> {
    int pn    = in.readByte() & 0xFF;
    int count = in.readByte() & 0xFF;
    byte[] ids = new byte[count];
    in.readFully(ids);
    dispatch.execute(() -> listener.onItemDealt(pn, ids));
}
case PacketType.ITEM_USED -> {
    int pn = in.readByte() & 0xFF;
    int id = in.readByte() & 0xFF;
    dispatch.execute(() -> listener.onItemUsed(pn, id));
}
case PacketType.FLY_SPAWN -> {
    int count  = in.readByte() & 0xFF;
    float[] xs = new float[count];
    float[] zs = new float[count];
    for (int i = 0; i < count; i++) { xs[i] = in.readFloat(); zs[i] = in.readFloat(); }
    dispatch.execute(() -> listener.onFlySpawn(xs, zs));
}
case PacketType.FLY_KILLED -> {
    int idx = in.readByte() & 0xFF;
    dispatch.execute(() -> listener.onFlyKilled(idx));
}
case PacketType.ITEM_READY -> dispatch.execute(() -> listener.onItemReady());
case PacketType.USE_ITEM -> {
    int id = in.readByte() & 0xFF;
    dispatch.execute(() -> listener.onUseItem(id));
}
```

- [ ] **Step 3: Add send methods — Server → Client section**

After `sendMatchReady`, add:

```java
public void sendRoundOver(int winner, int p1Wins, int p2Wins) {
    write(() -> {
        out.writeByte(PacketType.ROUND_OVER);
        out.writeByte(winner);
        out.writeByte(p1Wins);
        out.writeByte(p2Wins);
    });
}

public void sendItemDealt(int playerNumber, byte[] itemIds) {
    write(() -> {
        out.writeByte(PacketType.ITEM_DEALT);
        out.writeByte(playerNumber);
        out.writeByte(itemIds.length);
        out.write(itemIds);
    });
}

public void sendItemUsed(int playerNumber, int itemId) {
    write(() -> {
        out.writeByte(PacketType.ITEM_USED);
        out.writeByte(playerNumber);
        out.writeByte(itemId);
    });
}

public void sendFlySpawn(float[] xs, float[] zs) {
    write(() -> {
        out.writeByte(PacketType.FLY_SPAWN);
        out.writeByte(xs.length);
        for (int i = 0; i < xs.length; i++) { out.writeFloat(xs[i]); out.writeFloat(zs[i]); }
    });
}

public void sendFlyKilled(int flyIndex) {
    write(() -> { out.writeByte(PacketType.FLY_KILLED); out.writeByte(flyIndex); });
}
```

- [ ] **Step 4: Add send methods — Client → Server section**

After `sendBye`, add:

```java
public void sendItemReady() {
    write(() -> out.writeByte(PacketType.ITEM_READY));
}

public void sendUseItem(byte itemId) {
    write(() -> { out.writeByte(PacketType.USE_ITEM); out.writeByte(itemId); });
}
```

- [ ] **Step 5: Compile check**

```bash
./gradlew :sim:compileJava 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/network/GameConnection.java
git commit -m "feat: add item-phase send/receive methods to GameConnection"
```

---

## Task 8: MatchWorld3D — ITEM_PHASE state and round structure

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java`
- Create: `sim/src/test/java/io/github/some_example_name/world/MatchWorld3DItemTest.java`

- [ ] **Step 1: Write failing tests**

```java
// sim/src/test/java/io/github/some_example_name/world/MatchWorld3DItemTest.java
package io.github.some_example_name.world;

import com.badlogic.gdx.math.RandomXS128;
import io.github.some_example_name.model.ItemEffects;
import io.github.some_example_name.model.ItemType;
import io.github.some_example_name.model.MatchConfig;
import io.github.some_example_name.model.MatchMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MatchWorld3DItemTest {

    private MatchWorld3D world;

    @BeforeEach void setup() {
        world = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128(1L));
        world.setMatchMode(MatchMode.BOT);
    }

    @Test void enterItemPhaseDealsItems() {
        world.enterItemPhase();
        assertEquals(MatchWorld3D.Phase.ITEM_PHASE, world.getPhase());
        assertEquals(2, world.getP1Inventory().size());
        assertEquals(2, world.getP2Inventory().size());
    }

    @Test void playerReadyBothAdvancesToPrepareServe() {
        world.enterItemPhase();
        world.playerReady(1);
        assertEquals(MatchWorld3D.Phase.ITEM_PHASE, world.getPhase()); // still waiting
        world.playerReady(2);
        assertEquals(MatchWorld3D.Phase.PREPARE_SERVE, world.getPhase());
    }

    @Test void itemPhaseTimeoutAdvancesToPrepareServe() {
        world.enterItemPhase();
        world.update(16f); // advance past 15s timeout
        assertEquals(MatchWorld3D.Phase.PREPARE_SERVE, world.getPhase());
    }

    @Test void lastDealtItemsAvailableAfterEnterItemPhase() {
        world.enterItemPhase();
        assertNotNull(world.getLastDealtItems(1));
        assertNotNull(world.getLastDealtItems(2));
        assertEquals(2, world.getLastDealtItems(1).length);
        assertEquals(2, world.getLastDealtItems(2).length);
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :sim:test --tests "io.github.some_example_name.world.MatchWorld3DItemTest" 2>&1 | tail -20
```

Expected: compilation errors for missing methods

- [ ] **Step 3: Add imports and fields to MatchWorld3D**

At the top of `MatchWorld3D.java`, add imports:

```java
import io.github.some_example_name.model.ItemEffects;
import io.github.some_example_name.model.ItemType;
import io.github.some_example_name.model.PlayerInventory;
import java.util.List;
```

Change the `Phase` enum to add `ITEM_PHASE`:

```java
public enum Phase { PREPARE_SERVE, INCOMING, OUTGOING, BOT_RESOLVE, ITEM_PHASE }
```

After `private int nextServer = 1;` add:

```java
private static final float ITEM_PHASE_TIMEOUT = 15f;
private static final int ITEMS_PER_DEAL = 2;

private final PlayerInventory p1Inventory = new PlayerInventory();
private final PlayerInventory p2Inventory = new PlayerInventory();
private final ItemEffects p1Effects = new ItemEffects();
private final ItemEffects p2Effects = new ItemEffects();

private boolean p1Ready;
private boolean p2Ready;

private byte[] lastDealtP1 = new byte[0];
private byte[] lastDealtP2 = new byte[0];

private boolean itemUsedEvent;
private int itemUsedPlayer;
private byte itemUsedId;

private boolean flySpawnEvent;
private float[] flySpawnXs = new float[0];
private float[] flySpawnZs = new float[0];

private int flyKilledIndex = -1;
```

- [ ] **Step 4: Add ITEM_PHASE to the update switch**

In `update()`, add to the switch statement:

```java
case ITEM_PHASE -> updateItemPhase(delta);
```

Add the method after `updateBotResolve`:

```java
private void updateItemPhase(float delta) {
    player.tickPunchTimer(delta);
    bot.tickPunchTimer(delta);
    phaseTimer -= delta;
    if ((p1Ready && p2Ready) || phaseTimer <= 0f) {
        p1Ready = false;
        p2Ready = false;
        prepareServe(GameConfig.BETWEEN_POINTS_DELAY, buildServeStatusText());
    }
}

private String buildServeStatusText() {
    if (matchMode == MatchMode.PVP) {
        return nextServer == 1 ? "P1 to serve." : "P2 to serve.";
    }
    return nextServer == 1 ? "Click anywhere to serve." : "Bot is preparing the opening shot.";
}
```

- [ ] **Step 5: Add enterItemPhase, playerReady, getters**

Add after `prepareServe`:

```java
public void enterItemPhase() {
    ItemType[] pool = ItemType.values();
    lastDealtP1 = dealItems(p1Inventory, pool, ITEMS_PER_DEAL);
    lastDealtP2 = dealItems(p2Inventory, pool, ITEMS_PER_DEAL);
    p1Ready = false;
    p2Ready = false;
    phase = Phase.ITEM_PHASE;
    phaseTimer = ITEM_PHASE_TIMEOUT;
    statusText = "Use your items, then press READY.";
}

private byte[] dealItems(PlayerInventory inv, ItemType[] pool, int count) {
    java.util.List<Byte> dealt = new java.util.ArrayList<>();
    for (int i = 0; i < count; i++) {
        ItemType item = pool[(int)(random.nextLong() & Integer.MAX_VALUE) % pool.length];
        if (inv.add(item)) dealt.add(item.getId());
    }
    byte[] arr = new byte[dealt.size()];
    for (int i = 0; i < arr.length; i++) arr[i] = dealt.get(i);
    return arr;
}

public void playerReady(int playerNumber) {
    if (phase != Phase.ITEM_PHASE) return;
    if (playerNumber == 1) p1Ready = true;
    else p2Ready = true;
    if (p1Ready && p2Ready) {
        p1Ready = false;
        p2Ready = false;
        prepareServe(GameConfig.BETWEEN_POINTS_DELAY, buildServeStatusText());
    }
}

public byte[] getLastDealtItems(int playerNumber) {
    return playerNumber == 1 ? lastDealtP1 : lastDealtP2;
}

public PlayerInventory getP1Inventory() { return p1Inventory; }
public PlayerInventory getP2Inventory() { return p2Inventory; }
public ItemEffects getP1Effects() { return p1Effects; }
public ItemEffects getP2Effects() { return p2Effects; }
```

- [ ] **Step 6: Run — expect PASS**

```bash
./gradlew :sim:test --tests "io.github.some_example_name.world.MatchWorld3DItemTest" 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java \
        sim/src/test/java/io/github/some_example_name/world/MatchWorld3DItemTest.java
git commit -m "feat: add ITEM_PHASE state, enterItemPhase, playerReady to MatchWorld3D"
```

---

## Task 9: MatchWorld3D — applyItem() implementation

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java`
- Modify: `sim/src/test/java/io/github/some_example_name/world/MatchWorld3DItemTest.java`

- [ ] **Step 1: Add tests for applyItem**

Append to `MatchWorld3DItemTest`:

```java
@Test void applyPatchKitAddsLife() {
    world.enterItemPhase();
    // give P1 a PATCH_KIT manually for testing
    world.getP1Inventory().clear();
    world.getP1Inventory().add(ItemType.PATCH_KIT);
    // simulate losing a life first (can't go above DEFAULT_LIVES)
    // applyItem should work even at full lives (capped)
    assertTrue(world.applyItem(1, ItemType.PATCH_KIT));
    assertFalse(world.getP1Inventory().getItems().contains(ItemType.PATCH_KIT));
}

@Test void applyWideClickSetsEffect() {
    world.enterItemPhase();
    world.getP1Inventory().clear();
    world.getP1Inventory().add(ItemType.WIDE_PADDLE);
    world.applyItem(1, ItemType.WIDE_PADDLE);
    assertEquals(1.5f, world.getP1Effects().hitScaleMultiplier(), 0.001f);
}

@Test void applyItemNotInInventoryReturnsFalse() {
    world.enterItemPhase();
    world.getP1Inventory().clear();
    assertFalse(world.applyItem(1, ItemType.COIN_FLIP));
}

@Test void applyTinyPaddleAffectsOpponentEffects() {
    world.enterItemPhase();
    world.getP1Inventory().clear();
    world.getP1Inventory().add(ItemType.TINY_PADDLE);
    world.applyItem(1, ItemType.TINY_PADDLE);
    assertEquals(0.6f, world.getP2Effects().hitScaleMultiplier(), 0.001f);
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
./gradlew :sim:test --tests "io.github.some_example_name.world.MatchWorld3DItemTest" 2>&1 | tail -20
```

Expected: compilation error `applyItem does not exist`

- [ ] **Step 3: Add applyItem to MatchWorld3D**

Add after `playerReady`:

```java
/**
 * Called by the server when a player sends USE_ITEM.
 * Returns false if item not in inventory (invalid use).
 */
public boolean applyItem(int playerNumber, ItemType item) {
    if (phase != Phase.ITEM_PHASE) return false;
    PlayerInventory inv = playerNumber == 1 ? p1Inventory : p2Inventory;
    DuelistState self  = playerNumber == 1 ? player : bot;
    DuelistState opp   = playerNumber == 1 ? bot    : player;
    ItemEffects selfFx = playerNumber == 1 ? p1Effects : p2Effects;
    ItemEffects oppFx  = playerNumber == 1 ? p2Effects : p1Effects;

    if (!inv.remove(item)) return false;

    switch (item) {
        case PATCH_KIT -> {
            int capped = Math.min(self.getLives() + 1, GameConfig.DEFAULT_LIVES);
            // set via loseLife negation — add a addLife helper or inline the cap
            while (self.getLives() < capped) { /* loseLife is subtract-only; use new method */ }
            self.addLife();
        }
        case WIDE_PADDLE  -> selfFx.wideClick   = true;
        case SLOW_MO      -> selfFx.slowIncoming = true;
        case STEAL        -> {
            ItemType stolen = opp.getInventory(playerNumber == 1 ? p2Inventory : p1Inventory)
                .steal(random);
            if (stolen != null) inv.add(stolen);
        }
        case FAST_SERVE   -> oppFx.fastIncoming  = true;
        case TINY_PADDLE  -> oppFx.tinyPaddle    = true;
        case PUNCH        -> opp.setPunchTimer(10f);
        case FLY_BAIT     -> spawnFlies(oppFx);
        case COIN_FLIP    -> {
            if (random.nextFloat() < 0.5f) self.loseLife();
            else opp.loseLife();
            checkMatchOver();
        }
    }

    itemUsedEvent  = true;
    itemUsedPlayer = playerNumber;
    itemUsedId     = item.getId();
    return true;
}
```

That draft has helper calls that don't exist yet. Replace with the clean version:

```java
public boolean applyItem(int playerNumber, ItemType item) {
    if (phase != Phase.ITEM_PHASE) return false;
    PlayerInventory inv    = playerNumber == 1 ? p1Inventory : p2Inventory;
    PlayerInventory oppInv = playerNumber == 1 ? p2Inventory : p1Inventory;
    DuelistState self      = playerNumber == 1 ? player : bot;
    DuelistState opp       = playerNumber == 1 ? bot    : player;
    ItemEffects selfFx     = playerNumber == 1 ? p1Effects : p2Effects;
    ItemEffects oppFx      = playerNumber == 1 ? p2Effects : p1Effects;

    if (!inv.remove(item)) return false;

    switch (item) {
        case PATCH_KIT   -> self.addLife(GameConfig.DEFAULT_LIVES);
        case WIDE_PADDLE -> selfFx.wideClick    = true;
        case SLOW_MO     -> selfFx.slowIncoming = true;
        case STEAL       -> { ItemType stolen = oppInv.steal(random);  // oppInv defined above
                              if (stolen != null) inv.add(stolen); }
        case FAST_SERVE  -> oppFx.fastIncoming  = true;
        case TINY_PADDLE -> oppFx.tinyPaddle    = true;
        case PUNCH       -> opp.setPunchTimer(10f);
        case FLY_BAIT    -> spawnFlies(oppFx, playerNumber == 1 ? -1 : 1);
        case COIN_FLIP   -> { if (random.nextFloat() < 0.5f) self.loseLife();
                              else opp.loseLife(); checkMatchOver(); }
    }

    itemUsedEvent  = true;
    itemUsedPlayer = playerNumber;
    itemUsedId     = item.getId();
    return true;
}

private void spawnFlies(ItemEffects targetFx, int sideSign) {
    int count = 2 + (int)(random.nextFloat() * 2); // 2 or 3
    float[] xs = new float[count];
    float[] zs = new float[count];
    for (int i = 0; i < count; i++) {
        xs[i] = (random.nextFloat() - 0.5f) * TABLE_HALF_WIDTH * 1.6f;
        zs[i] = sideSign * (2f + random.nextFloat() * 4f); // sideSign=-1 → P2 side, 1 → P1 side
        targetFx.flies.add(new FlyState(xs[i], zs[i]));
    }
    flySpawnEvent = true;
    flySpawnXs = xs;
    flySpawnZs = zs;
}

private void checkMatchOver() {
    if (player.getLives() <= 0) { outcome = MatchOutcome.BOT_WIN; ballVisible = false; }
    else if (bot.getLives() <= 0) { outcome = MatchOutcome.PLAYER_WIN; ballVisible = false; }
}

public boolean consumeItemUsedEvent() {
    boolean v = itemUsedEvent; itemUsedEvent = false; return v;
}
public int getItemUsedPlayer() { return itemUsedPlayer; }
public byte getItemUsedId()    { return itemUsedId; }

public boolean consumeFlySpawnEvent() {
    boolean v = flySpawnEvent; flySpawnEvent = false; return v;
}
public float[] getFlySpawnXs() { return flySpawnXs; }
public float[] getFlySpawnZs() { return flySpawnZs; }

public int consumeFlyKilledIndex() {
    int v = flyKilledIndex; flyKilledIndex = -1; return v;
}
```

- [ ] **Step 4: Add `addLife` to DuelistState**

In `DuelistState.java`, after `loseLife()` add:

```java
/** Adds one life, capped at {@code maxLives}. */
public void addLife(int maxLives) {
    lives = Math.min(lives + 1, maxLives);
}
```

- [ ] **Step 5: Wire item effects into hit-radius and ball-speed calculations**

In `tryHitBall()`, replace the call to `player.getTargetScaleMultiplier()` with:

```java
player.getTargetScaleMultiplier() * p1Effects.hitScaleMultiplier()
```

There are two usages (one in `HitVelocity.computeFromRay` and one in the `hitRadius` line). Replace both:

```java
// line: HitVelocity.computeFromRay(...)
if (!HitVelocity.computeFromRay(pickRay, ballPos,
        player.getTargetScaleMultiplier() * p1Effects.hitScaleMultiplier(),
        player.getReturnPowerMultiplier(), true, ballVel, hitPoint, tmpVel)) {

// line: float hitRadius = ...
float hitRadius = BALL_RADIUS * player.getTargetScaleMultiplier()
        * p1Effects.hitScaleMultiplier() * HitVelocity.CLICK_HIT_PADDING;
```

In `handleOpponentClick()`, replace `bot.getTargetScaleMultiplier()` and `bot.getReturnPowerMultiplier()`:

```java
if (!HitVelocity.computeFromRay(pickRay, ballPos,
        bot.getTargetScaleMultiplier() * p2Effects.hitScaleMultiplier(),
        bot.getReturnPowerMultiplier(), false, ballVel, hitPoint, tmpVel)) {
```

In `applyBotImpulse()`, apply speed multiplier from `p1Effects` (bot serves → ball incoming to P1):

```java
private void applyBotImpulse() {
    float speedScale = (1f / Math.max(0.5f, player.getIncomingTimeMultiplier()))
            * p1Effects.incomingSpeedMultiplier();
    float aim = (random.nextFloat() - 0.5f) * 1.4f;
    ballVel.set(aim, 5.0f, 7.5f * speedScale);
    paddleHitEvent = true;
}
```

In `tryPlayerServe()`, apply P2's fast-incoming effect (P1 serves → incoming to P2):

```java
ballVel.set(0f, 5.0f, -10f * p2Effects.incomingSpeedMultiplier());
```

In `tryClientServe()`, apply P1's fast-incoming effect (P2 serves → incoming to P1):

```java
ballVel.set(0f, 5.0f, 10f * p1Effects.incomingSpeedMultiplier());
```

- [ ] **Step 6: Run tests — expect PASS**

```bash
./gradlew :sim:test 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java \
        sim/src/main/java/io/github/some_example_name/world/DuelistState.java \
        sim/src/test/java/io/github/some_example_name/world/MatchWorld3DItemTest.java
git commit -m "feat: implement applyItem with all 9 item effects wired into physics"
```

---

## Task 10: MatchWorld3D — fly collision + scoring changes

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java`

- [ ] **Step 1: Add fly swat hit-test method**

Add after `tryHitBall`:

```java
/**
 * Checks if the pick ray hits any alive fly.
 * Returns the index of the first hit fly, or -1 if none.
 * Kills the fly and fires a FLY_KILLED event.
 */
public int trySwatFly(Ray pickRay, int playerNumber) {
    java.util.List<FlyState> flies = playerNumber == 1 ? p1Effects.flies : p2Effects.flies;
    for (int i = 0; i < flies.size(); i++) {
        FlyState fly = flies.get(i);
        if (!fly.alive) continue;
        float flyY = MatchWorld3D.TABLE_TOP_Y + 0.4f;
        float dx = pickRay.direction.x, dy = pickRay.direction.y, dz = pickRay.direction.z;
        float ox = pickRay.origin.x - fly.x, oy = pickRay.origin.y - flyY, oz = pickRay.origin.z - fly.z;
        float b = 2f * (dx*ox + dy*oy + dz*oz);
        float c = ox*ox + oy*oy + oz*oz - FlyState.FLY_RADIUS * FlyState.FLY_RADIUS;
        float disc = b*b - 4f*c;
        if (disc >= 0f) {
            fly.alive = false;
            flyKilledIndex = i;
            return i;
        }
    }
    return -1;
}
```

- [ ] **Step 2: Add fly-ball collision check to updateIncoming**

In `updateIncoming`, after the bounce-on-player-side block and before `handlePlayerMiss()`, add a fly collision check. Insert at the end of `updateIncoming`, just before the final bounds check:

```java
// Fly collision — ball hits an unswatted fly on P1's side
if (crossedNet && ballPos.z > 0f) {
    for (int i = 0; i < p1Effects.flies.size(); i++) {
        FlyState fly = p1Effects.flies.get(i);
        if (!fly.alive) continue;
        float flyY = TABLE_TOP_Y + 0.4f;
        float dx = ballPos.x - fly.x, dy = ballPos.y - flyY, dz = ballPos.z - fly.z;
        if (dx*dx + dy*dy + dz*dz < FlyState.FLY_RADIUS * FlyState.FLY_RADIUS) {
            fly.alive = false;
            flyKilledIndex = i;
            handlePlayerFlyHit();
            return;
        }
    }
}
```

Add also in `updateOutgoing`, for P2's side:

```java
// Fly collision — ball hits an unswatted fly on P2's side
if (crossedNet && ballPos.z < 0f) {
    for (int i = 0; i < p2Effects.flies.size(); i++) {
        FlyState fly = p2Effects.flies.get(i);
        if (!fly.alive) continue;
        float flyY = TABLE_TOP_Y + 0.4f;
        float dx = ballPos.x - fly.x, dy = ballPos.y - flyY, dz = ballPos.z - fly.z;
        if (dx*dx + dy*dy + dz*dz < FlyState.FLY_RADIUS * FlyState.FLY_RADIUS) {
            fly.alive = false;
            flyKilledIndex = i;
            handleBotFlyHit();
            return;
        }
    }
}
```

Add the handlers:

```java
private void handlePlayerFlyHit() {
    player.loseLife();
    if (player.getLives() <= 0) {
        outcome = MatchOutcome.BOT_WIN; ballVisible = false; return;
    }
    nextServer = 2;
    enterItemPhase();
}

private void handleBotFlyHit() {
    bot.loseLife();
    if (bot.getLives() <= 0) {
        outcome = MatchOutcome.PLAYER_WIN; ballVisible = false; return;
    }
    nextServer = 1;
    enterItemPhase();
}
```

- [ ] **Step 3: Replace prepareServe with enterItemPhase in all scoring paths**

Find every call to `prepareServe(GameConfig.BETWEEN_POINTS_DELAY, ...)` that occurs after a life is lost (not the opening delay). Replace each one with `enterItemPhase()`. The relevant sites are:

In `updateBotResolve` (bot missed):
```java
// BEFORE:
nextServer = 1;
prepareServe(GameConfig.BETWEEN_POINTS_DELAY, "Bot missed. Click anywhere to serve.");
// AFTER:
nextServer = 1;
enterItemPhase();
```

In `clientMiss` (opponent missed):
```java
// BEFORE:
nextServer = 1;
prepareServe(GameConfig.BETWEEN_POINTS_DELAY, "Opponent missed. P1 serves next.");
// AFTER:
nextServer = 1;
enterItemPhase();
```

In `handlePlayerMiss`:
```java
// BEFORE:
nextServer = 2;
prepareServe(GameConfig.BETWEEN_POINTS_DELAY, "You missed the shot. Opponent serves next.");
// AFTER:
nextServer = 2;
enterItemPhase();
```

In `handlePlayerFault`:
```java
// BEFORE:
nextServer = 2;
prepareServe(GameConfig.BETWEEN_POINTS_DELAY, text + " Opponent serves next.");
// AFTER:
nextServer = 2;
enterItemPhase();
```

In `botMissedShot`:
```java
// BEFORE:
nextServer = 1;
prepareServe(GameConfig.BETWEEN_POINTS_DELAY, text + " Click anywhere to serve.");
// AFTER:
nextServer = 1;
enterItemPhase();
```

Also clear effects when entering ITEM_PHASE by adding to `enterItemPhase()`:

```java
// At the start of enterItemPhase(), before dealing items:
p1Effects.clear();
p2Effects.clear();
```

- [ ] **Step 4: Compile and run all tests**

```bash
./gradlew :sim:test 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/world/MatchWorld3D.java
git commit -m "feat: fly-ball collision + scoring paths now enter ITEM_PHASE"
```

---

## Task 11: GameServer — runBestOf3 restructure

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/server/GameServer.java`

- [ ] **Step 1: Restructure runOneMatch into runBestOf3 + runOneRound**

Replace the `run()` inner while loop body:

```java
// BEFORE:
while (!shutdown) {
    try {
        runOneMatch();
    } catch (java.net.SocketException stopped) { ... }
}
```

```java
// AFTER:
while (!shutdown) {
    try {
        runBestOf3();
    } catch (java.net.SocketException stopped) {
        if (!shutdown) System.err.println("[GameServer] Socket error: " + stopped);
        break;
    }
}
```

Rename `runOneMatch()` to `runBestOf3()` and extract round logic. Replace the body of the former `runOneMatch()` with:

```java
private void runBestOf3() throws Exception {
    MatchLobby lobby = new MatchLobby();
    System.out.println("[GameServer] Waiting for JOIN...");
    serverSocket.setSoTimeout(200);
    try {
        while (!lobby.isReady() && !shutdown) {
            try {
                Socket socket = serverSocket.accept();
                GameConnection[] connRef = new GameConnection[1];
                connRef[0] = GameConnection.wrap(socket, Runnable::run,
                    new LobbyListener(lobby, connRef));
            } catch (java.net.SocketTimeoutException ignored) {}
        }
    } finally {
        serverSocket.setSoTimeout(0);
    }
    if (shutdown) return;

    p1 = lobby.p1;
    p2 = lobby.p2;
    MatchMode mode = lobby.mode;
    byte modeWire = mode == MatchMode.BOT ? PacketType.MODE_BOT : PacketType.MODE_PVP;
    p1.sendMatchReady(modeWire);
    if (p2 != null) p2.sendMatchReady(modeWire);

    p1.setListener(new MatchPlayerListener(1));
    if (p2 != null) p2.setListener(new MatchPlayerListener(2));

    int p1Wins = 0, p2Wins = 0;
    while (p1Wins < 2 && p2Wins < 2 && !shutdown) {
        int roundWinner = runOneRound(mode);
        if (roundWinner == 1) p1Wins++;
        else p2Wins++;
        sendRoundOverToAll(roundWinner, p1Wins, p2Wins);
        System.out.println("[GameServer] Round over — P" + roundWinner
            + " wins  (" + p1Wins + "-" + p2Wins + ")");
        if (p1Wins < 2 && p2Wins < 2) {
            // Brief pause between rounds
            LockSupport.parkNanos(2_000_000_000L);
        }
    }

    int matchWinner = p1Wins >= 2 ? 1 : 2;
    sendGameOverToAll(matchWinner);
    System.out.println("[GameServer] Match over — winner P" + matchWinner);

    endMatchConnections();
    world = null;
    p1 = null;
    p2 = null;
    System.out.println("[GameServer] Waiting for next JOIN...");
}
```

- [ ] **Step 2: Extract runOneRound**

Add the new method:

```java
private int runOneRound(MatchMode mode) throws Exception {
    MatchWorld3D w = new MatchWorld3D(MatchConfig.createDefault(), new RandomXS128());
    w.setMatchMode(mode);
    world = w;
    matchRunning = true;

    long lastNs = System.nanoTime();
    float stateAcc = 0f;
    Phase prevPhase = w.getPhase();

    while (matchRunning && !w.isMatchOver() && !shutdown) {
        long now = System.nanoTime();
        float delta = Math.min((now - lastNs) / 1_000_000_000f, 0.1f);
        lastNs = now;

        Runnable action;
        while ((action = actions.poll()) != null) action.run();

        w.update(delta);

        Phase curPhase = w.getPhase();
        if (prevPhase != Phase.ITEM_PHASE && curPhase == Phase.ITEM_PHASE) {
            broadcastItemDealt(w);
        }
        prevPhase = curPhase;

        if (w.consumeItemUsedEvent()) {
            broadcastItemUsed(w.getItemUsedPlayer(), w.getItemUsedId());
        }
        if (w.consumeFlySpawnEvent()) {
            broadcastFlySpawn(w.getFlySpawnXs(), w.getFlySpawnZs());
        }
        int killed = w.consumeFlyKilledIndex();
        if (killed >= 0) broadcastFlyKilled(killed);

        if (w.consumePaddleHitEvent())  sendSfxToAll(PacketType.SFX_PADDLE);
        if (w.consumeTableBounceEvent()) sendSfxToAll(PacketType.SFX_TABLE);

        stateAcc += delta;
        if (stateAcc >= STATE_DT) {
            stateAcc -= STATE_DT;
            broadcastState(w);
        }

        long sleepNs = TICK_NS - (System.nanoTime() - now);
        if (sleepNs > 100_000L) LockSupport.parkNanos(sleepNs);
    }

    world = null;
    return w.getOutcome() == MatchOutcome.PLAYER_WIN ? 1 : 2;
}
```

- [ ] **Step 3: Add new broadcast helpers**

```java
private void broadcastItemDealt(MatchWorld3D w) {
    GameConnection c1 = p1, c2 = p2;
    if (c1 != null) c1.sendItemDealt(1, w.getLastDealtItems(1));
    if (c1 != null) c1.sendItemDealt(2, w.getLastDealtItems(2));
    if (c2 != null) c2.sendItemDealt(1, w.getLastDealtItems(1));
    if (c2 != null) c2.sendItemDealt(2, w.getLastDealtItems(2));
}

private void broadcastItemUsed(int playerNumber, byte itemId) {
    GameConnection c1 = p1, c2 = p2;
    if (c1 != null) c1.sendItemUsed(playerNumber, itemId);
    if (c2 != null) c2.sendItemUsed(playerNumber, itemId);
}

private void broadcastFlySpawn(float[] xs, float[] zs) {
    GameConnection c1 = p1, c2 = p2;
    if (c1 != null) c1.sendFlySpawn(xs, zs);
    if (c2 != null) c2.sendFlySpawn(xs, zs);
}

private void broadcastFlyKilled(int idx) {
    GameConnection c1 = p1, c2 = p2;
    if (c1 != null) c1.sendFlyKilled(idx);
    if (c2 != null) c2.sendFlyKilled(idx);
}

private void sendRoundOverToAll(int winner, int p1Wins, int p2Wins) {
    GameConnection c1 = p1, c2 = p2;
    if (c1 != null) c1.sendRoundOver(winner, p1Wins, p2Wins);
    if (c2 != null) c2.sendRoundOver(winner, p1Wins, p2Wins);
}
```

- [ ] **Step 4: Add missing import**

At the top, add:

```java
import io.github.some_example_name.world.MatchWorld3D.Phase;
```

- [ ] **Step 5: Compile check**

```bash
./gradlew :sim:compileJava 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/server/GameServer.java
git commit -m "feat: GameServer runs best-of-3 rounds, broadcasts item events"
```

---

## Task 12: GameServer — USE_ITEM and ITEM_READY packet handling

**Files:**
- Modify: `sim/src/main/java/io/github/some_example_name/server/GameServer.java`

- [ ] **Step 1: Handle USE_ITEM in MatchPlayerListener**

In `MatchPlayerListener`, add:

```java
@Override
public void onUseItem(int itemId) {
    MatchWorld3D w = world;
    if (w == null || !matchRunning) return;
    ItemType item = ItemType.fromId((byte) itemId);
    if (item == null) return;
    actions.offer(() -> w.applyItem(playerNumber, item));
}

@Override
public void onItemReady() {
    MatchWorld3D w = world;
    if (w == null || !matchRunning) return;
    actions.offer(() -> w.playerReady(playerNumber));
}
```

Add the `ItemType` import at the top:

```java
import io.github.some_example_name.model.ItemType;
```

- [ ] **Step 2: Bot auto-ready in BOT mode**

In `runOneRound`, after the `broadcastItemDealt(w)` call, add bot auto-ready when in BOT mode:

```java
if (prevPhase != Phase.ITEM_PHASE && curPhase == Phase.ITEM_PHASE) {
    broadcastItemDealt(w);
    // Bot never sends ITEM_READY — auto-advance P2 after a short delay
    if (mode == MatchMode.BOT) {
        long botReadyAt = System.nanoTime() + 500_000_000L; // 0.5s "thinking" delay
        actions.offer(() -> {
            if (System.nanoTime() >= botReadyAt) w.playerReady(2);
        });
    }
}
```

- [ ] **Step 3: Compile check**

```bash
./gradlew :sim:compileJava 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Run all sim tests**

```bash
./gradlew :sim:test 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add sim/src/main/java/io/github/some_example_name/server/GameServer.java
git commit -m "feat: server handles USE_ITEM and ITEM_READY; bot auto-ready in BOT mode"
```

---

## Task 13: RetroPostProcess — Punch blur effect

**Files:**
- Modify: `lwjgl3/src/main/resources/shaders/retro.frag`
- Modify: `core/src/main/java/io/github/some_example_name/render/RetroPostProcess.java`

- [ ] **Step 1: Add u_blur uniform to the shader**

In `retro.frag`, after `uniform float u_warm;` add:

```glsl
uniform float u_blur; // punch blur intensity 0..1
```

In the `main()` function of `retro.frag`, find the snapped UV sampling block (the `vec2 snapped = ...` line). After the final colour value is computed (just before the last `gl_FragColor = ...`), add the blur overlay:

```glsl
// Punch blur: amplify chromatic aberration and add luminance noise
if (u_blur > 0.0) {
    float blurAb = u_aberration + u_blur * 0.04;
    vec3 rr = texture2D(u_texture, vec2(snapped.x + blurAb, snapped.y)).rgb;
    vec3 gg = texture2D(u_texture, snapped).rgb;
    vec3 bb = texture2D(u_texture, vec2(snapped.x - blurAb, snapped.y)).rgb;
    colour = vec3(rr.r, gg.g, bb.b);
    colour = mix(colour, vec3(dot(colour, vec3(0.299, 0.587, 0.114))), u_blur * 0.35);
}
```

Find exactly where `snapped` is declared in the shader and add this block right before `gl_FragColor`. If you need to locate the exact spot:

```bash
grep -n "snapped\|gl_FragColor" lwjgl3/src/main/resources/shaders/retro.frag
```

- [ ] **Step 2: Add setPunchBlur to RetroPostProcess**

In `RetroPostProcess.java`, after the `vignette` field add:

```java
private float punchBlur; // 0..1, driven by NetMatchScreen
```

Add a public setter:

```java
public void setPunchBlur(float t) { punchBlur = Math.max(0f, Math.min(1f, t)); }
```

In `endAndBlit()`, after the existing `shader.setUniformf("u_vignette", vignette);` line, add:

```java
shader.setUniformf("u_blur", punchBlur);
```

- [ ] **Step 3: Compile check**

```bash
./gradlew :core:compileJava :lwjgl3:compileJava 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add lwjgl3/src/main/resources/shaders/retro.frag \
        core/src/main/java/io/github/some_example_name/render/RetroPostProcess.java
git commit -m "feat: add punch-blur uniform to retro shader and setPunchBlur to RetroPostProcess"
```

---

## Task 14: MatchArenaRenderer — fly rendering

**Files:**
- Modify: `core/src/main/java/io/github/some_example_name/render/MatchArenaRenderer.java`

- [ ] **Step 1: Add fly sphere model + instances**

At the top of `MatchArenaRenderer`, after the existing `Model` fields, add:

```java
private Model flyModel;
private final com.badlogic.gdx.utils.Array<ModelInstance> flyInstances = new com.badlogic.gdx.utils.Array<>();
private float flyBuzzTimer;
```

In `buildModels()`, after the ball model, add:

```java
float fd = FlyState.FLY_RADIUS * 1.5f;
flyModel = mb.createSphere(fd, fd, fd, 6, 6,
    new Material(ColorAttribute.createDiffuse(new Color(0.15f, 0.15f, 0.05f, 1f))),
    VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
```

Add the import at the top:
```java
import io.github.some_example_name.world.FlyState;
```

In `dispose()`, add `if (flyModel != null) flyModel.dispose();`

- [ ] **Step 2: Add setFlies and renderFlies methods**

```java
/** Called each frame with the current live fly list. */
public void setFlies(java.util.List<FlyState> playerFlies, java.util.List<FlyState> oppFlies) {
    flyInstances.clear();
    addFlyInstances(playerFlies);
    addFlyInstances(oppFlies);
}

private void addFlyInstances(java.util.List<FlyState> flies) {
    for (FlyState fly : flies) {
        if (!fly.alive) continue;
        ModelInstance inst = new ModelInstance(flyModel);
        float buzz = com.badlogic.gdx.math.MathUtils.sin(flyBuzzTimer * 8f) * 0.08f;
        inst.transform.setToTranslation(fly.x, MatchWorld3D.TABLE_TOP_Y + 0.4f + buzz, fly.z);
        flyInstances.add(inst);
    }
}

public void tickFlyBuzz(float delta) { flyBuzzTimer += delta; }
```

- [ ] **Step 3: Render flies in render3DScene**

In `render3DScene()`, after the ball render block, add:

```java
for (ModelInstance fi : flyInstances) modelBatch.render(fi, environment);
```

- [ ] **Step 4: Compile check**

```bash
./gradlew :core:compileJava 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/io/github/some_example_name/render/MatchArenaRenderer.java
git commit -m "feat: add fly sphere rendering to MatchArenaRenderer"
```

---

## Task 15: ItemPhaseRenderer — 3D item objects on table

**Files:**
- Create: `core/src/main/java/io/github/some_example_name/render/ItemPhaseRenderer.java`

- [ ] **Step 1: Create ItemPhaseRenderer**

```java
// core/src/main/java/io/github/some_example_name/render/ItemPhaseRenderer.java
package io.github.some_example_name.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import io.github.some_example_name.model.ItemType;
import io.github.some_example_name.world.MatchWorld3D;
import java.util.List;

public final class ItemPhaseRenderer implements Disposable {
    private static final float ITEM_SIZE   = 0.35f;
    private static final float ITEM_Y_FLAT = MatchWorld3D.TABLE_TOP_Y + 0.2f;
    private static final float ITEM_Y_HOVER = ITEM_Y_FLAT + 0.35f;
    private static final float HOVER_LERP  = 6f;

    private final Model itemModel;
    private final Array<ItemEntry> p1Entries = new Array<>();
    private final Array<ItemEntry> p2Entries = new Array<>();

    private static final class ItemEntry {
        final ModelInstance instance;
        final ItemType type;
        boolean hovered;
        float currentY;
        ItemEntry(ModelInstance inst, ItemType type) {
            this.instance = inst; this.type = type;
            this.currentY = ITEM_Y_FLAT;
        }
    }

    public ItemPhaseRenderer() {
        ModelBuilder mb = new ModelBuilder();
        itemModel = mb.createBox(ITEM_SIZE, ITEM_SIZE, ITEM_SIZE,
            new Material(ColorAttribute.createDiffuse(Color.WHITE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    /** Populate item entries for both players' inventories. */
    public void load(List<ItemType> p1Items, List<ItemType> p2Items) {
        p1Entries.clear();
        p2Entries.clear();
        buildEntries(p1Items, p1Entries, true);
        buildEntries(p2Items, p2Entries, false);
    }

    private void buildEntries(List<ItemType> items, Array<ItemEntry> entries, boolean playerSide) {
        int n = items.size();
        float spacing = 1.0f;
        float startX = -(n - 1) * spacing * 0.5f;
        float z = playerSide ? MatchWorld3D.TABLE_HALF_LENGTH * 0.55f
                             : -MatchWorld3D.TABLE_HALF_LENGTH * 0.55f;
        for (int i = 0; i < n; i++) {
            Material mat = new Material(ColorAttribute.createDiffuse(colorForItem(items.get(i))));
            ModelInstance inst = new ModelInstance(itemModel);
            inst.materials.get(0).set(mat);
            float x = startX + i * spacing;
            inst.transform.setToTranslation(x, ITEM_Y_FLAT, z);
            entries.add(new ItemEntry(inst, items.get(i)));
        }
    }

    /** Mark an item as used (it will hover). */
    public void markUsed(int playerNumber, ItemType item) {
        Array<ItemEntry> entries = playerNumber == 1 ? p1Entries : p2Entries;
        for (ItemEntry e : entries) {
            if (e.type == item && !e.hovered) { e.hovered = true; return; }
        }
    }

    public void update(float delta) {
        updateEntries(p1Entries, delta);
        updateEntries(p2Entries, delta);
    }

    private void updateEntries(Array<ItemEntry> entries, float delta) {
        for (ItemEntry e : entries) {
            float targetY = e.hovered ? ITEM_Y_HOVER : ITEM_Y_FLAT;
            e.currentY += (targetY - e.currentY) * Math.min(1f, HOVER_LERP * delta);
            float[] t = e.instance.transform.getValues();
            // update Y component of translation (index 13 in column-major 4x4)
            e.instance.transform.val[com.badlogic.gdx.math.Matrix4.M13] = e.currentY;
        }
    }

    public void render(ModelBatch batch, Environment env) {
        for (ItemEntry e : p1Entries) batch.render(e.instance, env);
        for (ItemEntry e : p2Entries) batch.render(e.instance, env);
    }

    private static Color colorForItem(ItemType item) {
        return switch (item) {
            case PATCH_KIT   -> new Color(0.2f, 0.8f, 0.2f, 1f);   // green
            case WIDE_PADDLE -> new Color(0.2f, 0.6f, 1.0f, 1f);   // blue
            case SLOW_MO     -> new Color(0.4f, 0.2f, 0.8f, 1f);   // purple
            case STEAL       -> new Color(0.8f, 0.8f, 0.1f, 1f);   // yellow
            case FAST_SERVE  -> new Color(1.0f, 0.4f, 0.1f, 1f);   // orange
            case TINY_PADDLE -> new Color(0.9f, 0.1f, 0.1f, 1f);   // red
            case PUNCH       -> new Color(0.9f, 0.3f, 0.5f, 1f);   // pink
            case FLY_BAIT    -> new Color(0.3f, 0.5f, 0.1f, 1f);   // dark green
            case COIN_FLIP   -> new Color(0.8f, 0.7f, 0.1f, 1f);   // gold
        };
    }

    @Override public void dispose() { itemModel.dispose(); }
}
```

- [ ] **Step 2: Compile check**

```bash
./gradlew :core:compileJava 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/io/github/some_example_name/render/ItemPhaseRenderer.java
git commit -m "feat: add ItemPhaseRenderer with colored 3D item cubes and hover animation"
```

---

## Task 16: NetMatchScreen — item phase UI and packet handlers

**Files:**
- Modify: `core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java`

- [ ] **Step 1: Add item phase fields**

After the existing `private boolean disconnected;` field, add:

```java
// ── Item phase ────────────────────────────────────────────────────────────
private boolean inItemPhase;
private ItemPhaseRenderer itemPhaseRenderer;
private final java.util.List<io.github.some_example_name.model.ItemType> myItems   = new java.util.ArrayList<>();
private final java.util.List<io.github.some_example_name.model.ItemType> oppItems  = new java.util.ArrayList<>();
private boolean itemReadySent;

// ── Flies ─────────────────────────────────────────────────────────────────
private final java.util.List<io.github.some_example_name.world.FlyState> myFlies  = new java.util.ArrayList<>();
private final java.util.List<io.github.some_example_name.world.FlyState> oppFlies = new java.util.ArrayList<>();

// ── Punch blur ────────────────────────────────────────────────────────────
private float punchTimer;

// ── Round overlay ─────────────────────────────────────────────────────────
private String roundOverlayText;
private float roundOverlayTimer;
private int p1RoundWins;
private int p2RoundWins;
```

Add the import at the top:

```java
import io.github.some_example_name.render.ItemPhaseRenderer;
import io.github.some_example_name.model.ItemType;
import io.github.some_example_name.world.FlyState;
```

- [ ] **Step 2: Initialise and dispose ItemPhaseRenderer**

In `show()`, after `arena = new MatchArenaRenderer(...)`:

```java
itemPhaseRenderer = new ItemPhaseRenderer();
```

In `hide()` (create it if missing, or add to existing):

```java
@Override public void hide() {
    if (itemPhaseRenderer != null) { itemPhaseRenderer.dispose(); itemPhaseRenderer = null; }
}
```

- [ ] **Step 3: Implement new Listener callbacks**

Add these methods to `NetMatchScreen` (it already implements `GameConnection.Listener`):

```java
@Override
public void onRoundOver(int winner, int p1Wins, int p2Wins) {
    p1RoundWins = p1Wins;
    p2RoundWins = p2Wins;
    String who = (playerNumber == winner) ? "YOU WIN THE ROUND" : "OPPONENT WINS THE ROUND";
    roundOverlayText = who + "  (" + p1Wins + " - " + p2Wins + ")";
    roundOverlayTimer = 2.5f;
    // reset lives display for next round
    p1lives = 5;
    p2lives = 5;
    inItemPhase = false;
    myItems.clear();
    oppItems.clear();
    myFlies.clear();
    oppFlies.clear();
    punchTimer = 0f;
}

@Override
public void onItemDealt(int forPlayer, byte[] itemIds) {
    java.util.List<ItemType> target = (forPlayer == playerNumber) ? myItems : oppItems;
    for (byte id : itemIds) {
        ItemType t = ItemType.fromId(id);
        if (t != null) target.add(t);
    }
    inItemPhase = true;
    itemReadySent = false;
    if (itemPhaseRenderer != null) itemPhaseRenderer.load(myItems, oppItems);
}

@Override
public void onItemUsed(int byPlayer, int itemId) {
    ItemType t = ItemType.fromId((byte) itemId);
    if (t == null) return;
    java.util.List<ItemType> inv = (byPlayer == playerNumber) ? myItems : oppItems;
    inv.remove(t);
    if (itemPhaseRenderer != null) itemPhaseRenderer.markUsed(byPlayer, t);
    // Punch blur — applied when opponent used PUNCH against us
    if (t == ItemType.PUNCH && byPlayer != playerNumber) punchTimer = 10f;
}

@Override
public void onFlySpawn(float[] xs, float[] zs) {
    // Flies spawned on OUR side (opponent used FLY_BAIT against us)
    myFlies.clear();
    for (int i = 0; i < xs.length; i++) myFlies.add(new FlyState(xs[i], zs[i]));
    if (arena != null) arena.setFlies(myFlies, oppFlies);
}

@Override
public void onFlyKilled(int flyIndex) {
    if (flyIndex < myFlies.size()) myFlies.get(flyIndex).alive = false;
    else {
        int oppIdx = flyIndex - myFlies.size();
        if (oppIdx < oppFlies.size()) oppFlies.get(oppIdx).alive = false;
    }
    if (arena != null) arena.setFlies(myFlies, oppFlies);
}
```

- [ ] **Step 4: Add pickItem to ItemPhaseRenderer**

Add this method to `ItemPhaseRenderer`:

```java
/**
 * Returns the ItemType of the first item the ray intersects on the given player's side,
 * or null if no hit. Uses sphere intersection with radius = ITEM_SIZE.
 */
public ItemType pickItem(com.badlogic.gdx.math.collision.Ray ray, int playerNumber) {
    Array<ItemEntry> entries = playerNumber == 1 ? p1Entries : p2Entries;
    float r2 = ITEM_SIZE * ITEM_SIZE;
    for (ItemEntry e : entries) {
        if (e.hovered) continue; // already used
        float[] m = e.instance.transform.val;
        float ex = m[com.badlogic.gdx.math.Matrix4.M03];
        float ey = m[com.badlogic.gdx.math.Matrix4.M13];
        float ez = m[com.badlogic.gdx.math.Matrix4.M23];
        float ox = ray.origin.x - ex, oy = ray.origin.y - ey, oz = ray.origin.z - ez;
        float b = 2f * (ray.direction.x*ox + ray.direction.y*oy + ray.direction.z*oz);
        float c = ox*ox + oy*oy + oz*oz - r2;
        if (b*b - 4f*c >= 0f) return e.type;
    }
    return null;
}
```

- [ ] **Step 5: Wire item click and READY button in touchDown**

In the existing `touchDown` / `InputAdapter`, at the very start of the touch handler (before the existing `conn.sendClick(...)` call), add:

```java
// During item phase
if (inItemPhase) {
    // READY zone: bottom 15% of screen
    if (screenY > Gdx.graphics.getHeight() * 0.85f && !itemReadySent) {
        itemReadySent = true;
        inItemPhase = false;
        if (conn != null) conn.sendItemReady();
        return true;
    }
    // Item pick: ray-test against item cubes
    if (itemPhaseRenderer != null && arena != null) {
        com.badlogic.gdx.math.collision.Ray ray = arena.getCamera()
            .getPickRay(screenX, Gdx.graphics.getHeight() - screenY);
        ItemType picked = itemPhaseRenderer.pickItem(ray, playerNumber);
        if (picked != null && conn != null) conn.sendUseItem(picked.getId());
    }
    return true; // consume — no CLICK sent to server during ITEM_PHASE
}
```

Add a READY button to the render path (see Step 5 → now Step 6). Clicking READY sends `conn.sendItemReady()`.

- [ ] **Step 5: Render item phase overlay and round overlay**

In the `render(float delta)` method, after `postProcess.begin()` (or wherever the 2D HUD render happens):

Add at the end of the HUD 2D rendering section:

```java
// Item phase
if (inItemPhase && itemPhaseRenderer != null) {
    itemPhaseRenderer.update(delta);
    itemPhaseRenderer.render(arena.getModelBatch(), arena.getEnvironment());
    // Draw READY button text in HUD
    batch.begin();
    String readyLabel = itemReadySent ? "WAITING..." : "[ READY ]";
    layout.setText(context.getFontMedium(), readyLabel);
    context.getFontMedium().draw(batch, readyLabel,
        viewport.getWorldWidth() * 0.5f - layout.width * 0.5f, 60f);
    batch.end();
}

// Round overlay
if (roundOverlayTimer > 0f) {
    roundOverlayTimer -= delta;
    batch.begin();
    layout.setText(context.getFontLarge(), roundOverlayText);
    context.getFontLarge().draw(batch, roundOverlayText,
        viewport.getWorldWidth() * 0.5f - layout.width * 0.5f,
        viewport.getWorldHeight() * 0.5f + layout.height * 0.5f);
    batch.end();
}

// Punch blur
if (punchTimer > 0f) {
    punchTimer -= delta;
    context.getRetroPostProcess().setPunchBlur(punchTimer / 10f);
} else {
    context.getRetroPostProcess().setPunchBlur(0f);
}

// Fly buzz animation
if (arena != null) arena.tickFlyBuzz(delta);
```

- [ ] **Step 6: Expose getCamera on MatchArenaRenderer**

In `MatchArenaRenderer.java`, add a getter for the camera (it's already used internally):

```java
public com.badlogic.gdx.graphics.PerspectiveCamera getCamera() { return cam; }
```

- [ ] **Step 7: Compile check**

```bash
./gradlew :core:compileJava 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/io/github/some_example_name/screen/NetMatchScreen.java
git commit -m "feat: NetMatchScreen handles item phase, round overlay, punch blur, flies"
```

---

## Task 17: End-to-end smoke test

- [ ] **Step 1: Run all tests**

```bash
./gradlew :sim:test 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` — all tests pass.

- [ ] **Step 2: Build the desktop launcher**

```bash
./gradlew :lwjgl3:jar 2>&1 | tail -15
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Run VS BOT and verify item phase**

```bash
./gradlew :lwjgl3:run
```

Play a point intentionally (let the ball pass). After losing a life you should see:
- Item phase: colored 3D cubes appear on each side of the table
- Status text says "Use your items, then press READY."
- Bottom of screen shows `[ READY ]`

Click the READY zone (bottom 15% of screen). The serve phase should begin.

- [ ] **Step 4: Verify round structure**

Play until one player reaches 0 lives. You should see:
- "YOU WIN THE ROUND" or "OPPONENT WINS THE ROUND" overlay for ~2.5s
- Lives reset to 5 and a new round begins

Win two rounds — the existing GAME_OVER screen should appear.

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "feat: item system + best-of-3 rounds — complete implementation"
```
