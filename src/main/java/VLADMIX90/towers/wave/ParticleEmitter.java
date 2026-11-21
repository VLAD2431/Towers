package VLADMIX90.towers.wave;

import java.util.UUID;

public final class ParticleEmitter {
    private final UUID mobId;
    private final MobParticleSpec spec;
    private int ticks;

    public ParticleEmitter(UUID mobId, MobParticleSpec spec) { this.mobId = mobId; this.spec = spec; }
    public UUID mobId() { return mobId; }
    public MobParticleSpec spec() { return spec; }
    public int ticks() { return ticks; }
    public void tick() { ticks++; }
}
