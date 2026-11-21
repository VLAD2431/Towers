package VLADMIX90.towers.wave;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

import java.util.Set;
import java.util.function.Function;

/**
 * Water checks shared by the wave and the rift.
 *
 * <p>Paper has no {@code Biome.isWater()}, so the vanilla water biomes are listed by their key.
 * A place counts as water when its biome is one of them or when its surface block is water.
 */
public final class WaterGuard {
    private WaterGuard() { }

    /** Vanilla biomes that are water: the oceans and the rivers. */
    private static final Set<String> WATER_BIOME_KEYS = Set.of(
            "minecraft:ocean", "minecraft:warm_ocean", "minecraft:lukewarm_ocean",
            "minecraft:cold_ocean", "minecraft:deep_ocean", "minecraft:deep_lukewarm_ocean",
            "minecraft:deep_cold_ocean", "minecraft:deep_frozen_ocean", "minecraft:frozen_ocean",
            "minecraft:frozen_river", "minecraft:river");

    /** Blocks that are water: the water itself and the ice of a frozen pond. */
    private static final Set<Material> WATER_MATERIALS = Set.of(
            Material.WATER, Material.BUBBLE_COLUMN, Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE);

    /** Points of a chunk that are checked for water: the corners, the middle of the edges and the centre. */
    private static final int[] CHUNK_SAMPLES = {0, 8, 15};

    /** Tells whether the column at the given block coordinates holds water. */
    @FunctionalInterface
    public interface ColumnProbe {
        boolean isWater(int x, int z);
    }

    /** Tells whether the chunk at the given chunk coordinates counts as water. */
    @FunctionalInterface
    public interface ChunkProbe {
        boolean isWater(int chunkX, int chunkZ);
    }

    /**
     * True when the biome key names a vanilla water biome. The key may be given with or without
     * its namespace; a biome of another mod is never water.
     */
    public static boolean isWaterBiomeKey(String biomeKey) {
        if (biomeKey == null) return false;
        String key = biomeKey.indexOf(':') < 0 ? "minecraft:" + biomeKey : biomeKey;
        return WATER_BIOME_KEYS.contains(key);
    }

    /** True when the material is water. */
    public static boolean isWaterBlock(Material material) {
        return material != null && WATER_MATERIALS.contains(material);
    }

    /** True when the column is in a water biome or has water on its surface. */
    public static boolean isWaterColumn(World world, int x, int z) {
        return isWaterColumn(world, x, z, WaterGuard::biomeKey);
    }

    /**
     * Column check with a replaceable source of the biome key, so the rule can be verified without
     * a running server: the biome of a block cannot be created in a unit test.
     */
    public static boolean isWaterColumn(World world, int x, int z, Function<Block, String> biomeKey) {
        int top = world.getHighestBlockYAt(x, z);
        Block block = world.getBlockAt(x, top, z);
        if (isWaterBlock(block.getType())) return true;
        return isWaterBiomeKey(biomeKey.apply(block));
    }

    /** True when the chunk touches water at any of its sample points. */
    public static boolean isWaterChunk(World world, int chunkX, int chunkZ) {
        return isWaterChunk(world, chunkX, chunkZ, WaterGuard::biomeKey);
    }

    /** Chunk check with a replaceable source of the biome key. */
    public static boolean isWaterChunk(World world, int chunkX, int chunkZ, Function<Block, String> biomeKey) {
        int baseX = chunkX * 16, baseZ = chunkZ * 16;
        for (int dx : CHUNK_SAMPLES) for (int dz : CHUNK_SAMPLES)
            if (isWaterColumn(world, baseX + dx, baseZ + dz, biomeKey)) return true;
        return false;
    }

    /** The biome key of a block; null when the block has no biome. */
    private static String biomeKey(Block block) {
        Biome biome = block.getBiome();
        return biome == null || biome.getKey() == null ? null : biome.getKey().toString();
    }

    /**
     * First dry column of the chunk that starts at {@code baseX}/{@code baseZ}, searched from its
     * centre outwards. Null when the whole chunk is water.
     */
    public static int[] firstDryColumn(int baseX, int baseZ, ColumnProbe probe) {
        int centreX = baseX + 8, centreZ = baseZ + 8;
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = centreX + dx, z = centreZ + dz;
                    if (x < baseX || x >= baseX + 16 || z < baseZ || z >= baseZ + 16) continue;
                    if (!probe.isWater(x, z)) return new int[]{x, z};
                }
            }
        }
        return null;
    }
}
