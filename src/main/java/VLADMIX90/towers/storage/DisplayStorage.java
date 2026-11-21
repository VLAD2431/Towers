package VLADMIX90.towers.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class DisplayStorage {
    private final SqliteDatabase database;

    public DisplayStorage(SqliteDatabase database) { this.database = database; }

    public CompletableFuture<List<DisplayRecord>> loadAll() {
        return database.read(connection -> {
            List<DisplayRecord> records = new ArrayList<>();
            try (var statement = connection.prepareStatement("SELECT * FROM display_objects ORDER BY entity_id");
                 var row = statement.executeQuery()) {
                while (row.next()) {
                    String world = row.getString("world_uuid");
                    records.add(new DisplayRecord(UUID.fromString(row.getString("entity_id")), row.getString("object_key"),
                            row.getInt("tower_id"), world == null ? null : UUID.fromString(world), row.getString("world_name"),
                            row.getDouble("x"), row.getDouble("y"), row.getDouble("z"), row.getFloat("yaw"), row.getFloat("pitch")));
                }
            }
            return List.copyOf(records);
        });
    }

    public CompletableFuture<Void> upsert(DisplayRecord record) {
        return database.transaction(connection -> { writeRecord(connection, record); return null; });
    }

    public static void writeRecord(Connection connection, DisplayRecord record) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO display_objects VALUES(?,?,?,?,?,?,?,?,?,?) ON CONFLICT(entity_id) DO UPDATE SET object_key=excluded.object_key,tower_id=excluded.tower_id,world_uuid=excluded.world_uuid,world_name=excluded.world_name,x=excluded.x,y=excluded.y,z=excluded.z,yaw=excluded.yaw,pitch=excluded.pitch")) {
            statement.setString(1, record.entityId().toString()); statement.setString(2, record.key());
            statement.setInt(3, record.towerId()); statement.setString(4, record.worldId() == null ? null : record.worldId().toString());
            statement.setString(5, record.worldName()); statement.setDouble(6, record.x()); statement.setDouble(7, record.y());
            statement.setDouble(8, record.z()); statement.setFloat(9, record.yaw()); statement.setFloat(10, record.pitch());
            statement.executeUpdate();
        }
    }

    public CompletableFuture<Void> delete(UUID entityId) {
        return database.transaction(connection -> {
            try (var statement = connection.prepareStatement("DELETE FROM display_objects WHERE entity_id=?")) {
                statement.setString(1, entityId.toString()); statement.executeUpdate();
            }
            return null;
        });
    }
}
