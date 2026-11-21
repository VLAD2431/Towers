package VLADMIX90.towers.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class WaveMobBoundaryTest {

    @Test
    void acceptsLocationsWithinFiftyHorizontalBlocks() {
        World world = mock(World.class);
        Location center = new Location(world, 10, 70, 10);

        assertTrue(GameManager.isWithinRiftBoundary(center, new Location(world, 60, -20, 10)));
        assertFalse(GameManager.isWithinRiftBoundary(center, new Location(world, 60.01, 70, 10)));
        assertFalse(GameManager.isWithinRiftBoundary(center, new Location(mock(World.class), 10, 70, 10)));
    }
}
