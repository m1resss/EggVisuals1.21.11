package delta.module.combat;
import delta.render.RenderStateHelper;

import delta.handler.Handler_2;

import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.render.EasingList;
import delta.render.ColorUtil;

import delta.config.ThemeInfo;
import delta.core.EventTarget;
import delta.core.GlobalEvent;
import delta.core.Interface;
import delta.event.DrawEvent;
import delta.event.TickEvent;
import delta.handler.BaseHandler;
import delta.module.render.TargetESP;

import delta.render.AnimationUtil;
import delta.render.LightningRenderer;
import delta.util.ProjectUtil;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import lombok.Generated;

import org.lwjgl.opengl.GL11;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import delta.render.ImmediateBufferRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.render.Camera;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import delta.render.LightningRenderer;
import org.joml.Vector2f;
import org.joml.Vector3f;

@Handler_2
public class AuraHandler extends BaseHandler implements Interface {
    private static final Identifier TARGET_ESP_TEXTURE = Identifier.of("delta", "pictures/target_esp.png");
    private static final Identifier GLOW_TEXTURE = Identifier.of("delta", "pictures/bloom.png");
    private static final Identifier RHOMBUS_TEXTURE = Identifier.of("delta", "textures/target/rhombus.png");
    private static AuraHandler instance;
    private final AnimationUtil e = new AnimationUtil();
    private final LightningRenderer.State lightningState = new LightningRenderer.State();
    private LivingEntity f;

    public AuraHandler() {
        instance = this;
    }

    public static AuraHandler getInstance() {
        return instance;
    }

    @Generated
    public AnimationUtil a() {
        return this.e;
    }

    @Generated
    public LivingEntity getTarget() {
        return this.f;
    }

    @EventTarget
    public void a(DrawEvent event) {
        TargetESP targetESP = TargetESP.getInstance();
        if (targetESP == null || !targetESP.m()) {
            return;
        }
        this.e.a(0.0f, 1.0f, 0.2f, EasingList.g, event.g());
        float moving = ((System.currentTimeMillis() % 360000) / 2.5f) + this.e.c();
        if (this.f == null || aM_.player == null || !aM_.player.canSee(this.f)) {
            return;
        }
        float anim = this.e.c();
        if (anim <= 0.0f) {
            return;
        }
        String visualizationMode = targetESP.getMode().c();
        if (event.c()) {
            int themeColor = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
            Vec3d renderPos = a(this.f);

            if (visualizationMode.equals("Алмаз")) {
                renderDiamond(event, anim, themeColor);
            } else if (visualizationMode.equals("Ромб")) {
                renderRhombus(event, anim, themeColor);
            } else if (visualizationMode.equals("Призраки") || visualizationMode.equalsIgnoreCase("Ghosts")) {
                renderSpirits(event, anim, themeColor);
            } else if (visualizationMode.equalsIgnoreCase("Scan")) {
                // Scan is submitted per-model onto the player geometry via TargetScanRenderer & Mixin
            } else if (visualizationMode.equalsIgnoreCase("Sadnes Молнии") || visualizationMode.toLowerCase().contains("молни")) {
                int lColor;
                if (targetESP.getLightningColor().l("Тема")) {
                    lColor = themeColor;
                } else if (targetESP.getLightningColor().l("Кастом")) {
                    lColor = targetESP.getLightningCustomColor().c().intValue();
                } else {
                    lColor = ColorUtil.a(255, 255, 255, 255);
                }
                float lSpeed = targetESP.getLightningSpeed().c().floatValue();
                float lCount = targetESP.getLightningBranches().c().floatValue();
                float lFade = targetESP.getLightningFade().c().floatValue();
                float lGlowPower = targetESP.getLightningGlowPower().c().floatValue();
                boolean lBloom = targetESP.getLightningBloom().c().booleanValue();

                LightningRenderer.render(
                        event.h(),
                        this.lightningState,
                        renderPos,
                        this.f.getHeight(),
                        this.f,
                        anim,
                        lSpeed,
                        lCount,
                        lFade,
                        lGlowPower,
                        lBloom,
                        lColor
                );
            } else if (visualizationMode.equals("Круг")) {
                b();
                a(event.h(), renderPos, ColorUtil.a(themeColor, anim));
                c();
            }
        }
    }

