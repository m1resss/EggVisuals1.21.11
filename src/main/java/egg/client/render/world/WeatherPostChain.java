package egg.client.render.world;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import egg.client.EggClient;
import egg.client.mixin.PostEffectPassAccessor;
import egg.client.mixin.PostEffectProcessorAccessor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.FrameGraphBuilder;
import net.minecraft.util.Identifier;
import org.joml.Vector2fc;
import org.joml.Vector4fc;

final class WeatherPostChain {
    private static final String UNIFORMS = "EggWeatherPost";
    private final Identifier id;
    private final Identifier json;
    private PostEffectProcessor owner;
    private List<List<Field>> layouts = List.of();

    WeatherPostChain(String name) {
        id = Identifier.of("egg", name);
        json = Identifier.of("egg", "post_effect/" + name + ".json");
    }

    boolean append(MinecraftClient client, FrameGraphBuilder graph, DefaultFramebufferSet targets, Map<String, Object> values) {
        PostEffectProcessor processor = client.getShaderLoader().loadPostEffect(id, DefaultFramebufferSet.MAIN_ONLY);
        if (processor == null) return false;
        if (processor != owner) {
            owner = processor;
            layouts = readLayouts(client);
        }
        List<PostEffectPass> passes = ((PostEffectProcessorAccessor) processor).egg$getPasses();
        if (passes.size() != layouts.size()) return false;

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        for (int i = 0; i < passes.size(); i++) {
            List<Field> fields = layouts.get(i);
            if (fields.isEmpty()) continue;
            Map<String, GpuBuffer> buffers = ((PostEffectPassAccessor) passes.get(i)).egg$getUniformBuffers();
            GpuBuffer current = buffers.get(UNIFORMS);
            if (current == null) return false;
            if ((current.usage() & GpuBuffer.USAGE_MAP_WRITE) == 0) {
                GpuBuffer replacement = RenderSystem.getDevice().createBuffer(() -> "Egg " + id.getPath() + " uniforms",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, current.size());
                buffers.put(UNIFORMS, replacement);
                current.close();
                current = replacement;
            }
            try (GpuBuffer.MappedView view = encoder.mapBuffer(current, false, true)) {
                Std140Builder builder = Std140Builder.intoBuffer(view.data());
                for (Field field : fields) field.put(builder, values.get(field.name));
            }
        }
        var framebuffer = client.getFramebuffer();
        processor.render(graph, framebuffer.textureWidth, framebuffer.textureHeight, targets);
        return true;
    }

    private List<List<Field>> readLayouts(MinecraftClient client) {
        try (var reader = client.getResourceManager().openAsReader(json)) {
            JsonArray passes = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("passes");
            List<List<Field>> result = new ArrayList<>(passes.size());
            for (JsonElement element : passes) {
                JsonObject blocks = element.getAsJsonObject().getAsJsonObject("uniforms");
                JsonArray uniforms = blocks == null ? null : blocks.getAsJsonArray(UNIFORMS);
                if (uniforms == null) {
                    result.add(Collections.emptyList());
                    continue;
                }
                List<Field> fields = new ArrayList<>(uniforms.size());
                for (JsonElement field : uniforms) {
                    JsonObject object = field.getAsJsonObject();
                    fields.add(new Field(object.get("name").getAsString(), object.get("type").getAsString(), object.get("value")));
                }
                result.add(fields);
            }
            return result;
        } catch (IOException | RuntimeException exception) {
            EggClient.LOGGER.warn("Could not read weather shader {}", id, exception);
            return List.of();
        }
    }

    private record Field(String name, String type, JsonElement defaultValue) {
        void put(Std140Builder builder, Object value) {
            if (value instanceof Vector4fc vector) {
                builder.putVec4(vector);
            } else if (value instanceof Vector2fc vector) {
                builder.putVec2(vector);
            } else if (value instanceof Number number) {
                builder.putFloat(number.floatValue());
            } else if ("vec4".equals(type)) {
                JsonArray array = defaultValue.getAsJsonArray();
                builder.putVec4(array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat(), array.get(3).getAsFloat());
            } else if ("vec2".equals(type)) {
                JsonArray array = defaultValue.getAsJsonArray();
                builder.putVec2(array.get(0).getAsFloat(), array.get(1).getAsFloat());
            } else {
                builder.putFloat(defaultValue.getAsFloat());
            }
        }
    }
}
