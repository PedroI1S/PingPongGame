package io.github.some_example_name.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute;
import com.badlogic.gdx.graphics.g3d.loader.ObjLoader;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.g3d.utils.TextureProvider;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import io.github.some_example_name.model.ItemType;
import io.github.some_example_name.world.MatchWorld3D;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Renders the per-player item shelf during the item phase. Each item is the
 * generated voxel OBJ under {@code assets/models/items/<type>/item.obj}
 * (tools/voxel/generate_props.py); if a model is missing or fails to load,
 * that item falls back to a colour-coded cube so the phase always works.
 * Picking/hover use the item's logical centre (not the render transform), so
 * scaling and idle spin don't affect click accuracy.
 */
public final class ItemPhaseRenderer implements Disposable {
    private static final float ITEM_DISPLAY = 0.65f;  // longest axis of a model item
    private static final float BOX_SIZE     = 0.35f;  // fallback cube edge
    private static final float PICK_RADIUS  = 0.38f;
    private static final float ITEM_Y_FLAT  = MatchWorld3D.TABLE_TOP_Y + 0.28f;
    private static final float ITEM_Y_HOVER = ITEM_Y_FLAT + 0.35f;
    private static final float HOVER_LERP   = 6f;
    private static final float SPIN_SPEED   = 35f;    // deg/sec gentle idle spin
    /** Inverted-hull silhouette: enlarged black copy, front faces culled. */
    private static final float OUTLINE_SCALE = 1.16f;
    private static final float PAD_SIZE = 0.85f;

    private final Model boxModel;                       // shared fallback cube
    private final Model padModel;                       // dark display pad under each item
    private final EnumMap<ItemType, Model> itemModels = new EnumMap<>(ItemType.class);
    private final Array<ItemEntry> p1Entries = new Array<>();
    private final Array<ItemEntry> p2Entries = new Array<>();
    private final boolean myItemsOnPositiveZ;
    private float spin;

    private static final class ItemEntry {
        final ModelInstance instance;
        final ModelInstance outline;
        final ModelInstance pad;
        final ItemType type;
        final float baseX, baseZ;   // logical centre on the table
        final float scale;          // 1 for the cube; auto-fit for models
        final Vector3 center;       // model-local centre to re-origin
        final boolean spinnable;    // models spin; the flat cube stays put
        boolean hovered, used;
        float currentY;
        ItemEntry(ModelInstance inst, ModelInstance outline, ModelInstance pad,
                  ItemType type, float x, float z,
                  float scale, Vector3 center, boolean spinnable) {
            this.instance = inst; this.outline = outline; this.pad = pad;
            this.type = type;
            this.baseX = x; this.baseZ = z;
            this.scale = scale; this.center = center;
            this.spinnable = spinnable;
            this.currentY = ITEM_Y_FLAT;
        }
    }

