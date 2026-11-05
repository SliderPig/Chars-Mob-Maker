package com.example.charmobmaker.client.render;

import com.example.charmobmaker.CharMobMaker;
import com.example.charmobmaker.model.Part;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Reads other mobs' models so their body parts can be used: bakes the model layer of a mob, walks its named parts, and copies each part's quads into a Mesh.
// Fields are found by their type because their names are scrambled in the real game.
public final class MobLibrary {
    public static final class Bone {
        public final String path, name;
        public final Mesh self;   // just this bone
        public final Mesh whole;  // with the bones hanging off it, in place
        public final float[] pos, rot; // where it sits in its parent, in our units
        public final List<Bone> kids = new ArrayList<>();
        public final boolean topLevel;

        Bone(String path, String name, Mesh self, Mesh whole, float[] pos, float[] rot, boolean topLevel) {
            this.path = path;
            this.name = name;
            this.self = self;
            this.whole = whole;
            this.pos = pos;
            this.rot = rot;
            this.topLevel = topLevel;
        }
    }

    public static final class Model {
        public final ResourceLocation id;
        public final ResourceLocation texture;
        public final List<Bone> tops = new ArrayList<>();
        public final List<Bone> all = new ArrayList<>();
        public final Map<String, Bone> byPath = new HashMap<>();
        public LivingEntity dummy;
        public EntityRenderer<? super LivingEntity> renderer;
        public float width = 0.6f, height = 1.8f;
        public boolean brokenLive;
        int lastTick = -1;

        Model(ResourceLocation id, ResourceLocation texture) {
            this.id = id;
            this.texture = texture;
        }
    }

    public record Entry(ResourceLocation id, Component name, ItemStack egg) {}

    private static final Map<ResourceLocation, Model> MODELS = new HashMap<>();
    private static List<Entry> everyMob;
    private static Field childrenField, cubesField, rootsField;

    private MobLibrary() {}

    public static void clear() {
        MODELS.clear();
        everyMob = null;
    }

    // Dummies belong to one world, so they're made again in the next one.
    public static void worldChanged() {
        for (Model m : MODELS.values()) m.dummy = null;
        MODELS.clear();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static List<Entry> all() {
        if (everyMob != null) return everyMob;
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<net.minecraft.resources.ResourceKey<EntityType<?>>, EntityType<?>> e : ForgeRegistries.ENTITY_TYPES.getEntries()) {
            EntityType<?> type = e.getValue();
            if (type == EntityType.PLAYER || type == EntityType.ARMOR_STAND) continue;
            if (!DefaultAttributes.hasSupplier((EntityType) type)) continue;
            ResourceLocation id = e.getKey().location();
            if (id.getNamespace().equals(CharMobMaker.MODID)) continue;
            var egg = ForgeSpawnEggItem.fromEntityType(type);
            out.add(new Entry(id, type.getDescription(), egg == null ? ItemStack.EMPTY : new ItemStack(egg)));
        }
        out.sort(Comparator.comparing(a -> a.name().getString().toLowerCase(Locale.ROOT)));
        everyMob = out;
        return out;
    }

    public static Model get(String entityId) {
        ResourceLocation id = ResourceLocation.tryParse(entityId);
        if (id == null) return null;
        Model m = MODELS.get(id);
        if (m != null) return m;
        if (MODELS.containsKey(id)) return null;
        m = build(id);
        MODELS.put(id, m);
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Model build(ResourceLocation id) {
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) return null;
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(id);
        Minecraft mc = Minecraft.getInstance();
        LivingEntity dummy = null;
        EntityRenderer<? super LivingEntity> renderer = null;
        ResourceLocation texture = null;
        try {
            if (mc.level != null) {
                Entity e = type.create(mc.level);
                if (e instanceof LivingEntity le && !(e instanceof Player)) {
                    dummy = le;
                    renderer = (EntityRenderer<? super LivingEntity>) mc.getEntityRenderDispatcher().getRenderer(le);
                    texture = renderer.getTextureLocation(le);
                }
            }
        } catch (Throwable t) {
            CharMobMaker.LOG.warn("[MobMaker] Couldn't look at mob {}: {}", id, t.toString());
        }
        Model model = new Model(id, texture == null ? new ResourceLocation(id.getNamespace(), "textures/entity/" + id.getPath() + ".png") : texture);
        model.dummy = dummy;
        model.renderer = renderer;
        model.width = type.getWidth();
        model.height = type.getHeight();
        ModelPart root = bakeRoot(mc.getEntityModels(), id);
        if (root != null) {
            try {
                readBones(model, root);
            } catch (Throwable t) {
                CharMobMaker.LOG.warn("[MobMaker] Couldn't read the model of {}: {}", id, t.toString());
            }
        }
        return model;
    }

    private static ModelPart bakeRoot(EntityModelSet set, ResourceLocation id) {
        ModelPart root = tryBake(set, new ModelLayerLocation(id, "main"));
        if (root != null && !isEmptyModel(root)) return root;
        for (ModelLayerLocation layer : knownLayers(set)) {
            if (layer.getModel().getNamespace().equals(id.getNamespace()) && layer.getModel().getPath().contains(id.getPath())) {
                ModelPart p = tryBake(set, layer);
                if (p != null && !isEmptyModel(p)) return p;
            }
        }
        return root;
    }

    private static boolean isEmptyModel(ModelPart root) {
        return root.getAllParts().allMatch(p -> cubes(p).isEmpty());
    }

