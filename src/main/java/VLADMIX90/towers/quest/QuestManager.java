package VLADMIX90.towers.quest;

import VLADMIX90.towers.model.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Consumer;
import VLADMIX90.towers.wave.WaveConfig;
import VLADMIX90.towers.storage.PlayerSessionCache;
import VLADMIX90.towers.storage.PlayerSnapshot;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class QuestManager {
    private final Random random = new Random();
    private final PlayerDataStorage storage;
    private final JavaPlugin plugin;
    private final WaveConfig waves;
    private final PlayerSessionCache sessions;
    private Consumer<UUID> runtimeCleanup = ignored -> { };
    public QuestManager(JavaPlugin plugin, WaveConfig waves, PlayerDataStorage storage, Executor main) {
        this.plugin = plugin; this.waves = waves; this.storage = storage;
        sessions = new PlayerSessionCache(storage::load, storage::save, main);
    }
    public CompletableFuture<Void> loadPlayer(UUID player) {
        return sessions.load(player);
    }
    public boolean isLoaded(UUID player) { return sessions.isLoaded(player); }
    public boolean canAct(UUID player) { return sessions.isLoaded(player); }
    public void runtimeCleanup(Consumer<UUID> cleanup) { runtimeCleanup = Objects.requireNonNull(cleanup); }
    public PlayerQuestData data(UUID player) {
        return sessions.data(player);
    }
    public void generateWeekly(UUID player, int count) {
        PlayerQuestData data = data(player);
        if (!data.quests().isEmpty()) return;
        List<Integer> towers = new ArrayList<>();
        for (int i = 1; i <= 16; i++) towers.add(i);
        Collections.shuffle(towers, random);
        for (int i = 0; i < count && i < towers.size(); i++) {
            int tower = towers.get(i);
            data.quests().add(new Quest(UUID.randomUUID(), "Вышка " + tower,
                    "Зачистить выбранный сектор", QuestState.AVAILABLE, tower, waves.rollType(random)));
        }
        data.guaranteedDrops(waves.assignGuaranteed(data.quests().stream().map(Quest::id).toList(), random));
        persist(data);
    }

    public void refreshWeekly(UUID player, int count) {
        PlayerQuestData data = data(player);
        ensureSchedule();
        if (isDue()) {
            advanceSchedule();
            refreshOnline(count);
        }
        migratePlayer(data);
        if (data.questGeneration() != storage.globalGeneration()) {
            rotate(player, count, data);
            return;
        }
        if (data.quests().isEmpty() && !data.hasActiveQuest()) generateWeekly(player, count);
    }

    public void refreshOnline(int count) {
        ensureSchedule();
        if (isDue()) advanceSchedule();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isLoaded(player.getUniqueId())) continue;
            PlayerQuestData data = data(player.getUniqueId());
            migratePlayer(data);
            if (data.questGeneration() != storage.globalGeneration()) rotate(player.getUniqueId(), count, data);
        }
    }

    public void refreshOnline() { refreshOnline(plugin.getConfig().getInt("weekly-quest-count", 3)); }

    /** Forces a new quest generation for every online player. Offline players rotate on join. */
    public void forceRefreshOnline(int count) {
        ensureSchedule();
        storage.globalSchedule(storage.globalGeneration() + 1, computeNext(), scheduleKey());
        refreshOnline(count);
    }

    /** Called on reload: changes the schedule only when its configuration actually changed. */
    public void rescheduleOnline() { ensureSchedule(); }

    /** Rotates/initializes a player as soon as they join, including players who were offline at rollover. */
    public void refreshOnJoin(UUID player) { refreshWeekly(player, plugin.getConfig().getInt("weekly-quest-count", 3)); }

    /** The single refresh moment shared by every player. */
    public long refreshAt() { ensureSchedule(); return storage.globalRefreshAt(); }

    private boolean isDue() {
        ensureSchedule();
        long refreshAt = storage.globalRefreshAt();
        return refreshAt > 0 && System.currentTimeMillis() >= refreshAt;
    }

    private void ensureSchedule() {
        String schedule = scheduleKey();
        if (storage.globalGeneration() <= 0) {
            long legacy = storage.globalRefreshAt();
            long generation = legacy > 0 && legacy <= System.currentTimeMillis() ? 2 : 1;
            storage.globalSchedule(generation, computeNext(), schedule);
        } else if (!schedule.equals(storage.globalSchedule())) {
            long generation = storage.globalGeneration();
            if (storage.globalRefreshAt() > 0 && storage.globalRefreshAt() <= System.currentTimeMillis()) generation++;
            storage.globalSchedule(generation, computeNext(), schedule);
        }
    }

    private void advanceSchedule() {
        storage.globalSchedule(storage.globalGeneration() + 1, computeNext(), scheduleKey());
    }

    private void migratePlayer(PlayerQuestData data) {
        if (data.questGeneration() == 0) {
            data.questGeneration(1);
            data.questRefreshAt(storage.globalRefreshAt());
            save(data.playerId());
        }
    }

    private String scheduleKey() {
        return plugin.getConfig().getString("weekly-refresh-time", "16:00") + "/"
                + Math.max(1, plugin.getConfig().getLong("weekly-refresh-hours", 168));
    }


    private void rotate(UUID player, int count, PlayerQuestData data) {
        if (data.hasActiveQuest()) runtimeCleanup.accept(player);
        data.quests().clear();
        data.select(null);
        data.calibrated(false);
        data.remainingWaves(0);
        data.waveStarted(false);
        data.calibrationAttempt(data.calibrationAttempt() + 1);
        data.calibrationStage(0);
        data.stageSlots(Collections.emptyMap());
        data.stageClicked(Collections.emptySet());
        data.clearComputerChunk();
        data.clearWaveChunk();
        data.guaranteedDrops(Map.of());
        data.questGeneration(storage.globalGeneration());
        data.questRefreshAt(storage.globalRefreshAt());
        generateWeekly(player, count);
        save(player);
    }

    private long computeNext() {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        ZonedDateTime now = ZonedDateTime.now(zone);
        String configured = plugin.getConfig().getString("weekly-refresh-time", "16:00");
        LocalTime refreshTime;
        try { refreshTime = LocalTime.parse(configured); }
        catch (RuntimeException exception) {
            plugin.getLogger().warning("Некорректное weekly-refresh-time: " + configured + "; используется 16:00 по московскому времени");
            refreshTime = LocalTime.of(16, 0);
        }
        long refreshHours = Math.max(1, plugin.getConfig().getLong("weekly-refresh-hours", 168));
        ZonedDateTime next = now.plusHours(refreshHours).with(refreshTime);
        while (!next.isAfter(now)) next = next.plusDays(1);
        return next.toInstant().toEpochMilli();
    }

    public Optional<Quest> tower(UUID player, int towerId) {
        return data(player).quests().stream().filter(q -> q.towerId() == towerId).findFirst();
    }

    public boolean selectTower(UUID player, int towerId) {
        PlayerQuestData data = data(player);
        if (data.hasActiveQuest()) return false;
        Optional<Quest> quest = tower(player, towerId);
        if (quest.isEmpty() || quest.get().state() != QuestState.AVAILABLE) return false;
        return select(player, data.quests().indexOf(quest.get())).isPresent();
    }
    /**
     * Gives the player a task for the given tower. A task that is already finished does not block
     * a new one — the new task replaces the finished one. A task that is still running is never
     * overwritten.
     */
    public boolean give(UUID player, int towerId) {
        PlayerQuestData data = data(player);
        for (Quest quest : data.quests()) {
            if (quest.towerId() == towerId && isActive(quest)) return false;
        }
        List<Quest> replaced = data.quests().stream().filter(quest -> quest.towerId() == towerId).toList();
        data.quests().removeAll(replaced);
        for (Quest quest : replaced) {
            // the guarantee of a replaced task would still point at a task that does not exist
            data.guaranteedDrops().values().removeIf(quest.id().toString()::equals);
            if (quest.id().equals(data.selectedQuest())) {
                data.select(null);
                data.calibrated(false);
                data.remainingWaves(0);
            }
        }
        data.quests().add(new Quest(UUID.randomUUID(), "Вышка " + towerId,
                "Задача от администратора", QuestState.AVAILABLE, towerId, waves.rollType(random)));
        persist(data);
        return true;
    }
    public boolean hasActiveQuest(UUID player) { return data(player).hasActiveQuest(); }
    public Optional<Quest> select(UUID player, int index) {
        PlayerQuestData data = data(player);
        if (index < 0 || index >= data.quests().size()) return Optional.empty();
        Quest quest = data.quests().get(index);
        if (quest.state() != QuestState.AVAILABLE) return Optional.empty();
        quest = quest.withState(QuestState.CALIBRATING);
        data.quests().set(index, quest);
        data.select(quest.id());
        data.calibrated(false);
        data.remainingWaves(0);
        data.clearComputerChunk();
        data.clearWaveChunk();
        save(player);
        return Optional.of(quest);
    }
    public void completeCalibration(UUID player) {
        PlayerQuestData data = data(player);
        data.calibrated(true);
        updateSelected(data, QuestState.CALIBRATED);
        save(player);
    }
    public void startWaves(UUID player, int waves) {
        PlayerQuestData data = data(player);
        if (data.remainingWaves() <= 0) data.remainingWaves(waves);
        updateSelected(data, QuestState.WAVES_ACTIVE);
        save(player);
    }
    public boolean waveDefeated(UUID player) {
        PlayerQuestData data = data(player);
        if (data.remainingWaves() <= 0) return false;
        data.remainingWaves(data.remainingWaves() - 1);
        if (data.remainingWaves() == 0) updateSelected(data, QuestState.COMPLETED);
        save(player);
        return true;
    }
    /**
     * Aborts a running wave without deleting the quest: the wave chunk is dropped and the quest
     * returns to CALIBRATED so it can be started again. Returns false when no wave was active.
     */
    public boolean cancelRun(UUID player) {
        PlayerQuestData data = data(player);
        if (selected(player).filter(quest -> quest.state() == QuestState.WAVES_ACTIVE).isEmpty()) return false;
        data.remainingWaves(0);
        data.clearWaveChunk();
        updateSelected(data, QuestState.CALIBRATED);
        save(player);
        return true;
    }
    /**
     * Returns the player's currently active quest to AVAILABLE and clears the progress tied to it
     * (calibration, waves, computer and wave chunk), leaving the other weekly quests untouched.
     * Returns false when there is no active quest to reset.
     */
    public boolean resetActiveQuest(UUID player) {
        PlayerQuestData data = data(player);
        Quest target = data.quests().stream()
                .filter(this::isActive)
                .filter(quest -> quest.id().equals(data.selectedQuest()))
                .findFirst()
                .orElseGet(() -> data.quests().stream().filter(this::isActive).findFirst().orElse(null));
        if (target == null) return false;
        for (int i = 0; i < data.quests().size(); i++) {
            if (data.quests().get(i).id().equals(target.id())) {
                data.quests().set(i, target.withState(QuestState.AVAILABLE));
                break;
            }
        }
        data.select(null);
        data.calibrated(false);
        data.remainingWaves(0);
        data.calibrationStage(0);
        data.stageSlots(Collections.emptyMap());
        data.stageClicked(Collections.emptySet());
        data.clearComputerChunk();
        data.clearWaveChunk();
        save(player);
        return true;
    }

    private boolean isActive(Quest quest) {
        return quest.state() == QuestState.CALIBRATING
                || quest.state() == QuestState.CALIBRATED
                || quest.state() == QuestState.WAVES_ACTIVE;
    }
    public void reset(UUID player) {
            PlayerQuestData data = data(player);
            data.quests().clear(); data.select(null); data.calibrated(false); data.remainingWaves(0);
            data.waveStarted(false); data.calibrationStage(0); data.calibrationAttempt(data.calibrationAttempt() + 1);
            data.stageSlots(Map.of()); data.stageClicked(Set.of()); data.guaranteedDrops(Map.of());
            data.clearComputerChunk(); data.clearWaveChunk();
            observe(storage.delete(player));
    }
    /** Persists and evicts a player's data when they leave so the cache cannot grow without bound. */
    public void unload(UUID player) {
        observe(sessions.unload(player));
    }
    public void save(UUID player) {
        observe(sessions.save(player));
    }
    /** Saves detached runtime progress without recreating an offline login session. */
    public void saveRuntime(PlayerQuestData data) { persist(data); }
    public void saveAll() {
        observe(sessions.saveAll());
    }
    public CompletableFuture<Void> closeAsync() { return sessions.close(); }
    private void persist(PlayerQuestData data) {
        observe(storage.save(PlayerSnapshot.capture(data)));
    }
    private void observe(CompletableFuture<Void> operation) {
        operation.whenComplete((ignored, failure) -> {
            if (failure != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to persist quest progress", failure);
        });
    }
    public Optional<Quest> selected(UUID player) {
        PlayerQuestData data = data(player);
        return data.quests().stream().filter(q -> q.id().equals(data.selectedQuest())).findFirst();
    }
    public String state(UUID player) {
        return selected(player).map(quest -> quest.state().name()).orElse("NONE");
    }
    private void updateSelected(PlayerQuestData data, QuestState state) {
        for (int i = 0; i < data.quests().size(); i++) {
            if (data.quests().get(i).id().equals(data.selectedQuest())) {
                data.quests().set(i, data.quests().get(i).withState(state));
            }
        }
    }
}
