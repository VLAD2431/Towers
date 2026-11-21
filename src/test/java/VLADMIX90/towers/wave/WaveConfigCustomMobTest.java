package VLADMIX90.towers.wave;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaveConfigCustomMobTest {
    @Test
    void parsesCustomAndVanillaMobsFromYaml() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("""
                wave-types:
                  mixed:
                    chance: 100
                    mobs:
                      ZOMBIE:
                        counts: [5, 8]
                      'custom:skelebass':
                        counts: [1, 2]
                      'custom:dancebomber':
                        counts: [0, 3]
                      'custom:pumpkin':
                        counts: [1, 1]
                      'custom:pumpkin_soul':
                        counts: [1, 1]
                      'custom:pumpkin_brute':
                        counts: [1, 1]
                      'custom:pumpkin_poison':
                        counts: [1, 1]
                """);
        var mobs = new WaveConfig(config).type("mixed").mobs();
        assertEquals(7, mobs.size());
        assertEquals(EntityType.ZOMBIE, mobs.get(0).type());
        assertNull(mobs.get(0).customId());
        assertEquals("skelebass", mobs.get(1).customId());
        assertNull(mobs.get(1).type());
        assertEquals(2, mobs.get(1).countForWave(2));
        assertEquals("dancebomber", mobs.get(2).customId());
        assertEquals(3, mobs.get(2).countForWave(2));
        assertEquals("pumpkin", mobs.get(3).customId());
        assertEquals("pumpkin_soul", mobs.get(4).customId());
        assertEquals("pumpkin_brute", mobs.get(5).customId());
        assertEquals("pumpkin_poison", mobs.get(6).customId());
    }
}
