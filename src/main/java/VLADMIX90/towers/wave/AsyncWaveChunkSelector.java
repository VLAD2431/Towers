package VLADMIX90.towers.wave;

import java.util.*;
import java.util.concurrent.*;

/** Tick-budgeted selection. All state and environment probes belong to the main thread. */
public final class AsyncWaveChunkSelector {
    public interface Environment {
        CompletableFuture<Void> load(ChunkKey key);
        boolean isWater(ChunkKey key);
        boolean occupied(ChunkKey key);
        void retain(ChunkKey key);
        void release(ChunkKey key);
    }

    private final Random random;
    private final Environment environment;
    private final Executor main;
    private final Map<UUID, Search> searches = new LinkedHashMap<>();
    private int inFlight;

    public AsyncWaveChunkSelector(Random random, Environment environment, Executor main) {
        this.random = random; this.environment = Objects.requireNonNull(environment); this.main = Objects.requireNonNull(main);
    }

    public CompletableFuture<Optional<ChunkKey>> select(UUID playerId, UUID worldId, int computerX,
                                                        int computerZ, int gridSize, int sectorSize) {
        return select(playerId, worldId, computerX, computerZ, gridSize, sectorSize, null);
    }

    public CompletableFuture<Optional<ChunkKey>> select(UUID playerId, UUID worldId, int computerX,
                                                        int computerZ, int gridSize, int sectorSize, ChunkKey saved) {
        Search existing = searches.get(playerId);
        if (existing != null) return existing.result;
        Search search = new Search(playerId, worldId, computerX, computerZ, gridSize, sectorSize);
        search.saved = saved;
        searches.put(playerId, search);
        return search.result;
    }

    public void tick() {
        for (Search search : List.copyOf(searches.values())) {
            if (inFlight >= 4) break;
            if (search.loading || search.result.isDone()) continue;
            ChunkKey next = search.next();
            if (next == null) { search.result.complete(Optional.empty()); searches.remove(search.player, search); continue; }
            search.loading = true; inFlight++;
            try {
                environment.load(next).whenComplete((ignored, failure) -> {
                    try { main.execute(() -> loaded(search, next, failure)); }
                    catch (RuntimeException rejected) { search.result.completeExceptionally(rejected); }
                });
            } catch (RuntimeException failure) { loaded(search, next, failure); }
        }
    }

    private void loaded(Search search, ChunkKey key, Throwable failure) {
        inFlight--; search.loading = false;
        if (searches.get(search.player) != search) return;
        if (failure != null) { searches.remove(search.player, search); search.result.completeExceptionally(failure); return; }
        boolean retained = false;
        try {
            environment.retain(key); retained = true;
            if (environment.isWater(key)) return;
            if (!environment.occupied(key) || search.shared || key.equals(search.saved)) {
                search.ticket = key; retained = false;
                search.result.complete(Optional.of(key));
            } else if (search.taken == null) search.taken = key;
        } catch (RuntimeException error) {
            searches.remove(search.player, search); search.result.completeExceptionally(error);
        } finally { if (retained) environment.release(key); }
    }

    /** Release after the caller has installed durable ownership or on cancellation. */
    public void release(UUID playerId) { cancel(playerId); }
    public void cancel(UUID playerId) {
        Search search = searches.remove(playerId);
        if (search == null) return;
        if (search.ticket != null) environment.release(search.ticket);
        search.result.completeExceptionally(new CancellationException("Wave search cancelled"));
    }
    public void cancelAll() { List.copyOf(searches.keySet()).forEach(this::cancel); }

    private final class Search {
        final UUID player, world;
        final int x, z, grid, size;
        final CompletableFuture<Optional<ChunkKey>> result = new CompletableFuture<>();
        final Iterator<int[]> sectors;
        Iterator<int[]> chunks = Collections.emptyIterator();
        final Set<ChunkKey> visited = new HashSet<>();
        boolean near, shared, loading;
        ChunkKey taken, ticket, saved;
        boolean triedSaved;

        Search(UUID player, UUID world, int x, int z, int grid, int size) {
            this.player = player; this.world = world; this.x = x; this.z = z; this.grid = grid; this.size = size;
            var order = WaveChunkSelector.sectors(grid);
            if (random != null) Collections.shuffle(order, random);
            sectors = order.iterator();
        }

        ChunkKey next() {
            if (saved != null && !triedSaved) { triedSaved = true; visited.add(saved); return saved; }
            if (shared) return null;
            while (true) {
                if (chunks.hasNext()) {
                    int[] point = chunks.next(); ChunkKey key = new ChunkKey(world, point[0], point[1]);
                    if (visited.add(key)) return key;
                } else if (!near && sectors.hasNext()) {
                    int[] sector = sectors.next();
                    chunks = WaveChunkSelector.sectorChunks(random, x, z, sector[0], sector[1], size).iterator();
                } else if (taken != null) {
                    shared = true; return taken;
                } else if (!near) {
                    near = true;
                    chunks = WaveChunkSelector.candidates(x, z, size, WaveChunkSelector.searchDistance(grid, size), random).iterator();
                } else return null;
            }
        }
    }
}
