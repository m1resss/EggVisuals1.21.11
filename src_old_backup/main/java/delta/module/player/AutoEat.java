package delta.module.player;

import static delta.core.Interface.aM_;
import delta.core.Category;
import delta.core.EventTarget;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.event.TickEvent;
import delta.setting.BooleanSetting;
import delta.setting.ModeSetting;
import delta.setting.SliderSetting;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.FishingRodItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import org.lwjgl.glfw.GLFW;

@ModuleRegister(a = "Legit AutoEat", b = "Берёт в руки морковку (если голоден) или гэпл (если сыт) по ПКМ с инструментом", c = Category.Player)
public class AutoEat extends Module {
    private final ModeSetting mode = new ModeSetting("Режим", "Умный по ПКМ", "Умный по ПКМ", "Автоматический");
    private final BooleanSetting onlyWithTool = new BooleanSetting("Только с инструментом", true);
    private final BooleanSetting returnToTool = new BooleanSetting("Возвращать инструмент", true);
    private final BooleanSetting fallbackFood = new BooleanSetting("Запасная еда", true);
    private final BooleanSetting includeWeapons = new BooleanSetting("Включать оружие", false);
    private final SliderSetting hungerThreshold = new SliderSetting("Порог сытости (Авто)", 16.0f, 1.0f, 19.0f, 1.0f);

    // State for "Умный по ПКМ"
    private boolean swappedForRmb = false;
    private int prevToolSlot = -1;
    private int swappedFoodSlot = -1;
    private int releaseTicks = 0;

    // State for "Автоматический"
    private enum AutoState {
        IDLE,
        EATING_OFFHAND,
        EATING_HOTBAR
    }
    private AutoState autoState = AutoState.IDLE;
    private int autoPrevSlot = -1;
    private int autoEatTicks = 0;

    public AutoEat() {
        a(this.mode, this.onlyWithTool, this.returnToTool, this.fallbackFood, this.includeWeapons, this.hungerThreshold);
    }

    @Override
    public void c() {
        super.c();
        resetAll();
    }

    private void resetAll() {
        if (this.swappedForRmb && this.prevToolSlot != -1 && aM_.player != null) {
            aM_.player.getInventory().setSelectedSlot(this.prevToolSlot);
        }
        this.swappedForRmb = false;
        this.prevToolSlot = -1;
        this.swappedFoodSlot = -1;
        this.releaseTicks = 0;

        if (this.autoState != AutoState.IDLE) {
            aM_.options.useKey.setPressed(false);
            if (this.autoState == AutoState.EATING_HOTBAR && this.autoPrevSlot != -1 && aM_.player != null) {
                aM_.player.getInventory().setSelectedSlot(this.autoPrevSlot);
            }
        }
        this.autoState = AutoState.IDLE;
        this.autoPrevSlot = -1;
        this.autoEatTicks = 0;
    }

