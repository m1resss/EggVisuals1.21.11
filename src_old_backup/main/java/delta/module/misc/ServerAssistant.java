package delta.module.misc;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.enchantment.Enchantments;

import static delta.core.Interface.aM_;
import delta.core.Delta;
import delta.core.Module;
import delta.util.ChatUtil;
import delta.render.ColorUtil;
import delta.util.InventoryUtil;
import delta.util.KeyUtil;
import delta.util.ServerUtil;

import delta.config.DescriptionProcessor;
import delta.config.EnchantmentProcessor;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import delta.core.Category;
import delta.core.EventTarget;
import delta.core.Interface;
import delta.core.ModuleRegister;
import delta.event.ContainerEvent;
import delta.event.PacketEvent;
import delta.event.TickEvent;
import delta.event.TooltipEvent;

import delta.render.AnimationUtil;
import delta.render.Fonts;
import delta.setting.BindSetting;
import delta.setting.BooleanSetting;
import delta.util.CounterUtil;
import delta.setting.ModeSetting;
import delta.setting.MultiModeSetting;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.runtime.SwitchBootstraps;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.Generated;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.util.Formatting;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.screen.slot.Slot;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.text.MutableText;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.tooltip.TooltipType;
import platform.inject.accessors.ClickSlotC2SPacketAccessor;
import platform.inject.accessors.HandledScreenAccessor;
import platform.inject.accessors.ItemCooldownManagerAccessor;
import platform.inject.accessors.ScreenHandlerSlotUpdateS2CPacketAccessor;

@ModuleRegister(a = "Server Assistant", b = "Помощник, упрощающий работу с сервером и игровыми механиками", c = Category.Misc)
public class ServerAssistant extends Module implements Interface {
    private List<b> w;
    private final ModeSetting b = new ModeSetting("Целевой сервер помощи", "FunTime", "FunTime", "SpookyTime", "HolyWorld");
    private final MultiModeSetting c = (MultiModeSetting) new MultiModeSetting("Элементы помощи", new BooleanSetting("Аукционный ассистент", true), new BooleanSetting("Сортировать по цене", true), new BooleanSetting("Цены на слотах", true)).a(() -> {
        return Boolean.valueOf(this.b.l("FunTime") || this.b.l("SpookyTime"));
    });
    final MultiModeSetting d = (MultiModeSetting) new MultiModeSetting("Фильтр брони по", new BooleanSetting("Защите", false), new BooleanSetting("Аншип", true), new BooleanSetting("Починке", true), new BooleanSetting("Подводной ходьбе", true)).a(() -> {
        return Boolean.valueOf(this.c.a("Аукционный ассистент").c().booleanValue() && (this.b.l("FunTime") || this.b.l("SpookyTime")));
    });
    final MultiModeSetting e = (MultiModeSetting) new MultiModeSetting("Фильтр меча по", new BooleanSetting("Остроте", false), new BooleanSetting("Детекции", true), new BooleanSetting("Вампиризму", true), new BooleanSetting("Окислению", true), new BooleanSetting("Яду", true)).a(() -> {
        return Boolean.valueOf(this.c.a("Аукционный ассистент").c().booleanValue() && (this.b.l("FunTime") || this.b.l("SpookyTime")));
    });
    final MultiModeSetting f = (MultiModeSetting) new MultiModeSetting("Фильтр кирки по", new BooleanSetting("Эффективности", false), new BooleanSetting("Удаче", true), new BooleanSetting("Магнит", true), new BooleanSetting("Починке", true)).a(() -> {
        return Boolean.valueOf(this.c.a("Аукционный ассистент").c().booleanValue() && (this.b.l("FunTime") || this.b.l("SpookyTime")));
    });
    private final BindSetting g = a("Трапка", Items.NETHERITE_SCRAP, "FunTime", "SpookyTime");
    private final BindSetting h = a("Снежок заморозка", Items.SNOWBALL, "FunTime", "SpookyTime");
    private final BindSetting i = a("Пласт", Items.DRIED_KELP, "FunTime", "SpookyTime");
    private final BindSetting j = a("Дезориентация", Items.ENDER_EYE, "FunTime", "SpookyTime");
    private final BindSetting k = a("Явная пыль", Items.SUGAR, "FunTime", "SpookyTime");
    private final BindSetting l = a("Заряд ветра", Items.WIND_CHARGE, "FunTime", "SpookyTime");
    private final BindSetting m = a("Огненный заряд", Items.FIRE_CHARGE, "FunTime", "SpookyTime");
    private final BindSetting n = a("Божья аура", Items.PHANTOM_MEMBRANE, "FunTime", "SpookyTime");
    private final BindSetting o = a("Взрывная штучка", Items.FIRE_CHARGE, "HolyWorld");
    private final BindSetting p = a("Взрывная палочка", Items.BLAZE_ROD, "HolyWorld");
    private final BindSetting q = a("Взрывная трапка", Items.PRISMARINE_SHARD, "HolyWorld");
    private final BindSetting r = a("Стан", Items.NETHER_STAR, "HolyWorld");
    private final BindSetting s = a("Ком снега", Items.SNOWBALL, "HolyWorld");
    private final BooleanSetting t = (BooleanSetting) new BooleanSetting("Авто-божья аура", false).a(() -> {
        return Boolean.valueOf(this.b.l("FunTime") || this.b.l("SpookyTime"));
    });
    private final a u = new a();
    private final CounterUtil v = new CounterUtil();
    private Slot x = null;
    private int[] y = null;
    private int z = -1;

