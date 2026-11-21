package VLADMIX90.towers.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Bukkit;
import VLADMIX90.towers.game.GameManager;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class TowersTabCompleter implements TabCompleter {
    private static final List<String> TEST_SUBCOMMANDS = List.of("spawn", "central", "calibration", "chunk", "cancel", "rift", "reset", "status", "loot");
    private static final List<String> TOWERS = List.of("central", "computer", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16");
    private static final List<String> REMOVE_TARGETS = List.of("central", "calibration", "chunk", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "all");
    private final GameManager game;

    public TowersTabCompleter(GameManager game) {
        this.game = game;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("towers.admin")) return List.of();
        if (args.length == 1) return matching(List.of("reload", "refresh", "update", "spawn", "remove", "give", "reset", "wave", "test"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("wave")) return matching(game.waveTypeIds(), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("spawn")) return matching(TOWERS, args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) return matching(REMOVE_TARGETS, args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) return matching(Bukkit.getOnlinePlayers().stream().map(player -> player.getName()).toList(), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("reset")) return matching(Bukkit.getOnlinePlayers().stream().map(player -> player.getName()).toList(), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return matching(TOWERS.stream().filter(tower -> tower.matches("\\d+")).toList(), args[2]);
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) return matching(TEST_SUBCOMMANDS, args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("test") && args[1].equalsIgnoreCase("loot")) {
            return matching(game.waveTypeIds(), args[2]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("test") && args[1].equalsIgnoreCase("rift")) return matching(List.of("remove"), args[2]);
        return List.of();
    }
    private List<String> matching(List<String> suggestions, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return suggestions.stream().filter(suggestion -> suggestion.toLowerCase(Locale.ROOT).startsWith(prefix)).collect(Collectors.toList());
    }
}
