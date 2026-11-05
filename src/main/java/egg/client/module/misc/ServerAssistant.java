package egg.client.module.misc;

import egg.client.mixin.HandledScreenAccessor;
import egg.client.mixin.ItemCooldownEntryAccessor;
import egg.client.mixin.ItemCooldownManagerAccessor;
import egg.client.module.Category;
import egg.client.module.Module;
import egg.client.module.setting.BindSetting;
import egg.client.module.setting.BooleanSetting;
import egg.client.module.setting.ModeSetting;
import egg.client.render.Render2D;
import egg.client.render.font.ClientFont;
import egg.client.util.ColorUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;

public class ServerAssistant extends Module {
    private static ServerAssistant instance;

    public static ServerAssistant getInstance() {
        return instance;
    }

    private final ModeSetting server = new ModeSetting("Сервер", "FunTime", "FunTime", "SpookyTime", "HolyWorld");

    private final BooleanSetting auctionAssist = new BooleanSetting("Аукционный ассистент", true, "Подсветка самого дешевого товара в аукционе");
    private final BooleanSetting slotPrices = new BooleanSetting("Цены на слотах", true, "Отображение цены прямо над слотами в аукционе");
    private final BooleanSetting unitPriceTooltip = new BooleanSetting("Цена за штуку", true, "Отображение цены за одну штуку в описании товара");
    private final BooleanSetting storageTotal = new BooleanSetting("Сумма хранилища", true, "Отображение общей стоимости в заголовке хранилища");

    private final BindSetting bindTrap = new BindSetting("Трапка", 0, () -> triggerItem("Трапка", Items.NETHERITE_SCRAP), "FunTime / SpookyTime");
    private final BindSetting bindSnowball = new BindSetting("Снежок заморозка", 0, () -> triggerItem("Снежок заморозка", Items.SNOWBALL), "FunTime / SpookyTime");
    private final BindSetting bindPlast = new BindSetting("Пласт", 0, () -> triggerItem("Пласт", Items.DRIED_KELP), "FunTime / SpookyTime");
    private final BindSetting bindDisorient = new BindSetting("Дезориентация", 0, () -> triggerItem("Дезориентация", Items.ENDER_EYE), "FunTime / SpookyTime");
    private final BindSetting bindDust = new BindSetting("Явная пыль", 0, () -> triggerItem("Явная пыль", Items.SUGAR), "FunTime / SpookyTime");
    private final BindSetting bindWind = new BindSetting("Заряд ветра", 0, () -> triggerItem("Заряд ветра", Items.WIND_CHARGE), "FunTime / SpookyTime");
    private final BindSetting bindFire = new BindSetting("Огненный заряд", 0, () -> triggerItem("Огненный заряд", Items.FIRE_CHARGE), "FunTime / SpookyTime");
    private final BindSetting bindAura = new BindSetting("Божья аура", 0, () -> triggerItem("Божья аура", Items.PHANTOM_MEMBRANE), "FunTime / SpookyTime");

    private final BindSetting bindHolyStuff = new BindSetting("Взрывная штучка", 0, () -> triggerItem("Взрывная штучка", Items.FIRE_CHARGE), "HolyWorld");
    private final BindSetting bindHolyStick = new BindSetting("Взрывная палочка", 0, () -> triggerItem("Взрывная палочка", Items.BLAZE_ROD), "HolyWorld");
    private final BindSetting bindHolyTrap = new BindSetting("Взрывная трапка", 0, () -> triggerItem("Взрывная трапка", Items.PRISMARINE_SHARD), "HolyWorld");
    private final BindSetting bindHolyStun = new BindSetting("Стан", 0, () -> triggerItem("Стан", Items.NETHER_STAR), "HolyWorld");
    private final BindSetting bindHolySnow = new BindSetting("Ком снега", 0, () -> triggerItem("Ком снега", Items.SNOWBALL), "HolyWorld");

