package VLADMIX90.towers.menu;

import org.bukkit.event.inventory.ClickType;

public final class ClickGuard {
    private ClickGuard() { }
    public static boolean isLeftClick(ClickType click) { return click == ClickType.LEFT; }
}
