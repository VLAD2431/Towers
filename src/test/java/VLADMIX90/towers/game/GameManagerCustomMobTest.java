package VLADMIX90.towers.game;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.quest.QuestManager;
import VLADMIX90.towers.wave.WaveConfig;
import VLADMIX90.towers.wave.WaveMobSpec;
import VLADMIX90.towers.wave.WaveTypeSpec;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameManagerCustomMobTest {
    private GameManager game;
    private Server server;
    private PluginManager plugins;

    @BeforeEach
    void setUp() throws Exception {
        Path folder = Files.createTempDirectory("towers-custom-mob");
        JavaPlugin plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        plugins = mock(PluginManager.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);
        when(plugin.getLogger()).thenReturn(Mockito.mock(java.util.logging.Logger.class));
        game = new GameManager(plugin, mock(ConfigManager.class), mock(QuestManager.class),
                mock(MenuManager.class), mock(WaveConfig.class));
    }

    @Test
    void prefersCustomMobPlugin() {
        TestMobPlugin customMob = mock(TestMobPlugin.class);
        LivingEntity spawned = mock(LivingEntity.class);
        when(customMob.isEnabled()).thenReturn(true);
        when(customMob.spawnMob(any(String.class), any(Location.class))).thenReturn(spawned);
        when(plugins.getPlugin("CustomMob")).thenReturn(customMob);

        assertSame(spawned, game.spawnCustomMob("pumpkin", mock(Location.class)));
        Mockito.verify(customMob).spawnMob(Mockito.eq("pumpkin"), any(Location.class));
        Mockito.verify(plugins, Mockito.never()).getPlugin("RockFrost");
    }

    @Test
    void fallsBackToRockFrostWhenCustomMobIsUnavailable() {
        TestMobPlugin rockFrost = mock(TestMobPlugin.class);
        LivingEntity spawned = mock(LivingEntity.class);
        when(plugins.getPlugin("CustomMob")).thenReturn(null);
        when(plugins.getPlugin("RockFrost")).thenReturn(rockFrost);
        when(rockFrost.isEnabled()).thenReturn(true);
        when(rockFrost.spawnMob(any(String.class), any(Location.class))).thenReturn(spawned);

        assertSame(spawned, game.spawnCustomMob("pumpkin", mock(Location.class)));
        Mockito.verify(rockFrost).spawnMob(Mockito.eq("pumpkin"), any(Location.class));
    }

    public static class TestMobPlugin extends JavaPlugin {
        public LivingEntity spawnMob(String id, Location location) {
            return null;
        }
    }
}
