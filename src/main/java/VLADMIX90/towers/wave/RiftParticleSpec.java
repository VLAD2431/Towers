package VLADMIX90.towers.wave;

import org.bukkit.Particle;

/** One particle of the rift cloud: how many, how wide, how fast and with what data. */
public record RiftParticleSpec(Particle type, int count, double offsetX, double offsetY,
                               double offsetZ, double speed, Object data) { }
