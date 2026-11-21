package VLADMIX90.towers.quest;

import VLADMIX90.towers.storage.PlayerSnapshot;
import VLADMIX90.towers.wave.WaveConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QuestManagerLoadingTest {
    @Test void readinessGuardBlocksActionsUntilLoaded() {
        PlayerDataStorage storage = mock(PlayerDataStorage.class);
        when(storage.save(any(PlayerSnapshot.class))).thenReturn(CompletableFuture.completedFuture(null));
        UUID id = UUID.randomUUID();
        var pending = new CompletableFuture<Optional<PlayerSnapshot>>();
        when(storage.load(id)).thenReturn(pending);
        QuestManager quests = new QuestManager(mock(JavaPlugin.class), mock(WaveConfig.class), storage, Runnable::run);
        quests.loadPlayer(id);
        assertFalse(quests.canAct(id));
        pending.complete(Optional.empty());
        assertTrue(quests.canAct(id));
        quests.closeAsync().join();
        assertFalse(quests.canAct(id));
    }
    @Test void dataIsCacheOnlyAndFailedLoadCannotBeSaved() {
        PlayerDataStorage storage = mock(PlayerDataStorage.class);
        UUID id = UUID.randomUUID();
        var pending = new CompletableFuture<Optional<PlayerSnapshot>>();
        when(storage.load(id)).thenReturn(pending);
        QuestManager quests = new QuestManager(mock(JavaPlugin.class), mock(WaveConfig.class), storage, Runnable::run);
        assertThrows(IllegalStateException.class, () -> quests.data(id));
        var loaded = quests.loadPlayer(id);
        assertFalse(quests.isLoaded(id));
        quests.save(id);
        verify(storage, never()).save(any(PlayerSnapshot.class));
        pending.completeExceptionally(new IllegalStateException("disk error"));
        assertTrue(loaded.isCompletedExceptionally());
        assertThrows(IllegalStateException.class, () -> quests.data(id));
    }
}
