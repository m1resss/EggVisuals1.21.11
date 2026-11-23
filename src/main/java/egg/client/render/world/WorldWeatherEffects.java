package egg.client.render.world;

import com.mojang.blaze3d.textures.GpuTextureView;
import egg.client.module.render.WorldTweaks;
import egg.client.module.render.WorldTweaks.Frame;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.client.render.fog.FogData;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

public final class WorldWeatherEffects {
    private static final WeatherPostChain SKY = new WeatherPostChain("weather_sky");
    private static final WeatherPostChain GLOSS = new WeatherPostChain("weather_gloss");
    private static final WeatherPostChain GRADE = new WeatherPostChain("world_grade");
    private static final Identifier TERRAIN = Identifier.of("egg", "textures/effect/weather_terrain_depth.png");
    private static final DepthTexture DEPTH_TEXTURE = new DepthTexture();
    private static SimpleFramebuffer terrainDepth;
    private static boolean captureDepth;
    private static boolean depthTextureRegistered;
    private static boolean depthReady;
    private static float tanHalfFov = 0.7f;
    private static float environmentStart;
    private static float environmentEnd;
    private static float distanceStart;
    private static float distanceEnd;
    private static final Vector4f fogColor = new Vector4f();
    private static WeatherSky.State currentSky;
    private static WeatherSky.State previousSky;
    private static WeatherProfile lastProfile;
    private static int lastStyle;
    private static long skyStart;

    private WorldWeatherEffects() {
    }

    public static void begin(float projectionY) {
        tanHalfFov = 1 / Math.max(0.001f, Math.abs(projectionY));
        depthReady = false;
        captureDepth = false;
    }

    public static void captureFog(FogData data, Vector4f color) {
        environmentStart = data.environmentalStart;
        environmentEnd = data.environmentalEnd;
        distanceStart = data.renderDistanceStart;
        distanceEnd = data.renderDistanceEnd;
        fogColor.set(color);
    }

    public static Vector4f weatherFog(WorldTweaks tweaks) {
        return sky(tweaks).fogColor();
    }

    static WeatherSky.State sky(WorldTweaks tweaks) {
        WeatherProfile profile = tweaks.profile();
        int style = tweaks.skyStyle();
        long now = System.currentTimeMillis();
        if (lastProfile != profile || lastStyle != style) {
            previousSky = currentSky;
            lastProfile = profile;
            lastStyle = style;
            skyStart = now;
        }
        WeatherSky.State desired = WeatherSky.sample(profile, style, tweaks.skyHour(), tweaks.frame().haze());
        float t = WeatherProfile.smooth((now - skyStart) / 900f);
        currentSky = previousSky != null && t < 1 ? previousSky.mix(desired, t) : desired;
        return currentSky;
    }

