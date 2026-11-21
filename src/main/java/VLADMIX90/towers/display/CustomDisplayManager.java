package VLADMIX90.towers.display;

import VLADMIX90.towers.config.ConfigManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import VLADMIX90.towers.storage.DisplayRecord;
import VLADMIX90.towers.storage.DisplayStorage;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Predicate;

/**
 * Keeps the objects of the plugin in the world.
 *
 * <p>An object is a plain click zone: an interaction with a hitbox and nothing else — no model is
 * built for it. On startup, persistent tagged hitboxes already in loaded worlds are restored into
 * runtime tracking; new objects are created only by explicit in-game commands.
 */
public final class CustomDisplayManager {

    /** Tag of the interaction of a towers object; its value is the key of the object. */
    private static final String TAG_NAMESPACE = "towers";
    private static final String TAG_KEY = "object";

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final Map<UUID, DisplayRecord> objects = new HashMap<>();
    private final DisplayStorage storage;
    private final NamespacedKey objectTag = new NamespacedKey(TAG_NAMESPACE, TAG_KEY);

    public CustomDisplayManager(JavaPlugin plugin, ConfigManager config, DisplayStorage storage, List<DisplayRecord> records) {
        this.plugin = plugin; this.config = config;
        this.storage = storage;
        records.forEach(record -> objects.put(record.entityId(), record));
        restorePersistentObjects();
    }

    /** Restores tagged persistent hitboxes from chunks that are already loaded at startup. */
    private void restorePersistentObjects() {
        for (World world : plugin.getServer().getWorlds()) {
            restoreEntities(world.getEntities());
        }
    }

    /** Restores tagged persistent hitboxes when their chunk is loaded after startup. */
    public void restoreChunk(Chunk chunk) {
        restoreEntities(Arrays.asList(chunk.getEntities()));
    }

    private void restoreEntities(Collection<? extends Entity> entities) {
        for (Entity entity : entities) {
            if (!(entity instanceof Interaction interaction)) continue;
            String key = tagOf(interaction);
            if (key == null) continue;
            DisplayObject existing = byKey(key);
            if (existing != null && !existing.interaction().getUniqueId().equals(interaction.getUniqueId())) {
                interaction.remove();
                objects.remove(interaction.getUniqueId());
                observe(storage.delete(interaction.getUniqueId()));
                continue;
            }
            DisplayObject object = new DisplayObject(interaction, key, towerIdFromKey(key));
            applyConfig(object);
            saveObject(object);
        }
    }

    /** Puts the object with the given key at the spot; an object that already stands there is moved, never copied. */
    public DisplayObject spawn(String key, Location location) { return place(key, location, towerIdFromKey(key)); }

    public DisplayObject spawnTower(int towerId, Location location) {
        return place("tower-" + towerId, location, towerId);
    }

    public DisplayObject spawnComputer(int towerId, Location location) {
        return place("computer-" + towerId, location, towerId);
    }

    public DisplayObject spawnCentral(Location location) { return place("central-tower", location, 0); }

    /**
     * Moves the object with the given key to the spot or creates it when it does not exist yet.
     * The object is never created twice: a second call only moves the object that is already there.
     */
    private DisplayObject place(String key, Location location, int towerId) {
        DisplayObject existing = byKey(key);
        if (existing != null) {
            existing.interaction().teleport(location);
            applyConfig(existing);
            saveObject(existing);
            return existing;
        }
        Interaction hitbox = location.getWorld().spawn(location, Interaction.class);
        hitbox.setPersistent(true);
        mark(hitbox, key);
        DisplayObject object = new DisplayObject(hitbox, key, towerId);
        applyConfig(object);
        saveObject(object);
        return object;
    }

    /** The object with the given key, when it stands in the world. */
    private DisplayObject byKey(String key) {
        return liveObjects().filter(o -> o.key().equals(key)).findFirst().orElse(null);
    }

    private java.util.stream.Stream<DisplayObject> liveObjects() {
        return objects.values().stream().map(record -> {
            Entity entity = plugin.getServer().getEntity(record.entityId());
            return entity instanceof Interaction interaction ? new DisplayObject(interaction, record.key(), record.towerId()) : null;
        }).filter(Objects::nonNull);
    }

    private void applyConfig(DisplayObject object) {
        String path = "displays." + sharedConfigKey(object.key()) + ".";
        object.interaction().setInteractionWidth((float) config.decimal(path + "hitbox-width", 1.5));
        object.interaction().setInteractionHeight((float) config.decimal(path + "hitbox-height", 2.5));
    }

    private String sharedConfigKey(String key) {
        if (key.startsWith("tower-")) return "calibration-tower";
        if (key.startsWith("computer-")) return "chunk-tower";
        return key;
    }

    public void refresh() {
        liveObjects().forEach(this::applyConfig);
    }

    /** Marks the interaction as an object of the plugin and stores its key. */
    private void mark(Interaction interaction, String key) {
        interaction.getPersistentDataContainer().set(objectTag, PersistentDataType.STRING, key);
    }

