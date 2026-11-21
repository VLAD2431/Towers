package VLADMIX90.towers.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Set;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class SchemaMigratorTest {
    @TempDir Path directory;

    @Test void createsRelationalSchemaIdempotentlyAndEnforcesForeignKeys() {
        SqliteDatabase database = new SqliteDatabase(directory.resolve("database.db"));
        database.initialize().join();
        try {
            database.transaction(connection -> {
                SchemaMigrator.migrate(connection);
                Set<String> names = new HashSet<>();
                try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table'")) {
                    while (result.next()) names.add(result.getString(1));
                }
                assertTrue(names.containsAll(Set.of("schema_version", "migration_history", "players", "quests",
                        "calibration_slots", "calibration_clicks", "guaranteed_drops", "global_schedule", "display_objects", "forced_chunks")));
                try (var statement = connection.createStatement()) {
                    assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO calibration_clicks(player_id, slot) VALUES ('missing', 1)"));
                }
                return null;
            }).join();
        } finally { database.closeAsync().join(); }
    }
}
