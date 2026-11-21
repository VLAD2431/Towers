package VLADMIX90.towers.wave;

import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Checks the rift cloud shipped in config.yml. */
class ShippedConfigRiftCloudTest {

    private static YamlConfiguration shippedYaml() {
        InputStream stream = ShippedConfigRiftCloudTest.class.getResourceAsStream("/config.yml");
        assertNotNull(stream, "config.yml must be on the classpath");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }

    /** Runs the shipped rift section through the real parser; wave-types need a live registry. */
    private static RiftCloud shippedCloud() {
        YamlConfiguration config = shippedYaml();
        config.set("wave-types", null);
        return new WaveConfig(config).rift().cloud();
    }

    @Test
    void shipsBothCloudParticles() {
        RiftCloud cloud = shippedCloud();
        assertEquals(2, cloud.particles().size());

        RiftParticleSpec ominous = cloud.particles().get(0);
        assertEquals(Particle.OMINOUS_SPAWNING, ominous.type());
        assertEquals(10, ominous.count());
        assertEquals(2.5, ominous.offsetX());
        assertEquals(1.5, ominous.offsetY());
        assertEquals(2.5, ominous.offsetZ());
        assertEquals(0.2, ominous.speed());

        RiftParticleSpec dust = cloud.particles().get(1);
        assertEquals(Particle.DUST_COLOR_TRANSITION, dust.type());
        assertEquals(5, dust.count());
        assertEquals(2.5, dust.offsetX());
        assertEquals(0.5, dust.offsetY());
        assertEquals(2.5, dust.offsetZ());
        assertEquals(0.0, dust.speed());
        Particle.DustTransition transition = (Particle.DustTransition) dust.data();
        assertEquals(0xE6FFFF, transition.getColor().asRGB());
        assertEquals(0x2BA3D9, transition.getToColor().asRGB());
        assertEquals(3f, transition.getSize());
        assertEquals(4, cloud.interval());
        assertEquals(128, cloud.viewDistance());
    }
}
