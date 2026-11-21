package VLADMIX90.towers.item;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Creates model items using the 1.21.7 CustomModelData component. */
public final class CustomItemFactory {
    private CustomItemFactory() {}

    public static ItemStack modelItem(Material material, String customModelData, String itemModel) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        applyItemModel(meta, itemModel);

        if (customModelData != null && !customModelData.isBlank()) {
            var model = meta.getCustomModelDataComponent();
            String value = customModelData.trim();
            model.setStrings(List.of(value));
            try {
                model.setFloats(List.of(Float.parseFloat(value)));
            } catch (NumberFormatException ignored) {
                // Non-numeric values are intended for the string-based select model. The float list
                // is cleared so the component stays exactly {strings:[...]} and matches items given
                // by hand with /give.
                model.setFloats(List.of());
            }
            meta.setCustomModelDataComponent(model);
        }
        item.setItemMeta(meta);
        return item;
    }

    static void applyItemModel(ItemMeta meta, String itemModel) {
        if (itemModel == null || itemModel.isBlank()) return;
        try {
            NamespacedKey key = NamespacedKey.fromString(itemModel.trim());
            if (key != null) meta.setItemModel(key);
        } catch (IllegalArgumentException ignored) {
            // Invalid model identifiers are ignored so one bad config value cannot break a menu.
        }
    }
}
