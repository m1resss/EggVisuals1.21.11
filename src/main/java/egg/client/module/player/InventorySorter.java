package egg.client.module.player;

import egg.client.inventory.InventoryLayoutManager;
import egg.client.inventory.InventoryPreset;
import egg.client.inventory.InventorySlotRule;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ButtonSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.module.setting.SliderSetting;
import egg.client.util.GuiSoundManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class InventorySorter extends Module {

    private static InventorySorter instance;

    private final ModeSetting presetMode;
    private final BooleanSetting autoSortOnOpen = new BooleanSetting("Авто-сортировка при открытии", false, "Автоматически сортировать инвентарь при открытии инвентаря на E");
    private final SliderSetting delayTicks = new SliderSetting("Задержка (тики)", 2.0, 1.0, 5.0, 1.0, "Задержка между перемещениями слотов (2 тика безопасно для античитов)");
    private final BooleanSetting sortHotbar = new BooleanSetting("Сортировать хотбар", true, "Раскладывать хотбар (слоты 1-9)");
    private final BooleanSetting sortMain = new BooleanSetting("Сортировать основной инвентарь", true, "Раскладывать верхние 3 ряда инвентаря");
    private final BooleanSetting sortArmor = new BooleanSetting("Сортировать броню", true, "Надевать и сортировать слоты брони");
    private final BooleanSetting sortOffhand = new BooleanSetting("Сортировать вторую руку", true, "Перемещать тотем/щит во вторую руку");

    private final ButtonSetting sortBtn = new ButtonSetting("Сортировать сейчас", "Начать автоматическую раскладку инвентаря по активному КФГ", this::startSort);

    private final ButtonSetting saveBtn = new ButtonSetting("Сохранить текущий инвентарь", "Сохранить текущую раскладку слотов игрока в активный КФГ", () -> {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            InventoryPreset active = InventoryLayoutManager.getInstance().getActivePreset();
            String targetName = (active != null) ? active.getName() : "КФГ 1";
            InventoryLayoutManager.getInstance().saveCurrentAsPreset(
                    targetName,
                    mc.player.getInventory()
            );
            updatePresetModes();
            GuiSoundManager.playSelect();
        }
    });

    private final ButtonSetting newPresetBtn = new ButtonSetting("Создать новый КФГ", "Создать новый профиль инвентаря", () -> {
        int nextNum = InventoryLayoutManager.getInstance().getPresets().size() + 1;
        String newName = "КФГ " + nextNum;
        InventoryLayoutManager.getInstance().createNewPreset(newName);
        updatePresetModes();
        GuiSoundManager.playSelect();
    });

    private final ButtonSetting deletePresetBtn = new ButtonSetting("Удалить текущий КФГ", "Удалить выбранный пресет", () -> {
        if (InventoryLayoutManager.getInstance().deleteCurrentPreset()) {
            updatePresetModes();
            GuiSoundManager.playSelect();
        }
    });

    private final Deque<SwapTask> taskQueue = new ArrayDeque<>();
    private int tickTimer = 0;
    private boolean sortingActive = false;
    private String statusMessage = "";

    public InventorySorter() {
        super("InvSorter", "Авто-сортировка инвентаря по сохранённым КФГ с превью", Category.PLAYER);
        instance = this;

        List<String> names = InventoryLayoutManager.getInstance().getPresetNames();
        InventoryPreset active = InventoryLayoutManager.getInstance().getActivePreset();
        String activeName = (active != null) ? active.getName() : "None";
        List<String> modes = names.isEmpty() ? List.of("None") : names;
        this.presetMode = new ModeSetting("Активный КФГ", activeName, "Выбор сохраненного профиля инвентаря", modes.toArray(new String[0]));

        addSettings(presetMode, autoSortOnOpen, delayTicks, sortHotbar, sortMain, sortArmor, sortOffhand, sortBtn, saveBtn, newPresetBtn, deletePresetBtn);
    }

    public static InventorySorter getInstance() {
        return instance;
    }

    public void updatePresetModes() {
        List<String> names = InventoryLayoutManager.getInstance().getPresetNames();
        InventoryPreset active = InventoryLayoutManager.getInstance().getActivePreset();
        String activeName = (active != null) ? active.getName() : "None";
        List<String> modes = names.isEmpty() ? List.of("None") : names;
        presetMode.setModes(modes);
        presetMode.set(activeName);
    }

    public void onInventoryScreenOpened() {
        if (!isEnabled()) return;
        if (autoSortOnOpen.isEnabled()) {
            startSort();
        }
    }

    public void startSort() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null) return;

        if (!(mc.currentScreen instanceof InventoryScreen)) {
            mc.setScreen(new InventoryScreen(mc.player));
        }

        taskQueue.clear();
        buildTaskQueue(mc.player);

        if (!taskQueue.isEmpty()) {
            sortingActive = true;
            tickTimer = 0;
            statusMessage = "Сортировка: " + taskQueue.size();
            GuiSoundManager.playSelect();
        } else {
            statusMessage = "Уже разложен!";
            GuiSoundManager.playToggle(true);
        }
    }

    private void buildTaskQueue(PlayerEntity player) {
        InventoryPreset preset = InventoryLayoutManager.getInstance().getActivePreset();
        if (preset == null) return;
        PlayerScreenHandler handler = player.playerScreenHandler;

        ItemStack[] virtualSlots = new ItemStack[handler.slots.size()];
        for (int i = 0; i < handler.slots.size(); i++) {
            virtualSlots[i] = handler.slots.get(i).getStack().copy();
        }

        boolean[] lockedSlots = new boolean[handler.slots.size()];

        for (int pSlot = 0; pSlot <= 40; pSlot++) {
            if (!isSlotAllowed(pSlot)) continue;
            int hSlot = playerInvToScreenHandler(pSlot);
            if (hSlot < 0 || hSlot >= handler.slots.size()) continue;

            InventorySlotRule rule = preset.getRule(pSlot);
            if (rule != null && !rule.isEmpty()) {
                ItemStack cur = virtualSlots[hSlot];
                if (rule.matches(cur)) {
                    lockedSlots[hSlot] = true;
                }
            }
        }

        for (int pSlot = 0; pSlot <= 40; pSlot++) {
            if (!isSlotAllowed(pSlot)) continue;
            int dstSlot = playerInvToScreenHandler(pSlot);
            if (dstSlot < 0 || dstSlot >= handler.slots.size()) continue;
            if (lockedSlots[dstSlot]) continue;

            InventorySlotRule rule = preset.getRule(pSlot);
            if (rule == null || rule.isEmpty()) continue;

            int srcSlot = findBestMatchSlot(virtualSlots, lockedSlots, rule, dstSlot);
            if (srcSlot != -1) {

                boolean isHotbar = (dstSlot >= 36 && dstSlot <= 44);
                int hotbarBtn = isHotbar ? (dstSlot - 36) : 0;

                taskQueue.add(new SwapTask(srcSlot, dstSlot, isHotbar, hotbarBtn));

                ItemStack tmp = virtualSlots[dstSlot];
                virtualSlots[dstSlot] = virtualSlots[srcSlot];
                virtualSlots[srcSlot] = tmp;

                lockedSlots[dstSlot] = true;
            }
        }
    }

    private int findBestMatchSlot(ItemStack[] slots, boolean[] locked, InventorySlotRule rule, int targetSlot) {

        int[] searchOrder = new int[37];
        int idx = 0;
        for (int i = 9; i <= 35; i++) searchOrder[idx++] = i;
        for (int i = 36; i <= 44; i++) searchOrder[idx++] = i;
        searchOrder[idx++] = 45;

        for (int s : searchOrder) {
            if (s == targetSlot) continue;
            if (locked[s]) continue;
            ItemStack stack = slots[s];
            if (stack != null && !stack.isEmpty() && rule.matches(stack)) {
                return s;
            }
        }
        return -1;
    }

    private boolean isSlotAllowed(int pSlot) {
        if (pSlot >= 0 && pSlot < 9) return sortHotbar.isEnabled();
        if (pSlot >= 9 && pSlot < 36) return sortMain.isEnabled();
        if (pSlot >= 36 && pSlot <= 39) return sortArmor.isEnabled();
        if (pSlot == 40) return sortOffhand.isEnabled();
        return false;
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (!sortingActive || mc.player == null || mc.interactionManager == null) {
            return;
        }

        if (!(mc.currentScreen instanceof InventoryScreen)) {
            taskQueue.clear();
            sortingActive = false;
            statusMessage = "Прервано";
            return;
        }

        tickTimer++;
        int wait = Math.max(1, (int) delayTicks.getValueFloat());
        if (tickTimer < wait) {
            return;
        }
        tickTimer = 0;

        if (taskQueue.isEmpty()) {
            sortingActive = false;
            statusMessage = "Отсортировано!";
            GuiSoundManager.playToggle(true);
            return;
        }

        SwapTask task = taskQueue.poll();
        int syncId = mc.player.playerScreenHandler.syncId;

        try {
            if (task.isHotbarSwap) {

                mc.interactionManager.clickSlot(syncId, task.srcSlot, task.hotbarBtn, SlotActionType.SWAP, mc.player);
            } else {

                mc.interactionManager.clickSlot(syncId, task.srcSlot, 0, SlotActionType.PICKUP, mc.player);
                mc.interactionManager.clickSlot(syncId, task.dstSlot, 0, SlotActionType.PICKUP, mc.player);
                if (!mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                    mc.interactionManager.clickSlot(syncId, task.srcSlot, 0, SlotActionType.PICKUP, mc.player);
                }
            }
            GuiSoundManager.playSelect();
        } catch (Throwable e) {
            taskQueue.clear();
            sortingActive = false;
        }
    }

    public static int playerInvToScreenHandler(int pSlot) {
        if (pSlot >= 0 && pSlot < 9) return pSlot + 36;
        if (pSlot >= 9 && pSlot < 36) return pSlot;
        if (pSlot == 36) return 8;
        if (pSlot == 37) return 7;
        if (pSlot == 38) return 6;
        if (pSlot == 39) return 5;
        if (pSlot == 40) return 45;
        return -1;
    }

    public static int screenHandlerToPlayerInv(int hSlot) {
        if (hSlot >= 36 && hSlot <= 44) return hSlot - 36;
        if (hSlot >= 9 && hSlot <= 35) return hSlot;
        if (hSlot == 8) return 36;
        if (hSlot == 7) return 37;
        if (hSlot == 6) return 38;
        if (hSlot == 5) return 39;
        if (hSlot == 45) return 40;
        return -1;
    }

    public boolean isSorting() {
        return sortingActive;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    private static class SwapTask {
        final int srcSlot;
        final int dstSlot;
        final boolean isHotbarSwap;
        final int hotbarBtn;

        SwapTask(int srcSlot, int dstSlot, boolean isHotbarSwap, int hotbarBtn) {
            this.srcSlot = srcSlot;
            this.dstSlot = dstSlot;
            this.isHotbarSwap = isHotbarSwap;
            this.hotbarBtn = hotbarBtn;
        }
    }
}
