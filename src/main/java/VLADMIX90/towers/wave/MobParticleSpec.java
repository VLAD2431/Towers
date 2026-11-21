package VLADMIX90.towers.wave;

import org.bukkit.Particle;

public record MobParticleSpec(Particle type, int count, double spread, int interval,
                              double speed, Object data) { }
