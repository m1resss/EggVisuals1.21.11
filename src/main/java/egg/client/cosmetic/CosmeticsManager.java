package egg.client.cosmetic;

import egg.client.module.cosmetic.CosmeticsModule;
import egg.client.util.ColorUtil;
import net.minecraft.client.render.model.UnbakedGeometry;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;

import egg.client.mixin.ItemRenderStateAccessor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.CubeFace;
import net.minecraft.client.render.model.json.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.gui.DrawContext;
import egg.client.render.IconRenderer;
import egg.client.mixin.DrawContextAccessor;
import org.joml.Matrix3x2f;
import net.minecraft.util.math.Direction;
import org.joml.Quaternionf;
public final class CosmeticsManager  {
    public static String previewOverrideSword = null;
    private static final CosmeticsManager INSTANCE = new CosmeticsManager();

    private final Map<String, Mesh> meshes = new HashMap<>();
    private final Map<String, AnimatedMeshEntry> animatedMeshes = new HashMap<>();
    private final Map<String, Asset> assets = new HashMap<>();
    private final Map<String, List<Asset>> categoryAssets = new LinkedHashMap<>();
    private final List<SwordEntry> swords = new ArrayList<>();
    private final Map<String, ParsedSwordModel> swordModelCache = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> animationJson = new HashMap<>();
    private boolean indexed = false;

    private CosmeticsManager() {}

    public static CosmeticsManager getInstance() {
        return INSTANCE;
    }

    public synchronized void ensureIndex() {
        if (this.indexed) return;
        this.indexed = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.getResourceManager() == null) return;

