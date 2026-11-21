package VLADMIX90.towers.listener;

import VLADMIX90.towers.menu.MenuHolder;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.menu.MenuType;
import VLADMIX90.towers.menu.ClickGuard;
import VLADMIX90.towers.quest.QuestManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import VLADMIX90.towers.game.GameManager;

public final class MenuClickListener implements Listener {
    private final QuestManager quests;
    private final MenuManager menus;
    private final GameManager game;
    public MenuClickListener(QuestManager quests, MenuManager menus, GameManager game) { this.quests = quests; this.menus = menus; this.game = game; }
    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (holder.preview()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!quests.canAct(player.getUniqueId())) return;
        if (!ClickGuard.isLeftClick(event.getClick())) return;
        if (holder.type() == MenuType.CALIBRATION && MenuManager.calibrationSlots().contains(event.getRawSlot())) {
            game.calibrationClick(player, event.getRawSlot());
            return;
        }
        if (holder.type() == MenuType.TOWER) {
            for (int i = 0; i < MenuManager.towerSlots().size(); i++) {
                if (event.getRawSlot() != MenuManager.towerSlots().get(i)) continue;
                if (quests.selectTower(player.getUniqueId(), i + 1)) {
                    player.closeInventory();
                }
                return;
            }
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }
    @EventHandler public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder) {
            event.setCancelled(true);
        }
    }
}
