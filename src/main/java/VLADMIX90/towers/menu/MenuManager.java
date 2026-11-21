package VLADMIX90.towers.menu;

import VLADMIX90.towers.config.ConfigManager;
import VLADMIX90.towers.display.CustomDisplayManager;
import VLADMIX90.towers.model.PlayerQuestData;
import VLADMIX90.towers.quest.QuestManager;
import VLADMIX90.towers.util.ItemBuilder;
import VLADMIX90.towers.util.TimeFormat;
import VLADMIX90.towers.wave.SectorGrid;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;
import VLADMIX90.towers.util.Text;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MenuManager {
    public enum ChunkMapView {
        PREVIEW,
        ASSIGNED_UNCALIBRATED,
        READY
    }

    private static final List<Integer> CALIBRATION_SLOTS = List.of(3, 4, 5, 6, 7, 12, 13, 14, 15, 16);
    private static final List<Integer> TOWER_SLOTS = List.of(3, 4, 5, 11, 13, 15, 20, 21, 23, 24, 29, 31, 33, 39, 40, 41);
    /** The chunk-map menu always renders this many sector rows/columns; the wave area is clamped to it. */
    public static final int GRID_SIZE = 5;
    /** The size of a sector of the map in chunks; the wave runs in one of the sectors. */
    public static final int SECTOR_SIZE = 20;
    private final ConfigManager config;
    private final QuestManager quests;
    private final CustomDisplayManager displays;
    public MenuManager(ConfigManager config, QuestManager quests, CustomDisplayManager displays) {
        this.config = config; this.quests = quests; this.displays = displays;
    }

    public void openTower(Player player) {
        quests.refreshWeekly(player.getUniqueId(), config.integer("weekly-quest-count", 3));
        MenuHolder holder = new MenuHolder(MenuType.TOWER, false);
        Inventory inventory = Bukkit.createInventory(holder, menuSize("menus.tower.size", 54, 45), title("menus.tower.title"));
        holder.attach(inventory);
        Material paper = config.material("menus.tower.material", Material.PAPER);
        String itemModel = config.string("menus.tower.item-model", config.string("menus.item-model", "quack_gui:paper"));
        inventory.setItem(0, ItemBuilder.model(paper, config.customModelData("menus.tower.info-model", "tower_info"), itemModel,
                config.itemName("menus.tower.info-name", "<yellow>Еженедельные задания"),
                config.itemLore("menus.tower.info-lore", List.of("<gray>Каждую неделю доступны три вышки")),
                hideSlot0Tooltip()));
        long refreshAt = quests.refreshAt();
        updateTowerTimer(inventory, paper, itemModel, refreshAt);
        scheduleTowerTimerUpdate(inventory, paper, itemModel, refreshAt);
        boolean locked = quests.hasActiveQuest(player.getUniqueId());
        for (int i = 0; i < TOWER_SLOTS.size(); i++) {
            int towerId = i + 1;
            var quest = quests.tower(player.getUniqueId(), towerId);
            boolean active = quest.filter(q -> q.state() == VLADMIX90.towers.model.QuestState.CALIBRATING
                    || q.state() == VLADMIX90.towers.model.QuestState.CALIBRATED
                    || q.state() == VLADMIX90.towers.model.QuestState.WAVES_ACTIVE).isPresent();
            boolean available = quest.filter(q -> q.state() == VLADMIX90.towers.model.QuestState.AVAILABLE).isPresent() && !locked;
            String state = active ? "in-progress" : (available ? "available" : "unavailable");
            String model = config.customModelData("menus.tower.towers." + state, "tower_" + state);
            String name = config.itemName("menus.tower.towers." + state + "-name", available ? "<green>Вышка " + towerId : "<red>Вышка " + towerId);
            String defaultLore = active ? "<yellow>Задание уже выполняется" : (available ? "<yellow>Нажмите, чтобы взять задание" : "<gray>Задание недоступно");
            List<String> lore = config.itemLore("menus.tower.towers." + state + "-lore", List.of(defaultLore));
            Map<String, Object> placeholders = new LinkedHashMap<>();
            placeholders.put("{tower}", towerId);
            Location towerLocation = displays.towerLocation(towerId).orElse(null);
            placeholders.put("{x}", towerLocation == null ? "?" : towerLocation.getBlockX());
            placeholders.put("{z}", towerLocation == null ? "?" : towerLocation.getBlockZ());
            inventory.setItem(TOWER_SLOTS.get(i), ItemBuilder.model(paper, model, itemModel,
                    config.replace(name, placeholders), config.replace(lore, placeholders)));
        }
        player.openInventory(inventory);
    }

    private void updateTowerTimer(Inventory inventory, Material paper, String itemModel, long refreshAt) {
        long remaining = Math.max(0L, refreshAt - System.currentTimeMillis());
        String time = TimeFormat.humanize(remaining, config.string("menus.tower.central-time-format", "{days}д {hours}ч {minutes}м"));
        inventory.setItem(22, ItemBuilder.model(paper, config.customModelData("menus.tower.central-model", "tower_central"), itemModel,
                config.itemName("menus.tower.central-name", "<gold>Центральная вышка"),
                config.replace(config.itemLore("menus.tower.central-lore", List.of("<gray>Выберите доступную вышку")), Map.of("{time}", time))));
    }

    private void scheduleTowerTimerUpdate(Inventory inventory, Material paper, String itemModel, long refreshAt) {
        new BukkitRunnable() {
            @Override public void run() {
                if (updateOpenTower(inventory, paper, itemModel, refreshAt)) cancel();
            }
        }.runTaskTimer(JavaPlugin.getPlugin(VLADMIX90.towers.Towers.class), 20L, 20L);
    }

    boolean updateOpenTower(Inventory inventory, Material paper, String itemModel, long refreshAt) {
        if (inventory.getViewers().isEmpty()) return true;
        long currentRefreshAt = quests.refreshAt();
        if (currentRefreshAt != refreshAt) {
            for (var viewer : List.copyOf(inventory.getViewers())) viewer.closeInventory();
            return true;
        }
        updateTowerTimer(inventory, paper, itemModel, currentRefreshAt);
        return false;
    }

    public void openCalibration(Player player, PlayerQuestData data) {
        Inventory current = player.getOpenInventory().getTopInventory();
        boolean updating = current.getHolder() instanceof MenuHolder open
                && open.type() == MenuType.CALIBRATION && !open.preview();
        Inventory inventory;
        if (updating) {
            inventory = current;
        } else {
            MenuHolder holder = new MenuHolder(MenuType.CALIBRATION, false);
            inventory = Bukkit.createInventory(holder, menuSize("menus.calibration.size", 27, 18), title("menus.calibration.title"));
            holder.attach(inventory);
        }
        inventory.clear();
        Material paper = config.material("menus.calibration.material", Material.PAPER);
        String itemModel = config.string("menus.calibration.item-model", config.string("menus.item-model", "quack_gui:paper"));
        boolean hideTooltips = hideCalibrationTooltips();
        inventory.setItem(0, ItemBuilder.model(paper, config.customModelData("menus.calibration.slot0-model", "calibration_paper"), itemModel,
                config.itemName("menus.calibration.slot0-name", "<yellow>Калибровка"),
                config.itemLore("menus.calibration.slot0-lore", List.of("<gray>Найдите нужные бумаги")),
                hideSlot0Tooltip() || hideTooltips));

        List<String> stageModels = config.pluginList("menus.calibration.stage-models");
        String stageModel = stageModels.isEmpty() ? "calibration_stage_0" : stageModels.get(Math.min(data.calibrationStage(), stageModels.size() - 1));
        int stages = config.integer("calibration.stages", 3);
        Map<String, Object> placeholders = Map.of("{stage}", Math.min(data.calibrationStage() + 1, stages), "{stages}", stages);
        inventory.setItem(1, ItemBuilder.model(paper, stageModel, itemModel,
                config.replace(config.itemName("menus.calibration.stage-name", "<yellow>Этап {stage} из {stages}"), placeholders),
                config.replace(config.itemLore("menus.calibration.stage-lore", List.of("<gray>Текущий этап калибровки")), placeholders),
                hideTooltips));

        String realModel = config.customModelData("menus.calibration.real-model", "calibration_paper_real");
        String fakeModel = config.customModelData("menus.calibration.fake-model", "calibration_paper_fake");
        for (int slot : CALIBRATION_SLOTS) {
            Boolean real = data.stageSlots().get(slot);
            if (real == null) continue;
            if (real && data.stageClicked().contains(slot)) continue;
            inventory.setItem(slot, ItemBuilder.model(paper, real ? realModel : fakeModel, itemModel,
                    config.itemName(real ? "menus.calibration.real-name" : "menus.calibration.fake-name", real ? "<white>Бумага" : "<gray>Бумага"),
                    config.itemLore(real ? "menus.calibration.real-lore" : "menus.calibration.fake-lore", List.of("<gray>Нажмите на бумагу")),
                    hideTooltips));
        }
        if (!updating) player.openInventory(inventory);
    }

    public static List<Integer> calibrationSlots() { return CALIBRATION_SLOTS; }
    public static List<Integer> towerSlots() { return TOWER_SLOTS; }

    public void openCalibrationPreview(Player player) {
        MenuHolder holder = new MenuHolder(MenuType.CALIBRATION, true);
        Inventory inventory = Bukkit.createInventory(holder, menuSize("menus.calibration.size", 27, 18), title("menus.calibration.title"));
        holder.attach(inventory);
        Material paper = config.material("menus.calibration.material", Material.PAPER);
        String itemModel = config.string("menus.calibration.item-model", config.string("menus.item-model", "quack_gui:paper"));
        boolean hideTooltips = hideCalibrationTooltips();
        inventory.setItem(0, ItemBuilder.model(paper, config.customModelData("menus.calibration.slot0-model", "calibration_paper"), itemModel,
                config.itemName("menus.calibration.slot0-name", "<yellow>Калибровка"),
                config.itemLore("menus.calibration.slot0-lore", List.of("<gray>Найдите нужные бумаги")),
                hideSlot0Tooltip() || hideTooltips));
        List<String> stageModels = config.pluginList("menus.calibration.stage-models");
        String stageModel = stageModels.isEmpty() ? "calibration_stage_0" : stageModels.get(0);
        int stages = config.integer("calibration.stages", 3);
        Map<String, Object> placeholders = Map.of("{stage}", 1, "{stages}", stages);
        inventory.setItem(1, ItemBuilder.model(paper, stageModel, itemModel,
                config.replace(config.itemName("menus.calibration.stage-name", "<yellow>Этап {stage} из {stages}"), placeholders),
                config.replace(config.itemLore("menus.calibration.stage-lore", List.of("<gray>Текущий этап калибровки")), placeholders),
                hideTooltips));
        player.openInventory(inventory);
    }

    public void openChunkMap(Player player, boolean preview) {
        openChunkMap(player, preview ? ChunkMapView.PREVIEW : ChunkMapView.READY);
    }

    public void openChunkMap(Player player, ChunkMapView view) {
        PlayerQuestData data = quests.data(player.getUniqueId());
        openChunkMap(player, view, data.computerChunkX(), data.computerChunkZ());
    }

    public void openChunkMap(Player player, ChunkMapView view, int computerX, int computerZ) {
        if (view == null) view = ChunkMapView.PREVIEW;
        boolean previewFlag = view == ChunkMapView.PREVIEW;
        MenuHolder holder = new MenuHolder(MenuType.CHUNK_MAP, previewFlag);
        Inventory inventory = Bukkit.createInventory(holder, menuSize("menus.chunk-map.size", 54, 45), title("menus.chunk-map.title"));
        holder.attach(inventory);
        Material material = config.material("menus.chunk-map.material", Material.PAPER);
        String itemModel = config.string("menus.chunk-map.item-model", config.string("menus.item-model", "quack_gui:paper"));
        String infoModel = config.customModelData("menus.chunk-map.info-model", "chunk_map_info");
        PlayerQuestData data = quests.data(player.getUniqueId());
        int sectorSize = Math.max(1, config.integer("chunk-area.sector-size", SECTOR_SIZE));
        Map<String, Object> area = Map.<String, Object>of("{grid-size}", GRID_SIZE, "{sector-size}", sectorSize);
        inventory.setItem(0, ItemBuilder.model(material, infoModel, itemModel,
                config.itemName("menus.chunk-map.info-name", "<yellow>Определение чанка"),
                config.replace(config.itemLore("menus.chunk-map.info-lore",
                        List.of("<gray>Область: 5×5 секторов", "<gray>Сектор: 20×20 чанков")), area),
                hideSlot0Tooltip()));
        String sectorModel = config.customModelData("menus.chunk-map.sector-model", "chunk_sector");
        String foundModel = config.customModelData("menus.chunk-map.found-model", "chunk_sector_found");
        String computerModel = config.customModelData("menus.chunk-map.computer-model", "chunk_sector_computer");
        String previewModel = config.customModelData("menus.chunk-map.preview-model", "chunk_sector_preview");
        String previewComputerModel = config.customModelData("menus.chunk-map.preview-computer-model", "chunk_sector_computer_preview");
        String assignedModel = config.customModelData("menus.chunk-map.assigned-model", previewModel);
        String assignedComputerModel = config.customModelData("menus.chunk-map.assigned-computer-model", previewComputerModel);
        boolean hasWave = data.waveWorld() != null && data.computerWorld() != null && data.waveWorld().equals(data.computerWorld());
        int center = GRID_SIZE / 2;
        for (int row = 0; row < GRID_SIZE; row++) for (int column = 0; column < GRID_SIZE; column++) {
            int slot = (row * 9) + column + 2;
            int offsetX = column - center, offsetZ = row - center;
            int centerX = SectorGrid.center(computerX, offsetX, sectorSize);
            int centerZ = SectorGrid.center(computerZ, offsetZ, sectorSize);
            boolean computerSector = offsetX == 0 && offsetZ == 0;
            boolean found = !computerSector && hasWave
                    && SectorGrid.contains(data.waveChunkX(), data.waveChunkZ(), computerX, computerZ, offsetX, offsetZ, sectorSize);
            String sector = sectorLabel(row, column);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Центральный чанк: <white>" + centerX + ", " + centerZ);
            if (found) {
                lore.add("<gray>Сектор: <white>" + sector);
                lore.add("<yellow>Волна: <white>" + data.waveChunkX() + ", " + data.waveChunkZ());
            }
            String model;
            if (found) {
                model = foundModel;
            } else if (computerSector) {
                if (view == ChunkMapView.ASSIGNED_UNCALIBRATED) model = assignedComputerModel;
                else if (view == ChunkMapView.READY) model = computerModel;
                else model = previewComputerModel;
            } else {
                if (view == ChunkMapView.ASSIGNED_UNCALIBRATED) model = assignedModel;
                else if (view == ChunkMapView.READY) model = sectorModel;
                else model = previewModel;
            }
            String namePath = sectorNamePath(computerSector, found, view);
            String lorePath = sectorLorePath(computerSector, found, view);
            String fallbackName = computerSector
                    ? (view == ChunkMapView.PREVIEW ? "<red>Центральный сектор" : (view == ChunkMapView.ASSIGNED_UNCALIBRATED ? "<yellow>Центральный сектор" : "<gold>Сектор компьютера"))
                    : (found ? "<red>Сектор " + sector + " — волна" : "<green>Сектор " + sector);
            List<String> fallbackLore = found
                    ? lore
                    : (computerSector && view == ChunkMapView.PREVIEW
                            ? List.of("<red>Центральный чанк: <red>" + centerX + ", " + centerZ, "<red>Требуется калибровка")
                            : (computerSector && view == ChunkMapView.ASSIGNED_UNCALIBRATED
                                    ? List.of("<gray>Центральный чанк: <white>" + centerX + ", " + centerZ, "<yellow>Требуется калибровка")
                                    : List.of("<gray>Центральный чанк: <white>" + centerX + ", " + centerZ)));
            Map<String, Object> placeholders = Map.of(
                    "{sector}", sector,
                    "{x}", centerX, "{z}", centerZ,
                    "{wave-x}", data.waveChunkX(), "{wave-z}", data.waveChunkZ());
            inventory.setItem(slot, ItemBuilder.model(material, model, itemModel,
                    config.replace(config.itemName(namePath, fallbackName), placeholders),
                    config.replace(config.itemLore(lorePath, fallbackLore), placeholders)));
        }
        player.openInventory(inventory);
    }

    private boolean hideSlot0Tooltip() { return config.bool("menus.hide-slot0-tooltip", false); }
    private boolean hideCalibrationTooltips() { return config.bool("menus.calibration.hide-tooltips", false); }

    private int menuSize(String path, int fallback, int minimum) {
        int size = config.integer(path, fallback);
        if (size < minimum || size > 54 || size % 9 != 0) return fallback;
        return size;
    }

    static String sectorLabel(int row, int column) {
        return (char) ('A' + column) + Integer.toString(row + 1);
    }
    /** Config path of the sector item name; the computer sector text depends on the menu preview flag. */
    static String sectorNamePath(boolean computerSector, boolean found, boolean preview) {
        if (found) return "menus.chunk-map.found-name";
        ChunkMapView view = preview ? ChunkMapView.PREVIEW : ChunkMapView.READY;
        return sectorNamePath(computerSector, found, view);
    }

    static String sectorNamePath(boolean computerSector, boolean found, ChunkMapView view) {
        if (view == null) view = ChunkMapView.PREVIEW;
        if (found) return "menus.chunk-map.found-name";
        if (computerSector) {
            if (view == ChunkMapView.PREVIEW) return "menus.chunk-map.preview-computer-name";
            if (view == ChunkMapView.ASSIGNED_UNCALIBRATED) return "menus.chunk-map.assigned-computer-name";
            return "menus.chunk-map.computer-name";
        }
        if (view == ChunkMapView.PREVIEW) return "menus.chunk-map.preview-name";
        if (view == ChunkMapView.ASSIGNED_UNCALIBRATED) return "menus.chunk-map.assigned-name";
        return "menus.chunk-map.sector-name";
    }

    static String sectorLorePath(boolean computerSector, boolean found, boolean preview) {
        if (found) return "menus.chunk-map.found-lore";
        ChunkMapView view = preview ? ChunkMapView.PREVIEW : ChunkMapView.READY;
        return sectorLorePath(computerSector, found, view);
    }

    static String sectorLorePath(boolean computerSector, boolean found, ChunkMapView view) {
        if (view == null) view = ChunkMapView.PREVIEW;
        if (found) return "menus.chunk-map.found-lore";
        if (computerSector) {
            if (view == ChunkMapView.PREVIEW) return "menus.chunk-map.preview-computer-lore";
            if (view == ChunkMapView.ASSIGNED_UNCALIBRATED) return "menus.chunk-map.assigned-computer-lore";
            return "menus.chunk-map.computer-lore";
        }
        if (view == ChunkMapView.PREVIEW) return "menus.chunk-map.preview-lore";
        if (view == ChunkMapView.ASSIGNED_UNCALIBRATED) return "menus.chunk-map.assigned-lore";
        return "menus.chunk-map.sector-lore";
    }
    private Component title(String path) { return Text.parse(config.string(path, "<dark_gray>Towers")); }
}
