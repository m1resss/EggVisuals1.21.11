package egg.client.render.world;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import egg.client.EggClient;
import egg.client.render.shader.GlStateSnapshot;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.render.Frustum;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;

public final class SodiumPearlTerrain {
    public static final boolean LOADED = FabricLoader.getInstance().isModLoaded("sodium");
    private static final String SODIUM = "net.caffeinemc.mods.sodium.client.";
    private static final int RADIUS = 6;

    private static Access access;
    private static boolean failed;

    private SodiumPearlTerrain() {}

    public static boolean render(Matrix4f view, Matrix4f projection, Vec3d position) {
        if (!LOADED || failed || !GlStateSnapshot.isOpenGl()) return false;
        try {
            if (access == null) access = new Access();
            return access.render(view, projection, position);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            failed = true;
            EggClient.LOGGER.warn("PearlPredict could not render the Sodium camera", exception);
            return false;
        }
    }

    private record VisibleSection(Object region, int index, double distance) {}

    private static final class Access {
        private final Class<?> renderManagerType = sodium("render.chunk.RenderSectionManager");
        private final Class<?> worldRendererType = sodium("render.SodiumWorldRenderer");
        private final Class<?> matricesType = sodium("render.chunk.ChunkRenderMatrices");
        private final Class<?> sectionType = sodium("render.chunk.RenderSection");
        private final Class<?> regionType = sodium("render.chunk.region.RenderRegion");
        private final Class<?> listType = sodium("render.chunk.lists.ChunkRenderList");
        private final Class<?> listsType = sodium("render.chunk.lists.SortedRenderLists");
        private final Class<?> deviceType = sodium("gl.device.RenderDevice");
        private final Class<?> passType = sodium("render.chunk.terrain.TerrainRenderPass");
        private final Class<?> passesType = sodium("render.chunk.terrain.DefaultTerrainRenderPasses");
        private final Class<?> fogType = sodium("util.FogParameters");

        private final Method worldInstance = worldRendererType.getMethod("instanceNullable");
        private final Field sectionManager = field(worldRendererType, "renderSectionManager");
        private final Field lastFog = field(worldRendererType, "lastFogParameters");
        private final Field sectionMap = field(renderManagerType, "sectionByPosition");
        private final Field renderLists = field(renderManagerType, "renderLists");
        private final Method renderLayer = renderManagerType.getMethod("renderLayer", matricesType, passType,
                double.class, double.class, double.class, fogType, GpuSampler.class);
        private final Method enterManaged = deviceType.getMethod("enterManagedCode");
        private final Method exitManaged = deviceType.getMethod("exitManagedCode");
        private final Method sectionFlags = sectionType.getMethod("getFlags");
        private final Method sectionIndex = sectionType.getMethod("getSectionIndex");
        private final Method sectionRegion = sectionType.getMethod("getRegion");
        private final Method clearBatches = regionType.getMethod("clearAllCachedBatches");
        private final Method resetList = listType.getMethod("reset", int.class, boolean.class);
        private final Method addSection = listType.getMethod("add", int.class, int.class);
        private final Constructor<?> listConstructor = listType.getConstructor(regionType);
        private final Constructor<?> listsConstructor = constructor(listsType, ObjectArrayList.class);
        private final Constructor<?> matricesConstructor = matricesType.getConstructor(Matrix4fc.class, Matrix4fc.class);
        private final Object[] passes = { passesType.getField("SOLID").get(null), passesType.getField("CUTOUT").get(null),
                passesType.getField("TRANSLUCENT").get(null) };
        private final Object irisApi;
        private final Method irisShadersActive;

        private Access() throws ReflectiveOperationException {
            if (FabricLoader.getInstance().isModLoaded("iris")) {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisShadersActive = api.getMethod("isShaderPackInUse");
            } else {
                irisApi = null;
                irisShadersActive = null;
            }
        }

        private boolean render(Matrix4f view, Matrix4f projection, Vec3d position) throws ReflectiveOperationException {
            if (irisShadersActive != null && (boolean) irisShadersActive.invoke(irisApi)) return false;
            Object renderer = worldInstance.invoke(null);
            if (renderer == null) return false;
            Object manager = sectionManager.get(renderer);
            Object fog = lastFog.get(renderer);
            if (manager == null || fog == null) return false;

            var sections = (Long2ReferenceMap<?>) sectionMap.get(manager);
            var visible = findVisibleSections(sections, view, projection, position);
            if (visible.isEmpty()) return false;
            var regionLists = new LinkedHashMap<Object, Object>();
            for (VisibleSection section : visible) {
                Object list = regionLists.get(section.region());
                if (list == null) {
                    list = listConstructor.newInstance(section.region());
                    resetList.invoke(list, 0, true);
                    regionLists.put(section.region(), list);
                }
                addSection.invoke(list, section.index(), 1);
            }

            Object previewLists = listsConstructor.newInstance(new ObjectArrayList<>(regionLists.values()));
            Object originalLists = renderLists.get(manager);
            Object matrices = matricesConstructor.newInstance(projection, view);
            GpuSampler sampler = RenderSystem.getSamplerCache().get(FilterMode.LINEAR);
            GlStateSnapshot glState = GlStateSnapshot.capture();
            enterManaged.invoke(null);
            try {
                for (Object region : regionLists.keySet()) clearBatches.invoke(region);
                renderLists.set(manager, previewLists);
                for (Object pass : passes) {
                    renderLayer.invoke(manager, matrices, pass, position.x, position.y, position.z, fog, sampler);
                }
            } finally {
                try {
                    renderLists.set(manager, originalLists);
                    for (Object region : regionLists.keySet()) clearBatches.invoke(region);
                } finally {
                    try {
                        exitManaged.invoke(null);
                    } finally {
                        glState.restore();
                    }
                }
            }
            return true;
        }

        private ArrayList<VisibleSection> findVisibleSections(Long2ReferenceMap<?> sections, Matrix4f view,
                                                               Matrix4f projection, Vec3d position) throws ReflectiveOperationException {
            Frustum frustum = new Frustum(view, projection);
            frustum.setPosition(position.x, position.y, position.z);
            ChunkSectionPos center = ChunkSectionPos.from(position);
            var visible = new ArrayList<VisibleSection>();
            for (int x = center.getSectionX() - RADIUS; x <= center.getSectionX() + RADIUS; x++) {
                for (int y = center.getSectionY() - RADIUS; y <= center.getSectionY() + RADIUS; y++) {
                    for (int z = center.getSectionZ() - RADIUS; z <= center.getSectionZ() + RADIUS; z++) {
                        Object section = sections.get(ChunkSectionPos.asLong(x, y, z));
                        if (section == null) continue;
                        Box bounds = new Box(x * 16, y * 16, z * 16, x * 16 + 16, y * 16 + 16, z * 16 + 16);
                        if (!frustum.isVisible(bounds) || (((int) sectionFlags.invoke(section)) & 1) == 0) continue;
                        visible.add(new VisibleSection(sectionRegion.invoke(section), (int) sectionIndex.invoke(section),
                                bounds.getCenter().squaredDistanceTo(position)));
                    }
                }
            }
            visible.sort(Comparator.comparingDouble(VisibleSection::distance));
            return visible;
        }
    }

    private static Class<?> sodium(String name) throws ClassNotFoundException {
        return Class.forName(SODIUM + name);
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Constructor<?> constructor(Class<?> owner, Class<?> argument) throws NoSuchMethodException {
        Constructor<?> constructor = owner.getDeclaredConstructor(argument);
        constructor.setAccessible(true);
        return constructor;
    }
}
