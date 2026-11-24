package egg.client.module.misc;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class ItemComparison {
    private static final int MAX_CHANGES = 6;
    private static final Pattern LISTING_LABEL = Pattern.compile(
            "^(?:цена(?: за штуку)?|продавец|владелец|покупатель|истекает|время продажи|до конца|выставил(?: игрок)?|оставшееся время|price|seller|expires)\\s*:.*");
    private ItemStack pinned = ItemStack.EMPTY;
    private WeakReference<ClientWorld> world = new WeakReference<>(null);

    static boolean isAltHeld() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    void pin(MinecraftClient client, ItemStack stack) {
        world = new WeakReference<>(client.world);
        pinned = stack.copy();
    }

    void clear() {
        pinned = ItemStack.EMPTY;
        world.clear();
    }

    void append(MinecraftClient client, ItemStack candidate, List<Text> tooltip) {
        if (client.player == null || candidate.isEmpty()) return;
        if (world.get() != client.world) clear();
        ItemStack reference = pinned.isEmpty() ? findEquipped(client, candidate) : pinned;
        tooltip.add(Text.empty());
        if (reference.isEmpty()) {
            tooltip.add(Text.literal("Alt + СКМ — закрепить предмет").formatted(Formatting.GRAY));
            return;
        }

        tooltip.add(Text.literal(shorten(client, "Сравнение с " + reference.getName().getString())).formatted(Formatting.GRAY));
        List<String> before = comparisonLines(client, reference);
        List<String> added = new ArrayList<>();
        for (String line : comparisonLines(client, candidate)) {
            if (!before.remove(line)) added.add(line);
        }
        List<Text> changes = new ArrayList<>();
        if (reference.isDamageable() && candidate.isDamageable()) {
            String from = durability(reference);
            String to = durability(candidate);
            if (!from.equals(to)) changes.add(Text.literal("Прочность " + from + " → " + to).formatted(Formatting.GRAY));
        }
        for (String line : added) changes.add(Text.literal(shorten(client, "+ " + line)).formatted(Formatting.GREEN));
        for (String line : before) changes.add(Text.literal(shorten(client, "− " + line)).formatted(Formatting.RED));
        tooltip.addAll(changes.subList(0, Math.min(MAX_CHANGES, changes.size())));
        if (changes.size() > MAX_CHANGES) {
            tooltip.add(Text.literal("Ещё отличий: " + (changes.size() - MAX_CHANGES)).formatted(Formatting.GRAY));
        } else if (changes.isEmpty()) {
            tooltip.add(Text.literal("Описание совпадает").formatted(Formatting.GRAY));
        }
    }

    private static ItemStack findEquipped(MinecraftClient client, ItemStack candidate) {
        var equipment = candidate.get(DataComponentTypes.EQUIPPABLE);
        if (equipment != null) {
            ItemStack worn = client.player.getEquippedStack(equipment.slot());
            if (!worn.isEmpty() && worn != candidate) return worn;
        }
        for (ItemStack held : List.of(client.player.getMainHandStack(), client.player.getOffHandStack())) {
            if (held != candidate && held.isOf(candidate.getItem())) return held;
        }
        return ItemStack.EMPTY;
    }

    private static List<String> comparisonLines(MinecraftClient client, ItemStack stack) {
        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.BASIC);
        List<String> lines = new ArrayList<>();
        for (int i = 1; i < tooltip.size(); i++) {
            String line = tooltip.get(i).getString().strip();
            if (!line.isBlank() && !isListingLine(line)) lines.add(line);
        }
        return lines;
    }

    private static boolean isListingLine(String line) {
        String plain = line.toLowerCase(Locale.ROOT).replace("ценa", "цена").replaceFirst("^[^\\p{L}\\d]+", "");
        return LISTING_LABEL.matcher(plain).matches()
                || plain.startsWith("нажмите, чтобы купить")
                || plain.startsWith("нажмите для покупки")
                || plain.startsWith("лкм — купить")
                || plain.startsWith("товар не актуален");
    }

    private static String durability(ItemStack stack) {
        return (stack.getMaxDamage() - stack.getDamage()) + "/" + stack.getMaxDamage();
    }

    private static String shorten(MinecraftClient client, String text) {
        int maxWidth = Math.min(220, Math.max(50, client.getWindow().getScaledWidth() - 28));
        if (client.textRenderer.getWidth(text) <= maxWidth) return text;
        return client.textRenderer.trimToWidth(text, maxWidth - client.textRenderer.getWidth("…")) + "…";
    }
}
