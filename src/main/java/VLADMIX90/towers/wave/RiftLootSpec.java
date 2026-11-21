package VLADMIX90.towers.wave;

import org.bukkit.Material;

import java.util.List;

public record RiftLootSpec(String id, Material material, List<Material> alternatives,
                           String itemModel, String customModelData, String name, List<String> lore,
                           int min, int max, double chance, boolean guaranteedInCycle,
                           boolean replacesAll, String skinId, double weight) {
    public RiftLootSpec(String id, Material material, List<Material> alternatives,
                        String itemModel, String customModelData, String name, List<String> lore,
                        int min, int max, double chance, boolean guaranteedInCycle,
                        boolean replacesAll, String skinId) {
        this(id, material, alternatives, itemModel, customModelData, name, lore,
                min, max, chance, guaranteedInCycle, replacesAll, skinId, chance);
    }
}
