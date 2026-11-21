package VLADMIX90.towers.listener;

import VLADMIX90.towers.game.GameManager;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;

/**
 * Keeps the mobs of a wave from burning in daylight. The sun is the only fire source they are immune
 * to: lava, flaming arrows and other mobs still set them on fire.
 */
public final class WaveFireListener implements Listener {
    @EventHandler
    public void onCombust(EntityCombustEvent event) {
        Entity entity = event.getEntity();
        if (!GameManager.isWaveMob(entity)) return;
        if (!burnsWithoutCause(event)) return;
        event.setCancelled(true);
    }

    /**
     * True when nothing caused the combustion: that is the vanilla daylight burn of undead mobs. Lava
     * arrives as a block event, flaming arrows, other mobs and lightning as an entity event.
     */
    static boolean burnsWithoutCause(EntityCombustEvent event) {
        if (event instanceof EntityCombustByBlockEvent) return false;
        if (event instanceof EntityCombustByEntityEvent byEntity) return byEntity.getCombuster() == null;
        return true;
    }
}
