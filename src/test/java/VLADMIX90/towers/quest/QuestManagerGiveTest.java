package VLADMIX90.towers.quest;

import VLADMIX90.towers.model.Quest;
import VLADMIX90.towers.model.QuestState;
import VLADMIX90.towers.wave.WaveConfig;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the task of the administrator: {@code /towers give <player> <tower>}.
 *
 * <p>A finished task does not block a new one — the new task replaces the finished one. A task
 * that is still running is never overwritten.
 */
class QuestManagerGiveTest {

    private final UUID playerId = UUID.randomUUID();
    private QuestManager quests;
    private VLADMIX90.towers.storage.SqliteDatabase database;

    @org.junit.jupiter.api.AfterEach
    void closeDatabase() { database.closeAsync().join(); }

    @BeforeEach
    void setUp() throws IOException {
        Path folder = Files.createTempDirectory("towers-give");
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(plugin.getDataFolder()).thenReturn(folder.toFile());
        Mockito.when(plugin.getLogger()).thenReturn(Mockito.mock(Logger.class));
        WaveConfig waves = Mockito.mock(WaveConfig.class);
        Mockito.when(waves.rollType(Mockito.any())).thenReturn("type");
        database = new VLADMIX90.towers.storage.SqliteDatabase(folder.resolve("database.db"));
        database.initialize().join();
        quests = new QuestManager(plugin, waves, new PlayerDataStorage(database), Runnable::run);
        quests.loadPlayer(playerId).join();
    }

    @Test
    void aTaskIsGivenForAFreeTower() {
        assertTrue(quests.give(playerId, 3));
        assertEquals(QuestState.AVAILABLE, quests.tower(playerId, 3).orElseThrow().state());
    }

    @Test
    void aFinishedTaskDoesNotBlockANewOne() {
        quests.data(playerId).quests().add(new Quest(UUID.randomUUID(), "Вышка 3",
                "Задача", QuestState.COMPLETED, 3, "type"));

        assertTrue(quests.give(playerId, 3), "a finished task must not stop a new one");
        assertEquals(1, quests.data(playerId).quests().stream().filter(q -> q.towerId() == 3).count(),
                "the tower holds only the new task");
        assertEquals(QuestState.AVAILABLE, quests.tower(playerId, 3).orElseThrow().state());
    }

    @Test
    void aRunningTaskIsNotOverwritten() {
        quests.data(playerId).quests().add(new Quest(UUID.randomUUID(), "Вышка 3",
                "Задача", QuestState.CALIBRATING, 3, "type"));

        assertFalse(quests.give(playerId, 3));
        assertEquals(QuestState.CALIBRATING, quests.tower(playerId, 3).orElseThrow().state());
    }

    @Test
    void theGuaranteeOfAReplacedTaskIsDropped() {
        Quest finished = new Quest(UUID.randomUUID(), "Вышка 3", "Задача", QuestState.COMPLETED, 3, "type");
        quests.data(playerId).quests().add(finished);
        quests.data(playerId).guaranteedDrops().put("loot-id", finished.id().toString());

        quests.give(playerId, 3);

        assertFalse(quests.data(playerId).guaranteedDrops().containsValue(finished.id().toString()),
                "the guarantee of the replaced task must not stay behind");
    }
}
