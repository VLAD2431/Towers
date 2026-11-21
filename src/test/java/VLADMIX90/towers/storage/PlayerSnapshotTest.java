package VLADMIX90.towers.storage;

import VLADMIX90.towers.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlayerSnapshotTest {
    @Test void capturesEveryFieldWithoutSharingMutableCollections() {
        PlayerQuestData data = new PlayerQuestData(UUID.randomUUID());
        UUID quest = UUID.randomUUID(), computer = UUID.randomUUID(), wave = UUID.randomUUID();
        data.quests().add(new Quest(quest, "Башня", "Описание", QuestState.WAVES_ACTIVE, 7, "fire"));
        data.select(quest); data.calibrated(true); data.remainingWaves(4); data.calibrationStage(2);
        data.stageSlots(Map.of(3, true, 8, false)); data.stageClicked(Set.of(3)); data.calibrationAttempt(91);
        data.computerChunk(computer, -12, 19, 7); data.waveChunk(wave, 22, -9);
        data.guaranteedDrops(Map.of("loot", quest.toString())); data.waveStarted(true);
        data.questRefreshAt(123456L); data.questGeneration(12L);
        PlayerSnapshot snapshot = PlayerSnapshot.capture(data);
        data.quests().clear(); data.stageSlots().clear(); data.stageClicked().clear(); data.guaranteedDrops().clear();
        PlayerQuestData restored = snapshot.toData();
        assertEquals(quest, restored.quests().getFirst().id());
        assertEquals(quest, restored.selectedQuest()); assertTrue(restored.calibrated());
        assertEquals(4, restored.remainingWaves()); assertEquals(2, restored.calibrationStage());
        assertEquals(Map.of(3, true, 8, false), restored.stageSlots()); assertEquals(Set.of(3), restored.stageClicked());
        assertEquals(91, restored.calibrationAttempt()); assertEquals(computer, restored.computerWorld());
        assertEquals(-12, restored.computerChunkX()); assertEquals(19, restored.computerChunkZ());
        assertEquals(7, restored.computerTowerId()); assertEquals(wave, restored.waveWorld());
        assertEquals(22, restored.waveChunkX()); assertEquals(-9, restored.waveChunkZ());
        assertEquals(Map.of("loot", quest.toString()), restored.guaranteedDrops()); assertTrue(restored.waveStarted());
        assertEquals(123456L, restored.questRefreshAt()); assertEquals(12L, restored.questGeneration());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.quests().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.stageSlots().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.stageClicked().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.guaranteedDrops().clear());
        restored.quests().clear();
        assertEquals(1, snapshot.quests().size());
    }
}
