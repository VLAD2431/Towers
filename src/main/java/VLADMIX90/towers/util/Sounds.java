package VLADMIX90.towers.util;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.util.Locale;

/**
 * Resolves sound names from the config: the sounds of the game become Bukkit sounds, the sounds of
 * a resource pack stay a name, because the game does not know them.
 */
public final class Sounds {
    private Sounds() {}

    /**
     * Normalizes a configured sound name to the Bukkit constant form.
     * «block.note_block.bit», «minecraft:block.note_block.bit» and «block_note_block_bit»
     * all become «BLOCK_NOTE_BLOCK_BIT». Pure — needs no running server.
     */
    public static String canonical(String raw) {
        if (raw == null) return "";
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("minecraft:")) value = value.substring("minecraft:".length());
        return value.replace('.', '_').replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
    }

    /** Resolves the sound at runtime; null when the name is blank or unknown. Needs a running server. */
    public static Sound resolve(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String sound = raw.trim();
        Sound found = byResourcePath(sound);
        return found != null ? found : byConstantName(sound);
    }

    /**
     * The path of a namespaced sound: «Towers:Quest_Given» → «towers:quest_given».
     * Null when the name has no namespace or is not a valid sound path.
     *
     * <p>Custom sounds of a resource pack have no Bukkit sound, so they are played by this path.
     */
    public static String resourcePath(String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(Locale.ROOT);
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1) return null;
        String namespace = value.substring(0, colon);
        String path = value.substring(colon + 1);
        if (!namespace.matches("[a-z0-9_.-]+")) return null;
        if (!path.matches("[a-z0-9_./-]+")) return null;
        return namespace + ":" + path;
    }

    /**
     * True when the sound comes from a resource pack and not from the game itself: the sounds of the
     * game live in the namespace «minecraft» and have a Bukkit sound, the custom ones do not.
     */
    public static boolean isCustom(String raw) {
        if (raw == null) return false;
        String value = raw.trim();
        int colon = value.indexOf(':');
        if (colon <= 0) return false;
        return !value.substring(0, colon).equalsIgnoreCase("minecraft");
    }

    /** «block.note_block.bit» / «minecraft:block.note_block.bit» → the sound event itself. */
    private static Sound byResourcePath(String raw) {
        try {
            NamespacedKey key = NamespacedKey.fromString(raw.toLowerCase(Locale.ROOT));
            if (key == null) return null;
            return Registry.SOUNDS.get(key);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** «BLOCK_NOTE_BLOCK_BIT» → the Bukkit constant. */
    private static Sound byConstantName(String raw) {
        try {
            return Sound.valueOf(canonical(raw));
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
