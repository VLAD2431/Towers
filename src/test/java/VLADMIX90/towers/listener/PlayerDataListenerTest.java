package VLADMIX90.towers.listener;

import VLADMIX90.towers.game.GameManager;
import VLADMIX90.towers.quest.QuestManager;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;

class PlayerDataListenerTest {
    @Test void joinWaitsForLoadAndQuitDoesNotTouchUnloadedData() {
        GameManager game = mock(GameManager.class);
        QuestManager quests = mock(QuestManager.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        var pending = new CompletableFuture<Void>();
        when(quests.loadPlayer(id)).thenReturn(pending);
        var listener = new PlayerDataListener(game, quests, ignored -> player, ignored -> { });
        PlayerJoinEvent join = mock(PlayerJoinEvent.class);
        when(join.getPlayer()).thenReturn(player);
        listener.onJoin(join);
        verifyNoInteractions(game);
        verify(quests, never()).refreshOnJoin(id);
        when(quests.isLoaded(id)).thenReturn(true);
        pending.complete(null);
        verify(quests).refreshOnJoin(id);
        verify(game).resumeCalibration(player);
        verify(game).resumeWaves(player);
        when(quests.isLoaded(id)).thenReturn(false);
        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
        when(quit.getPlayer()).thenReturn(player);
        listener.onQuit(quit);
        verify(game, never()).quit(player);
        verify(quests).unload(id);
    }

    @Test void failedLoadDoesNotResumeOrCreateProgress() {
        GameManager game = mock(GameManager.class);
        QuestManager quests = mock(QuestManager.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(quests.loadPlayer(id)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("disk")));
        var listener = new PlayerDataListener(game, quests, ignored -> player, ignored -> { });
        listener.load(id);
        verifyNoInteractions(game);
        verify(quests, never()).refreshOnJoin(id);
    }
}
