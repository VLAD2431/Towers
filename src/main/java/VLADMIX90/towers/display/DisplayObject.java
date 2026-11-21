package VLADMIX90.towers.display;

import org.bukkit.entity.Interaction;

/**
 * An object of the plugin in the world: a plain click zone without any model.
 *
 * @param interaction the hitbox the player clicks
 * @param key         the name of the object, e.g. {@code tower-3} or {@code central-tower}
 * @param towerId     the number of the tower the object belongs to, 0 when it belongs to none
 */
public record DisplayObject(Interaction interaction, String key, int towerId) {
    public boolean isNumberedTower() { return key.startsWith("tower-"); }
    public boolean isChunkComputer() { return key.startsWith("computer-"); }
}
