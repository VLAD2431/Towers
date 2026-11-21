package VLADMIX90.towers.wave;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/** Bukkit operations are invoked by the selector's main-thread executor only. */
public final class PaperChunkEnvironment implements AsyncWaveChunkSelector.Environment {
    private final JavaPlugin plugin;
    private final Predicate<ChunkKey> occupied;
    private final Map<ChunkKey, Integer> tickets = new HashMap<>();

    public PaperChunkEnvironment(JavaPlugin plugin, Predicate<ChunkKey> occupied) {
        this.plugin = plugin; this.occupied = occupied;
    }

    private World world(ChunkKey key) {
        return Objects.requireNonNull(plugin.getServer().getWorld(key.world()), "Wave world is unavailable");
    }

    public CompletableFuture<Void> load(ChunkKey key) {
        return world(key).getChunkAtAsync(key.x(), key.z()).thenApply(chunk -> null);
    }
    public boolean isWater(ChunkKey key) { return WaterGuard.isWaterChunk(world(key), key.x(), key.z()); }
    public boolean occupied(ChunkKey key) { return occupied.test(key); }
    public void retain(ChunkKey key) {
        if (!tickets.containsKey(key)) world(key).addPluginChunkTicket(key.x(), key.z(), plugin);
        tickets.merge(key, 1, Integer::sum);
    }
    public void release(ChunkKey key) {
        Integer count = tickets.get(key);
        if (count == null) return;
        if (count > 1) { tickets.put(key, count - 1); return; }
        tickets.remove(key);
        World world = plugin.getServer().getWorld(key.world());
        if (world != null) world.removePluginChunkTicket(key.x(), key.z(), plugin);
    }
}
