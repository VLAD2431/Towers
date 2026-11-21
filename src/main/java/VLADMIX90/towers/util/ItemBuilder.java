package VLADMIX90.towers.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import VLADMIX90.towers.item.CustomItemFactory;

import java.util.List;

public final class ItemBuilder {
    private ItemBuilder() {}
    public static ItemStack model(Material material, String customModelData, String name, List<String> lore) {
        return model(material, customModelData, null, name, lore, false);
    }
    public static ItemStack model(Material material, String customModelData, String itemModel, String name, List<String> lore) {
        return model(material, customModelData, itemModel, name, lore, false);
    }
    public static ItemStack model(Material material, String customModelData, String itemModel, String name, List<String> lore, boolean hideTooltip) {
        ItemStack item = CustomItemFactory.modelItem(material, customModelData, itemModel);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.flat(Text.parse(name)));
        meta.lore(lore.stream().map(line -> Text.flat(Text.parse(line))).toList());
        if (hideTooltip) meta.setHideTooltip(true);
        item.setItemMeta(meta);
        return item;
    }
}