    public ServerAssistant() {
        super("Server Assistant", "Помощник для работы с донат-предметами и аукционом на серверах", Category.MISC);
        instance = this;

        auctionAssist.setVisible(() -> isFunTimeOrSpooky());
        slotPrices.setVisible(() -> isFunTimeOrSpooky());
        unitPriceTooltip.setVisible(() -> isFunTimeOrSpooky());
        storageTotal.setVisible(() -> isFunTimeOrSpooky());

        bindTrap.setVisible(() -> isFunTimeOrSpooky());
        bindSnowball.setVisible(() -> isFunTimeOrSpooky());
        bindPlast.setVisible(() -> isFunTimeOrSpooky());
        bindDisorient.setVisible(() -> isFunTimeOrSpooky());
        bindDust.setVisible(() -> isFunTimeOrSpooky());
        bindWind.setVisible(() -> isFunTimeOrSpooky());
        bindFire.setVisible(() -> isFunTimeOrSpooky());
        bindAura.setVisible(() -> isFunTimeOrSpooky());

        bindHolyStuff.setVisible(() -> server.is("HolyWorld"));
        bindHolyStick.setVisible(() -> server.is("HolyWorld"));
        bindHolyTrap.setVisible(() -> server.is("HolyWorld"));
        bindHolyStun.setVisible(() -> server.is("HolyWorld"));
        bindHolySnow.setVisible(() -> server.is("HolyWorld"));

        addSettings(
                server,
                auctionAssist,
                slotPrices,
                unitPriceTooltip,
                storageTotal,
                bindTrap,
                bindSnowball,
                bindPlast,
                bindDisorient,
                bindDust,
                bindWind,
                bindFire,
                bindAura,
                bindHolyStuff,
                bindHolyStick,
                bindHolyTrap,
                bindHolyStun,
                bindHolySnow
        );
    }

    public boolean isFunTimeOrSpooky() {
        return server.is("FunTime") || server.is("SpookyTime");
    }

