package VLADMIX90.towers.game;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.model.PlayerQuestData;
import VLADMIX90.towers.model.Quest;
import VLADMIX90.towers.model.QuestState;
import VLADMIX90.towers.quest.QuestManager;
import VLADMIX90.towers.wave.Rift;
import VLADMIX90.towers.wave.RiftCloud;
import VLADMIX90.towers.wave.RiftFrame;
import VLADMIX90.towers.wave.RiftOpenMode;
import VLADMIX90.towers.wave.RiftSpec;
import VLADMIX90.towers.wave.WaveConfig;
import VLADMIX90.towers.wave.WaveMobSpec;
import VLADMIX90.towers.wave.WaveTypeSpec;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Checks the beginning of a wave: the mobs wait for the rift, and between two waves there is a
 * pause of five to seven seconds.
 */
class GameManagerWaveStartTest {

    private final UUID playerId = UUID.randomUUID();
    private final UUID worldId = UUID.randomUUID();

    private JavaPlugin plugin;
    private Server server;
    private BukkitScheduler scheduler;
    private World world;
    private ConfigManager config;
    private QuestManager quests;
    private WaveConfig waves;
    private Player player;
    private PlayerQuestData data;
    private LivingEntity mob;
    private UUID mobId;
    private GameManager game;

    @BeforeEach
    void setUp() throws IOException {
        Path folder = Files.createTempDirectory("towers-wave-start");
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        world = mock(World.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getWorld(worldId)).thenReturn(world);
        when(world.getChunkAtAsync(anyInt(), anyInt())).thenReturn(java.util.concurrent.CompletableFuture.completedFuture(mock(org.bukkit.Chunk.class)));
        when(server.getPlayer(playerId)).thenReturn(player);
        when(scheduler.runTaskTimer(any(JavaPlugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenAnswer(invocation -> mock(BukkitTask.class));

        Block ground = mock(Block.class);
        when(ground.getType()).thenReturn(Material.GRASS_BLOCK);
        when(world.getHighestBlockYAt(anyInt(), anyInt())).thenReturn(64);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(ground);
        when(world.getDifficulty()).thenReturn(Difficulty.NORMAL);
        when(world.getPlayers()).thenReturn(List.of());
        when(world.getEntities()).thenReturn(List.of());
        mob = mock(LivingEntity.class);
        mobId = UUID.randomUUID();
        when(mob.getUniqueId()).thenReturn(mobId);
        when(world.spawnEntity(any(Location.class), any(EntityType.class))).thenReturn(mob);

        config = mock(ConfigManager.class);
        when(config.integer(anyString(), anyInt())).thenAnswer(call -> call.getArgument(1));

        data = new PlayerQuestData(playerId);
        data.computerChunk(worldId, 0, 0, 1);
        // Three waves are left to fight, so the first one is the first entry of the mob counts.
        data.remainingWaves(3);
        quests = mock(QuestManager.class);
        when(quests.data(playerId)).thenReturn(data);
        when(quests.isLoaded(playerId)).thenReturn(true);
        when(quests.selected(playerId)).thenReturn(Optional.of(new Quest(UUID.randomUUID(), "Вышка 1",
                "Задача", QuestState.CALIBRATED, 1, "type-1")));

        waves = mock(WaveConfig.class);
        when(waves.waves()).thenReturn(3);
        when(waves.typeIds()).thenReturn(List.of("type-1"));
        when(waves.type("type-1")).thenReturn(new WaveTypeSpec("type-1", 100,
                List.of(new WaveMobSpec(EntityType.ZOMBIE, new int[]{2}, List.of(), List.of())), List.of()));
        when(waves.rift()).thenReturn(new RiftSpec(Material.WOODEN_HOE, "wooden_hoe", "rift", 5f, 15, 64, 15, 15,
                32, RiftOpenMode.CHUNK, RiftCloud.DISABLED, List.of(new RiftFrame(0, 0f, 0f, 0.2f)),
                List.of(new RiftFrame(0, 1f, 1f, 1f)), List.of(new RiftFrame(0, 1f, 1f, 1f))));

        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world, 0.5, 64, 0.5));
        when(server.getPlayer(playerId)).thenReturn(player);

        // The rift needs a running server for its model, so the test only keeps the callback.
        var ownership = new VLADMIX90.towers.storage.ForcedChunkOwnership(
                key -> java.util.concurrent.CompletableFuture.completedFuture(null),
                key -> java.util.concurrent.CompletableFuture.completedFuture(null),
                new VLADMIX90.towers.storage.ForcedChunkOwnership.WorldAccess() {
                    public boolean available(VLADMIX90.towers.wave.ChunkKey key) { return true; }
                    public boolean forced(VLADMIX90.towers.wave.ChunkKey key) { return false; }
                    public void force(VLADMIX90.towers.wave.ChunkKey key, boolean value) { }
                }, Runnable::run);
        game = new GameManager(plugin, config, quests, mock(VLADMIX90.towers.menu.MenuManager.class), null, waves, ownership, Runnable::run) {
            @Override public void startWaves(Player target) {
                super.startWaves(target);
                tickWaveSearches();
            }
            @Override Rift createRift(Location center, RiftSpec spec, UUID ownerId, Runnable onOpened) {
                return mock(Rift.class);
            }
        };
    }

    @Test
    void theMobsWaitForTheRift() {
        game.startWaves(player);

        verify(world, never()).spawnEntity(any(Location.class), any(EntityType.class));
    }

    @Test
    void theRiftStartsTheWaveAfterThePause() {
        game.startWaves(player);
        game.onRiftOpened(player);

        ArgumentCaptor<Long> delay = ArgumentCaptor.forClass(Long.class);
        verify(scheduler).runTaskLater(eq(plugin), any(Runnable.class), delay.capture());
        assertTrue(delay.getValue() >= 100 && delay.getValue() <= 140,
                "the pause between the waves has to be five to seven seconds: " + delay.getValue());
    }

    @Test
    void theWaveComesAfterThePause() {
        game.startWaves(player);
        game.onRiftOpened(player);

        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), task.capture(), anyLong());
        task.getValue().run();