    /**
     * @param myItemsOnPositiveZ {@code true} if the local player (P1) views from
     *        +z, so the local player's items render on the +z side (P2 mirrors).
     */
    public ItemPhaseRenderer(boolean myItemsOnPositiveZ) {
        this.myItemsOnPositiveZ = myItemsOnPositiveZ;
        ModelBuilder mb = new ModelBuilder();
        boxModel = mb.createBox(BOX_SIZE, BOX_SIZE, BOX_SIZE,
            new Material(ColorAttribute.createDiffuse(Color.WHITE)),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        // Near-black display pad — isolates each prop from the busy tabletop.
        padModel = mb.createBox(PAD_SIZE, 0.05f, PAD_SIZE,
            new Material(ColorAttribute.createDiffuse(new Color(0.05f, 0.04f, 0.04f, 1f))),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        loadItemModels();
    }

    private void loadItemModels() {
        ObjLoader loader = new ObjLoader();
        // Mipmapped trilinear sampling keeps textures stable at small
        // on-screen sizes.
        TextureProvider textures = new TextureProvider.FileTextureProvider(
            Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear,
            Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat, true);
        for (ItemType t : ItemType.values()) {
            String path = "models/items/" + t.name().toLowerCase(Locale.ROOT) + "/item.obj";
            try {
                if (!Gdx.files.internal(path).exists()) continue;
                Model m = loader.loadModel(Gdx.files.internal(path), textures);
                if (m == null || m.meshes.size == 0) {
                    if (m != null) m.dispose();
                    continue;
                }
                // .mtl has no Kd; force white so the baked texture shows.
                for (Material mat : m.materials) {
                    ColorAttribute d = (ColorAttribute) mat.get(ColorAttribute.Diffuse);
                    if (d != null) d.color.set(Color.WHITE);
                    else mat.set(ColorAttribute.createDiffuse(Color.WHITE));
                    // Render imported OBJs double-sided — winding-proof.
                    mat.set(IntAttribute.createCullFace(GL20.GL_NONE));
                }
                itemModels.put(t, m);
            } catch (Throwable e) {
                Gdx.app.error("ItemPhaseRenderer", "item model failed: " + t, e);
            }
        }
    }

    public void load(List<ItemType> myItems, List<ItemType> oppItems) {
        p1Entries.clear();
        p2Entries.clear();
        buildEntries(myItems,  p1Entries,  myItemsOnPositiveZ);
        buildEntries(oppItems, p2Entries, !myItemsOnPositiveZ);
    }

    private void buildEntries(List<ItemType> items, Array<ItemEntry> entries, boolean positiveZSide) {
        int n = items.size();
        float spacing = 1.0f;
        float startX = -(n - 1) * spacing * 0.5f;
        float z = positiveZSide ? MatchWorld3D.TABLE_HALF_LENGTH * 0.55f
                                : -MatchWorld3D.TABLE_HALF_LENGTH * 0.55f;
        for (int i = 0; i < n; i++) {
            entries.add(makeEntry(items.get(i), startX + i * spacing, z));
        }
    }

    private ItemEntry makeEntry(ItemType type, float x, float z) {
        Model m = itemModels.get(type);
        ModelInstance pad = new ModelInstance(padModel);
        pad.transform.setToTranslation(x, MatchWorld3D.TABLE_TOP_Y + 0.015f, z);
        if (m != null) {
            BoundingBox bb = new BoundingBox();
            m.calculateBoundingBox(bb);
            Vector3 center = bb.getCenter(new Vector3());
            float maxDim = Math.max(bb.getWidth(), Math.max(bb.getHeight(), bb.getDepth()));
            float scale = ITEM_DISPLAY / Math.max(1e-4f, maxDim);
            return new ItemEntry(new ModelInstance(m), makeOutline(m), pad,
                type, x, z, scale, center, true);
        }
        ModelInstance inst = new ModelInstance(boxModel);
        inst.materials.get(0).set(new Material(ColorAttribute.createDiffuse(colorForItem(type))));
        return new ItemEntry(inst, makeOutline(boxModel), pad,
            type, x, z, 1f, new Vector3(), false);
    }

    /**
     * Enlarged black copy with FRONT faces culled — only the shell behind the
     * prop renders, forming a silhouette rim that separates it from the
     * busy tabletop behind.
     */
    private static ModelInstance makeOutline(Model model) {
        ModelInstance outline = new ModelInstance(model);
        for (Material mat : outline.materials) {
            ColorAttribute d = (ColorAttribute) mat.get(ColorAttribute.Diffuse);
            if (d != null) d.color.set(Color.BLACK);
            else mat.set(ColorAttribute.createDiffuse(Color.BLACK));
            mat.set(IntAttribute.createCullFace(GL20.GL_FRONT));
        }
        return outline;
    }

    public void markUsed(int playerNumber, ItemType item) {
        Array<ItemEntry> entries = playerNumber == 1 ? p1Entries : p2Entries;
        for (ItemEntry e : entries) {
            if (e.type == item && !e.used) { e.used = true; e.hovered = false; return; }
        }
    }

    /**
     * Ray-tests pickable items for the given player and updates the transient
     * {@code hovered} flag so the floating animation activates on mouse-over.
     *
     * @return {@code true} if any item newly became hovered this call
     *         (rising edge — callers use it for the hover sound)
     */
    public boolean updateHover(Ray ray, int playerNumber) {
        Array<ItemEntry> entries = playerNumber == 1 ? p1Entries : p2Entries;
        boolean rose = false;
        for (ItemEntry e : entries) {
            boolean was = e.hovered;
            e.hovered = !e.used && raySphere(ray, e.baseX, e.currentY, e.baseZ);
            rose |= !was && e.hovered;
        }
        return rose;
    }

    public void update(float delta) {
        spin = (spin + SPIN_SPEED * delta) % 360f;
        updateEntries(p1Entries, delta);
        updateEntries(p2Entries, delta);
    }

    private void updateEntries(Array<ItemEntry> entries, float delta) {
        for (ItemEntry e : entries) {
            float targetY = (e.hovered || e.used) ? ITEM_Y_HOVER : ITEM_Y_FLAT;
            e.currentY += (targetY - e.currentY) * Math.min(1f, HOVER_LERP * delta);
            // T(pos) · Ry(spin) · S(scale) · T(-center): visual centre lands on (baseX, currentY, baseZ)
            applyTransform(e.instance, e, e.scale);
            applyTransform(e.outline, e, e.scale * OUTLINE_SCALE);
        }
    }

    private void applyTransform(ModelInstance inst, ItemEntry e, float scale) {
        inst.transform.idt();
        inst.transform.translate(e.baseX, e.currentY, e.baseZ);
        if (e.spinnable) inst.transform.rotate(0f, 1f, 0f, spin);
        if (scale != 1f) inst.transform.scale(scale, scale, scale);
        inst.transform.translate(-e.center.x, -e.center.y, -e.center.z);
    }

    public void render(ModelBatch batch, Environment env) {
        for (ItemEntry e : p1Entries) batch.render(e.pad, env);
        for (ItemEntry e : p2Entries) batch.render(e.pad, env);
        for (ItemEntry e : p1Entries) batch.render(e.outline, env);
        for (ItemEntry e : p2Entries) batch.render(e.outline, env);
        for (ItemEntry e : p1Entries) batch.render(e.instance, env);
        for (ItemEntry e : p2Entries) batch.render(e.instance, env);
    }

    public ItemType pickItem(Ray ray, int playerNumber) {
        Array<ItemEntry> entries = playerNumber == 1 ? p1Entries : p2Entries;
        for (ItemEntry e : entries) {
            if (e.used) continue;
            if (raySphere(ray, e.baseX, e.currentY, e.baseZ)) return e.type;
        }
        return null;
    }

    private static boolean raySphere(Ray ray, float cx, float cy, float cz) {
        float ox = ray.origin.x - cx, oy = ray.origin.y - cy, oz = ray.origin.z - cz;
        float b = 2f * (ray.direction.x * ox + ray.direction.y * oy + ray.direction.z * oz);
        float c = ox * ox + oy * oy + oz * oz - PICK_RADIUS * PICK_RADIUS;
        float disc = b * b - 4f * c;
        // Also require the hit to be in front of the ray origin.
        return disc >= 0f && (-b + (float) Math.sqrt(disc)) >= 0f;
    }

    /** Fallback cube tint, also a quick colour legend per item. */
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

    @Override public void dispose() {
        boxModel.dispose();
        padModel.dispose();
        for (Model m : itemModels.values()) m.dispose();
        itemModels.clear();
    }
}