        // 1. Load Cosmetics Catalog
        Identifier catId = Identifier.of("egg", "cosmetics/catalog.json");
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(catId);
            if (res.isPresent()) {
                try (InputStreamReader isr = new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(isr).getAsJsonObject();
                    JsonArray items = root.getAsJsonArray("items");
                    for (JsonElement el : items) {
                        JsonObject obj = el.getAsJsonObject();
                        String id = str(obj, "id", "");
                        String cat = str(obj, "category", "").toLowerCase(Locale.ROOT);
                        String path = str(obj, "path", cat + "/" + id);
                        String displayName = str(obj, "display_name_en", str(obj, "display_name", id));
                        displayName = cleanName(displayName, id);

                        if (id.isEmpty() || path.isEmpty()) continue;

                        Identifier geoId = Identifier.of("egg", "cosmetics/" + path + "/geometry.json");
                        Identifier animId = Identifier.of("egg", "cosmetics/" + path + "/animation.json");
                        Identifier texId = Identifier.of("egg", "cosmetics/" + path + "/texture.png");
                        Identifier metaId = Identifier.of("egg", "cosmetics/" + path + "/meta.json");

                        Asset asset = new Asset(id, cat, path, displayName, geoId, animId, texId, loadMetadata(metaId));
                        this.assets.put(id.toLowerCase(Locale.ROOT), asset);
                        this.categoryAssets.computeIfAbsent(cat, k -> new ArrayList<>()).add(asset);
                    }
                }
            }
        } catch (Exception ignored) {}

        // 2. Load Swords Catalog
        Identifier swordCatId = Identifier.of("egg", "item_replacer/sword/catalog.json");
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(swordCatId);
            if (res.isPresent()) {
                String jsonStr = new String(res.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (jsonStr.startsWith("\uFEFF")) {
                    jsonStr = jsonStr.substring(1);
                }
                JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
                if (root.has("items") && root.get("items").isJsonArray()) {
                    JsonArray items = root.getAsJsonArray("items");
                    for (JsonElement el : items) {
                        JsonObject obj = el.getAsJsonObject();
                        String name = str(obj, "name", "");
                        String model = str(obj, "model", "");
                        String tex = str(obj, "texture", "");
                        if (tex.startsWith("item/")) {
                            tex = tex.substring(5);
                        }
                        if (!name.isEmpty() && !tex.isEmpty()) {
                            Identifier texRes = Identifier.of("egg", "textures/item_replacer/sword/" + tex + ".png");
                            this.swords.add(new SwordEntry(name, model, texRes));
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Cosmetics] Failed to load swords catalog: " + e.getMessage());
        }
    }

    public List<Asset> getCategoryAssets(String category, String query) {
        ensureIndex();
        List<Asset> list = this.categoryAssets.getOrDefault(category.toLowerCase(Locale.ROOT), Collections.emptyList());
        if (query == null || query.isBlank()) {
            return list;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Asset> filtered = new ArrayList<>();
        for (Asset a : list) {
            if (a.displayName.toLowerCase(Locale.ROOT).contains(q) || a.id.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(a);
            }
        }
        return filtered;
    }

    public List<SwordEntry> getSwords(String query) {
        ensureIndex();
        if (query == null || query.isBlank()) {
            return this.swords;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<SwordEntry> filtered = new ArrayList<>();
        for (SwordEntry s : this.swords) {
            if (s.name.toLowerCase(Locale.ROOT).contains(q)) {
                filtered.add(s);
            }
        }
        return filtered;
    }

    public Asset getAsset(String id) {
        if (id == null) return null;
        ensureIndex();
        return this.assets.get(id.toLowerCase(Locale.ROOT));
    }

    public SwordEntry getSwordEntry(String name) {
        if (name == null || name.isEmpty()) return null;
        ensureIndex();
        for (SwordEntry s : this.swords) {
            if (s.name.equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    public SwordEntry getSelectedSwordEntry() {
        if (previewOverrideSword != null && !previewOverrideSword.isEmpty()) {
            return getSwordEntry(previewOverrideSword);
        }
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null || !mod.isEnabled()) return null;
        String selName = mod.selectedSword();
        if (selName == null || selName.isEmpty()) return null;
        return getSwordEntry(selName);
    }

    public ParsedSwordModel getSwordModel(SwordEntry sword) {
        if (sword == null) return null;
        ParsedSwordModel cached = this.swordModelCache.get(sword.model());
        if (cached != null) return cached;

        Identifier modelId = Identifier.of("egg", "item_replacer/sword/models/" + sword.model() + ".json");
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.getResourceManager() == null) return null;
            Optional<Resource> res = mc.getResourceManager().getResource(modelId);
            if (res.isPresent()) {
                String jsonStr = new String(res.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (jsonStr.startsWith("\uFEFF")) {
                    jsonStr = jsonStr.substring(1);
                }
                JsonObject root = JsonParser.parseString(jsonStr).getAsJsonObject();
                Map<String, Identifier> texMap = new HashMap<>();
                if (root.has("textures") && root.get("textures").isJsonObject()) {
                    JsonObject texturesObj = root.getAsJsonObject("textures");
                    for (Map.Entry<String, JsonElement> e : texturesObj.entrySet()) {
                        String k = e.getKey();
                        String v = e.getValue().getAsString();
                        if (v.startsWith("item/")) {
                            v = v.substring(5);
                        }
                        Identifier texRes = Identifier.of("egg", "textures/item_replacer/sword/" + v + ".png");
                        texMap.put(k, texRes);
                        texMap.put("#" + k, texRes);
                    }
                }

                JsonUnbakedModel unbaked = JsonUnbakedModel.deserialize(new java.io.StringReader(jsonStr));
                List<ModelElement> elements = Collections.emptyList();
                if (unbaked.geometry() instanceof UnbakedGeometry geom) {
                    elements = geom.elements();
                }
                ParsedSwordModel parsed = new ParsedSwordModel(elements, unbaked.transformations(), texMap);
                this.swordModelCache.put(sword.model(), parsed);
                return parsed;
            }
        } catch (Exception e) {
            System.err.println("[Cosmetics] Failed to load sword model " + sword.model() + ": " + e.getMessage());
        }
        return null;
    }

    public boolean applySwordRenderState(ItemRenderState renderState, ItemStack stack, LivingEntity entity) {
        return applySwordRenderState(renderState, stack, entity, null);
    }

    public boolean applySwordRenderState(ItemRenderState renderState, ItemStack stack, LivingEntity entity, ItemDisplayContext displayContext) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return false;

        SwordEntry sword = null;
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod != null && (mod.isEnabled() || previewOverrideSword != null)) {
            if (previewOverrideSword != null || entity == null || mc.player == null || entity == mc.player || entity.getId() == mc.player.getId() || entity.getId() <= 0) {
                sword = getSelectedSwordEntry();
            }
        }

        if (sword == null) {
            return false;
        }
        ParsedSwordModel model = getSwordModel(sword);
        if (model == null) {
            return false;
        }

        ItemDisplayContext currentCtx = null;
        if (renderState instanceof ItemRenderStateAccessor accessor) {
            currentCtx = accessor.getDisplayContext();
        }

        ItemDisplayContext activeCtx = (displayContext != null && displayContext != ItemDisplayContext.NONE)
                ? displayContext
                : (currentCtx != null && currentCtx != ItemDisplayContext.NONE
                    ? currentCtx
                    : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);

        renderState.clear();
        if (renderState instanceof ItemRenderStateAccessor accessor) {
            accessor.setDisplayContext(activeCtx);
        }

        ItemRenderState.LayerRenderState layer = renderState.newLayer();
        layer.setSpecialModel(SwordSpecialModelRenderer.INSTANCE, sword);
        if (stack != null && stack.hasGlint()) {
            layer.setGlint(ItemRenderState.Glint.STANDARD);
        }
        return true;
    }

    public void renderSwordModel(SwordEntry sword, ItemDisplayContext mode, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, int overlay, boolean glint) {
        ParsedSwordModel parsed = getSwordModel(sword);
        if (parsed == null || parsed.elements.isEmpty()) return;

        ItemDisplayContext activeMode = (mode != null && mode != ItemDisplayContext.NONE) ? mode : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;

        matrices.push();
        if (parsed.transformation != null) {
            Transformation tf = parsed.transformation.getTransformation(activeMode);
            if (tf != null) {
                tf.apply(activeMode.isLeftHand(), matrices.peek());
            } else {
                matrices.translate(-0.5f, -0.5f, -0.5f);
            }
        } else {
            matrices.translate(-0.5f, -0.5f, -0.5f);
        }

        for (ModelElement el : parsed.elements) {
            if (el.from() == null || el.to() == null || el.faces() == null || el.faces().isEmpty()) continue;

            matrices.push();
            if (el.rotation() != null) {
                var rot = el.rotation();
                float ox = rot.origin().x() * 0.0625f;
                float oy = rot.origin().y() * 0.0625f;
                float oz = rot.origin().z() * 0.0625f;
                matrices.translate(ox, oy, oz);
                matrices.multiplyPositionMatrix(rot.transform());
                matrices.translate(-ox, -oy, -oz);
            }

            float x0 = el.from().x() * 0.0625f;
            float y0 = el.from().y() * 0.0625f;
            float z0 = el.from().z() * 0.0625f;
            float x1 = el.to().x() * 0.0625f;
            float y1 = el.to().y() * 0.0625f;
            float z1 = el.to().z() * 0.0625f;

            for (Map.Entry<Direction, ModelElementFace> faceEntry : el.faces().entrySet()) {
                Direction dir = faceEntry.getKey();
                ModelElementFace face = faceEntry.getValue();
                if (face == null) continue;

                String texKey = face.textureId();
                Identifier faceTex = parsed.textures.getOrDefault(texKey, sword.texture());
                RenderLayer renderLayer = RenderLayers.entityCutoutNoCull(faceTex);

                float nx = dir.getOffsetX();
                float ny = dir.getOffsetY();
                float nz = dir.getOffsetZ();

                queue.submitCustom(matrices, renderLayer, (entry, vc) -> {
                    float[][] quad;
                    switch (dir) {
                        case NORTH -> quad = new float[][]{{x1, y1, z0}, {x0, y1, z0}, {x0, y0, z0}, {x1, y0, z0}};
                        case SOUTH -> quad = new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y0, z1}, {x0, y0, z1}};
                        case WEST  -> quad = new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}, {x0, y0, z0}};
                        case EAST  -> quad = new float[][]{{x1, y1, z1}, {x1, y1, z0}, {x1, y0, z0}, {x1, y0, z1}};
                        case UP    -> quad = new float[][]{{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
                        case DOWN  -> quad = new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y0, z0}, {x0, y0, z0}};
                        default    -> quad = new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}};
                    }

                    for (int i = 0; i < 4; i++) {
                        float u = ModelElementFace.getUValue(face.uvs(), face.rotation(), i) / 16.0f;
                        float v = ModelElementFace.getVValue(face.uvs(), face.rotation(), i) / 16.0f;
                        vc.vertex(entry, quad[i][0], quad[i][1], quad[i][2])
                          .color(255, 255, 255, 255)
                          .texture(u, v)
                          .overlay(overlay)
                          .light(light)
                          .normal(entry, nx, ny, nz);
                    }
                });
            }

            matrices.pop();
        }
        matrices.pop();
    }

    // --- In-Game Attached Player Rendering ---

    public void renderAttached(MatrixStack matrices, OrderedRenderCommandQueue queue, PlayerEntityModel model, int light) {
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null || !mod.isEnabled()) return;

        ensureIndex();

        String wingsId = CosmeticsRenderer.previewOverrideCat != null
                ? ("wings".equals(CosmeticsRenderer.previewOverrideCat) ? CosmeticsRenderer.previewOverrideId : "")
                : mod.getSelectedWingId();

        String backpackId = CosmeticsRenderer.previewOverrideCat != null
                ? ("backpacks".equals(CosmeticsRenderer.previewOverrideCat) ? CosmeticsRenderer.previewOverrideId : "")
                : mod.getSelectedBackpackId();

        String hatId = CosmeticsRenderer.previewOverrideCat != null
                ? ("hats".equals(CosmeticsRenderer.previewOverrideCat) ? CosmeticsRenderer.previewOverrideId : "")
                : mod.getSelectedHatId();

        String maskId = CosmeticsRenderer.previewOverrideCat != null
                ? ("masks".equals(CosmeticsRenderer.previewOverrideCat) ? CosmeticsRenderer.previewOverrideId : "")
                : mod.getSelectedMaskId();

        String petId = CosmeticsRenderer.previewOverrideCat != null
                ? ("pets".equals(CosmeticsRenderer.previewOverrideCat) ? CosmeticsRenderer.previewOverrideId : "")
                : mod.getSelectedPetId();

        renderItemQueue(matrices, queue, model, light, wingsId, "wings");
        renderItemQueue(matrices, queue, model, light, backpackId, "backpacks");
        renderItemQueue(matrices, queue, model, light, hatId, "hats");
        renderItemQueue(matrices, queue, model, light, maskId, "masks");
        renderItemQueue(matrices, queue, model, light, petId, "pets");

        SwordEntry sword = getSelectedSwordEntry();
        if (sword != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean isHoldingSword = mc.player != null && !mc.player.getMainHandStack().isEmpty() && isSword(mc.player.getMainHandStack());
            if (!isHoldingSword) {
                renderSwordInHand(matrices, queue, model, light, sword);
            }
        }
    }

    public void renderAttachedForPlayer(MatrixStack matrices, OrderedRenderCommandQueue queue, PlayerEntityModel model, int light, Map<String, String> cosmetics) {
        if (cosmetics == null || cosmetics.isEmpty()) return;

        renderItemQueue(matrices, queue, model, light, cosmetics.get("wings"), "wings");
        renderItemQueue(matrices, queue, model, light, cosmetics.get("backpacks"), "backpacks");
        renderItemQueue(matrices, queue, model, light, cosmetics.get("hats"), "hats");
        renderItemQueue(matrices, queue, model, light, cosmetics.get("masks"), "masks");
        renderItemQueue(matrices, queue, model, light, cosmetics.get("pets"), "pets");
    }

    public static boolean isSword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.isIn(net.minecraft.registry.tag.ItemTags.SWORDS)) return true;
        String path = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
        return path.endsWith("_sword") || path.contains("sword");
    }

    public void renderSwordInHand(MatrixStack matrices, OrderedRenderCommandQueue queue, PlayerEntityModel model, int light, SwordEntry sword) {
        if (sword == null) return;
        matrices.push();
        model.rightArm.applyTransform(matrices);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0f));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));
        matrices.translate(0.0f, 0.125f, -0.625f);
        renderSwordModel(sword, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, matrices, queue, light, net.minecraft.client.render.OverlayTexture.DEFAULT_UV, false);
        matrices.pop();
    }

    private void renderItemQueue(MatrixStack matrices, OrderedRenderCommandQueue queue, PlayerEntityModel model, int light, String itemId, String cat) {
        if (itemId == null || itemId.isEmpty()) return;
        Asset asset = this.assets.get(itemId.toLowerCase(Locale.ROOT));
        if (asset == null) return;

        Mesh mesh = getAnimatedMesh(asset);
        if (mesh == null || mesh.vertices.isEmpty()) return;

        matrices.push();

        if ("hats".equals(cat) || "masks".equals(cat)) {
            model.head.applyTransform(matrices);
        } else {
            model.body.applyTransform(matrices);
        }

        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));

        if ("hats".equals(cat)) {
            matrices.translate(0.0f, -0.125f, 0.0f);
        }

        Metadata meta = asset.metadata;
        float s = meta.scale;
        matrices.scale(s, s, s);

        float ox = meta.x;
        float oy = meta.y;
        float oz = meta.z;
        if (!meta.rawOffset) {
            ox += 0.5f;
            oy += 0.51f;
            oz += 0.5f;
        }
        if (meta.rawOffset && mesh.playerSpaceMesh && ("hats".equals(cat) || "pets".equals(cat))) {
            ox -= mesh.rawCx;
            oy -= mesh.rawCy;
            oz -= mesh.rawCz;
        }
        matrices.translate(ox, oy, oz);

        queue.submitCustom(matrices, RenderLayers.entityCutoutNoCull(asset.texture), (entry, vc) -> {
            int count = mesh.vertices.size();
            for (int i = 0; i + 5 < count; i += 6) {
                V v0 = mesh.vertices.get(i);
                V v1 = mesh.vertices.get(i + 1);
                V v2 = mesh.vertices.get(i + 2);
                V v3 = mesh.vertices.get(i + 5);

                float dy1 = v1.y - v0.y;
                float dz2 = v2.z - v0.z;
                float dz1 = v1.z - v0.z;
                float dy2 = v2.y - v0.y;
                float nx = dy1 * dz2 - dz1 * dy2;

                float dx2 = v2.x - v0.x;
                float dx1 = v1.x - v0.x;
                float ny = dz1 * dx2 - dx1 * dz2;
                float nz = dx1 * dy2 - dy1 * dx2;

                float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                if (len > 1.0E-6f) {
                    nx /= len; ny /= len; nz /= len;
                } else {
                    nx = 0.0f; ny = 1.0f; nz = 0.0f;
                }

                putVertex(vc, entry, v0, light, nx, ny, nz);
                putVertex(vc, entry, v1, light, nx, ny, nz);
                putVertex(vc, entry, v2, light, nx, ny, nz);
                putVertex(vc, entry, v3, light, nx, ny, nz);
            }
        });

        matrices.pop();
    }

    public void renderAttached(MatrixStack matrices, VertexConsumerProvider consumers, PlayerEntityModel model, int light) {
        CosmeticsModule mod = CosmeticsModule.getInstance();
        if (mod == null || !mod.isEnabled()) return;

        ensureIndex();

        renderItem(matrices, consumers, model, light, mod.getSelectedWingId(), "wings");
        renderItem(matrices, consumers, model, light, mod.getSelectedBackpackId(), "backpacks");
        renderItem(matrices, consumers, model, light, mod.getSelectedHatId(), "hats");
        renderItem(matrices, consumers, model, light, mod.getSelectedMaskId(), "masks");
        renderItem(matrices, consumers, model, light, mod.getSelectedPetId(), "pets");
    }



    private void renderItem(MatrixStack matrices, VertexConsumerProvider consumers, PlayerEntityModel model, int light, String itemId, String cat) {
        if (itemId == null || itemId.isEmpty()) return;
        Asset asset = this.assets.get(itemId.toLowerCase(Locale.ROOT));
        if (asset == null) return;

        Mesh mesh = getAnimatedMesh(asset);
        if (mesh == null || mesh.vertices.isEmpty()) return;

        matrices.push();

        if ("hats".equals(cat) || "masks".equals(cat)) {
            model.head.applyTransform(matrices);
        } else {
            model.body.applyTransform(matrices);
        }

        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));

        if ("hats".equals(cat)) {
            matrices.translate(0.0f, -0.125f, 0.0f);
        }

        Metadata meta = asset.metadata;
        float s = meta.scale;
        matrices.scale(s, s, s);

        float ox = meta.x;
        float oy = meta.y;
        float oz = meta.z;
        if (!meta.rawOffset) {
            ox += 0.5f;
            oy += 0.51f;
            oz += 0.5f;
        }
        if (meta.rawOffset && mesh.playerSpaceMesh && ("hats".equals(cat) || "pets".equals(cat))) {
            ox -= mesh.rawCx;
            oy -= mesh.rawCy;
            oz -= mesh.rawCz;
        }
        matrices.translate(ox, oy, oz);

        VertexConsumer vc = consumers.getBuffer(RenderLayers.entityCutoutNoCull(asset.texture));
        MatrixStack.Entry entry = matrices.peek();

        int count = mesh.vertices.size();
        for (int i = 0; i + 5 < count; i += 6) {
            V v0 = mesh.vertices.get(i);
            V v1 = mesh.vertices.get(i + 1);
            V v2 = mesh.vertices.get(i + 2);
            V v3 = mesh.vertices.get(i + 5);

            float dy1 = v1.y - v0.y;
            float dz2 = v2.z - v0.z;
            float dz1 = v1.z - v0.z;
            float dy2 = v2.y - v0.y;
            float nx = dy1 * dz2 - dz1 * dy2;

            float dx2 = v2.x - v0.x;
            float dx1 = v1.x - v0.x;
            float ny = dz1 * dx2 - dx1 * dz2;
            float nz = dx1 * dy2 - dy1 * dx2;

            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len > 1.0E-6f) {
                nx /= len; ny /= len; nz /= len;
            } else {
                nx = 0.0f; ny = 1.0f; nz = 0.0f;
            }

            putVertex(vc, entry, v0, light, nx, ny, nz);
            putVertex(vc, entry, v1, light, nx, ny, nz);
            putVertex(vc, entry, v2, light, nx, ny, nz);
            putVertex(vc, entry, v3, light, nx, ny, nz);
        }

        matrices.pop();
    }

    private void putVertex(VertexConsumer vc, MatrixStack.Entry entry, V v, int light, float nx, float ny, float nz) {
        vc.vertex(entry, v.x, v.y, v.z)
          .color(255, 255, 255, 255)
          .texture(v.u, v.v)
          .overlay(OverlayTexture.DEFAULT_UV)
          .light(light)
          .normal(entry, nx, ny, nz);
    }

    // --- GUI Preview Rendering ---

    public void renderGuiPreview(DrawContext context, String itemId, float x, float y, float size, float alpha) {
        renderGuiPreview(context, itemId, x, y, size, alpha, null);
    }

    public void renderGuiPreview(DrawContext context, String itemId, float x, float y, float size, float alpha, net.minecraft.client.gui.ScreenRect scissorArea) {
        if (itemId == null || itemId.isBlank() || size <= 0.0f || alpha <= 0.0f) return;
        ensureIndex();

        Asset asset = this.assets.get(itemId.toLowerCase(Locale.ROOT));
        if (asset == null || asset.texture == null) return;

        Mesh mesh = getAnimatedMesh(asset);
        if (mesh == null || mesh.vertices.isEmpty()) return;

        float yawRad = (float) Math.toRadians(asset.metadata.previewYaw);
        float pitchRad = (float) Math.toRadians(asset.metadata.previewPitch);
        float cosYaw = (float) Math.cos(yawRad);
        float sinYaw = (float) Math.sin(yawRad);
        float cosPitch = (float) Math.cos(pitchRad);
        float sinPitch = (float) Math.sin(pitchRad);

        float pScale = Math.max(0.78f, Math.min(1.18f, asset.metadata.previewScale));
        float renderSize = size * 0.78f * pScale;
        float centerX = x + size * 0.5f;
        float centerY = y + size * (0.52f - asset.metadata.previewY);

        int vCount = mesh.vertices.size();
        List<PreviewFace> faces = new ArrayList<>(vCount / 6);
        for (int i = 0; i + 5 < vCount; i += 6) {
            PreviewVertex a = previewVertex(mesh.vertices.get(i), mesh, centerX, centerY, renderSize, cosYaw, sinYaw, cosPitch, sinPitch);
            PreviewVertex b = previewVertex(mesh.vertices.get(i + 1), mesh, centerX, centerY, renderSize, cosYaw, sinYaw, cosPitch, sinPitch);
            PreviewVertex c = previewVertex(mesh.vertices.get(i + 2), mesh, centerX, centerY, renderSize, cosYaw, sinYaw, cosPitch, sinPitch);
            PreviewVertex d = previewVertex(mesh.vertices.get(i + 5), mesh, centerX, centerY, renderSize, cosYaw, sinYaw, cosPitch, sinPitch);
            float avgDepth = (a.depth + b.depth + c.depth + d.depth) * 0.25f;
            faces.add(new PreviewFace(a, b, c, d, avgDepth));
        }

        faces.sort(Comparator.comparingDouble(f -> f.depth));

        float[] quadVertices = new float[faces.size() * 16];
        int idx = 0;
        for (PreviewFace f : faces) {
            quadVertices[idx++] = f.a.x; quadVertices[idx++] = f.a.y; quadVertices[idx++] = f.a.u; quadVertices[idx++] = f.a.v;
            quadVertices[idx++] = f.b.x; quadVertices[idx++] = f.b.y; quadVertices[idx++] = f.b.u; quadVertices[idx++] = f.b.v;
            quadVertices[idx++] = f.c.x; quadVertices[idx++] = f.c.y; quadVertices[idx++] = f.c.u; quadVertices[idx++] = f.c.v;
            quadVertices[idx++] = f.d.x; quadVertices[idx++] = f.d.y; quadVertices[idx++] = f.d.u; quadVertices[idx++] = f.d.v;
        }

        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
        int color = (a << 24) | 0x00FFFFFF;

        Matrix3x2f pose = new Matrix3x2f(context.getMatrices());
        CosmeticMeshGuiRenderState guiState = new CosmeticMeshGuiRenderState(
                asset.texture, pose, quadVertices, color, x, y, size, size, scissorArea
        );

        ((DrawContextAccessor) context).getState().addSimpleElement(guiState);
    }

    private static PreviewVertex previewVertex(V v, Mesh mesh, float cx, float cy, float renderSize,
                                                float cosYaw, float sinYaw, float cosPitch, float sinPitch) {
        float nx = (v.x - mesh.cx) / mesh.span;
        float ny = (v.y - mesh.cy) / mesh.span;
        float nz = (v.z - mesh.cz) / mesh.span;

        float rotX = nx * cosYaw + nz * sinYaw;
        float rotZ1 = -nx * sinYaw + nz * cosYaw;

        float rotY = ny * cosPitch - rotZ1 * sinPitch;
        float depth = ny * sinPitch + rotZ1 * cosPitch;

        float sx = cx + rotX * renderSize;
        float sy = cy - rotY * renderSize;

        return new PreviewVertex(sx, sy, v.u, v.v, depth);
    }

    // --- Bedrock Animation & Mesh Engine ---

    private Mesh getAnimatedMesh(Asset asset) {
        double timeSec = (double) System.nanoTime() * 1.0E-9;
        long frameKey = (long) Math.floor(timeSec * 120.0);

        AnimatedMeshEntry entry = this.animatedMeshes.get(asset.id);
        if (entry != null && entry.frame == frameKey) {
            return entry.mesh;
        }

        Map<String, AnimationPose> poses = loadAnimationPose(asset.animation, timeSec);
        Mesh mesh = poses.isEmpty() ? loadMesh(asset) : loadMesh(asset, poses);

        this.animatedMeshes.put(asset.id, new AnimatedMeshEntry(frameKey, mesh));
        return mesh;
    }

    private Map<String, AnimationPose> loadAnimationPose(Identifier animId, double timeSec) {
        Map<String, AnimationPose> poses = new HashMap<>();
        try {
            JsonObject root = getAnimationJson(animId);
            if (root == null || !root.has("animations")) return poses;
            JsonObject anims = root.getAsJsonObject("animations");
            if (anims.isEmpty()) return poses;

            JsonObject firstAnim = anims.entrySet().iterator().next().getValue().getAsJsonObject();
            double len = firstAnim.has("animation_length") && !firstAnim.get("animation_length").isJsonNull()
                    ? Math.max(0.001, firstAnim.get("animation_length").getAsDouble())
                    : 1.0;
            boolean loop = !firstAnim.has("loop") || firstAnim.get("loop").getAsBoolean();
            double cur = loop ? timeSec % len : Math.min(timeSec, len);

            JsonObject bones = firstAnim.getAsJsonObject("bones");
            if (bones == null) return poses;

            for (Map.Entry<String, JsonElement> e : bones.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject b = e.getValue().getAsJsonObject();
                Vector3f rot = sampleAnimationTrack(b.get("rotation"), cur, new Vector3f(), loop, len);
                Vector3f pos = sampleAnimationTrack(b.get("position"), cur, new Vector3f(), loop, len);
                Vector3f scl = sampleAnimationTrack(b.get("scale"), cur, new Vector3f(1.0f, 1.0f, 1.0f), loop, len);
                poses.put(e.getKey(), new AnimationPose(rot, pos, scl));
            }
        } catch (Exception ignored) {}
        return poses;
    }

    private static Vector3f sampleAnimationTrack(JsonElement el, double time, Vector3f def, boolean loop, double animLen) {
        if (el == null || el.isJsonNull()) return new Vector3f(def);

        if (!el.isJsonObject()) {
            return keyframeVector(el, def);
        }

        JsonObject obj = el.getAsJsonObject();
        if (obj.has("vector") || obj.has("pre") || obj.has("post")) {
            return keyframeVector(obj, def);
        }

        List<Map.Entry<Double, JsonElement>> frames = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            try {
                frames.add(Map.entry(Double.parseDouble(entry.getKey()), entry.getValue()));
            } catch (NumberFormatException ignored) {}
        }

        if (frames.isEmpty()) {
            return new Vector3f(def);
        }

        frames.sort(Comparator.comparingDouble(Map.Entry::getKey));

        if (frames.size() == 1) {
            return keyframeVector(frames.get(0).getValue(), def);
        }

        if (loop && animLen > 0.0 && time >= 0.0) {
            double min = Math.min(0.12, animLen * 0.1);
            double wrapThreshold = animLen - min;
            if (min > 0.0 && time > wrapThreshold) {
                Vector3f vWrap = sampleSortedFrames(frames, wrapThreshold, def);
                Vector3f vStart = sampleSortedFrames(frames, 0.0, def);
                float blend = smoothstep((float) ((time - wrapThreshold) / min));
                return vWrap.lerp(vStart, blend, new Vector3f());
            }
        }

        return sampleSortedFrames(frames, time, def);
    }

    private static Vector3f sampleSortedFrames(List<Map.Entry<Double, JsonElement>> frames, double time, Vector3f def) {
        Map.Entry<Double, JsonElement> first = frames.get(0);
        Map.Entry<Double, JsonElement> last = frames.get(frames.size() - 1);

        if (time <= first.getKey()) {
            return keyframeVector(first.getValue(), def);
        }
        if (time >= last.getKey()) {
            return keyframeVector(last.getValue(), def);
        }

        Map.Entry<Double, JsonElement> prev = first;
        Map.Entry<Double, JsonElement> next = last;

        for (int i = 1; i < frames.size(); i++) {
            Map.Entry<Double, JsonElement> cur = frames.get(i);
            if (time <= cur.getKey()) {
                next = cur;
                prev = frames.get(i - 1);
                break;
            }
        }

        double span = Math.max(1.0E-9, next.getKey() - prev.getKey());
        float progress = (float) ((time - prev.getKey()) / span);

        if (hasEasing(next.getValue())) {
            progress = applyEasing(next.getValue(), progress);
        } else if (span >= 0.12) {
            progress = smoothstep(progress);
        }

        Vector3f v1 = keyframeVectorForStart(prev.getValue(), def);
        Vector3f v2 = keyframeVectorForEnd(next.getValue(), def);
        return v1.lerp(v2, progress, new Vector3f());
    }

    private static Vector3f keyframeVector(JsonElement el, Vector3f def) {
        if (el == null || el.isJsonNull()) return new Vector3f(def);

        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() == 1) {
                float v = arr.get(0).getAsFloat();
                return new Vector3f(v, v, v);
            }
            if (arr.size() >= 3) {
                return new Vector3f(arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat());
            }
            return new Vector3f(def);
        }

        if (el.isJsonPrimitive()) {
            try {
                float v = el.getAsFloat();
                return new Vector3f(v, v, v);
            } catch (Exception e) {
                return new Vector3f(def);
            }
        }

        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("vector")) {
                return keyframeVector(obj.get("vector"), def);
            }
            if (obj.has("post")) {
                return keyframeVector(obj.get("post"), def);
            }
            if (obj.has("pre")) {
                return keyframeVector(obj.get("pre"), def);
            }
        }

        return new Vector3f(def);
    }

    private static Vector3f keyframeVectorForStart(JsonElement el, Vector3f def) {
        if (el != null && el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("post")) {
                return keyframeVector(obj.get("post"), def);
            }
        }
        return keyframeVector(el, def);
    }

    private static Vector3f keyframeVectorForEnd(JsonElement el, Vector3f def) {
        if (el != null && el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("pre")) {
                return keyframeVector(obj.get("pre"), def);
            }
        }
        return keyframeVector(el, def);
    }

    private static boolean hasEasing(JsonElement el) {
        if (el != null && el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            return obj.has("easing") && !obj.get("easing").isJsonNull();
        }
        return false;
    }

    private static float applyEasing(JsonElement el, float t) {
        if (el != null && el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("easing") && !obj.get("easing").isJsonNull()) {
                String ease = obj.get("easing").getAsString().toLowerCase(Locale.ROOT);
                return switch (ease) {
                    case "easeinoutquad" -> t < 0.5f ? 2.0f * t * t : 1.0f - (float) Math.pow(-2.0f * t + 2.0f, 2.0) * 0.5f;
                    case "easeinquad" -> t * t;
                    case "easeoutquad" -> 1.0f - (1.0f - t) * (1.0f - t);
                    default -> t;
                };
            }
        }
        return t;
    }

    private static float smoothstep(float x) {
        x = Math.max(0.0f, Math.min(1.0f, x));
        return x * x * (3.0f - 2.0f * x);
    }

    private JsonObject getAnimationJson(Identifier id) {
        String key = id.toString();
        if (this.animationJson.containsKey(key)) return this.animationJson.get(key);
        try {
            Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (res.isPresent()) {
                try (InputStreamReader isr = new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(isr).getAsJsonObject();
                    this.animationJson.put(key, obj);
                    return obj;
                }
            }
        } catch (Exception ignored) {}
        this.animationJson.put(key, null);
        return null;
    }

    // --- Mesh Loader & Geometry ---

    private Mesh loadMesh(Asset asset) {
        return loadMesh(asset, Collections.emptyMap());
    }

    private Mesh loadMesh(Asset asset, Map<String, AnimationPose> poses) {
        try {
            Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(asset.geometry);
            if (res.isEmpty()) return Mesh.empty();

            try (InputStreamReader isr = new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8)) {
                JsonObject obj = JsonParser.parseReader(isr).getAsJsonObject();
                JsonArray geoms = obj.getAsJsonArray("minecraft:geometry");
                if (geoms == null || geoms.isEmpty()) return Mesh.empty();

                JsonObject geom = geoms.get(0).getAsJsonObject();
                JsonObject desc = geom.getAsJsonObject("description");
                float tw = desc != null && desc.has("texture_width") ? desc.get("texture_width").getAsFloat() : 64.0f;
                float th = desc != null && desc.has("texture_height") ? desc.get("texture_height").getAsFloat() : 64.0f;

                JsonArray bones = geom.getAsJsonArray("bones");
                if (bones == null) return Mesh.empty();

                Map<String, Bone> boneMap = new LinkedHashMap<>();
                for (JsonElement el : bones) {
                    JsonObject b = el.getAsJsonObject();
                    String name = str(b, "name", UUID.randomUUID().toString());
                    String parent = str(b, "parent", "");
                    Vector3f pivot = vec(b.getAsJsonArray("pivot"));
                    Vector3f rot = vec(b.getAsJsonArray("rotation"));
                    boneMap.put(name, new Bone(name, parent, pivot, rot, b.getAsJsonArray("cubes")));
                }

                float[] bounds = rawMeshBounds(bones);
                List<V> vertices = new ArrayList<>();
                Map<String, Matrix4f> transforms = new HashMap<>();

                for (Bone bone : boneMap.values()) {
                    Matrix4f m = computeBoneMatrix(bone, boneMap, transforms, poses);
                    if (bone.cubes != null) {
                        for (JsonElement cubeEl : bone.cubes) {
                            bakeCube(cubeEl.getAsJsonObject(), m, tw, th, vertices);
                        }
                    }
                }
                return normalizeMesh(vertices, bounds);
            }
        } catch (Exception e) {
            return Mesh.empty();
        }
    }

    private Matrix4f computeBoneMatrix(Bone bone, Map<String, Bone> map, Map<String, Matrix4f> cache, Map<String, AnimationPose> poses) {
        if (cache.containsKey(bone.name)) return new Matrix4f(cache.get(bone.name));
        Matrix4f m = (!bone.parent.isEmpty() && map.containsKey(bone.parent))
                ? computeBoneMatrix(map.get(bone.parent), map, cache, poses)
                : new Matrix4f();

        AnimationPose pose = poses.getOrDefault(bone.name, AnimationPose.IDENTITY);
        if (pose.position.lengthSquared() > 0.0f) {
            m.translate(-pose.position.x * 0.0625f, pose.position.y * 0.0625f, pose.position.z * 0.0625f);
        }

        float px = -bone.pivot.x * 0.0625f;
        float py = bone.pivot.y * 0.0625f;
        float pz = bone.pivot.z * 0.0625f;
        m.translate(px, py, pz);

        m.rotateZYX((float) Math.toRadians(bone.rotation.z + pose.rotation.z),
                    (float) Math.toRadians(-(bone.rotation.y + pose.rotation.y)),
                    (float) Math.toRadians(-(bone.rotation.x + pose.rotation.x)));

        if (pose.scale.x != 1.0f || pose.scale.y != 1.0f || pose.scale.z != 1.0f) {
            m.scale(pose.scale.x, pose.scale.y, pose.scale.z);
        }

        m.translate(-px, -py, -pz);
        cache.put(bone.name, new Matrix4f(m));
        return m;
    }

    private void bakeCube(JsonObject json, Matrix4f m, float tw, float th, List<V> vs) {
        Vector3f origin = vec(json.getAsJsonArray("origin")).mul(0.0625f);
        Vector3f size = vec(json.getAsJsonArray("size")).mul(0.0625f);
        float inf = json.has("inflate") ? json.get("inflate").getAsFloat() * 0.0625f : 0.0f;

        float x1 = -(origin.x + size.x) - inf;
        float y1 = origin.y - inf;
        float z1 = origin.z - inf;
        float x2 = -origin.x + inf;
        float y2 = origin.y + size.y + inf;
        float z2 = origin.z + size.z + inf;

        Vector3f pivot = json.has("pivot") ? vec(json.getAsJsonArray("pivot")).mul(0.0625f) : new Vector3f();
        pivot.x = -pivot.x;
        Vector3f rot = json.has("rotation") ? vec(json.getAsJsonArray("rotation")) : new Vector3f();
        rot.x = -rot.x;
        rot.y = -rot.y;

        Matrix4f local = new Matrix4f(m);
        if (rot.lengthSquared() > 0.0f) {
            local.translate(pivot);
            local.rotateZYX((float) Math.toRadians(rot.z), (float) Math.toRadians(rot.y), (float) Math.toRadians(rot.x));
            local.translate(-pivot.x, -pivot.y, -pivot.z);
        }

        Face[] faces = cubeUvs(json, tw, th, Math.abs(size.x / 0.0625f), Math.abs(size.y / 0.0625f), Math.abs(size.z / 0.0625f));

        boolean flatX = Math.abs(size.x) < 1.0E-7f;
        boolean flatY = Math.abs(size.y) < 1.0E-7f;
        boolean flatZ = Math.abs(size.z) < 1.0E-7f;
        boolean is3D = !flatX && !flatY && !flatZ;

        // North (0) & South (1)
        if (is3D || flatZ) {
            addFace(vs, local, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, faces[0]);
            addFace(vs, local, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, faces[1]);
        }

        // West (2) & East (3)
        if (is3D || flatX) {
            addFace(vs, local, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, faces[2]);
            addFace(vs, local, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, faces[3]);
        }

        // Up (4) & Down (5)
        if (is3D || flatY) {
            addFace(vs, local, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, faces[4]);
            addFace(vs, local, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, faces[5]);
        }
    }

    private void addFace(List<V> vs, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2,
                         float x3, float y3, float z3, float x4, float y4, float z4, Face f) {
        if (f == null) return;
        Vector3f p1 = m.transformPosition(new Vector3f(x1, y1, z1));
        Vector3f p2 = m.transformPosition(new Vector3f(x2, y2, z2));
        Vector3f p3 = m.transformPosition(new Vector3f(x3, y3, z3));
        Vector3f p4 = m.transformPosition(new Vector3f(x4, y4, z4));

        vs.add(new V(p1.x, p1.y, p1.z, f.u1, f.v2));
        vs.add(new V(p2.x, p2.y, p2.z, f.u2, f.v2));
        vs.add(new V(p3.x, p3.y, p3.z, f.u2, f.v1));
        vs.add(new V(p1.x, p1.y, p1.z, f.u1, f.v2));
        vs.add(new V(p3.x, p3.y, p3.z, f.u2, f.v1));
        vs.add(new V(p4.x, p4.y, p4.z, f.u1, f.v1));
    }

    private Face[] cubeUvs(JsonObject json, float tw, float th, float sx, float sy, float sz) {
        Face[] faces = new Face[6];
        if (json.has("uv") && json.get("uv").isJsonObject()) {
            JsonObject uvObj = json.getAsJsonObject("uv");
            faces[0] = faceUv(uvObj, "north", tw, th);
            faces[1] = faceUv(uvObj, "south", tw, th);
            faces[2] = faceUv(uvObj, "west", tw, th);
            faces[3] = faceUv(uvObj, "east", tw, th);
            faces[4] = faceUv(uvObj, "up", tw, th);
            faces[5] = faceUv(uvObj, "down", tw, th);
            return faces;
        }

        float u = 0.0f, v = 0.0f;
        if (json.has("uv") && json.get("uv").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("uv");
            if (arr.size() >= 2) {
                u = arr.get(0).getAsFloat();
                v = arr.get(1).getAsFloat();
            }
        }

        faces[2] = rect(u + sz + sx, v + sz, sz, sy, tw, th);
        faces[3] = rect(u, v + sz, sz, sy, tw, th);
        faces[0] = rect(u + sz, v + sz, sx, sy, tw, th);
        faces[1] = rect(u + sz + sx + sz, v + sz, sx, sy, tw, th);
        faces[4] = rect(u + sz, v, sx, sz, tw, th);
        faces[5] = rect(u + sz + sx, v + sz, sx, -sz, tw, th);
        return faces;
    }

    private static Face faceUv(JsonObject json, String name, float tw, float th) {
        if (json.has(name) && json.get(name).isJsonObject()) {
            JsonObject fObj = json.getAsJsonObject(name);
            JsonArray uvArr = fObj.getAsJsonArray("uv");
            JsonArray szArr = fObj.getAsJsonArray("uv_size");
            if (uvArr != null && uvArr.size() >= 2) {
                float u = uvArr.get(0).getAsFloat();
                float v = uvArr.get(1).getAsFloat();
                float w = szArr != null && szArr.size() >= 2 ? szArr.get(0).getAsFloat() : 1.0f;
                float h = szArr != null && szArr.size() >= 2 ? szArr.get(1).getAsFloat() : 1.0f;
                return rect(u, v, w, h, tw, th);
            }
        }
        return null;
    }

    private static Face rect(float x, float y, float w, float h, float tw, float th) {
        return new Face(x / tw, y / th, (x + w) / tw, (y + h) / th);
    }

    private static float[] rawMeshBounds(JsonArray bones) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        boolean hasCubes = false;

        for (JsonElement bEl : bones) {
            JsonArray cubes = bEl.getAsJsonObject().getAsJsonArray("cubes");
            if (cubes != null) {
                for (JsonElement cEl : cubes) {
                    JsonObject c = cEl.getAsJsonObject();
                    JsonArray o = c.getAsJsonArray("origin");
                    JsonArray s = c.getAsJsonArray("size");
                    if (o != null && s != null && o.size() >= 3 && s.size() >= 3) {
                        float inf = c.has("inflate") ? c.get("inflate").getAsFloat() : 0.0f;
                        float ox = o.get(0).getAsFloat();
                        float oy = o.get(1).getAsFloat();
                        float oz = o.get(2).getAsFloat();
                        float sx = s.get(0).getAsFloat();
                        float sy = s.get(1).getAsFloat();
                        float sz = s.get(2).getAsFloat();

                        float x1 = -(ox + sx) - inf;
                        float x2 = -ox + inf;
                        float y1 = oy - inf;
                        float y2 = oy + sy + inf;
                        float z1 = oz - inf;
                        float z2 = oz + sz + inf;

                        minX = Math.min(minX, Math.min(x1, x2));
                        maxX = Math.max(maxX, Math.max(x1, x2));
                        minY = Math.min(minY, Math.min(y1, y2));
                        maxY = Math.max(maxY, Math.max(y1, y2));
                        minZ = Math.min(minZ, Math.min(z1, z2));
                        maxZ = Math.max(maxZ, Math.max(z1, z2));
                        hasCubes = true;
                    }
                }
            }
        }

        if (!hasCubes) return new float[]{0.0f, 0.0f, 0.0f, 0.0f};
        return new float[] {
                (minX + maxX) * 0.5f * 0.0625f,
                (minY + maxY) * 0.5f * 0.0625f,
                (minZ + maxZ) * 0.5f * 0.0625f,
                1.0f
        };
    }

    private static Mesh normalizeMesh(List<V> vs, float[] bounds) {
        if (vs.isEmpty()) return Mesh.empty();
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;

        for (V v : vs) {
            minX = Math.min(minX, v.x); maxX = Math.max(maxX, v.x);
            minY = Math.min(minY, v.y); maxY = Math.max(maxY, v.y);
            minZ = Math.min(minZ, v.z); maxZ = Math.max(maxZ, v.z);
        }

        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;
        float span = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));

        float rawX = bounds != null && bounds.length > 0 ? bounds[0] : 0.0f;
        float rawY = bounds != null && bounds.length > 1 ? bounds[1] : 0.0f;
        float rawZ = bounds != null && bounds.length > 2 ? bounds[2] : 0.0f;
        boolean playerSpaceMesh = Math.abs(rawY) > 0.85f;

        return new Mesh(List.copyOf(vs), cx, cy, cz, Math.max(0.05f, span), rawX, rawY, rawZ, playerSpaceMesh);
    }

    private Metadata loadMetadata(Identifier id) {
        try {
            Optional<Resource> res = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (res.isPresent()) {
                try (InputStreamReader isr = new InputStreamReader(res.get().getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject obj = JsonParser.parseReader(isr).getAsJsonObject();
                    return new Metadata(
                            number(obj, "scale", 1.0f),
                            number(obj, "x", 0.0f),
                            number(obj, "y", 0.0f),
                            number(obj, "z", 0.0f),
                            number(obj, "preview_scale", 1.0f),
                            number(obj, "preview_yaw", -38.0f),
                            number(obj, "preview_pitch", 22.0f),
                            number(obj, "preview_y", 0.0f),
                            obj.has("raw_offset") ? obj.get("raw_offset").getAsBoolean() : false
                    );
                }
            }
        } catch (Exception ignored) {}
        return Metadata.DEFAULT;
    }

    private static String str(JsonObject obj, String key, String def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : def;
    }

    private static float number(JsonObject obj, String key, float def) {
        return obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsFloat() : def;
    }

    private static Vector3f vec(JsonArray a) {
        if (a == null || a.size() < 3) return new Vector3f();
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    private static String cleanName(String name, String fallback) {
        if (name == null || name.isBlank() || name.indexOf('\uFFFD') >= 0) {
            String s = fallback.replace('_', ' ').replace('-', ' ').trim();
            return s.isEmpty() ? fallback : Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
        return name;
    }

    // --- Records & Data Classes ---

    public record Asset(String id, String category, String path, String displayName,
                        Identifier geometry, Identifier animation, Identifier texture, Metadata metadata) {}

    public record SwordEntry(String name, String model, Identifier texture) {}

    public record Metadata(float scale, float x, float y, float z,
                           float previewScale, float previewYaw, float previewPitch, float previewY, boolean rawOffset) {
        public static final Metadata DEFAULT = new Metadata(1.0f, 0.0f, 0.0f, 0.0f, 1.0f, -38.0f, 22.0f, 0.0f, false);
    }

    public record Face(float u1, float v1, float u2, float v2) {}

    public record Mesh(List<V> vertices, float cx, float cy, float cz, float span,
                       float rawCx, float rawCy, float rawCz, boolean playerSpaceMesh) {
        public static Mesh empty() {
            return new Mesh(Collections.emptyList(), 0, 0, 0, 1, 0, 0, 0, false);
        }
    }

    public record V(float x, float y, float z, float u, float v) {}

    public record Bone(String name, String parent, Vector3f pivot, Vector3f rotation, JsonArray cubes) {}

    public record AnimationPose(Vector3f rotation, Vector3f position, Vector3f scale) {
        public static final AnimationPose IDENTITY = new AnimationPose(new Vector3f(), new Vector3f(), new Vector3f(1.0f, 1.0f, 1.0f));
    }

    private record AnimatedMeshEntry(long frame, Mesh mesh) {}

    private record PreviewVertex(float x, float y, float u, float v, float depth) {}

    private record PreviewFace(PreviewVertex a, PreviewVertex b, PreviewVertex c, PreviewVertex d, float depth) {}

    public static class ParsedSwordModel {
        public final List<ModelElement> elements;
        public final ModelTransformation transformation;
        public final Map<String, Identifier> textures;

        public ParsedSwordModel(List<ModelElement> elements, ModelTransformation transformation, Map<String, Identifier> textures) {
            this.elements = elements != null ? elements : Collections.emptyList();
            this.transformation = transformation != null ? transformation : ModelTransformation.NONE;
            this.textures = textures != null ? textures : Collections.emptyMap();
        }
    }


}


