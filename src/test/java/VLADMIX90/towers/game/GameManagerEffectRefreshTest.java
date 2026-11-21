package VLADMIX90.towers.game;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks which applied effects have to be dropped from a mob after the wave config changed.
 *
 * <p>The comparison is generic over the effect type so it can be tested without the potion registry.
 */
class GameManagerEffectRefreshTest {

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    @Test
    void effectsNoLongerConfiguredAreDropped() {
        assertEquals(Set.of("SPEED"), GameManager.removedEffects(set("SPEED", "STRENGTH"), set("STRENGTH", "RESISTANCE")));
    }

    @Test
    void nothingIsDroppedWhenTheConfigDidNotChange() {
        assertTrue(GameManager.removedEffects(set("SPEED", "STRENGTH"), set("SPEED", "STRENGTH")).isEmpty());
    }

    @Test
    void everythingIsDroppedWhenNoEffectIsConfiguredAnymore() {
        assertEquals(Set.of("SPEED", "STRENGTH"), GameManager.removedEffects(set("SPEED", "STRENGTH"), Set.of()));
    }

    @Test
    void nothingIsDroppedWithoutPreviousEffects() {
        assertTrue(GameManager.removedEffects(Set.of(), set("SPEED")).isEmpty());
    }
}
