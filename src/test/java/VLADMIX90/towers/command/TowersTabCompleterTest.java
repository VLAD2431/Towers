package VLADMIX90.towers.command;

import VLADMIX90.towers.game.GameManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TowersTabCompleterTest {
    @Test
    void suggestsConfiguredWaveTypesForTestLoot() {
        GameManager game = mock(GameManager.class);
        when(game.waveTypeIds()).thenReturn(List.of("ice", "fire", "forest"));
        TowersTabCompleter completer = new TowersTabCompleter(game);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("towers.admin")).thenReturn(true);

        assertEquals(List.of("fire", "forest"), completer.onTabComplete(sender, mock(Command.class), "towers",
                new String[]{"test", "loot", "f"}));
    }

    @Test
    void doesNotSuggestTypesWithoutPermission() {
        GameManager game = mock(GameManager.class);
        TowersTabCompleter completer = new TowersTabCompleter(game);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("towers.admin")).thenReturn(false);

        assertEquals(List.of(), completer.onTabComplete(sender, mock(Command.class), "towers",
                new String[]{"test", "loot", ""}));
    }

    @Test void suggestsTypesForWaveAndNotForExtraLootArgument() {
        GameManager game = mock(GameManager.class);
        when(game.waveTypeIds()).thenReturn(List.of("ice", "fire", "forest"));
        TowersTabCompleter completer = new TowersTabCompleter(game);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("towers.admin")).thenReturn(true);
        assertEquals(List.of("wave"), completer.onTabComplete(sender, mock(Command.class), "towers", new String[]{"wa"}));
        assertEquals(List.of("fire", "forest"), completer.onTabComplete(sender, mock(Command.class), "towers", new String[]{"wave", "f"}));
        assertEquals(List.of(), completer.onTabComplete(sender, mock(Command.class), "towers", new String[]{"test", "loot", "fire", ""}));
    }
}
