package delta.module.misc;

import static delta.core.Interface.aM_;
import delta.core.Category;
import delta.core.Module;
import delta.core.ModuleRegister;
import delta.setting.BooleanSetting;
import java.util.List;
import java.util.Locale;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

@ModuleRegister(a = "Container Searcher", b = "Поиск предметов в сундуках и шалкерах с подсветкой и затемнением", c = Category.Misc)
public class ContainerSearcher extends Module {
    private static ContainerSearcher instance;

    private final BooleanSetting searchLore = new BooleanSetting("Искать в описании", true);
    private final BooleanSetting dimNonMatching = new BooleanSetting("Затемнять остальные", true);
    private String currentQuery = "";

    public ContainerSearcher() {
        instance = this;
        a(this.searchLore, this.dimNonMatching);
    }

    public static ContainerSearcher getInstance() {
        return instance;
    }

    public String getCurrentQuery() {
        return this.currentQuery;
    }

    public void setCurrentQuery(String query) {
        this.currentQuery = query != null ? query : "";
    }

    public boolean isDimNonMatching() {
        return this.dimNonMatching.c().booleanValue();
    }

    public boolean matches(ItemStack stack, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        String cleanQuery = query.trim().toLowerCase(Locale.ROOT);

        // Name match
        if (stack.getName().getString().toLowerCase(Locale.ROOT).contains(cleanQuery)) {
            return true;
        }

        // Registry ID match (e.g. "totem", "diamond", "golden_apple")
        String itemId = Registries.ITEM.getId(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
        if (itemId.contains(cleanQuery)) {
            return true;
        }

        // Tooltip / Lore match
        if (this.searchLore.c().booleanValue()) {
            try {
                List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, aM_.player, TooltipType.BASIC);
                for (Text line : tooltip) {
                    if (line.getString().toLowerCase(Locale.ROOT).contains(cleanQuery)) {
                        return true;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        return false;
    }
}
