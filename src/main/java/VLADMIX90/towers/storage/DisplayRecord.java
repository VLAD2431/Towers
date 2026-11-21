package VLADMIX90.towers.storage;

import java.util.Objects;
import java.util.UUID;

/** World UUID can be absent in an imported legacy record; the name remains available for resolution. */
public record DisplayRecord(UUID entityId, String key, int towerId, UUID worldId, String worldName,
                            double x, double y, double z, float yaw, float pitch) {
    public DisplayRecord {
        Objects.requireNonNull(entityId);
        Objects.requireNonNull(key);
        Objects.requireNonNull(worldName);
    }
}
