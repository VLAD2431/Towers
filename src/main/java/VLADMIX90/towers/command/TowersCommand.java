package VLADMIX90.towers.command;

import VLADMIX90.towers.config.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import VLADMIX90.towers.util.Text;
import VLADMIX90.towers.display.CustomDisplayManager;
import VLADMIX90.towers.game.GameManager;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.quest.QuestManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Location;

import java.util.Map;

public final class TowersCommand implements CommandExecutor {
    private final ConfigManager config;
    private final CustomDisplayManager displays;
    private final GameManager game;
    private final MenuManager menus;
    private final QuestManager quests;
    public TowersCommand(ConfigManager config, CustomDisplayManager displays, GameManager game, MenuManager menus, QuestManager quests) {
        this.config = config; this.displays = displays; this.game = game; this.menus = menus; this.quests = quests;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !quests.canAct(player.getUniqueId())) {
            sender.sendMessage(Text.parse("<yellow>Задания ещё не загружены."));
            return true;
        }
        if (!sender.hasPermission("towers.admin")) {
            sender.sendMessage(Text.parse("<red>Недостаточно прав."));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("wave")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Text.parse("<red>Эту команду может выполнить только игрок."));
            } else if (args.length != 2) {
                sender.sendMessage(Text.parse("<yellow>/towers wave <type>"));
            } else {
                sender.sendMessage(game.startStandaloneWaves(player, args[1])
                        ? Text.parse("<green>Рифт создан. Серия волн выбранного типа готова к запуску.")
                        : Text.parse("<red>Не удалось создать волны: проверьте тип, отсутствие активных волн и свободный сухой чанк."));
            }
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            config.reload();
            game.reloadWaves();
            game.refreshLiveMobEffects();
            displays.refresh();
            quests.rescheduleOnline();
            quests.refreshOnline();
            sender.sendMessage(config.message("reloaded"));
            return true;
        }
        if (args.length == 1 && (args[0].equalsIgnoreCase("refresh") || args[0].equalsIgnoreCase("update"))) {
            quests.forceRefreshOnline(config.integer("weekly-quest-count", 3));
            sender.sendMessage(Text.parse("<green>Задания всех игроков обновлены."));
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("spawn") && sender instanceof Player player) {
            if (args[1].equalsIgnoreCase("central")) {
                displays.spawnCentral(player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5));
                sender.sendMessage(Text.parse("<green>Центральная башня создана."));
                return true;
            }
            if (args[1].equalsIgnoreCase("computer")) {
                Location location = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
                var nearest = displays.nearestNumbered(location);
                if (nearest.isEmpty()) {
                    sender.sendMessage(config.message("computer-tower-not-found"));
                    return true;
                }
                int towerId = nearest.get().towerId();
                displays.spawnComputer(towerId, location);
                sender.sendMessage(config.message("computer-spawned", "{tower}", towerId));
                return true;
            }
            try {
                int tower = Integer.parseInt(args[1]);
                if (tower < 1 || tower > 16) throw new NumberFormatException();
                displays.spawnTower(tower, player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5));
                sender.sendMessage(Text.parse("<green>Башня калибровки " + tower + " создана."));
            } catch (NumberFormatException exception) {
                sender.sendMessage(Text.parse("<yellow>/towers spawn <1-16|central|computer>"));
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("remove")) {
            String target = args[1];
            if (target.equalsIgnoreCase("all")) {
                int removed = displays.removeAllSaved();
                sender.sendMessage(Text.parse("<green>Удалено объектов Towers: " + removed + "."));
                return true;
            }
            if (target.equalsIgnoreCase("central")) {
                sender.sendMessage(message(displays.removeByKey("central-tower") > 0, "Центральная башня удалена.", "Центральная башня не найдена."));
                return true;
            }
            if (target.equalsIgnoreCase("calibration")) {
                int removed = displays.removeByKey("calibration-tower") + displays.removeByPrefix("tower-");
                sender.sendMessage(message(removed > 0, "Нумерованные вышки калибровки удалены.", "Нумерованные вышки калибровки не найдены."));
                return true;
            }
            if (target.equalsIgnoreCase("chunk")) {
                int removed = displays.removeByKey("chunk-tower") + displays.removeByPrefix("computer-");
                sender.sendMessage(message(removed > 0, "Компьютеры чанков удалены.", "Компьютеры чанков не найдены."));
                return true;
            }
            try {
                int tower = Integer.parseInt(target);
                if (tower < 1 || tower > 16) throw new NumberFormatException();
                sender.sendMessage(message(displays.removeByTowerId(tower) > 0, "Башня " + tower + " и привязанные компьютеры удалены.", "Башня " + tower + " и привязанные компьютеры не найдены."));
            } catch (NumberFormatException exception) {
                sender.sendMessage(Text.parse("<yellow>/towers remove <1-16|central|calibration|chunk|all>"));
            }
            return true;
        }
        if (args.length >= 3 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !quests.canAct(target.getUniqueId())) {
                sender.sendMessage(Text.parse("<red>Игрок не найден."));
                return true;
            }
            try {
                int tower = Integer.parseInt(args[2]);
                if (tower < 1 || tower > 16) throw new NumberFormatException();
                boolean added = quests.give(target.getUniqueId(), tower);
                sender.sendMessage(added
                        ? config.message("quest-given", Map.<String, Object>of("{player}", target.getName(), "{tower}", tower))
                        : config.message("quest-given-active", Map.<String, Object>of("{tower}", tower)));
            } catch (NumberFormatException exception) {
                sender.sendMessage(Text.parse("<yellow>/towers give <player> <1-16>"));
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("reset")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !quests.canAct(target.getUniqueId())) {
                sender.sendMessage(Text.parse("<red>Игрок не найден."));
                return true;
            }
            boolean reset = game.resetActiveQuest(target);
            if (reset) {
                sender.sendMessage(config.message("quest-reset", "{player}", target.getName()));
            } else {
                sender.sendMessage(config.message("quest-reset-none", "{player}", target.getName()));
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("test") && sender instanceof Player player) {
            String action = args.length > 1 ? args[1].toLowerCase() : "help";
            switch (action) {
                case "spawn" -> {
                    Location base = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
                    displays.spawn("central-tower", base.clone().add(0, 0, 0));
                    displays.spawn("calibration-tower", base.clone().add(4, 0, 0));
                    displays.spawn("chunk-tower", base.clone().add(8, 0, 0));
                    sender.sendMessage(config.message("test-spawned"));
                }
                case "central" -> menus.openTower(player);
                case "calibration" -> game.startCalibration(player);
                case "chunk" -> game.startWaves(player);
                case "cancel" -> game.cancel(player);
                case "reset" -> game.reset(player);
                case "loot" -> {
                    if (args.length > 2) {
                        sender.sendMessage(game.giveTestLoot(player, args[2])
                                ? Text.parse("<green>Тестовый лут типа " + args[2] + " выдан в инвентарь.")
                                : Text.parse("<red>Неизвестный тип волны: " + args[2]));
                    } else {
                        sender.sendMessage(game.giveTestLoot(player)
                                ? Text.parse("<green>Тестовый лут выдан в инвентарь.")
                                : Text.parse("<yellow>Нет выбранного задания для теста лута."));
                    }
                }
                case "rift" -> {
                    if (args.length > 2 && args[2].equalsIgnoreCase("remove")) {
                        int removed = game.clearTestRifts();
                        if (removed > 0) config.playSound(player, "sounds.rift-close");
                        sender.sendMessage(removed > 0
                                ? config.message("test-rift-removed", "{count}", removed)
                                : Text.parse("<yellow>Тестовых рифтов нет."));
                    } else {
                        boolean created = game.spawnTestRift(player.getLocation().getBlock().getLocation().add(0.5, game.riftHeight(), 0.5)) != null;
                        sender.sendMessage(created
                                ? config.message("test-rift-spawned")
                                : config.message("test-rift-blocked"));
                    }
                }
                case "status" -> sender.sendMessage(Text.parse("<yellow>Состояние: <white>" + gameState(player)));
                default -> sender.sendMessage(Text.parse("<yellow>/towers test <spawn|central|calibration|chunk|cancel|rift|reset|status|loot [type-id]>"));
            }
            return true;
        }
        sender.sendMessage(Text.parse("<yellow>/towers <reload|refresh|spawn|remove|give|reset|wave|test>")); return true;
    }
    private net.kyori.adventure.text.Component message(boolean success, String successText, String failureText) {
        return success ? Text.parse("<green>" + successText) : Text.parse("<yellow>" + failureText);
    }
    private String gameState(Player player) { return quests.state(player.getUniqueId()); }
}
