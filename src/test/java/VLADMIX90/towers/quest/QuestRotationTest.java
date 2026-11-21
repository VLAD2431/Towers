package VLADMIX90.towers.quest;

import VLADMIX90.towers.model.PlayerQuestData;
import VLADMIX90.towers.model.QuestState;
import VLADMIX90.towers.wave.WaveConfig;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class QuestRotationTest {

    private final java.util.List<VLADMIX90.towers.storage.SqliteDatabase> databases = new java.util.ArrayList<>();
    @org.junit.jupiter.api.AfterEach
    void closeDatabases() { databases.forEach(db -> db.closeAsync().join()); }

    private QuestManager manager(JavaPlugin plugin, UUID player) {
        var database = new VLADMIX90.towers.storage.SqliteDatabase(plugin.getDataFolder().toPath().resolve("database.db"));
        databases.add(database);
        database.initialize().join();
        var storage = new PlayerDataStorage(database);
        storage.acceptSchedule(storage.loadSchedule().join());
        var manager = new QuestManager(plugin, waves(), storage, Runnable::run);
        if (player != null) manager.loadPlayer(player).join();
        return manager;
    }

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    @Test
    void nextRefreshUsesConfiguredMoscowTimeAndIsInTheFuture() throws IOException {
        AtomicReference<String> refreshTime = new AtomicReference<>("03:17");
        JavaPlugin plugin = plugin(Files.createTempDirectory("towers-moscow"), refreshTime, 168L, 2);
        QuestManager quests = manager(plugin, null);

        long refreshAt = quests.refreshAt();
        ZonedDateTime scheduled = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(refreshAt), MOSCOW);

        assertEquals(LocalTime.of(3, 17), scheduled.toLocalTime());
        assertTrue(refreshAt > System.currentTimeMillis());
    }

    @Test
    void dueScheduleRotatesAnOfflinePlayerWhenTheyJoin() throws IOException {
        Path folder = Files.createTempDirectory("towers-offline");
        AtomicReference<String> refreshTime = new AtomicReference<>("00:00");
        JavaPlugin plugin = plugin(folder, refreshTime, 168L, 2);
        UUID player = UUID.randomUUID();

        QuestManager beforeRollover = manager(plugin, player);
        beforeRollover.refreshWeekly(player, 2);
        long oldGeneration = beforeRollover.data(player).questGeneration();

        databases.get(0).flush().join();
        databases.get(0).transaction(connection -> {
            try (var statement = connection.prepareStatement("UPDATE global_schedule SET refresh_at=? WHERE id=1")) {
                statement.setLong(1, System.currentTimeMillis() - 1); statement.executeUpdate();
            }
            return null;
        }).join();

        QuestManager afterRollover = manager(plugin, player);
        try (var bukkit = Mockito.mockStatic(org.bukkit.Bukkit.class)) {
            bukkit.when(org.bukkit.Bukkit::getOnlinePlayers).thenReturn(java.util.List.of());
            afterRollover.refreshOnJoin(player);
        }

        PlayerQuestData data = afterRollover.data(player);
        assertEquals(oldGeneration + 1, data.questGeneration());
        assertEquals(2, data.quests().size());
        assertTrue(data.questRefreshAt() > System.currentTimeMillis());
    }

    @Test
    void reloadChangesFutureScheduleWithoutRotatingQuestGeneration() throws IOException {
        AtomicReference<String> refreshTime = new AtomicReference<>("00:00");
        JavaPlugin plugin = plugin(Files.createTempDirectory("towers-reload"), refreshTime, 168L, 2);
        UUID player = UUID.randomUUID();
        QuestManager quests = manager(plugin, player);
        quests.refreshWeekly(player, 2);
        long generation = quests.data(player).questGeneration();
        long oldRefreshAt = quests.refreshAt();

        refreshTime.set("12:00");
        when(plugin.getConfig().getLong("weekly-refresh-hours", 168L)).thenReturn(24L);
        quests.rescheduleOnline();

        assertEquals(generation, quests.data(player).questGeneration());
        assertNotEquals(oldRefreshAt, quests.refreshAt());
    }

    @Test
    void activeQuestAndWaveProgressSurviveUnloadAndRestart() throws IOException {
        Path folder = Files.createTempDirectory("towers-restart");
        AtomicReference<String> refreshTime = new AtomicReference<>("00:00");
        JavaPlugin plugin = plugin(folder, refreshTime, 168L, 2);
        UUID player = UUID.randomUUID();

        QuestManager first = manager(plugin, player);
        first.refreshWeekly(player, 2);
        int tower = first.data(player).quests().get(0).towerId();
        assertTrue(first.selectTower(player, tower));
        first.startWaves(player, 3);
        first.unload(player);

        databases.get(0).flush().join();
        QuestManager restarted = manager(plugin, player);
        restarted.refreshOnJoin(player);

        assertEquals(QuestState.WAVES_ACTIVE, restarted.selected(player).orElseThrow().state());
        assertEquals(3, restarted.data(player).remainingWaves());
    }

    private static JavaPlugin plugin(Path folder, AtomicReference<String> refreshTime,
                                     long refreshHours, int questCount) {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        when(plugin.getLogger()).thenReturn(Mockito.mock(Logger.class));
        when(plugin.getConfig()).thenReturn(config);
        when(config.getString("weekly-refresh-time", "16:00")).thenAnswer(invocation -> refreshTime.get());
        when(config.getLong("weekly-refresh-hours", 168L)).thenReturn(refreshHours);
        when(config.getInt("weekly-quest-count", 3)).thenReturn(questCount);
        return plugin;
    }

    private static WaveConfig waves() {
        WaveConfig waves = Mockito.mock(WaveConfig.class);
        when(waves.rollType(Mockito.any())).thenReturn("type");
        when(waves.assignGuaranteed(Mockito.anyList(), Mockito.any())).thenReturn(Map.of());
        return waves;
    }
}
