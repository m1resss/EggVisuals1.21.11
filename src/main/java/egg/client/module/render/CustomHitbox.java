package egg.client.module.render;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.theme.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class CustomHitbox extends Module {

    private final ModeSetting colorMode = new ModeSetting("Режим цвета", "Тема", "Источник цвета хитбоксов", "Тема", "Кастомный");

    private final BooleanSetting fill = new BooleanSetting("Заливка", true, "Отображать сплошные грани хитбокса");
    private final SliderSetting fillAlpha = new SliderSetting("Прозрачность заливки", 0.35, 0.05, 1.00, 0.05, "Степень прозрачности заливки граней");
    private final ColorSetting fillColor = new ColorSetting("Цвет заливки", 0xFFFFFFFF, "Пользовательский цвет заливки граней");

    private final BooleanSetting outline = new BooleanSetting("Обводка", true, "Отображать контурные линии хитбокса");
    private final SliderSetting outlineAlpha = new SliderSetting("Прозрачность обводки", 1.00, 0.05, 1.00, 0.05, "Степень прозрачности контурных линий");
    private final ColorSetting outlineColor = new ColorSetting("Цвет обводки", 0xFFFFFFFF, "Пользовательский цвет контура");
    private final SliderSetting outlineWidth = new SliderSetting("Толщина обводки", 1.5, 0.5, 4.0, 0.5, "Толщина линий контура");

    private final BooleanSetting players = new BooleanSetting("Игроки", true, "Отображать хитбоксы других игроков");
    private final BooleanSetting mobs = new BooleanSetting("Мобы", false, "Отображать хитбоксы мобов и существ");
    private final BooleanSetting self = new BooleanSetting("Себя", false, "Отображать свой хитбокс при виде от третьего лица");

    public CustomHitbox() {
        super("CustomHitbox", "Настраиваемое 3D выделение хитбоксов сущностей", Category.ESP);

        fillAlpha.setVisible(() -> fill.get());
        fillColor.setVisible(() -> fill.get() && colorMode.is("Кастомный"));

        outlineAlpha.setVisible(() -> outline.get());
        outlineColor.setVisible(() -> outline.get() && colorMode.is("Кастомный"));
        outlineWidth.setVisible(() -> outline.get());

        addSettings(colorMode, fill, fillAlpha, fillColor, outline, outlineAlpha, outlineColor, outlineWidth, players, mobs, self);
    }

    public void render(VertexConsumerProvider consumers, Vec3d camPos, float tickDelta) {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        VertexConsumer quadBuffer = consumers.getBuffer(RenderLayers.debugQuads());

        boolean isTheme = colorMode.is("Тема");
        int themeCol = Theme.getAccent();

        int fColor = isTheme ? themeCol : fillColor.get();
        int fR = (fColor >> 16) & 0xFF;
        int fG = (fColor >> 8) & 0xFF;
        int fB = fColor & 0xFF;
        int fA = (int) MathHelper.clamp(fillAlpha.get() * 255.0, 0, 255);

        int oColor = isTheme ? themeCol : outlineColor.get();
        int oR = (oColor >> 16) & 0xFF;
        int oG = (oColor >> 8) & 0xFF;
        int oB = oColor & 0xFF;
        int oA = (int) MathHelper.clamp(outlineAlpha.get() * 255.0, 0, 255);

        float width = (float) outlineWidth.get();
        boolean doFill = fill.get();
        boolean doOutline = outline.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!entity.isAlive() || entity.isSpectator()) continue;
            if (entity.squaredDistanceTo(mc.player) > 64.0 * 64.0) continue;

            if (entity instanceof PlayerEntity player) {
                if (player == mc.player) {
                    if (!self.get() || mc.options.getPerspective().isFirstPerson()) continue;
                } else {
                    if (!players.get()) continue;
                }
            } else if (entity instanceof LivingEntity) {
                if (!mobs.get()) continue;
            } else {
                continue;
            }

            Box box = getInterpolatedBoundingBox(entity, tickDelta);
            renderHitbox(quadBuffer, box, camPos, doFill, fR, fG, fB, fA, doOutline, oR, oG, oB, oA, width);
        }
    }

    public static Box getInterpolatedBoundingBox(Entity entity, float tickDelta) {
        if (entity == null) return new Box(0, 0, 0, 0, 0, 0);
        double dx = MathHelper.lerp(tickDelta, entity.lastRenderX, entity.getX()) - entity.getX();
        double dy = MathHelper.lerp(tickDelta, entity.lastRenderY, entity.getY()) - entity.getY();
        double dz = MathHelper.lerp(tickDelta, entity.lastRenderZ, entity.getZ()) - entity.getZ();
        return entity.getBoundingBox().offset(dx, dy, dz);
    }

    public static void renderHitbox(VertexConsumer buf, Box box, Vec3d camPos,
                                    boolean doFill, int fR, int fG, int fB, int fA,
                                    boolean doOutline, int oR, int oG, int oB, int oA,
                                    float outWidth) {
        if (box == null || buf == null) return;
        if (!doFill && !doOutline) return;

        float x1 = (float) (box.minX - camPos.x);
        float y1 = (float) (box.minY - camPos.y);
        float z1 = (float) (box.minZ - camPos.z);
        float x2 = (float) (box.maxX - camPos.x);
        float y2 = (float) (box.maxY - camPos.y);
        float z2 = (float) (box.maxZ - camPos.z);

        if (x1 > x2) { float t = x1; x1 = x2; x2 = t; }
        if (y1 > y2) { float t = y1; y1 = y2; y2 = t; }
        if (z1 > z2) { float t = z1; z1 = z2; z2 = t; }

        if (doFill && fA > 0) {
            drawQuadDoubleSided(buf, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, fR, fG, fB, fA);
            drawQuadDoubleSided(buf, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, fR, fG, fB, fA);
            drawQuadDoubleSided(buf, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, fR, fG, fB, fA);
            drawQuadDoubleSided(buf, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, fR, fG, fB, fA);
            drawQuadDoubleSided(buf, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, fR, fG, fB, fA);
            drawQuadDoubleSided(buf, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, fR, fG, fB, fA);
        }

        if (doOutline && oA > 0) {
            float t = Math.max(0.003f, outWidth * 0.006f);

            drawSolidBox(buf, x1 - t, y1 - t, z1 - t, x2 + t, y1 + t, z1 + t, oR, oG, oB, oA);
            drawSolidBox(buf, x1 - t, y1 - t, z2 - t, x2 + t, y1 + t, z2 + t, oR, oG, oB, oA);
            drawSolidBox(buf, x1 - t, y2 - t, z1 - t, x2 + t, y2 + t, z1 + t, oR, oG, oB, oA);
            drawSolidBox(buf, x1 - t, y2 - t, z2 - t, x2 + t, y2 + t, z2 + t, oR, oG, oB, oA);

            drawSolidBox(buf, x1 - t, y1, z1 - t, x1 + t, y2, z1 + t, oR, oG, oB, oA);
            drawSolidBox(buf, x2 - t, y1, z1 - t, x2 + t, y2, z1 + t, oR, oG, oB, oA);
            drawSolidBox(buf, x1 - t, y1, z2 - t, x1 + t, y2, z2 + t, oR, oG, oB, oA);
            drawSolidBox(buf, x2 - t, y1, z2 - t, x2 + t, y2, z2 + t, oR, oG, oB, oA);

            drawSolidBox(buf, x1 - t, y1 - t, z1, x1 + t, y1 + t, z2, oR, oG, oB, oA);
            drawSolidBox(buf, x2 - t, y1 - t, z1, x2 + t, y1 + t, z2, oR, oG, oB, oA);
            drawSolidBox(buf, x1 - t, y2 - t, z1, x1 + t, y2 + t, z2, oR, oG, oB, oA);
            drawSolidBox(buf, x2 - t, y2 - t, z1, x2 + t, y2 + t, z2, oR, oG, oB, oA);
        }
    }

    private static void drawSolidBox(VertexConsumer buf,
                                     float x1, float y1, float z1,
                                     float x2, float y2, float z2,
                                     int r, int g, int b, int a) {
        drawQuadDoubleSided(buf, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, r, g, b, a);
        drawQuadDoubleSided(buf, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
        drawQuadDoubleSided(buf, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1, r, g, b, a);
        drawQuadDoubleSided(buf, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, r, g, b, a);
        drawQuadDoubleSided(buf, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, r, g, b, a);
        drawQuadDoubleSided(buf, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2, r, g, b, a);
    }

    private static void drawQuadDoubleSided(VertexConsumer buf,
                                            float x1, float y1, float z1,
                                            float x2, float y2, float z2,
                                            float x3, float y3, float z3,
                                            float x4, float y4, float z4,
                                            int r, int g, int b, int a) {
        buf.vertex(x1, y1, z1).color(r, g, b, a);
        buf.vertex(x2, y2, z2).color(r, g, b, a);
        buf.vertex(x3, y3, z3).color(r, g, b, a);
        buf.vertex(x4, y4, z4).color(r, g, b, a);

        buf.vertex(x4, y4, z4).color(r, g, b, a);
        buf.vertex(x3, y3, z3).color(r, g, b, a);
        buf.vertex(x2, y2, z2).color(r, g, b, a);
        buf.vertex(x1, y1, z1).color(r, g, b, a);
    }
}
