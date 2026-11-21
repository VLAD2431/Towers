package VLADMIX90.towers.game;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.display.CustomDisplayManager;
import VLADMIX90.towers.item.CustomItemFactory;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.model.PlayerQuestData;
import VLADMIX90.towers.model.Quest;
import VLADMIX90.towers.model.QuestState;
import VLADMIX90.towers.quest.QuestManager;
import VLADMIX90.towers.wave.*;
import org.bukkit.Location;
import org.bukkit.Difficulty;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import io.papermc.paper.event.entity.EntityMoveEvent;
import VLADMIX90.towers.storage.ForcedChunkOwnership;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.CompletableFuture;
import java.util.*;

/** Coordinates calibration, computer chunk detection and mob waves. */
public class GameManager {
    public static final String WAVE_MOB_TAG = "towers_wave_mob";

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final QuestManager quests;
    private final MenuManager menus;
    private final CustomDisplayManager displays;
    private final WaveConfig waves;
    private record StandaloneRun(String typeId, PlayerQuestData data) { }
    private final Map<UUID, StandaloneRun> standaloneRuns = new HashMap<>();
    private final Map<UUID, PlayerQuestData> offlineWaves = new HashMap<>();
    private final Map<UUID, Integer> alive = new HashMap<>();
    private final Map<UUID, List<UUID>> mobs = new HashMap<>();
    private final Map<UUID, Location> waveMobRiftCenters = new HashMap<>();
    /** Effects currently applied to every living wave mob, so a reload can drop the ones removed from the config. */
    private final Map<UUID, Set<PotionEffectType>> appliedEffects = new HashMap<>();
    private final Map<UUID, Rift> rifts = new HashMap<>();
    private final Map<UUID, List<ParticleEmitter>> emitters = new HashMap<>();
    private final Map<UUID, BukkitTask> particleTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> scheduledWaves = new HashMap<>();
    private final Map<UUID, Long> waveScheduleVersions = new HashMap<>();
    /** Players for whom the battle track is currently playing. */
    private final Set<UUID> battlePlaying = new HashSet<>();
    /** Per-player music repeat tasks; the resource-pack track has no completion callback. */
    private final Map<UUID, BukkitTask> battleTasks = new HashMap<>();
    private final Map<UUID, Integer> battleElapsedSeconds = new HashMap<>();
    private final Map<UUID, BukkitTask> riftAmbientTasks = new HashMap<>();
    private final Set<UUID> riftAmbientPlaying = new HashSet<>();
    private final Map<UUID, Integer> riftAmbientElapsedSeconds = new HashMap<>();
    private final List<Rift> testRifts = new ArrayList<>();
    /** The chunks the waves run in and the players that use them. */
    private final ChunkOccupancy occupancy = new ChunkOccupancy();
    private ForcedChunkOwnership chunkOwnership;
    private AsyncWaveChunkSelector chunkSelector;
    private final Map<UUID, CompletableFuture<Boolean>> chunkSearches = new HashMap<>();
    private final Random random = new Random();

    public GameManager(JavaPlugin plugin, ConfigManager config, QuestManager quests, MenuManager menus, CustomDisplayManager displays, WaveConfig waves) {
        this.plugin = plugin; this.config = config; this.quests = quests; this.menus = menus; this.displays = displays; this.waves = waves;
    }

    public GameManager(JavaPlugin plugin, ConfigManager config, QuestManager quests, MenuManager menus,
                       CustomDisplayManager displays, WaveConfig waves, ForcedChunkOwnership chunkOwnership) {
        this(plugin, config, quests, menus, displays, waves, chunkOwnership,
                action -> plugin.getServer().getScheduler().runTask(plugin, action));
    }

    public GameManager(JavaPlugin plugin, ConfigManager config, QuestManager quests, MenuManager menus,
                       CustomDisplayManager displays, WaveConfig waves, ForcedChunkOwnership chunkOwnership,
                       java.util.concurrent.Executor main) {
        this(plugin, config, quests, menus, displays, waves);
        this.chunkOwnership = Objects.requireNonNull(chunkOwnership);
        this.chunkSelector = new AsyncWaveChunkSelector(random, new PaperChunkEnvironment(plugin, occupancy::isOccupied), main);
    }

    public void tickWaveSearches() { if (chunkSelector != null) chunkSelector.tick(); }

    public GameManager(JavaPlugin plugin, ConfigManager config, QuestManager quests, MenuManager menus, WaveConfig waves) {
        this(plugin, config, quests, menus, null, waves);
    }

    public void startCalibration(Player player) {
        if (!quests.isLoaded(player.getUniqueId())) return;
        if (quests.selected(player.getUniqueId()).filter(quest -> quest.state() == QuestState.CALIBRATING).isEmpty()) {
            return;
        }
        PlayerQuestData data = quests.data(player.getUniqueId());
        data.calibrationStage(0);
        startStage(player, data);
    }

    public void openCalibrationMenu(Player player, int towerId) {
        boolean active = quests.selected(player.getUniqueId()).filter(quest -> quest.state() == QuestState.CALIBRATING)
                .filter(quest -> towerId <= 0 || quest.towerId() == towerId).isPresent();
        if (active) startCalibration(player); else menus.openCalibrationPreview(player);
    }

