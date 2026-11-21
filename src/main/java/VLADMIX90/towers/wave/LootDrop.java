package VLADMIX90.towers.wave;

import org.bukkit.Material;

/** A rolled drop: the entry it came from, the material it is made of and how many. */
public record LootDrop(RiftLootSpec spec, Material material, int amount) { }
