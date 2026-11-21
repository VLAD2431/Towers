package VLADMIX90.towers.wave;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the mob particle setup shipped in config.yml.
 *
 * <p>Only the particle entries are parsed: the effects section needs a live registry
 * ({@code PotionEffectType} cannot be initialized without a server), and it is untouched here.
 */
class ShippedConfigParticleTest {

    private static final Color FROM = Color.fromRGB(0xE6FFFF);
    private static final Color TO = Color.fromRGB(0x2BA3D9);

    private static YamlConfiguration shippedYaml() {
        InputStream stream = ShippedConfigParticleTest.class.getResourceAsStream("/config.yml");
        assertNotNull(stream, "config.yml must be on the classpath");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    /** Runs the shipped particle entries through the real parser. */
    private static List<MobParticleSpec> parse(List<Map<?, ?>> particles) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("wave-types.type-1.chance", 100);
        config.set("wave-types.type-1.mobs.ZOMBIE.counts", List.of(1));
        config.set("wave-types.type-1.mobs.ZOMBIE.effects", List.of());
        config.set("wave-types.type-1.mobs.ZOMBIE.particles", particles);
        return new WaveConfig(config).type("type-1").mobs().get(0).particles();
    }

    @Test
    void everyWaveMobHasBothParticles() {
        ConfigurationSection types = shippedYaml().getConfigurationSection("wave-types");
        assertNotNull(types, "config.yml must define wave types");
        for (String typeId : types.getKeys(false)) {
            ConfigurationSection mobs = types.getConfigurationSection(typeId + ".mobs");
            assertNotNull(mobs, typeId + " must define mobs");
            for (String mob : mobs.getKeys(false)) {
                String where = typeId + "/" + mob;
                List<Map<?, ?>> entries = mobs.getMapList(mob + ".particles");
                assertEquals(2, entries.size(), where + " must have both particles");

                List<MobParticleSpec> particles = parse(entries);
                assertEquals(2, particles.size(), where + " must have both parsable particles");

                MobParticleSpec dust = particles.get(0);
                assertEquals(Particle.DUST_COLOR_TRANSITION, dust.type(), where);
                assertEquals(5, dust.count(), where);
                assertEquals(0.3, dust.spread(), where);
                assertEquals(1, dust.interval(), where);
                assertEquals(0.0, dust.speed(), where);
                assertInstanceOf(Particle.DustTransition.class, dust.data(), where);
                Particle.DustTransition transition = (Particle.DustTransition) dust.data();
                assertEquals(FROM.asRGB(), transition.getColor().asRGB(), where);
                assertEquals(TO.asRGB(), transition.getToColor().asRGB(), where);
                assertEquals(0.7f, transition.getSize(), where);

                MobParticleSpec ominous = particles.get(1);
                assertEquals(Particle.OMINOUS_SPAWNING, ominous.type(), where);
                assertEquals(3, ominous.count(), where);
                assertEquals(0.3, ominous.spread(), where);
                assertEquals(5, ominous.interval(), where);
                assertEquals(0.2, ominous.speed(), where);
                assertNull(ominous.data(), where);
            }
        }
    }
}
