package io.github.some_example_name.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import io.github.some_example_name.model.ItemType;
import io.github.some_example_name.world.MatchWorld3D;
import java.util.List;

public final class ItemPhaseRenderer implements Disposable {
    private static final float ITEM_SIZE    = 0.35f;
    private static final float ITEM_Y_FLAT  = MatchWorld3D.TABLE_TOP_Y + 0.2f;
    private static final float ITEM_Y_HOVER = ITEM_Y_FLAT + 0.35f;
    private static final float HOVER_LERP   = 6f;

    private final Model itemModel;
    private final Array<ItemEntry> p1Entries = new Array<>();
    private final Array<ItemEntry> p2Entries = new Array<>();

    private static final class ItemEntry {
        final ModelInstance instance;
        final ItemType type;
        boolean hovered;
        float currentY;
        ItemEntry(ModelInstance inst, ItemType type) {
            this.instance = inst;
            this.type = type;
            this.currentY = ITEM_Y_FLAT;
        }
    }

    public ItemPhaseRenderer() {
        ModelBuilder mb = new ModelBuilder();
        itemModel = mb.createBox(ITEM_SIZE, ITEM_SIZE, ITEM_SIZE,
            new Material(ColorAttribute.createDiffuse(Color.WHITE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

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
            e.instance.transform.val[Matrix4.M13] = e.currentY;
        }
    }

    public void render(ModelBatch batch, Environment env) {
        for (ItemEntry e : p1Entries) batch.render(e.instance, env);
        for (ItemEntry e : p2Entries) batch.render(e.instance, env);
    }

    public ItemType pickItem(com.badlogic.gdx.math.collision.Ray ray, int playerNumber) {
        Array<ItemEntry> entries = playerNumber == 1 ? p1Entries : p2Entries;
        float r2 = ITEM_SIZE * ITEM_SIZE;
        for (ItemEntry e : entries) {
            if (e.hovered) continue;
            float ex = e.instance.transform.val[Matrix4.M03];
            float ey = e.instance.transform.val[Matrix4.M13];
            float ez = e.instance.transform.val[Matrix4.M23];
            float ox = ray.origin.x - ex, oy = ray.origin.y - ey, oz = ray.origin.z - ez;
            float b = 2f * (ray.direction.x*ox + ray.direction.y*oy + ray.direction.z*oz);
            float c = ox*ox + oy*oy + oz*oz - r2;
            if (b*b - 4f*c >= 0f) return e.type;
        }
        return null;
    }

    private static Color colorForItem(ItemType item) {
        return switch (item) {
            case PATCH_KIT   -> new Color(0.2f, 0.8f, 0.2f, 1f);
            case WIDE_PADDLE -> new Color(0.2f, 0.6f, 1.0f, 1f);
            case SLOW_MO     -> new Color(0.4f, 0.2f, 0.8f, 1f);
            case STEAL       -> new Color(0.8f, 0.8f, 0.1f, 1f);
            case FAST_SERVE  -> new Color(1.0f, 0.4f, 0.1f, 1f);
            case TINY_PADDLE -> new Color(0.9f, 0.1f, 0.1f, 1f);
            case PUNCH       -> new Color(0.9f, 0.3f, 0.5f, 1f);
            case FLY_BAIT    -> new Color(0.3f, 0.5f, 0.1f, 1f);
            case COIN_FLIP   -> new Color(0.8f, 0.7f, 0.1f, 1f);
        };
    }

    @Override public void dispose() { itemModel.dispose(); }
}
