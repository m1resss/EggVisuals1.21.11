package delta.render;

import delta.collector.BatchProcessor;
import delta.core.NativeMethodLookup;
import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.render.ColorUtil;

import delta.config.BaseProcessor;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.event.DrawEvent;

import delta.api.Compile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.texture.Sprite;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

public class Draw3DProcessor extends BaseProcessor implements Interface {
    @Override
    @Compile
    public void setup() {
    }

    static {
        NativeMethodLookup.lookup(Draw3DProcessor.class, 29);
    }

    @Override
    public void unSetup() {
    }

    @EventTarget(a = 4)
    public void a(DrawEvent event) {
        if (event.c()) {
            Delta.h().d().l().b();
        } else if (event.b()) {
            Delta.h().d().l().a();
        }
    }

    public void a(MatrixStack matrices, Box box, int color, float width) {
        Delta.h().d().l().a(BatchProcessor.b.a(matrices, box, color, width));
    }

    public void a(Matrix4f matrix, float minX, float minY, float maxX, float maxY, int color, boolean corners, boolean healthBar, float healthPercent, int healthColor) {
        Delta.h().d().l().a(new BatchProcessor.a(matrix, minX, minY, maxX, maxY, color, corners, healthBar, healthPercent, healthColor));
    }

    public void a(MatrixStack matrices, Vec3d start, Vec3d end, Vec3d control, int color, float width) {
        Delta.h().d().l().a(BatchProcessor.b.a(matrices, start, end, control, color, width));
    }

    public void a(DrawContext context, ItemStack stack, float x, float y, int z, float alpha, float scale, boolean overlay) {
        if (!stack.isEmpty()) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(scale, scale);
            
            context.drawItem(stack, 0, 0);
            if (overlay) {
                context.drawStackOverlay(aM_.textRenderer, stack, 0, 0);
            }
            
            context.getMatrices().popMatrix();
        }
    }

    public void a(DrawContext context, Sprite sprite, float x, float y, float z, float scale, float alpha) {
        if (context == null || sprite == null) return;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        context.drawSpriteStretched(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, sprite, 0, 0, 18, 18, ColorUtil.a(-1, alpha));
        context.getMatrices().popMatrix();
    }

    public void a(DrawContext context, net.minecraft.util.Identifier texture, float x, float y, float z, float scale, float alpha) {
        if (context == null || texture == null) return;
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y);
        context.getMatrices().scale(scale, scale);
        context.drawGuiTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture, 0, 0, 18, 18, ColorUtil.a(-1, alpha));
        context.getMatrices().popMatrix();
    }
}
