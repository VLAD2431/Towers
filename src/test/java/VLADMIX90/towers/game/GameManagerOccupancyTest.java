package VLADMIX90.towers.game;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.model.PlayerQuestData;
import VLADMIX90.towers.quest.QuestManager;
import VLADMIX90.towers.wave.ChunkKey;
import VLADMIX90.towers.wave.WaveConfig;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks how the game manager gives the chunk of a wave back.
 *
 * <p>A chunk can host the waves of several players: it is only released when the last wave leaves
 * it, and a chunk that turned into water is given up so a dry one can be picked.
 */
class GameManagerOccupancyTest {

    private final UUID worldId = UUID.randomUUID();
    private final UUID playerId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();

    private World world;
    private GameManager game;
    private Player player;

    @BeforeEach
    void setUp() throws IOException {
        Path folder = Files.createTempDirectory("towers-occupancy");
        Files.writeString(folder.resolve("forced-chunks.yml"), "");
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        Server server = Mockito.mock(Server.class);
        world = Mockito.mock(World.class);
        Mockito.when(plugin.getDataFolder()).thenReturn(folder.toFile());
        Mockito.when(plugin.getServer()).thenReturn(server);
        Mockito.when(plugin.getLogger()).thenReturn(Mockito.mock(Logger.class));
        Mockito.when(server.getWorld(worldId)).thenReturn(world);
        Mockito.when(world.getChunkAtAsync(Mockito.anyInt(), Mockito.anyInt())).thenReturn(
                java.util.concurrent.CompletableFuture.completedFuture(Mockito.mock(org.bukkit.Chunk.class)));
        ConfigManager config = Mockito.mock(ConfigManager.class);
        Mockito.when(config.integer(Mockito.anyString(), Mockito.anyInt())).thenAnswer(call -> call.getArgument(1));
        var ownership = new VLADMIX90.towers.storage.ForcedChunkOwnership(
                key -> java.util.concurrent.CompletableFuture.completedFuture(null),
                key -> java.util.concurrent.CompletableFuture.completedFuture(null),
                new VLADMIX90.towers.storage.ForcedChunkOwnership.WorldAccess() {
                    public boolean available(ChunkKey key) { return true; }
                    public boolean forced(ChunkKey key) { return world.isChunkForceLoaded(key.x(), key.z()); }
                    public void force(ChunkKey key, boolean value) { world.setChunkForceLoaded(key.x(), key.z(), value); }
                }, Runnable::run);
        game = new GameManager(plugin, config, Mockito.mock(QuestManager.class), Mockito.mock(MenuManager.class),
                null, Mockito.mock(WaveConfig.class), ownership, Runnable::run);
        player = Mockito.mock(Player.class);
        Mockito.when(player.getUniqueId()).thenReturn(playerId);
    }

    @Test
    void theChunkStaysLoadedWhileAnotherWaveUsesIt() {
        PlayerQuestData data = new PlayerQuestData(playerId);
        data.waveChunk(worldId, 3, 3);
        game.forceLoadWaveChunk(data);
        game.markOccupied(new ChunkKey(worldId, 3, 3), playerId);
        game.markOccupied(new ChunkKey(worldId, 3, 3), otherId);

        game.releaseWaveChunk(data, playerId);
        Mockito.verify(world, Mockito.never()).setChunkForceLoaded(3, 3, false);

        game.releaseWaveChunk(data, otherId);
        Mockito.verify(world).setChunkForceLoaded(3, 3, false);
    }

    @Test
    void aChunkThatTurnedIntoWaterIsReleasedAndReplaced() {
        PlayerQuestData data = new PlayerQuestData(playerId);
        data.computerChunk(worldId, 0, 0);
        data.waveChunk(worldId, 3, 3);
        game.markOccupied(new ChunkKey(worldId, 3, 3), playerId);
        game.forceLoadWaveChunk(data);
        floodTheChunk(3, 3);

        var selected = game.ensureDryWaveChunk(playerId, data);
        assertFalse(selected.isDone(), "selection must wait for the tick budget");
        for (int tick = 0; tick < 1000 && !selected.isDone(); tick++) game.tickWaveSearches();
        assertTrue(selected.join(), "a dry chunk has to be found");
        assertFalse(data.waveChunkX() == 3 && data.waveChunkZ() == 3, "the flooded chunk must not be used");
        assertFalse(game.occupancy().isOccupied(new ChunkKey(worldId, 3, 3)), "the flooded chunk must be released");
        Mockito.verify(world).setChunkForceLoaded(3, 3, false);
        assertTrue(game.occupancy().isOccupied(new ChunkKey(worldId, data.waveChunkX(), data.waveChunkZ())),
                "the new chunk of the wave must be taken");
    }

    /** The given chunk is under water, every other chunk is dry land. */
    private void floodTheChunk(int chunkX, int chunkZ) {
        Mockito.when(world.getHighestBlockYAt(Mockito.anyInt(), Mockito.anyInt())).thenReturn(64);
        Mockito.when(world.getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenAnswer(call -> {
            int x = call.getArgument(0);
            int z = call.getArgument(2);
            boolean flooded = x >= chunkX * 16 && x < chunkX * 16 + 16 && z >= chunkZ * 16 && z < chunkZ * 16 + 16;
            Block block = Mockito.mock(Block.class);
            Mockito.when(block.getType()).thenReturn(flooded ? Material.WATER : Material.GRASS_BLOCK);
            return block;
        });
    }
}
