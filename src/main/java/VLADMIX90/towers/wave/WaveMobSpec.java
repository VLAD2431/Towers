package VLADMIX90.towers.wave;

import org.bukkit.entity.EntityType;

import java.util.List;

public record WaveMobSpec(EntityType type, String customId, int[] counts, List<MobEffectSpec> effects,
                          List<MobParticleSpec> particles) {
    public WaveMobSpec(EntityType type, int[] counts, List<MobEffectSpec> effects, List<MobParticleSpec> particles) {
        this(type, null, counts, effects, particles);
    }
    public int countForWave(int waveIndex) {
        if (counts == null || counts.length == 0) return 0;
        int index = Math.max(1, waveIndex) - 1;
        return index < counts.length ? Math.max(0, counts[index]) : 0;
    }
}
