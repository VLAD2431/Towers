package VLADMIX90.towers.quest;

import VLADMIX90.towers.model.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.sql.Connection;
import java.sql.SQLException;
import VLADMIX90.towers.storage.PlayerSnapshot;
import VLADMIX90.towers.storage.SqliteDatabase;

/** Persists quest progress independently from the server's runtime state. */
public final class PlayerDataStorage {
    private final SqliteDatabase database;
    private Schedule cachedSchedule = new Schedule(0, 0, "");

    public PlayerDataStorage(SqliteDatabase database) {
        this.database = Objects.requireNonNull(database);
    }

    public record Schedule(long generation, long refreshAt, String schedule) { }
    public void acceptSchedule(Schedule schedule) { cachedSchedule = Objects.requireNonNull(schedule); }

    public CompletableFuture<Schedule> loadSchedule() {
        return database.read(connection -> {
            try (var statement = connection.prepareStatement("SELECT generation, refresh_at, schedule FROM global_schedule WHERE id=1");
                 var result = statement.executeQuery()) {
                return result.next() ? new Schedule(result.getLong(1), result.getLong(2), result.getString(3))
                        : new Schedule(0, 0, "");
            }
        });
    }

    public CompletableFuture<Void> saveSchedule(long generation, long refreshAt, String schedule) {
        return database.transaction(connection -> {
            try (var statement = connection.prepareStatement("INSERT INTO global_schedule(id,generation,refresh_at,schedule) VALUES(1,?,?,?) ON CONFLICT(id) DO UPDATE SET generation=excluded.generation,refresh_at=excluded.refresh_at,schedule=excluded.schedule")) {
                statement.setLong(1, generation); statement.setLong(2, refreshAt); statement.setString(3, schedule);
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Optional<PlayerSnapshot>> load(UUID playerId) {
        return database.read(connection -> {
            PlayerQuestData data = new PlayerQuestData(playerId);
            try (var statement = connection.prepareStatement("SELECT * FROM players WHERE player_id=?")) {
                statement.setString(1, playerId.toString());
                try (var row = statement.executeQuery()) {
                    if (!row.next()) return Optional.empty();
                    String selected = row.getString("selected_quest");
                    data.select(selected == null ? null : UUID.fromString(selected));
                    data.calibrated(row.getBoolean("calibrated")); data.remainingWaves(row.getInt("remaining_waves"));
                    data.calibrationStage(row.getInt("calibration_stage")); data.calibrationAttempt(row.getLong("calibration_attempt"));
                    data.waveStarted(row.getBoolean("wave_started")); data.questRefreshAt(row.getLong("quest_refresh_at"));
                    data.questGeneration(row.getLong("quest_generation"));
                    String computer = row.getString("computer_world"), wave = row.getString("wave_world");
                    data.computerChunk(computer == null ? null : UUID.fromString(computer), row.getInt("computer_chunk_x"), row.getInt("computer_chunk_z"), row.getInt("computer_tower_id"));
                    data.waveChunk(wave == null ? null : UUID.fromString(wave), row.getInt("wave_chunk_x"), row.getInt("wave_chunk_z"));
                }
            }
            try (var statement = connection.prepareStatement("SELECT * FROM quests WHERE player_id=? ORDER BY position")) {
                statement.setString(1, playerId.toString());
                try (var row = statement.executeQuery()) {
                    while (row.next()) data.quests().add(new Quest(UUID.fromString(row.getString("quest_id")), row.getString("name"),
                            row.getString("description"), QuestState.valueOf(row.getString("state")), row.getInt("tower_id"), row.getString("wave_type")));
                }
            }
            readChildren(connection, playerId, "calibration_slots", row -> data.stageSlots().put(row.getInt("slot"), row.getBoolean("value")));
            readChildren(connection, playerId, "calibration_clicks", row -> data.stageClicked().add(row.getInt("slot")));
            readChildren(connection, playerId, "guaranteed_drops", row -> data.guaranteedDrops().put(row.getString("drop_key"), row.getString("quest_id")));
            return Optional.of(PlayerSnapshot.capture(data));
        });
    }

    @FunctionalInterface
    private interface RowReader { void accept(java.sql.ResultSet row) throws SQLException; }

    private static void readChildren(Connection connection, UUID playerId, String table, RowReader reader) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT * FROM " + table + " WHERE player_id=?")) {
            statement.setString(1, playerId.toString());
            try (var row = statement.executeQuery()) { while (row.next()) reader.accept(row); }
        }
    }

    public CompletableFuture<Void> save(PlayerSnapshot snapshot) {
        return database.transaction(connection -> { writeSnapshot(connection, snapshot); return null; });
    }

    /** Also used by the legacy importer inside its encompassing transaction. */
    public static void writeSnapshot(Connection connection, PlayerSnapshot data) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM players WHERE player_id=?")) {
            statement.setString(1, data.playerId().toString()); statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("INSERT INTO players VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, data.playerId().toString());
            statement.setString(2, data.selectedQuest() == null ? null : data.selectedQuest().toString());
            statement.setBoolean(3, data.calibrated()); statement.setInt(4, data.remainingWaves());
            statement.setInt(5, data.calibrationStage()); statement.setLong(6, data.calibrationAttempt());
            statement.setBoolean(7, data.waveStarted()); statement.setLong(8, data.questRefreshAt());
            statement.setLong(9, data.questGeneration());
            statement.setString(10, data.computerWorld() == null ? null : data.computerWorld().toString());
            statement.setInt(11, data.computerChunkX()); statement.setInt(12, data.computerChunkZ()); statement.setInt(13, data.computerTowerId());
            statement.setString(14, data.waveWorld() == null ? null : data.waveWorld().toString());
            statement.setInt(15, data.waveChunkX()); statement.setInt(16, data.waveChunkZ()); statement.executeUpdate();
        }
        try (var statement = connection.prepareStatement("INSERT INTO quests VALUES(?,?,?,?,?,?,?,?)")) {
            int position = 0;
            for (Quest quest : data.quests()) {
                statement.setString(1, data.playerId().toString()); statement.setInt(2, position++);
                statement.setString(3, quest.id().toString()); statement.setString(4, quest.name());
                statement.setString(5, quest.description()); statement.setString(6, quest.state().name());
                statement.setInt(7, quest.towerId()); statement.setString(8, quest.waveType()); statement.addBatch();
            }
            statement.executeBatch();
        }
        try (var statement = connection.prepareStatement("INSERT INTO calibration_slots VALUES(?,?,?)")) {
            for (var entry : data.stageSlots().entrySet()) {
                statement.setString(1, data.playerId().toString()); statement.setInt(2, entry.getKey());
                statement.setBoolean(3, entry.getValue()); statement.addBatch();
            }
            statement.executeBatch();
        }
        try (var statement = connection.prepareStatement("INSERT INTO calibration_clicks VALUES(?,?)")) {
            for (int slot : data.stageClicked()) {
                statement.setString(1, data.playerId().toString()); statement.setInt(2, slot); statement.addBatch();
            }
            statement.executeBatch();
        }
        try (var statement = connection.prepareStatement("INSERT INTO guaranteed_drops VALUES(?,?,?)")) {
            for (var entry : data.guaranteedDrops().entrySet()) {
                statement.setString(1, data.playerId().toString()); statement.setString(2, entry.getKey());
                statement.setString(3, entry.getValue()); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    /** Shared schedule generation and the moment at which it began. */
    public long globalGeneration() { return cachedSchedule.generation(); }
    public long globalRefreshAt() { return cachedSchedule.refreshAt(); }
    public String globalSchedule() { return cachedSchedule.schedule(); }

    public synchronized void globalSchedule(long generation, long refreshAt, String schedule) {
            cachedSchedule = new Schedule(generation, refreshAt, schedule);
            saveSchedule(generation, refreshAt, schedule).whenComplete((ignored, failure) -> {
                if (failure != null) java.util.logging.Logger.getLogger(PlayerDataStorage.class.getName())
                        .log(java.util.logging.Level.SEVERE, "Failed to persist global quest schedule", failure);
            });
    }

    public synchronized CompletableFuture<Void> delete(UUID player) {
        return database.transaction(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM players WHERE player_id=?")) {
                statement.setString(1, player.toString()); statement.executeUpdate();
            }
            return null;
        });
    }

}
