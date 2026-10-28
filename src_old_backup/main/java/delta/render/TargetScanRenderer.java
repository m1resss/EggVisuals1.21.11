package delta.render;

import delta.core.Delta;
import delta.config.ThemeInfo;
import delta.module.combat.AuraHandler;
import delta.module.render.TargetESP;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector3f;

import java.lang.reflect.Method;

/** Renders a single animated scan band directly on the visible player skin. */
public final class TargetScanRenderer {
    private static final float ATTRIBUTE_SCALE = 1000.0f;
    private static final float MODEL_INFLATE = 1.008f;
    private static final float ARMOR_INFLATE = 1.006f;
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static volatile boolean injectedMeshLookupComplete;
    private static volatile Method injectedMeshGetter;

    public record ScanParams(
            int color,
            int secondColor,
            float saturation,
            float animation,
            float speed,
            float glow
    ) {}

    public static boolean isTarget(LivingEntity entity) {
        if (entity == null) return false;
        TargetESP esp = TargetESP.getInstance();
        if (esp != null && esp.isEnabled() && (esp.getMode().l("Scan") || esp.getMode().l("Скан"))) {
            if (entity == esp.getTarget()) return true;
        }
        AuraHandler aura = AuraHandler.getInstance();
        if (aura != null && aura.getTarget() == entity) {
            if (esp != null && esp.isEnabled() && (esp.getMode().l("Scan") || esp.getMode().l("Скан"))) {
                return true;
            }
        }
        return false;
    }

    public static ScanParams getParams() {
        TargetESP esp = TargetESP.getInstance();
        if (esp == null || !esp.isEnabled() || (!esp.getMode().l("Scan") && !esp.getMode().l("Скан"))) {
            return null;
        }
        int primary = Delta.h().d().o().a(ThemeInfo.PRIMARY).a();
        int secondary = ColorUtil.b(primary, 0.45f);
        float speed = esp.getScanSpeed() != null ? esp.getScanSpeed().c().floatValue() : 1.2f;
        float glow = esp.getScanGlow() != null ? esp.getScanGlow().c().floatValue() : 0.8f;
        float anim = (float) ((System.currentTimeMillis() % 10000000L) / 1000.0);
        return new ScanParams(primary, secondary, 1.0f, anim, speed, glow);
    }

    private TargetScanRenderer() {
    }

    public static <S extends LivingEntityRenderState> void submit(
            EntityModel<? super S> model,
            S state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            Identifier texture,
            ScanParams params) {
        if (params == null) return;
        submit(
                model,
                state,
                matrices,
                vertexConsumers,
                texture,
                params.color(),
                params.secondColor(),
                params.saturation(),
                params.animation(),
                params.speed(),
                params.glow());
    }

    public static <S extends BipedEntityRenderState> void submitArmor(
            BipedEntityModel<S> model,
            S state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            Identifier armorTexture,
            EquipmentSlot armorSlot,
            ScanParams params) {
        if (params == null) return;
        submitArmor(
                model,
                state,
                matrices,
                vertexConsumers,
                armorTexture,
                armorSlot,
                params.color(),
                params.secondColor(),
                params.saturation(),
                params.animation(),
                params.speed(),
                params.glow());
    }

    public static <S extends LivingEntityRenderState> void submit(
            EntityModel<? super S> model,
            S state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            Identifier texture,
            int themeColor,
            int secondaryColor,
            float saturation,
            float animation,
            float speed,
            float glow) {
        submitSurface(
                model,
                state,
                matrices,
                vertexConsumers,
                texture,
                themeColor,
                secondaryColor,
                saturation,
                animation,
                speed,
                glow,
                null);
    }

    public static <S extends BipedEntityRenderState> void submitArmor(
            BipedEntityModel<S> model,
            S state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            Identifier armorTexture,
            EquipmentSlot armorSlot,
            int themeColor,
            int secondaryColor,
            float saturation,
            float animation,
            float speed,
            float glow) {
        submitSurface(
                model,
                state,
                matrices,
                vertexConsumers,
                armorTexture,
                themeColor,
                secondaryColor,
                saturation,
                animation,
                speed,
                glow,
                armorSlot);
    }

