package VLADMIX90.towers.wave;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks how the wave chunk is chosen: the sector is chosen first and the wave runs in it, so it
 * does not always start in the same corner; the sector of the computer is never used, chunks that
 * are already taken by another player are avoided and water chunks are never used at all.
 *
 * <p>The probe answers the question "is this chunk water?", so a chunk that the probe does not
 * call water is dry.
 */
class WaveChunkSelectorTest {

    private static final UUID WORLD = UUID.randomUUID();
    /** The size of a sector in chunks; the computer stands in the middle of the map. */
    private static final int SECTOR_SIZE = 5;
    /** The map is a 5×5 grid of sectors. */
    private static final int GRID_SIZE = 5;

    private static int[] pick(Random random, Set<ChunkKey> occupied, WaterGuard.ChunkProbe water) {
        return WaveChunkSelector.pick(random, WORLD, 0, 0, GRID_SIZE, SECTOR_SIZE, occupied, water);
    }

    @Test
    void picksTheOnlyDryChunk() {
        int[] picked = pick(new Random(1), Set.of(), (x, z) -> !(x == 3 && z == 3));
        assertArrayEquals(new int[]{3, 3}, picked);
    }

    @Test
    void neverPicksTheComputerSector() {
        for (int seed = 0; seed < 50; seed++) {
            int[] picked = pick(new Random(seed), Set.of(), (x, z) -> false);
            assertNotNull(picked);
            assertFalse(SectorGrid.contains(picked[0], picked[1], 0, 0, 0, 0, SECTOR_SIZE),
                    "the wave must not run in the sector of the computer: " + picked[0] + "," + picked[1]);
        }
    }