    /** The key of the object the interaction belongs to, null when it belongs to none. */
    private String tagOf(Interaction interaction) {
        return interaction.getPersistentDataContainer().get(objectTag, PersistentDataType.STRING);
    }

    private void saveObject(DisplayObject object) {
        Location l = object.interaction().getLocation();
        if (l != null && l.getWorld() != null) {
            var record = new DisplayRecord(object.interaction().getUniqueId(), object.key(), object.towerId(),
                    l.getWorld().getUID(), l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
            objects.put(record.entityId(), record);
            observe(storage.upsert(record));
        }
    }

    private void observe(java.util.concurrent.CompletableFuture<Void> operation) {
        operation.whenComplete((ignored, failure) -> {
            if (failure != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not persist display objects", failure);
        });
    }

    /** Removes every display object whose model key matches the given key and deletes it from storage. */
    public int removeByKey(String key) {
        return removeMatching(object -> object.key().equals(key), key::equals);
    }

    /** Removes every display object whose key starts with the given prefix and deletes it from storage. */
    public int removeByPrefix(String prefix) {
        return removeMatching(object -> object.key().startsWith(prefix), key -> key.startsWith(prefix));
    }

    /** Removes the numbered tower and its bound computers from the world and storage. */
    public int removeByTowerId(int towerId) {
        return removeMatching(object -> object.towerId() == towerId, key -> towerIdFromKey(key) == towerId);
    }

    /** Removes matching tracked and orphaned hitboxes, then clears every matching saved record. */
    private int removeMatching(Predicate<DisplayRecord> objectMatches, Predicate<String> keyMatches) {
        List<DisplayRecord> tracked = objects.values().stream().filter(objectMatches).toList();
        Set<UUID> removedIds = new HashSet<>();
        tracked.forEach(object -> {
            removedIds.add(object.entityId());
            Entity entity = plugin.getServer().getEntity(object.entityId());
            if (entity != null) entity.remove();
            objects.remove(object.entityId());
            observe(storage.delete(object.entityId()));
        });
        int removed = tracked.size();
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Interaction interaction) || removedIds.contains(interaction.getUniqueId())) continue;
                String key = tagOf(interaction);
                if (key == null || !keyMatches.test(key)) continue;
                interaction.remove();
                removed++;
            }
        }
        return removed;
    }

    /** Removes every tracked display object and deletes all stored records. */
    public int removeAllSaved() {
        return removeMatching(record -> true, key -> true);
    }

    public DisplayObject find(Interaction interaction) {
        DisplayRecord record = objects.get(interaction.getUniqueId());
        return record == null ? null : new DisplayObject(interaction, record.key(), record.towerId());
    }

    /** Returns the numbered tower display with the given id, if it is currently spawned. */
    public Optional<DisplayObject> tower(int towerId) {
        return liveObjects().filter(o -> o.key().equals("tower-" + towerId)).findFirst();
    }

    /** Returns saved tower coordinates even when its hitbox's chunk is unloaded. */
    public Optional<Location> towerLocation(int towerId) {
        return objects.values().stream()
                .filter(record -> record.key().equals("tower-" + towerId))
                .findFirst()
                .map(record -> {
                    World world = record.worldId() == null ? null : plugin.getServer().getWorld(record.worldId());
                    if (world == null) world = plugin.getServer().getWorld(record.worldName());
                    return new Location(world, record.x(), record.y(), record.z(), record.yaw(), record.pitch());
                });
    }

    public Optional<DisplayObject> nearestNumbered(Location location) {
        return liveObjects()
                .filter(DisplayObject::isNumberedTower)
                .filter(object -> object.interaction().getWorld() != null
                        && object.interaction().getWorld().equals(location.getWorld()))
                .min(Comparator.comparingDouble(object -> object.interaction().getLocation().distanceSquared(location)));
    }

    /**
     * The number of the tower that stands at the given spot, 0 when no numbered tower does.
     * Used to find the tower a computer belongs to.
     */
    public int towerIdAt(Location location) {
        if (location == null || location.getWorld() == null) return 0;
        for (DisplayObject object : liveObjects().toList()) {
            if (object.towerId() <= 0) continue;
            Location spot = object.interaction().getLocation();
            if (spot == null || !spot.getWorld().equals(location.getWorld())) continue;
            if (spot.distanceSquared(location) < 4.0) return object.towerId();
        }
        return 0;
    }

    private int towerIdFromKey(String key) {
        for (String prefix : List.of("tower-", "computer-")) {
            if (!key.startsWith(prefix)) continue;
            try {
                int towerId = Integer.parseInt(key.substring(prefix.length()));
                return towerId >= 1 && towerId <= 16 ? towerId : 0;
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** Drops only runtime references; persistent hitboxes stay in the world for the next startup. */
    public void removeAll() { objects.clear(); }
}
