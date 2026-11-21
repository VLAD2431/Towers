package VLADMIX90.towers.wave;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Chooses the chunk a wave runs in.
 *
 * <p>The sector is chosen first: one of the sectors of the map at random, so the wave does not
 * always start in the same corner. The sector of the computer is never used (the chunk map never
 * marks that sector as found). Every chunk inside the chosen sector is eligible, so a wave can
 * appear across its entire area rather than only around its centre.
 */
public final class WaveChunkSelector {
    private WaveChunkSelector() { }

    /** How far from the computer the wave may start, in chunks, when no sector holds a dry chunk. */
    public static final int MAX_SEARCH_DISTANCE = 6;

    /**
     * Picks the chunk the wave runs in: a sector of the map at random, the dry chunk inside it that
     * is not taken by another player. Null when the whole area is water — the wave must not run
     * there.
     */
    public static int[] pick(Random random, UUID world, int computerX, int computerZ,
                             int gridSize, int sectorSize, Set<ChunkKey> occupied,
                             WaterGuard.ChunkProbe water) {
        Picker picker = new Picker(world, occupied, water);
        List<int[]> sectors = sectors(gridSize);
        if (random != null) Collections.shuffle(sectors, random);
        int[] taken = null;
        for (int[] sector : sectors) {
            for (int[] chunk : sectorChunks(random, computerX, computerZ, sector[0], sector[1], sectorSize)) {
                if (picker.isWater(chunk[0], chunk[1])) continue;
                if (!picker.isOccupied(chunk[0], chunk[1])) return chunk;
                // dry but taken by another player: it is only used when no free chunk is left
                if (taken == null) taken = chunk;
            }
        }
        if (taken != null) return taken;
        // No sector holds a dry chunk: the wave starts near the computer instead.
        return pickNearComputer(random, computerX, computerZ, gridSize, sectorSize, picker);
    }

    /**
     * True when the wave may keep running in the stored chunk; false when the chunk has become
     * water and a new one has to be chosen.
     */
    public static boolean canKeep(int[] stored, WaterGuard.ChunkProbe water) {
        return stored != null && !water.isWater(stored[0], stored[1]);
    }

    /**
     * The sectors of the map without the sector of the computer: the wave has to be visible on the
     * chunk map, and the sector of the computer is never marked as found.
     */
    static List<int[]> sectors(int gridSize) {
        int rings = Math.max(1, gridSize / 2);
        List<int[]> sectors = new ArrayList<>();
        for (int dz = -rings; dz <= rings; dz++) {
            for (int dx = -rings; dx <= rings; dx++) {
                if (dx == 0 && dz == 0) continue;
                sectors.add(new int[]{dx, dz});
            }
        }
        return sectors;
    }

    /**
     * Every chunk of one sector in a random order. A sector is selected first; shuffling its
     * complete area lets a wave appear anywhere in that sector.
     */
    static List<int[]> sectorChunks(Random random, int computerX, int computerZ,
                                            int offsetX, int offsetZ, int sectorSize) {
        int minX = SectorGrid.first(computerX, offsetX, sectorSize);
        int minZ = SectorGrid.first(computerZ, offsetZ, sectorSize);
        int size = Math.max(1, sectorSize);
        List<int[]> chunks = new ArrayList<>();
        for (int x = minX; x < minX + size; x++)
            for (int z = minZ; z < minZ + size; z++)
                chunks.add(new int[]{x, z});
        if (random != null) Collections.shuffle(chunks, random);
        return chunks;
    }

    /**
     * The chunks around the computer, nearest first: the rings of the Chebyshev distance, each ring
     * shuffled so two waves do not always start in the same corner. The sector of the computer is
     * left out, the wave has to be visible on the chunk map.
     */
    static List<int[]> candidates(int computerX, int computerZ, int sectorSize, int maxDistance,
                                          Random random) {
        List<int[]> candidates = new ArrayList<>();
        for (int distance = 1; distance <= maxDistance; distance++) {
            List<int[]> ring = new ArrayList<>();
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dz = -distance; dz <= distance; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != distance) continue;
                    int x = computerX + dx, z = computerZ + dz;
                    if (SectorGrid.contains(x, z, computerX, computerZ, 0, 0, sectorSize)) continue;
                    ring.add(new int[]{x, z});
                }
            }
            if (random != null) Collections.shuffle(ring, random);
            candidates.addAll(ring);
        }
        return candidates;
    }

    /** Picks the chunk near the computer: the nearest dry chunk that is not taken by another player. */
    private static int[] pickNearComputer(Random random, int computerX, int computerZ,
                                          int gridSize, int sectorSize, Picker picker) {
        int distance = searchDistance(gridSize, sectorSize);
        int[] taken = null;
        for (int[] candidate : candidates(computerX, computerZ, sectorSize, distance, random)) {
            if (picker.isWater(candidate[0], candidate[1])) continue;
            if (!picker.isOccupied(candidate[0], candidate[1])) return candidate;
            if (taken == null) taken = candidate;
        }
        return taken;
    }

    /**
     * How far the wave may start from the computer: the search never leaves the sectors of the map,
     * so the chunk of the wave is always shown on it, and it stops before the far side of the grid.
     */
    static int searchDistance(int gridSize, int sectorSize) {
        int rings = Math.max(1, gridSize / 2);
        int size = Math.max(1, sectorSize);
        // The outermost sector of the map reaches this far on both sides of the computer. With an
        // even sector size the two sides differ by one chunk, so the smaller one is used and the
        // wave always stays on the map.
        int reach = Math.min(rings * size + size - 1 - size / 2, rings * size + size / 2);
        // the search starts behind the sector of the computer, that one is never used
        int first = (size + 1) / 2;
        return Math.max(first, Math.min(MAX_SEARCH_DISTANCE, reach));
    }

    /**
     * Answers whether a chunk is dry and free. The water of a chunk is asked only once, so a chunk
     * that is looked at in two sectors is not probed twice.
     */
    private static final class Picker {
        private final UUID world;
        private final Set<ChunkKey> occupied;
        private final WaterGuard.ChunkProbe water;
        private final Set<Long> probed = new HashSet<>();
        private final Set<Long> dry = new HashSet<>();

        Picker(UUID world, Set<ChunkKey> occupied, WaterGuard.ChunkProbe water) {
            this.world = world; this.occupied = occupied; this.water = water;
        }

        boolean isWater(int chunkX, int chunkZ) {
            long id = id(chunkX, chunkZ);
            if (!probed.contains(id)) {
                probed.add(id);
                if (!water.isWater(chunkX, chunkZ)) dry.add(id);
            }
            return !dry.contains(id);
        }

        boolean isOccupied(int chunkX, int chunkZ) {
            return occupied.contains(new ChunkKey(world, chunkX, chunkZ));
        }

        private static long id(int chunkX, int chunkZ) {
            return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
        }
    }
}
