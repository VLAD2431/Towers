package VLADMIX90.towers.storage;

import java.sql.Connection;
import java.sql.SQLException;

public final class SchemaMigrator {
    private SchemaMigrator() { }

    public static Void migrate(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS schema_version(version INTEGER NOT NULL)");
            try (var versions = statement.executeQuery("SELECT version FROM schema_version")) {
                if (versions.next() && versions.getInt(1) != 1) throw new SQLException("Unsupported Towers database schema");
            }
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS players(
                    player_id TEXT PRIMARY KEY, selected_quest TEXT, calibrated INTEGER NOT NULL,
                    remaining_waves INTEGER NOT NULL, calibration_stage INTEGER NOT NULL,
                    calibration_attempt INTEGER NOT NULL, wave_started INTEGER NOT NULL,
                    quest_refresh_at INTEGER NOT NULL, quest_generation INTEGER NOT NULL,
                    computer_world TEXT, computer_chunk_x INTEGER NOT NULL, computer_chunk_z INTEGER NOT NULL,
                    computer_tower_id INTEGER NOT NULL, wave_world TEXT, wave_chunk_x INTEGER NOT NULL,
                    wave_chunk_z INTEGER NOT NULL)
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS quests(
                    player_id TEXT NOT NULL REFERENCES players(player_id) ON DELETE CASCADE,
                    position INTEGER NOT NULL, quest_id TEXT NOT NULL, name TEXT NOT NULL,
                    description TEXT NOT NULL, state TEXT NOT NULL, tower_id INTEGER NOT NULL, wave_type TEXT,
                    PRIMARY KEY(player_id, position))
                    """);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS calibration_slots(player_id TEXT NOT NULL REFERENCES players(player_id) ON DELETE CASCADE, slot INTEGER NOT NULL, value INTEGER NOT NULL, PRIMARY KEY(player_id, slot))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS calibration_clicks(player_id TEXT NOT NULL REFERENCES players(player_id) ON DELETE CASCADE, slot INTEGER NOT NULL, PRIMARY KEY(player_id, slot))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS guaranteed_drops(player_id TEXT NOT NULL REFERENCES players(player_id) ON DELETE CASCADE, drop_key TEXT NOT NULL, quest_id TEXT NOT NULL, PRIMARY KEY(player_id, drop_key))");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS global_schedule(id INTEGER PRIMARY KEY CHECK(id=1), generation INTEGER NOT NULL, refresh_at INTEGER NOT NULL, schedule TEXT NOT NULL)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS migration_history(file_name TEXT PRIMARY KEY, imported_at INTEGER NOT NULL DEFAULT (unixepoch()))");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS display_objects(entity_id TEXT PRIMARY KEY, object_key TEXT NOT NULL,
                    tower_id INTEGER NOT NULL, world_uuid TEXT, world_name TEXT NOT NULL,
                    x REAL NOT NULL, y REAL NOT NULL, z REAL NOT NULL, yaw REAL NOT NULL, pitch REAL NOT NULL)
                    """);
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS forced_chunks(world_uuid TEXT NOT NULL, chunk_x INTEGER NOT NULL, chunk_z INTEGER NOT NULL, PRIMARY KEY(world_uuid, chunk_x, chunk_z))");
            statement.executeUpdate("INSERT INTO schema_version(version) SELECT 1 WHERE NOT EXISTS (SELECT 1 FROM schema_version)");
        }
        return null;
    }
}
