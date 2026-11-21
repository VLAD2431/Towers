package VLADMIX90.towers.wave;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaveConfigWaveCountTest {
    @Test
    void typeOverrideWinsAndMissingOverrideUsesGlobalWaveCount() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("chunk-area.waves", 3);
        config.set("wave-types.harry_mono.waves", 1);
        config.set("wave-types.harry_mono.mobs.custom:harry_mono.counts", List.of(1));
        config.set("wave-types.zombie.mobs.ZOMBIE.counts", List.of(1, 2, 3));

        WaveConfig waves = new WaveConfig(config);

        assertEquals(1, waves.wavesForType("harry_mono"));
        assertEquals(3, waves.wavesForType("zombie"));
    }

    @Test
    void nonpositiveTypeOverrideFallsBackToGlobalWaveCount() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("chunk-area.waves", 3);
        config.set("wave-types.invalid.waves", 0);

        WaveConfig waves = new WaveConfig(config);

        assertEquals(3, waves.wavesForType("invalid"));
    }

    @Test
    void shippedHarryMonoTypeHasOneWave() {
        InputStream stream = getClass().getResourceAsStream("/config.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        YamlConfiguration waveSettings = new YamlConfiguration();
        waveSettings.set("chunk-area.waves", config.getInt("chunk-area.waves"));
        waveSettings.set("wave-types.harry_mono.waves", config.getInt("wave-types.harry_mono.waves"));

        assertEquals(1, new WaveConfig(waveSettings).wavesForType("harry_mono"));
    }
}
