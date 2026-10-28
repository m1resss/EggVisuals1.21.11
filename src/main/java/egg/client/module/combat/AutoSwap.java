package egg.client.module.combat;

import egg.client.mixin.HandledScreenAccessor;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.ui.screen.SwapScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

public class AutoSwap extends Module {
    private static AutoSwap instance;

    public static AutoSwap getInstance() {
        return instance;
    }

    private final ModeSetting mode = new ModeSetting("Режим перемещения", "Двойной", "Режим свапа", "Двойной", "Тройной");
    private final ModeSetting firstItem = new ModeSetting("Первый предмет", "Сфера", "Первый предмет", "Сфера", "Тотем", "Золотое яблоко", "Щит");
    private final ModeSetting secondItem = new ModeSetting("Второй предмет", "Тотем", "Второй предмет", "Сфера", "Тотем", "Золотое яблоко", "Щит");
    private final SliderSetting delay = new SliderSetting("Задержка (тики)", 1.0, 1.0, 4.0, 1.0, "Задержка между тиками");

    private final SwapScreen swapScreen = new SwapScreen(Text.literal("SwapMenu"));

    // По дефолту слоты пустые
    private final ItemStack[] wheelSlots = new ItemStack[]{
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY
    };

    private enum Stage {
        IDLE,
        OPENING,
        SWAPPING
    }

    private static Stage stage = Stage.IDLE;
    private static int targetSlot = -1;
    private static int timer = 0;

    public AutoSwap() {
        super("AutoSwap", "Легитно открывает инвентарь, наводится на предмет и меняет его в левую руку", Category.COMBAT);
        instance = this;
        addSettings(this.mode, this.firstItem, this.secondItem, this.delay);
        setKey(GLFW.GLFW_KEY_V); // Default bind V (86)
    }

    public SwapScreen getSwapScreen() {
        return this.swapScreen;
    }

    public ItemStack[] getWheelSlots() {
        return this.wheelSlots;
    }

    public ItemStack getWheelSlot(int index) {
        if (index >= 0 && index < 3) return this.wheelSlots[index];
        return ItemStack.EMPTY;
    }

    public void setWheelSlot(int index, ItemStack stack) {
        if (index >= 0 && index < 3) {
            this.wheelSlots[index] = (stack != null && !stack.isEmpty()) ? stack.copy() : ItemStack.EMPTY;
        }
    }

    public void triggerSwap() {
        if (!isEnabled()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;

        if ("Двойной".equals(this.mode.get())) {
            Item first = getItemByMode(this.firstItem.get());
            Item second = getItemByMode(this.secondItem.get());
            if (first != null && second != null) {
                ItemStack offhand = mc.player.getOffHandStack();
                Item target;

                if (first == Items.PLAYER_HEAD && second == Items.PLAYER_HEAD) {
                    int altSlot = findAlternativeSphereSlot(mc, offhand);
                    if (altSlot != -1) {
                        startSwapSlot(altSlot);
                        return;
                    }
                    target = Items.PLAYER_HEAD;
                } else {
                    target = isMatchingItem(offhand, first) ? second : first;
                }
                startSwap(new ItemStack(target));
            }
        } else if ("Тройной".equals(this.mode.get())) {
            mc.setScreen(this.swapScreen);
        }
    }

    public static int findTargetSlot(ItemStack targetStack) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || targetStack == null || targetStack.isEmpty()) return -1;

        // 1. Сначала проверяем хотбар (слоты 36-44 в playerScreenHandler)
        for (int i = 36; i <= 44; i++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (!stack.isEmpty() && isMatching(stack, targetStack)) {
                return i;
            }
        }

        // 2. Затем основной инвентарь (слоты 9-35 в playerScreenHandler)
        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (!stack.isEmpty() && isMatching(stack, targetStack)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean isMatching(ItemStack inInv, ItemStack target) {
        if (inInv == null || target == null || inInv.isEmpty() || target.isEmpty()) return false;
        if (inInv.getItem() != target.getItem()) return false;

        if ((target.isOf(Items.GOLDEN_APPLE) || target.isOf(Items.ENCHANTED_GOLDEN_APPLE))
                && (inInv.isOf(Items.GOLDEN_APPLE) || inInv.isOf(Items.ENCHANTED_GOLDEN_APPLE))) {
            return true;
        }

        if (target.isOf(Items.PLAYER_HEAD)) {
            if (target.contains(DataComponentTypes.CUSTOM_NAME)) {
                return Objects.equals(inInv.getName(), target.getName());
            }
        }
        return true;
    }

    public static boolean isMatchingItem(ItemStack stack, Item targetItem) {
        if (stack == null || stack.isEmpty() || targetItem == null) return false;
        if (stack.isOf(targetItem)) return true;
        if (targetItem == Items.GOLDEN_APPLE && stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return true;
        if (targetItem == Items.PLAYER_HEAD && stack.isOf(Items.PLAYER_HEAD)) return true;
        return false;
    }

    public static boolean startSwap(ItemStack targetStack) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || targetStack == null || targetStack.isEmpty()) {
            return false;
        }
        if (stage != Stage.IDLE) {
            return false;
        }

        int slot = findTargetSlot(targetStack);
        if (slot == -1) {
            return false;
        }

        return startSwapSlot(slot);
    }

