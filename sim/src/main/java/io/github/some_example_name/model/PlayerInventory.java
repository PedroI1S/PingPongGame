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
