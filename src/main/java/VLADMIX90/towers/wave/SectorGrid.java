package VLADMIX90.towers.wave;

/**
 * The grid of sectors of the chunk map.
 *
 * <p>The computer stands in the sector in the middle of the map. Every sector is a square of
 * {@code sectorSize} chunks: the sector with the offset {@code (dx, dz)} is centred on the chunk
 * {@code computer + offset * sectorSize} and reaches {@code sectorSize / 2} chunks to each side of
 * its centre, so the sectors of the grid follow each other without a gap.
 */
public final class SectorGrid {
    private SectorGrid() { }

    /** The chunk the sector with the given offset is centred on. */
    public static int center(int computerChunk, int offset, int sectorSize) {
        return computerChunk + offset * Math.max(1, sectorSize);
    }

    /** The first chunk of the sector with the given offset. */
    public static int first(int computerChunk, int offset, int sectorSize) {
        int size = Math.max(1, sectorSize);
        return computerChunk + offset * size - size / 2;
    }

    /** True when the chunk lies in the sector with the given offset. */
    public static boolean contains(int chunkX, int chunkZ, int computerX, int computerZ,
                                   int offsetX, int offsetZ, int sectorSize) {
        int size = Math.max(1, sectorSize);
        int minX = first(computerX, offsetX, size);
        int minZ = first(computerZ, offsetZ, size);
        return chunkX >= minX && chunkX < minX + size && chunkZ >= minZ && chunkZ < minZ + size;
    }
}
