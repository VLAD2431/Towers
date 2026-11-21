package VLADMIX90.towers.listener;

import VLADMIX90.towers.game.GameManager;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.entity.Projectile;

public final class GameListener implements Listener {
    private final GameManager game;
    public GameListener(GameManager game) { this.game = game; }
    @EventHandler public void onDeath(EntityDeathEvent event) { game.onDeath(event); }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!GameManager.isWaveMob(event.getEntity())) return;
        Entity attacker = event.getDamager();
        if (attacker instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (!(shooter instanceof Entity shooterEntity)) return;
            attacker = shooterEntity;
        }
        if (game.shouldCancelWaveMobDamage(event.getEntity(), attacker)) event.setCancelled(true);
    }

    @EventHandler
    public void onEntityMove(EntityMoveEvent event) {
        game.onEntityMove(event);
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (GameManager.isWaveMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityTransform(EntityTransformEvent event) {
        if (event.getEntity().getType() == EntityType.ZOMBIE
                && event.getTransformedEntity().getType() == EntityType.DROWNED
                && GameManager.isWaveMob(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity().getType() == EntityType.RAVAGER) {
            event.setCancelled(true);
        }
    }
}