    public static boolean performLegitSwap(ItemStack targetStack) {
        return startSwap(targetStack);
    }

    public static boolean performLegitSwap(Item targetItem) {
        if (targetItem == null) return false;
        return startSwap(new ItemStack(targetItem));
    }

    public static boolean startSwapSlot(int slot) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return false;
        if (stage != Stage.IDLE) return false;
        if (slot < 0) return false;

        targetSlot = slot;
        timer = 0;
        stage = Stage.OPENING;

        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        mc.setScreen(new InventoryScreen(mc.player));
        aimAtSlot(targetSlot);
        return true;
    }

    private static void aimAtSlot(int slotIndex) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen instanceof InventoryScreen screen && mc.player != null) {
            try {
                if (slotIndex >= 0 && slotIndex < mc.player.playerScreenHandler.slots.size()) {
                    Slot slot = mc.player.playerScreenHandler.getSlot(slotIndex);
                    int guiX = ((HandledScreenAccessor) screen).getX();
                    int guiY = ((HandledScreenAccessor) screen).getY();
                    double cursorX = (guiX + slot.x + 8.0) * mc.getWindow().getScaleFactor();
                    double cursorY = (guiY + slot.y + 8.0) * mc.getWindow().getScaleFactor();
                    GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), cursorX, cursorY);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (stage == Stage.IDLE || mc.player == null || mc.interactionManager == null) {
            return;
        }

        if (!(mc.currentScreen instanceof InventoryScreen)) {
            stage = Stage.IDLE;
            targetSlot = -1;
            timer = 0;
            return;
        }

        timer++;
        int waitTicks = Math.max(1, (int) this.delay.getValueFloat());

        if (stage == Stage.OPENING) {
            aimAtSlot(targetSlot);
            if (timer >= waitTicks) {
                mc.interactionManager.clickSlot(
                        mc.player.playerScreenHandler.syncId,
                        targetSlot,
                        40,
                        SlotActionType.SWAP,
                        mc.player
                );
                stage = Stage.SWAPPING;
                timer = 0;
            }
        } else if (stage == Stage.SWAPPING) {
            if (timer >= waitTicks) {
                stage = Stage.IDLE;
                targetSlot = -1;
                timer = 0;
                mc.player.closeHandledScreen();
                mc.setScreen((Screen) null);
            }
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        stage = Stage.IDLE;
        targetSlot = -1;
        timer = 0;
    }

    private static Item getItemByMode(String modeSetting) {
        if (modeSetting == null) return null;
        switch (modeSetting) {
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

    private static int findAlternativeSphereSlot(MinecraftClient mc, ItemStack offhand) {
        if (mc.player == null) return -1;

        for (int i = 36; i <= 44; i++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.isOf(Items.PLAYER_HEAD)) {
                if (offhand.isEmpty() || !sameSphere(stack, offhand)) {
                    return i;
                }
            }
        }

        for (int i = 9; i <= 35; i++) {
            ItemStack stack = mc.player.playerScreenHandler.getSlot(i).getStack();
            if (stack.isOf(Items.PLAYER_HEAD)) {
                if (offhand.isEmpty() || !sameSphere(stack, offhand)) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static boolean sameSphere(ItemStack a, ItemStack b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) return false;
        if (!a.isOf(Items.PLAYER_HEAD) || !b.isOf(Items.PLAYER_HEAD)) return false;
        String nameA = a.getName().getString();
        String nameB = b.getName().getString();
        if (!nameA.equals(nameB)) return false;
        var loreA = a.get(DataComponentTypes.LORE);
        var loreB = b.get(DataComponentTypes.LORE);
        if (loreA != null && loreB != null) {
            return Objects.equals(loreA.lines(), loreB.lines());
        }
        return loreA == null && loreB == null;
    }
}
