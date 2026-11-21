package VLADMIX90.towers.wave;

import org.bukkit.Material;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

public final class WaveConfig {
    // The model is rebuilt in place; everything runs on the main thread, so no synchronization is needed.
    private int waves;
    private LinkedHashMap<String, WaveTypeSpec> types;
    private RiftSpec rift;
    private List<RiftLootSpec> loot;
    private double scrollChance;
    private Map<String, RiftLootSpec> lootById;
    private final Logger logger;
    private final Function<String, PotionEffectType> effectResolver;

    public WaveConfig(ConfigurationSection config) {
        this(config, Logger.getLogger("Towers"));
    }

    public WaveConfig(ConfigurationSection config, Logger logger) {
        this(config, logger, WaveConfig::resolveEffect);
    }

    public WaveConfig(ConfigurationSection config, Logger logger, Function<String, PotionEffectType> effectResolver) {
        this.logger = logger == null ? Logger.getLogger("Towers") : logger;
        this.effectResolver = effectResolver == null ? WaveConfig::resolveEffect : effectResolver;
        reload(config);
    }

    /**
     * Re-reads every wave setting from the config so a reload applies the new values in place.
     * The instance stays the same, so everyone holding it sees the new values.
     */
    public void reload(ConfigurationSection config) {
        waves = Math.max(1, config.getInt("chunk-area.waves", 3));
        types = parseTypes(config.getConfigurationSection("wave-types"));
        rift = parseRift(config.getConfigurationSection("rift"));
        loot = parseLoot(config.getMapList("rift-loot"));
        double configuredScrollChance = decimal(config.get("rift-scrolls.chance"), 50);
        scrollChance = Double.isFinite(configuredScrollChance)
                ? Math.max(0, Math.min(100, configuredScrollChance)) : 50;
        for (RiftLootSpec item : loot) {
            if (item.skinId() != null && item.guaranteedInCycle()) {
                logger.warning("Ignoring guaranteed-in-cycle for scroll loot '" + item.id()
                        + "': scrolls use the shared rift-scrolls.chance roll.");
            }
        }
        lootById = new LinkedHashMap<>();
        for (RiftLootSpec item : loot) lootById.put(item.id(), item);
    }

    public int waves() { return waves; }
    public int wavesForType(String typeId) {
        WaveTypeSpec type = types.get(typeId);
        int count = type == null ? waves : type.waves();
        return count <= 0 ? waves : count;
    }
    public List<String> typeIds() { return List.copyOf(types.keySet()); }
    public WaveTypeSpec type(String id) { return types.get(id); }
    public RiftSpec rift() { return rift; }
    public List<RiftLootSpec> loot() { return loot; }
    public double scrollChance() { return scrollChance; }
    public List<RiftLootSpec> lootForType(String typeId) {
        WaveTypeSpec type = types.get(typeId);
        if (type == null || type.lootIds().isEmpty()) return loot;
        List<RiftLootSpec> result = new ArrayList<>();
        for (String id : type.lootIds()) {
            RiftLootSpec item = lootById.get(id);
            if (item != null) {
                result.add(item);
            } else {
                logger.warning("Неизвестный ID предмета '" + id + "' в wave-types." + typeId + ".loot — пропущен.");
            }
        }
        return List.copyOf(result);
    }

    public String rollType(Random random) {
        if (types.isEmpty()) return null;
        int total = types.values().stream().mapToInt(t -> Math.max(0, t.chance())).sum();
        if (total <= 0) return types.keySet().iterator().next();
        int roll = random.nextInt(total);
        for (WaveTypeSpec type : types.values()) {
            roll -= Math.max(0, type.chance());
            if (roll < 0) return type.id();
        }
        return types.keySet().iterator().next();
    }

    public Map<String, String> assignGuaranteed(List<UUID> questIds, Random random) {
        if (questIds.isEmpty()) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (RiftLootSpec item : loot) {
            if (item.skinId() == null && item.guaranteedInCycle()) {
                result.put(item.id(), questIds.get(random.nextInt(questIds.size())).toString());
            }
        }
        return result;
    }

