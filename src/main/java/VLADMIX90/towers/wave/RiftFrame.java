package VLADMIX90.towers.wave;

/**
 * A single keyframe of a rift animation. {@code tick} is the offset in ticks from the
 * start of the phase; {@code x}/{@code y}/{@code z} are per-axis scale multipliers
 * applied on top of {@link RiftSpec#scale()}.
 */
public record RiftFrame(int tick, float x, float y, float z) { }