    private void renderDiamond(DrawEvent event, float anim, int themeColor) {
        if (this.f == null || aM_.player == null || aM_.gameRenderer == null) return;

        MatrixStack matrices = event.h();
        float tickDelta = event.g();
        Camera camera = aM_.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        double x = this.f.getLerpedPos(tickDelta).x - camPos.x;
        double y = this.f.getLerpedPos(tickDelta).y - camPos.y;
        double z = this.f.getLerpedPos(tickDelta).z - camPos.z;

        float time = (float) ((System.currentTimeMillis() % 10000000L) / 1000.0);
        float bob = (float) Math.sin(time * 3.5) * 0.08f;
        float height = this.f.getHeight();
        double centerY = y + height + 0.42 + bob;

        float hurtTimer = Math.max(0.0F, this.f.hurtTime - tickDelta);
        float hurtFactor = (float) Math.sin(hurtTimer * (Math.PI / 10.0)) * 0.5F;
        int activeColor = ColorUtil.b(themeColor, ColorUtil.a(255, 0, 0, (themeColor >> 24) & 0xFF), hurtFactor);

        matrices.push();
        matrices.translate(x, centerY, z);
        float rotAngle = (time * 100.0f) % 360.0f;
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotAngle));

        float scale = 0.22f * anim;
        matrices.scale(scale, scale, scale);
        Matrix4f m = matrices.peek().getPositionMatrix();

        RenderStateHelper.enableBlend();
        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.disableCull();
        RenderStateHelper.enableDepthTest();
        RenderStateHelper.depthFunc(GL11.GL_LEQUAL);
        RenderStateHelper.depthMask(false);

        // 3D Diamond Gemstone Geometry (32 faceted cut)
        Vector3f top = new Vector3f(0f, 1.40f, 0f);
        Vector3f bot = new Vector3f(0f, -1.40f, 0f);
        Vector3f[] upper = new Vector3f[8];
        Vector3f[] lower = new Vector3f[8];

        float r1 = 1.05f;
        float r2 = 0.90f;
        float yUpper = 0.40f;
        float yLower = -0.10f;

        for (int i = 0; i < 8; i++) {
            double aUpper = i * (Math.PI / 4.0);
            double aLower = (i + 0.5) * (Math.PI / 4.0);
            upper[i] = new Vector3f((float) Math.cos(aUpper) * r1, yUpper, (float) Math.sin(aUpper) * r1);
            lower[i] = new Vector3f((float) Math.cos(aLower) * r2, yLower, (float) Math.sin(aLower) * r2);
        }

        Vector3f lightDir = new Vector3f(0.55f, 0.75f, 0.35f).normalize();
        Vector3f specDir = new Vector3f(0.0f, 0.707f, 0.707f).normalize();

        int cr = (activeColor >> 16) & 0xFF;
        int cg = (activeColor >> 8) & 0xFF;
        int cb = activeColor & 0xFF;
        int faceAlpha = (int) (225 * anim);

        // 1. Facet Fill Pass with Directional Shading
        // RenderSystem.setShader(...);
        BufferBuilder fb = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < 8; i++) {
            int next = (i + 1) % 8;

            // Crown Triangles (Top -> Upper Ring)
            renderFacet(fb, m, top, upper[i], upper[next], lightDir, specDir, cr, cg, cb, faceAlpha);

            // Girdle Facets (Upper -> Lower Rings)
            renderFacet(fb, m, upper[i], lower[i], upper[next], lightDir, specDir, cr, cg, cb, faceAlpha);
            renderFacet(fb, m, upper[next], lower[i], lower[next], lightDir, specDir, cr, cg, cb, faceAlpha);

            // Pavilion Triangles (Lower Ring -> Bottom Tip)
            renderFacet(fb, m, lower[i], bot, lower[next], lightDir, specDir, cr, cg, cb, faceAlpha);
        }
        ImmediateBufferRenderer.draw(fb.end());

        // 2. Facet Ridge Edges (Luminous Wireframe Lines)
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        RenderStateHelper.lineWidth(1.6f);
        int edgeAlpha = (int) (255 * anim);
        int er = Math.min(255, (int) (cr * 1.25f + 40));
        int eg = Math.min(255, (int) (cg * 1.25f + 40));
        int eb = Math.min(255, (int) (cb * 1.25f + 40));

        BufferBuilder edgeBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < 8; i++) {
            int next = (i + 1) % 8;

            // Ridge from top
            drawEdge(edgeBuffer, m, top, upper[i], er, eg, eb, edgeAlpha);
            // Upper ring edge
            drawEdge(edgeBuffer, m, upper[i], upper[next], er, eg, eb, edgeAlpha);
            // Middle girdle edges
            drawEdge(edgeBuffer, m, upper[i], lower[i], er, eg, eb, edgeAlpha);
            drawEdge(edgeBuffer, m, upper[next], lower[i], er, eg, eb, edgeAlpha);
            // Lower ring edge
            drawEdge(edgeBuffer, m, lower[i], lower[next], er, eg, eb, edgeAlpha);
            // Ridge to bot
            drawEdge(edgeBuffer, m, lower[i], bot, er, eg, eb, edgeAlpha);
        }
        ImmediateBufferRenderer.draw(edgeBuffer.end());
        GL11.glDisable(GL11.GL_LINE_SMOOTH);

        matrices.pop();

        // 3. Center Radiant Bloom Glow
        matrices.push();
        matrices.translate(x, centerY, z);
        matrices.multiply(camera.getRotation());
        float glowSize = 0.65f * anim;
        float hg = glowSize * 0.5f;
        Matrix4f gm = matrices.peek().getPositionMatrix();

        RenderStateHelper.enableBlend();
        RenderStateHelper.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        RenderStateHelper.setShaderTexture(0, GLOW_TEXTURE);
        // RenderSystem.setShader(...);

        int glowCol = ColorUtil.a(activeColor, (int) (155 * anim));
        int gr = (glowCol >> 16) & 0xFF;
        int gg = (glowCol >> 8) & 0xFF;
        int gb = glowCol & 0xFF;
        int ga = (glowCol >> 24) & 0xFF;

        BufferBuilder glowBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        glowBuffer.vertex(gm, -hg, -hg, 0.0f).texture(0.0f, 0.0f).color(gr, gg, gb, ga);
        glowBuffer.vertex(gm, -hg, hg, 0.0f).texture(0.0f, 1.0f).color(gr, gg, gb, ga);
        glowBuffer.vertex(gm, hg, hg, 0.0f).texture(1.0f, 1.0f).color(gr, gg, gb, ga);
        glowBuffer.vertex(gm, hg, -hg, 0.0f).texture(1.0f, 0.0f).color(gr, gg, gb, ga);
        ImmediateBufferRenderer.draw(glowBuffer.end());
        RenderStateHelper.setShaderTexture(0, 0);

        matrices.pop();

        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.depthMask(true);
        RenderStateHelper.enableDepthTest();
        RenderStateHelper.enableCull();
        RenderStateHelper.disableBlend();
    }

    private void renderRhombus(DrawEvent event, float anim, int themeColor) {
        if (this.f == null || aM_.player == null || aM_.gameRenderer == null) return;

        MatrixStack matrices = event.h();
        float tickDelta = event.g();
        Camera camera = aM_.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        double x = this.f.getLerpedPos(tickDelta).x - camPos.x;
        double y = this.f.getLerpedPos(tickDelta).y - camPos.y;
        double z = this.f.getLerpedPos(tickDelta).z - camPos.z;

        TargetESP targetESP = TargetESP.getInstance();
        float userHeight = (targetESP != null) ? targetESP.getRhombusHeight().c().floatValue() : 0.0f;
        float userSize = (targetESP != null) ? targetESP.getRhombusSize().c().floatValue() : 1.25f;

        float timeSec = (float) ((System.currentTimeMillis() % 10000000L) / 1000.0);
        float bob = (float) Math.sin(timeSec * 3.5) * 0.04f;
        float height = this.f.getHeight();
        double centerY = y + (height * 0.5) + userHeight + bob;

        double dist = Math.sqrt(x * x + centerY * centerY + z * z);
        if (dist < 0.01) return;

        // Offset slightly along line of sight towards camera (0.42 blocks)
        // so the rhombus renders in front of the player's 3D body without clipping inside the model
        double offset = Math.min(0.42, dist * 0.4);
        double renderX = x - (x / dist) * offset;
        double renderY = centerY - (centerY / dist) * offset;
        double renderZ = z - (z / dist) * offset;

        float hurtTimer = Math.max(0.0F, this.f.hurtTime - tickDelta);
        float hurtFactor = (float) Math.sin(hurtTimer * (Math.PI / 10.0)) * 0.5F;
        int activeColor = ColorUtil.b(themeColor, ColorUtil.a(255, 30, 40, (themeColor >> 24) & 0xFF), hurtFactor);

        // 3 Full rotations (1080 degrees) forward and backward with smooth deceleration / stop
        long cyclePeriod = 5600L; // 2.8s forward (3 rotations), 2.8s backward (3 rotations)
        long halfPeriod = cyclePeriod / 2L;
        long curTime = System.currentTimeMillis() % cyclePeriod;

        float rotAngle;
        if (curTime < halfPeriod) {
            float u = (float) curTime / (float) halfPeriod; // 0.0 -> 1.0
            float ease = (float) (0.5 - 0.5 * Math.cos(u * Math.PI)); // smooth ease in and out
            rotAngle = ease * 1080.0f;
        } else {
            float u = (float) (curTime - halfPeriod) / (float) halfPeriod; // 0.0 -> 1.0
            float ease = (float) (0.5 - 0.5 * Math.cos(u * Math.PI));
            rotAngle = (1.0f - ease) * 1080.0f;
        }

        float size = userSize * anim;
        float hs = size * 0.5f;

        RenderStateHelper.enableBlend();
        RenderStateHelper.disableCull();
        RenderStateHelper.enableDepthTest();
        RenderStateHelper.depthFunc(GL11.GL_LEQUAL);
        RenderStateHelper.depthMask(false);

        // 1. Center soft bloom aura glow
        matrices.push();
        matrices.translate(renderX, renderY, renderZ);
        matrices.multiply(camera.getRotation());
        float glowSize = size * 1.35f;
        float hg = glowSize * 0.5f;
        Matrix4f gm = matrices.peek().getPositionMatrix();

        RenderStateHelper.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        RenderStateHelper.setShaderTexture(0, GLOW_TEXTURE);
        // RenderSystem.setShader(...);

        int glowCol = ColorUtil.a(activeColor, (int) (140 * anim));
        int gr = (glowCol >> 16) & 0xFF;
        int gg = (glowCol >> 8) & 0xFF;
        int gb = glowCol & 0xFF;
        int ga = (glowCol >> 24) & 0xFF;

        BufferBuilder glowBuffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        glowBuffer.vertex(gm, -hg, -hg, 0.0f).texture(0.0f, 0.0f).color(gr, gg, gb, ga);
        glowBuffer.vertex(gm, -hg, hg, 0.0f).texture(0.0f, 1.0f).color(gr, gg, gb, ga);
        glowBuffer.vertex(gm, hg, hg, 0.0f).texture(1.0f, 1.0f).color(gr, gg, gb, ga);
        glowBuffer.vertex(gm, hg, -hg, 0.0f).texture(1.0f, 0.0f).color(gr, gg, gb, ga);
        ImmediateBufferRenderer.draw(glowBuffer.end());
        matrices.pop();

        // 2. Main Rotating Rhombus Billboard
        matrices.push();
        matrices.translate(renderX, renderY, renderZ);
        matrices.multiply(camera.getRotation());
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-rotAngle));
        Matrix4f rm = matrices.peek().getPositionMatrix();

        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.setShaderTexture(0, RHOMBUS_TEXTURE);
        // RenderSystem.setShader(...);

        int cr = (activeColor >> 16) & 0xFF;
        int cg = (activeColor >> 8) & 0xFF;
        int cb = activeColor & 0xFF;
        int ca = (int) (255 * anim);

        BufferBuilder rb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        rb.vertex(rm, -hs, -hs, 0.0f).texture(0.0f, 0.0f).color(cr, cg, cb, ca);
        rb.vertex(rm, -hs, hs, 0.0f).texture(0.0f, 1.0f).color(cr, cg, cb, ca);
        rb.vertex(rm, hs, hs, 0.0f).texture(1.0f, 1.0f).color(cr, cg, cb, ca);
        rb.vertex(rm, hs, -hs, 0.0f).texture(1.0f, 0.0f).color(cr, cg, cb, ca);
        ImmediateBufferRenderer.draw(rb.end());

        // 3. Additive bright specular edge highlight pass
        RenderStateHelper.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        int whiteGlow = (int) (180 * anim);
        BufferBuilder wb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        wb.vertex(rm, -hs, -hs, 0.0f).texture(0.0f, 0.0f).color(255, 255, 255, whiteGlow);
        wb.vertex(rm, -hs, hs, 0.0f).texture(0.0f, 1.0f).color(255, 255, 255, whiteGlow);
        wb.vertex(rm, hs, hs, 0.0f).texture(1.0f, 1.0f).color(255, 255, 255, whiteGlow);
        wb.vertex(rm, hs, -hs, 0.0f).texture(1.0f, 0.0f).color(255, 255, 255, whiteGlow);
        ImmediateBufferRenderer.draw(wb.end());

        RenderStateHelper.setShaderTexture(0, 0);
        matrices.pop();

        RenderStateHelper.defaultBlendFunc();
        RenderStateHelper.depthMask(true);
        RenderStateHelper.enableDepthTest();
        RenderStateHelper.enableCull();
        RenderStateHelper.disableBlend();
    }

    private void renderFacet(BufferBuilder fb, Matrix4f m, Vector3f v0, Vector3f v1, Vector3f v2,
                             Vector3f lightDir, Vector3f specDir, int cr, int cg, int cb, int alpha) {
        Vector3f e1 = new Vector3f(v1).sub(v0);
        Vector3f e2 = new Vector3f(v2).sub(v0);
        Vector3f norm = new Vector3f(e1).cross(e2).normalize();

        float diff = Math.max(0.0f, norm.dot(lightDir));
        float spec = (float) Math.pow(Math.max(0.0f, norm.dot(specDir)), 14.0) * 0.35f;
        float brightness = Math.min(1.0f, 0.48f + 0.42f * diff + spec);

        int r = Math.min(255, (int) (cr * brightness));
        int g = Math.min(255, (int) (cg * brightness));
        int b = Math.min(255, (int) (cb * brightness));

        fb.vertex(m, v0.x, v0.y, v0.z).color(r, g, b, alpha);
        fb.vertex(m, v1.x, v1.y, v1.z).color(r, g, b, alpha);
        fb.vertex(m, v2.x, v2.y, v2.z).color(r, g, b, alpha);
    }

    private void drawEdge(BufferBuilder b, Matrix4f m, Vector3f v0, Vector3f v1, int r, int g, int bl, int a) {
        b.vertex(m, v0.x, v0.y, v0.z).color(r, g, bl, a);
        b.vertex(m, v1.x, v1.y, v1.z).color(r, g, bl, a);
    }

    private void renderSpirits(DrawEvent event, float anim, int themeColor) {
        if (this.f == null || aM_.player == null || aM_.gameRenderer == null) return;

        MatrixStack matrices = event.h();
        float tickDelta = event.g();
        Camera camera = aM_.gameRenderer.getCamera();
        Vec3d camPos = camera.getCameraPos();

        double x = this.f.getLerpedPos(tickDelta).x - camPos.x;
        double yBase = this.f.getLerpedPos(tickDelta).y - camPos.y;
        double z = this.f.getLerpedPos(tickDelta).z - camPos.z;

        float time = (float) ((System.currentTimeMillis() % 10000000L) / 1000.0);
        float alphaGlobal = anim;

        // RenderSystem.setShader(...);
        RenderStateHelper.setShaderTexture(0, GLOW_TEXTURE);
        RenderStateHelper.enableBlend();
        RenderStateHelper.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        RenderStateHelper.disableCull();
        RenderStateHelper.enableDepthTest();
        RenderStateHelper.depthFunc(GL11.GL_LEQUAL);
        RenderStateHelper.depthMask(false);

        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float rotSpeedBase = time * 9.0F;
        float vertSpeedBase = time * 3.25F;
        float entityHeight = this.f.getHeight();

        for (int i = 0; i < 3; i++) {
            float angleOffset = i * (float) (Math.PI * 2.0 / 3.0);

            for (int j = 0; j < 10; j++) {
                float trailDelay = j * 0.05F;
                float currentAngle = rotSpeedBase + angleOffset - (j * 0.1F);

                float radius = 0.725F;
                double posX = Math.cos(currentAngle) * radius;
                double posZ = Math.sin(currentAngle) * radius;

                double verticalOscillation = (Math.sin(vertSpeedBase - trailDelay) * 0.5 + 0.5) * (entityHeight * 0.975);

                matrices.push();
                matrices.translate(x + posX, yBase + verticalOscillation, z + posZ);
                matrices.multiply(camera.getRotation());
                Matrix4f matrix = matrices.peek().getPositionMatrix();

                float size = (0.30F - (j * 0.015F)) * alphaGlobal;
                if (size < 0.03F) size = 0.03F;

                float tailFade = 1.35F - (j / 13.5F);
                int a = MathHelper.clamp((int) (150 * tailFade * alphaGlobal), 0, 255);
                int color = ColorUtil.a(themeColor, a);

                buffer.vertex(matrix, -size, -size, 0).texture(0, 0).color(color);
                buffer.vertex(matrix, size, -size, 0).texture(1, 0).color(color);
                buffer.vertex(matrix, size, size, 0).texture(1, 1).color(color);
                buffer.vertex(matrix, -size, size, 0).texture(0, 1).color(color);

                matrices.pop();
            }
        }

        ImmediateBufferRenderer.draw(buffer.end());
        RenderStateHelper.enableDepthTest();
        RenderStateHelper.depthMask(true);
        RenderStateHelper.disableBlend();
        RenderStateHelper.enableCull();
        RenderStateHelper.defaultBlendFunc();
    }

    private void a(DrawEvent event, float anim) {
        Vec3d targetPos = a(this.f, event.g());
        Vector2f screen = ProjectUtil.a(targetPos.x, targetPos.y, targetPos.z);
        if (!ProjectUtil.a(screen)) {
            return;
        }
        float distance = (float) aM_.player.getEyePos().distanceTo(targetPos);
        float size = ((float) Math.max(32.0d, 48.0d - (((double) distance) * 0.7000002488091963d))) * (1.15f - (0.15f * anim));
        int themeColor = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        event.h().push();
        event.h().translate(screen.x(), screen.y(), 0.0f);
        event.h().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(((float) Math.sin(System.currentTimeMillis() / 900.0d)) * 360.0f));
        event.d().a(event.h(), TARGET_ESP_TEXTURE, (-size) / 2.0f, (-size) / 2.0f, size, size, 0.0f, ColorUtil.a(-1, anim * 0.85f));
        event.h().pop();
    }

    private Vec3d a(LivingEntity target, float delta) {
        return new Vec3d(target.getLerpedPos(delta).x, target.getLerpedPos(delta).y + (((double) target.getHeight()) / 2.0d), target.getLerpedPos(delta).z);
    }

    @EventTarget
    public void a(TickEvent event) {
        TargetESP targetESP = TargetESP.getInstance();
        LivingEntity current = (targetESP != null && targetESP.m()) ? targetESP.getTarget() : null;
        boolean changed = (current == null || this.f == null || current == this.f) ? false : true;
        boolean visible = (current == null || changed) ? false : true;
        if (visible) {
            this.f = current;
        }
        this.e.a(visible);
        if (!visible && this.e.a() <= 0.0f) {
            this.f = changed ? current : null;
        }
    }

    private void a(MatrixStack stack, Vec3d renderPos, float ringWidth, float ringScale, float moving, int color) {
        // RenderSystem.setShader(...);
        
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        Vec3d cam = aM_.getEntityRenderDispatcher().camera.getCameraPos();
        Vec3d targetCenter = this.f.getEntityPos().add(0.0d, ((double) this.f.getHeight()) / 2.0d, 0.0d);
        for (int i = 0; i < 360; i += 20) {
            float angle = (float) Math.toRadians(i + (moving * 0.3f));
            float offsetX = ((float) Math.sin(angle)) * ringWidth * ringScale;
            float offsetZ = ((float) Math.cos(angle)) * ringWidth * ringScale;
            float offsetY = 0.1f + (this.f.getHeight() * Math.abs((float) Math.sin(i)));
            Vec3d crystalPos = renderPos.add(offsetX, offsetY, offsetZ);
            stack.push();
            stack.translate(crystalPos.getX() - cam.x, crystalPos.getY() - cam.y, crystalPos.getZ() - cam.z);
            stack.multiply(new Quaternionf().rotationTo(new Vector3f(0.0f, 1.0f, 0.0f), new Vector3f((float) (targetCenter.x - crystalPos.getX()), (float) (targetCenter.y - crystalPos.getY()), (float) (targetCenter.z - crystalPos.getZ())).normalize()));
            stack.scale(0.1f, 0.1f, 0.1f);

            float[] bArr = {1.0f, 0.8f, 0.6f, 0.9f, 0.7f, 0.5f, 0.4f, 0.6f};
            Vector3f[] vecArr = {new Vector3f(0.0f, 1.5f, 0.0f), new Vector3f(0.0f, -1.5f, 0.0f), new Vector3f(1.0f, 0.0f, 0.0f), new Vector3f(-1.0f, 0.0f, 0.0f), new Vector3f(0.0f, 0.0f, 1.0f), new Vector3f(0.0f, 0.0f, -1.0f)};
            int[][] cArr = {new int[]{0, 4, 2}, new int[]{0, 3, 4}, new int[]{0, 5, 3}, new int[]{0, 2, 5}, new int[]{1, 2, 4}, new int[]{1, 4, 3}, new int[]{1, 3, 5}, new int[]{1, 5, 2}};

            int[] rgba = ColorUtil.b(color);
            int red = rgba[0];
            int green = rgba[1];
            int blue = rgba[2];
            int alpha = rgba[3];

            Matrix4f matrix = stack.peek().getPositionMatrix();
            for (int idx = 0; idx < cArr.length; idx++) {
                int[] face = cArr[idx];
                float brightness = bArr[idx];
                int shaded = (alpha << 24) | (Math.min(255, (int) (red * brightness)) << 16) | (Math.min(255, (int) (green * brightness)) << 8) | Math.min(255, (int) (blue * brightness));
                for (int v = 0; v < 3; v++) {
                    Vector3f vertex = vecArr[face[v]];
                    buffer.vertex(matrix, vertex.x, vertex.y, vertex.z).color(shaded);
                }
            }
            stack.pop();
        }
        ImmediateBufferRenderer.draw(buffer.end());
    }

    private void a(MatrixStack stack, Vec3d renderPos, float ringWidth, float ringScale, float moving, int color, float anim) {
        int[] rgba = ColorUtil.b(color);
        int red = rgba[0];
        int green = rgba[1];
        int blue = rgba[2];
        int alpha = rgba[3];
        RenderStateHelper.setShaderTexture(0, Identifier.of("delta", "pictures/bloom.png"));
        // RenderSystem.setShader(...);
        a(stack, renderPos, ringWidth, ringScale, moving, 1.5f * anim, red, green, blue, alpha);
        a(stack, renderPos, ringWidth, ringScale, moving, 0.6f * anim, red, green, blue, alpha);
        RenderStateHelper.setShaderTexture(0, 0);
    }

    private void a(MatrixStack stack, Vec3d renderPos, float ringWidth, float ringScale, float moving, float size, int red, int green, int blue, int alpha) {
        BufferBuilder buffer = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        Quaternionf cameraRotation = aM_.gameRenderer.getCamera().getRotation();
        Vec3d cam = aM_.getEntityRenderDispatcher().camera.getCameraPos();
        float half = size / 2.0f;
        for (int i = 0; i < 360; i += 20) {
            float angle = (float) Math.toRadians(i + (moving * 0.3f));
            float offsetX = ((float) Math.sin(angle)) * ringWidth * ringScale;
            float offsetZ = ((float) Math.cos(angle)) * ringWidth * ringScale;
            float offsetY = 0.1f + (this.f.getHeight() * Math.abs((float) Math.sin(i)));
            stack.push();
            stack.translate((renderPos.getX() + ((double) offsetX)) - cam.x, (renderPos.getY() + ((double) offsetY)) - cam.y, (renderPos.getZ() + ((double) offsetZ)) - cam.z);
            stack.multiply(cameraRotation);
            Matrix4f matrix = stack.peek().getPositionMatrix();
            buffer.vertex(matrix, -half, -half, 0.0f).texture(0.0f, 0.0f).color(red, green, blue, alpha);
            buffer.vertex(matrix, -half, half, 0.0f).texture(0.0f, 1.0f).color(red, green, blue, alpha);
            buffer.vertex(matrix, half, half, 0.0f).texture(1.0f, 1.0f).color(red, green, blue, alpha);
            buffer.vertex(matrix, half, -half, 0.0f).texture(1.0f, 0.0f).color(red, green, blue, alpha);
            stack.pop();
        }
        ImmediateBufferRenderer.draw(buffer.end());
    }

    private void a(MatrixStack stack, Vec3d renderPos, int color) {
        // RenderSystem.setShader(...);
        
        Matrix4f matrix = stack.peek().getPositionMatrix();
        Vec3d cam = aM_.getEntityRenderDispatcher().camera.getCameraPos();
        float height = this.f.getHeight() + 0.15f;
        float radius = this.f.getWidth() * 0.8f;
        double time = System.currentTimeMillis() % 1750.0d;
        boolean inverted = time > 875.0d;
        double progress = time / 875.0d;
        double progress2 = inverted ? progress - 1.0d : 1.0d - progress;
        double ease = progress2 < 0.5d ? 2.0d * progress2 * progress2 : 1.0d - (Math.pow(((-2.0d) * progress2) + 2.0d, 2.0d) / 2.0d);
        float y = (float) ((renderPos.getY() - cam.y) + (((double) height) * ease));
        float offset = (float) (((double) height) * 0.8000001435473696d * Math.min(ease, 1.0d - ease) * (inverted ? -1.0d : 1.0d));
        int[] c = ColorUtil.b(color);
        float r = c[0] / 255.0f;
        float g = c[1] / 255.0f;
        float b = c[2] / 255.0f;
        float a = c[3] / 255.0f;
        double cx = renderPos.getX() - cam.x;
        double cz = renderPos.getZ() - cam.z;
        BufferBuilder skirt = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int deg = 0; deg <= 360; deg++) {
            double rad = Math.toRadians(deg);
            float x = (float) (cx + (Math.cos(rad) * ((double) radius)));
            float z = (float) (cz + (Math.sin(rad) * ((double) radius)));
            skirt.vertex(matrix, x, y, z).color(r, g, b, a * 0.55f);
            skirt.vertex(matrix, x, y + offset, z).color(r, g, b, 0.0f);
        }
        ImmediateBufferRenderer.draw(skirt.end());
        BufferBuilder outline = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        for (int deg2 = 0; deg2 < 360; deg2++) {
            double a0 = Math.toRadians(deg2);
            double a1 = Math.toRadians(deg2 + 1);
            outline.vertex(matrix, (float) (cx + (Math.cos(a0) * ((double) radius))), y, (float) (cz + (Math.sin(a0) * ((double) radius)))).color(r, g, b, a);
            outline.vertex(matrix, (float) (cx + (Math.cos(a1) * ((double) radius))), y, (float) (cz + (Math.sin(a1) * ((double) radius)))).color(r, g, b, a);
        }
        ImmediateBufferRenderer.draw(outline.end());
    }

    private void b() {
        RenderStateHelper.enableBlend();
        RenderStateHelper.blendFunc(org.lwjgl.opengl.GL11.GL_SRC_ALPHA, org.lwjgl.opengl.GL11.GL_ONE);
        RenderStateHelper.disableCull();
        RenderStateHelper.enableDepthTest();
        RenderStateHelper.depthFunc(GL11.GL_LEQUAL);
        RenderStateHelper.depthMask(false);
    }

    private void c() {
        RenderStateHelper.depthMask(true);
        RenderStateHelper.enableDepthTest();
        RenderStateHelper.enableCull();
        RenderStateHelper.disableBlend();
    }

    private Vec3d a(LivingEntity target) {
        float tickDelta = MinecraftClient.getInstance().getRenderTickCounter().getTickProgress(false);
        return new Vec3d(target.getLerpedPos(tickDelta).x, target.getLerpedPos(tickDelta).y, target.getLerpedPos(tickDelta).z);
    }
}