    private static <S extends LivingEntityRenderState> void submitSurface(
            EntityModel<? super S> model,
            S state,
            MatrixStack matrices,
            VertexConsumerProvider vertexConsumers,
            Identifier texture,
            int themeColor,
            int secondaryColor,
            float saturation,
            float animation,
            float speed,
            float glow,
            EquipmentSlot armorSlot) {
        if (model == null || state == null || matrices == null || vertexConsumers == null
                || texture == null || animation <= 0.001f) {
            return;
        }

        int color = saturate(themeColor, saturation);
        int red = (color >>> 16) & 255;
        int green = (color >>> 8) & 255;
        int blue = color & 255;
        int secondColor = saturate(secondaryColor, saturation);
        int secondRed = (secondColor >>> 16) & 255;
        int secondGreen = (secondColor >>> 8) & 255;
        int secondBlue = secondColor & 255;
        float appear = MathHelper.clamp(animation, 0.0f, 1.0f);
        float glowStrength = MathHelper.clamp(glow, 0.0f, 2.0f);
        ScanMotion motion = motion(speed);

        matrices.push();
        try {
            boolean armorSurface = armorSlot != null;
            float inflate = armorSurface ? ARMOR_INFLATE : MODEL_INFLATE;
            matrices.scale(inflate, inflate, inflate);

            if (!armorSurface) {
                model.setAngles(state);
            }
            BaseLayerVisibility visibility = BaseLayerVisibility.hide(model);
            try {
                BoundsConsumer bounds = new BoundsConsumer();
                renderSurfaceModel(model, matrices, bounds, armorSlot);
                if (armorSurface) {
                    bounds.useHumanoidReference(matrices.peek());
                }
                if (!bounds.valid()) {
                    return;
                }

                VertexConsumer buffer = vertexConsumers.getBuffer(DeltaWorldRenderTypes.targetEspScan(texture));
                VertexConsumer scan = new ScanVertexConsumer(
                        buffer,
                        bounds,
                        red,
                        green,
                        blue,
                        secondRed,
                        secondGreen,
                        secondBlue,
                        motion.phase(),
                        motion.ascending(),
                        appear,
                        glowStrength);
                renderSurfaceModel(model, matrices, scan, armorSlot);
            } finally {
                visibility.restore();
            }
        } finally {
            matrices.pop();
        }
    }

    private static ScanMotion motion(float speed) {
        double seconds = (System.nanoTime() % 120_000_000_000L) / 1_000_000_000.0;
        double duration = 2.8 / Math.max(0.05, speed);
        float progress = (float) ((seconds % duration) / duration);
        float phase = 0.5f - (0.5f * MathHelper.cos(progress * (MathHelper.PI * 2.0f)));
        return new ScanMotion(phase, progress < 0.5f);
    }

    private static void renderSurfaceModel(
            EntityModel<?> model,
            MatrixStack matrices,
            VertexConsumer buffer,
            EquipmentSlot armorSlot) {
        if (model instanceof BipedEntityModel<?> humanoid) {
            if (armorSlot != null) {
                renderArmorSlot(humanoid, armorSlot, matrices, buffer);
                return;
            }
            renderPart(humanoid.head, matrices, buffer);
            renderPart(humanoid.body, matrices, buffer);
            renderPart(humanoid.rightArm, matrices, buffer);
            renderPart(humanoid.leftArm, matrices, buffer);
            renderPart(humanoid.rightLeg, matrices, buffer);
            renderPart(humanoid.leftLeg, matrices, buffer);
            return;
        }
        model.render(matrices, buffer, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, -1);
    }