    public static void appendSky(MinecraftClient client, FrameGraphBuilder graph, DefaultFramebufferSet targets, Camera camera) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        if (tweaks == null || !tweaks.hasWeatherEffects() || camera.getSubmersionType() != CameraSubmersionType.NONE) return;
        Frame weather = tweaks.frame();
        SKY.append(client, graph, targets, skyUniforms(client, camera, tweaks, weather, sky(tweaks)));
    }

    public static void appendWorld(MinecraftClient client, FrameGraphBuilder graph, DefaultFramebufferSet targets, Camera camera) {
        WorldTweaks tweaks = WorldTweaks.getInstance();
        if (tweaks == null || client.world == null) return;
        if (!tweaks.isEnabled() && !tweaks.hasWeatherEffects() && tweaks.wetness() <= 0.005f) {
            releaseTerrain();
            return;
        }
        Frame weather = tweaks.frame();
        WeatherSky.State sky = sky(tweaks);
        float weatherAmount = weather.amount();
        float brightness = tweaks.brightness() * MathHelper.lerp(weatherAmount, 1, sky.brightness())
                * (1 + tweaks.flash() * 0.85f);
        float saturation = tweaks.saturation() * weather.saturation();
        Vector4f tint = rgb(sky.lightTint());
        Vector4f grade = new Vector4f(MathHelper.lerp(weatherAmount, 1, tint.x),
                MathHelper.lerp(weatherAmount, 1, tint.y), MathHelper.lerp(weatherAmount, 1, tint.z), brightness);

        boolean gloss = tweaks.wetness() > 0.005f && camera.getSubmersionType() == CameraSubmersionType.NONE;
        if (gloss) {
            if (!depthTextureRegistered) {
                client.getTextureManager().registerTexture(TERRAIN, DEPTH_TEXTURE);
                depthTextureRegistered = true;
            }
            Map<String, Object> uniforms = skyUniforms(client, camera, tweaks, weather, sky);
            gradeUniforms(uniforms, saturation, grade);
            float fogLimit = weather.fogDistance() > 0 ? weather.fogDistance() : client.gameRenderer.getFarPlaneDistance();
            float far = client.gameRenderer.getFarPlaneDistance();
            uniforms.put("WetAmount", tweaks.wetness());
            uniforms.put("DarkAmount", weather.darkening());
            uniforms.put("WetTint", weather.wetTint());
            uniforms.put("ReflectAmount", weather.reflection() * sky.reflections());
            uniforms.put("GlintAmount", weather.glint() * sky.reflections());
            uniforms.put("PuddleAmount", weather.puddles());
            uniforms.put("Frost", weather.frost());
            uniforms.put("TerrainValid", 1f);
            uniforms.put("SnowGeometry", weather.frost());
            uniforms.put("CoverRadius", weather.frost() > 0.005f ? WeatherSnowMesh.RADIUS : 0f);
            uniforms.put("MistAmount", weather.mist() * weatherAmount);
            uniforms.put("MistTint", weather.mistTint());
            uniforms.put("MistBase", (float) camera.getCameraPos().y - 1.6f);
            uniforms.put("MistHeight", 3.2f);
            uniforms.put("MistWind", new Vector2f(weather.windX(), weather.windZ()));
            uniforms.put("FadeEnd", weather.frost() > 0.005f ? Math.min(far, Math.max(fogLimit * 1.6f, 192))
                    : MathHelper.clamp(fogLimit * 1.1f, Math.min(24, far), Math.min(96, far)));
            uniforms.put("RainAmount", weather.rain());
            if (GLOSS.append(client, graph, targets, uniforms)) {
                captureDepth = true;
                return;
            }
        }
        releaseTerrain();
        if (Math.abs(saturation - 1) < 0.001f && Math.abs(brightness - 1) < 0.001f && weatherAmount < 0.001f) return;
        Map<String, Object> uniforms = new HashMap<>();
        viewUniforms(client, camera, uniforms);
        gradeUniforms(uniforms, saturation, grade);
        GRADE.append(client, graph, targets, uniforms);
    }

    private static void releaseTerrain() {
        if (terrainDepth != null) {
            terrainDepth.delete();
            terrainDepth = null;
        }
    }

    public static void captureTerrain(MinecraftClient client) {
        if (!captureDepth || depthReady) return;
        var framebuffer = client.getFramebuffer();
        if (framebuffer.getDepthAttachment() == null) return;
        if (terrainDepth == null || terrainDepth.textureWidth != framebuffer.textureWidth
                || terrainDepth.textureHeight != framebuffer.textureHeight) {
            if (terrainDepth != null) terrainDepth.delete();
            terrainDepth = new SimpleFramebuffer("Egg weather terrain depth", framebuffer.textureWidth, framebuffer.textureHeight, true);
        }
        terrainDepth.copyDepthFrom(framebuffer);
        depthReady = true;
    }

    private static Map<String, Object> skyUniforms(MinecraftClient client, Camera camera, WorldTweaks tweaks, Frame weather, WeatherSky.State sky) {
        Map<String, Object> values = new HashMap<>(64);
        viewUniforms(client, camera, values);
        Vec3d eye = camera.getCameraPos();
        values.put("Intensity", weather.amount());
        values.put("time", tweaks.clock());
        values.put("CameraPosition", new Vector4f((float) eye.x, (float) eye.y, (float) eye.z, 0));
        values.put("SkyZenith", rgb(sky.zenith()));
        values.put("SkyHorizon", rgb(sky.horizon()));
        values.put("SkyNadir", rgb(sky.nadir()));
        values.put("SunDir", rgb(sky.sun()));
        values.put("SunColor", rgb(sky.sunColor()));
        values.put("SunSize", sky.sunSize());
        values.put("SunGlare", sky.sunGlare());
        values.put("CloudCover", sky.cloudCover());
        values.put("CloudDark", sky.cloudDark());
        values.put("CloudSpeed", sky.cloudSpeed());
        values.put("CloudScale", sky.cloudScale());
        values.put("CloudLit", rgb(sky.cloudLit()));
        values.put("CloudShadow", rgb(sky.cloudShadow()));
        values.put("StarAmount", sky.stars());
        values.put("BandAmount", sky.band());
        values.put("AuroraAmount", sky.aurora());
        values.put("MeteorAmount", sky.meteors());
        values.put("HazeAmount", sky.haze());
        values.put("FrostAmount", weather.frost() * 0.55f);
        values.put("HazeColor", rgb(sky.hazeColor()));
        values.put("GlowDir", rgb(sky.glowDirection()));
        values.put("GlowFocus", sky.glowFocus());
        values.put("Flash", tweaks.flash());
        values.put("FlashColor", rgb(sky.flashColor()));
        return values;
    }

    private static void viewUniforms(MinecraftClient client, Camera camera, Map<String, Object> values) {
        var framebuffer = client.getFramebuffer();
        float pitch = camera.getPitch() * MathHelper.RADIANS_PER_DEGREE;
        values.put("SkyPitch", pitch);
        values.put("Pitch", pitch);
        values.put("SkyYaw", (float) Math.PI - camera.getYaw() * MathHelper.RADIANS_PER_DEGREE);
        values.put("TanHalfFov", tanHalfFov);
        values.put("Aspect", framebuffer.textureWidth / (float) Math.max(1, framebuffer.textureHeight));
        values.put("NearPlane", 0.05f);
        values.put("FarPlane", client.gameRenderer.getFarPlaneDistance());
    }

    private static void gradeUniforms(Map<String, Object> values, float saturation, Vector4f grade) {
        values.put("WorldSaturation", saturation);
        values.put("WorldGrade", grade);
        values.put("FogEnvironmentalStart", environmentStart);
        values.put("FogEnvironmentalEnd", Math.max(environmentStart + 0.001f, environmentEnd));
        values.put("FogRenderDistanceStart", distanceStart);
        values.put("FogRenderDistanceEnd", Math.max(distanceStart + 0.001f, distanceEnd));
        values.put("FogColor", fogColor);
    }

    private static Vector4f rgb(Vector3fc color) {
        return new Vector4f(color.x(), color.y(), color.z(), 0);
    }

    private static final class DepthTexture extends AbstractTexture {
        @Override
        public GpuTextureView getGlTextureView() {
            return depthReady && terrainDepth != null ? terrainDepth.getDepthAttachmentView()
                    : MinecraftClient.getInstance().getFramebuffer().getDepthAttachmentView();
        }
    }
}
