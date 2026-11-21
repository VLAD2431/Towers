package VLADMIX90.towers.game;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.model.PlayerQuestData;
import VLADMIX90.towers.model.Quest;
import VLADMIX90.towers.model.QuestState;
import VLADMIX90.towers.quest.QuestManager;
import VLADMIX90.towers.wave.RiftLootSpec;
import VLADMIX90.towers.wave.WaveConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;

class GameManagerTestLootTest {
    @Test
    void testLootUsesExplicitWaveTypeWithoutSelectedQuest() {
        UUID playerId = UUID.randomUUID();
        PlayerQuestData data = new PlayerQuestData(playerId);
        QuestManager quests = mock(QuestManager.class);
        when(quests.data(playerId)).thenReturn(data);

        RiftLootSpec loot = new RiftLootSpec("cycle", Material.DIAMOND, List.of(), null, null,
                null, List.of(), 2, 2, 0, true, false, null);
        WaveConfig waves = mock(WaveConfig.class);
        when(waves.typeIds()).thenReturn(List.of("ice", "fire"));
        when(waves.lootForType("fire")).thenReturn(List.of(loot));
        GameManager game = new GameManager(mock(JavaPlugin.class), mock(ConfigManager.class), quests,
                mock(MenuManager.class), waves);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(inventory);
        ItemStack rolledItem = mock(ItemStack.class);

        try (var lootBuilder = mockStatic(VLADMIX90.towers.wave.RiftLoot.class)) {
            lootBuilder.when(() -> VLADMIX90.towers.wave.RiftLoot.rollAll(eq(List.of(loot)), eq(data.guaranteedDrops()),
                    isNull(), any(), eq(0.0))).thenReturn(List.of(
                    new VLADMIX90.towers.wave.LootDrop(loot, Material.DIAMOND, 2)));
            lootBuilder.when(() -> VLADMIX90.towers.wave.RiftLoot.build(any())).thenReturn(rolledItem);

            assertTrue(game.giveTestLoot(player, "fire"));
        }

        verify(inventory).addItem(rolledItem);
        verify(waves).lootForType("fire");
    }

    @Test
    void testLootRejectsUnknownWaveTypeWithoutAccessingInventory() {
        UUID playerId = UUID.randomUUID();
        QuestManager quests = mock(QuestManager.class);
        when(quests.data(playerId)).thenReturn(new PlayerQuestData(playerId));
        WaveConfig waves = mock(WaveConfig.class);
        when(waves.typeIds()).thenReturn(List.of("ice"));
        GameManager game = new GameManager(mock(JavaPlugin.class), mock(ConfigManager.class), quests,
                mock(MenuManager.class), waves);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        assertFalse(game.giveTestLoot(player, "unknown"));

        verify(player, never()).getInventory();
        verify(waves, never()).lootForType(anyString());
    }

    @Test
    void testLootGivesGuaranteedLootForSelectedQuestType() {
        UUID playerId = UUID.randomUUID();
        Quest quest = new Quest(UUID.randomUUID(), "Вышка 1", "Задача", QuestState.COMPLETED, 1, "ice");
        PlayerQuestData data = new PlayerQuestData(playerId);
        data.select(quest.id());
        data.guaranteedDrops(Map.of("cycle", quest.id().toString()));
        QuestManager quests = mock(QuestManager.class);
        when(quests.data(playerId)).thenReturn(data);
        when(quests.selected(playerId)).thenReturn(Optional.of(quest));

        RiftLootSpec loot = new RiftLootSpec("cycle", Material.DIAMOND, List.of(), null, null,
                null, List.of(), 2, 2, 0, true, false, null);
        WaveConfig waves = mock(WaveConfig.class);
        when(waves.lootForType("ice")).thenReturn(List.of(loot));
        GameManager game = new GameManager(mock(JavaPlugin.class), mock(ConfigManager.class), quests,
                mock(MenuManager.class), waves);
        Player player = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(inventory);

        ItemStack rolledItem = mock(ItemStack.class);
        try (var lootBuilder = mockStatic(VLADMIX90.towers.wave.RiftLoot.class)) {
            lootBuilder.when(() -> VLADMIX90.towers.wave.RiftLoot.rollAll(eq(List.of(loot)), eq(data.guaranteedDrops()),
                    eq(quest.id().toString()), any(), eq(0.0))).thenReturn(List.of(
                    new VLADMIX90.towers.wave.LootDrop(loot, Material.DIAMOND, 2)));
            lootBuilder.when(() -> VLADMIX90.towers.wave.RiftLoot.build(any())).thenReturn(rolledItem);

            assertTrue(game.giveTestLoot(player));
        }

        verify(inventory).addItem(rolledItem);
        verify(waves).lootForType("ice");
        verify(waves, never()).loot();
    }

    @Test
    void testLootDoesNothingWithoutSelectedQuest() {
        UUID playerId = UUID.randomUUID();
        QuestManager quests = mock(QuestManager.class);
        when(quests.data(playerId)).thenReturn(new PlayerQuestData(playerId));
        when(quests.selected(playerId)).thenReturn(Optional.empty());
        GameManager game = new GameManager(mock(JavaPlugin.class), mock(ConfigManager.class), quests,
                mock(MenuManager.class), mock(WaveConfig.class));
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);

        assertFalse(game.giveTestLoot(player));

        verify(player, never()).getInventory();
    }
}
