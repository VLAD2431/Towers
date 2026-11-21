package VLADMIX90.towers.game;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.model.PlayerQuestData;
import VLADMIX90.towers.quest.QuestManager;
import VLADMIX90.towers.wave.WaveConfig;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Checks the repeating battle sound and the rift-close sound. */
class GameManagerBattleSoundTest {

    private JavaPlugin plugin;
    private Server server;
    private BukkitScheduler scheduler;
    private ConfigManager config;
    private QuestManager quests;
    private PlayerQuestData data;
    private Player player;
    private UUID playerId;
    private BukkitTask task;
    private Runnable battleTicker;
    private GameManager game;

    @BeforeEach
    void setUp() throws IOException {
        playerId = UUID.randomUUID();
        plugin = mock(JavaPlugin.class);
        server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        config = mock(ConfigManager.class);
        player = mock(Player.class);
        when(plugin.getDataFolder()).thenReturn(Files.createTempDirectory("towers-battle-sound").toFile());
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getPlayer(playerId)).thenReturn(player);
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        quests = mock(QuestManager.class);
        data = mock(PlayerQuestData.class);
        when(quests.data(playerId)).thenReturn(data);
        game = new GameManager(plugin, config, quests,
                mock(MenuManager.class), mock(WaveConfig.class));
        when(config.integer("sounds.battle-interval-seconds", 8)).thenReturn(1);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L)))
                .thenAnswer(call -> { battleTicker = call.getArgument(1); return task; });
    }

    @Test
    void battleSoundStartsOnlyOnceForAnActiveRun() {
        game.startBattleSound(player);

        verify(config).playSound(player, "sounds.battle");
    }

    @Test
    void theBattleSoundStartsOnlyOnce() {
        game.startBattleSound(player);
        game.startBattleSound(player);

        verify(config, times(1)).playSound(player, "sounds.battle");
    }

    @Test
    void battleSoundRepeatsAfterItsConfiguredInterval() {
        game.startBattleSound(player);

        battleTicker.run();

        verify(config).stopSound(player, "sounds.battle");
        verify(config, times(2)).playSound(player, "sounds.battle");
    }

    @Test
    void stoppingTheBattleSoundStopsThePlayingTrack() {
        game.startBattleSound(player);
        game.stopBattleSound(playerId);

        verify(config).stopSound(player, "sounds.battle");
    }

    @Test
    void quittingStopsTheBattleSound() {
        game.startBattleSound(player);
        game.quit(player);

        verify(config).stopSound(player, "sounds.battle");
    }

    @Test
    void finishingWavesStopsTheBattleSound() {
        game.startBattleSound(player);
        game.finishWaves(playerId);

        verify(config).stopSound(player, "sounds.battle");
    }

    @Test
    void resettingAQuestStopsTheBattleSound() {
        game.startBattleSound(player);
        game.resetActiveQuest(player);

        verify(config).stopSound(player, "sounds.battle");
    }

    @Test
    void cleanupStopsTheBattleSound() {
        game.startBattleSound(player);
        game.cleanup();

        verify(config).stopSound(player, "sounds.battle");
    }

    @Test
    void theRiftCloseSoundIsPlayedToTheOwner() {
        game.playRiftCloseSound(playerId);

        verify(config).playSound(player, "sounds.rift-close");
    }
}
