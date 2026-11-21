package VLADMIX90.towers.storage;

import VLADMIX90.towers.quest.PlayerDataStorage;
import VLADMIX90.towers.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerDataStorageTest {
    @TempDir Path directory;

    @Test void roundTripsAllFieldsAndOrderedQuestsThenDeletesWithoutResurrection() {
        SqliteDatabase database = new SqliteDatabase(directory.resolve("database.db"));
        database.initialize().join();
        try {
            PlayerDataStorage storage = new PlayerDataStorage(database);
            PlayerQuestData data = new PlayerQuestData(UUID.randomUUID());
            UUID first = UUID.randomUUID(), second = UUID.randomUUID();
            data.quests().add(new Quest(first, "First", "description", QuestState.CALIBRATED, 2, null));
            data.quests().add(new Quest(second, "Second", "", QuestState.AVAILABLE, 8, "fire"));
            data.select(first); data.calibrated(true); data.remainingWaves(3); data.calibrationStage(4);
            data.stageSlots(Map.of(1, false, 5, true)); data.stageClicked(Set.of(5)); data.calibrationAttempt(65);
            data.computerChunk(UUID.randomUUID(), -7, 8, 2); data.waveChunk(UUID.randomUUID(), 9, -10);
            data.guaranteedDrops(Map.of("drop", first.toString())); data.waveStarted(true);
            data.questRefreshAt(1670000000000L); data.questGeneration(8);
            PlayerSnapshot snapshot = PlayerSnapshot.capture(data);
            storage.save(snapshot).join();
            assertEquals(snapshot, storage.load(data.playerId()).join().orElseThrow());
            data.remainingWaves(2);
            storage.save(PlayerSnapshot.capture(data));
            data.remainingWaves(1);
            storage.save(PlayerSnapshot.capture(data));
            assertEquals(1, storage.load(data.playerId()).join().orElseThrow().remainingWaves());
            storage.save(snapshot);
            storage.delete(data.playerId()).join();
            assertTrue(storage.load(data.playerId()).join().isEmpty());
            PlayerSnapshot empty = PlayerSnapshot.capture(new PlayerQuestData(data.playerId()));
            storage.save(empty).join();
            assertEquals(empty, storage.load(data.playerId()).join().orElseThrow());
        } finally { database.closeAsync().join(); }
    }

    @Test void restoresGlobalScheduleAcrossConnections() {
        Path file = directory.resolve("database.db");
        SqliteDatabase database = new SqliteDatabase(file);
        database.initialize().join();
        new PlayerDataStorage(database).saveSchedule(12, 123456789L, "16:00/168").join();
        database.closeAsync().join();
        SqliteDatabase reopened = new SqliteDatabase(file);
        reopened.initialize().join();
        try {
            var schedule = new PlayerDataStorage(reopened).loadSchedule().join();
            assertEquals(12, schedule.generation()); assertEquals(123456789L, schedule.refreshAt());
            assertEquals("16:00/168", schedule.schedule());
        } finally { reopened.closeAsync().join(); }
    }
}
