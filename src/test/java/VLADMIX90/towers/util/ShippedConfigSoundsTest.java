package VLADMIX90.towers.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks the custom sounds of the resource pack that are shipped in config.yml. */
class ShippedConfigSoundsTest {

    private static final List<String> CUSTOM_SOUNDS = List.of(
            "quest-complete", "rift-open", "wave-start", "rift-close", "rift-ambient", "battle");
    private static final List<String> REMOVED_SOUNDS = List.of(
            "quest-given", "quest-selected", "wave-clear");

    private static YamlConfiguration shipped() {
        InputStream stream = ShippedConfigSoundsTest.class.getResourceAsStream("/config.yml");
        assertNotNull(stream, "config.yml must be on the classpath");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    @Test
    void shipsEveryCustomSound() {
        YamlConfiguration config = shipped();
        for (String key : CUSTOM_SOUNDS) {
            String raw = config.getString("sounds." + key);
            assertNotNull(raw, "the sound " + key + " must be in config.yml");
            assertTrue(Sounds.isCustom(raw), "the sound " + key + " must be a sound of the resource pack");
            assertNotNull(Sounds.resourcePath(raw), "the name of the sound " + key + " is broken");
        }
    }

    @Test
    void everyCustomSoundHasItsVolumeAndPitch() {
        YamlConfiguration config = shipped();
        for (String key : CUSTOM_SOUNDS) {
            assertTrue(config.contains("sounds." + key + "-volume"), "the sound " + key + " needs a volume");
            assertTrue(config.contains("sounds." + key + "-pitch"), "the sound " + key + " needs a pitch");
        }
    }

    @Test
    void shipsTheRiftAmbientSoundInterval() {
        assertTrue(shipped().getInt("sounds.rift-ambient-interval-seconds", 0) > 0);
    }

    @Test
    void obsoleteSoundsAreNotShipped() {
        YamlConfiguration config = shipped();
        for (String key : REMOVED_SOUNDS) {
            assertFalse(config.contains("sounds." + key), "the sound " + key + " must be removed");
            assertFalse(config.contains("sounds." + key + "-volume"), "the volume of " + key + " must be removed");
            assertFalse(config.contains("sounds." + key + "-pitch"), "the pitch of " + key + " must be removed");
        }
    }
}
