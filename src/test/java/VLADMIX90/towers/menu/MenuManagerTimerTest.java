package VLADMIX90.towers.menu;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.display.CustomDisplayManager;
import VLADMIX90.towers.quest.QuestManager;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class MenuManagerTimerTest {
    @Test
    void stopsWithoutCheckingScheduleWhenMenuIsClosed() {
        QuestManager quests = mock(QuestManager.class);
        MenuManager menus = new MenuManager(mock(ConfigManager.class), quests, mock(CustomDisplayManager.class));
        Inventory inventory = mock(Inventory.class);
        when(inventory.getViewers()).thenReturn(List.of());

        assertTrue(menus.updateOpenTower(inventory, Material.PAPER, "model", 100));
        verifyNoInteractions(quests);
    }

    @Test
    void closesEveryViewerAndStopsWhenRotationChanges() {
        QuestManager quests = mock(QuestManager.class);
        MenuManager menus = new MenuManager(mock(ConfigManager.class), quests, mock(CustomDisplayManager.class));
        Inventory inventory = mock(Inventory.class);
        HumanEntity first = mock(HumanEntity.class);
        HumanEntity second = mock(HumanEntity.class);
        List<HumanEntity> viewers = new ArrayList<>(List.of(first, second));
        when(inventory.getViewers()).thenReturn(viewers);
        doAnswer(invocation -> { viewers.remove(first); return null; }).when(first).closeInventory();
        when(quests.refreshAt()).thenReturn(200L);

        assertTrue(menus.updateOpenTower(inventory, Material.PAPER, "model", 100));
        verify(first).closeInventory();
        verify(second).closeInventory();
        verify(inventory, never()).setItem(anyInt(), any());
    }
}