    private boolean isHoldingTool() {
        if (aM_.player == null) {
            return false;
        }
        ItemStack stack = aM_.player.getMainHandStack();
        if (stack.isEmpty()) {
            return false;
        }
        boolean tool = stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.SHOVELS) || stack.isIn(ItemTags.HOES) || stack.isOf(Items.SHEARS) || stack.isOf(Items.FISHING_ROD);
        if (!tool && this.includeWeapons.c().booleanValue()) {
            tool = stack.isIn(ItemTags.SWORDS) || stack.isOf(Items.MACE);
        }
        return tool;
    }

    private boolean isSafeFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.get(DataComponentTypes.FOOD) == null) {
            return false;
        }
        Item item = stack.getItem();
        return item != Items.ROTTEN_FLESH
            && item != Items.SPIDER_EYE
            && item != Items.POISONOUS_POTATO
            && item != Items.PUFFERFISH
            && item != Items.CHORUS_FRUIT;
    }

    private int getFoodScore(ItemStack stack) {
        if (!isSafeFood(stack)) {
            return -1;
        }
        Item item = stack.getItem();
        if (item == Items.ENCHANTED_GOLDEN_APPLE) {
            return 100;
        }
        if (item == Items.GOLDEN_APPLE) {
            return 90;
        }
        if (item == Items.GOLDEN_CARROT) {
            return 85;
        }
        if (item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP) {
            return 80;
        }
        if (item == Items.COOKED_MUTTON || item == Items.COOKED_SALMON) {
            return 70;
        }
        if (item == Items.BREAD || item == Items.BAKED_POTATO || item == Items.COOKED_CHICKEN) {
            return 60;
        }
        return 50;
    }

    private int findCarrotOrFoodSlot() {
        if (aM_.player == null) {
            return -1;
        }
        // First priority: Golden Carrot
        for (int i = 0; i < 9; i++) {
            ItemStack stack = aM_.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.GOLDEN_CARROT) {
                return i;
            }
        }
        // Fallback to any safe food if enabled
        if (this.fallbackFood.c().booleanValue()) {
            return findBestFoodSlot();
        }
        return -1;
    }

    private int findGappleSlot() {
        if (aM_.player == null) {
            return -1;
        }
        // Look for golden apple or enchanted golden apple
        for (int i = 0; i < 9; i++) {
            ItemStack stack = aM_.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                if (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE) {
                    return i;
                }
            }
        }
        return -1;
    }

    private int findBestFoodSlot() {
        if (aM_.player == null) {
            return -1;
        }
        int bestSlot = -1;
        int bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = aM_.player.getInventory().getStack(i);
            int score = getFoodScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private boolean isRmbPressed() {
        boolean useKeyPressed = aM_.options.useKey.isPressed();
        boolean mousePressed = false;
        if (aM_.getWindow() != null && aM_.getWindow().getHandle() != 0) {
            mousePressed = GLFW.glfwGetMouseButton(aM_.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        }
        return useKeyPressed || mousePressed;
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (aM_.player == null || aM_.world == null || !aM_.player.isAlive()) {
            resetAll();
            return;
        }

        if (aM_.currentScreen != null) {
            if (this.swappedForRmb && this.prevToolSlot != -1) {
                aM_.player.getInventory().setSelectedSlot(this.prevToolSlot);
                this.swappedForRmb = false;
                this.prevToolSlot = -1;
            }
            return;
        }

        if (this.mode.l("Умный по ПКМ")) {
            handleRmbSmartMode();
        } else {
            handleAutoMode();
        }
    }

    private void handleRmbSmartMode() {
        boolean rmb = isRmbPressed();
        boolean usingItem = aM_.player.isUsingItem();

        if (this.swappedForRmb) {
            // Player is still holding RMB or still eating
            if (rmb || usingItem) {
                this.releaseTicks = 0;
                // Keep the food slot selected
                if (this.swappedFoodSlot != -1 && aM_.player.getInventory().getSelectedSlot() != this.swappedFoodSlot) {
                    ItemStack current = aM_.player.getInventory().getStack(this.swappedFoodSlot);
                    if (isSafeFood(current)) {
                        aM_.player.getInventory().setSelectedSlot(this.swappedFoodSlot);
                    }
                }
                return;
            }

            // Released RMB and not using item
            this.releaseTicks++;
            if (this.releaseTicks >= 2) {
                if (this.returnToTool.c().booleanValue() && this.prevToolSlot != -1) {
                    aM_.player.getInventory().setSelectedSlot(this.prevToolSlot);
                }
                this.swappedForRmb = false;
                this.prevToolSlot = -1;
                this.swappedFoodSlot = -1;
                this.releaseTicks = 0;
            }
            return;
        }

        // Not currently swapped: check if RMB is pressed with tool in hand
        if (rmb) {
            if (this.onlyWithTool.c().booleanValue() && !isHoldingTool()) {
                return;
            }

            int foodLevel = aM_.player.getHungerManager().getFoodLevel();
            int targetSlot;

            if (foodLevel < 20) {
                // Hungry: select Golden Carrot
                targetSlot = findCarrotOrFoodSlot();
            } else {
                // Full: select Golden Apple
                targetSlot = findGappleSlot();
            }

            if (targetSlot != -1 && targetSlot != aM_.player.getInventory().getSelectedSlot()) {
                this.prevToolSlot = aM_.player.getInventory().getSelectedSlot();
                this.swappedFoodSlot = targetSlot;
                this.swappedForRmb = true;
                this.releaseTicks = 0;
                aM_.player.getInventory().setSelectedSlot(targetSlot);
            }
        }
    }

    private void handleAutoMode() {
        if (this.onlyWithTool.c().booleanValue() && !isHoldingTool()) {
            resetAll();
            return;
        }

        int currentFood = aM_.player.getHungerManager().getFoodLevel();

        if (this.autoState != AutoState.IDLE) {
            this.autoEatTicks++;

            if (currentFood >= 20 || (currentFood > this.hungerThreshold.c().intValue() && !aM_.player.isUsingItem())) {
                resetAll();
                return;
            }

            if (this.autoState == AutoState.EATING_HOTBAR) {
                ItemStack currentHand = aM_.player.getInventory().getStack(this.autoPrevSlot);
                if (!isSafeFood(currentHand)) {
                    resetAll();
                    return;
                }
            }

            aM_.options.useKey.setPressed(true);

            if (this.autoEatTicks > 80 && !aM_.player.isUsingItem()) {
                resetAll();
            }
            return;
        }

        if (currentFood <= this.hungerThreshold.c().intValue()) {
            int bestSlot = findBestFoodSlot();
            if (bestSlot != -1) {
                this.autoPrevSlot = aM_.player.getInventory().getSelectedSlot();
                this.autoState = AutoState.EATING_HOTBAR;
                this.autoEatTicks = 0;
                aM_.player.getInventory().setSelectedSlot(bestSlot);
                aM_.options.useKey.setPressed(true);
            }
        }
    }
}
