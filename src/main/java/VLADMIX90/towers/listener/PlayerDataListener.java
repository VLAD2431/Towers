package VLADMIX90.towers.listener;

import VLADMIX90.towers.game.GameManager;
import VLADMIX90.towers.quest.QuestManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import VLADMIX90.towers.util.Text;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.Function;
import java.util.function.Consumer;

public final class PlayerDataListener implements Listener {
    private final GameManager game;
    private final QuestManager quests;
    private final Function<UUID, Player> online;
    private final Consumer<Throwable> failures;
    public PlayerDataListener(GameManager game, QuestManager quests) {
        this(game, quests, Bukkit::getPlayer, failure -> Bukkit.getLogger().log(
                java.util.logging.Level.SEVERE, "Failed to load Towers player progress", failure));
    }
    public PlayerDataListener(GameManager game, QuestManager quests, Function<UUID, Player> online, Consumer<Throwable> failures) {
        this.game = game; this.quests = quests; this.online = online; this.failures = failures;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer().getUniqueId());
    }

    public void load(UUID id) {
        quests.loadPlayer(id).whenComplete((ignored, failure) -> {
            if (failure instanceof CancellationException) return;
            Player player = online.apply(id);
            if (failure != null) {
                failures.accept(failure);
                if (player != null) player.sendMessage(Text.parse("<red>Не удалось загрузить задания. Действия заблокированы; обратитесь к администратору."));
                return;
            }
            if (player == null || !quests.isLoaded(id)) return;
            quests.refreshOnJoin(id);
            game.resumeCalibration(player);
            game.resumeWaves(player);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (quests.isLoaded(event.getPlayer().getUniqueId())) game.quit(event.getPlayer());
        quests.unload(event.getPlayer().getUniqueId());
    }
}
