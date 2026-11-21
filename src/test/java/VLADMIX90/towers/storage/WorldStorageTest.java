package VLADMIX90.towers.storage;

import VLADMIX90.towers.wave.ChunkKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorldStorageTest {
    @TempDir Path directory;

    @Test void preservesUnloadedWorldRecordsAndIdempotentChunkOwnershipAcrossRestart() {
        Path file = directory.resolve("database.db");
        SqliteDatabase database = new SqliteDatabase(file);
        database.initialize().join();
        UUID world = UUID.randomUUID(), entity = UUID.randomUUID();
        DisplayRecord record = new DisplayRecord(entity, "computer-3", 3, world, "missing-world", 1.25, -8, 34, 90, 5);
        ChunkKey chunk = new ChunkKey(world, -3, 9);
        DisplayStorage displays = new DisplayStorage(database);
        ForcedChunkStorage chunks = new ForcedChunkStorage(database);
        displays.upsert(record).join();
        displays.upsert(record).join();
        chunks.add(chunk).join(); chunks.add(chunk).join();
        database.closeAsync().join();
        SqliteDatabase reopened = new SqliteDatabase(file);
        reopened.initialize().join();
        try {
            displays = new DisplayStorage(reopened); chunks = new ForcedChunkStorage(reopened);
            assertEquals(java.util.List.of(record), displays.loadAll().join());
            assertEquals(java.util.Set.of(chunk), chunks.loadAll().join());
            DisplayRecord moved = new DisplayRecord(entity, "computer-3", 3, null, "legacy", 5, 6, 7, 0, 0);
            displays.upsert(moved).join();
            assertEquals(java.util.List.of(moved), displays.loadAll().join());
            displays.delete(entity).join(); chunks.remove(chunk).join(); chunks.remove(chunk).join();
            assertTrue(displays.loadAll().join().isEmpty());
            assertTrue(chunks.loadAll().join().isEmpty());
        } finally { reopened.closeAsync().join(); }
    }
}
