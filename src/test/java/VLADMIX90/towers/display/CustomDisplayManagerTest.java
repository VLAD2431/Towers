package VLADMIX90.towers.display;

import VLADMIX90.towers.config.ConfigManager;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks that the objects of the world are plain click zones and that they are never created twice.
 *
 * <p>A tower is only an interaction (a hitbox) — it carries no model. When the plugin starts again,
 * the object that is already standing in the world is taken over instead of being created a second
 * time; only an object that really is gone is created anew.
 */
class CustomDisplayManagerTest {

    private Path folder;
    private JavaPlugin plugin;
    private Server server;
    private World world;
    private ConfigManager config;
    private CustomDisplayManager manager;
    private VLADMIX90.towers.storage.SqliteDatabase database;
    private VLADMIX90.towers.storage.DisplayStorage storage;
    @org.junit.jupiter.api.AfterEach
    void closeDatabase() { database.closeAsync().join(); }
    private CustomDisplayManager reload() {
        return new CustomDisplayManager(plugin, config, storage, storage.loadAll().join());
    }
    /** Entities around the spot the manager looks at. */
    private final List<Entity> nearby = new ArrayList<>();
    /** Tags of every interaction the test created, by entity id. */
    private final Map<UUID, Map<String, String>> tags = new HashMap<>();
    /** Spot of every interaction the test created, by entity id. */
    private final Map<UUID, Location> spots = new HashMap<>();

