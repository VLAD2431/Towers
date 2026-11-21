package VLADMIX90.towers.storage;

import VLADMIX90.towers.wave.ChunkKey;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class ForcedChunkOwnershipTest {
    @Test void recoveryReportsDeleteFailureInsteadOfSilentlyDiscardingIt() {
        var key = new ChunkKey(UUID.randomUUID(), 1, 2);
        var ownership = new ForcedChunkOwnership(k -> CompletableFuture.completedFuture(null),
                k -> CompletableFuture.failedFuture(new IllegalStateException("disk")),
                new ForcedChunkOwnership.WorldAccess() {
                    public boolean available(ChunkKey k) { return true; }
                    public boolean forced(ChunkKey k) { return true; }
                    public void force(ChunkKey k, boolean value) { assertFalse(value); }
                }, Runnable::run);
        assertThrows(java.util.concurrent.CompletionException.class, () -> ownership.recover(Set.of(key)).join());
    }
    @Test void foreignFlagCleanupCompletionReturnsThroughMainExecutor() {
        var key = new ChunkKey(UUID.randomUUID(), 1, 2);
        var commit = new CompletableFuture<Void>();
        var deleted = new CompletableFuture<Void>();
        Queue<Runnable> main = new ArrayDeque<>();
        boolean[] foreign = {false};
        var ownership = new ForcedChunkOwnership(k -> commit, k -> deleted,
                new ForcedChunkOwnership.WorldAccess() {
                    public boolean available(ChunkKey k) { return true; }
                    public boolean forced(ChunkKey k) { return foreign[0]; }
                    public void force(ChunkKey k, boolean value) { fail("foreign flag"); }
                }, main::add);
        var result = ownership.acquire(key);
        foreign[0] = true; commit.complete(null); main.remove().run();
        deleted.complete(null);
        assertFalse(result.isDone(), "SQL completion must not publish to gameplay on its worker");
        main.remove().run();
        assertFalse(result.join());
    }
    @Test void flagAppearingDuringCommitIsNotClaimedOrReleased() throws Exception {
        var key = new ChunkKey(UUID.randomUUID(), 1, 2);
        var commit = new CompletableFuture<Void>();
        boolean[] foreign = {false};
        List<ChunkKey> deleted = new ArrayList<>();
        var ownership = new ForcedChunkOwnership(k -> commit,
                k -> { deleted.add(k); return CompletableFuture.completedFuture(null); },
                new ForcedChunkOwnership.WorldAccess() {
                    public boolean available(ChunkKey k) { return true; }
                    public boolean forced(ChunkKey k) { return foreign[0]; }
                    public void force(ChunkKey k, boolean value) { fail("Foreign flag must remain unchanged"); }
                }, Runnable::run);
        var result = ownership.acquire(key);
        foreign[0] = true;
        commit.complete(null);
        assertFalse(result.get(1, java.util.concurrent.TimeUnit.SECONDS));
        ownership.release(key);
        assertEquals(List.of(key), deleted);
    }
    @Test void commitsBeforeForcingAndReleasesFlagBeforeDeleting() {
        var key = new ChunkKey(UUID.randomUUID(), 1, 2);
        List<String> events = new ArrayList<>();
        CompletableFuture<Void> committed = new CompletableFuture<>();
        var ownership = new ForcedChunkOwnership(k -> { events.add("save"); return committed; },
                k -> { events.add("delete"); return CompletableFuture.completedFuture(null); },
                new ForcedChunkOwnership.WorldAccess() {
                    public boolean available(ChunkKey k) { return true; }
                    public boolean forced(ChunkKey k) { return false; }
                    public void force(ChunkKey k, boolean value) { events.add("force=" + value); }
                }, Runnable::run);
        var acquired = ownership.acquire(key);
        assertEquals(List.of("save"), events);
        committed.complete(null);
        assertTrue(acquired.join());
        ownership.release(key);
        assertEquals(List.of("save", "force=true", "force=false", "delete"), events);
    }

    @Test void foreignFlagsAreNotClaimedAndMissingWorldRecoveryIsRetained() {
        var key = new ChunkKey(UUID.randomUUID(), 1, 2);
        List<ChunkKey> deleted = new ArrayList<>();
        boolean[] available = {false};
        var ownership = new ForcedChunkOwnership(k -> { fail("must not claim foreign flag"); return null; },
                k -> { deleted.add(k); return CompletableFuture.completedFuture(null); },
                new ForcedChunkOwnership.WorldAccess() {
                    public boolean available(ChunkKey k) { return available[0]; }
                    public boolean forced(ChunkKey k) { return true; }
                    public void force(ChunkKey k, boolean value) { assertFalse(value); }
                }, Runnable::run);
        ownership.recover(Set.of(key));
        assertTrue(deleted.isEmpty());
        available[0] = true;
        ownership.worldLoaded(key.world());
        assertEquals(List.of(key), deleted);
        assertFalse(ownership.acquire(key).join());
        ownership.release(key);
        assertEquals(1, deleted.size());
    }
}
