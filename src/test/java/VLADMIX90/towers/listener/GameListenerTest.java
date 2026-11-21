package VLADMIX90.towers.listener;

import VLADMIX90.towers.game.GameManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class GameListenerTest {

    @Test
    void cancelsWaveMobDamageFromOutsideRiftBoundary() {
        World world = mock(World.class);
        Player attacker = mock(Player.class);
        LivingEntity mob = waveMobAt(new Location(world, 10, 64, 10));
        when(attacker.getLocation()).thenReturn(new Location(world, 60.01, 64, 10));
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker);
        when(event.getEntity()).thenReturn(mob);

        GameManager game = mock(GameManager.class);
        when(game.shouldCancelWaveMobDamage(mob, attacker)).thenReturn(true);
        new GameListener(game).onEntityDamage(event);

        verify(event).setCancelled(true);
    }

    @Test
    void cancelsProjectileDamageWhenShooterIsOutsideBoundary() {
        World world = mock(World.class);
        Player shooter = mock(Player.class);
        Arrow arrow = mock(Arrow.class);
        LivingEntity mob = waveMobAt(new Location(world, 10, 64, 10));
        when(shooter.getLocation()).thenReturn(new Location(world, 80, 64, 10));
        when(arrow.getShooter()).thenReturn(shooter);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(arrow);
        when(event.getEntity()).thenReturn(mob);

        GameManager game = mock(GameManager.class);
        when(game.shouldCancelWaveMobDamage(mob, shooter)).thenReturn(true);
        new GameListener(game).onEntityDamage(event);

        verify(event).setCancelled(true);
    }

    @Test
    void leavesOrdinaryAndEnvironmentalDamageUnchanged() {
        Entity ordinaryMob = mock(Entity.class);
        EntityDamageByEntityEvent ordinaryEvent = mock(EntityDamageByEntityEvent.class);
        when(ordinaryEvent.getDamager()).thenReturn(mock(Player.class));
        when(ordinaryEvent.getEntity()).thenReturn(ordinaryMob);
        GameManager game = mock(GameManager.class);

        new GameListener(game).onEntityDamage(ordinaryEvent);

        verify(ordinaryEvent, never()).setCancelled(true);
        verify(game, never()).shouldCancelWaveMobDamage(any(), any());
    }

    @Test
    void usesProjectileShooterWhenResolvingDamageSource() {
        World world = mock(World.class);
        Arrow arrow = mock(Arrow.class);
        Player shooter = mock(Player.class);
        LivingEntity mob = waveMobAt(new Location(world, 10, 64, 10));
        when(shooter.getLocation()).thenReturn(new Location(world, 10, 64, 10));
        when(arrow.getShooter()).thenReturn(shooter);
        EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(arrow);
        when(event.getEntity()).thenReturn(mob);
        GameManager game = mock(GameManager.class);

        new GameListener(game).onEntityDamage(event);

        verify(game).shouldCancelWaveMobDamage(mob, shooter);
        verify(event, never()).setCancelled(true);
    }

    private static LivingEntity waveMobAt(Location location) {
        LivingEntity mob = mock(LivingEntity.class);
        when(mob.getScoreboardTags()).thenReturn(java.util.Set.of(GameManager.WAVE_MOB_TAG));
        when(mob.getLocation()).thenReturn(location);
        return mob;
    }

    @Test
    void waveZombieCannotTransformIntoDrowned() {
        Entity zombie = mock(Entity.class);
        Entity drowned = mock(Entity.class);
        EntityTransformEvent event = mock(EntityTransformEvent.class);
        when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
        when(zombie.getScoreboardTags()).thenReturn(java.util.Set.of(GameManager.WAVE_MOB_TAG));
        when(drowned.getType()).thenReturn(EntityType.DROWNED);
        when(event.getEntity()).thenReturn(zombie);
        when(event.getTransformedEntity()).thenReturn(drowned);

        new GameListener(mock(GameManager.class)).onEntityTransform(event);

        verify(event).setCancelled(true);
    }

    @Test
    void ordinaryZombieMayTransformIntoDrowned() {
        Entity zombie = mock(Entity.class);
        Entity drowned = mock(Entity.class);
        EntityTransformEvent event = mock(EntityTransformEvent.class);
        when(zombie.getType()).thenReturn(EntityType.ZOMBIE);
        when(zombie.getScoreboardTags()).thenReturn(java.util.Set.of());
        when(drowned.getType()).thenReturn(EntityType.DROWNED);
        when(event.getEntity()).thenReturn(zombie);
        when(event.getTransformedEntity()).thenReturn(drowned);

        new GameListener(mock(GameManager.class)).onEntityTransform(event);

        verify(event, never()).setCancelled(true);
    }
}
