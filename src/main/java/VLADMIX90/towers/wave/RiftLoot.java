package VLADMIX90.towers.wave;

import VLADMIX90.towers.item.CustomItemFactory;
import VLADMIX90.towers.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;

public final class RiftLoot {
    private RiftLoot() { }

    /** Rolls one entry; {@code chance} is a percent and may be fractional (0.1 = 0.1%). */
    public static Optional<LootDrop> roll(RiftLootSpec spec, boolean guaranteed, Random random) {
        if (!guaranteed && random.nextDouble() * 100 >= spec.chance()) return Optional.empty();
        int span = Math.max(1, spec.max() - spec.min() + 1);
        return Optional.of(new LootDrop(spec, material(spec, random), spec.min() + random.nextInt(span)));
    }

    /**
     * Rolls ordinary entries independently and at most one scroll, with a default 50% shared chance.
     * An entry with {@code replaces-all} wins alone when selected.
     */
    public static List<LootDrop> rollAll(List<RiftLootSpec> specs, Map<String, String> guaranteedDrops,
                                         String questId, Random random) {
        return rollAll(specs, guaranteedDrops, questId, random, 50);
    }

    public static List<LootDrop> rollAll(List<RiftLootSpec> specs, Map<String, String> guaranteedDrops,
                                         String questId, Random random, double scrollChance) {
        for (RiftLootSpec spec : specs) {
            if (spec.skinId() != null || !spec.replacesAll()) continue;
            Optional<LootDrop> drop = roll(spec, guaranteed(spec, guaranteedDrops, questId), random);
            if (drop.isPresent()) return List.of(drop.get());
        }
        Optional<LootDrop> scroll = rollScroll(specs, scrollChance, random);
        if (scroll.isPresent() && scroll.get().spec().replacesAll()) return List.of(scroll.get());
        List<LootDrop> drops = new ArrayList<>();
        for (RiftLootSpec spec : specs) {
            if (spec.skinId() != null || spec.replacesAll()) continue;
            roll(spec, guaranteed(spec, guaranteedDrops, questId), random).ifPresent(drops::add);
        }
        scroll.ifPresent(drops::add);
        return drops;
    }

    /** One shared gate followed by one relative-weight selection, without individual scroll rolls. */
    private static Optional<LootDrop> rollScroll(List<RiftLootSpec> specs, double chance, Random random) {
        List<RiftLootSpec> scrolls = specs.stream()
                .filter(spec -> spec.skinId() != null && Double.isFinite(spec.weight()) && spec.weight() > 0)
                .toList();
        if (scrolls.isEmpty() || random.nextDouble() * 100 >= chance) return Optional.empty();
        // Scaling first keeps the sum finite even for weights near Double.MAX_VALUE.
        double maximum = scrolls.stream().mapToDouble(RiftLootSpec::weight).max().orElseThrow();
        double total = scrolls.stream().mapToDouble(spec -> spec.weight() / maximum).sum();
        double remaining = random.nextDouble() * total;
        RiftLootSpec selected = scrolls.getLast();
        for (RiftLootSpec spec : scrolls) {
            remaining -= spec.weight() / maximum;
            if (remaining < 0) {
                selected = spec;
                break;
            }
        }
        return Optional.of(new LootDrop(selected, selected.material(), 1));
    }

    /** True when this quest is the one the cycle guarantees this entry for. */
    private static boolean guaranteed(RiftLootSpec spec, Map<String, String> guaranteedDrops, String questId) {
        return spec.guaranteedInCycle() && questId != null && questId.equals(guaranteedDrops.get(spec.id()));
    }

    /** Picks the material of the drop: the configured one or one of its alternatives. */
    private static Material material(RiftLootSpec spec, Random random) {
        List<Material> options = new ArrayList<>();
        options.add(spec.material());
        List<Material> alternatives = spec.alternatives();
        if (alternatives != null) options.addAll(alternatives);
        return options.get(random.nextInt(options.size()));
    }

    public static ItemStack build(LootDrop drop) {
        return build(drop, id -> null);
    }

    /** Skin entries preserve the factory's metadata and always yield one non-stackable scroll. */
    public static ItemStack build(LootDrop drop, Function<String, ItemStack> scrollFactory) {
        RiftLootSpec spec = drop.spec();
        if (spec.skinId() != null) {
            ItemStack scroll = scrollFactory.apply(spec.skinId());
            if (scroll != null) scroll.setAmount(1);
            return scroll;
        }
        ItemStack item = CustomItemFactory.modelItem(drop.material(), spec.customModelData(), spec.itemModel());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (spec.name() != null && !spec.name().isBlank()) meta.displayName(Text.flat(Text.parse(spec.name())));
            if (spec.lore() != null && !spec.lore().isEmpty()) {
                meta.lore(spec.lore().stream().map(line -> Text.flat(Text.parse(line))).toList());
            }
            item.setItemMeta(meta);
        }
        item.setAmount(drop.amount());
        return item;
    }
}
