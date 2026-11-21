package VLADMIX90.towers.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuHolder implements InventoryHolder {
    private final MenuType type;
    private final boolean preview;
    private Inventory inventory;

    public MenuHolder(MenuType type, boolean preview) {
        this.type = type;
        this.preview = preview;
    }

    public MenuType type() { return type; }
    public boolean preview() { return preview; }

    /** Called right after Bukkit creates the inventory this holder was passed to. */
    void attach(Inventory inventory) { this.inventory = inventory; }

    @Override public Inventory getInventory() { return inventory; }
}
