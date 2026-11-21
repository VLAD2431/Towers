package VLADMIX90.towers.wave;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaveConfigLootTypeTest {
    @Test
    void waveTypeSelectsLootByIdsFromCatalog() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("wave-types.type-1.chance", 100);
        config.set("wave-types.type-1.loot", List.of("chrono", "ore"));
        config.set("wave-types.type-1.mobs.ZOMBIE.counts", List.of(1));
        config.set("rift-loot", List.of(
                Map.of("id", "chrono", "material", "DIAMOND", "chance", 100),
                Map.of("id", "ore", "material", "IRON_INGOT", "chance", 100),
                Map.of("id", "other", "material", "GOLD_INGOT", "chance", 100)));
        config.set("wave-types.type-1.loot", List.of("chrono", "ore"));

        WaveConfig waves = new WaveConfig(config, Logger.getAnonymousLogger());

        assertEquals(List.of("chrono", "ore"), waves.type("type-1").lootIds());
        assertEquals(List.of("chrono", "ore"), waves.lootForType("type-1").stream().map(RiftLootSpec::id).toList());
    }

    @Test
    void emptyTypeLootListKeepsTheLegacySharedLootTable() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("wave-types.type-1.chance", 100);
        config.set("wave-types.type-1.loot", List.of());
        config.set("rift-loot", List.of(Map.of("id", "shared", "material", "DIAMOND", "chance", 100)));

        WaveConfig waves = new WaveConfig(config);

        assertEquals(List.of("shared"), waves.lootForType("type-1").stream().map(RiftLootSpec::id).toList());
    }
}
