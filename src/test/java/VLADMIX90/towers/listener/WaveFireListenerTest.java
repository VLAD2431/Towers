package VLADMIX90.towers.listener;

import VLADMIX90.towers.game.GameManager;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Checks which fire sources still burn the mobs of a wave: only the daylight burn is blocked, lava
 * and flaming arrows keep working.
 */
class WaveFireListenerTest {

    private final WaveFireListener listener = new WaveFireListener();

    private static Entity waveMob() {
        Entity entity = mock(Entity.class);
        when(entity.getScoreboardTags()).thenReturn(Set.of(GameManager.WAVE_MOB_TAG));
        return entity;
    }

    private static Entity otherMob() {
        Entity entity = mock(Entity.class);
        when(entity.getScoreboardTags()).thenReturn(Set.of());
        return entity;
    }

    @Test
    void waveMobDoesNotBurnInDaylight() {
        EntityCombustEvent event = new EntityCombustEvent(waveMob(), 8f);
        listener.onCombust(event);
        assertTrue(event.isCancelled());
    }

    @Test
    void waveMobBurnsInLava() {
        EntityCombustByBlockEvent event = new EntityCombustByBlockEvent(mock(Block.class), waveMob(), 15f);
        listener.onCombust(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void waveMobBurnsFromFlameArrow() {
        EntityCombustByEntityEvent event = new EntityCombustByEntityEvent(mock(Entity.class), waveMob(), 5f);
        listener.onCombust(event);
        assertFalse(event.isCancelled());
    }

    @Test
    void otherMobStillBurnsInDaylight() {
        EntityCombustEvent event = new EntityCombustEvent(otherMob(), 8f);
        listener.onCombust(event);
        assertFalse(event.isCancelled());
    }
}
