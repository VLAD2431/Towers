package VLADMIX90.towers.game;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.model.PlayerQuestData;
import VLADMIX90.towers.model.Quest;
import VLADMIX90.towers.model.QuestState;
import VLADMIX90.towers.quest.QuestManager;
import VLADMIX90.towers.wave.Rift;
import VLADMIX90.towers.wave.WaveConfig;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class GameManagerSkinLootTest {
    public interface ScrollPlugin extends Plugin {
        ItemStack createSkinScroll(String id);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testLootDeliversAuthenticScrollOrSkipsMissingPlugin(boolean available) {
        Fixture fixture = new Fixture(available);
        assertTrue(fixture.game.giveTestLoot(fixture.player));
        if (available) verify(fixture.inventory).addItem(fixture.scroll);
        else verifyNoInteractions(fixture.inventory);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @SuppressWarnings("unchecked")
    void finishingWavesDropsAuthenticScrollOrSkipsMissingPlugin(boolean available) throws Exception {
        Fixture fixture = new Fixture(available);
        World world = mock(World.class);
        Location location = new Location(world, 1, 64, 1);
        Rift rift = mock(Rift.class);
        when(rift.location()).thenReturn(location);
        var field = GameManager.class.getDeclaredField("rifts");
        field.setAccessible(true);
        ((Map<UUID, Rift>) field.get(fixture.game)).put(fixture.id, rift);
        fixture.game.finishWaves(fixture.id);
        if (available) verify(world).dropItem(location, fixture.scroll);
        else verifyNoInteractions(world);
        verify(rift).despawn();
        verify(fixture.quests).save(fixture.id);
    }

    @Test
    void disabledScrollGroupDoesNotGiveTestLootOrRequestExternalScrolls() {
        Fixture fixture = new Fixture(true, 0);
        assertTrue(fixture.game.giveTestLoot(fixture.player));
        verifyNoInteractions(fixture.inventory, fixture.scrollPlugin);
    }

    @Test
    @SuppressWarnings("unchecked")
    void disabledScrollGroupDoesNotDropWaveLootOrRequestExternalScrolls() throws Exception {
        Fixture fixture = new Fixture(true, 0);
        World world = mock(World.class);
        Rift rift = mock(Rift.class);
        when(rift.location()).thenReturn(new Location(world, 1, 64, 1));
        var field = GameManager.class.getDeclaredField("rifts");
        field.setAccessible(true);
        ((Map<UUID, Rift>) field.get(fixture.game)).put(fixture.id, rift);
        fixture.game.finishWaves(fixture.id);
        verifyNoInteractions(world, fixture.scrollPlugin);
        verify(rift).despawn();
    }

    private static class Fixture {
        final UUID id = UUID.randomUUID();
        final Player player = mock(Player.class);
        final PlayerInventory inventory = mock(PlayerInventory.class);
        final ItemStack scroll = mock(ItemStack.class);
        final QuestManager quests = mock(QuestManager.class);
        final ScrollPlugin scrollPlugin = mock(ScrollPlugin.class);
        final GameManager game;

        Fixture(boolean available) {
            this(available, 100);
        }

        Fixture(boolean available, double groupChance) {
            JavaPlugin plugin = mock(JavaPlugin.class);
            Server server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(plugin.getServer()).thenReturn(server);
            when(plugin.getLogger()).thenReturn(mock(Logger.class));
            when(server.getPluginManager()).thenReturn(plugins);
            when(player.getUniqueId()).thenReturn(id);
            when(player.getInventory()).thenReturn(inventory);
            if (available) {
                when(plugins.getPlugin("QUACK-by-VLADMIX90")).thenReturn(scrollPlugin);
                when(scrollPlugin.isEnabled()).thenReturn(true);
                when(scrollPlugin.createSkinScroll(anyString())).thenReturn(scroll);
            }
            Quest quest = new Quest(UUID.randomUUID(), "wave", "test", QuestState.COMPLETED, 1, "ice");
            PlayerQuestData data = new PlayerQuestData(id);
            data.select(quest.id());
            when(quests.data(id)).thenReturn(data);
            when(quests.selected(id)).thenReturn(Optional.of(quest));
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("rift-scrolls.chance", groupChance);
            yaml.set("rift-loot", List.of(
                    Map.of("id", "scroll", "skin-id", "blue_sword", "chance", 100),
                    Map.of("id", "second_scroll", "skin-id", "red_sword", "chance", 100)));
            game = new GameManager(plugin, mock(ConfigManager.class), quests, mock(MenuManager.class), new WaveConfig(yaml));
            try {
                var field = GameManager.class.getDeclaredField("random");
                field.setAccessible(true);
                ((java.util.Random) field.get(game)).setSeed(4096);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        }
    }
}
