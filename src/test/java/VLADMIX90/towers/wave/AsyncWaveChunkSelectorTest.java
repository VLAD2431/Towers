package VLADMIX90.towers.wave;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class AsyncWaveChunkSelectorTest {
    @Test void rejectedMainContinuationFailsSearchWithoutProbingWorld() {
        var env = new Environment();
        var selector = new AsyncWaveChunkSelector(null, env, command -> { throw new RejectedExecutionException("stopped"); });
        var result = selector.select(UUID.randomUUID(), UUID.randomUUID(), 0, 0, 3, 1);
        selector.tick(); env.loads.remove().complete(null);
        assertTrue(result.isCompletedExceptionally());
        assertTrue(env.tickets.isEmpty());
        selector.cancelAll();
    }
    @Test void savedChunkIsLoadedBeforeReuseAndWaterFallsBackToCandidates() {
        for (boolean water : List.of(false, true)) {
            var env = new Environment();
            var selector = new AsyncWaveChunkSelector(null, env, Runnable::run);
            UUID player = UUID.randomUUID(), world = UUID.randomUUID();
            var saved = new ChunkKey(world, 8, 9);
            var result = selector.select(player, world, 0, 0, 3, 1, saved);
            selector.tick(); assertEquals(saved, env.requested.getFirst());
            env.water = water; env.loads.remove().complete(null);
            if (!water) assertEquals(saved, result.join().orElseThrow());
            else {
                assertFalse(result.isDone()); env.water = false;
                selector.tick(); env.loads.remove().complete(null);
                assertNotEquals(saved, result.join().orElseThrow());
            }
            selector.cancelAll(); assertTrue(env.tickets.isEmpty());
        }
    }
    @Test void allWaterCompletesEmptyAndOccupiedSectorIsPreferredToNearFallback() {
        for (boolean water : List.of(true, false)) {
            var env = new Environment(); env.water = water;
            UUID player = UUID.randomUUID(), world = UUID.randomUUID();
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++)
                if (x != 0 || z != 0) env.occupied.add(new ChunkKey(world, x, z));
            var selector = new AsyncWaveChunkSelector(null, env, Runnable::run);
            var result = selector.select(player, world, 0, 0, 3, 1);
            for (int i = 0; i < 100 && !result.isDone(); i++) {
                selector.tick();
                if (!env.loads.isEmpty()) env.loads.remove().complete(null);
            }
            assertTrue(result.isDone());
            if (water) assertTrue(result.join().isEmpty());
            else assertEquals(new ChunkKey(world, -1, -1), result.join().orElseThrow());
            selector.cancelAll(); assertTrue(env.tickets.isEmpty());
        }
    }

    @Test void occupancyIsCheckedAfterAsyncLoadNotWhenCandidateWasRequested() {
        var env = new Environment();
        var selector = new AsyncWaveChunkSelector(null, env, Runnable::run);
        var result = selector.select(UUID.randomUUID(), UUID.randomUUID(), 0, 0, 3, 1);
        selector.tick();
        env.occupied.add(env.requested.getFirst());
        env.loads.remove().complete(null);
        assertFalse(result.isDone());
        selector.tick(); env.loads.remove().complete(null);
        assertEquals(env.requested.get(1), result.join().orElseThrow());
    }

    static final class Environment implements AsyncWaveChunkSelector.Environment {
        final List<ChunkKey> requested = new ArrayList<>();
        final Queue<CompletableFuture<Void>> loads = new ArrayDeque<>();
        final Set<ChunkKey> tickets = new HashSet<>();
        final Set<ChunkKey> occupied = new HashSet<>();
        boolean water;
        public CompletableFuture<Void> load(ChunkKey key) {
            requested.add(key); var future = new CompletableFuture<Void>(); loads.add(future); return future;
        }
        public boolean isWater(ChunkKey key) { assertTrue(tickets.contains(key)); return water; }
        public boolean occupied(ChunkKey key) { return occupied.contains(key); }
        public void retain(ChunkKey key) { tickets.add(key); }
        public void release(ChunkKey key) { tickets.remove(key); }
    }

    @Test void loadsBeforeProbeAndHoldsTicketUntilTransfer() {
        var env = new Environment();
        var selector = new AsyncWaveChunkSelector(new Random(4), env, Runnable::run);
        UUID player = UUID.randomUUID(), world = UUID.randomUUID();
        var result = selector.select(player, world, 0, 0, 3, 1);
        assertSame(result, selector.select(player, world, 0, 0, 3, 1));
        assertTrue(env.requested.isEmpty());
        selector.tick(); assertEquals(1, env.requested.size()); assertFalse(result.isDone());
        env.loads.remove().complete(null);
        ChunkKey selected = result.join().orElseThrow();
        assertTrue(env.tickets.contains(selected));
        selector.release(player); assertTrue(env.tickets.isEmpty());
    }

    @Test void limitsGlobalLoadsAndCancelledCompletionCannotPublishOrLeakTicket() {
        var env = new Environment();
        var selector = new AsyncWaveChunkSelector(new Random(2), env, Runnable::run);
        UUID world = UUID.randomUUID(); List<UUID> players = new ArrayList<>();
        List<CompletableFuture<Optional<ChunkKey>>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            UUID id = UUID.randomUUID(); players.add(id); results.add(selector.select(id, world, 0, 0, 3, 1));
        }
        selector.tick(); selector.tick(); assertEquals(4, env.requested.size());
        players.forEach(selector::cancel);
        while (!env.loads.isEmpty()) env.loads.remove().complete(null);
        results.forEach(result -> assertTrue(result.isCompletedExceptionally()));
        assertTrue(env.tickets.isEmpty());
        selector.tick(); assertEquals(4, env.requested.size());
    }

    @Test void startsAtMostOneCandidatePerPlayerPerTickAndSeparatesLoadErrorFromWater() {
        var env = new Environment(); env.water = true;
        var selector = new AsyncWaveChunkSelector(null, env, Runnable::run);
        UUID player = UUID.randomUUID();
        var result = selector.select(player, UUID.randomUUID(), 0, 0, 3, 1);
        selector.tick(); env.loads.remove().complete(null);
        assertEquals(1, env.requested.size()); assertFalse(result.isDone()); assertTrue(env.tickets.isEmpty());
        selector.tick(); env.loads.remove().completeExceptionally(new IllegalStateException("load failed"));
        assertTrue(result.isCompletedExceptionally()); assertTrue(env.tickets.isEmpty());
    }
}
