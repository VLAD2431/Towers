package VLADMIX90.towers.storage;

import VLADMIX90.towers.model.PlayerQuestData;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerSessionCacheTest {
    @Test void repeatedCloseWaitsForTheSameFinalSave() {
        var save = new CompletableFuture<Void>();
        PlayerSessionCache cache = new PlayerSessionCache(id -> CompletableFuture.completedFuture(Optional.empty()),
                snapshot -> save, Runnable::run);
        cache.load(UUID.randomUUID()).join();
        var first = cache.close();
        var second = cache.close();
        assertSame(first, second);
        assertFalse(second.isDone());
        save.completeExceptionally(new IllegalStateException("disk full"));
        assertTrue(first.isCompletedExceptionally());
        assertTrue(second.isCompletedExceptionally());
    }

    @Test void synchronousLoaderFailureIsReturnedAndSessionCanBeEvicted() {
        PlayerSessionCache cache = new PlayerSessionCache(id -> { throw new IllegalStateException("rejected"); },
                snapshot -> CompletableFuture.completedFuture(null), Runnable::run);
        UUID id = UUID.randomUUID();
        var result = assertDoesNotThrow(() -> cache.load(id));
        assertTrue(result.isCompletedExceptionally());
        assertFalse(cache.isLoaded(id));
        cache.unload(id);
        assertNotSame(result, cache.load(id));
    }

    @Test void staleLoadCannotReplaceNewSessionOrResurrectQuitPlayer() {
        Queue<Runnable> main = new ArrayDeque<>();
        List<CompletableFuture<Optional<PlayerSnapshot>>> loads = new ArrayList<>();
        List<PlayerSnapshot> saves = new ArrayList<>();
        PlayerSessionCache cache = new PlayerSessionCache(id -> {
            var load = new CompletableFuture<Optional<PlayerSnapshot>>(); loads.add(load); return load;
        }, snapshot -> { saves.add(snapshot); return CompletableFuture.completedFuture(null); }, main::add);
        UUID id = UUID.randomUUID();
        var first = cache.load(id);
        assertFalse(cache.isLoaded(id));
        assertThrows(IllegalStateException.class, () -> cache.data(id));
        cache.unload(id);
        var second = cache.load(id);
        loads.get(1).complete(Optional.empty()); main.remove().run();
        assertTrue(second.isDone()); assertTrue(cache.isLoaded(id));
        cache.data(id).remainingWaves(7);
        loads.get(0).complete(Optional.empty()); main.remove().run();
        assertTrue(first.isCompletedExceptionally());
        assertEquals(7, cache.data(id).remainingWaves());
        cache.unload(id);
        assertEquals(1, saves.size()); assertEquals(7, saves.getFirst().remainingWaves());
        assertFalse(cache.isLoaded(id));
    }

    @Test void failuresNeverPublishEmptyDataAndCloseRejectsLateCallbacks() {
        Queue<Runnable> main = new ArrayDeque<>();
        var load = new CompletableFuture<Optional<PlayerSnapshot>>();
        PlayerSessionCache cache = new PlayerSessionCache(id -> load,
                snapshot -> CompletableFuture.completedFuture(null), main::add);
        UUID id = UUID.randomUUID();
        var result = cache.load(id);
        load.completeExceptionally(new IllegalStateException("disk unavailable")); main.remove().run();
        assertTrue(result.isCompletedExceptionally()); assertFalse(cache.isLoaded(id));
        assertThrows(IllegalStateException.class, () -> cache.data(id));
        cache.close();
        assertTrue(cache.load(id).isCompletedExceptionally());
    }

    @Test void saveCapturesBeforeDatabaseWorkerRuns() {
        List<PlayerSnapshot> saves = new ArrayList<>();
        PlayerSessionCache cache = new PlayerSessionCache(id -> CompletableFuture.completedFuture(Optional.empty()),
                snapshot -> { saves.add(snapshot); return CompletableFuture.completedFuture(null); }, Runnable::run);
        UUID id = UUID.randomUUID(); cache.load(id).join();
        PlayerQuestData data = cache.data(id); data.remainingWaves(3); cache.save(id); data.remainingWaves(2);
        assertEquals(3, saves.getFirst().remainingWaves());
        cache.close(); assertEquals(2, saves.getLast().remainingWaves());
        assertFalse(cache.isLoaded(id));
    }
}
