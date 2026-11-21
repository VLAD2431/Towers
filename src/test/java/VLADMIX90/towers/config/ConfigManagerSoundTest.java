package VLADMIX90.towers.config;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks the sounds of the config, especially the custom ones that come from a resource pack:
 * they have no Bukkit sound, so they are played by their name.
 */
class ConfigManagerSoundTest {

    private YamlConfiguration config;
    private ConfigManager manager;
    private Player player;
    private Location location;

    @BeforeEach
    void setUp() {
        config = new YamlConfiguration();
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        manager = new ConfigManager(plugin);
        player = mock(Player.class);
        location = new Location(mock(World.class), 1, 2, 3);
        when(player.getLocation()).thenReturn(location);
    }

    @Test
    void readsTheNameOfACustomSound() {
        config.set("sounds.quest-complete", "towers:quest_complete");
        assertEquals("towers:quest_complete", manager.soundName("sounds.quest-complete"));
    }

    @Test
    void aVanillaSoundHasNoCustomName() {
        config.set("sounds.quest-complete", "block.note_block.bit");
        assertNull(manager.soundName("sounds.quest-complete"));
    }

    @Test
    void aBlankSoundHasNoName() {
        config.set("sounds.quest-complete", "  ");
        assertNull(manager.soundName("sounds.quest-complete"));
        assertNull(manager.soundName("sounds.missing"));
    }

    @Test
    void aBrokenCustomNameIsRejected() {
        config.set("sounds.quest-complete", "towers:bad name");
        assertNull(manager.soundName("sounds.quest-complete"));
    }

    @Test
    void playsACustomSoundByItsName() {
        config.set("sounds.quest-complete", "towers:quest_complete");
        config.set("sounds.quest-complete-volume", 0.5);
        config.set("sounds.quest-complete-pitch", 1.5);
        manager.playSound(player, "sounds.quest-complete");
        verify(player).playSound(eq(location), eq("towers:quest_complete"), eq(0.5f), eq(1.5f));
    }

    @Test
    void playsNothingWithoutASound() {
        manager.playSound(player, "sounds.quest-complete");
        verify(player, never()).playSound(any(Location.class), any(String.class), anyFloat(), anyFloat());
    }

    @Test
    void playsNothingWithoutAPlayer() {
        config.set("sounds.quest-complete", "towers:quest_complete");
        manager.playSound(null, "sounds.quest-complete");
    }
}
