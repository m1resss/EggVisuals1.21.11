package delta.module.combat;

import static delta.core.Interface.aM_;
import delta.core.Category;
import delta.core.Delta;
import delta.core.EventManager;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.TickEvent;
import delta.setting.BindSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;
import delta.ui.screen.SwapScreen;
import delta.util.InventoryUtil;
import lombok.Generated;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import platform.inject.accessors.HandledScreenAccessor;

@ModuleRegister(a = "Auto Swap", b = "Легитно открывает инвентарь, наводится на предмет и меняет его в левую руку по клавише F", c = Category.Combat)
public class AutoSwap extends Module implements Interface {
    private final ModeSetting b = new ModeSetting("Режим перемещения", "Двойной", "Двойной", "Тройной");
    private final ModeSetting c = (ModeSetting) new ModeSetting("Первый предмет", "Сфера", "Сфера", "Тотем", "Золотое яблоко", "Щит").a(() -> {
        return Boolean.valueOf(this.b.l("Двойной"));
    });
    private final ModeSetting d = (ModeSetting) new ModeSetting("Второй предмет", "Тотем", "Сфера", "Тотем", "Золотое яблоко", "Щит").a(() -> {
        return Boolean.valueOf(this.b.l("Двойной"));
    });
    private final SliderSetting delay = new SliderSetting("Задержка (тики)", 1.0f, 1.0f, 4.0f, 1.0f);
    private final SwapScreen e = new SwapScreen(Text.literal("SwapMenu"));

    private enum Stage {
        IDLE,
        OPENING,
        SWAPPING
    }

    private static Stage stage = Stage.IDLE;
    private static int targetSlot = -1;
    private static int timer = 0;

    private final BindSetting f = new BindSetting("Кнопка перемещения", 86, 0).a(() -> {
        if (this.b.l("Двойной")) {
            Item first = a(this.c);
            Item second = a(this.d);
            if (first != null && second != null) {
                Item target = aM_.player.getOffHandStack().getItem() == first ? second : first;
                startSwap(target);
            }
        } else if (this.b.l("Тройной")) {
            aM_.setScreen(this.e);
        }
    }).b(() -> {
        if (aM_.currentScreen instanceof SwapScreen) {
            aM_.setScreen((Screen) null);
        }
    });

    public AutoSwap() {
        a(this.f, this.b, this.c, this.d, this.delay);
        EventManager.a(this);
    }

    public static int findTargetSlot(Item item) {
        if (aM_.player == null || item == null) {
            return -1;
        }
        int invSlot = InventoryUtil.c(item);
        if (invSlot != -1) {
            if (invSlot >= 0 && invSlot < 9) {
                return 36 + invSlot;
            }
            if (invSlot >= 9 && invSlot < 36) {
                return invSlot;
            }
        }
        for (int i = 9; i < 45; i++) {
            ItemStack stack = aM_.player.playerScreenHandler.getSlot(i).getStack();
            if (!stack.isEmpty() && stack.getItem() == item) {
                return i;
            }
        }
        return -1;
    }

    public static boolean startSwap(Item targetItem) {
        if (aM_.player == null || aM_.interactionManager == null || targetItem == null) {
            return false;
        }
        if (stage != Stage.IDLE) {
            return false;
        }

        int slot = findTargetSlot(targetItem);
        if (slot == -1) {
            return false;
        }

        targetSlot = slot;
        timer = 0;
        stage = Stage.OPENING;

        if (aM_.player.isSprinting()) {
            aM_.player.setSprinting(false);
        }

        if (!(aM_.currentScreen instanceof InventoryScreen)) {
            aM_.setScreen(new InventoryScreen(aM_.player));
        }
        aimAtSlot(targetSlot);
        return true;
    }

    public static boolean performLegitSwap(Item targetItem) {
        return startSwap(targetItem);
    }

    private static void aimAtSlot(int slotIndex) {
        if (aM_.currentScreen instanceof InventoryScreen screen && aM_.player != null) {
            try {
                if (slotIndex >= 0 && slotIndex < aM_.player.playerScreenHandler.slots.size()) {
                    Slot slot = aM_.player.playerScreenHandler.getSlot(slotIndex);
                    int guiX = ((HandledScreenAccessor) screen).getX();
                    int guiY = ((HandledScreenAccessor) screen).getY();
                    double cursorX = (guiX + slot.x + 8.0) * aM_.getWindow().getScaleFactor();
                    double cursorY = (guiY + slot.y + 8.0) * aM_.getWindow().getScaleFactor();
                    GLFW.glfwSetCursorPos(aM_.getWindow().getHandle(), cursorX, cursorY);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @EventTarget
    public void a(TickEvent event) {
        if (stage == Stage.IDLE || aM_.player == null || aM_.interactionManager == null) {
            return;
        }

        if (!(aM_.currentScreen instanceof InventoryScreen)) {
            stage = Stage.IDLE;
            targetSlot = -1;
            timer = 0;
            return;
        }

        timer++;
        int waitTicks = Math.max(1, (int) this.delay.c().floatValue());

        if (stage == Stage.OPENING) {
            aimAtSlot(targetSlot);
            if (timer >= waitTicks) {
                aM_.interactionManager.clickSlot(
                    aM_.player.playerScreenHandler.syncId,
                    targetSlot,
                    40,
                    SlotActionType.SWAP,
                    aM_.player
                );
                stage = Stage.SWAPPING;
                timer = 0;
            }
        } else if (stage == Stage.SWAPPING) {
            if (timer >= waitTicks) {
                stage = Stage.IDLE;
                targetSlot = -1;
                timer = 0;
                aM_.player.closeHandledScreen();
                aM_.setScreen((Screen) null);
            }
        }
    }

    @Override
    public void c() {
        super.c();
        stage = Stage.IDLE;
        targetSlot = -1;
        timer = 0;
    }

    @Generated
    public SwapScreen q() {
        return this.e;
    }

    private Item a(ModeSetting modeSetting) {
        switch (modeSetting.c()) {
            case "Сфера":
                return Items.PLAYER_HEAD;
            case "Тотем":
                return Items.TOTEM_OF_UNDYING;
            case "Золотое яблоко":
                return Items.GOLDEN_APPLE;
            case "Щит":
                return Items.SHIELD;
            default:
                return null;
        }
    }
}
