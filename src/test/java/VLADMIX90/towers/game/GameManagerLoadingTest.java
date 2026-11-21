package VLADMIX90.towers.game;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.quest.QuestManager;
import VLADMIX90.towers.wave.WaveConfig;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class GameManagerLoadingTest {
    @Test void calibrationTimerDoesNotTouchAnEvictedSession() {
        var plugin = mock(JavaPlugin.class);
        var server = mock(org.bukkit.Server.class);
        var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server); when(server.getScheduler()).thenReturn(scheduler);
        var quests = mock(QuestManager.class);
        var player = mock(Player.class);
        var id = java.util.UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true); when(server.getPlayer(id)).thenReturn(player);
        var data = new VLADMIX90.towers.model.PlayerQuestData(id);
        when(quests.isLoaded(id)).thenReturn(true); when(quests.data(id)).thenReturn(data);
        when(quests.selected(id)).thenReturn(java.util.Optional.of(new VLADMIX90.towers.model.Quest(
                java.util.UUID.randomUUID(), "test", "test", VLADMIX90.towers.model.QuestState.CALIBRATING, 1, null)));
        var game = new GameManager(plugin, mock(ConfigManager.class), quests, mock(MenuManager.class), mock(WaveConfig.class));
        game.startCalibration(player);
        var timer = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), timer.capture(), anyLong());
        when(quests.isLoaded(id)).thenReturn(false);
        when(quests.data(id)).thenThrow(new IllegalStateException("evicted"));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> timer.getValue().run());
    }
    @Test void unloadedSessionCannotEnterCalibrationOrWaveFlows() {
        var quests = mock(QuestManager.class);
        var game = new GameManager(mock(JavaPlugin.class), mock(ConfigManager.class), quests,
                mock(MenuManager.class), mock(WaveConfig.class));
        var player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        game.startCalibration(player);
        game.calibrationClick(player, 1);
        game.resumeCalibration(player);
        game.resumeWaves(player);
        verify(quests, never()).data(any());
        verify(quests, never()).selected(any());
    }
}