    @Generated
    public List<b> q() {
        return this.w;
    }

    public ServerAssistant() {
        a(this.b, this.c, this.d, this.e, this.f, this.g, this.h, this.i, this.j, this.k, this.m, this.l, this.n, this.o, this.p, this.q, this.r, this.s, this.t);
    }

    @EventTarget
    public void a(TickEvent event) {
        if (this.t.c().booleanValue()) {
            if (ServerUtil.a.a() || ServerUtil.d.a()) {
                boolean hasBadEffect = aM_.player.getStatusEffects().stream().anyMatch(effect -> {
                    return effect.getEffectType() == StatusEffects.WEAKNESS && effect.getAmplifier() >= 1 && ((double) effect.getDuration()) / 20.0d >= 15.0d;
                });
                if (InventoryUtil.b(Items.PHANTOM_MEMBRANE) != -1 && hasBadEffect && !aM_.player.getItemCooldownManager().isCoolingDown(Items.PHANTOM_MEMBRANE.getDefaultStack()) && this.v.a(5000L) && aM_.player.getAbsorptionAmount() <= 3.0f) {
                    Delta.h().d().v().b().a(Items.PHANTOM_MEMBRANE.getDefaultStack());
                    this.v.b();
                }
            }
        }
    }

    @EventTarget
    public void a(TooltipEvent event) {
        ItemStack stack = event.b();
        int price = ServerUtil.a.a(stack);
        if (price > 0 && stack.getCount() > 1) {
            List<Text> lines = event.c();
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).getString().contains("$ Ценa: ")) {
                    lines.add(i + 1, Text.literal("$").formatted(Formatting.GREEN).append(Text.literal(" Цена за штуку: ").formatted(Formatting.WHITE)).append(Text.literal(String.format(Locale.US, "%,d", Integer.valueOf(price))).formatted(Formatting.GREEN)));
                    return;
                }
            }
        }
    }

    @EventTarget
    public void a(ContainerEvent event) {
        HandledScreenAccessor handledScreenAccessorB = (HandledScreenAccessor) event.b();
        if (handledScreenAccessorB instanceof GenericContainerScreen) {
            HandledScreenAccessor handledScreenAccessor = (HandledScreenAccessor) (GenericContainerScreen) handledScreenAccessorB;
            ToIntFunction<ItemStack> price = ServerUtil.a.a() ? ServerUtil.a::a : ServerUtil.d::a;
            if (event.h() == ContainerEvent.Phase.TITLE) {
                if (event.i().getString().contains("Хранилище")) {
                    long storage = handledScreenAccessor.getScreenHandler().slots.stream().mapToLong(slot -> {
                        return ((long) Math.max(price.applyAsInt(slot.getStack()), 0)) * ((long) Math.max(slot.getStack().getCount(), 1));
                    }).sum();
                    String suffix = " - " + ((String) Stream.of(Map.entry(1000000000L, "ккк"), Map.entry(1000000L, "кк"), Map.entry(1000L, "к")).filter((Map.Entry<Long, String> unit) -> {
                        return storage >= ((Long) unit.getKey()).longValue();
                    }).map((Map.Entry<Long, String> unit2) -> {
                        return String.format(Locale.US, "%.0f%s", Double.valueOf(storage / ((Long) unit2.getKey()).longValue()), unit2.getValue());
                    }).findFirst().orElseGet(() -> {
                        return String.valueOf(storage);
                    }));
                    MutableText title = Text.empty();
                    event.i().visit((style, part) -> {
                        title.append(Text.literal(part).setStyle(style));
                        if (part.contains("Хранилище")) {
                            title.append(Text.literal(suffix).setStyle(style));
                        }
                        return Optional.empty();
                    }, Style.EMPTY);
                    event.a((Text) title);
                    return;
                }
                return;
            }
            if (handledScreenAccessor.getScreenHandler().slots.size() >= 90) {
                List<Slot> containerSlots = new ArrayList<>(event.e().subList(0, event.e().size() - 36));
                boolean useFilter = containerSlots.stream().anyMatch(slot2 -> {
                    return price.applyAsInt(slot2.getStack()) >= 0 && this.u.a(slot2.getStack());
                });
                if (event.h() == ContainerEvent.Phase.POST) {
                    HandledScreenAccessor accessor = (HandledScreenAccessor) handledScreenAccessor;
                    if (this.c.a("Аукционный ассистент").c().booleanValue()) {
                        if (this.x == null) {
                            Slot cheapest = null;
                            int minPrice = Integer.MAX_VALUE;
                            for (Slot slot3 : containerSlots) {
                                ItemStack stack = slot3.getStack();
                                int value = price.applyAsInt(stack);
                                if (!useFilter || this.u.a(stack)) {
                                    if (value >= 0 && value < minPrice) {
                                        minPrice = value;
                                        cheapest = slot3;
                                    }
                                }
                            }
                            this.x = cheapest;
                        }
                        if (this.x != null) {
                            float pulse = (float) ((Math.sin(((System.currentTimeMillis() % 100000) / 1000.0f) * 10.0f) + 1.0d) * 0.5d);
                            Delta.h().d().i().a(event.d(), accessor.getX() + ((Slot) this.x).x, accessor.getY() + ((Slot) this.x).y, 16.0f, 16.0f, ColorUtil.a(0, 255, 0, (int) (25.0f + (175.0f * pulse))));
                        }
                    }
                    if (this.c.a("Цены на слотах").c().booleanValue()) {
                        for (Slot slot : containerSlots) {
                            ItemStack stack = slot.getStack();
                            if (stack.isEmpty()) {
                                continue;
                            }
                            long itemPrice = getAuctionItemPrice(stack);
                            if (itemPrice <= 0) {
                                continue;
                            }
                            String text = formatCompactPrice(itemPrice);
                            if (text.isEmpty()) {
                                continue;
                            }
                            float slotX = accessor.getX() + slot.x;
                            float slotY = accessor.getY() + slot.y;
                            float fontSize = 5.5f;
                            float textW = Fonts.c.a(text, fontSize);
                            float textH = Fonts.c.a(fontSize);
                            float padX = 1.0f;
                            float padY = 0.5f;
                            float bgW = textW + (padX * 2.0f);
                            float bgH = textH + (padY * 2.0f);
                            float badgeX = slotX + 8.0f - (bgW / 2.0f);
                            float badgeY = slotY - 0.5f;

                            MatrixStack matrices = new MatrixStack();
                            matrices.push();
                            matrices.translate(0.0f, 0.0f, 300.0f);
                            Delta.h().d().i().a(matrices, badgeX, badgeY, bgW, bgH, 1.5f, 0xCC101014);
                            Fonts.c.a(matrices, text, badgeX + padX, badgeY + padY + 0.25f, fontSize, 0xFF55FF55);
                            matrices.pop();
                        }
                    }
                }
            }
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
            List<Text> tooltip = itemStack.getTooltip(Item.TooltipContext.DEFAULT, Interface.aM_.player, TooltipType.BASIC);
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
                            return Long.parseLong(digitsOnly);
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
        if (price < 0) {
            return "";
        }
        if (price < 1000) {
            return String.valueOf(price);
        }
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

    @EventTarget
    public void a(PacketEvent event) {
        if (event.c()) {
            GameMessageS2CPacket class_7439VarD = (GameMessageS2CPacket) event.d();
            if (class_7439VarD instanceof GameMessageS2CPacket) {
                GameMessageS2CPacket message = class_7439VarD;
                if (ServerUtil.a.a() && message.content().getString().equals("На этой анархии этот предмет не работает")) {
                    this.z = ServerUtil.a.d();
                }
            }
            InventoryS2CPacket class_2649VarD = (InventoryS2CPacket) event.d();
            if (class_2649VarD instanceof InventoryS2CPacket) {
                InventoryS2CPacket packet = class_2649VarD;
                if (this.c.a("Сортировать по цене").c().booleanValue() && (ServerUtil.a.a() || ServerUtil.d.a())) {
                    List<ItemStack> contents = packet.contents();
                    int chestSlots = contents.size() > 36 ? contents.size() - 36 : contents.size();
                    this.y = null;
                    if (chestSlots >= 44 && packet.syncId() != 0) {
                        ToIntFunction<ItemStack> price = ServerUtil.a.a() ? ServerUtil.a::a : ServerUtil.d::a;
                        boolean useFilter = contents.subList(0, chestSlots).stream().anyMatch(stack -> {
                            return price.applyAsInt(stack) >= 0 && this.u.a(stack);
                        });
                        int[] prices = new int[chestSlots];
                        for (int i = 0; i < chestSlots; i++) {
                            ItemStack stack2 = contents.get(i);
                            int value = (stack2.isEmpty() || (useFilter && !this.u.a(stack2))) ? -1 : price.applyAsInt(stack2);
                            prices[i] = value < 0 ? Integer.MAX_VALUE : value;
                        }
                        Integer[] order = (Integer[]) IntStream.range(0, chestSlots).boxed().sorted(Comparator.comparingInt(i2 -> {
                            return prices[i2.intValue()];
                        })).toArray(x$0 -> {
                            return new Integer[x$0];
                        });
                        List<ItemStack> original = new ArrayList<>(contents);
                        this.y = new int[chestSlots];
                        for (int display = 0; display < chestSlots; display++) {
                            this.y[display] = order[display].intValue();
                            contents.set(display, original.get(order[display].intValue()));
                        }
                    }
                }
            }
            ScreenHandlerSlotUpdateS2CPacketAccessor screenHandlerSlotUpdateS2CPacketAccessorD = (ScreenHandlerSlotUpdateS2CPacketAccessor) event.d();
            if (screenHandlerSlotUpdateS2CPacketAccessorD instanceof ScreenHandlerSlotUpdateS2CPacket) {
                ScreenHandlerSlotUpdateS2CPacketAccessor screenHandlerSlotUpdateS2CPacketAccessor = (ScreenHandlerSlotUpdateS2CPacketAccessor) screenHandlerSlotUpdateS2CPacketAccessorD;
                if (this.y != null && screenHandlerSlotUpdateS2CPacketAccessor.getSyncId() != 0 && screenHandlerSlotUpdateS2CPacketAccessor.getSyncId() == aM_.player.currentScreenHandler.syncId && screenHandlerSlotUpdateS2CPacketAccessor.getSlot() >= 0 && screenHandlerSlotUpdateS2CPacketAccessor.getSlot() < this.y.length) {
                    for (int display2 = 0; display2 < this.y.length; display2++) {
                        if (this.y[display2] == screenHandlerSlotUpdateS2CPacketAccessor.getSlot()) {
                            screenHandlerSlotUpdateS2CPacketAccessor.setSlot(display2);
                            break;
                        }
                    }
                }
                this.x = null;
            }
        }
        if (event.b()) {
            ClickSlotC2SPacketAccessor clickSlotC2SPacketAccessorD = (ClickSlotC2SPacketAccessor) event.d();
            if (event.d() instanceof ClickSlotC2SPacket) {
                ClickSlotC2SPacketAccessor clickSlotC2SPacketAccessor = (ClickSlotC2SPacketAccessor) clickSlotC2SPacketAccessorD;
                if (this.y != null && clickSlotC2SPacketAccessor.getSyncId() != 0 && clickSlotC2SPacketAccessor.getSyncId() == aM_.player.currentScreenHandler.syncId && clickSlotC2SPacketAccessor.getSlot() >= 0 && clickSlotC2SPacketAccessor.getSlot() < this.y.length) {
                    clickSlotC2SPacketAccessor.setSlot((short) this.y[clickSlotC2SPacketAccessor.getSlot()]);
                }
            }
        }
    }

    private BindSetting a(String name, Item item, String... servers) {
        BindSetting setting = (BindSetting) new BindSetting(name, -1).a(() -> {
            ItemCooldownManagerAccessor itemCooldownManagerAccessorMethod_7357 = (ItemCooldownManagerAccessor) (ItemCooldownManagerAccessor) aM_.player.getItemCooldownManager();
            if (((net.minecraft.entity.player.ItemCooldownManager) (Object) itemCooldownManagerAccessorMethod_7357).isCoolingDown(item.getDefaultStack())) {
                ItemCooldownManagerAccessor accessor = itemCooldownManagerAccessorMethod_7357;
                Object entry = accessor.getEntries().get(Registries.ITEM.getId(item));
                Locale locale = Locale.US;
                Object[] objArr = new Object[1];
                objArr[0] = Float.valueOf(entry != null ? Math.max(((platform.inject.accessors.ItemCooldownEntryAccessor) entry).getEndTick() - accessor.getTick(), 0) / 20.0f : 0.0f);
                ChatUtil.a((Object) ("&c" + name + "&7 - имеет задержку &c" + String.format(locale, "%.1fс", objArr)));
                return;
            }
            int hotbarSlot = InventoryUtil.a(item, true);
            if (hotbarSlot == -1) {
                ChatUtil.a((Object) ("&c" + name + "&7 - нет в хотбаре"));
            } else {
                aM_.player.getInventory().setSelectedSlot(hotbarSlot);
                if (aM_.player.networkHandler != null) {
                    aM_.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(hotbarSlot));
                }
            }
        }).a(() -> {
            Stream stream = Arrays.stream(servers);
            ModeSetting modeSetting = this.b;
            Objects.requireNonNull(modeSetting);
            return Boolean.valueOf(Arrays.stream(servers).anyMatch(server -> modeSetting.l(server)));
        });
        if (this.w == null) {
            this.w = new ArrayList();
        }
        this.w.add(new b(new AnimationUtil(), setting, item));
        return setting;
    }

    public class a {
        public a() {
        }

        public boolean a(ItemStack stack) {
            if (stack.isIn(ItemTags.TRIMMABLE_ARMOR) || stack.isIn(ItemTags.ARMOR_ENCHANTABLE)) {
                return b(stack);
            }
            if (stack.isIn(ItemTags.SWORDS)) {
                return c(stack);
            }
            if (stack.isIn(ItemTags.PICKAXES)) {
                return d(stack);
            }
            return true;
        }

        private boolean b(ItemStack stack) {
            if (stack.isIn(ItemTags.TRIMMABLE_ARMOR) || stack.isIn(ItemTags.ARMOR_ENCHANTABLE)) {
                EnchantmentProcessor enchament = new EnchantmentProcessor();
                if (ServerAssistant.this.d.a("Защите").c().booleanValue()) {
                    enchament.a(Enchantments.UNBREAKING, 4);
                    enchament.a(Enchantments.PROTECTION, 5);
                }
                if (ServerAssistant.this.d.a("Аншип").c().booleanValue()) {
                    enchament.b(Enchantments.THORNS);
                }
                if (ServerAssistant.this.d.a("Починке").c().booleanValue()) {
                    enchament.a(Enchantments.MENDING, 1);
                }
                if (ServerAssistant.this.d.a("Подводной ходьбе").c().booleanValue() && stack.get(DataComponentTypes.EQUIPPABLE) != null && ((EquippableComponent) stack.get(DataComponentTypes.EQUIPPABLE)).slot() == EquipmentSlot.FEET) {
                    enchament.a(Enchantments.DEPTH_STRIDER, 1);
                }
                return enchament.a(stack);
            }
            return true;
        }

        private boolean c(ItemStack stack) {
            if (stack.isIn(ItemTags.SWORDS)) {
                EnchantmentProcessor enchantmentProcessor = new EnchantmentProcessor().b(Enchantments.KNOCKBACK, 2);
                if (ServerAssistant.this.e.a("Остроте").c().booleanValue()) {
                    enchantmentProcessor.a(Enchantments.SHARPNESS, 6);
                }
                enchantmentProcessor.b(Enchantments.KNOCKBACK, 1);
                if (enchantmentProcessor.a(stack)) {
                    DescriptionProcessor descriptionProcessor = new DescriptionProcessor();
                    descriptionProcessor.b("Нестабильность ");
                    descriptionProcessor.b("Нестабильный ");
                    if (ServerAssistant.this.e.a("Детекции").c().booleanValue()) {
                        descriptionProcessor.a("Детекция", 2);
                    }
                    if (ServerAssistant.this.e.a("Вампиризму").c().booleanValue()) {
                        descriptionProcessor.a("Вампиризм", 2);
                    }
                    if (ServerAssistant.this.e.a("Окислению").c().booleanValue()) {
                        descriptionProcessor.a("Окисление", 2);
                    }
                    if (ServerAssistant.this.e.a("Яду").c().booleanValue()) {
                        descriptionProcessor.a("Яд", 3);
                    }
                    return descriptionProcessor.a(stack);
                }
                return false;
            }
            return false;
        }

        private boolean d(ItemStack stack) {
            if (stack.isIn(ItemTags.PICKAXES)) {
                if (ServerAssistant.this.f.a("Починке").c().booleanValue() && !new EnchantmentProcessor().a(Enchantments.MENDING, 1).a(stack)) {
                    return false;
                }
                DescriptionProcessor descriptionProcessor = new DescriptionProcessor();
                if (ServerAssistant.this.f.a("Удаче").c().booleanValue()) {
                    descriptionProcessor.a("Удача", 5);
                }
                if (ServerAssistant.this.f.a("Эффективности").c().booleanValue()) {
                    descriptionProcessor.a("Эффективность", 4);
                }
                if (ServerAssistant.this.f.a("Магнит").c().booleanValue()) {
                    descriptionProcessor.a("Магнит");
                }
                return descriptionProcessor.a(stack);
            }
            return false;
        }
    }

    public String getBindNameForItem(ItemStack stack) {
        if (!m() || stack == null || stack.isEmpty()) {
            return null;
        }
        if (this.w != null) {
            for (b entry : this.w) {
                if (entry.c() == stack.getItem()) {
                    BindSetting bind = entry.b();
                    if (bind.e().get() && bind.c() != null && bind.c() != -1) {
                        return KeyUtil.b(bind.c());
                    }
                }
            }
        }
        return null;
    }

    public static final class b {
        private final AnimationUtil a;
        private final BindSetting b;
        private final Item c;

        public b(AnimationUtil animationUtil, BindSetting bind, Item item) {
            this.a = animationUtil;
            this.b = bind;
            this.c = item;
        }
public AnimationUtil a() {
            return this.a;
        }

        public BindSetting b() {
            return this.b;
        }

        public Item c() {
            return this.c;
        }
    }
}
