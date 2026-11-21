package VLADMIX90.towers.storage;

import VLADMIX90.towers.model.PlayerQuestData;
import VLADMIX90.towers.model.Quest;
import java.util.*;

/** Capture on the game thread; safe to pass to the database worker. Quest is itself immutable. */
public record PlayerSnapshot(UUID playerId, List<Quest> quests, UUID selectedQuest, boolean calibrated,
                             int remainingWaves, int calibrationStage, Map<Integer, Boolean> stageSlots,
                             Set<Integer> stageClicked, long calibrationAttempt, UUID computerWorld,
                             int computerChunkX, int computerChunkZ, int computerTowerId, UUID waveWorld,
                             int waveChunkX, int waveChunkZ, Map<String, String> guaranteedDrops,
                             boolean waveStarted, long questRefreshAt, long questGeneration) {
    public PlayerSnapshot {
        Objects.requireNonNull(playerId);
        quests = List.copyOf(quests);
        stageSlots = Collections.unmodifiableMap(new LinkedHashMap<>(stageSlots));
        stageClicked = Collections.unmodifiableSet(new LinkedHashSet<>(stageClicked));
        guaranteedDrops = Collections.unmodifiableMap(new LinkedHashMap<>(guaranteedDrops));
    }

    public static PlayerSnapshot capture(PlayerQuestData data) {
        return new PlayerSnapshot(data.playerId(), data.quests(), data.selectedQuest(), data.calibrated(),
                data.remainingWaves(), data.calibrationStage(), data.stageSlots(), data.stageClicked(),
                data.calibrationAttempt(), data.computerWorld(), data.computerChunkX(), data.computerChunkZ(),
                data.computerTowerId(), data.waveWorld(), data.waveChunkX(), data.waveChunkZ(),
                data.guaranteedDrops(), data.waveStarted(), data.questRefreshAt(), data.questGeneration());
    }

    public PlayerQuestData toData() {
        PlayerQuestData data = new PlayerQuestData(playerId);
        data.quests().addAll(quests); data.select(selectedQuest); data.calibrated(calibrated);
        data.remainingWaves(remainingWaves); data.calibrationStage(calibrationStage);
        data.stageSlots(stageSlots); data.stageClicked(stageClicked); data.calibrationAttempt(calibrationAttempt);
        data.computerChunk(computerWorld, computerChunkX, computerChunkZ, computerTowerId);
        data.waveChunk(waveWorld, waveChunkX, waveChunkZ); data.guaranteedDrops(guaranteedDrops);
        data.waveStarted(waveStarted); data.questRefreshAt(questRefreshAt); data.questGeneration(questGeneration);
        return data;
    }
}
