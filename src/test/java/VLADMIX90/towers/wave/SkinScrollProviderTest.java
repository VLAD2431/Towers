package VLADMIX90.towers.wave;

import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;

import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinScrollProviderTest {
    public interface ScrollPlugin extends Plugin {
        ItemStack createSkinScroll(String id);
    }

    private final PluginManager plugins = mock(PluginManager.class);
    private final Logger logger = mock(Logger.class);

    @Test
    void missingPluginWarnsAndSkips() {
        assertNull(new SkinScrollProvider(plugins, logger).create("blue_sword"));
        verify(logger).warning(contains("blue_sword"));
    }

    @Test
    void enabledPluginReturnsItsAuthenticItem() {
        ScrollPlugin plugin = mock(ScrollPlugin.class);
        ItemStack item = mock(ItemStack.class);
        when(plugins.getPlugin("QUACK-by-VLADMIX90")).thenReturn(plugin);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.createSkinScroll("blue_sword")).thenReturn(item);
        assertSame(item, new SkinScrollProvider(plugins, logger).create("blue_sword"));
        verifyNoInteractions(logger);
    }

    @Test
    void disabledPluginIsNotCalled() {
        ScrollPlugin plugin = mock(ScrollPlugin.class);
        when(plugins.getPlugin("QUACK-by-VLADMIX90")).thenReturn(plugin);
        assertNull(new SkinScrollProvider(plugins, logger).create("blue_sword"));
        verify(plugin, never()).createSkinScroll(anyString());
    }

    @Test
    void oldPluginWithoutApiWarnsAndSkips() {
        Plugin plugin = mock(Plugin.class);
        when(plugins.getPlugin("QUACK-by-VLADMIX90")).thenReturn(plugin);
        when(plugin.isEnabled()).thenReturn(true);
        assertNull(new SkinScrollProvider(plugins, logger).create("blue_sword"));
        verify(logger).warning(contains("blue_sword"));
    }

    @Test
    void unknownSkinWarnsAndSkips() {
        ScrollPlugin plugin = mock(ScrollPlugin.class);
        when(plugins.getPlugin("QUACK-by-VLADMIX90")).thenReturn(plugin);
        when(plugin.isEnabled()).thenReturn(true);
        assertNull(new SkinScrollProvider(plugins, logger).create("unknown"));
        verify(logger).warning(contains("unknown"));
    }

    @Test
    void apiFailureWarnsAndSkips() {
        ScrollPlugin plugin = mock(ScrollPlugin.class);
        when(plugins.getPlugin("QUACK-by-VLADMIX90")).thenReturn(plugin);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.createSkinScroll("broken")).thenThrow(new IllegalStateException("broken config"));
        assertNull(new SkinScrollProvider(plugins, logger).create("broken"));
        verify(logger).warning(contains("broken"));
    }
}
