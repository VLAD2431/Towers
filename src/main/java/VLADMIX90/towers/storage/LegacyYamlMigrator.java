package VLADMIX90.towers.storage;

import VLADMIX90.towers.model.*;
import VLADMIX90.towers.quest.PlayerDataStorage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Only this startup importer may read legacy dynamic YAML. Each file has its own atomic marker. */
public final class LegacyYamlMigrator {
    private final SqliteDatabase database;
    private final Path directory;

    public LegacyYamlMigrator(SqliteDatabase database, Path directory) {
        this.database = database; this.directory = directory;
    }

    public CompletableFuture<Void> migrate() {
        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        for (String file : List.of("players.yml", "displays.yml", "forced-chunks.yml")) {
            result = result.thenCompose(ignored -> database.transaction(connection -> {
                try (var statement = connection.prepareStatement("SELECT 1 FROM migration_history WHERE file_name=?")) {
                    statement.setString(1, file);
                    try (var row = statement.executeQuery()) { if (row.next()) return null; }
                }
                Path source = directory.resolve(file);
                if (!Files.exists(source)) return null;
                Path backup = directory.resolve(file + ".sqlite-backup");
                if (!Files.exists(backup)) Files.copy(source, backup);
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(source.toFile()); // Unlike loadConfiguration, malformed YAML must fail startup.
                switch (file) {
                    case "players.yml" -> importPlayers(connection, yaml);
                    case "displays.yml" -> importDisplays(connection, yaml);
                    case "forced-chunks.yml" -> importChunks(connection, yaml);
                    default -> throw new IllegalArgumentException(file);
                }
                try (var statement = connection.prepareStatement("INSERT INTO migration_history(file_name) VALUES(?)")) {
                    statement.setString(1, file); statement.executeUpdate();
                }
                return null;
            }));
        }
        return result;
    }