    @Test
    void theWaveStaysInsideTheSectorsOfTheMap() {
        for (int seed = 0; seed < 50; seed++) {
            int[] picked = pick(new Random(seed), Set.of(), (x, z) -> false);
            assertNotNull(picked);
            boolean onTheMap = false;
            for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                if (SectorGrid.contains(picked[0], picked[1], 0, 0, dx, dz, SECTOR_SIZE)) onTheMap = true;
            }
            assertTrue(onTheMap, "the wave must run in a sector of the map: " + picked[0] + "," + picked[1]);
        }
    }

    @Test
    void theWaveStaysOnTheMapWithAnEvenSectorSize() {
        int evenSector = 4;
        for (int seed = 0; seed < 50; seed++) {
            int[] picked = WaveChunkSelector.pick(new Random(seed), WORLD, 0, 0, GRID_SIZE, evenSector,
                    Set.of(), (x, z) -> false);
            assertNotNull(picked);
            assertFalse(SectorGrid.contains(picked[0], picked[1], 0, 0, 0, 0, evenSector),
                    "the wave must not run in the sector of the computer: " + picked[0] + "," + picked[1]);
            boolean onTheMap = false;
            for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
                if (SectorGrid.contains(picked[0], picked[1], 0, 0, dx, dz, evenSector)) onTheMap = true;
            }
            assertTrue(onTheMap, "the wave must run in a sector of the map: " + picked[0] + "," + picked[1]);
        }
    }

    @Test
    void neverPicksTheComputerChunk() {
        int[] picked = WaveChunkSelector.pick(new Random(1), WORLD, 0, 0, GRID_SIZE, 1, Set.of(), (x, z) -> false);
        assertNotNull(picked);
        assertTrue(picked[0] != 0 || picked[1] != 0, "the computer chunk itself must never be used");
    }

    @Test
    void skipsChunksThatAreAlreadyTaken() {
        Set<ChunkKey> occupied = Set.of(new ChunkKey(WORLD, 3, 3));
        int[] picked = pick(new Random(1), occupied, (x, z) -> !(x == 3 && (z == 3 || z == 4)));
        assertArrayEquals(new int[]{3, 4}, picked, "a taken chunk must not be picked while a free one is left");
    }

    @Test
    void fallsBackToAnOccupiedButDryChunk() {
        // every chunk of the area is taken, so the only dry chunk is used although it is occupied
        Set<ChunkKey> occupied = new HashSet<>();
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) occupied.add(new ChunkKey(WORLD, x, z));
        int[] picked = pick(new Random(1), occupied, (x, z) -> !(x == 3 && z == 3));
        assertArrayEquals(new int[]{3, 3}, picked, "a dry chunk must be used even when it is already taken");
    }

    @Test
    void returnsNothingWhenEveryChunkIsWater() {
        assertNull(pick(new Random(1), Set.of(), (x, z) -> true), "there is no dry chunk to run the wave on");
    }

    @Test
    void theWaveDoesNotStartOutsideTheMap() {
        assertNull(pick(new Random(1), Set.of(), (x, z) -> !(x == 20 && z == 20)),
                "the wave must not start outside the sectors of the map");
    }

    @Test
    void searchesTheWholeNearbyArea() {
        int[] picked = pick(new Random(1), Set.of(), (x, z) -> !(x == 6 && z == 3));
        assertArrayEquals(new int[]{6, 3}, picked);
    }

    @Test
    void picksTheOnlyDryChunkAtTheFarEdgeOfATwentyChunkSector() {
        int[] picked = WaveChunkSelector.pick(new Random(1), WORLD, 0, 0, 5, 20,
                Set.of(), (x, z) -> !(x == 29 && z == 29));

        assertArrayEquals(new int[]{29, 29}, picked,
                "a dry chunk anywhere in the selected sector must be eligible, not only its centre");
    }

    @Test
    void everyChunkIsProbedOnlyOnce() {
        Set<String> probed = new HashSet<>();
        pick(new Random(1), Set.of(), (x, z) -> {
            assertTrue(probed.add(x + ":" + z), "a chunk must not be probed twice");
            return true;
        });
        assertFalse(probed.isEmpty(), "the water probe must be asked about the chunks");
    }

    @Test
    void theSearchDoesNotSweepTheWholeMap() {
        Set<String> probed = new HashSet<>();
        pick(new Random(1), Set.of(), (x, z) -> {
            probed.add(x + ":" + z);
            return true;
        });
        assertTrue(probed.size() < 25 * 25, "the search must not probe the whole area of the map: " + probed.size());
    }

    @Test
    void theWaveIsSpreadOverTheSectors() {
        Set<String> sectors = new LinkedHashSet<>();
        for (int seed = 0; seed < 200; seed++) {
            int[] picked = pick(new Random(seed), Set.of(), (x, z) -> false);
            assertNotNull(picked);
            sectors.add(sectorOf(picked[0], picked[1]));
        }
        assertTrue(sectors.size() >= 10,
                "the wave has to appear in many sectors, not in the same one again and again: " + sectors.size());
    }

    @Test
    void theWaveDoesNotAlwaysStartInTheSameSector() {
        int[] firstPick = pick(new Random(0), Set.of(), (x, z) -> false);
        String first = sectorOf(firstPick[0], firstPick[1]);
        boolean different = false;
        for (int seed = 1; seed < 20 && !different; seed++) {
            int[] picked = pick(new Random(seed), Set.of(), (x, z) -> false);
            different = !first.equals(sectorOf(picked[0], picked[1]));
        }
        assertTrue(different, "the wave must not always start in the same sector");
    }

    @Test
    void theSectorThatHoldsADryChunkIsUsed() {
        int[] picked = pick(new Random(1), Set.of(), (x, z) -> !nearCentre(x, z, 1, 1));
        assertNotNull(picked);
        assertTrue(nearCentre(picked[0], picked[1], 1, 1),
                "the wave has to run in the only sector that holds a dry chunk: " + picked[0] + "," + picked[1]);
    }

    @Test
    void aTakenCentreIsSkippedWhenANeighbourIsFree() {
        Set<ChunkKey> occupied = Set.of(new ChunkKey(WORLD, 5, 5));
        int[] picked = pick(new Random(1), occupied, (x, z) -> !nearCentre(x, z, 1, 1));
        assertNotNull(picked);
        assertFalse(picked[0] == 5 && picked[1] == 5, "a taken chunk must not be used while a free one is left");
    }

    @Test
    void aTakenSectorIsUsedWhenNothingElseIsFree() {
        Set<ChunkKey> occupied = new HashSet<>();
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) occupied.add(new ChunkKey(WORLD, x, z));
        int[] picked = pick(new Random(1), occupied, (x, z) -> !nearCentre(x, z, 1, 1));
        assertNotNull(picked);
        assertTrue(nearCentre(picked[0], picked[1], 1, 1),
                "the only dry chunk is used even when it is already taken");
    }

    @Test
    void aDryChunkIsKept() {
        assertTrue(WaveChunkSelector.canKeep(new int[]{3, 3}, (x, z) -> false));
    }

    @Test
    void aChunkThatBecameWaterIsNotKept() {
        assertFalse(WaveChunkSelector.canKeep(new int[]{3, 3}, (x, z) -> x == 3 && z == 3));
    }

    @Test
    void withoutAStoredChunkNothingIsKept() {
        assertFalse(WaveChunkSelector.canKeep(null, (x, z) -> false));
    }

    /** The name of the sector the chunk lies in. */
    private static String sectorOf(int chunkX, int chunkZ) {
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) {
            if (SectorGrid.contains(chunkX, chunkZ, 0, 0, dx, dz, SECTOR_SIZE)) return dx + ":" + dz;
        }
        return "outside";
    }

    /** True when the chunk lies at the centre of the sector or right next to it. */
    private static boolean nearCentre(int chunkX, int chunkZ, int offsetX, int offsetZ) {
        int centreX = SectorGrid.center(0, offsetX, SECTOR_SIZE);
        int centreZ = SectorGrid.center(0, offsetZ, SECTOR_SIZE);
        return Math.abs(chunkX - centreX) <= 1 && Math.abs(chunkZ - centreZ) <= 1;
    }
}
