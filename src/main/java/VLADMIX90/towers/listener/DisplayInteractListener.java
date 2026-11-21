package VLADMIX90.towers.listener;

import VLADMIX90.towers.display.CustomDisplayManager;
import VLADMIX90.towers.display.DisplayObject;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.game.GameManager;
import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class DisplayInteractListener implements Listener {
    private final CustomDisplayManager displays;
    private final MenuManager menus;
    private final GameManager game;
    private java.util.function.Predicate<java.util.UUID> ready = ignored -> true;
    public DisplayInteractListener(CustomDisplayManager displays, MenuManager menus, GameManager game, VLADMIX90.towers.quest.QuestManager quests) {
        this(displays, menus, game); ready = quests::canAct;
    }
    public DisplayInteractListener(CustomDisplayManager displays, MenuManager menus, GameManager game) {
        this.displays = displays; this.menus = menus; this.game = game;
    }
    @EventHandler public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Interaction interaction)) return;
        DisplayObject object = displays.find(interaction);
        if (object == null) return;
        event.setCancelled(true);
        if (!ready.test(event.getPlayer().getUniqueId())) {
            event.getPlayer().sendMessage(VLADMIX90.towers.util.Text.parse("<yellow>Задания ещё не загружены."));
            return;
        }
        switch (object.key()) {
            case "central-tower" -> menus.openTower(event.getPlayer());
            case "calibration-tower" -> game.openCalibrationMenu(event.getPlayer(), 0);
            case "chunk-tower" -> game.openChunkComputer(event.getPlayer(), interaction.getLocation(), object.towerId());
            default -> {
                if (object.isNumberedTower()) {
                    game.openCalibrationMenu(event.getPlayer(), object.towerId());
                    return;
                }
                if (!object.isChunkComputer()) return;
                game.openChunkComputer(event.getPlayer(), interaction.getLocation(), object.towerId());
            }
        }
    }
}