    private static void importPlayers(Connection connection, YamlConfiguration yaml) throws Exception {
        ConfigurationSection players = section(yaml, "players");
        if (players != null) for (String key : players.getKeys(false)) {
            UUID id = uuid(key);
            ConfigurationSection saved = Objects.requireNonNull(section(players, key), "Missing player section " + key);
            PlayerQuestData data = new PlayerQuestData(id);
            Object quests = saved.get("quests");
            if (quests != null) {
                if (!(quests instanceof List<?> list)) throw invalid("quests");
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> quest)) throw invalid("quest");
                    data.quests().add(new Quest(Objects.requireNonNull(uuid(quest.get("id"))), requiredString(quest.get("name")),
                            requiredString(quest.get("description")), QuestState.valueOf(requiredString(quest.get("state"))),
                            integer(quest.get("tower-id"), 0), nullableString(quest.get("wave-type"))));
                }
            }
            data.select(uuid(saved.get("selected-quest")));
            data.calibrated(bool(saved.get("calibrated"))); data.waveStarted(bool(saved.get("wave-started")));
            data.remainingWaves(integer(saved.get("remaining-waves"), 0));
            data.calibrationStage(integer(saved.get("calibration-stage"), 0));
            data.calibrationAttempt(number(saved.get("calibration-attempt")));
            data.questRefreshAt(number(saved.get("quest-refresh-at"))); data.questGeneration(number(saved.get("quest-generation")));
            ConfigurationSection slots = section(saved, "stage-slots");
            if (slots != null) for (String slot : slots.getKeys(false)) data.stageSlots().put(Integer.parseInt(slot), bool(slots.get(slot)));
            Object clicked = saved.get("stage-clicked");
            if (clicked != null) {
                if (!(clicked instanceof List<?> list)) throw invalid("stage-clicked");
                for (Object slot : list) data.stageClicked().add(integer(slot, 0));
            }
            ConfigurationSection drops = section(saved, "guaranteed-drops");
            if (drops != null) for (String drop : drops.getKeys(false)) {
                String questId = requiredString(drops.get(drop)); uuid(questId);
                data.guaranteedDrops().put(drop, questId);
            }
            data.computerChunk(uuid(saved.get("computer-world")), integer(saved.get("computer-chunk-x"), 0),
                    integer(saved.get("computer-chunk-z"), 0), integer(saved.get("computer-tower-id"), 0));
            data.waveChunk(uuid(saved.get("wave-world")), integer(saved.get("wave-chunk-x"), 0), integer(saved.get("wave-chunk-z"), 0));
            if (!exists(connection, "players", "player_id", key)) PlayerDataStorage.writeSnapshot(connection, PlayerSnapshot.capture(data));
        }
        ConfigurationSection global = section(yaml, "global");
        if (global != null) try (var statement = connection.prepareStatement("INSERT OR IGNORE INTO global_schedule VALUES(1,?,?,?)")) {
            statement.setLong(1, number(global.get("generation"))); statement.setLong(2, number(global.get("next-refresh-at")));
            String schedule = nullableString(global.get("schedule")); statement.setString(3, schedule == null ? "" : schedule);
            statement.executeUpdate();
        }
    }

    private static void importDisplays(Connection connection, YamlConfiguration yaml) throws Exception {
        ConfigurationSection objects = section(yaml, "objects");
        if (objects == null) return;
        for (String key : objects.getKeys(false)) {
            UUID id = uuid(key);
            ConfigurationSection saved = Objects.requireNonNull(section(objects, key), "Missing object section");
            if (saved.contains("uuid") && !id.equals(uuid(saved.get("uuid")))) throw invalid("object uuid mismatch");
            DisplayRecord record = new DisplayRecord(id, requiredString(saved.get("key")), integer(saved.get("tower-id"), 0),
                    uuid(saved.get("world-uuid")), requiredString(saved.get("world")), decimal(saved.get("x")), decimal(saved.get("y")),
                    decimal(saved.get("z")), (float) decimal(saved.get("yaw")), (float) decimal(saved.get("pitch")));
            if (!exists(connection, "display_objects", "entity_id", key)) DisplayStorage.writeRecord(connection, record);
        }
    }

    private static void importChunks(Connection connection, YamlConfiguration yaml) throws Exception {
        Object value = yaml.get("chunks");
        if (value == null) return;
        if (!(value instanceof List<?> list)) throw invalid("chunks");
        try (var statement = connection.prepareStatement("INSERT OR IGNORE INTO forced_chunks VALUES(?,?,?)")) {
            for (Object item : list) {
                String[] parts = requiredString(item).split(",", -1);
                if (parts.length != 3) throw invalid("chunk key");
                statement.setString(1, uuid(parts[0]).toString()); statement.setInt(2, Integer.parseInt(parts[1].trim()));
                statement.setInt(3, Integer.parseInt(parts[2].trim())); statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static boolean exists(Connection connection, String table, String column, String key) throws Exception {
        try (var statement = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE " + column + "=?")) {
            statement.setString(1, key); try (var row = statement.executeQuery()) { return row.next(); }
        }
    }

    private static ConfigurationSection section(ConfigurationSection parent, String key) {
        if (!parent.contains(key)) return null;
        ConfigurationSection result = parent.getConfigurationSection(key);
        if (result == null) throw invalid(key);
        return result;
    }
    private static String requiredString(Object value) { if (value instanceof String text) return text; throw invalid("string"); }
    private static String nullableString(Object value) { return value == null ? null : requiredString(value); }
    private static UUID uuid(Object value) {
        if (value == null) return null;
        String text = requiredString(value);
        UUID id = UUID.fromString(text);
        if (!id.toString().equalsIgnoreCase(text)) throw invalid("UUID");
        return id;
    }
    private static boolean bool(Object value) { if (value == null) return false; if (value instanceof Boolean flag) return flag; throw invalid("boolean"); }
    private static long number(Object value) {
        if (value == null) return 0;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) return ((Number) value).longValue();
        throw invalid("integer");
    }
    private static int integer(Object value, int fallback) { return value == null ? fallback : Math.toIntExact(number(value)); }
    private static double decimal(Object value) { if (value instanceof Number n && Double.isFinite(n.doubleValue())) return n.doubleValue(); throw invalid("coordinate"); }
    private static IllegalArgumentException invalid(String field) { return new IllegalArgumentException("Invalid legacy YAML field: " + field); }
}
