package VLADMIX90.towers;

import org.bukkit.plugin.java.JavaPlugin;
import VLADMIX90.towers.command.TowersCommand;
import VLADMIX90.towers.command.TowersTabCompleter;
import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.display.CustomDisplayManager;
import VLADMIX90.towers.listener.DisplayChunkLoadListener;
import VLADMIX90.towers.listener.DisplayInteractListener;
import VLADMIX90.towers.listener.MenuClickListener;
import VLADMIX90.towers.menu.MenuManager;
import VLADMIX90.towers.quest.QuestManager;
import VLADMIX90.towers.game.GameManager;
import VLADMIX90.towers.listener.GameListener;
import VLADMIX90.towers.listener.PlayerDataListener;
import VLADMIX90.towers.listener.WaveFireListener;
import VLADMIX90.towers.wave.WaveConfig;
import VLADMIX90.towers.quest.PlayerDataStorage;
import VLADMIX90.towers.storage.SqliteDatabase;
import VLADMIX90.towers.storage.LegacyYamlMigrator;
import VLADMIX90.towers.storage.StartupState;
import VLADMIX90.towers.util.Text;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;

public final class Towers extends JavaPlugin {
    private CustomDisplayManager displays;
    private QuestManager quests;
    private GameManager game;
    private SqliteDatabase database;
    private final StartupState startup = new StartupState();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        ConfigManager config = new ConfigManager(this);
        WaveConfig waves = new WaveConfig(getConfig(), getLogger());
        if (getCommand("towers") != null) getCommand("towers").setExecutor((sender, command, label, args) -> {
            sender.sendMessage(Text.parse("<yellow>Towers: хранилище ещё не готово."));
            return true;
        });
        database = new SqliteDatabase(getDataFolder().toPath().resolve("database.db"));
        PlayerDataStorage storage = new PlayerDataStorage(database);
        var displayStorage = new VLADMIX90.towers.storage.DisplayStorage(database);
        var forcedStorage = new VLADMIX90.towers.storage.ForcedChunkStorage(database);
        Executor main = action -> {
            if (startup.phase() == StartupState.Phase.STOPPING) throw new CancellationException("Towers is stopping");
            getServer().getScheduler().runTask(this, () -> {
                if (startup.phase() != StartupState.Phase.STOPPING) action.run();
            });
        };
        database.initialize().thenCompose(ignored -> new LegacyYamlMigrator(database, getDataFolder().toPath()).migrate())
                .thenCompose(ignored -> storage.loadSchedule().thenCombine(displayStorage.loadAll(),
                         (schedule, records) -> new StartupData(schedule, records, java.util.Set.of()))
                         .thenCombine(forcedStorage.loadAll(), (loaded, chunks) -> new StartupData(loaded.schedule(), loaded.displays(), chunks))).whenComplete((loaded, failure) -> {
                    if (startup.phase() == StartupState.Phase.STOPPING) return;
                    try {
                        main.execute(() -> {
                            if (failure != null) {
                                startup.fail();
                                getLogger().log(java.util.logging.Level.SEVERE, "Towers storage startup failed; gameplay remains blocked", failure);
                                return;
                            }
                            try {
                                storage.acceptSchedule(loaded.schedule());
                                 startGameplay(config, waves, storage, main, displayStorage, loaded.displays(), forcedStorage, loaded.chunks());
                            } catch (RuntimeException error) {
                                startup.fail();
                                getLogger().log(java.util.logging.Level.SEVERE, "Towers gameplay initialization failed", error);
                                getServer().getPluginManager().disablePlugin(this);
                            }
                        });
                    } catch (CancellationException ignored) { }
                });
    }

    private record StartupData(PlayerDataStorage.Schedule schedule, java.util.List<VLADMIX90.towers.storage.DisplayRecord> displays,
                               java.util.Set<VLADMIX90.towers.wave.ChunkKey> chunks) { }

    private void startGameplay(ConfigManager config, WaveConfig waves, PlayerDataStorage storage, Executor main,
                               VLADMIX90.towers.storage.DisplayStorage displayStorage,
                                java.util.List<VLADMIX90.towers.storage.DisplayRecord> records,
                                VLADMIX90.towers.storage.ForcedChunkStorage forcedStorage,
                                java.util.Set<VLADMIX90.towers.wave.ChunkKey> staleChunks) {
        quests = new QuestManager(this, waves, storage, main);
        displays = new CustomDisplayManager(this, config, displayStorage, records);
        MenuManager menus = new MenuManager(config, quests, displays);
        var ownership = new VLADMIX90.towers.storage.ForcedChunkOwnership(forcedStorage::add, forcedStorage::remove,
                new VLADMIX90.towers.storage.ForcedChunkOwnership.WorldAccess() {
                    public boolean available(VLADMIX90.towers.wave.ChunkKey key) { return getServer().getWorld(key.world()) != null; }
                    public boolean forced(VLADMIX90.towers.wave.ChunkKey key) {
                        return getServer().getWorld(key.world()).isChunkForceLoaded(key.x(), key.z());
                    }
                    public void force(VLADMIX90.towers.wave.ChunkKey key, boolean value) {
                        getServer().getWorld(key.world()).setChunkForceLoaded(key.x(), key.z(), value);
                    }
                }, main);
        observeRecovery(ownership.recover(staleChunks));
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onWorldLoad(org.bukkit.event.world.WorldLoadEvent event) { observeRecovery(ownership.worldLoaded(event.getWorld().getUID())); }
        }, this);
        game = new GameManager(this, config, quests, menus, displays, waves, ownership, main);
        getServer().getScheduler().runTaskTimer(this, () -> game.tickWaveSearches(), 1L, 1L);
        quests.runtimeCleanup(id -> {
            game.cleanupQuestRuntime(id);
        });
        getServer().getPluginManager().registerEvents(new DisplayInteractListener(displays, menus, game, quests), this);
        getServer().getPluginManager().registerEvents(new DisplayChunkLoadListener(displays), this);
        getServer().getPluginManager().registerEvents(new MenuClickListener(quests, menus, game), this);
        getServer().getPluginManager().registerEvents(new GameListener(game), this);
        getServer().getPluginManager().registerEvents(new WaveFireListener(), this);
        PlayerDataListener players = new PlayerDataListener(game, quests);
        getServer().getPluginManager().registerEvents(players, this);
        getServer().getScheduler().runTaskTimer(this, () -> quests.refreshOnline(config.integer("weekly-quest-count", 3)), 20L, 20L * 60L);
        if (getCommand("towers") != null) {
            getCommand("towers").setExecutor(new TowersCommand(config, displays, game, menus, quests));
            getCommand("towers").setTabCompleter(new TowersTabCompleter(game));
        }
        if (startup.markReady()) getServer().getOnlinePlayers().forEach(player -> players.load(player.getUniqueId()));
    }

    @Override
    public void onDisable() {
        startup.stop();
        if (game != null) game.cleanup();
        if (displays != null) displays.removeAll();
        CompletableFuture<Void> saved = quests == null ? CompletableFuture.completedFuture(null) : quests.closeAsync();
        CompletableFuture<Void> closed = database == null ? saved : saved.handle((ignored, failure) -> {
            if (failure != null) getLogger().log(java.util.logging.Level.SEVERE, "Failed to save final quest snapshots", failure);
            return null;
        }).thenCompose(ignored -> database.closeAsync());
        try { closed.get(10, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); getLogger().warning("Interrupted while closing Towers database"); }
        catch (Exception error) { getLogger().log(java.util.logging.Level.SEVERE, "Towers database did not close cleanly within 10 seconds", error); }
    }

    private void observeRecovery(CompletableFuture<Void> recovery) {
        recovery.whenComplete((ignored, failure) -> {
            if (failure != null) getLogger().log(java.util.logging.Level.SEVERE, "Failed to remove recovered chunk ownership rows", failure);
        });
    }
}
