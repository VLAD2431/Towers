package VLADMIX90.towers.game;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChunkComputerPreviewTest {
    @Test
    void previewMapCentersOnTheComputerWhenNoQuestHasBeenAssigned() {
        World world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.randomUUID());
        Chunk chunk = mock(Chunk.class);
        when(chunk.getX()).thenReturn(37);
        when(chunk.getZ()).thenReturn(-12);
        Location computer = mock(Location.class);
        when(computer.getWorld()).thenReturn(world);
        when(computer.getChunk()).thenReturn(chunk);

        assertArrayEquals(new int[]{37, -12}, GameManager.previewChunkCoordinates(computer));
    }
}
