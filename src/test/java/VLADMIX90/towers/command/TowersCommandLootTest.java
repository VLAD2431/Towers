package VLADMIX90.towers.command;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.display.CustomDisplayManager;
import VLADMIX90.towers.game.GameManager;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.quest.QuestManager;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class TowersCommandLootTest {
    @Test
    void testLootWithTypeIdUsesExplicitTypePath() {
        GameManager game = mock(GameManager.class);
        Player player = mock(Player.class);
        when(player.hasPermission("towers.admin")).thenReturn(true);
        when(game.giveTestLoot(player, "fire")).thenReturn(true);
        TowersCommand command = command(game);

        command.onCommand(player, mock(Command.class), "towers", new String[]{"test", "loot", "fire"});

        verify(game).giveTestLoot(player, "fire");
        verify(game, never()).giveTestLoot(player);
    }

    @Test
    void testLootWithoutTypeIdKeepsSelectedQuestPath() {
        GameManager game = mock(GameManager.class);
        Player player = mock(Player.class);
        when(player.hasPermission("towers.admin")).thenReturn(true);
        when(game.giveTestLoot(player)).thenReturn(true);
        TowersCommand command = command(game);

        command.onCommand(player, mock(Command.class), "towers", new String[]{"test", "loot"});

        verify(game).giveTestLoot(player);
        verify(game, never()).giveTestLoot(eq(player), anyString());
    }

    private TowersCommand command(GameManager game) {
        QuestManager quests = mock(QuestManager.class);
        when(quests.canAct(any())).thenReturn(true);
        return new TowersCommand(mock(ConfigManager.class), mock(CustomDisplayManager.class), game,
                mock(MenuManager.class), quests);
    }
}
