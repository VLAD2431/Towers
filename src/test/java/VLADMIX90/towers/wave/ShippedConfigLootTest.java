package VLADMIX90.towers.wave;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Checks the rift loot table shipped in config.yml. */
class ShippedConfigLootTest {

    private static YamlConfiguration shippedConfiguration() {
        InputStream stream = ShippedConfigLootTest.class.getResourceAsStream("/config.yml");
        assertNotNull(stream, "config.yml must be on the classpath");
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    private static List<RiftLootSpec> shippedLoot() {
        YamlConfiguration shipped = shippedConfiguration();
        List<Map<?, ?>> entries = shipped.getMapList("rift-loot");
        YamlConfiguration config = new YamlConfiguration();
        config.set("rift-loot", entries);
        return new WaveConfig(config).loot();
    }

    @Test
    void shipsTheExpectedLootTable() {
        List<RiftLootSpec> loot = shippedLoot();
        assertEquals(20, loot.size());

        RiftLootSpec chrono = loot.get(0);
        assertEquals("chrono_token", chrono.id());
        assertEquals("chrono_token", chrono.customModelData());
        assertEquals(1, chrono.min());
        assertEquals(3, chrono.max());
        assertEquals(100.0, chrono.chance());
        assertFalse(chrono.replacesAll());

        RiftLootSpec ore = loot.get(1);
        assertEquals("ars", ore.id());
        assertEquals(Material.DEEPSLATE_DIAMOND_ORE, ore.material());
        assertEquals(1, ore.min());
        assertEquals(7, ore.max());
        assertEquals(20.0, ore.chance());
        assertTrue(ore.guaranteedInCycle());
        assertFalse(ore.replacesAll());

        RiftLootSpec seeds = loot.get(2);
        assertEquals("shlak", seeds.id());
        assertEquals(Material.TORCHFLOWER_SEEDS, seeds.material());
        assertEquals(List.of(Material.PITCHER_POD), seeds.alternatives());
        assertEquals(2, seeds.min());
        assertEquals(4, seeds.max());
        assertEquals(50.0, seeds.chance());

        RiftLootSpec cake = loot.get(3);
        assertEquals("imba", cake.id());
        assertEquals(Material.CAKE, cake.material());
        assertEquals(1, cake.min());
        assertEquals(1, cake.max());
        assertEquals(0.5, cake.chance());
        assertTrue(cake.replacesAll());
    }

    @Test
    void skelebassWaveReferencesValidLootIds() {
        YamlConfiguration shipped = shippedConfiguration();
        YamlConfiguration config = new YamlConfiguration();
        config.set("rift-loot", shipped.getMapList("rift-loot"));
        config.set("wave-types.skelebas.chance", 100);
        config.set("wave-types.skelebas.loot", shipped.getStringList("wave-types.skelebas.loot"));
        WaveConfig waves = new WaveConfig(config);

        assertEquals(List.of("chrono_token", "shlak", "ars", "imba", "blueprint_guitar"),
                waves.lootForType("skelebas").stream().filter(spec -> spec.skinId() == null)
                        .map(RiftLootSpec::id).toList());
    }

    @Test
    void guitarWaveCanDropScrollsWithApprovedRarityWeights() {
        List<String> skinIds = List.of("guitar_blue", "guitar_wave",
                "guitar_litghtningbolt", "guitar_black", "guitar_gradient", "guitar_mgla",
                "guitar_white", "guitar_badtime", "guitar_glem", "guitar_gold");
        List<Double> weights = List.of(6.0, 2.5, 1.4, 6.0, 2.5, 1.4, 6.0, 2.5, 1.4, 0.3);
        YamlConfiguration shipped = shippedConfiguration();
        YamlConfiguration config = new YamlConfiguration();
        config.set("rift-loot", shipped.getMapList("rift-loot"));
        config.set("rift-scrolls.chance", shipped.getDouble("rift-scrolls.chance"));
        for (String typeId : List.of("zombie", "pillager", "pumpkin", "skelebas")) {
            config.set("wave-types." + typeId + ".chance", 25);
            config.set("wave-types." + typeId + ".loot",
                    shipped.getStringList("wave-types." + typeId + ".loot"));
        }
        WaveConfig waves = new WaveConfig(config);
        assertEquals(50.0, waves.scrollChance());
        assertEquals(20, waves.loot().stream().map(RiftLootSpec::id).distinct().count());
        List<RiftLootSpec> scrolls = waves.lootForType("skelebas").stream()
                .filter(spec -> spec.skinId() != null).toList();
        assertEquals(skinIds, scrolls.stream().map(RiftLootSpec::skinId).toList(), "Skelebas");
            double cumulativeWeight = 0;
            for (int index = 0; index < skinIds.size(); index++) {
                RiftLootSpec scroll = scrolls.get(index);
                assertEquals("scroll_" + skinIds.get(index), scroll.id());
                assertEquals(weights.get(index), scroll.weight());
                assertFalse(scroll.replacesAll());
                assertFalse(scroll.guaranteedInCycle());
                double selection = (cumulativeWeight + weights.get(index) / 2) / 30.0;
                cumulativeWeight += weights.get(index);
                Random random = new Random() {
                    private int calls;

                    @Override
                    public double nextDouble() {
                        return calls++ == 0 ? 0.0 : selection;
                    }
                };
                List<LootDrop> drops = RiftLoot.rollAll(scrolls, Map.of(), "quest", random,
                        waves.scrollChance());
                assertEquals(1, drops.size());
                assertEquals(skinIds.get(index), drops.getFirst().spec().skinId());
                assertEquals(1, drops.getFirst().amount());
            }
            assertEquals(30.0, cumulativeWeight, 0.000001);
        assertEquals(List.of("chrono_token", "ars", "shlak", "imba", "blueprint_heart", "blueprint_reaper"),
                waves.lootForType("pumpkin").stream().filter(spec -> spec.skinId() == null)
                        .map(RiftLootSpec::id).toList());
    }
}
