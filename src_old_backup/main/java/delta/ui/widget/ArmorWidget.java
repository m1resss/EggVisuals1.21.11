package delta.ui.widget;

import delta.render.ScaleUtil;
import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.core.InterfaceC0020Opcode;

import delta.core.Interface;
import delta.event.DrawEvent;
import delta.ui.element.DragInfo;
import delta.ui.widget.Widget;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.Arm;
import net.minecraft.item.ItemStack;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.util.Identifier;

public class ArmorWidget extends Widget implements Interface {
    public ArmorWidget() {
        super(new DragInfo("Броня", 0.0f, 0.0f, 0.0f, 0.0f));
        j().a(this);
    }

    @Override
    public void a(DrawEvent event) {
        if (event.b() && !aM_.options.hudHidden && !aM_.player.isSpectator()) {
            EquipmentSlot[] armorSlots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
            int count = 0;
            for (EquipmentSlot slot : armorSlots) {
                if (!aM_.player.getEquippedStack(slot).isEmpty()) {
                    count++;
                }
            }
            if (count > 0) {
                ScaleUtil.b(event.i());
                event.i().getMatrices().pushMatrix();
                event.i().getMatrices().translate(0.0f, (-16.0f) * Delta.h().d().t().Q().s().c());
                int startX = ((aM_.getWindow().getScaledWidth() / 2) - 91) + InterfaceC0020Opcode.bJ + 4;
                int startY = aM_.getWindow().getScaledHeight() - 22;
                int epta = startX + ((aM_.player.getMainArm() != Arm.LEFT || aM_.player.getOffHandStack().isEmpty()) ? 0 : 30);
                event.i().drawGuiTexture(RenderPipelines.GUI_TEXTURED, Identifier.ofVanilla("hud/hotbar"), InterfaceC0020Opcode.bJ, 22, 0, 0, epta, startY, (count * 20) + 1, 22);
                int index = 0;
                for (EquipmentSlot slot2 : armorSlots) {
                    ItemStack stack = aM_.player.getEquippedStack(slot2);
                    if (!stack.isEmpty()) {
                        int x = epta + 3 + (index * 20);
                        int y = startY + 3;
                        event.i().drawItem(stack, x, y);
                        event.i().drawStackOverlay(aM_.textRenderer, stack, x, y);
                        index++;
                    }
                }
                event.i().getMatrices().popMatrix();
                ScaleUtil.c(event.i());
            }
        }
        super.a(event);
    }
}
