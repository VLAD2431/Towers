package VLADMIX90.towers.game;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.model.PlayerQuestData;
import VLADMIX90.towers.model.Quest;
import VLADMIX90.towers.model.QuestState;
import VLADMIX90.towers.quest.QuestManager;
import VLADMIX90.towers.wave.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameManagerStandaloneWaveTest {
    @TempDir Path folder;
    UUID id = UUID.randomUUID(), worldId = UUID.randomUUID();
    JavaPlugin plugin;
    World world;
    Player player;
    QuestManager quests;
    WaveConfig waves;
    GameManager game;
    Rift rift;
    Block ground;
    Runnable opened;
    List<Runnable> pending;
    List<Integer> indices;
    List<String> types;
    boolean failSpawn;
    boolean useRealSpawn;
    org.bukkit.entity.LivingEntity customMob;
    PlayerQuestData saved;
    java.util.concurrent.CompletableFuture<Void> ownershipWrite;

    @BeforeEach void setup() {
        plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(server.getScheduler()).thenReturn(scheduler);
        world = mock(World.class);
        when(world.getUID()).thenReturn(worldId);
        when(server.getWorld(worldId)).thenReturn(world);
        ground = mock(Block.class);
        when(ground.getType()).thenReturn(Material.GRASS_BLOCK);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(ground);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(64);
        when(world.getEntities()).thenReturn(List.of());
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.getLocation()).thenReturn(new Location(world, 1, 65, 1));
        when(player.isOnline()).thenReturn(true);
        when(server.getPlayer(id)).thenReturn(player);
        pending = new ArrayList<>();
        when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
            pending.add(call.getArgument(1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(call -> mock(BukkitTask.class));
        ConfigManager config = mock(ConfigManager.class);
        when(config.integer(anyString(), anyInt())).thenAnswer(call -> call.getArgument(1));
        quests = mock(QuestManager.class);
        saved = new PlayerQuestData(id);
        saved.remainingWaves(17);
        when(quests.data(id)).thenReturn(saved);
        waves = mock(WaveConfig.class);
        when(waves.waves()).thenReturn(2);
        when(waves.wavesForType("fire")).thenReturn(2);
        when(waves.typeIds()).thenReturn(List.of("ice", "fire"));
        when(waves.type("fire")).thenReturn(new WaveTypeSpec("fire", 50, List.of(), List.of()));
        when(waves.rift()).thenReturn(mock(RiftSpec.class));
        rift = mock(Rift.class);
        when(rift.location()).thenReturn(new Location(world, 1, 70, 1));
        indices = new ArrayList<>(); types = new ArrayList<>();
        ownershipWrite = java.util.concurrent.CompletableFuture.completedFuture(null);
        var ownership = new VLADMIX90.towers.storage.ForcedChunkOwnership(key -> ownershipWrite,
                key -> java.util.concurrent.CompletableFuture.completedFuture(null),
                new VLADMIX90.towers.storage.ForcedChunkOwnership.WorldAccess() {
                    public boolean available(ChunkKey key) { return true; }
                    public boolean forced(ChunkKey key) { return false; }
                    public void force(ChunkKey key, boolean value) { }
                }, Runnable::run);
        game = new GameManager(plugin, config, quests, mock(MenuManager.class), null, waves, ownership, Runnable::run) {
            @Override Rift createRift(Location center, RiftSpec spec, UUID owner, Runnable callback) {
                opened = callback;
                return rift;
            }
            @Override List<UUID> spawnMobs(World w, WaveTypeSpec type, int index, Player p, int x, int z) {
                types.add(type.id()); indices.add(index);
                if (useRealSpawn) return super.spawnMobs(w, type, index, p, x, z);
                return failSpawn ? null : new ArrayList<>();
            }
            @Override org.bukkit.entity.LivingEntity spawnCustomMob(String mobId, Location location) {
                assertEquals("harry_mono", mobId);
                return customMob;
            }
        };
    }

    @Test void singleWaveTypeSpawnsItsMobBeforeGrantingLoot() {
        when(waves.waves()).thenReturn(3);
        when(waves.wavesForType("harry_mono")).thenReturn(1);
        when(waves.type("harry_mono")).thenReturn(new WaveTypeSpec("harry_mono", 26,
                List.of(new WaveMobSpec(null, "harry_mono", new int[]{1}, List.of(), List.of())),
                List.of(), 1));
        customMob = mock(org.bukkit.entity.LivingEntity.class);
        when(customMob.getUniqueId()).thenReturn(UUID.randomUUID());
        useRealSpawn = true;

        assertTrue(game.startStandaloneWaves(player, "harry_mono"));
        opened.run();
        pending.removeFirst().run();

        assertEquals(List.of(1), indices);
        verify(customMob).setPersistent(true);
        verify(waves, never()).lootForType(anyString());
        verify(rift, never()).despawn();
    }

    @Test void typeWithMoreWavesThanDefaultUsesSequentialMobCounts() {
        when(waves.wavesForType("fire")).thenReturn(3);
        assertTrue(game.startStandaloneWaves(player, "fire"));
        opened.run();
        while (!pending.isEmpty()) pending.removeFirst().run();
        assertEquals(List.of(1, 2, 3), indices);
    }

    @Test void runsEntireSeriesWithoutChangingQuestProgress() {
        assertTrue(game.startStandaloneWaves(player, "fire"));
        assertTrue(indices.isEmpty());
        assertFalse(game.startStandaloneWaves(player, "fire"));
        opened.run();
        assertFalse(game.startStandaloneWaves(player, "fire"));
        while (!pending.isEmpty()) pending.removeFirst().run();
        assertEquals(List.of(1, 2), indices);
        assertEquals(List.of("fire", "fire"), types);
        verify(waves).lootForType("fire");
        assertEquals(17, saved.remainingWaves());
        verify(quests, never()).save(any());
        verify(quests, never()).startWaves(any(), anyInt());
        verify(quests, never()).waveDefeated(any());
        verify(rift).despawn();
        assertFalse(game.occupancy().isOccupied(new ChunkKey(worldId, 0, 0)));
    }

    @Test void riftWaitsForOwnershipCommitAndQuitCancelsContinuation() {
        ownershipWrite = new java.util.concurrent.CompletableFuture<>();
        assertTrue(game.startStandaloneWaves(player, "fire"));
        assertNull(opened);
        game.quit(player);
        ownershipWrite.complete(null);
        assertNull(opened);
        assertFalse(game.occupancy().isOccupied(new ChunkKey(worldId, 0, 0)));
    }

    @Test void rejectsUnknownTypeWaterOccupiedChunkAndActiveQuestWaves() {
        assertFalse(game.startStandaloneWaves(player, "missing"));
        when(ground.getType()).thenReturn(Material.WATER);
        assertFalse(game.startStandaloneWaves(player, "fire"));
        when(ground.getType()).thenReturn(Material.GRASS_BLOCK);
        UUID other = UUID.randomUUID();
        game.markOccupied(new ChunkKey(worldId, 0, 0), other);
        assertFalse(game.startStandaloneWaves(player, "fire"));
        game.occupancy().unmark(new ChunkKey(worldId, 0, 0), other);
        when(quests.selected(id)).thenReturn(Optional.of(new Quest(UUID.randomUUID(), "q", "q", QuestState.WAVES_ACTIVE, 1, "ice")));
        assertFalse(game.startStandaloneWaves(player, "fire"));
        assertNull(opened);
    }

    @Test void cancelDoesNotCancelQuestAndInvalidatesScheduledWave() {
        assertTrue(game.startStandaloneWaves(player, "fire"));
        opened.run();
        game.cancel(player);
        pending.forEach(Runnable::run);
        assertTrue(indices.isEmpty());
        verify(quests, never()).cancelRun(any());
        assertEquals(17, saved.remainingWaves());
        assertTrue(game.startStandaloneWaves(player, "fire"));
    }

    @Test void quitAndShutdownReleaseStandaloneState() {
        assertTrue(game.startStandaloneWaves(player, "fire"));
        game.quit(player);
        assertFalse(game.occupancy().isOccupied(new ChunkKey(worldId, 0, 0)));
        assertTrue(game.startStandaloneWaves(player, "fire"));
        game.cleanup();
        assertFalse(game.occupancy().isOccupied(new ChunkKey(worldId, 0, 0)));
        verify(quests, never()).save(any());
    }

    @Test void spawnFailureDoesNotCancelQuest() {
        failSpawn = true;
        assertTrue(game.startStandaloneWaves(player, "fire"));
        opened.run();
        pending.removeFirst().run();
        verify(quests, never()).cancelRun(any());
        assertTrue(game.startStandaloneWaves(player, "fire"));
    }

    @Test void removingTypeOnReloadCleansUpRun() {
        assertTrue(game.startStandaloneWaves(player, "fire"));
        when(waves.type("fire")).thenReturn(null);
        game.reloadWaves();
        verify(rift).despawn();
        assertFalse(game.occupancy().isOccupied(new ChunkKey(worldId, 0, 0)));
    }

    @Test void externallyRemovedRiftReleasesTemporaryRun() {
        assertTrue(game.startStandaloneWaves(player, "fire"));
        when(rift.location()).thenReturn(new Location(world, 1, 70, 1));
        when(world.getEntities()).thenReturn(List.of());
        game.cleanupQuestRuntime(id);
        assertFalse(game.occupancy().isOccupied(new ChunkKey(worldId, 0, 0)));
    }
}
