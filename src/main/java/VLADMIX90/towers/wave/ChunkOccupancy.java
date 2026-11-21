package VLADMIX90.towers.wave;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The chunks the waves run in and the players that use them.
 *
 * <p>Every player is remembered separately, so a chunk can host the waves of several players at the
 * same time and is only free again when the last wave leaves it.
 */
public final class ChunkOccupancy {
    private final Map<ChunkKey, Set<UUID>> owners = new HashMap<>();

    /** Remembers that the wave of the player runs in the chunk. */
    public void mark(ChunkKey chunk, UUID owner) {
        owners.computeIfAbsent(chunk, key -> new HashSet<>()).add(owner);
    }

    /** Forgets the wave of the player; the chunk stays taken while another wave uses it. */
    public void unmark(ChunkKey chunk, UUID owner) {
        Set<UUID> current = owners.get(chunk);
        if (current == null) return;
        current.remove(owner);
        if (current.isEmpty()) owners.remove(chunk);
    }

    /** True when a wave of any player runs in the chunk. */
    public boolean isOccupied(ChunkKey chunk) {
        return owners.containsKey(chunk);
    }

    /** True when a wave of another player runs in the chunk. */
    public boolean occupiedByOther(ChunkKey chunk, UUID owner) {
        Set<UUID> current = owners.get(chunk);
        if (current == null) return false;
        for (UUID other : current) if (!other.equals(owner)) return true;
        return false;
    }

    /** The chunks that are taken by the wave of another player. */
    public Set<ChunkKey> takenByOthers(UUID owner) {
        Set<ChunkKey> taken = new HashSet<>();
        for (ChunkKey chunk : owners.keySet()) if (occupiedByOther(chunk, owner)) taken.add(chunk);
        return taken;
    }

    /** Forgets every wave. */
    public void clear() {
        owners.clear();
    }
}
