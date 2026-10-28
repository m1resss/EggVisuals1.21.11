package egg.client.module.combat;

import egg.client.mixin.OverlayTextureAccessor;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ColorSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;

public class HitColor extends Module {
    public static final int VANILLA_HURT_COLOR = -1291911168;

    private final ColorSetting color = new ColorSetting("Цвет", 0xB2FF2A2A, "Цвет вспышки при получении урона");
    private final BooleanSetting colorArmor = new BooleanSetting("Окрашивать броню", true, "Окрашивать броню при получении урона в цвет вспышки");

    private int lastUploadedColor = VANILLA_HURT_COLOR;

    public HitColor() {
        super("HitColor", "Кастомный цвет вспышки при получении урона", Category.COMBAT);
        addSettings(color, colorArmor);
    }

    public ColorSetting getColorSetting() {
        return color;
    }

    public boolean isColorArmor() {
        return colorArmor.isEnabled();
    }

    public int getTargetColor() {
        if (!isEnabled()) {
            return VANILLA_HURT_COLOR;
        }
        int c = color.get();
        int a = (c >> 24) & 0xFF;
        if (a == 0) {
            a = 0xB2;
        }
        return (a << 24) | (c & 0x00FFFFFF);
    }

    public void updateOverlay(OverlayTexture overlayTexture) {
        if (overlayTexture == null) return;
        int target = getTargetColor();
        if (target == lastUploadedColor) return;

        try {
            NativeImageBackedTexture tex = ((OverlayTextureAccessor) overlayTexture).getTexture();
            if (tex == null) return;
            NativeImage img = tex.getImage();
            if (img == null) return;

            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 16; x++) {
                    img.setColorArgb(x, y, target);
                }
            }
            tex.upload();
            lastUploadedColor = target;
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.gameRenderer != null) {
            updateOverlay(mc.gameRenderer.getOverlayTexture());
        }
    }
}
