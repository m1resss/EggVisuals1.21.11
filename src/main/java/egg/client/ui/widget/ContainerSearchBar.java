package egg.client.ui.widget;

import egg.client.ui.theme.Theme;
import egg.client.util.ColorUtil;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.text.Normalizer;
import java.util.Locale;

public final class ContainerSearchBar {
    private static final int HEIGHT = 17;
    private static final int COUNTER_WIDTH = 19;
    private final TextFieldWidget field;
    private final ItemStack[] cachedItems;
    private final String[] searchableNames;
    private final boolean[] matches;
    private final int x;
    private final int y;
    private final int width;
    private final boolean outsideCounter;
    private String[] terms = new String[0];
    private int count;

    public ContainerSearchBar(TextRenderer font, int slots, int screenWidth, int guiX, int guiY, int guiWidth, String query) {
        boolean side = guiY < HEIGHT + 5 && screenWidth - guiX - guiWidth >= 46;
        width = side ? Math.min(112, screenWidth - guiX - guiWidth - 9) : Math.min(guiWidth, 150);
        outsideCounter = width < 74;
        x = side ? guiX + guiWidth + 5 : guiX + (guiWidth - width) / 2;
        y = side ? Math.max(3, guiY + 4) : Math.max(2, guiY - HEIGHT - 4);
        int fieldWidth = width - 12 - (outsideCounter ? 0 : COUNTER_WIDTH);
        field = new TextFieldWidget(font, x + 6, y + 5, fieldWidth, 10, Text.literal("Поиск предметов"));
        field.setDrawsBackground(false);
        field.setTextShadow(false);
        field.setMaxLength(100);
        field.setPlaceholder(Text.literal(side ? "Поиск…" : "Поиск…  Ctrl+F").withColor(0xA4A7B3));
        field.setChangedListener(value -> {
            String normalized = normalize(value).strip();
            terms = normalized.isEmpty() ? new String[0] : normalized.split("(?U)\\s+");
        });
        field.setText(query);
        cachedItems = new ItemStack[slots];
        searchableNames = new String[slots];
        matches = new boolean[slots];
    }

    public TextFieldWidget field() {
        return field;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + HEIGHT;
    }

    public void update(ScreenHandler handler) {
        count = 0;
        if (terms.length == 0) return;
        for (int i = 0; i < matches.length; i++) {
            Slot slot = handler.slots.get(i);
            ItemStack stack = slot.getStack();
            matches[i] = slot.isEnabled() && !stack.isEmpty() && matches(i, stack);
            if (matches[i]) count++;
        }
    }

    private boolean matches(int index, ItemStack stack) {
        if (cachedItems[index] == null || !ItemStack.areItemsAndComponentsEqual(cachedItems[index], stack)) {
            cachedItems[index] = stack.copy();
            StringBuilder text = new StringBuilder(stack.getName().getString());
            var lore = stack.get(DataComponentTypes.LORE);
            if (lore != null) {
                for (Text line : lore.lines()) text.append(' ').append(line.getString());
            }
            searchableNames[index] = normalize(text.toString());
        }
        for (String term : terms) {
            if (!searchableNames[index].contains(term)) return false;
        }
        return true;
    }

    public void drawSlot(DrawContext context, Slot slot) {
        if (terms.length == 0 || slot.id < 0 || slot.id >= matches.length || !matches[slot.id]) return;
        int accent = Theme.getAccent();
        context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, ColorUtil.withAlpha(accent, 105));
        context.drawStrokedRectangle(slot.x - 1, slot.y - 1, 18, 18, ColorUtil.withAlpha(accent, 255));
    }

    public void render(DrawContext context, TextRenderer font, int mouseX, int mouseY, float delta) {
        int border = field.isFocused() ? ColorUtil.withAlpha(Theme.getAccent(), 185) : 0xFF42454F;
        context.fill(x, y, x + width, y + HEIGHT, 0xF0191B22);
        context.drawStrokedRectangle(x, y, width, HEIGHT, border);
        field.render(context, mouseX, mouseY, delta);
        if (terms.length == 0) return;
        String result = Integer.toString(count);
        context.drawText(font, result, x + width - font.getWidth(result) - 6, y + (outsideCounter ? HEIGHT + 3 : 5),
                count > 0 ? 0xFFDDE1F0 : 0xFFE2A5A5, outsideCounter);
    }

    private static String normalize(String text) {
        String plain = Formatting.strip(text);
        return Normalizer.normalize(plain, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё', 'е');
    }
}
