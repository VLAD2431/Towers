package VLADMIX90.towers.wave;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WaveConfigParticleTest {

    private static MobParticleSpec parseParticle(Map<String, Object> particle) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("wave-types.type-1.chance", 100);
        config.set("wave-types.type-1.mobs.ZOMBIE.counts", List.of(1));
        config.set("wave-types.type-1.mobs.ZOMBIE.effects", List.of());
        config.set("wave-types.type-1.mobs.ZOMBIE.particles", List.of(particle));
        WaveConfig waves = new WaveConfig(config);
        List<MobParticleSpec> particles = waves.type("type-1").mobs().get(0).particles();
        assertEquals(1, particles.size());
        return particles.get(0);
    }

    private static Map<String, Object> particle(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void keepsLegacyEntryDefaults() {
        MobParticleSpec spec = parseParticle(particle(
                "type", "FLAME", "count", 3, "spread", 0.3, "interval", 5));
        assertEquals(new MobParticleSpec(Particle.FLAME, 3, 0.3, 5, 0.0, null), spec);
    }

    @Test
    void parsesSpeed() {
        MobParticleSpec spec = parseParticle(particle("type", "FLAME", "speed", 0.25));
        assertEquals(0.25, spec.speed());
        assertNull(spec.data());
    }

    @Test
    void parsesTransitionColors() {
        MobParticleSpec spec = parseParticle(particle(
                "type", "DUST_COLOR_TRANSITION",
                "from-color", "#FF0000", "to-color", "#0000FF", "size", 1.5));
        assertInstanceOf(Particle.DustTransition.class, spec.data());
        Particle.DustTransition transition = (Particle.DustTransition) spec.data();
        assertEquals(Color.RED.asRGB(), transition.getColor().asRGB());
        assertEquals(Color.BLUE.asRGB(), transition.getToColor().asRGB());
        assertEquals(1.5f, transition.getSize());
    }

    @Test
    void parsesSingleDustColor() {
        MobParticleSpec spec = parseParticle(particle(
                "type", "DUST", "from-color", "#00FF00", "size", 2));
        assertInstanceOf(Particle.DustOptions.class, spec.data());
        Particle.DustOptions options = (Particle.DustOptions) spec.data();
        assertEquals(Color.LIME.asRGB(), options.getColor().asRGB());
        assertEquals(2f, options.getSize());
    }

    @Test
    void transitionWithoutToColorHasNoData() {
        MobParticleSpec spec = parseParticle(particle(
                "type", "DUST_COLOR_TRANSITION", "from-color", "#FF0000"));
        assertEquals(Particle.DUST_COLOR_TRANSITION, spec.type());
        assertNull(spec.data());
    }

    @Test
    void invalidColorYieldsNoData() {
        MobParticleSpec spec = parseParticle(particle("type", "DUST", "from-color", "red"));
        assertEquals(Particle.DUST, spec.type());
        assertNull(spec.data());
    }

    @Test
    void colorsIgnoredForNonColorParticle() {
        MobParticleSpec spec = parseParticle(particle(
                "type", "FLAME", "from-color", "#FF0000", "to-color", "#0000FF"));
        assertNull(spec.data());
    }
}
