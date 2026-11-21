package VLADMIX90.towers.listener;

import VLADMIX90.towers.display.CustomDisplayManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

/** Restores click zones that were in chunks unloaded while the plugin started. */
public final class DisplayChunkLoadListener implements Listener {
    private final CustomDisplayManager displays;

    public DisplayChunkLoadListener(CustomDisplayManager displays) {
        this.displays = displays;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        displays.restoreChunk(event.getChunk());
    }
}
