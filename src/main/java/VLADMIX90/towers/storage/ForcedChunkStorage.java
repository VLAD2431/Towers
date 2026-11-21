package VLADMIX90.towers.storage;

import VLADMIX90.towers.wave.ChunkKey;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ForcedChunkStorage {
    private final SqliteDatabase database;

    public ForcedChunkStorage(SqliteDatabase database) { this.database = database; }

    public CompletableFuture<Set<ChunkKey>> loadAll() {
        return database.read(connection -> {
            Set<ChunkKey> chunks = new HashSet<>();
            try (var statement = connection.prepareStatement("SELECT world_uuid,chunk_x,chunk_z FROM forced_chunks");
                 var row = statement.executeQuery()) {
                while (row.next()) chunks.add(new ChunkKey(UUID.fromString(row.getString(1)), row.getInt(2), row.getInt(3)));
            }
            return Set.copyOf(chunks);
        });
    }

    public CompletableFuture<Void> add(ChunkKey chunk) { return update(chunk, true); }
    public CompletableFuture<Void> remove(ChunkKey chunk) { return update(chunk, false); }

    private CompletableFuture<Void> update(ChunkKey chunk, boolean add) {
        return database.transaction(connection -> {
            String sql = add ? "INSERT OR IGNORE INTO forced_chunks VALUES(?,?,?)"
                    : "DELETE FROM forced_chunks WHERE world_uuid=? AND chunk_x=? AND chunk_z=?";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, chunk.world().toString()); statement.setInt(2, chunk.x()); statement.setInt(3, chunk.z());
                statement.executeUpdate();
            }
            return null;
        });
    }
}