    public void triggerItem(String name, Item item) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.player == null || mc.world == null) return;

        if (mc.player.getItemCooldownManager().isCoolingDown(item.getDefaultStack())) {
            try {
                ItemCooldownManagerAccessor accessor = (ItemCooldownManagerAccessor) mc.player.getItemCooldownManager();
                Object entry = accessor.getEntries().get(Registries.ITEM.getId(item));
                float remaining = entry != null ? Math.max(((ItemCooldownEntryAccessor) entry).getEndTick() - accessor.getTick(), 0) / 20.0f : 0.0f;
                mc.inGameHud.getChatHud().addMessage(Text.literal("§c" + name + " §7- имеет задержку §c" + String.format(Locale.US, "%.1fс", remaining)));
            } catch (Exception e) {
                mc.inGameHud.getChatHud().addMessage(Text.literal("§c" + name + " §7- имеет задержку"));
            }
            return;
        }

        int hotbarSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != null && !stack.isEmpty() && stack.isOf(item)) {
                hotbarSlot = i;
                break;
            }
        }

        if (hotbarSlot == -1) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("§c" + name + " §7- нет в хотбаре"));
            return;
        }

        mc.player.getInventory().setSelectedSlot(hotbarSlot);
        if (mc.player.networkHandler != null) {
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
        }
    }

    public static long getAuctionItemPrice(ItemStack itemStack) {
        if (itemStack == null || itemStack.isEmpty()) {
            return -1L;
        }
        if (itemStack.getItem() == Items.GRAY_DYE) {
            return -1L;
        }
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return -1L;
            List<Text> tooltip = itemStack.getTooltip(Item.TooltipContext.DEFAULT, mc.player, TooltipType.BASIC);
            if (tooltip == null || tooltip.size() <= 1) {
                return -1L;
            }
            for (int i = 1; i < tooltip.size(); i++) {
                String line = tooltip.get(i).getString();
                if (line.contains("Товар не актуален")) {
                    return -1L;
                }
                String lower = line.toLowerCase(Locale.ROOT);
                if ((lower.contains("цена") || lower.contains("ценa")) && !lower.contains("штук") && !lower.contains("шт.")) {
                    int colonIdx = line.indexOf(':');
                    String after = colonIdx != -1 ? line.substring(colonIdx + 1) : line;
                    String digitsOnly = after.replaceAll("[^0-9]", "");
                    if (!digitsOnly.isEmpty()) {
                        try {
                            long total = Long.parseLong(digitsOnly);
                            int count = Math.max(1, itemStack.getCount());
                            return total / count;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return -1L;
    }

    public static String formatCompactPrice(long price) {
        if (price < 0) return "";
        if (price < 1000) return String.valueOf(price);
        if (price < 1_000_000) {
            double val = price / 1000.0;
            if (price % 1000 == 0 || val >= 100) {
                return String.format(Locale.US, "%.0fк", val);
            }
            String s = String.format(Locale.US, "%.1fк", val);
            return s.endsWith(".0к") ? s.replace(".0к", "к") : s;
        }
        if (price < 1_000_000_000L) {
            double val = price / 1_000_000.0;
            if (price % 1_000_000 == 0 || val >= 100) {
                return String.format(Locale.US, "%.0fкк", val);
            }
            String s = String.format(Locale.US, "%.1fкк", val);
            return s.endsWith(".0кк") ? s.replace(".0кк", "кк") : s;
        }
        double val = price / 1_000_000_000.0;
        String s = String.format(Locale.US, "%.1fккк", val);
        return s.endsWith(".0ккк") ? s.replace(".0ккк", "ккк") : s;
    }

    public void renderAuctionOverlay(DrawContext context, HandledScreen<?> screen, int mouseX, int mouseY) {
        if (!isEnabled()) return;
        if (!(screen instanceof GenericContainerScreen containerScreen)) return;

        List<Slot> slots = screen.getScreenHandler().slots;
        if (slots == null || slots.size() < 45) return;

        int chestSlots = slots.size() - 36;
        if (chestSlots <= 0) return;

        HandledScreenAccessor accessor = (HandledScreenAccessor) screen;
        int guiX = accessor.getX();
        int guiY = accessor.getY();

        Slot cheapest = null;
        long minPrice = Long.MAX_VALUE;

        for (int i = 0; i < chestSlots; i++) {
            Slot slot = slots.get(i);
            if (!slot.hasStack()) continue;
            long p = getAuctionItemPrice(slot.getStack());
            if (p > 0 && p < minPrice) {
                minPrice = p;
                cheapest = slot;
            }
        }

        if (auctionAssist.get() && cheapest != null) {
            float pulse = (float) ((Math.sin((System.currentTimeMillis() % 100000) / 100.0) + 1.0) * 0.5);
            int glowAlpha = (int) (70 + 175 * pulse);
            int greenColor = ColorUtil.rgba(74, 222, 128, glowAlpha);
            float slotX = guiX + cheapest.x;
            float slotY = guiY + cheapest.y;
            Render2D.drawRoundedOutline(context, slotX - 0.5f, slotY - 0.5f, 17.0f, 17.0f, 2.0f, 1.2f, greenColor);
        }

        if (slotPrices.get()) {
            for (int i = 0; i < chestSlots; i++) {
                Slot slot = slots.get(i);
                if (!slot.hasStack()) continue;
                long p = getAuctionItemPrice(slot.getStack());
                if (p <= 0) continue;
                String text = formatCompactPrice(p);
                if (text.isEmpty()) continue;

                float slotX = guiX + slot.x;
                float slotY = guiY + slot.y;
                float textW = ClientFont.getWidth(text, ClientFont.SF_REGULAR);
                float bw = textW + 4.0f;
                float bh = 9.0f;
                float bx = slotX + 8.0f - bw / 2.0f;
                float by = slotY - 1.0f;

                context.getMatrices().pushMatrix();
                context.getMatrices().translate(0.0f, 0.0f);
                Render2D.drawRoundedRect(context, bx, by, bw, bh, 2.0f, 0xD012131A);
                Render2D.drawRoundedOutline(context, bx, by, bw, bh, 2.0f, 0.6f, ColorUtil.rgba(255, 255, 255, 35));
                ClientFont.draw(context, text, bx + 2.0f, by + 1.0f, ClientFont.SF_REGULAR, 0xFF4ADE80);
                context.getMatrices().popMatrix();
            }
        }
    }

    public Text modifyTitle(Text title, HandledScreen<?> screen) {
        if (!isEnabled() || !storageTotal.get()) return title;
        if (title == null) return title;
        String raw = title.getString();
        if (!raw.contains("Хранилище")) return title;

        List<Slot> slots = screen.getScreenHandler().slots;
        if (slots == null) return title;

        long sum = 0L;
        for (Slot slot : slots) {
            if (slot.hasStack()) {
                long p = getAuctionItemPrice(slot.getStack());
                if (p > 0) {
                    sum += p * Math.max(1, slot.getStack().getCount());
                }
            }
        }

        if (sum <= 0) return title;
        String suffix = " - " + formatCompactPrice(sum);
        MutableText result = Text.empty();
        result.append(title);
        result.append(Text.literal(suffix).formatted(Formatting.GREEN));
        return result;
    }

    public void modifyTooltip(ItemStack stack, List<Text> lines) {
        if (!isEnabled() || !unitPriceTooltip.get()) return;
        if (stack == null || stack.isEmpty() || stack.getCount() <= 1) return;
        long unit = getAuctionItemPrice(stack);
        if (unit <= 0) return;

        for (int i = 0; i < lines.size(); i++) {
            String str = lines.get(i).getString();
            if (str.contains("Цена:") || str.contains("Ценa:") || str.contains("$")) {
                MutableText unitLine = Text.literal("$ ").formatted(Formatting.GREEN)
                        .append(Text.literal("Цена за штуку: ").formatted(Formatting.GRAY))
                        .append(Text.literal(String.format(Locale.US, "%,d", unit)).formatted(Formatting.GREEN));
                lines.add(i + 1, unitLine);
                return;
            }
        }
    }
}
