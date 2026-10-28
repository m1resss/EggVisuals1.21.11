package egg.client.module.player;

import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import org.lwjgl.glfw.GLFW;

public class AutoEat extends Module {
    private final ModeSetting mode = new ModeSetting("Режим", "Умный по ПКМ", "Режим работы", "Умный по ПКМ", "Автоматический");
    private final BooleanSetting onlyWithTool = new BooleanSetting("Только с инструментом", true, "Свапать только если в руках инструмент");
    private final BooleanSetting returnToTool = new BooleanSetting("Возвращать инструмент", true, "Возвращать инструмент в руку после еды");
    private final BooleanSetting fallbackFood = new BooleanSetting("Запасная еда", true, "Если нет золотой морковки, брать другую безопасную еду");
    private final BooleanSetting includeWeapons = new BooleanSetting("Включать оружие", false, "Считать мечи и булавы инструментами");
    private final SliderSetting hungerThreshold = new SliderSetting("Порог сытости (Авто)", 16.0, 1.0, 19.0, 1.0, "Порог голода для автоматического режима");

    private boolean swappedForRmb = false;
    private int prevToolSlot = -1;
    private int swappedFoodSlot = -1;
    private int releaseTicks = 0;

    private enum AutoState {
        IDLE,
        EATING_OFFHAND,
        EATING_HOTBAR
    }
    private AutoState autoState = AutoState.IDLE;
    private int autoPrevSlot = -1;
    private int autoEatTicks = 0;

    public AutoEat() {
        super("AutoEat", "Берёт в руки морковку (если голоден) или гэпл (если сыт) по ПКМ с инструментом", Category.PLAYER);
        addSettings(this.mode, this.onlyWithTool, this.returnToTool, this.fallbackFood, this.includeWeapons, this.hungerThreshold);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetAll();
    }

    private void resetAll() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (this.swappedForRmb && this.prevToolSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(this.prevToolSlot);
        }
        this.swappedForRmb = false;
        this.prevToolSlot = -1;
        this.swappedFoodSlot = -1;
        this.releaseTicks = 0;

