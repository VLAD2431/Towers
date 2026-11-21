package VLADMIX90.towers.wave;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks which places count as water: the vanilla water biomes, the water blocks, the columns of
 * the world and the search for a dry column inside a chunk.
 */
class WaterGuardTest {

    /** World whose columns all end in a block of the given material. */
    private static World worldWith(Material surface) {
        World world = Mockito.mock(World.class);
        Block block = Mockito.mock(Block.class);
        Mockito.when(world.getHighestBlockYAt(Mockito.anyInt(), Mockito.anyInt())).thenReturn(64);
        Mockito.when(world.getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(block);
        Mockito.when(block.getType()).thenReturn(surface);
        return world;
    }

    /**
     * World whose surface is grass, except for the given block coordinates that hold water.
     */
    private static World worldWithWaterAt(int wetX, int wetZ) {
        World world = Mockito.mock(World.class);
        Mockito.when(world.getHighestBlockYAt(Mockito.anyInt(), Mockito.anyInt())).thenReturn(64);
        Mockito.when(world.getBlockAt(Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenAnswer(call -> {
            int x = call.getArgument(0);
            int z = call.getArgument(2);
            Block block = Mockito.mock(Block.class);
            Mockito.when(block.getType()).thenReturn(x == wetX && z == wetZ ? Material.WATER : Material.GRASS_BLOCK);
            return block;
        });
        return world;
    }

    @Test
    void oceansAndRiversAreWaterBiomes() {
        assertTrue(WaterGuard.isWaterBiomeKey("ocean"));
        assertTrue(WaterGuard.isWaterBiomeKey("warm_ocean"));
        assertTrue(WaterGuard.isWaterBiomeKey("lukewarm_ocean"));
        assertTrue(WaterGuard.isWaterBiomeKey("cold_ocean"));
        assertTrue(WaterGuard.isWaterBiomeKey("deep_ocean"));
        assertTrue(WaterGuard.isWaterBiomeKey("deep_lukewarm_ocean"));
        assertTrue(WaterGuard.isWaterBiomeKey("deep_cold_ocean"));
        assertTrue(WaterGuard.isWaterBiomeKey("deep_frozen_ocean"));
        assertTrue(WaterGuard.isWaterBiomeKey("frozen_ocean"));
        assertTrue(WaterGuard.isWaterBiomeKey("frozen_river"));
        assertTrue(WaterGuard.isWaterBiomeKey("river"));
    }

    @Test
    void theNamespaceOfTheBiomeDoesNotMatter() {
        assertTrue(WaterGuard.isWaterBiomeKey("minecraft:ocean"));
        assertFalse(WaterGuard.isWaterBiomeKey("custom:ocean"), "a biome of another mod is not a water biome");
    }

    @Test
    void landBiomesAreNotWaterBiomes() {
        assertFalse(WaterGuard.isWaterBiomeKey("plains"));
        assertFalse(WaterGuard.isWaterBiomeKey("forest"));
        assertFalse(WaterGuard.isWaterBiomeKey("taiga"));
        assertFalse(WaterGuard.isWaterBiomeKey("desert"));
        assertFalse(WaterGuard.isWaterBiomeKey("beach"));
        assertFalse(WaterGuard.isWaterBiomeKey("snowy_beach"));
        assertFalse(WaterGuard.isWaterBiomeKey("swamp"));
        assertFalse(WaterGuard.isWaterBiomeKey("mangrove_swamp"));
        assertFalse(WaterGuard.isWaterBiomeKey("nether_wastes"));
        assertFalse(WaterGuard.isWaterBiomeKey("the_end"));
    }

    @Test
    void anUnknownOrMissingBiomeIsNotWater() {
        assertFalse(WaterGuard.isWaterBiomeKey(null));
        assertFalse(WaterGuard.isWaterBiomeKey(""));
        assertFalse(WaterGuard.isWaterBiomeKey("my_custom_ocean"));
    }

    @Test
    void waterBlocksAreWater() {
        assertTrue(WaterGuard.isWaterBlock(Material.WATER));
        assertTrue(WaterGuard.isWaterBlock(Material.BUBBLE_COLUMN));
    }

    @Test
    void theIceOfAFrozenPondIsWater() {
        assertTrue(WaterGuard.isWaterBlock(Material.ICE));
        assertTrue(WaterGuard.isWaterBlock(Material.PACKED_ICE));
        assertTrue(WaterGuard.isWaterBlock(Material.BLUE_ICE));
    }

    @Test
    void dryBlocksAreNotWater() {
        assertFalse(WaterGuard.isWaterBlock(Material.GRASS_BLOCK));
        assertFalse(WaterGuard.isWaterBlock(Material.STONE));
        assertFalse(WaterGuard.isWaterBlock(Material.SAND));
        assertFalse(WaterGuard.isWaterBlock(null));
    }

    @Test
    void aColumnWithWaterOnTheSurfaceIsWater() {
        assertTrue(WaterGuard.isWaterColumn(worldWith(Material.WATER), 0, 0, block -> "minecraft:plains"));
    }

    @Test
    void aColumnInAWaterBiomeIsWater() {
        assertTrue(WaterGuard.isWaterColumn(worldWith(Material.GRASS_BLOCK), 0, 0, block -> "minecraft:ocean"));
    }

    @Test
    void aFrozenOceanIsWaterEvenWithIceOnTheSurface() {
        assertTrue(WaterGuard.isWaterColumn(worldWith(Material.ICE), 0, 0, block -> "minecraft:frozen_ocean"));
    }

    @Test
    void aFrozenPondInALandBiomeIsWater() {
        assertTrue(WaterGuard.isWaterColumn(worldWith(Material.ICE), 0, 0, block -> "minecraft:plains"));
    }

    @Test
    void aDryColumnIsNotWater() {
        assertFalse(WaterGuard.isWaterColumn(worldWith(Material.GRASS_BLOCK), 0, 0, block -> "minecraft:plains"));
    }

    @Test
    void aColumnWithoutABiomeIsNotWater() {
        assertFalse(WaterGuard.isWaterColumn(worldWith(Material.GRASS_BLOCK), 0, 0, block -> null));
    }

    @Test
    void aChunkWithWaterIsWater() {
        assertTrue(WaterGuard.isWaterChunk(worldWith(Material.WATER), 0, 0, block -> "minecraft:plains"));
    }

    @Test
    void aDryChunkIsNotWater() {
        assertFalse(WaterGuard.isWaterChunk(worldWith(Material.GRASS_BLOCK), 0, 0, block -> "minecraft:plains"));
    }

    @Test
    void aWetCornerMakesTheChunkWater() {
        assertTrue(WaterGuard.isWaterChunk(worldWithWaterAt(15, 15), 0, 0, block -> "minecraft:plains"));
    }

    @Test
    void aWetEdgeMidpointMakesTheChunkWater() {
        assertTrue(WaterGuard.isWaterChunk(worldWithWaterAt(8, 0), 0, 0, block -> "minecraft:plains"));
    }

    @Test
    void aWetCentreMakesTheChunkWater() {
        assertTrue(WaterGuard.isWaterChunk(worldWithWaterAt(8, 8), 0, 0, block -> "minecraft:plains"));
    }

    @Test
    void aWetSpotOutsideTheSamplePointsLeavesTheChunkDry() {
        assertFalse(WaterGuard.isWaterChunk(worldWithWaterAt(7, 7), 0, 0, block -> "minecraft:plains"),
                "only the nine sample points of the chunk are checked");
    }

    @Test
    void theCentreOfTheChunkIsTheFirstCandidate() {
        assertArrayEquals(new int[]{8, 8}, WaterGuard.firstDryColumn(0, 0, (x, z) -> false));
    }

    @Test
    void aWetCentreIsSkipped() {
        WaterGuard.ColumnProbe probe = (x, z) -> x == 8 && z == 8;
        int[] column = WaterGuard.firstDryColumn(0, 0, probe);
        assertNotNull(column);
        assertFalse(column[0] == 8 && column[1] == 8, "the wet centre must not be returned");
        assertFalse(probe.isWater(column[0], column[1]), "the returned column must be dry");
    }

    @Test
    void theSearchStaysInsideTheChunk() {
        int[] column = WaterGuard.firstDryColumn(64, -32, (x, z) -> x < 70);
        assertNotNull(column);
        assertTrue(column[0] >= 64 && column[0] < 80, "x outside the chunk: " + column[0]);
        assertTrue(column[1] >= -32 && column[1] < -16, "z outside the chunk: " + column[1]);
        assertTrue(column[0] >= 70, "the returned column must be dry: " + column[0]);
    }

    @Test
    void theFarCornerOfTheChunkIsReachable() {
        int[] column = WaterGuard.firstDryColumn(0, 0, (x, z) -> !(x == 0 && z == 0));
        assertNotNull(column);
        assertArrayEquals(new int[]{0, 0}, column);
    }

    @Test
    void aFullyWetChunkHasNoDryColumn() {
        assertNull(WaterGuard.firstDryColumn(0, 0, (x, z) -> true));
    }
}
