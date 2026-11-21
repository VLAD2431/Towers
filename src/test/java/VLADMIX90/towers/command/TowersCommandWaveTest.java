package VLADMIX90.towers.command;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.game.GameManager;
import VLADMIX90.towers.quest.QuestManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class TowersCommandWaveTest {
    @Test void startsConfiguredStandaloneType() {
        GameManager game = mock(GameManager.class);
        Player player = mock(Player.class);
        when(player.hasPermission("towers.admin")).thenReturn(true);
        when(game.startStandaloneWaves(player, "fire")).thenReturn(true);
        TowersCommand command = command(game);
        command.onCommand(player, mock(Command.class), "towers", new String[]{"wave", "fire"});
        verify(game).startStandaloneWaves(player, "fire");
    }

    @Test void rejectsMissingTypeConsoleAndMissingPermission() {
        GameManager game = mock(GameManager.class);
        TowersCommand command = command(game);
        Player player = mock(Player.class);
        when(player.hasPermission("towers.admin")).thenReturn(true);
        command.onCommand(player, mock(Command.class), "towers", new String[]{"wave"});
        CommandSender console = mock(CommandSender.class);
        when(console.hasPermission("towers.admin")).thenReturn(true);
        command.onCommand(console, mock(Command.class), "towers", new String[]{"wave", "fire"});
        when(player.hasPermission("towers.admin")).thenReturn(false);
        command.onCommand(player, mock(Command.class), "towers", new String[]{"wave", "fire"});
        verifyNoInteractions(game);
    }
    private TowersCommand command(GameManager game) {
        QuestManager quests = mock(QuestManager.class);
        when(quests.canAct(any())).thenReturn(true);
        return new TowersCommand(mock(ConfigManager.class), null, game, null, quests);
    }
}