        if (this.autoState != AutoState.IDLE) {
            mc.options.useKey.setPressed(false);
            if (this.autoState == AutoState.EATING_HOTBAR && this.autoPrevSlot != -1 && mc.player != null) {
                mc.player.getInventory().setSelectedSlot(this.autoPrevSlot);
            }
        }
        this.autoState = AutoState.IDLE;
        this.autoPrevSlot = -1;
        this.autoEatTicks = 0;
    }

    private boolean isHoldingTool() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return false;
        ItemStack stack = mc.player.getMainHandStack();
        if (stack.isEmpty()) return false;

        boolean tool = stack.isIn(ItemTags.PICKAXES) || stack.isIn(ItemTags.AXES) || stack.isIn(ItemTags.SHOVELS)
                || stack.isIn(ItemTags.HOES) || stack.isOf(Items.SHEARS) || stack.isOf(Items.FISHING_ROD);
        if (!tool && this.includeWeapons.get()) {
            tool = stack.isIn(ItemTags.SWORDS) || stack.isOf(Items.MACE);
        }
        return tool;
    }

    private boolean isSafeFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.get(DataComponentTypes.FOOD) == null) return false;
        Item item = stack.getItem();
        return item != Items.ROTTEN_FLESH
                && item != Items.SPIDER_EYE
                && item != Items.POISONOUS_POTATO
                && item != Items.PUFFERFISH
                && item != Items.CHORUS_FRUIT;
    }

    private int getFoodScore(ItemStack stack) {
        if (!isSafeFood(stack)) return -1;
        Item item = stack.getItem();
        if (item == Items.ENCHANTED_GOLDEN_APPLE) return 100;
        if (item == Items.GOLDEN_APPLE) return 90;
        if (item == Items.GOLDEN_CARROT) return 85;
        if (item == Items.COOKED_BEEF || item == Items.COOKED_PORKCHOP) return 80;
        if (item == Items.COOKED_MUTTON || item == Items.COOKED_SALMON) return 70;
        if (item == Items.BREAD || item == Items.BAKED_POTATO || item == Items.COOKED_CHICKEN) return 60;
        return 50;
    }

    private int findCarrotOrFoodSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(Items.GOLDEN_CARROT)) {
                return i;
            }
        }

        if (this.fallbackFood.get()) {
            return findBestFoodSlot();
        }
        return -1;
    }

    private int findGappleSlot() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
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
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return -1;

        int bestSlot = -1;
        int bestScore = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            int score = getFoodScore(stack);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    private boolean isRmbPressed() {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean useKeyPressed = mc.options.useKey.isPressed();
        boolean mousePressed = false;
        if (mc.getWindow() != null && mc.getWindow().getHandle() != 0) {
            mousePressed = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        }
        return useKeyPressed || mousePressed;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || !mc.player.isAlive()) {
            resetAll();
            return;
        }

        if (mc.currentScreen != null) {
            if (this.swappedForRmb && this.prevToolSlot != -1) {
                mc.player.getInventory().setSelectedSlot(this.prevToolSlot);
                this.swappedForRmb = false;
                this.prevToolSlot = -1;
            }
            return;
        }

        if ("Умный по ПКМ".equals(this.mode.get())) {
            handleRmbSmartMode(mc);
        } else {
            handleAutoMode(mc);
        }
    }

    private void handleRmbSmartMode(MinecraftClient mc) {
        boolean rmb = isRmbPressed();
        boolean usingItem = mc.player.isUsingItem();

        if (this.swappedForRmb) {

            if (rmb || usingItem) {
                this.releaseTicks = 0;

                if (this.swappedFoodSlot != -1 && mc.player.getInventory().getSelectedSlot() != this.swappedFoodSlot) {
                    ItemStack current = mc.player.getInventory().getStack(this.swappedFoodSlot);
                    if (isSafeFood(current)) {
                        mc.player.getInventory().setSelectedSlot(this.swappedFoodSlot);
                    }
                }
                return;
            }

            this.releaseTicks++;
            if (this.releaseTicks >= 2) {
                if (this.returnToTool.get() && this.prevToolSlot != -1) {
                    mc.player.getInventory().setSelectedSlot(this.prevToolSlot);
                }
                this.swappedForRmb = false;
                this.prevToolSlot = -1;
                this.swappedFoodSlot = -1;
                this.releaseTicks = 0;
            }
            return;
        }

        if (rmb) {
            if (this.onlyWithTool.get() && !isHoldingTool()) {
                return;
            }

            int foodLevel = mc.player.getHungerManager().getFoodLevel();
            int targetSlot;

            if (foodLevel < 20) {

                targetSlot = findCarrotOrFoodSlot();
            } else {

                targetSlot = findGappleSlot();
            }

            if (targetSlot != -1 && targetSlot != mc.player.getInventory().getSelectedSlot()) {
                this.prevToolSlot = mc.player.getInventory().getSelectedSlot();
                this.swappedFoodSlot = targetSlot;
                this.swappedForRmb = true;
                this.releaseTicks = 0;
                mc.player.getInventory().setSelectedSlot(targetSlot);
            }
        }
    }

    private void handleAutoMode(MinecraftClient mc) {
        if (this.onlyWithTool.get() && !isHoldingTool()) {
            resetAll();
            return;
        }

        int currentFood = mc.player.getHungerManager().getFoodLevel();

        if (this.autoState != AutoState.IDLE) {
            this.autoEatTicks++;

            if (currentFood >= 20 || (currentFood > (int) this.hungerThreshold.get() && !mc.player.isUsingItem())) {
                resetAll();
                return;
            }

            if (this.autoState == AutoState.EATING_HOTBAR) {
                ItemStack currentHand = mc.player.getInventory().getStack(mc.player.getInventory().getSelectedSlot());
                if (!isSafeFood(currentHand)) {
                    resetAll();
                    return;
                }
            }

            mc.options.useKey.setPressed(true);

            if (this.autoEatTicks > 80 && !mc.player.isUsingItem()) {
                resetAll();
            }
            return;
        }

        if (currentFood <= (int) this.hungerThreshold.get()) {
            int bestSlot = findBestFoodSlot();
            if (bestSlot != -1) {
                this.autoPrevSlot = mc.player.getInventory().getSelectedSlot();
                this.autoState = AutoState.EATING_HOTBAR;
                this.autoEatTicks = 0;
                mc.player.getInventory().setSelectedSlot(bestSlot);
                mc.options.useKey.setPressed(true);
            }
        }
    }
}
