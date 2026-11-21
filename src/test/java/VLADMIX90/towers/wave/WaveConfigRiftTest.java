package VLADMIX90.towers.wave;

import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WaveConfigRiftTest {

    private static RiftSpec parseRift(Map<String, Object> rift) {
        YamlConfiguration config = new YamlConfiguration();
        rift.forEach(config::set);
        return new WaveConfig(config).rift();
    }

    private static Map<String, Object> rift(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put("rift." + keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> frame(int tick, double x, double y, double z) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tick", tick);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        return map;
    }

    private static Map<String, Object> cloudParticle(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return map;
    }

    @Test
    void parsesOpenFrames() {
        RiftSpec spec = parseRift(rift("open-frames", List.of(
                frame(0, 0, 0, 0.2),
                frame(2, 1, 0, 0.2))));
        assertEquals(List.of(
                new RiftFrame(0, 0f, 0f, 0.2f),
                new RiftFrame(2, 1f, 0f, 0.2f)), spec.openFrames());
    }

    @Test
    void defaultsWhenFramesMissing() {
        RiftSpec spec = parseRift(rift("scale", 5));
        assertEquals(List.of(
                new RiftFrame(0, 0f, 0f, 0.2f),
                new RiftFrame(2, 1f, 0f, 0.2f),
                new RiftFrame(4, 1f, 0.2f, 0.2f),
                new RiftFrame(7, 1f, 0.2f, 1f),
                new RiftFrame(10, 1f, 1f, 1f)), spec.openFrames());
        assertEquals(List.of(
                new RiftFrame(0, 1f, 1f, 1f),
                new RiftFrame(2, 1f, 1.02f, 1.02f),
                new RiftFrame(4, 1.02f, 1.02f, 1f),
                new RiftFrame(6, 1f, 1f, 1f),
                new RiftFrame(8, 1f, 1.02f, 1.02f),
                new RiftFrame(10, 1.02f, 1.02f, 1f),
                new RiftFrame(12, 1f, 1f, 1f)), spec.pulseFrames());
        assertEquals(List.of(
                new RiftFrame(0, 1f, 1f, 1f),
                new RiftFrame(3, 1f, 0.2f, 1f),
                new RiftFrame(6, 1f, 0.2f, 0.2f),
                new RiftFrame(8, 1f, 0f, 0.2f),
                new RiftFrame(10, 0f, 0f, 0.2f)), spec.closeFrames());
        assertEquals(32, spec.openDistance());
    }

    @Test
    void parsesOpenDistance() {
        assertEquals(48, parseRift(rift("open-distance", 48)).openDistance());
        assertEquals(0, parseRift(rift("open-distance", -7)).openDistance());
    }

    @Test
    void parsesRiftViewDistanceSeparatelyFromCloudViewDistance() {
        RiftSpec spec = parseRift(rift("view-distance", 64, "cloud.view-distance", 128));

        assertEquals(64, spec.viewDistance());
        assertEquals(128, spec.cloud().viewDistance());
    }

    @Test
    void clampsAndSortsFrames() {
        RiftSpec spec = parseRift(rift("open-frames", List.of(
                frame(5, -1, 2, 0.4),
                frame(1, 0.5, 0, 0))));
        assertEquals(List.of(
                new RiftFrame(1, 0.5f, 0f, 0f),
                new RiftFrame(5, 0f, 2f, 0.4f)), spec.openFrames());
    }

    @Test
    void parsesEmptyCloseFrames() {
        assertTrue(parseRift(rift("close-frames", List.of())).closeFrames().isEmpty());
    }

    @Test
    void parsesOpenMode() {
        assertEquals(RiftOpenMode.DISTANCE, parseRift(rift("open-mode", "distance")).openMode());
        assertEquals(RiftOpenMode.CHUNK, parseRift(rift("open-mode", "CHUNK")).openMode());
    }

    @Test
    void anUnknownOpenModeKeepsTheRiftSleeping() {
        assertEquals(RiftOpenMode.CHUNK, parseRift(rift("open-mode", "whenever")).openMode());
    }

    @Test
    void theRiftSleepsByDefault() {
        assertEquals(RiftOpenMode.CHUNK, parseRift(rift("scale", 5)).openMode());
    }

    @Test
    void parsesTheCloudParticles() {
        RiftCloud cloud = parseRift(rift(
                "cloud.interval", 4,
                "cloud.view-distance", 128,
                "cloud.particles", List.of(
                        cloudParticle("type", "OMINOUS_SPAWNING", "count", 10,
                                "offset-x", 2.5, "offset-y", 1.5, "offset-z", 2.5, "speed", 0.2),
                        cloudParticle("type", "DUST_COLOR_TRANSITION", "count", 5,
                                "offset-x", 2.5, "offset-y", 0.5, "offset-z", 2.5, "speed", 0,
                                "size", 3, "from-color", "#E6FFFF", "to-color", "#2BA3D9")))).cloud();

        assertEquals(4, cloud.interval());
        assertEquals(128, cloud.viewDistance());
        assertEquals(2, cloud.particles().size());

        RiftParticleSpec ominous = cloud.particles().get(0);
        assertEquals(Particle.OMINOUS_SPAWNING, ominous.type());
        assertEquals(10, ominous.count());
        assertEquals(2.5, ominous.offsetX());
        assertEquals(1.5, ominous.offsetY());
        assertEquals(2.5, ominous.offsetZ());
        assertEquals(0.2, ominous.speed());
        assertNull(ominous.data());

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
    }

    @Test
    void parsesTheLegacyCloud() {
        RiftCloud cloud = parseRift(rift("cloud.type", "FLAME", "cloud.count", 12,
                "cloud.radius", 3.5, "cloud.interval", 6, "cloud.view-distance", 100)).cloud();

        assertEquals(1, cloud.particles().size());
        RiftParticleSpec spec = cloud.particles().get(0);
        assertEquals(Particle.FLAME, spec.type());
        assertEquals(12, spec.count());
        assertEquals(3.5, spec.offsetX());
        assertEquals(3.5, spec.offsetY());
        assertEquals(3.5, spec.offsetZ());
    }

    @Test
    void theCloudIsOffWithoutItsSection() {
        assertEquals(RiftCloud.DISABLED, parseRift(rift("scale", 5)).cloud());
    }

    @Test
    void anUnknownParticleTurnsTheCloudOff() {
        RiftCloud cloud = parseRift(rift("cloud.particles",
                List.of(cloudParticle("type", "not_a_particle")))).cloud();
        assertFalse(cloud.enabled());
    }

    @Test
    void anEmptyCloudIsOff() {
        RiftCloud cloud = parseRift(rift("cloud.particles",
                List.of(cloudParticle("type", "PORTAL", "count", 0)))).cloud();
        assertFalse(cloud.enabled());
    }

    @Test
    void explicitEmptyParticlesTurnsTheCloudOff() {
        assertFalse(parseRift(rift("cloud.particles", List.of())).cloud().enabled());
    }

    @Test
    void theCloudIsAsWideAsTheRiftByDefault() {
        RiftParticleSpec spec = parseRift(rift("cloud.type", "PORTAL")).cloud().particles().get(0);
        assertEquals(5.0, spec.offsetX());
        assertEquals(5.0, spec.offsetY());
        assertEquals(5.0, spec.offsetZ());
    }

    @Test
    void nonFiniteFactorsBecomeZero() {
        RiftSpec spec = parseRift(rift("open-frames", List.of(frame(0, Double.NaN, Double.POSITIVE_INFINITY, 0.5))));
        assertEquals(0f, spec.openFrames().get(0).x());
        assertEquals(0f, spec.openFrames().get(0).y());
        assertEquals(0.5f, spec.openFrames().get(0).z());
    }
}