    private LinkedHashMap<String, WaveTypeSpec> parseTypes(ConfigurationSection section) {
        LinkedHashMap<String, WaveTypeSpec> result = new LinkedHashMap<>();
        if (section == null) return result;
        for (String id : section.getKeys(false)) {
            ConfigurationSection type = section.getConfigurationSection(id);
            if (type == null) continue;
            ConfigurationSection mobs = type.getConfigurationSection("mobs");
            List<WaveMobSpec> specs = new ArrayList<>();
            List<String> knownMobs = new ArrayList<>();
            List<String> unknownMobs = new ArrayList<>();
            if (mobs != null) {
                for (String name : mobs.getKeys(false)) {
                    EntityType entityType = null;
                    String customId = null;
                    if (name.toLowerCase(Locale.ROOT).startsWith("custom:")) {
                        customId = name.substring("custom:".length()).toLowerCase(Locale.ROOT);
                        if (customId.isBlank()) { unknownMobs.add(name); continue; }
                    } else {
                        try { entityType = EntityType.valueOf(name.toUpperCase(Locale.ROOT)); }
                        catch (IllegalArgumentException exception) { unknownMobs.add(name); continue; }
                    }
                    ConfigurationSection mob = mobs.getConfigurationSection(name);
                    if (mob == null) {
                        logger.warning("Моб '" + name + "' в wave-types (" + id + ") не настроен — пропущен.");
                        continue;
                    }
                    List<Integer> values = mob.getIntegerList("counts");
                    int[] counts = values.stream().mapToInt(Integer::intValue).toArray();
                    knownMobs.add(name);
                    specs.add(new WaveMobSpec(entityType, customId, counts, parseEffects(mob.getMapList("effects")),
                            parseParticles(mob.getMapList("particles"))));
                }
            }
            for (String unknown : unknownMobs) {
                logger.warning("Неизвестный моб '" + unknown + "' в wave-types (" + id + ") — моб пропущен. Мобы этой волны: "
                        + (knownMobs.isEmpty() ? "—" : String.join(", ", knownMobs)));
            }
            result.put(id, new WaveTypeSpec(id, type.getInt("chance", 0), List.copyOf(specs),
                    type.getStringList("loot"), type.getInt("waves", waves)));
        }
        return result;
    }