    @BeforeEach
    void setUp() throws IOException {
        folder = Files.createTempDirectory("towers-displays");
        plugin = Mockito.mock(JavaPlugin.class);
        server = Mockito.mock(Server.class);
        world = Mockito.mock(World.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(Mockito.mock(Logger.class));
        when(server.getWorld("world")).thenReturn(world);
        config = Mockito.mock(ConfigManager.class);
        when(config.decimal(anyString(), anyDouble())).thenAnswer(call -> call.getArgument(1));
        when(world.spawn(any(Location.class), eq(Interaction.class))).thenAnswer(call -> {
            Interaction interaction = newInteraction(UUID.randomUUID());
            spots.put(interaction.getUniqueId(), call.getArgument(0));
            return interaction;
        });
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(call -> new ArrayList<>(nearby));
        when(server.getWorlds()).thenReturn(List.of(world));
        when(world.getEntities()).thenAnswer(call -> new ArrayList<>(nearby));
        when(world.getUID()).thenReturn(UUID.randomUUID());
        when(world.getName()).thenReturn("world");
        database = new VLADMIX90.towers.storage.SqliteDatabase(folder.resolve("database.db"));
        database.initialize().join();
        storage = new VLADMIX90.towers.storage.DisplayStorage(database);
        manager = reload();
    }

    @Test
    void aTowerIsOnlyAHitboxWithoutAModel() {
        manager.spawnTower(3, at(10, 64, 10));

        verify(world, times(1)).spawn(any(Location.class), eq(Interaction.class));
        verify(world, never()).spawn(any(Location.class), eq(ItemDisplay.class));
    }

    @Test
    void theCentralTowerAndTheComputerAreHitboxesToo() {
        manager.spawnCentral(at(0, 64, 0));
        manager.spawnComputer(2, at(20, 64, 20));

        verify(world, times(2)).spawn(any(Location.class), eq(Interaction.class));
        verify(world, never()).spawn(any(Location.class), eq(ItemDisplay.class));
    }

    @Test
    void towerCoordinatesRemainAvailableWhenItsChunkUnloads() {
        DisplayObject tower = manager.spawnTower(3, at(-21, 64, 50));
        when(server.getEntity(tower.interaction().getUniqueId())).thenReturn(null);

        assertEquals(at(-21, 64, 50), manager.towerLocation(3).orElseThrow());
        verify(world, never()).getChunkAt(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void towerCoordinatesAreRestoredFromSqliteWithoutLoadedHitbox() {
        DisplayObject tower = manager.spawnTower(3, at(10, 64, -50));
        when(server.getEntity(tower.interaction().getUniqueId())).thenReturn(null);

        CustomDisplayManager reloaded = reload();

        assertEquals(at(10, 64, -50), reloaded.towerLocation(3).orElseThrow());
        verify(world, times(1)).spawn(any(Location.class), eq(Interaction.class));
    }

    @Test
    void removedTowerDoesNotKeepCoordinates() {
        manager.spawnTower(3, at(10, 64, 50));
        manager.removeByTowerId(3);

        org.junit.jupiter.api.Assertions.assertTrue(manager.towerLocation(3).isEmpty());
    }

    @Test
    void theSameTowerIsMovedInsteadOfDuplicated() {
        DisplayObject first = manager.spawnTower(3, at(10, 64, 10));
        DisplayObject second = manager.spawnTower(3, at(50, 64, 50));

        assertSame(first.interaction(), second.interaction());
        verify(world, times(1)).spawn(any(Location.class), eq(Interaction.class));
        verify(first.interaction()).teleport(at(50, 64, 50));
    }

    @Test
    void taggedHitboxIsRestoredOnStartupWithoutSpawningAnother() {
        Interaction existing = newInteraction(UUID.randomUUID());
        tags.get(existing.getUniqueId()).put("object", "tower-3");
        spots.put(existing.getUniqueId(), at(10, 64, 10));
        nearby.add(existing);

        CustomDisplayManager reloaded = reload();

        assertSame(existing, reloaded.tower(3).orElseThrow().interaction());
        verify(world, never()).spawn(any(Location.class), eq(Interaction.class));
    }

    @Test
    void taggedHitboxIsRestoredWhenItsChunkLoadsAfterStartup() {
        Interaction existing = newInteraction(UUID.randomUUID());
        tags.get(existing.getUniqueId()).put("object", "tower-3");
        spots.put(existing.getUniqueId(), at(10, 64, 10));
        Chunk chunk = Mockito.mock(Chunk.class);
        when(chunk.getEntities()).thenReturn(new Entity[]{existing});

        manager.restoreChunk(chunk);

        assertSame(existing, manager.tower(3).orElseThrow().interaction());
        verify(world, never()).spawn(any(Location.class), eq(Interaction.class));
    }

    @Test
    void spawningARestoredObjectMovesItInsteadOfDuplicatingIt() {
        Interaction existing = newInteraction(UUID.randomUUID());
        tags.get(existing.getUniqueId()).put("object", "tower-3");
        spots.put(existing.getUniqueId(), at(10, 64, 10));
        nearby.add(existing);

        CustomDisplayManager reloaded = reload();
        DisplayObject result = reloaded.spawnTower(3, at(50, 64, 50));

        assertSame(existing, result.interaction());
        verify(existing).teleport(at(50, 64, 50));
        verify(world, never()).spawn(any(Location.class), eq(Interaction.class));
    }

    @Test
    void removeAllSavedAlsoRemovesTaggedHitboxesLeftOutsideRuntimeTracking() {
        Interaction tracked = newInteraction(UUID.randomUUID());
        Interaction orphan = newInteraction(UUID.randomUUID());
        tags.get(orphan.getUniqueId()).put("object", "tower-3");
        when(world.getEntities()).thenReturn(List.of(tracked, orphan));
        when(server.getWorlds()).thenReturn(List.of(world));
        manager.spawnCentral(at(0, 64, 0));

        manager.removeAllSaved();

        verify(orphan).remove();
    }

    @Test
    void removingByKeyAlsoRemovesAnUntrackedTaggedHitbox() {
        Interaction orphan = newInteraction(UUID.randomUUID());
        tags.get(orphan.getUniqueId()).put("object", "central-tower");
        when(world.getEntities()).thenReturn(List.of(orphan));
        when(server.getWorlds()).thenReturn(List.of(world));

        int removed = manager.removeByKey("central-tower");

        assertEquals(1, removed);
        verify(orphan).remove();
    }

    @Test
    void removingByPrefixAlsoRemovesAnUntrackedTaggedHitbox() {
        Interaction orphan = newInteraction(UUID.randomUUID());
        tags.get(orphan.getUniqueId()).put("object", "tower-3");
        when(world.getEntities()).thenReturn(List.of(orphan));
        when(server.getWorlds()).thenReturn(List.of(world));

        int removed = manager.removeByPrefix("tower-");

        assertEquals(1, removed);
        verify(orphan).remove();
    }

    @Test
    void removingByTowerIdAlsoRemovesAnUntrackedTaggedHitbox() {
        Interaction orphan = newInteraction(UUID.randomUUID());
        tags.get(orphan.getUniqueId()).put("object", "computer-3");
        when(world.getEntities()).thenReturn(List.of(orphan));
        when(server.getWorlds()).thenReturn(List.of(world));

        int removed = manager.removeByTowerId(3);

        assertEquals(1, removed);
        verify(orphan).remove();
    }

    private Location at(int x, int y, int z) {
        return new Location(world, x, y, z);
    }

    /** An interaction that behaves like a real one: it has an id, a spot and a tag store. */
    private Interaction newInteraction(UUID uuid) {
        Interaction interaction = Mockito.mock(Interaction.class);
        when(interaction.getUniqueId()).thenReturn(uuid);
        when(server.getEntity(uuid)).thenReturn(interaction);
        when(interaction.getWorld()).thenReturn(world);
        Map<String, String> store = new HashMap<>();
        tags.put(uuid, store);
        PersistentDataContainer container = Mockito.mock(PersistentDataContainer.class);
        when(container.has(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenAnswer(call -> store.containsKey(call.<NamespacedKey>getArgument(0).getKey()));
        when(container.get(any(NamespacedKey.class), eq(PersistentDataType.STRING)))
                .thenAnswer(call -> store.get(call.<NamespacedKey>getArgument(0).getKey()));
        Mockito.doAnswer(call -> {
            store.put(call.<NamespacedKey>getArgument(0).getKey(), call.getArgument(2));
            return null;
        }).when(container).set(any(NamespacedKey.class), eq(PersistentDataType.STRING), anyString());
        when(interaction.getPersistentDataContainer()).thenReturn(container);
        when(interaction.getLocation()).thenAnswer(call -> spots.get(uuid));
        Mockito.doAnswer(call -> {
            spots.put(uuid, call.getArgument(0));
            return true;
        }).when(interaction).teleport(any(Location.class));
        return interaction;
    }

    @Test
    void missingWorldRecordsAreNotSpawnedAndRemovalDeletesSqlRows() {
        UUID id = UUID.randomUUID();
        storage.upsert(new VLADMIX90.towers.storage.DisplayRecord(id, "tower-7", 7,
                UUID.randomUUID(), "missing", 1, 2, 3, 0, 0)).join();
        CustomDisplayManager reloaded = reload();
        verify(world, never()).spawn(any(Location.class), eq(Interaction.class));
        assertEquals(1, storage.loadAll().join().size());
        reloaded.removeByTowerId(7);
        assertEquals(0, storage.loadAll().join().size());
    }

    /** Writes the saved record of an object, as the plugin stores it. */
    private void writeSaved(UUID entityId, String key, int towerId, int x, int y, int z) throws IOException {
        Files.writeString(folder.resolve("displays.yml"), """
                objects:
                  %s:
                    key: %s
                    tower-id: %d
                    uuid: %s
                    world: world
                    x: %d.0
                    y: %d.0
                    z: %d.0
                    yaw: 0.0
                    pitch: 0.0
                """.formatted(entityId, key, towerId, entityId, x, y, z));
    }

    private void writeDuplicateSaved(UUID firstId, UUID secondId, String key, int towerId, int x, int y, int z) throws IOException {
        Files.writeString(folder.resolve("displays.yml"), """
                objects:
                  %s:
                    key: %s
                    tower-id: %d
                    uuid: %s
                    world: world
                    x: %d.0
                    y: %d.0
                    z: %d.0
                  %s:
                    key: %s
                    tower-id: %d
                    uuid: %s
                    world: world
                    x: %d.0
                    y: %d.0
                    z: %d.0
                """.formatted(firstId, key, towerId, firstId, x, y, z,
                secondId, key, towerId, secondId, x, y, z));
    }
}
