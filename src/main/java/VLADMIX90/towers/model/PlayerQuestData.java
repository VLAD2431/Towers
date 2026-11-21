package VLADMIX90.towers.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerQuestData {
    private final UUID playerId;
    private final List<Quest> quests = new ArrayList<>();
    private UUID selectedQuest;
    private boolean calibrated;
    private int remainingWaves;
    private int calibrationStage;
    private Map<Integer, Boolean> stageSlots = new LinkedHashMap<>();
    private Set<Integer> stageClicked = new LinkedHashSet<>();
    private long calibrationAttempt;
    private UUID computerWorld;
    private int computerChunkX;
    private int computerChunkZ;
    /** Number of the tower assigned to the computer that authorized this run. */
    private int computerTowerId;
    private UUID waveWorld;
    private int waveChunkX;
    private int waveChunkZ;
    private Map<String, String> guaranteedDrops = new LinkedHashMap<>();
    /** True when the first wave of the run has been triggered; before that the rift sleeps. */
    private boolean waveStarted;
    /** Schedule instant this player's quests were last rotated. */
    private long questRefreshAt;
    private long questGeneration;

    public PlayerQuestData(UUID playerId) { this.playerId = playerId; }
    public UUID playerId() { return playerId; }
    public List<Quest> quests() { return quests; }
    public boolean hasActiveQuest() {
        return quests.stream().anyMatch(quest -> quest.state() == QuestState.CALIBRATING
                || quest.state() == QuestState.CALIBRATED
                || quest.state() == QuestState.WAVES_ACTIVE);
    }
    public UUID selectedQuest() { return selectedQuest; }
    public void select(UUID id) { selectedQuest = id; }
    public boolean calibrated() { return calibrated; }
    public void calibrated(boolean value) { calibrated = value; }
    public int remainingWaves() { return remainingWaves; }
    public void remainingWaves(int value) { remainingWaves = value; }
    public int calibrationStage() { return calibrationStage; }
    public void calibrationStage(int value) { calibrationStage = value; }
    public Map<Integer, Boolean> stageSlots() { return stageSlots; }
    public void stageSlots(Map<Integer, Boolean> value) { stageSlots = new LinkedHashMap<>(value); }
    public Set<Integer> stageClicked() { return stageClicked; }
    public void stageClicked(Set<Integer> value) { stageClicked = new LinkedHashSet<>(value); }
    public long calibrationAttempt() { return calibrationAttempt; }
    public void calibrationAttempt(long value) { calibrationAttempt = value; }
    public UUID computerWorld() { return computerWorld; }
    public int computerChunkX() { return computerChunkX; }
    public int computerChunkZ() { return computerChunkZ; }
    public int computerTowerId() { return computerTowerId; }
    public void computerChunk(UUID world, int x, int z, int towerId) {
        computerWorld = world; computerChunkX = x; computerChunkZ = z; computerTowerId = towerId;
    }
    /** Legacy/test helper for callers without a bound computer. */
    public void computerChunk(UUID world, int x, int z) { computerChunk(world, x, z, 0); }
    public void clearComputerChunk() { computerWorld = null; computerChunkX = 0; computerChunkZ = 0; computerTowerId = 0; }
    public UUID waveWorld() { return waveWorld; }
    public int waveChunkX() { return waveChunkX; }
    public int waveChunkZ() { return waveChunkZ; }
    public void waveChunk(UUID world, int x, int z) { waveWorld = world; waveChunkX = x; waveChunkZ = z; }
    public void clearWaveChunk() { waveWorld = null; waveChunkX = 0; waveChunkZ = 0; }
    public Map<String, String> guaranteedDrops() { return guaranteedDrops; }
    public void guaranteedDrops(Map<String, String> value) { guaranteedDrops = new LinkedHashMap<>(value); }
    public boolean waveStarted() { return waveStarted; }
    public void waveStarted(boolean value) { waveStarted = value; }
    public long questRefreshAt() { return questRefreshAt; }
    public void questRefreshAt(long value) { questRefreshAt = value; }
    public long questGeneration() { return questGeneration; }
    public void questGeneration(long value) { questGeneration = value; }
}
