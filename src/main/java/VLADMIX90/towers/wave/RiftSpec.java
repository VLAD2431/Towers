package VLADMIX90.towers.wave;

import org.bukkit.Material;

import java.util.List;

public record RiftSpec(Material material, String itemModel, String customModelData,
                       float scale, int height, int viewDistance, int blockLight, int skyLight, int openDistance,
                       RiftOpenMode openMode, RiftCloud cloud,
                       List<RiftFrame> openFrames, List<RiftFrame> pulseFrames,
                       List<RiftFrame> closeFrames) { }