    private static ModelPart tryBake(EntityModelSet set, ModelLayerLocation layer) {
        try {
            return set.bakeLayer(layer);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ModelLayerLocation> knownLayers(EntityModelSet set) {
        try {
            if (rootsField == null) {
                for (Field f : EntityModelSet.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        rootsField = f;
                    }
                }
            }
            return rootsField == null ? List.of() : new ArrayList<>(((Map<ModelLayerLocation, ?>) rootsField.get(set)).keySet());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ModelPart> children(ModelPart p) {
        try {
            if (childrenField == null) {
                for (Field f : ModelPart.class.getDeclaredFields()) {
                    if (Map.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        childrenField = f;
                    }
                }
            }
            return (Map<String, ModelPart>) childrenField.get(p);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ModelPart.Cube> cubes(ModelPart p) {
        try {
            if (cubesField == null) {
                for (Field f : ModelPart.class.getDeclaredFields()) {
                    if (List.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        cubesField = f;
                    }
                }
            }
            return (List<ModelPart.Cube>) cubesField.get(p);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void readBones(Model model, ModelPart root) {
        root.getAllParts().forEach(ModelPart::resetPose);
        // A root that has its own cubes or offset counts as one more bone.
        if (!cubes(root).isEmpty()) {
            addBone(model, null, "root", "root", root, 0, 24f);
            return;
        }
        for (Map.Entry<String, ModelPart> e : inOrder(children(root))) {
            addBone(model, null, e.getKey(), e.getKey(), e.getValue(), 0, 24f);
        }
    }

    // Head first, then the body, then the rest by name, so left and right sit together.
    private static List<Map.Entry<String, ModelPart>> inOrder(Map<String, ModelPart> parts) {
        List<Map.Entry<String, ModelPart>> list = new ArrayList<>(parts.entrySet());
        list.sort(Comparator.<Map.Entry<String, ModelPart>>comparingInt(e -> rank(e.getKey())).thenComparing(Map.Entry::getKey));
        return list;
    }

    private static int rank(String name) {
        if (name.contains("head")) return 0;
        if (name.contains("body") || name.contains("torso")) return 1;
        return 2;
    }

    private static Bone addBone(Model model, Bone parent, String path, String key, ModelPart part, int depth, float ground) {
        Mesh self = mesh(part, false);
        Mesh whole = mesh(part, true);
        if (whole.isEmpty()) return null;
        float[] pos = {part.x, ground - part.y, -part.z};
        float toDeg = (float) (180.0 / Math.PI);
        float[] rot = {part.xRot * toDeg, -part.yRot * toDeg, -part.zRot * toDeg};
        Bone b = new Bone(path, pretty(key), self, whole, pos, rot, parent == null);
        model.all.add(b);
        model.byPath.put(path, b);
        if (parent == null) model.tops.add(b);
        else parent.kids.add(b);
        if (depth < 12) {
            for (Map.Entry<String, ModelPart> e : inOrder(children(part))) {
                addBone(model, b, path + "/" + e.getKey(), e.getKey(), e.getValue(), depth + 1, 0f);
            }
        }
        return b;
    }

    private static Mesh mesh(ModelPart part, boolean withKids) {
        Mesh.Builder out = new Mesh.Builder();
        QuadCatcher catcher = new QuadCatcher(out);
        PoseStack ps = new PoseStack();
        for (ModelPart.Cube c : cubes(part)) c.compile(ps.last(), catcher, 0, 0, 1f, 1f, 1f, 1f);
        if (withKids) for (ModelPart kid : children(part).values()) drawTree(kid, ps, catcher);
        return out.build();
    }

    private static void drawTree(ModelPart part, PoseStack ps, QuadCatcher catcher) {
        ps.pushPose();
        part.translateAndRotate(ps);
        for (ModelPart.Cube c : cubes(part)) c.compile(ps.last(), catcher, 0, 0, 1f, 1f, 1f, 1f);
        for (ModelPart kid : children(part).values()) drawTree(kid, ps, catcher);
        ps.popPose();
    }

    public static String pretty(String key) {
        StringBuilder b = new StringBuilder();
        boolean up = true;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_' || c == '/' || c == '-') {
                b.append(' ');
                continue;
            }
            b.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return b.toString();
    }

    // The whole mob, drawn by its own real renderer. Moves its legs along with the mob wearing it. Any trouble and it quietly stops trying.
    public static void drawWhole(Model m, PoseStack ps, MultiBufferSource buffers, int light, float partial, LivingEntity host) {
        if (m.dummy == null || m.renderer == null || m.brokenLive) return;
        try {
            if (host != null) {
                m.dummy.tickCount = host.tickCount;
                m.dummy.hurtTime = host.hurtTime;
                if (m.lastTick != host.tickCount) {
                    m.lastTick = host.tickCount;
                    m.dummy.walkAnimation.update(host.walkAnimation.speed(), 1f);
                }
            }
            m.renderer.render(m.dummy, 0f, partial, ps, buffers, light);
        } catch (Throwable t) {
            m.brokenLive = true;
            CharMobMaker.LOG.warn("[MobMaker] Whole mob {} can't be drawn: {}", m.id, t.toString());
        }
    }

    // Turns a whole mob's bones into separate parts, in place, so they can be swapped and moved one by one.
    public static List<Part> asParts(Model m) {
        List<Part> out = new ArrayList<>();
        for (Bone b : m.tops) addParts(m, b, "", out);
        return out;
    }

    private static void addParts(Model m, Bone b, String parent, List<Part> out) {
        Part p = new Part();
        p.id = "tmp" + out.size();
        p.kind = Part.Kind.MOB;
        p.name = b.name;
        p.parent = parent;
        p.mob = m.id.toString();
        p.bone = b.path;
        p.solo = true;
        p.pos = b.pos.clone();
        p.rot = b.rot.clone();
        out.add(p);
        for (Bone kid : b.kids) addParts(m, kid, p.id, out);
    }
}