    private void startStage(Player player, PlayerQuestData data) {
        data.calibrationAttempt(data.calibrationAttempt() + 1);
        rollStage(data);
        menus.openCalibration(player, data);
        quests.save(player.getUniqueId());
        long attempt = data.calibrationAttempt();
        int stage = data.calibrationStage();
        long delay = Math.max(1, config.integer("calibration.display-ticks", 30));
        UUID playerId = player.getUniqueId();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!quests.isLoaded(playerId)) return;
            Player online = plugin.getServer().getPlayer(playerId);
            if (online == null || !online.isOnline()) return;
            PlayerQuestData current = quests.data(playerId);
            if (current != data || current.calibrationAttempt() != attempt || current.calibrationStage() != stage || current.calibrated()) return;
            if (quests.selected(playerId).filter(q -> q.state() == QuestState.CALIBRATING).isEmpty()) return;
            if (!(online.getOpenInventory().getTopInventory().getHolder() instanceof VLADMIX90.towers.menu.MenuHolder holder)
                    || holder.type() != VLADMIX90.towers.menu.MenuType.CALIBRATION || holder.preview()) return;
            startStage(online, current);
        }, delay);
    }

    private void rollStage(PlayerQuestData data) {
        List<Integer> available = new ArrayList<>(MenuManager.calibrationSlots());
        Collections.shuffle(available);
        int paperCount = Math.max(1, Math.min(config.integer("calibration.papers-per-stage", 5), available.size()));
        int realCount = Math.max(1, Math.min(config.integer("calibration.real-per-stage", 2), paperCount));
        Map<Integer, Boolean> slots = new LinkedHashMap<>();
        for (int i = 0; i < paperCount; i++) slots.put(available.get(i), i < realCount);
        data.stageSlots(slots);
        data.stageClicked(new LinkedHashSet<>());
    }

    public void calibrationClick(Player player, int slot) {
        if (!quests.isLoaded(player.getUniqueId())) return;
        PlayerQuestData data = quests.data(player.getUniqueId());
        if (data.selectedQuest() == null || data.calibrated()
                || quests.selected(player.getUniqueId()).filter(q -> q.state() == QuestState.CALIBRATING).isEmpty()) return;
        Boolean real = data.stageSlots().get(slot);
        if (real == null) return;
        if (data.stageClicked().contains(slot)) return; // бумага уже нажата — звук не повторяется
        if (!real) {
            playCalibrationSound(player, "sounds.calibration-fake-click");
            startStage(player, data);
            return;
        }
        playCalibrationSound(player, "sounds.calibration-click");
        data.stageClicked().add(slot);
        long realCount = data.stageSlots().values().stream().filter(Boolean::booleanValue).count();
        if (data.stageClicked().size() < realCount) { menus.openCalibration(player, data); quests.save(player.getUniqueId()); return; }
        data.calibrationStage(data.calibrationStage() + 1);
        int stages = Math.max(1, config.integer("calibration.stages", 3));
        if (data.calibrationStage() >= stages) {
            quests.completeCalibration(player.getUniqueId()); player.closeInventory();
            startWaves(player); return;
        }
        startStage(player, data);
    }

    /** Plays the configured calibration click sound; a blank or unknown sound is silently skipped. */
    private void playCalibrationSound(Player player, String path) {
        config.playSound(player, path);
    }

    /** Rebuilds the wave configuration from the current config file; existing waves keep running. */
    public void reloadWaves() {
        waves.reload(plugin.getConfig());
        for (UUID id : new HashSet<>(standaloneRuns.keySet())) {
            if (waves.type(standaloneRuns.get(id).typeId()) == null) cleanupRuntime(id);
        }
    }

    private PlayerQuestData waveData(UUID id) {
        StandaloneRun run = standaloneRuns.get(id);
        return run != null ? run.data() : offlineWaves.containsKey(id) ? offlineWaves.get(id) : quests.data(id);
    }

    private void saveWaveData(UUID id) {
        if (!standaloneRuns.containsKey(id)) {
            if (offlineWaves.containsKey(id)) quests.saveRuntime(offlineWaves.get(id));
            else quests.save(id);
        }
    }

    private boolean waveDefeated(UUID id) {
        StandaloneRun run = standaloneRuns.get(id);
        if (run == null && !offlineWaves.containsKey(id)) return quests.waveDefeated(id);
        if (run == null) {
            PlayerQuestData data = offlineWaves.get(id);
            if (data.remainingWaves() <= 0) return false;
            data.remainingWaves(data.remainingWaves() - 1);
            if (data.remainingWaves() == 0) data.quests().replaceAll(q -> q.id().equals(data.selectedQuest()) ? q.withState(QuestState.COMPLETED) : q);
            saveWaveData(id);
            return true;
        }
        run.data().remainingWaves(Math.max(0, run.data().remainingWaves() - 1));
        return true;
    }

    /** Starts a temporary run without creating or changing a quest. */
    public boolean startStandaloneWaves(Player player, String typeId) {
        UUID id = player.getUniqueId();
        if (waves.type(typeId) == null || standaloneRuns.containsKey(id) || alive.containsKey(id)
                || rifts.containsKey(id) || scheduledWaves.containsKey(id)
                || quests.selected(id).filter(q -> q.state() == QuestState.WAVES_ACTIVE).isPresent()) return false;
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world == null) return false;
        int x = location.getBlockX() >> 4, z = location.getBlockZ() >> 4;
        ChunkKey chunk = new ChunkKey(world.getUID(), x, z);
        if (occupancy.isOccupied(chunk) || WaterGuard.isWaterChunk(world, x, z)) return false;
        PlayerQuestData data = new PlayerQuestData(id);
        data.waveChunk(world.getUID(), x, z);
        data.remainingWaves(waves.wavesForType(typeId));
        StandaloneRun run = new StandaloneRun(typeId, data);
        standaloneRuns.put(id, run);
        markOccupied(chunk, id);
        forceLoadWaveChunk(data).whenComplete((owned, failure) -> {
            if (standaloneRuns.get(id) != run) return;
            Player online = plugin.getServer().getPlayer(id);
            if (failure != null || online == null || !online.isOnline()) {
                if (failure != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot retain standalone wave chunk", failure);
                cleanupRuntime(id);
                return;
            }
            spawnRift(online);
            if (!rifts.containsKey(id)) cleanupRuntime(id);
            else startParticleTask(online);
        });
        return true;
    }

    /**
     * Re-applies the current wave configuration to the mobs that are already alive: new effects are
     * applied, changed ones are replaced and the ones removed from the config are dropped.
     */
    public void refreshLiveMobEffects() {
        pruneAppliedEffects();
        int updated = 0;
        for (Map.Entry<UUID, List<UUID>> entry : mobs.entrySet()) {
            WaveTypeSpec type = waveTypeFor(entry.getKey());
            if (type == null) continue;
            for (UUID mobId : entry.getValue()) {
                Entity entity = plugin.getServer().getEntity(mobId);
                if (!(entity instanceof LivingEntity living)) continue;
                List<MobEffectSpec> specs = specsFor(type, living);
                Set<PotionEffectType> wanted = effectTypes(specs);
                Set<PotionEffectType> previous = appliedEffects.getOrDefault(mobId, Set.of());
                for (PotionEffectType old : removedEffects(previous, wanted)) living.removePotionEffect(old);
                applyEffects(living, specs, true);
                appliedEffects.put(mobId, wanted);
                updated++;
            }
        }
        if (updated > 0) plugin.getLogger().info("[Towers] Эффекты живых мобов обновлены: " + updated);
    }

    /** True when the entity is a mob spawned by a wave of this plugin. */
    public static boolean isWaveMob(Entity entity) {
        return entity.getScoreboardTags().contains(WAVE_MOB_TAG);
    }

    static boolean isWithinRiftBoundary(Location center, Location location) {
        if (center == null || location == null || center.getWorld() == null
                || !center.getWorld().equals(location.getWorld())) return false;
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        return dx * dx + dz * dz <= 2500.0;
    }

    public void onEntityMove(EntityMoveEvent event) {
        Location center = waveMobRiftCenters.get(event.getEntity().getUniqueId());
        if (center != null && !isWithinRiftBoundary(center, event.getTo())) event.setCancelled(true);
    }

    public boolean shouldCancelWaveMobDamage(Entity target, Entity attacker) {
        if (target == null || attacker == null || !isWaveMob(target)) return false;
        Location center = waveMobRiftCenters.get(target.getUniqueId());
        return center != null && !isWithinRiftBoundary(center, attacker.getLocation());
    }

    /** Wave type of the player's running wave; falls back to the first configured type. */
    private WaveTypeSpec waveTypeFor(UUID owner) {
        StandaloneRun run = standaloneRuns.get(owner);
        if (run != null) return waves.type(run.typeId());
        String typeId = quests.selected(owner).map(Quest::waveType).orElse(null);
        WaveTypeSpec type = typeId == null ? null : waves.type(typeId);
        if (type == null && typeId != null) {
            plugin.getLogger().warning("Тип волны '" + typeId + "' больше не настроен в wave-types — используется первый доступный тип.");
        }
        if (type == null && !waves.typeIds().isEmpty()) type = waves.type(waves.typeIds().get(0));
        return type;
    }

    /** Mob spec of the given entity type inside the wave type; empty when the mob left the config. */
    List<MobEffectSpec> specsFor(WaveTypeSpec type, LivingEntity entity) {
        String customId = entity.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey("custommob", "rockfrost-mob"), org.bukkit.persistence.PersistentDataType.STRING);
        if (customId == null) customId = entity.getPersistentDataContainer().get(
                new org.bukkit.NamespacedKey("rockfrost", "rockfrost-mob"), org.bukkit.persistence.PersistentDataType.STRING);
        final String mobId = customId;
        return type.mobs().stream().filter(spec -> mobId != null
                        ? mobId.equals(spec.customId()) : spec.customId() == null && spec.type() == entity.getType())
                .findFirst().map(WaveMobSpec::effects).orElse(List.of());
    }

    public void openChunkComputer(Player player, Location computerLocation) {
        openChunkComputer(player, computerLocation, -1);
    }

    public void openChunkComputer(Player player, Location computerLocation, int towerId) {
        PlayerQuestData data = quests.data(player.getUniqueId());
        int linkedTowerId = towerId;
        if (linkedTowerId < 0 && displays != null) {
            // The computer stands at the spot of its tower: look up that tower when needed.
            linkedTowerId = displays.towerIdAt(computerLocation);
        }
        MenuManager.ChunkMapView view = determineChunkMapView(player, linkedTowerId);
        if (view == MenuManager.ChunkMapView.READY || view == MenuManager.ChunkMapView.ASSIGNED_UNCALIBRATED) {
            int chunkX = computerLocation.getChunk().getX();
            int chunkZ = computerLocation.getChunk().getZ();
            boolean changed = data.computerWorld() == null || !data.computerWorld().equals(computerLocation.getWorld().getUID())
                    || data.computerChunkX() != chunkX || data.computerChunkZ() != chunkZ
                    || data.computerTowerId() != linkedTowerId;
            data.computerChunk(computerLocation.getWorld().getUID(), chunkX, chunkZ, linkedTowerId);
            if (changed && data.waveWorld() != null && data.remainingWaves() <= 0) {
                // The authorized computer moved on: the chunk of the finished wave is given back.
                releaseWaveChunk(data, player.getUniqueId());
                data.clearWaveChunk();
            }
            quests.save(player.getUniqueId());
            if (view == MenuManager.ChunkMapView.READY) startWaves(player, linkedTowerId);
        }
        if (view == MenuManager.ChunkMapView.PREVIEW) {
            int[] center = previewChunkCoordinates(computerLocation);
            menus.openChunkMap(player, view, center[0], center[1]);
        } else {
            menus.openChunkMap(player, view);
        }
    }

    private MenuManager.ChunkMapView determineChunkMapView(Player player, int linkedTowerId) {
        java.util.Optional<VLADMIX90.towers.model.Quest> selected = quests.selected(player.getUniqueId());
        if (selected.isEmpty()) return MenuManager.ChunkMapView.PREVIEW;
        VLADMIX90.towers.model.Quest quest = selected.get();
        if (linkedTowerId > 0 && quest.towerId() == linkedTowerId) {
            if (quest.state() == QuestState.CALIBRATING) {
                return MenuManager.ChunkMapView.ASSIGNED_UNCALIBRATED;
            }
            if (quest.state() == QuestState.CALIBRATED || quest.state() == QuestState.WAVES_ACTIVE) {
                return MenuManager.ChunkMapView.READY;
            }
        }
        return MenuManager.ChunkMapView.PREVIEW;
    }

    static int[] previewChunkCoordinates(Location computerLocation) {
        return new int[]{computerLocation.getChunk().getX(), computerLocation.getChunk().getZ()};
    }

    /** Resumes a previously authorized run from its stored bound computer. */
    public void startWaves(Player player) {
        startWaves(player, quests.data(player.getUniqueId()).computerTowerId());
    }

    /** Starts waves only from the computer bound to the selected quest's tower. */
    public void startWaves(Player player, int computerTowerId) {
        if (standaloneRuns.containsKey(player.getUniqueId())) return;
        PlayerQuestData data = quests.data(player.getUniqueId());
        if (computerTowerId <= 0 || data.computerTowerId() != computerTowerId
                || quests.selected(player.getUniqueId()).filter(q -> q.towerId() == computerTowerId)
                .filter(q -> q.state() == QuestState.CALIBRATED || q.state() == QuestState.WAVES_ACTIVE).isEmpty()) {
            return;
        }
        if (data.computerWorld() == null) return;
        if (alive.containsKey(player.getUniqueId())) return;
        if (waves.typeIds().isEmpty()) {
            plugin.getLogger().warning("Волны не настроены: добавьте раздел wave-types в config.yml.");
            return;
        }
        beginWaveSearch(player.getUniqueId(), data, false);
    }

    /**
     * Picks the chunk the wave runs in: a free chunk of the area around the computer that is not
     * water. False when the area holds no dry chunk — the wave must not run in water.
     */
    CompletableFuture<Boolean> ensureDryWaveChunk(UUID owner, PlayerQuestData data) {
        CompletableFuture<Boolean> existing = chunkSearches.get(owner);
        if (existing != null) return existing;
        if (chunkSelector == null || data.computerWorld() == null) return CompletableFuture.failedFuture(
                new IllegalStateException("Wave chunk selection is unavailable"));
        int gridSize = Math.max(1, Math.min(config.integer("chunk-area.grid-size", MenuManager.GRID_SIZE), MenuManager.GRID_SIZE));
        int sectorSize = Math.max(1, config.integer("chunk-area.sector-size", MenuManager.SECTOR_SIZE));
        ChunkKey saved = data.waveWorld() == null ? null : new ChunkKey(data.waveWorld(), data.waveChunkX(), data.waveChunkZ());
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        chunkSearches.put(owner, result);
        chunkSelector.select(owner, data.computerWorld(), data.computerChunkX(), data.computerChunkZ(), gridSize, sectorSize, saved)
                .whenComplete((selected, failure) -> {
                    if (chunkSearches.get(owner) != result) return;
                    if (failure != null || selected.isEmpty()) {
                        chunkSearches.remove(owner, result); chunkSelector.release(owner);
                        if (failure != null) result.completeExceptionally(failure); else result.complete(false);
                        return;
                    }
                    ChunkKey key = selected.orElseThrow();
                    if (!key.equals(saved)) releaseWaveChunk(data, owner);
                    data.waveChunk(key.world(), key.x(), key.z());
                    markOccupied(key, owner);
                    forceLoadWaveChunk(data).whenComplete((owned, error) -> {
                        if (chunkSearches.get(owner) != result) return;
                        chunkSearches.remove(owner, result); chunkSelector.release(owner);
                        if (error != null) {
                            releaseWaveChunk(data, owner);
                            result.completeExceptionally(error);
                        } else result.complete(true); // Foreign force flags remain untouched; the loaded chunk is usable.
                    });
                });
        return result;
    }

    private void beginWaveSearch(UUID id, PlayerQuestData data, boolean resume) {
        if (chunkSearches.containsKey(id)) return;
        UUID selectedQuest = data.selectedQuest();
        ensureDryWaveChunk(id, data).whenComplete((found, error) -> {
            if (!quests.isLoaded(id) || quests.data(id) != data || !Objects.equals(data.selectedQuest(), selectedQuest)) return;
            Player current = plugin.getServer().getPlayer(id);
            if (current == null || !current.isOnline()) return;
            if (error != null) {
                if (!(error instanceof java.util.concurrent.CancellationException)) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Could not load wave chunk for " + id, error);
                    current.sendMessage(VLADMIX90.towers.util.Text.parse("<red>Не удалось загрузить чанк волны. Повторите попытку позже."));
                }
                return;
            }
            if (!found) {
                current.sendMessage(config.message("wave-no-dry-chunk"));
                if (resume) quests.cancelRun(id);
                return;
            }
            if (occupancy.occupiedByOther(new ChunkKey(data.waveWorld(), data.waveChunkX(), data.waveChunkZ()), id))
                current.sendMessage(config.message("wave-shared-chunk"));
            if (!resume) {
                String typeId = quests.selected(id).map(Quest::waveType).orElse(null);
                quests.startWaves(id, waves.wavesForType(typeId));
                data.waveStarted(false);
            }
            spawnRift(current); startParticleTask(current); startRiftAmbient(current);
            if (resume && data.waveStarted()) { stopRiftAmbient(id); scheduleWave(current); }
            quests.save(id);
        });
    }

    public void resumeWaves(Player player) {
        if (!quests.isLoaded(player.getUniqueId())) return;
        UUID playerId = player.getUniqueId();
        PlayerQuestData offline = offlineWaves.remove(playerId);
        if (offline != null) {
            PlayerQuestData loaded = quests.data(playerId);
            if (Objects.equals(loaded.selectedQuest(), offline.selectedQuest())) {
                loaded.quests().clear(); loaded.quests().addAll(offline.quests());
                loaded.remainingWaves(offline.remainingWaves());
                loaded.waveChunk(offline.waveWorld(), offline.waveChunkX(), offline.waveChunkZ());
                loaded.guaranteedDrops(offline.guaranteedDrops());
                quests.save(playerId);
            }
        }
        if (standaloneRuns.containsKey(player.getUniqueId())) return;
        if (quests.selected(player.getUniqueId()).filter(q -> q.state() == QuestState.WAVES_ACTIVE).isEmpty()) return;
        if (waves.typeIds().isEmpty()) return;
        if (alive.containsKey(player.getUniqueId())) {
            // The wave survived the relog: only its particle ticking was paused on quit.
            startParticleTask(player);
            startBattleSound(player);
            return;
        }
        if (quests.data(player.getUniqueId()).remainingWaves() > 0) {
            beginWaveSearch(player.getUniqueId(), quests.data(player.getUniqueId()), true);
        }
    }

    public void resumeCalibration(Player player) {
        if (!quests.isLoaded(player.getUniqueId())) return;
        if (quests.selected(player.getUniqueId()).filter(q -> q.state() == QuestState.CALIBRATING).isEmpty()) return;
        startStage(player, quests.data(player.getUniqueId()));
    }

    /** Spawn point of a wave mob: one of the dry columns of the wave chunk. */
    Location spawnLocation(World world, List<int[]> dryColumns) {
        if (dryColumns.isEmpty()) return null;
        int[] column = dryColumns.get(random.nextInt(dryColumns.size()));
        int top = world.getHighestBlockYAt(column[0], column[1]);
        return new Location(world, column[0] + 0.5, top + 1.0, column[1] + 0.5);
    }

    /** Every dry column of the wave chunk; empty when the whole chunk is water. */
    private List<int[]> dryColumns(World world, int baseX, int baseZ) {
        List<int[]> columns = new ArrayList<>();
        for (int x = baseX; x < baseX + 16; x++)
            for (int z = baseZ; z < baseZ + 16; z++)
                if (!WaterGuard.isWaterColumn(world, x, z)) columns.add(new int[]{x, z});
        return columns;
    }

    List<UUID> spawnMobs(World world, WaveTypeSpec type, int waveIndex, Player player, int baseX, int baseZ) {
        List<UUID> spawned = new ArrayList<>();
        List<int[]> dryColumns = dryColumns(world, baseX, baseZ);
        if (dryColumns.isEmpty()) {
            plugin.getLogger().warning("Чанк волны целиком в воде — мобы не созданы.");
            return spawned;
        }
        for (WaveMobSpec spec : type.mobs()) {
            for (int i = 0; i < spec.countForWave(waveIndex); i++) {
                Location location = spawnLocation(world, dryColumns);
                Entity entity = spec.customId() == null ? world.spawnEntity(location, spec.type())
                        : spawnCustomMob(spec.customId(), location);
                if (entity == null) {
                    for (UUID id : spawned) {
                        Entity previous = plugin.getServer().getEntity(id);
                        if (previous != null) previous.remove();
                    }
                    forgetAppliedEffects(spawned);
                    emitters.remove(player.getUniqueId());
                    return null;
                }
                entity.addScoreboardTag(WAVE_MOB_TAG);
                if (entity instanceof LivingEntity living) {
                    // Wave mobs must survive until killed by the player: vanilla despawn removes them
                    // beyond 128 blocks from any player and never fires EntityDeathEvent, which would
                    // strand the wave counter forever.
                    living.setRemoveWhenFarAway(false);
                    living.setPersistent(true);
                    applyEffects(living, spec.effects(), false);
                    appliedEffects.put(entity.getUniqueId(), effectTypes(spec.effects()));
                }
                if (entity instanceof Monster monster) monster.setTarget(player);
                spawned.add(entity.getUniqueId());
                Rift rift = rifts.get(player.getUniqueId());
                if (rift != null) waveMobRiftCenters.put(entity.getUniqueId(), rift.location());
                spec.particles().forEach(particle -> emitters.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayList<>())
                        .add(new ParticleEmitter(entity.getUniqueId(), particle)));
            }
        }
        return spawned;
    }

    LivingEntity spawnCustomMob(String id, Location location) {
        org.bukkit.plugin.Plugin customMob = plugin.getServer().getPluginManager().getPlugin("CustomMob");
        if (customMob == null || !customMob.isEnabled()) {
            customMob = plugin.getServer().getPluginManager().getPlugin("RockFrost");
        }
        if (customMob == null || !customMob.isEnabled()) {
            plugin.getLogger().warning("Для моба custom:" + id + " требуется включённый плагин CustomMob.");
            return null;
        }
        try {
            Object result = customMob.getClass().getMethod("spawnMob", String.class, Location.class)
                    .invoke(customMob, id, location);
            if (result instanceof LivingEntity living) return living;
            plugin.getLogger().warning("CustomMob не смог создать custom:" + id + ": проверьте ID и место спавна.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Не удалось вызвать API CustomMob для custom:" + id
                    + ": установите совместимую обновлённую версию CustomMob. " + exception);
        }
        return null;
    }

    /** Applies the configured effects to a mob; force replaces an already active effect of the same type. */
    private void applyEffects(LivingEntity living, List<MobEffectSpec> effects, boolean force) {
        for (MobEffectSpec effect : effects) {
            int duration = effect.duration() < 0 ? PotionEffect.INFINITE_DURATION : effect.duration();
            living.addPotionEffect(new PotionEffect(effect.type(), duration, Math.max(0, effect.level() - 1)), force);
        }
    }

    /** Distinct effect types of the given specs, in configuration order. */
    private static Set<PotionEffectType> effectTypes(List<MobEffectSpec> effects) {
        Set<PotionEffectType> types = new LinkedHashSet<>();
        for (MobEffectSpec effect : effects) types.add(effect.type());
        return types;
    }

    /**
     * Effects that were applied before but are not configured any more: those have to be removed
     * from the mob. Generic over the effect type so the comparison needs no potion registry.
     */
    static <T> Set<T> removedEffects(Set<T> previous, Set<T> wanted) {
        Set<T> removed = new LinkedHashSet<>();
        for (T type : previous) if (!wanted.contains(type)) removed.add(type);
        return removed;
    }

    private void spawnWave(Player player) {
        UUID id = player.getUniqueId(); PlayerQuestData data = waveData(id);
        data.waveStarted(true);
        World world = plugin.getServer().getWorld(data.waveWorld());
        if (world == null) {
            if (standaloneRuns.containsKey(id)) { cleanupRuntime(id); return; }
            alive.remove(id); mobs.remove(id);
            plugin.getLogger().warning("Мир волны недоступен, волна не запущена.");
            return;
        }
        if (world.getDifficulty() == Difficulty.PEACEFUL) {
            plugin.getLogger().warning("Сложность мира PEACEFUL: враждебные мобы исчезают сразу после спавна. Волна не будет держаться.");
        }
        WaveTypeSpec type = waveTypeFor(id);
        if (type == null) {
            plugin.getLogger().warning("Волны не настроены: добавьте раздел wave-types в config.yml.");
            if (standaloneRuns.containsKey(id)) cleanupRuntime(id);
            return;
        }
        purgeOrphanWaveMobs(world);
        emitters.put(id, new ArrayList<>());
        int waveIndex = Math.max(1, waves.wavesForType(type.id()) - data.remainingWaves() + 1);
        List<UUID> spawned = spawnMobs(world, type, waveIndex, player, data.waveChunkX() * 16, data.waveChunkZ() * 16);
        if (spawned == null) {
            plugin.getLogger().warning("Не удалось создать кастомного моба — волна прервана, задание можно запустить снова.");
            player.sendMessage("§cНе удалось создать кастомного моба. Волна прервана; сообщите администратору.");
            boolean standalone = standaloneRuns.containsKey(id);
            cleanupRuntime(id);
            if (!standalone) quests.cancelRun(id);
            return;
        }
        forgetAppliedEffects(mobs.put(id, spawned)); alive.put(id, spawned.size());
        plugin.getLogger().info("[Towers] Волна " + waveIndex + ": чанк " + data.waveChunkX() + "," + data.waveChunkZ()
                + ", создано=" + spawned.size());
        if (!spawned.isEmpty()) {
            config.playSound(player, "sounds.wave-start");
            startBattleSound(player);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                long survivors = spawned.stream().filter(u -> plugin.getServer().getEntity(u) != null).count();
                plugin.getLogger().info("[Towers] Проверка через 5с: живо=" + survivors + " из " + spawned.size());
            }, 100L);
        }
        if (spawned.isEmpty()) finishWave(player);
    }

    /** Drops tagged wave mobs that no longer belong to any tracked wave, e.g. left over after a restart. */
    private void purgeOrphanWaveMobs(World world) {
        Set<UUID> tracked = new HashSet<>();
        for (List<UUID> trackedMobs : mobs.values()) tracked.addAll(trackedMobs);
        for (Entity entity : world.getEntities()) {
            if (isWaveMob(entity) && !tracked.contains(entity.getUniqueId())) entity.remove();
        }
    }

    public void onDeath(EntityDeathEvent event) {
        if (isWaveMob(event.getEntity())) {
            event.getDrops().clear();
        }
        UUID mobId = event.getEntity().getUniqueId();
        waveMobRiftCenters.remove(mobId);
        UUID owner = mobs.entrySet().stream().filter(entry -> entry.getValue().remove(mobId)).map(Map.Entry::getKey).findFirst().orElse(null);
        appliedEffects.remove(mobId);
        if (owner == null) return;
        Integer left = alive.computeIfPresent(owner, (key, value) -> value - 1);
        if (left != null && left > 0) return;
        mobs.remove(owner); alive.remove(owner);
        if (left == null || !waveDefeated(owner)) return;
        advanceAfterWave(owner);
    }

    /**
     * After a wave is cleared: either the next wave starts after the pause, or the quest is done.
     */
    private void advanceAfterWave(UUID owner) {
        stopRiftAmbient(owner);
        Player player = plugin.getServer().getPlayer(owner);
        if (waveData(owner).remainingWaves() > 0) {
            // If the owner is offline the wave is resumed by resumeWaves() on their next join.
            if (player != null) scheduleWave(player);
        } else {
            config.playSound(player, "sounds.quest-complete");
            // Completion must not depend on the owner being online: loot and cleanup happen either way.
            finishWaves(owner);
        }
    }

    private void finishWave(Player player) {
        UUID id = player.getUniqueId();
        forgetAppliedEffects(mobs.remove(id)); alive.remove(id);
        waveDefeated(id);
        advanceAfterWave(id);
    }

    /**
     * The rift opened: the first wave starts after the pause between two waves.
     * A wave that is already running is not started a second time.
     */
    public void onRiftOpened(Player player) {
        PlayerQuestData data = waveData(player.getUniqueId());
        if (data.waveStarted()) return;
        data.waveStarted(true);
        stopRiftAmbient(player.getUniqueId());
        startBattleSound(player);
        saveWaveData(player.getUniqueId());
        config.playSound(player, "sounds.rift-open");
        scheduleWave(player);
    }

    /** Starts the wave after the pause between two waves. */
    private void scheduleWave(Player player) {
        UUID id = player.getUniqueId();
        stopRiftAmbient(id);
        cancelScheduledWave(id);
        long version = waveScheduleVersions.merge(id, 1L, Long::sum);
        long delay = waveDelayTicks(random, config.integer("chunk-area.wave-interval-min-seconds", 5),
                config.integer("chunk-area.wave-interval-max-seconds", 7));
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!Objects.equals(waveScheduleVersions.get(id), version)) return;
            scheduledWaves.remove(id);
            Player online = plugin.getServer().getPlayer(id);
            if (online == null || !online.isOnline()) return;
            spawnWave(online);
        }, delay);
        scheduledWaves.put(id, task);
    }

    private void cancelScheduledWave(UUID id) {
        BukkitTask task = scheduledWaves.remove(id);
        if (task != null) task.cancel();
        waveScheduleVersions.merge(id, 1L, Long::sum);
    }

    /**
     * The pause between two waves in ticks: a random moment between the given seconds.
     */
    static int waveDelayTicks(Random random, int minSeconds, int maxSeconds) {
        int min = Math.max(0, minSeconds) * 20;
        int max = Math.max(minSeconds, maxSeconds) * 20;
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }

    /** Creates the rift; null when the spot is in water, because a rift must not stand in water. */
    Rift createRift(Location center, RiftSpec spec) {
        return createRift(center, spec, null);
    }

    /** Creates the rift; {@code onOpened} is told when the rift opens. */
    Rift createRift(Location center, RiftSpec spec, Runnable onOpened) {
        return createRift(center, spec, null, onOpened);
    }

    /** Creates a rift restricted to its quest owner; null owner is used for standalone test rifts. */
    Rift createRift(Location center, RiftSpec spec, UUID ownerId, Runnable onOpened) {
        World world = center.getWorld();
        if (world != null && WaterGuard.isWaterColumn(world, center.getBlockX(), center.getBlockZ())) {
            plugin.getLogger().warning("Разлом не создан: место в воде.");
            return null;
        }
        return Rift.spawn(plugin, center, CustomItemFactory.modelItem(spec.material(), spec.customModelData(), spec.itemModel()), spec,
                ownerId, onOpened, ownerId != null && standaloneRuns.containsKey(ownerId)
                        ? () -> cleanupRuntime(ownerId) : null);
    }

    /** Spawns a standalone rift for testing; it is tracked so {@link #cleanup()} can remove it. */
    public Rift spawnTestRift(Location location) {
        Rift rift = createRift(location, waves.rift());
        if (rift == null) return null;
        testRifts.add(rift);
        return rift;
    }

    /** Removes every rift created by {@link #spawnTestRift(Location)}. Returns how many were removed. */
    public int clearTestRifts() {
        int count = testRifts.size();
        testRifts.forEach(Rift::despawn);
        testRifts.clear();
        return count;
    }

    public double riftHeight() { return waves.rift().height(); }

    void spawnRift(Player player) {
        UUID id = player.getUniqueId();
        if (rifts.containsKey(id)) return;
        PlayerQuestData data = waveData(id);
        Location center = riftDropLocation(data);
        if (center == null) {
            plugin.getLogger().warning("Нет сухого места для разлома в чанке волны.");
            return;
        }
        Rift rift = createRift(center, waves.rift(), id, () -> {
            Player online = plugin.getServer().getPlayer(id);
            if (online != null && rifts.containsKey(id)) onRiftOpened(online);
        });
        if (rift == null) return;
        rifts.put(id, rift);
        startRiftAmbient(player);
    }

    /** Center of the rift above the wave chunk; doubles as the loot anchor when the rift entity is gone. */
    private Location riftDropLocation(PlayerQuestData data) {
        if (data.waveWorld() == null) return null;
        World world = plugin.getServer().getWorld(data.waveWorld());
        if (world == null) return null;
        int baseX = data.waveChunkX() * 16, baseZ = data.waveChunkZ() * 16;
        int[] column = WaterGuard.firstDryColumn(baseX, baseZ, (x, z) -> WaterGuard.isWaterColumn(world, x, z));
        if (column == null) return null;
        int top = world.getHighestBlockYAt(column[0], column[1]);
        return new Location(world, column[0] + 0.5, top + waves.rift().height(), column[1] + 0.5);
    }

    void emitParticles(Server server, List<ParticleEmitter> list) {
        for (ParticleEmitter emitter : list) {
            emitter.tick();
            MobParticleSpec spec = emitter.spec();
            if (spec.interval() > 0 && emitter.ticks() % spec.interval() != 0) continue;
            Entity entity = server.getEntity(emitter.mobId());
            if (entity == null || entity.getWorld() == null) continue;
            Location location = entity.getLocation().add(0, 1, 0);
            if (spec.data() == null) {
                location.getWorld().spawnParticle(spec.type(), location, spec.count(),
                        spec.spread(), spec.spread(), spec.spread(), spec.speed());
            } else {
                location.getWorld().spawnParticle(spec.type(), location, spec.count(),
                        spec.spread(), spec.spread(), spec.spread(), spec.speed(), spec.data());
            }
        }
    }

    private void startParticleTask(Player player) {
        UUID id = player.getUniqueId();
        if (particleTasks.containsKey(id)) return;
        particleTasks.put(id, plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> emitParticles(plugin.getServer(), emitters.getOrDefault(id, List.of())), 0L, 1L));
    }

    void finishWaves(UUID id) {
        stopBattleSound(id);
        PlayerQuestData data = waveData(id);
        StandaloneRun standalone = standaloneRuns.get(id);
        forgetAppliedEffects(mobs.get(id));
        releaseWaveChunk(data, id);
        Rift rift = rifts.remove(id);
        Location drop = rift != null ? rift.location() : riftDropLocation(data);
        Quest selectedQuest = standalone == null ? data.quests().stream().filter(q -> q.id().equals(data.selectedQuest())).findFirst().orElse(null) : null;
        String questId = data.selectedQuest() == null ? null : data.selectedQuest().toString();
        if (drop != null) {
            List<RiftLootSpec> lootTable = standalone != null ? waves.lootForType(standalone.typeId())
                    : selectedQuest == null ? waves.loot() : waves.lootForType(selectedQuest.waveType());
            for (LootDrop loot : RiftLoot.rollAll(lootTable, data.guaranteedDrops(), questId, random, waves.scrollChance())) {
                ItemStack item = buildLoot(loot);
                if (item != null) drop.getWorld().dropItem(drop, item);
            }
        }
        if (rift != null) {
            playRiftCloseSound(id);
            rift.despawn();
        }
        // The wave is over: drop the chunk so the chunk-map menu stops highlighting its sector.
        data.clearWaveChunk();
        saveWaveData(id);
        standaloneRuns.remove(id);
        cancelParticleTask(id); emitters.remove(id);
    }

    /** Rolls the selected quest's loot table and gives the result directly to the player. */
    public boolean giveTestLoot(Player player) {
        UUID id = player.getUniqueId();
        Quest selectedQuest = quests.selected(id).orElse(null);
        if (selectedQuest == null) return false;
        PlayerQuestData data = quests.data(id);
        String questId = selectedQuest.id().toString();
        return giveTestLoot(player, waves.lootForType(selectedQuest.waveType()), data, questId);
    }

    public boolean giveTestLoot(Player player, String waveTypeId) {
        if (!waves.typeIds().contains(waveTypeId)) return false;
        return giveTestLoot(player, waves.lootForType(waveTypeId), quests.data(player.getUniqueId()), null);
    }

    public List<String> waveTypeIds() {
        return waves.typeIds();
    }

    private boolean giveTestLoot(Player player, List<RiftLootSpec> lootTable, PlayerQuestData data, String questId) {
        List<LootDrop> drops = RiftLoot.rollAll(lootTable, data.guaranteedDrops(), questId, random, waves.scrollChance());
        for (LootDrop loot : drops) {
            ItemStack item = buildLoot(loot);
            if (item != null) player.getInventory().addItem(item);
        }
        return true;
    }

    private ItemStack buildLoot(LootDrop loot) {
        if (loot.spec().skinId() == null) return RiftLoot.build(loot);
        SkinScrollProvider scrolls = new SkinScrollProvider(plugin.getServer().getPluginManager(), plugin.getLogger());
        return RiftLoot.build(loot, scrolls::create);
    }

    private void cancelParticleTask(UUID id) {
        BukkitTask task = particleTasks.remove(id);
        if (task != null) task.cancel();
    }

    /** Starts the battle track once for this run; later waves keep the same track. */
    void startBattleSound(Player player) {
        UUID id = player.getUniqueId();
        if (!battlePlaying.add(id)) return;
        config.playSound(player, "sounds.battle");
        battleElapsedSeconds.put(id, 0);
        battleTasks.put(id, plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player online = plugin.getServer().getPlayer(id);
            if (online == null) {
                stopBattleSound(id);
                return;
            }
            int elapsed = battleElapsedSeconds.merge(id, 1, Integer::sum);
            int interval = Math.max(1, config.integer("sounds.battle-interval-seconds", 8));
            if (elapsed >= interval) {
                config.stopSound(online, "sounds.battle");
                config.playSound(online, "sounds.battle");
                battleElapsedSeconds.put(id, 0);
            }
        }, 20L, 20L));
    }

    void stopBattleSound(UUID id) {
        BukkitTask task = battleTasks.remove(id);
        if (task != null) task.cancel();
        battleElapsedSeconds.remove(id);
        if (!battlePlaying.remove(id)) return;
        Player online = plugin.getServer().getPlayer(id);
        if (online != null) config.stopSound(online, "sounds.battle");
    }

    /** Plays the rift closing sound to its owner; null-safe when the owner is offline. */
    void playRiftCloseSound(UUID id) {
        config.playSound(plugin.getServer().getPlayer(id), "sounds.rift-close");
    }

    private void startRiftAmbient(Player player) {
        UUID id = player.getUniqueId();
        if (riftAmbientTasks.containsKey(id)) return;
        riftAmbientTasks.put(id, plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Player online = plugin.getServer().getPlayer(id);
            if (online == null) {
                stopRiftAmbient(id);
                return;
            }
            PlayerQuestData data = waveData(id);
            if (data.waveStarted()) {
                stopRiftAmbient(id);
                return;
            }
            Rift rift = rifts.get(id);
            if (rift == null) {
                stopRiftAmbient(id);
                return;
            }
            World w = plugin.getServer().getWorld(data.waveWorld());
            if (w == null || !w.equals(online.getWorld())) {
                if (riftAmbientPlaying.contains(id)) {
                    config.stopSound(online, "sounds.rift-ambient");
                    riftAmbientPlaying.remove(id);
                }
                riftAmbientElapsedSeconds.remove(id);
                return;
            }
            double cx = data.waveChunkX() * 16.0 + 8.0;
            double cz = data.waveChunkZ() * 16.0 + 8.0;
            double dx = online.getLocation().getX() - cx;
            double dz = online.getLocation().getZ() - cz;
            double dist2 = dx * dx + dz * dz;
            if (dist2 <= 64.0 * 64.0) {
                if (!riftAmbientPlaying.contains(id)) {
                    config.playSound(online, "sounds.rift-ambient");
                    riftAmbientPlaying.add(id);
                    riftAmbientElapsedSeconds.put(id, 0);
                } else {
                    int elapsed = riftAmbientElapsedSeconds.merge(id, 1, Integer::sum);
                    int interval = Math.max(1, config.integer("sounds.rift-ambient-interval-seconds", 8));
                    if (elapsed >= interval) {
                        config.stopSound(online, "sounds.rift-ambient");
                        config.playSound(online, "sounds.rift-ambient");
                        riftAmbientElapsedSeconds.put(id, 0);
                    }
                }
            } else {
                if (riftAmbientPlaying.contains(id)) {
                    config.stopSound(online, "sounds.rift-ambient");
                    riftAmbientPlaying.remove(id);
                }
                riftAmbientElapsedSeconds.remove(id);
            }
        }, 0L, 20L));
    }

    private void stopRiftAmbient(UUID id) {
        BukkitTask task = riftAmbientTasks.remove(id);
        if (task != null) task.cancel();
        if (riftAmbientPlaying.remove(id)) {
            Player online = plugin.getServer().getPlayer(id);
            if (online != null) config.stopSound(online, "sounds.rift-ambient");
        }
        riftAmbientElapsedSeconds.remove(id);
    }

    public void reset(Player player) {
        UUID id = player.getUniqueId();
        cleanupRuntime(id);
        PlayerQuestData data = quests.data(id);
        data.calibrationAttempt(data.calibrationAttempt() + 1); data.stageSlots(Collections.emptyMap()); data.stageClicked(Collections.emptySet());
        quests.reset(id); player.closeInventory(); player.sendMessage(config.message("test-reset"));
    }

    /**
     * Stops the wave that is currently running for this player and releases everything it spawned,
     * without deleting the quest: the quest goes back to CALIBRATED so it can be started again.
     * This is the escape hatch for a wave that got stuck (e.g. its mobs were removed without dying).
     */
    public void cancel(Player player) {
        UUID id = player.getUniqueId();
        boolean standalone = standaloneRuns.containsKey(id);
        cleanupRuntime(id);
        if (standalone || quests.cancelRun(id)) {
            player.sendMessage(config.message("test-cancel"));
        } else {
            player.sendMessage(config.message("test-cancel-idle"));
        }
    }

    /** Removes every runtime object a quest spawned and releases its forced chunk. */
    public void cleanupQuestRuntime(Player player) { cleanupRuntime(player.getUniqueId()); }

    public void cleanupQuestRuntime(UUID id) { cleanupRuntime(id); }

    private void cleanupRuntime(UUID id) {
        cancelWaveSearch(id);
        cancelScheduledWave(id);
        stopBattleSound(id);
        stopRiftAmbient(id);
        List<UUID> ids = mobs.remove(id); alive.remove(id);
        if (ids != null) for (UUID entityId : ids) {
            waveMobRiftCenters.remove(entityId);
            Entity entity = plugin.getServer().getEntity(entityId);
            if (entity != null) entity.remove();
        }
        forgetAppliedEffects(ids);
        Rift rift = rifts.remove(id);
        if (rift != null) {
            playRiftCloseSound(id);
            rift.despawn();
        }
        cancelParticleTask(id); emitters.remove(id);
        PlayerQuestData data = waveData(id);
        releaseWaveChunk(data, id);
        offlineWaves.remove(id);
        standaloneRuns.remove(id);
    }

    /**
     * Returns the target's currently active quest to AVAILABLE and cleans up anything it spawned,
     * leaving the other weekly quests untouched. Returns false when nothing is active.
     */
    public boolean resetActiveQuest(Player target) {
        UUID id = target.getUniqueId();
        cleanupRuntime(id);
        boolean reset = quests.resetActiveQuest(id);
        if (reset) target.closeInventory();
        return reset;
    }

    /**
     * Pauses per-player runtime state when the player leaves. Wave mobs and their particle emitters stay
     * tracked so relogging resumes the same wave; the rift is despawned and recreated on resume so it
     * cannot be stranded in the world while nobody is playing.
     */
    public void quit(Player player) {
        UUID id = player.getUniqueId();
        cancelWaveSearch(id);
        if (standaloneRuns.containsKey(id)) { cleanupRuntime(id); return; }
        if (alive.containsKey(id)) offlineWaves.put(id, quests.data(id));
        cancelScheduledWave(id);
        stopBattleSound(id);
        stopRiftAmbient(id);
        cancelParticleTask(id);
        Rift rift = rifts.remove(id);
        if (rift != null) rift.despawn();
    }

    public void cleanup() {
        for (UUID id : List.copyOf(chunkSearches.keySet())) cancelWaveSearch(id);
        if (chunkSelector != null) chunkSelector.cancelAll();
        for (UUID id : new HashSet<>(standaloneRuns.keySet())) cleanupRuntime(id);
        scheduledWaves.values().forEach(BukkitTask::cancel);
        scheduledWaves.clear();
        waveScheduleVersions.clear();
        rifts.values().forEach(Rift::despawnNow);
        testRifts.forEach(Rift::despawnNow);
        particleTasks.values().forEach(BukkitTask::cancel);
        for (UUID id : new HashSet<>(battlePlaying)) stopBattleSound(id);
        battleTasks.values().forEach(BukkitTask::cancel);
        battleTasks.clear();
        battleElapsedSeconds.clear();
        for (UUID id : new HashSet<>(riftAmbientPlaying)) stopRiftAmbient(id);
        riftAmbientTasks.values().forEach(BukkitTask::cancel);
        riftAmbientTasks.clear();
        riftAmbientPlaying.clear();
        riftAmbientElapsedSeconds.clear();
        if (chunkOwnership != null) observeOwnership(chunkOwnership.close());
        offlineWaves.clear();
        rifts.clear(); particleTasks.clear(); emitters.clear(); alive.clear(); mobs.clear(); waveMobRiftCenters.clear(); occupancy.clear();
        battlePlaying.clear();
        appliedEffects.clear();
        testRifts.clear();
    }

    /** Forgets the effects applied to the given mobs — they died, were removed or their wave ended. */
    private void forgetAppliedEffects(List<UUID> mobIds) {
        if (mobIds == null) return;
        for (UUID mobId : mobIds) appliedEffects.remove(mobId);
    }

    /** Drops the tracking of mobs that are gone and not part of a wave any more (removed without a death event). */
    private void pruneAppliedEffects() {
        Set<UUID> tracked = new HashSet<>();
        for (List<UUID> list : mobs.values()) tracked.addAll(list);
        appliedEffects.keySet().removeIf(id -> !tracked.contains(id) && plugin.getServer().getEntity(id) == null);
    }

    CompletableFuture<Boolean> forceLoadWaveChunk(PlayerQuestData data) {
        if (data.waveWorld() == null) return CompletableFuture.completedFuture(false);
        ChunkKey chunk = new ChunkKey(data.waveWorld(), data.waveChunkX(), data.waveChunkZ());
        if (chunkOwnership == null) return CompletableFuture.failedFuture(new IllegalStateException("Chunk ownership is not configured"));
        return chunkOwnership.acquire(chunk);
    }

    /**
     * Gives the chunk of the wave back: it is marked as free and unloaded, unless the wave of
     * another player still runs in it — then the chunk has to stay loaded.
     */
    void releaseWaveChunk(PlayerQuestData data, UUID owner) {
        if (data.waveWorld() == null) return;
        ChunkKey chunk = new ChunkKey(data.waveWorld(), data.waveChunkX(), data.waveChunkZ());
        occupancy.unmark(chunk, owner);
        if (occupancy.isOccupied(chunk)) return;
        if (chunkOwnership != null) observeOwnership(chunkOwnership.release(chunk));
    }

    /** Remembers that the wave of the player runs in the given chunk. */
    void markOccupied(ChunkKey chunk, UUID owner) {
        occupancy.mark(chunk, owner);
    }

    /** The chunks the waves occupy and the players that use them. */
    ChunkOccupancy occupancy() {
        return occupancy;
    }

    private void observeOwnership(CompletableFuture<?> operation) {
        operation.whenComplete((ignored, error) -> {
            if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to update wave chunk ownership", error);
        });
    }

    private void cancelWaveSearch(UUID id) {
        CompletableFuture<Boolean> search = chunkSearches.remove(id);
        if (chunkSelector != null) chunkSelector.cancel(id);
        if (search != null) {
            if (quests.isLoaded(id)) releaseWaveChunk(quests.data(id), id);
            search.completeExceptionally(new java.util.concurrent.CancellationException("Wave search cancelled"));
        }
    }
}
