package VLADMIX90.towers.wave;

import org.bukkit.Chunk;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaperChunkEnvironmentTest {
    @Test void loadsAsynchronouslyAndReferenceCountsPluginTickets() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        World world = mock(World.class);
        ChunkKey key = new ChunkKey(UUID.randomUUID(), 2, 3);
        when(plugin.getServer()).thenReturn(server);
        when(server.getWorld(key.world())).thenReturn(world);
        CompletableFuture<Chunk> pending = new CompletableFuture<>();
        when(world.getChunkAtAsync(2, 3)).thenReturn(pending);
        var environment = new PaperChunkEnvironment(plugin, ignored -> false);
        var loaded = environment.load(key);
        assertFalse(loaded.isDone());
        verify(world, never()).getChunkAt(anyInt(), anyInt());
        pending.complete(mock(Chunk.class));
        assertTrue(loaded.isDone());
        environment.retain(key); environment.retain(key);
        verify(world, times(1)).addPluginChunkTicket(2, 3, plugin);
        environment.release(key);
        verify(world, never()).removePluginChunkTicket(2, 3, plugin);
        environment.release(key);
        verify(world).removePluginChunkTicket(2, 3, plugin);
    }
}