    /**
     * Parses the effect entries and resolves their type by name.
     * Unknown names are reported to {@code onUnknown} and skipped.
     *
     * <p>Generic over the resolved type so the parsing itself needs no potion registry and can be
     * unit tested with a fake resolver.
     */
    static <T> List<EffectEntry<T>> parseEffectEntries(List<Map<?, ?>> maps, Function<String, T> resolver, Consumer<String> onUnknown) {
        List<EffectEntry<T>> result = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            String name = String.valueOf(map.get("type"));
            T type = resolver.apply(name);
            if (type == null) { onUnknown.accept(name); continue; }
            result.add(new EffectEntry<>(type, number(map.get("level"), 1), number(map.get("duration"), -1)));
        }
        return List.copyOf(result);
    }

    /** One parsed effect entry: the resolved effect type plus the configured level and duration. */
    record EffectEntry<T>(T type, int level, int duration) { }

    private List<MobEffectSpec> parseEffects(List<Map<?, ?>> maps) {
        List<MobEffectSpec> result = new ArrayList<>();
        List<EffectEntry<PotionEffectType>> entries = parseEffectEntries(maps, effectResolver, this::warnUnknownEffect);
        for (EffectEntry<PotionEffectType> entry : entries) {
            result.add(new MobEffectSpec(entry.type(), entry.level(), entry.duration()));
        }
        return List.copyOf(result);
    }

    /** Reports an effect name that the server does not know; the hint with the valid names is optional. */
    private void warnUnknownEffect(String name) {
        String hint = knownEffectNames();
        logger.warning("Неизвестный эффект '" + name + "' в wave-types — эффект пропущен."
                + (hint.isEmpty() ? "" : " Допустимые: " + hint));
    }

    private List<MobParticleSpec> parseParticles(List<Map<?, ?>> maps) {
        List<MobParticleSpec> result = new ArrayList<>();
        for (Map<?, ?> map : maps) {
            Particle type;
            String name = String.valueOf(map.get("type"));
            try { type = Particle.valueOf(name.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException | NullPointerException exception) {
                logger.warning("Неизвестная частица '" + name + "' в wave-types — частица пропущена.");
                continue;
            }
            float size = (float) Math.max(0.001, decimal(map.get("size"), 1.0));
            result.add(new MobParticleSpec(type, Math.max(1, number(map.get("count"), 1)),
                    decimal(map.get("spread"), 0), Math.max(1, number(map.get("interval"), 1)),
                    Math.max(0, decimal(map.get("speed"), 0)), particleData(type, map, size)));
        }
        return List.copyOf(result);
    }

    /** Dust data for color particles; null when colors are absent/invalid or the particle has no color data. */
    private static Object particleData(Particle type, Map<?, ?> map, float size) {
        Color from = color(map.get("from-color"));
        if (type == Particle.DUST_COLOR_TRANSITION) {
            Color to = color(map.get("to-color"));
            return from != null && to != null ? new Particle.DustTransition(from, to, size) : null;
        }
        if (type == Particle.DUST && from != null) return new Particle.DustOptions(from, size);
        return null;
    }

    /** Parses "#RRGGBB"; anything else yields null. */
    private static Color color(Object value) {
        if (value == null) return null;
        String hex = String.valueOf(value).trim();
        if (hex.length() != 7 || hex.charAt(0) != '#') return null;
        try { return Color.fromRGB(Integer.parseInt(hex.substring(1), 16)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static final List<RiftFrame> DEFAULT_OPEN_FRAMES = List.of(
            new RiftFrame(0, 0f, 0f, 0.2f),
            new RiftFrame(2, 1f, 0f, 0.2f),
            new RiftFrame(4, 1f, 0.2f, 0.2f),
            new RiftFrame(7, 1f, 0.2f, 1f),
            new RiftFrame(10, 1f, 1f, 1f));
    private static final List<RiftFrame> DEFAULT_PULSE_FRAMES = List.of(
            new RiftFrame(0, 1f, 1f, 1f),
            new RiftFrame(2, 1f, 1.02f, 1.02f),
            new RiftFrame(4, 1.02f, 1.02f, 1f),
            new RiftFrame(6, 1f, 1f, 1f),
            new RiftFrame(8, 1f, 1.02f, 1.02f),
            new RiftFrame(10, 1.02f, 1.02f, 1f),
            new RiftFrame(12, 1f, 1f, 1f));
    private static final List<RiftFrame> DEFAULT_CLOSE_FRAMES = List.of(
            new RiftFrame(0, 1f, 1f, 1f),
            new RiftFrame(3, 1f, 0.2f, 1f),
            new RiftFrame(6, 1f, 0.2f, 0.2f),
            new RiftFrame(8, 1f, 0f, 0.2f),
            new RiftFrame(10, 0f, 0f, 0.2f));

    private RiftSpec parseRift(ConfigurationSection section) {
        if (section == null) {
            return new RiftSpec(Material.PAPER, "quack_gui:paper", "rift", 5f, 15, 64, 15, 15, 32,
                    RiftOpenMode.CHUNK, RiftCloud.DISABLED,
                    DEFAULT_OPEN_FRAMES, DEFAULT_PULSE_FRAMES, DEFAULT_CLOSE_FRAMES);
        }
        Material material = Material.matchMaterial(section.getString("material", "PAPER"));
        float scale = (float) section.getDouble("scale", 5);
        return new RiftSpec(material == null ? Material.PAPER : material,
                section.getString("item-model", "quack_gui:paper"),
                section.getString("custom-model-data", "rift"),
                scale, section.getInt("height", 15), Math.max(1, section.getInt("view-distance", 64)),
                light(section.getInt("block-light", 15)), light(section.getInt("sky-light", 15)),
                Math.max(0, section.getInt("open-distance", 32)),
                openMode(section.getString("open-mode", "chunk")),
                cloud(section.getConfigurationSection("cloud"), scale),
                frames(section, "open-frames", DEFAULT_OPEN_FRAMES),
                frames(section, "pulse-frames", DEFAULT_PULSE_FRAMES),
                frames(section, "close-frames", DEFAULT_CLOSE_FRAMES));
    }

    /** When the rift opens; an unknown name keeps the rift sleeping in its chunk. */
    private RiftOpenMode openMode(String name) {
        if (name == null) return RiftOpenMode.CHUNK;
        try {
            return RiftOpenMode.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            logger.warning("Неизвестный режим открытия разлома '" + name + "' — разлом спит, пока игрок не войдёт в его чанк.");
            return RiftOpenMode.CHUNK;
        }
    }

    /** The particle cloud of the rift; the legacy single-particle form is still accepted. */
    private RiftCloud cloud(ConfigurationSection section, float scale) {
        if (section == null) return RiftCloud.DISABLED;
        int interval = Math.max(1, section.getInt("interval", 4));
        int viewDistance = Math.max(1, section.getInt("view-distance", 128));
        List<RiftParticleSpec> particles = new ArrayList<>();
        if (section.contains("particles")) {
            for (Map<?, ?> map : section.getMapList("particles")) {
                RiftParticleSpec spec = cloudParticle(map, scale);
                if (spec != null) particles.add(spec);
            }
        } else {
            RiftParticleSpec legacy = legacyCloudParticle(section, scale);
            if (legacy != null) particles.add(legacy);
        }
        return new RiftCloud(particles, interval, viewDistance);
    }

    private RiftParticleSpec cloudParticle(Map<?, ?> map, float scale) {
        Particle type = particle(map.get("type"), "rift.cloud");
        if (type == null) return null;
        int count = Math.max(0, number(map.get("count"), 1));
        if (count == 0) return null;
        double fallback = Math.max(0, scale);
        float size = (float) Math.max(0.001, decimal(map.get("size"), 1.0));
        return new RiftParticleSpec(type, count,
                Math.max(0, decimal(map.get("offset-x"), fallback)),
                Math.max(0, decimal(map.get("offset-y"), fallback)),
                Math.max(0, decimal(map.get("offset-z"), fallback)),
                Math.max(0, decimal(map.get("speed"), 0)),
                particleData(type, map, size));
    }

    private RiftParticleSpec legacyCloudParticle(ConfigurationSection section, float scale) {
        Particle type = particle(section.getString("type", "PORTAL"), "rift.cloud");
        if (type == null) return null;
        int count = Math.max(0, section.getInt("count", 30));
        if (count == 0) return null;
        double radius = Math.max(0, section.getDouble("radius", scale));
        return new RiftParticleSpec(type, count, radius, radius, radius, 0, null);
    }

    private Particle particle(Object value, String where) {
        String name = String.valueOf(value);
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            logger.warning("Неизвестная частица '" + name + "' в " + where + " — частица пропущена.");
            return null;
        }
    }

    /** Reads a frame list; a missing key falls back to {@code defaults}, an explicit empty list stays empty. */
    private static List<RiftFrame> frames(ConfigurationSection section, String key, List<RiftFrame> defaults) {
        if (!section.contains(key)) return defaults;
        List<RiftFrame> result = new ArrayList<>();
        for (Map<?, ?> map : section.getMapList(key)) {
            result.add(new RiftFrame(tick(map.get("tick")), factor(map.get("x")),
                    factor(map.get("y")), factor(map.get("z"))));
        }
        result.sort(Comparator.comparingInt(RiftFrame::tick));
        return List.copyOf(result);
    }

    private static int tick(Object value) {
        Object number = value == null ? 0 : value;
        try { return Math.max(0, (int) Math.round(Double.parseDouble(String.valueOf(number)))); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static float factor(Object value) {
        Object number = value == null ? 0 : value;
        try {
            float parsed = Float.parseFloat(String.valueOf(number));
            return Float.isFinite(parsed) ? Math.max(0, parsed) : 0;
        } catch (NumberFormatException ignored) { return 0; }
    }

    private static int light(int value) {
        return Math.max(0, Math.min(15, value));
    }

    private static List<RiftLootSpec> parseLoot(List<Map<?, ?>> maps) {
        List<RiftLootSpec> result = new ArrayList<>();
        int index = 0;
        for (Map<?, ?> map : maps) {
            String skinId = string(map.get("skin-id"), null);
            Object materialValue = map.containsKey("material") ? map.get("material") : "STONE";
            Material material = skinId != null ? Material.PAPER : Material.matchMaterial(String.valueOf(materialValue));
            if (material == null) { index++; continue; }
            String cmd = string(map.get("custom-model-data"), "");
            String id = string(map.get("id"), cmd.isBlank() ? "loot-" + index : cmd);
            int min = Math.max(1, number(map.get("min"), 1));
            int max = Math.max(min, number(map.get("max"), min));
            double chance = Math.max(0, Math.min(100, decimal(map.get("chance"), 0)));
            double weight = map.containsKey("weight") ? decimal(map.get("weight"), 0) : chance;
            if (!Double.isFinite(weight) || weight < 0) weight = 0;
            Object loreValue = map.get("lore");
            List<String> lore = loreValue instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
            result.add(new RiftLootSpec(id, material, alternatives(map.get("alternatives")),
                    string(map.get("item-model"), null),
                    cmd, string(map.get("name"), null), lore, min, max, chance,
                    Boolean.parseBoolean(String.valueOf(map.containsKey("guaranteed-in-cycle")
                            ? map.get("guaranteed-in-cycle") : false)),
                    Boolean.parseBoolean(String.valueOf(map.containsKey("replaces-all")
                            ? map.get("replaces-all") : false)),
                    skinId, weight));
            index++;
        }
        return List.copyOf(result);
    }

    /** Materials the drop may be rolled as; unknown names are ignored. */
    private static List<Material> alternatives(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Material> result = new ArrayList<>();
        for (Object entry : list) {
            Material material = Material.matchMaterial(String.valueOf(entry));
            if (material != null) result.add(material);
        }
        return List.copyOf(result);
    }

    /** Registry lookup by namespaced key; null when the effect name is unknown. Needs a running server. */
    private static PotionEffectType resolveEffect(String name) {
        if (name == null) return null;
        try {
            NamespacedKey key = NamespacedKey.fromString(name.trim().toLowerCase(Locale.ROOT));
            if (key == null) return null;
            return Registry.MOB_EFFECT.get(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Bukkit-style names of every known effect, e.g. SPEED, STRENGTH — used in the warning text. Needs a running server. */
    private String knownEffectNames() {
        List<String> names = new ArrayList<>();
        try {
            for (PotionEffectType type : Registry.MOB_EFFECT) names.add(type.getKey().getKey().toUpperCase(Locale.ROOT));
        } catch (Throwable ignored) {
            return ""; // the registry is unavailable outside a running server — the hint is optional
        }
        Collections.sort(names);
        return String.join(", ", names);
    }

    private static int number(Object value, int fallback) {
        return value instanceof Number n ? n.intValue() : fallback;
    }
    private static double decimal(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }
    private static String string(Object value, String fallback) { return value == null ? fallback : String.valueOf(value); }
}
