package VLADMIX90.towers.wave;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RiftLootTest {

    private static List<RiftLootSpec> parseLoot(List<Map<String, Object>> entries) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rift-loot", entries);
        return new WaveConfig(config).loot();
    }

    private static Map<String, Object> loot(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    private static RiftLootSpec spec(Object... keyValues) {
        List<RiftLootSpec> specs = parseLoot(List.of(loot(keyValues)));
        assertEquals(1, specs.size());
        return specs.get(0);
    }

    @Test
    void parsesFractionalChance() {
        assertEquals(0.1, spec("material", "CAKE", "chance", 0.1).chance());
        assertEquals(25.0, spec("material", "CAKE", "chance", 25).chance());
    }

    @Test
    void clampsChanceToHundred() {
        assertEquals(100.0, spec("material", "CAKE", "chance", 250).chance());
        assertEquals(0.0, spec("material", "CAKE", "chance", -5).chance());
    }

    @Test
    void parsesAlternatives() {
        RiftLootSpec spec = spec("material", "TORCHFLOWER_SEEDS",
                "alternatives", List.of("PITCHER_POD", "NOT_A_MATERIAL"));
        assertEquals(Material.TORCHFLOWER_SEEDS, spec.material());
        assertEquals(List.of(Material.PITCHER_POD), spec.alternatives());
    }

    @Test
    void parsesReplacesAll() {
        assertTrue(spec("material", "CAKE", "replaces-all", true).replacesAll());
        assertFalse(spec("material", "CAKE").replacesAll());
    }

    @Test
    void parsesOptionalSkinIdWithoutChangingMaterialLoot() {
        assertEquals("blue_sword", spec("skin-id", "blue_sword", "chance", 25).skinId());
        assertNull(spec("material", "CAKE").skinId());
    }

    @Test
    void skinScrollDoesNotRequireAValidMaterial() {
        assertEquals("blue_sword", spec("skin-id", "blue_sword", "material", "NOT_A_MATERIAL").skinId());
    }

    @Test
    void buildsAuthenticScrollWithoutReplacingItsMetadata() {
        RiftLootSpec spec = spec("skin-id", "blue_sword", "name", "ignored", "min", 5);
        org.bukkit.inventory.ItemStack scroll = org.mockito.Mockito.mock(org.bukkit.inventory.ItemStack.class);
        assertSame(scroll, RiftLoot.build(new LootDrop(spec, Material.STONE, 5), id -> {
            assertEquals("blue_sword", id);
            return scroll;
        }));
        org.mockito.Mockito.verify(scroll).setAmount(1);
        org.mockito.Mockito.verifyNoMoreInteractions(scroll);
    }

    @Test
    void skipsUnavailableScrollInsteadOfCreatingOrdinaryItem() {
        RiftLootSpec spec = spec("skin-id", "unknown");
        assertNull(RiftLoot.build(new LootDrop(spec, Material.STONE, 1), id -> null));
    }

    @Test
    void dropsNothingWhenChanceMisses() {
        RiftLootSpec spec = spec("material", "CAKE", "chance", 0);
        assertTrue(RiftLoot.roll(spec, false, new Random(1)).isEmpty());
    }

    @Test
    void dropsAtFullChance() {
        RiftLootSpec spec = spec("material", "CAKE", "chance", 100);
        assertTrue(RiftLoot.roll(spec, false, new Random(1)).isPresent());
    }

    @Test
    void guaranteedDropIgnoresChance() {
        RiftLootSpec spec = spec("material", "CAKE", "chance", 0);
        assertTrue(RiftLoot.roll(spec, true, new Random(1)).isPresent());
    }

    @Test
    void keepsAmountInRange() {
        RiftLootSpec spec = spec("material", "DEEPSLATE_DIAMOND_ORE", "min", 2, "max", 7, "chance", 100);
        Random random = new Random(7);
        for (int i = 0; i < 50; i++) {
            LootDrop drop = RiftLoot.roll(spec, false, random).orElseThrow();
            assertTrue(drop.amount() >= 2 && drop.amount() <= 7, "amount " + drop.amount());
        }
    }

    @Test
    void picksOneOfTheAlternativeMaterials() {
        RiftLootSpec spec = spec("material", "TORCHFLOWER_SEEDS",
                "alternatives", List.of("PITCHER_POD"), "chance", 100);
        Random random = new Random(3);
        List<Material> seen = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Material material = RiftLoot.roll(spec, false, random).orElseThrow().material();
            assertTrue(material == Material.TORCHFLOWER_SEEDS || material == Material.PITCHER_POD, "material " + material);
            if (!seen.contains(material)) seen.add(material);
        }
        assertEquals(2, seen.size(), "both materials must appear");
    }

    @Test
    void exclusiveDropReplacesEverythingElse() {
        RiftLootSpec ore = spec("material", "DEEPSLATE_DIAMOND_ORE", "chance", 100);
        RiftLootSpec cake = spec("material", "CAKE", "chance", 100, "replaces-all", true);
        List<LootDrop> drops = RiftLoot.rollAll(List.of(ore, cake), Map.of(), null, new Random(1));
        assertEquals(1, drops.size());
        assertEquals(Material.CAKE, drops.get(0).material());
    }

    @Test
    void regularDropsAllTogether() {
        RiftLootSpec ore = spec("material", "DEEPSLATE_DIAMOND_ORE", "chance", 100);
        RiftLootSpec cake = spec("material", "CAKE", "chance", 100);
        List<LootDrop> drops = RiftLoot.rollAll(List.of(ore, cake), Map.of(), null, new Random(1));
        assertEquals(2, drops.size());
    }

    @Test
    void cycleGuaranteedDropIsKept() {
        RiftLootSpec ore = spec("material", "DEEPSLATE_DIAMOND_ORE", "chance", 0,
                "custom-model-data", "ore", "guaranteed-in-cycle", true);
        Map<String, String> guaranteedDrops = Map.of("ore", "quest-1");
        List<LootDrop> drops = RiftLoot.rollAll(List.of(ore), guaranteedDrops, "quest-1", new Random(1));
        assertEquals(1, drops.size());
        assertEquals(Material.DEEPSLATE_DIAMOND_ORE, drops.get(0).material());
    }

    @Test
    void cycleGuaranteeOnlyAppliesToItsQuest() {
        RiftLootSpec ore = spec("material", "DEEPSLATE_DIAMOND_ORE", "chance", 0,
                "custom-model-data", "ore", "guaranteed-in-cycle", true);
        Map<String, String> guaranteedDrops = Map.of("ore", "quest-1");
        assertTrue(RiftLoot.rollAll(List.of(ore), guaranteedDrops, "quest-2", new Random(1)).isEmpty());
    }

    private static Random draws(double... values) {
        return new Random(0) {
            private int index;

            @Override
            public double nextDouble() {
                assertTrue(index < values.length, "unexpected probability roll");
                return values[index++];
            }
        };
    }

    private static List<RiftLootSpec> scrolls() {
        return parseLoot(List.of(
                loot("id", "common", "skin-id", "common_skin", "chance", 100, "weight", 7, "min", 5, "max", 9),
                loot("id", "rare", "skin-id", "rare_skin", "chance", 100, "weight", 2),
                loot("id", "legendary", "skin-id", "legendary_skin", "chance", 100, "weight", 1)));
    }

    @Test
    void successfulSharedGateSelectsExactlyOneScrollWithAmountOne() {
        List<LootDrop> drops = RiftLoot.rollAll(scrolls(), Map.of(), null, draws(0.49, 0.0));
        assertEquals(1, drops.size());
        assertEquals("common", drops.getFirst().spec().id());
        assertEquals(1, drops.getFirst().amount());
    }

    @Test
    void sharedGateMissesAtExactlyFiftyPercent() {
        assertTrue(RiftLoot.rollAll(scrolls(), Map.of(), null, draws(0.50)).isEmpty());
    }

    @Test
    void weightedSelectionUsesSevenTwoOneBoundaries() {
        double[] samples = {0.0, 0.699999, 0.700001, 0.899999, 0.900001, 0.999999};
        String[] expected = {"common", "common", "rare", "rare", "legendary", "legendary"};
        for (int i = 0; i < samples.length; i++) {
            List<LootDrop> drops = RiftLoot.rollAll(scrolls(), Map.of(), null, draws(0.0, samples[i]));
            assertEquals(1, drops.size());
            assertEquals(expected[i], drops.getFirst().spec().id());
        }
    }

    @Test
    void zeroAndInvalidScrollWeightsNeverDropOrConsumeRandomDraws() {
        for (Object weight : List.of(0, -3, "NaN", "Infinity", "not-a-number")) {
            RiftLootSpec scroll = spec("skin-id", "disabled", "chance", 100, "weight", weight);
            assertTrue(RiftLoot.rollAll(List.of(scroll), Map.of(), null, draws()).isEmpty());
        }
        assertTrue(RiftLoot.rollAll(List.of(), Map.of(), null, draws()).isEmpty());
    }

    @Test
    void explicitScrollWeightWorksWithoutIndividualChance() {
        RiftLootSpec scroll = spec("skin-id", "weighted", "weight", 0.25);
        List<LootDrop> drops = RiftLoot.rollAll(List.of(scroll), Map.of(), null, draws(0.0, 0.0));
        assertEquals(1, drops.size());
        assertEquals("weighted", drops.getFirst().spec().skinId());
    }

    @Test
    void absentWeightFallsBackToExistingChance() {
        RiftLootSpec scroll = spec("skin-id", "legacy", "chance", 0.1);
        assertEquals(1, RiftLoot.rollAll(List.of(scroll), Map.of(), null, draws(0.49, 0.99)).size());
    }

    @Test
    void weightsAboveHundredRemainRelativeAndHugeWeightsDoNotOverflow() {
        for (double weight : new double[] {200, Double.MAX_VALUE}) {
            List<RiftLootSpec> specs = parseLoot(List.of(
                    loot("skin-id", "first", "weight", weight),
                    loot("skin-id", "second", "weight", weight)));
            List<LootDrop> drops = RiftLoot.rollAll(specs, Map.of(), null, draws(0.0, 0.75));
            assertEquals(1, drops.size());
            assertEquals("second", drops.getFirst().spec().skinId());
        }
    }

    @Test
    void ordinaryLootStillDropsAlongsideOneScroll() {
        List<RiftLootSpec> specs = new ArrayList<>(scrolls());
        specs.add(spec("material", "DIAMOND", "chance", 100));
        List<LootDrop> drops = RiftLoot.rollAll(specs, Map.of(), null, draws(0.0, 0.0, 0.0));
        assertEquals(2, drops.size());
        assertEquals(1, drops.stream().filter(drop -> drop.spec().skinId() != null).count());
        assertEquals(1, drops.stream().filter(drop -> drop.material() == Material.DIAMOND).count());
    }

    @Test
    void ordinaryExclusiveLootHasPriorityOverScrollGroup() {
        List<RiftLootSpec> specs = new ArrayList<>(scrolls());
        specs.add(spec("material", "CAKE", "chance", 100, "replaces-all", true));
        List<LootDrop> drops = RiftLoot.rollAll(specs, Map.of(), null, draws(0.0));
        assertEquals(1, drops.size());
        assertEquals(Material.CAKE, drops.getFirst().material());
    }

    @Test
    void onlySelectedExclusiveScrollSuppressesOrdinaryLoot() {
        List<RiftLootSpec> specs = parseLoot(List.of(
                loot("id", "exclusive", "skin-id", "exclusive_skin", "weight", 1, "replaces-all", true),
                loot("id", "regular", "skin-id", "regular_skin", "weight", 1),
                loot("material", "DIAMOND", "chance", 100)));
        List<LootDrop> exclusive = RiftLoot.rollAll(specs, Map.of(), null, draws(0.0, 0.0));
        assertEquals(1, exclusive.size());
        assertEquals("exclusive", exclusive.getFirst().spec().id());
        List<LootDrop> regular = RiftLoot.rollAll(specs, Map.of(), null, draws(0.0, 0.75, 0.0));
        assertEquals(2, regular.size());
        assertTrue(regular.stream().noneMatch(drop -> drop.spec().replacesAll()));
        List<LootDrop> missed = RiftLoot.rollAll(specs, Map.of(), null, draws(0.5, 0.0));
        assertEquals(1, missed.size());
        assertEquals(Material.DIAMOND, missed.getFirst().material());
    }

    @Test
    void scrollCycleGuaranteeDoesNotBypassSharedGateOrReceiveAssignments() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rift-loot", List.of(
                loot("id", "scroll", "skin-id", "skin", "chance", 100, "guaranteed-in-cycle", true),
                loot("id", "ore", "material", "DIAMOND", "chance", 0, "guaranteed-in-cycle", true)));
        WaveConfig waves = new WaveConfig(config);
        java.util.UUID quest = java.util.UUID.randomUUID();
        assertEquals(Map.of("ore", quest.toString()), waves.assignGuaranteed(List.of(quest), new Random(1)));
        assertTrue(RiftLoot.rollAll(waves.loot(), Map.of("scroll", "quest"), "quest", draws(0.5, 0.0)).isEmpty());
    }

    @Test
    void configuredSharedChanceReloadsAndClampsInvalidValues() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rift-loot", List.of(loot("skin-id", "skin", "weight", 1)));
        WaveConfig waves = new WaveConfig(config);
        assertEquals(50, waves.scrollChance());
        Object[] values = {25.5, 0, 100, -1, 200, Double.NaN, Double.POSITIVE_INFINITY, "invalid"};
        double[] expected = {25.5, 0, 100, 0, 100, 50, 50, 50};
        for (int i = 0; i < values.length; i++) {
            config.set("rift-scrolls.chance", values[i]);
            waves.reload(config);
            assertEquals(expected[i], waves.scrollChance());
            boolean shouldDrop = expected[i] > 25;
            assertEquals(shouldDrop ? 1 : 0, RiftLoot.rollAll(waves.loot(), Map.of(), null,
                    draws(0.25, 0.0), waves.scrollChance()).size());
        }
    }

    @Test
    void fullSharedChanceSelectsOneEvenOnHighestRollAndZeroSelectsNone() {
        assertEquals(1, RiftLoot.rollAll(scrolls(), Map.of(), null, draws(0.999999, 0.999999), 100).size());
        assertTrue(RiftLoot.rollAll(scrolls(), Map.of(), null, draws(0.0), 0).isEmpty());
    }

    @Test
    void waveTypeFiltersScrollsBeforeWeightedSelection() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("rift-loot", List.of(
                loot("id", "excluded", "skin-id", "excluded_skin", "weight", 1000),
                loot("id", "included", "skin-id", "included_skin", "weight", 1)));
        config.set("wave-types.ice.chance", 100);
        config.set("wave-types.ice.loot", List.of("included"));
        WaveConfig waves = new WaveConfig(config);
        List<LootDrop> drops = RiftLoot.rollAll(waves.lootForType("ice"), Map.of(), null, draws(0.0, 0.0));
        assertEquals(1, drops.size());
        assertEquals("included", drops.getFirst().spec().id());
    }
}
