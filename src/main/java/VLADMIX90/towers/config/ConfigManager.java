package VLADMIX90.towers.config;

import net.kyori.adventure.text.Component;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import VLADMIX90.towers.util.Sounds;
import VLADMIX90.towers.util.Text;

public final class ConfigManager {
    private final JavaPlugin plugin;
    /** Sounds already resolved for the current config; cleared on every reload. */
    private final Map<String, Sound> soundCache = new HashMap<>();
    public ConfigManager(JavaPlugin plugin) { this.plugin = plugin; }
    public void reload() { plugin.reloadConfig(); soundCache.clear(); }
    public Material material(String path, Material fallback) {
        return material(path, null, fallback);
    }
    public Material material(String path, String fallbackPath, Material fallback) {
        String value = plugin.getConfig().getString(path);
        if (value == null && fallbackPath != null) value = plugin.getConfig().getString(fallbackPath);
        if (value == null) return fallback;
        Material material = Material.matchMaterial(value);
        return material == null ? fallback : material;
    }
    public String customModelData(String path, String fallback) { return customModelData(path, null, fallback); }
    public String customModelData(String path, String fallbackPath, String fallback) {
        String value = plugin.getConfig().getString(path);
        if (value == null && fallbackPath != null) value = plugin.getConfig().getString(fallbackPath);
        return value == null ? fallback : value;
    }
    public List<String> pluginList(String path) { return plugin.getConfig().getStringList(path); }
    public int integer(String path, int fallback) { return plugin.getConfig().getInt(path, fallback); }
    public double decimal(String path, double fallback) {
        return plugin.getConfig().contains(path) ? plugin.getConfig().getDouble(path) : fallback;
    }
    public String string(String path, String fallback) { return plugin.getConfig().getString(path, fallback); }
    public boolean bool(String path, boolean fallback) { return plugin.getConfig().getBoolean(path, fallback); }
    public String itemName(String path, String fallback) { return string(path, fallback); }
    public List<String> itemLore(String path, List<String> fallback) {
        return plugin.getConfig().contains(path) ? plugin.getConfig().getStringList(path) : fallback;
    }
    public String replace(String value, Map<String, ?> placeholders) {
        String result = value;
        for (Map.Entry<String, ?> entry : placeholders.entrySet())
            result = result.replace(entry.getKey(), String.valueOf(entry.getValue()));
        return result;
    }
    public List<String> replace(List<String> values, Map<String, ?> placeholders) {
        return values.stream().map(value -> replace(value, placeholders)).toList();
    }
    public Component message(String path) { return Text.parse(plugin.getConfig().getString("messages." + path, "")); }
    public Component message(String path, String placeholder, Object value) {
        return Text.parse(plugin.getConfig().getString("messages." + path, "").replace(placeholder, String.valueOf(value)));
    }
    /** Message of the path with every placeholder replaced by its value. */
    public Component message(String path, Map<String, ?> placeholders) {
        return Text.parse(replace(plugin.getConfig().getString("messages." + path, ""), placeholders));
    }

    /**
     * Sound configured at the path; null when the key is absent, blank or names an unknown sound.
     * The sound is resolved once per config version, so a typo is reported once and not on every click.
     */
    public Sound sound(String path) {
        if (soundCache.containsKey(path)) return soundCache.get(path);
        String raw = plugin.getConfig().getString(path, "");
        Sound sound = null;
        if (raw != null && !raw.isBlank()) {
            sound = Sounds.resolve(raw);
            if (sound == null) plugin.getLogger().warning("Неизвестный звук '" + raw + "' (" + path + ") — звук не воспроизведён.");
        }
        soundCache.put(path, sound);
        return sound;
    }
    public float soundVolume(String path, float fallback) { return (float) decimal(path, fallback); }
    public float soundPitch(String path, float fallback) { return (float) decimal(path, fallback); }

    /**
     * The name of the custom sound at the path; null when the key is absent, blank or names a sound
     * of the game. A custom sound comes from the resource pack and is played by its name, because
     * the game does not know it.
     */
    public String soundName(String path) {
        String raw = plugin.getConfig().getString(path, "");
        if (raw == null || raw.isBlank()) return null;
        if (!Sounds.isCustom(raw)) return null;
        String resource = Sounds.resourcePath(raw);
        if (resource == null) {
            plugin.getLogger().warning("Некорректное имя кастомного звука '" + raw + "' (" + path + ") — звук не воспроизведён.");
        }
        return resource;
    }

    /**
     * Plays the sound at the path: a custom sound of the resource pack by its name, a sound of the
     * game by its Bukkit sound. A blank or unknown sound is silently skipped.
     */
    public void playSound(Player player, String path) {
        if (player == null) return;
        String raw = plugin.getConfig().getString(path, "");
        if (raw == null || raw.isBlank()) return;
        float volume = soundVolume(path + "-volume", 1f);
        float pitch = soundPitch(path + "-pitch", 1f);
        String custom = soundName(path);
        if (custom != null) {
            player.playSound(player.getLocation(), custom, volume, pitch);
            return;
        }
        if (Sounds.isCustom(raw)) return; // the name of the custom sound is broken — the warning is already logged
        Sound sound = sound(path);
        if (sound == null) return;
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    /**
     * Stops the sound at the path for the player. Works for both custom resource-pack sounds and Bukkit sounds.
     */
    public void stopSound(Player player, String path) {
        if (player == null) return;
        String raw = plugin.getConfig().getString(path, "");
        if (raw == null || raw.isBlank()) return;
        String custom = soundName(path);
        if (custom != null) {
            player.stopSound(custom);
            return;
        }
        if (Sounds.isCustom(raw)) return;
        Sound sound = sound(path);
        if (sound == null) return;
        player.stopSound(sound);
    }
}
