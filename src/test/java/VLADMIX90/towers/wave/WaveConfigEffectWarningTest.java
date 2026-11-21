package VLADMIX90.towers.wave;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks that unknown names in wave-types are reported instead of being dropped silently.
 *
 * <p>{@code PotionEffectType} cannot be initialized without a running server (its static
 * initializer reads the server registry), so the effect resolver is injected and the tests
 * pass a fake one that never touches the registry.
 */
class WaveConfigEffectWarningTest {

    private final List<String> messages = new ArrayList<>();
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = Logger.getLogger("test-effect-warning-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { messages.add(record.getMessage()); }
            @Override public void flush() {}
            @Override public void close() {}
        });
    }

    private YamlConfiguration configWith(Object effects) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("wave-types.type-1.chance", 100);
        config.set("wave-types.type-1.mobs.ZOMBIE.counts", List.of(1));
        config.set("wave-types.type-1.mobs.ZOMBIE.effects", effects);
        return config;
    }

    /** Resolver that resolves nothing — every effect name counts as unknown. */
    private static Function<String, PotionEffectType> nothingKnown() { return name -> null; }

    @Test
    void unknownEffectIsSkipped() {
        WaveConfig waves = new WaveConfig(configWith(List.of(Map.of("type", "STRENGHT", "level", 2, "duration", -1))),
                logger, nothingKnown());
        assertTrue(waves.type("type-1").mobs().get(0).effects().isEmpty(),
                "неизвестный эффект не должен попадать в спецификацию моба");
    }

    @Test
    void unknownEffectIsLogged() {
        new WaveConfig(configWith(List.of(Map.of("type", "STRENGHT", "level", 2, "duration", -1))), logger, nothingKnown());
        assertTrue(messages.stream().anyMatch(message -> message.contains("STRENGHT") && message.contains("Неизвестный")),
                "в логе должно быть сообщение о неизвестном эффекте: " + messages);
    }

    @Test
    void knownEffectKeepsLevelAndDuration() {
        List<Map<?, ?>> maps = List.of(Map.of("type", "STRENGTH", "level", 2, "duration", -1));
        List<String> unknown = new ArrayList<>();
        List<WaveConfig.EffectEntry<String>> entries = WaveConfig.parseEffectEntries(maps,
                name -> "STRENGTH".equals(name) ? name : null, unknown::add);
        assertEquals(1, entries.size());
        assertEquals("STRENGTH", entries.get(0).type());
        assertEquals(2, entries.get(0).level());
        assertEquals(-1, entries.get(0).duration());
        assertTrue(unknown.isEmpty(), "известный эффект не должен попадать в отчёт: " + unknown);
    }

    @Test
    void unknownEffectIsReportedAndSkippedByParser() {
        List<Map<?, ?>> maps = List.of(Map.of("type", "STRENGHT", "level", 2, "duration", -1));
        List<String> unknown = new ArrayList<>();
        List<WaveConfig.EffectEntry<String>> entries = WaveConfig.parseEffectEntries(maps, name -> null, unknown::add);
        assertTrue(entries.isEmpty());
        assertEquals(List.of("STRENGHT"), unknown);
    }

    @Test
    void unknownParticleIsLogged() {
        YamlConfiguration config = configWith(List.of());
        config.set("wave-types.type-1.mobs.ZOMBIE.particles", List.of(Map.of("type", "NOT_A_PARTICLE", "count", 1)));
        new WaveConfig(config, logger, nothingKnown());
        assertTrue(messages.stream().anyMatch(message -> message.contains("NOT_A_PARTICLE") && message.contains("Неизвестная")),
                "в логе должно быть сообщение о неизвестной частице: " + messages);
    }

    @Test
    void unknownMobIsLogged() {
        YamlConfiguration config = configWith(List.of());
        config.set("wave-types.type-1.mobs.NOT_A_MOB.counts", List.of(1));
        new WaveConfig(config, logger, nothingKnown());
        assertTrue(messages.stream().anyMatch(message -> message.contains("NOT_A_MOB") && message.contains("Неизвестный")),
                "в логе должно быть сообщение о неизвестном мобе: " + messages);
    }
}