        verify(world, org.mockito.Mockito.times(2)).spawnEntity(any(Location.class), eq(EntityType.ZOMBIE));
    }

    @Test
    void anOpenRiftDoesNotStartTheWaveTwice() {
        game.startWaves(player);
        game.onRiftOpened(player);
        game.onRiftOpened(player);

        verify(scheduler, Mockito.times(1)).runTaskLater(eq(plugin), any(Runnable.class), anyLong());
    }

    @Test
    void theWaveStartsTheBattleSound() {
        game.startWaves(player);
        game.onRiftOpened(player);

        ArgumentCaptor<Runnable> waveTask = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), waveTask.capture(), anyLong());
        waveTask.getValue().run();

        verify(config).playSound(player, "sounds.battle");
    }

    @Test
    void clearingAWaveKeepsTheBattleSoundForTheNextWave() {
        game.startWaves(player);
        game.onRiftOpened(player);

        ArgumentCaptor<Runnable> waveTask = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), waveTask.capture(), anyLong());
        waveTask.getValue().run(); // the wave mobs are alive now
        when(quests.waveDefeated(playerId)).thenReturn(true);

        EntityDeathEvent death = mock(EntityDeathEvent.class);
        when(death.getEntity()).thenReturn(mob);
        game.onDeath(death);
        game.onDeath(death); // the wave is cleared now

        Mockito.verify(config, never()).stopSound(player, "sounds.battle");
    }

    @Test
    void theBattleSoundResumesAfterRelog() {
        game.startWaves(player);
        game.onRiftOpened(player);

        ArgumentCaptor<Runnable> waveTask = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), waveTask.capture(), anyLong());
        waveTask.getValue().run(); // the wave mobs are alive now

        game.quit(player);
        when(quests.selected(playerId)).thenReturn(Optional.of(new Quest(UUID.randomUUID(), "Вышка 1",
                "Задача", QuestState.WAVES_ACTIVE, 1, "type-1")));
        Mockito.clearInvocations(scheduler);
        Mockito.clearInvocations(config);

        game.resumeWaves(player);

        verify(config).playSound(player, "sounds.battle");
    }

    @Test
    void completedOfflineWaveOverridesSnapshotLoadedBeforeItsFinalSave() {
        data.select(UUID.randomUUID());
        data.quests().add(new Quest(data.selectedQuest(), "test", "test", QuestState.WAVES_ACTIVE, 1, null));
        game.startWaves(player);
        game.onRiftOpened(player);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), task.capture(), anyLong());
        task.getValue().run();
        game.quit(player);
        var stale = VLADMIX90.towers.storage.PlayerSnapshot.capture(data).toData();
        data.remainingWaves(0);
        data.quests().replaceAll(q -> q.withState(QuestState.COMPLETED));
        when(quests.isLoaded(playerId)).thenReturn(false);
        game.finishWaves(playerId);
        when(quests.isLoaded(playerId)).thenReturn(true);
        when(quests.data(playerId)).thenReturn(stale);
        game.resumeWaves(player);
        org.junit.jupiter.api.Assertions.assertEquals(0, stale.remainingWaves());
        org.junit.jupiter.api.Assertions.assertEquals(QuestState.COMPLETED, stale.quests().getFirst().state());
    }

    @Test
    void offlineWaveDeathsDoNotReadEvictedSession() {
        game.startWaves(player);
        game.onRiftOpened(player);
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTaskLater(eq(plugin), task.capture(), anyLong());
        task.getValue().run();
        game.quit(player);
        when(quests.isLoaded(playerId)).thenReturn(false);
        when(quests.data(playerId)).thenThrow(new IllegalStateException("evicted"));
        when(server.getPlayer(playerId)).thenReturn(null);
        EntityDeathEvent death = mock(EntityDeathEvent.class);
        when(death.getEntity()).thenReturn(mob);
        game.onDeath(death);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> game.onDeath(death));
        org.junit.jupiter.api.Assertions.assertEquals(2, data.remainingWaves());
    }

    @Test
    void thePauseIsFiveToSevenSeconds() {
        for (int seed = 0; seed < 100; seed++) {
            int ticks = GameManager.waveDelayTicks(new Random(seed), 5, 7);
            assertTrue(ticks >= 100 && ticks <= 140, "the pause has to be five to seven seconds: " + ticks);
        }
    }
}
