package VLADMIX90.towers.wave;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Logger;

/** Optional QUACK API bridge; no QUACK classes are bundled or required. */
public final class SkinScrollProvider {
    private final PluginManager plugins;
    private final Logger logger;

    public SkinScrollProvider(PluginManager plugins, Logger logger) {
        this.plugins = plugins;
        this.logger = logger;
    }

    /** Must be called on the server thread. Missing/old plugins and unknown IDs are skipped. */
    public ItemStack create(String skinId) {
        Plugin plugin = plugins.getPlugin("QUACK-by-VLADMIX90");
        if (plugin == null || !plugin.isEnabled()) {
            return skip(skinId, "QUACK is not enabled");
        }
        try {
            Object result = plugin.getClass().getMethod("createSkinScroll", String.class).invoke(plugin, skinId);
            if (result instanceof ItemStack item) return item;
            return skip(skinId, "unknown skin ID or invalid API result");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Throwable cause = exception instanceof InvocationTargetException invocation
                    ? invocation.getTargetException() : exception;
            return skip(skinId, "QUACK API unavailable/failed: " + cause);
        }
    }

    private ItemStack skip(String skinId, String reason) {
        logger.warning("Skipping skin-scroll loot '" + skinId + "': " + reason);
        return null;
    }
}
