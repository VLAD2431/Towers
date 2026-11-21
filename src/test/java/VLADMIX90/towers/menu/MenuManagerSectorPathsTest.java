package VLADMIX90.towers.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MenuManagerSectorPathsTest {

    @Test
    void computerPreviewUsesPreviewKeys() {
        assertEquals("menus.chunk-map.preview-computer-name", MenuManager.sectorNamePath(true, false, MenuManager.ChunkMapView.PREVIEW));
        assertEquals("menus.chunk-map.preview-computer-lore", MenuManager.sectorLorePath(true, false, MenuManager.ChunkMapView.PREVIEW));
    }

    @Test
    void computerNormalKeepsComputerKeys() {
        assertEquals("menus.chunk-map.computer-name", MenuManager.sectorNamePath(true, false, MenuManager.ChunkMapView.READY));
        assertEquals("menus.chunk-map.computer-lore", MenuManager.sectorLorePath(true, false, MenuManager.ChunkMapView.READY));
    }

    @Test
    void foundBeatsPreview() {
        assertEquals("menus.chunk-map.found-name", MenuManager.sectorNamePath(false, true, MenuManager.ChunkMapView.PREVIEW));
        assertEquals("menus.chunk-map.found-lore", MenuManager.sectorLorePath(false, true, MenuManager.ChunkMapView.PREVIEW));
    }

    @Test
    void previewSector() {
        assertEquals("menus.chunk-map.preview-name", MenuManager.sectorNamePath(false, false, MenuManager.ChunkMapView.PREVIEW));
        assertEquals("menus.chunk-map.preview-lore", MenuManager.sectorLorePath(false, false, MenuManager.ChunkMapView.PREVIEW));
    }

    @Test
    void plainSector() {
        assertEquals("menus.chunk-map.sector-name", MenuManager.sectorNamePath(false, false, MenuManager.ChunkMapView.READY));
        assertEquals("menus.chunk-map.sector-lore", MenuManager.sectorLorePath(false, false, MenuManager.ChunkMapView.READY));
    }

    @Test
    void assignedUncalibratedPaths() {
        assertEquals("menus.chunk-map.assigned-computer-name", MenuManager.sectorNamePath(true, false, MenuManager.ChunkMapView.ASSIGNED_UNCALIBRATED));
        assertEquals("menus.chunk-map.assigned-computer-lore", MenuManager.sectorLorePath(true, false, MenuManager.ChunkMapView.ASSIGNED_UNCALIBRATED));
        assertEquals("menus.chunk-map.assigned-name", MenuManager.sectorNamePath(false, false, MenuManager.ChunkMapView.ASSIGNED_UNCALIBRATED));
        assertEquals("menus.chunk-map.assigned-lore", MenuManager.sectorLorePath(false, false, MenuManager.ChunkMapView.ASSIGNED_UNCALIBRATED));
    }
}
