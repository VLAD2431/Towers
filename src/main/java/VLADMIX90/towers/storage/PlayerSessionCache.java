package VLADMIX90.towers.storage;

import VLADMIX90.towers.model.PlayerQuestData;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Mutable values are accessed only on the game thread; database boundaries use snapshots. */
public final class PlayerSessionCache {
    private final Map<UUID, PlayerQuestData> players = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<Void>> sessions = new ConcurrentHashMap<>();
    private final Function<UUID, CompletableFuture<Optional<PlayerSnapshot>>> loader;
    private final Function<PlayerSnapshot, CompletableFuture<Void>> saver;
    private final Executor main;
    private boolean closed;
    private CompletableFuture<Void> closeFuture;

    public PlayerSessionCache(Function<UUID, CompletableFuture<Optional<PlayerSnapshot>>> loader,
                              Function<PlayerSnapshot, CompletableFuture<Void>> saver, Executor main) {
        this.loader = Objects.requireNonNull(loader);
        this.saver = Objects.requireNonNull(saver);
        this.main = Objects.requireNonNull(main);
    }

    public CompletableFuture<Void> load(UUID id) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("Player cache is closed"));
        var existing = sessions.get(id);
        if (existing != null) return existing;
        var session = new CompletableFuture<Void>();
        sessions.put(id, session);
        CompletableFuture<Optional<PlayerSnapshot>> loading;
        try { loading = loader.apply(id); }
        catch (RuntimeException failure) {
            session.completeExceptionally(failure);
            return session;
        }
        loading.whenComplete((snapshot, failure) -> {
            try {
                main.execute(() -> {
                    if (closed || sessions.get(id) != session) {
                        session.completeExceptionally(new CancellationException("Player session ended"));
                    } else if (failure != null) {
                        session.completeExceptionally(failure);
                    } else {
                        players.put(id, snapshot.map(PlayerSnapshot::toData).orElseGet(() -> new PlayerQuestData(id)));
                        session.complete(null);
                    }
                });
            } catch (RuntimeException rejected) { session.completeExceptionally(rejected); }
        });
        return session;
    }

    public boolean isLoaded(UUID id) { return !closed && players.containsKey(id); }

    public PlayerQuestData data(UUID id) {
        PlayerQuestData data = players.get(id);
        if (closed || data == null) throw new IllegalStateException("Player progress is not loaded: " + id);
        return data;
    }

    public CompletableFuture<Void> save(UUID id) {
        PlayerQuestData data = players.get(id);
        return data == null ? CompletableFuture.completedFuture(null) : saver.apply(PlayerSnapshot.capture(data));
    }

    public CompletableFuture<Void> unload(UUID id) {
        var session = sessions.remove(id);
        if (session != null && !session.isDone()) session.completeExceptionally(new CancellationException("Player left"));
        PlayerQuestData data = players.remove(id);
        return data == null ? CompletableFuture.completedFuture(null) : saver.apply(PlayerSnapshot.capture(data));
    }

    public CompletableFuture<Void> saveAll() {
        return CompletableFuture.allOf(players.keySet().stream().map(this::save).toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<Void> close() {
        if (closeFuture != null) return closeFuture;
        closed = true;
        var saves = players.keySet().stream().map(this::unload).toArray(CompletableFuture[]::new);
        sessions.values().forEach(session -> session.completeExceptionally(new CancellationException("Plugin stopped")));
        sessions.clear();
        closeFuture = CompletableFuture.allOf(saves);
        return closeFuture;
    }
}