    private static void renderArmorSlot(
            BipedEntityModel<?> model,
            EquipmentSlot slot,
            MatrixStack matrices,
            VertexConsumer buffer) {
        switch (slot) {
            case HEAD -> renderPart(model.head, matrices, buffer);
            case CHEST -> {
                renderPart(model.body, matrices, buffer);
                renderPart(model.rightArm, matrices, buffer);
                renderPart(model.leftArm, matrices, buffer);
            }
            case LEGS -> {
                renderPart(model.body, matrices, buffer);
                renderPart(model.rightLeg, matrices, buffer);
                renderPart(model.leftLeg, matrices, buffer);
            }
            case FEET -> {
                renderPart(model.rightLeg, matrices, buffer);
                renderPart(model.leftLeg, matrices, buffer);
            }
            default -> {
            }
        }
    }

    private static void renderPart(ModelPart part, MatrixStack matrices, VertexConsumer buffer) {
        part.render(matrices, buffer, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, -1);
    }

    /**
     * Skin Layers 3D injects its voxel mesh into the vanilla outer ModelPart.
     * Reflection keeps the compatibility optional and avoids a hard dependency.
     */
    private static boolean hasInjectedSkinMesh(ModelPart part) {
        if (!injectedMeshLookupComplete) {
            synchronized (TargetScanRenderer.class) {
                if (!injectedMeshLookupComplete) {
                    try {
                        injectedMeshGetter = ModelPart.class.getMethod("getInjectedMesh");
                    } catch (NoSuchMethodException ignored) {
                        injectedMeshGetter = null;
                    }
                    injectedMeshLookupComplete = true;
                }
            }
        }

        Method getter = injectedMeshGetter;
        if (getter == null) {
            return false;
        }
        try {
            return getter.invoke(part) != null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private static int saturate(int color, float factor) {
        float red = ((color >>> 16) & 255) / 255.0f;
        float green = ((color >>> 8) & 255) / 255.0f;
        float blue = (color & 255) / 255.0f;
        float luminance = (red * 0.2126f) + (green * 0.7152f) + (blue * 0.0722f);
        float amount = MathHelper.clamp(factor, 0.0f, 2.0f);
        int outRed = Math.round(MathHelper.clamp(luminance + ((red - luminance) * amount), 0.0f, 1.0f) * 255.0f);
        int outGreen = Math.round(MathHelper.clamp(luminance + ((green - luminance) * amount), 0.0f, 1.0f) * 255.0f);
        int outBlue = Math.round(MathHelper.clamp(luminance + ((blue - luminance) * amount), 0.0f, 1.0f) * 255.0f);
        return (color & 0xFF000000) | (outRed << 16) | (outGreen << 8) | outBlue;
    }

    private record ScanMotion(float phase, boolean ascending) {
    }

    private static final class BaseLayerVisibility {
        private final BipedEntityModel<?> humanoid;
        private final boolean hat;
        private final PlayerEntityModel player;
        private final boolean jacket;
        private final boolean leftSleeve;
        private final boolean rightSleeve;
        private final boolean leftPants;
        private final boolean rightPants;

        private BaseLayerVisibility(EntityModel<?> model) {
            this.humanoid = model instanceof BipedEntityModel<?> value ? value : null;
            this.hat = this.humanoid != null && this.humanoid.hat.visible;
            this.player = model instanceof PlayerEntityModel value ? value : null;
            this.jacket = this.player != null && this.player.jacket.visible;
            this.leftSleeve = this.player != null && this.player.leftSleeve.visible;
            this.rightSleeve = this.player != null && this.player.rightSleeve.visible;
            this.leftPants = this.player != null && this.player.leftPants.visible;
            this.rightPants = this.player != null && this.player.rightPants.visible;
        }

        static BaseLayerVisibility hide(EntityModel<?> model) {
            BaseLayerVisibility visibility = new BaseLayerVisibility(model);
            if (visibility.humanoid != null) {
                visibility.humanoid.hat.visible = visibility.hat
                        && hasInjectedSkinMesh(visibility.humanoid.hat);
            }
            if (visibility.player != null) {
                visibility.player.jacket.visible = visibility.jacket
                        && hasInjectedSkinMesh(visibility.player.jacket);
                visibility.player.leftSleeve.visible = visibility.leftSleeve
                        && hasInjectedSkinMesh(visibility.player.leftSleeve);
                visibility.player.rightSleeve.visible = visibility.rightSleeve
                        && hasInjectedSkinMesh(visibility.player.rightSleeve);
                visibility.player.leftPants.visible = visibility.leftPants
                        && hasInjectedSkinMesh(visibility.player.leftPants);
                visibility.player.rightPants.visible = visibility.rightPants
                        && hasInjectedSkinMesh(visibility.player.rightPants);
            }
            return visibility;
        }

        void restore() {
            if (this.humanoid != null) {
                this.humanoid.hat.visible = this.hat;
            }
            if (this.player != null) {
                this.player.jacket.visible = this.jacket;
                this.player.leftSleeve.visible = this.leftSleeve;
                this.player.rightSleeve.visible = this.rightSleeve;
                this.player.leftPants.visible = this.leftPants;
                this.player.rightPants.visible = this.rightPants;
            }
        }
    }

    private static final class BoundsConsumer implements VertexConsumer {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;

        boolean valid() {
            return Float.isFinite(this.minX) && Float.isFinite(this.minY)
                    && this.maxX - this.minX > 0.0001f
                    && this.maxY - this.minY > 0.0001f;
        }

        void useHumanoidReference(MatrixStack.Entry entry) {
            Vector3f top = entry.getPositionMatrix().transformPosition(0.0f, -0.58f, 0.0f, new Vector3f());
            Vector3f bottom = entry.getPositionMatrix().transformPosition(0.0f, 1.58f, 0.0f, new Vector3f());
            float referenceMinY = Math.min(top.y, bottom.y);
            float referenceMaxY = Math.max(top.y, bottom.y);
            if (referenceMaxY - referenceMinY > 0.05f) {
                this.minY = referenceMinY;
                this.maxY = referenceMaxY;
            }
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.maxX = Math.max(this.maxX, x);
            this.maxY = Math.max(this.maxY, y);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer color(int color) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            return this;
        }
    }

    private static final class ScanVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final BoundsConsumer bounds;
        private final int red;
        private final int green;
        private final int blue;
        private final int phase;
        private final int motion;
        private final int glow;
        private float x;
        private float y;

        private ScanVertexConsumer(
                VertexConsumer delegate,
                BoundsConsumer bounds,
                int red,
                int green,
                int blue,
                int secondRed,
                int secondGreen,
                int secondBlue,
                float phase,
                boolean ascending,
                float appear,
                float glow) {
            this.delegate = delegate;
            this.bounds = bounds;
            this.red = red;
            this.green = green;
            this.blue = blue;
            int phaseByte = Math.round(MathHelper.clamp(phase, 0.0f, 1.0f) * 255.0f);
            int appearByte = Math.round(MathHelper.clamp(appear, 0.0f, 1.0f) * 127.0f);
            int motionByte = (ascending ? 0 : 128) | appearByte;
            int glowByte = Math.round(MathHelper.clamp(glow, 0.0f, 2.0f) * 127.5f);
            this.phase = ((secondRed & 255) << 8) | phaseByte;
            this.motion = ((secondGreen & 255) << 8) | motionByte;
            this.glow = ((secondBlue & 255) << 8) | glowByte;
        }

        @Override
        public VertexConsumer vertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this.setScanColor();
        }

        @Override
        public VertexConsumer color(int color) {
            return this.setScanColor();
        }

        private VertexConsumer setScanColor() {
            float height = normalize(this.y, this.bounds.minY, this.bounds.maxY);
            this.delegate.color(this.red, this.green, this.blue, Math.round(height * 255.0f));
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            this.delegate.texture(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            this.delegate.overlay(this.phase, this.motion);
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            float horizontal = normalize(this.x, this.bounds.minX, this.bounds.maxX);
            this.delegate.light(this.glow, Math.round(horizontal * ATTRIBUTE_SCALE));
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            this.delegate.normal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer lineWidth(float width) {
            this.delegate.lineWidth(width);
            return this;
        }

        private static float normalize(float value, float minimum, float maximum) {
            return MathHelper.clamp((value - minimum) / Math.max(0.0001f, maximum - minimum), 0.0f, 1.0f);
        }
    }
}
