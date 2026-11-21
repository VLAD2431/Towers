package VLADMIX90.towers.storage;

import VLADMIX90.towers.quest.PlayerDataStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;

class LegacyYamlMigratorTest {
    @TempDir Path directory;

    @Test void invalidDisplayStructureCannotLeavePartialObjectsOrSuccessMarker() throws Exception {
        UUID entity = UUID.randomUUID();
        Files.writeString(directory.resolve("displays.yml"), "objects:\n  " + entity
                + ":\n    key: tower-1\n    world: missing\n    x: 1\n    y: 2\n    z: 3\n    yaw: 0\n    pitch: 0\n  "
                + UUID.randomUUID() + ": invalid\n");
        var database = new SqliteDatabase(directory.resolve("database.db")); database.initialize().join();
        try {
            assertThrows(CompletionException.class, () -> new LegacyYamlMigrator(database, directory).migrate().join());
            assertTrue(new DisplayStorage(database).loadAll().join().isEmpty());
            assertEquals(0, database.read(connection -> {
                try (var statement = connection.prepareStatement("SELECT count(*) FROM migration_history WHERE file_name='displays.yml'");
                     var row = statement.executeQuery()) { row.next(); return row.getInt(1); }
            }).join());
        } finally { database.closeAsync().join(); }
    }

    @Test void sqlFailureRollsBackRowsAndHistoryAndAbsentFilesCanArriveLater() throws Exception {
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        var database = new SqliteDatabase(directory.resolve("database.db")); database.initialize().join();
        try {
            var migrator = new LegacyYamlMigrator(database, directory);
            migrator.migrate().join();
            Files.writeString(directory.resolve("players.yml"), "players:\n  " + first
                    + ":\n    remaining-waves: 1\n  " + second + ":\n    remaining-waves: 2\n");
            database.transaction(connection -> {
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE TRIGGER reject_player BEFORE INSERT ON players WHEN NEW.remaining_waves=2 BEGIN SELECT RAISE(ABORT,'injected'); END");
                }
                return null;
            }).join();
            assertThrows(CompletionException.class, () -> migrator.migrate().join());
            assertTrue(new PlayerDataStorage(database).load(first).join().isEmpty());
            database.transaction(connection -> {
                try (var statement = connection.createStatement()) { statement.execute("DROP TRIGGER reject_player"); }
                return null;
            }).join();
            migrator.migrate().join();
            assertEquals(1, new PlayerDataStorage(database).load(first).join().orElseThrow().remainingWaves());
            assertEquals(2, new PlayerDataStorage(database).load(second).join().orElseThrow().remainingWaves());
        } finally { database.closeAsync().join(); }
    }

    @Test void rejectsNonCanonicalUuidWithoutMarkingFileComplete() throws Exception {
        Files.writeString(directory.resolve("forced-chunks.yml"), "chunks:\n  - '1-1-1-1-1,0,0'\n");
        SqliteDatabase database = new SqliteDatabase(directory.resolve("database.db")); database.initialize().join();
        try {
            assertThrows(CompletionException.class, () -> new LegacyYamlMigrator(database, directory).migrate().join());
            assertTrue(new ForcedChunkStorage(database).loadAll().join().isEmpty());
            assertEquals(0, database.read(connection -> {
                try (var statement = connection.prepareStatement("SELECT count(*) FROM migration_history"); var row = statement.executeQuery()) {
                    return row.next() ? row.getInt(1) : -1;
                }
            }).join());
        } finally { database.closeAsync().join(); }
    }

    @Test void preexistingSqlWinsAndUnmarkedLegacyRemainsValidated() throws Exception {
        UUID player = UUID.randomUUID();
        SqliteDatabase database = new SqliteDatabase(directory.resolve("database.db")); database.initialize().join();
        try {
            var data = new VLADMIX90.towers.model.PlayerQuestData(player); data.remainingWaves(9);
            PlayerDataStorage storage = new PlayerDataStorage(database);
            storage.save(PlayerSnapshot.capture(data)).join(); storage.saveSchedule(11, 888, "sql").join();
            Files.writeString(directory.resolve("players.yml"), "players:\n  " + player + ":\n    remaining-waves: 2\nglobal:\n  generation: 1\n");
            new LegacyYamlMigrator(database, directory).migrate().join();
            assertEquals(9, storage.load(player).join().orElseThrow().remainingWaves());
            assertEquals(new PlayerDataStorage.Schedule(11, 888, "sql"), storage.loadSchedule().join());
        } finally { database.closeAsync().join(); }
    }

    @Test void importsOnceWithoutOverwritingSqlOrChangingOriginals() throws Exception {
        UUID player = UUID.randomUUID(), quest = UUID.randomUUID(), entity = UUID.randomUUID(), world = UUID.randomUUID();
        String yaml = "players:\n  " + player + ":\n    remaining-waves: 4\n    quests:\n      - id: " + quest
                + "\n        name: Task\n        description: Test\n        state: WAVES_ACTIVE\n        tower-id: 3\n"
                + "global:\n  generation: 7\n  next-refresh-at: 123\n  schedule: '16:00/168'\n";
        Files.writeString(directory.resolve("players.yml"), yaml);
        Files.writeString(directory.resolve("displays.yml"), "objects:\n  " + entity + ":\n    uuid: " + entity
                + "\n    key: tower-3\n    tower-id: 3\n    world: missing\n    x: 1\n    y: 2\n    z: 3\n    yaw: 4\n    pitch: 5\n");
        Files.writeString(directory.resolve("forced-chunks.yml"), "chunks:\n  - '" + world + ",-2,5'\n");
        SqliteDatabase database = new SqliteDatabase(directory.resolve("database.db")); database.initialize().join();
        try {
            LegacyYamlMigrator migrator = new LegacyYamlMigrator(database, directory);
            migrator.migrate().join();
            PlayerDataStorage storage = new PlayerDataStorage(database);
            var imported = storage.load(player).join().orElseThrow();
            assertEquals(4, imported.remainingWaves()); assertEquals(quest, imported.quests().getFirst().id());
            assertEquals(7, storage.loadSchedule().join().generation());
            assertEquals("missing", new DisplayStorage(database).loadAll().join().getFirst().worldName());
            assertEquals(1, new ForcedChunkStorage(database).loadAll().join().size());
            storage.delete(player).join();
            Files.writeString(directory.resolve("players.yml"), "invalid: [broken");
            migrator.migrate().join();
            assertTrue(storage.load(player).join().isEmpty());
            assertEquals(yaml, Files.readString(directory.resolve("players.yml.sqlite-backup")));
        } finally { database.closeAsync().join(); }
    }

    @Test void malformedFileRollsBackItsEntireImportAndCanBeRetried() throws Exception {
        UUID valid = UUID.randomUUID();
        Files.writeString(directory.resolve("players.yml"), "players:\n  " + valid + ":\n    remaining-waves: 2\n  broken-uuid:\n    quests: []\n");
        SqliteDatabase database = new SqliteDatabase(directory.resolve("database.db")); database.initialize().join();
        try {
            LegacyYamlMigrator migrator = new LegacyYamlMigrator(database, directory);
            assertThrows(CompletionException.class, () -> migrator.migrate().join());
            assertTrue(new PlayerDataStorage(database).load(valid).join().isEmpty());
            Files.writeString(directory.resolve("players.yml"), "players:\n  " + valid + ":\n    remaining-waves: 2\n");
            migrator.migrate().join();
            assertEquals(2, new PlayerDataStorage(database).load(valid).join().orElseThrow().remainingWaves());
        } finally { database.closeAsync().join(); }
    }
}
