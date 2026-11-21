package VLADMIX90.towers.storage;

import VLADMIX90.towers.wave.ChunkKey;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Main-thread ownership; SQLite records are committed before a world flag is changed. */
public final class ForcedChunkOwnership {
    public interface WorldAccess {
        boolean available(ChunkKey key);
        boolean forced(ChunkKey key);
        void force(ChunkKey key, boolean value);
    }
    private final Function<ChunkKey, CompletableFuture<Void>> save, delete;
    private final WorldAccess world;
    private final Executor main;
    private final Set<ChunkKey> owned = new HashSet<>(), stale = new HashSet<>();
    private final Map<ChunkKey, CompletableFuture<Boolean>> pending = new HashMap<>();
    private boolean stopped;

    public ForcedChunkOwnership(Function<ChunkKey, CompletableFuture<Void>> save,
                                Function<ChunkKey, CompletableFuture<Void>> delete, WorldAccess world, Executor main) {
        this.save = save; this.delete = delete; this.world = world; this.main = main;
    }
    public CompletableFuture<Boolean> acquire(ChunkKey key) {
        if (stopped) return CompletableFuture.failedFuture(new CancellationException("Ownership stopped"));
        if (owned.contains(key)) return CompletableFuture.completedFuture(true);
        if (pending.containsKey(key)) return pending.get(key);
        if (!world.available(key)) return CompletableFuture.failedFuture(new IllegalStateException("World is unavailable"));
        if (world.forced(key)) return CompletableFuture.completedFuture(false);
        var result = new CompletableFuture<Boolean>();
        pending.put(key, result);
        save.apply(key).whenComplete((ignored, failure) -> {
            try { main.execute(() -> {
                if (pending.get(key) != result || stopped) return; // durable row is recovered on next startup
                pending.remove(key);
                if (failure != null) { result.completeExceptionally(failure); return; }
                if (!world.available(key)) { stale.add(key); result.completeExceptionally(new IllegalStateException("World unloaded")); return; }
                if (world.forced(key)) {
                    delete.apply(key).whenComplete((removed, error) -> {
                        try { main.execute(() -> {
                            if (stopped) result.completeExceptionally(new CancellationException("Ownership stopped"));
                            else if (error != null) result.completeExceptionally(error);
                            else result.complete(false);
                        }); } catch (RuntimeException rejected) { result.completeExceptionally(rejected); }
                    });
                    return;
                }
                world.force(key, true);
                owned.add(key);
                result.complete(true);
            }); } catch (RuntimeException error) { result.completeExceptionally(error); }
        });
        return result;
    }
    public CompletableFuture<Void> release(ChunkKey key) {
        var acquiring = pending.remove(key);
        if (acquiring != null) {
            acquiring.completeExceptionally(new CancellationException("Ownership released"));
            // FIFO storage queue puts this delete after the pending ownership write.
            return delete.apply(key);
        }
        if (!owned.remove(key)) return CompletableFuture.completedFuture(null);
        if (!world.available(key)) { stale.add(key); return CompletableFuture.completedFuture(null); }
        world.force(key, false);
        return delete.apply(key);
    }
    public CompletableFuture<Void> recover(Set<ChunkKey> records) { stale.addAll(records); return recoverAvailable(); }
    public CompletableFuture<Void> worldLoaded(UUID worldId) { return recoverAvailable(); }
    private CompletableFuture<Void> recoverAvailable() {
        List<CompletableFuture<Void>> operations = new ArrayList<>();
        for (ChunkKey key : new HashSet<>(stale)) {
            if (!world.available(key)) continue;
            world.force(key, false);
            stale.remove(key);
            operations.add(delete.apply(key)); // failed deletes remain durable and are safe to retry after restart
        }
        return CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new));
    }
    public CompletableFuture<Void> close() {
        stopped = true;
        List<CompletableFuture<Void>> operations = new ArrayList<>();
        for (ChunkKey key : new HashSet<>(owned)) operations.add(release(key));
        for (ChunkKey key : new HashSet<>(pending.keySet())) operations.add(release(key));
        return CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new));
    }
}
