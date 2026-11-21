package VLADMIX90.towers.wave;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the geometry of the sectors of the chunk map: the computer stands in the middle one and
 * the sectors follow each other without a gap, so a chunk belongs to exactly one sector.
 */
class SectorGridTest {

    @Test
    void theCentreOfASectorIsTheChunkTheMapShows() {
        assertEquals(0, SectorGrid.center(0, 0, 20));
        assertEquals(20, SectorGrid.center(0, 1, 20));
        assertEquals(-40, SectorGrid.center(0, -2, 20));
    }

    @Test
    void theSectorsOfTheGridFollowEachOtherWithoutAGap() {
        // the sector of the computer holds the chunks -10..9, the next one starts at 10
        assertEquals(-10, SectorGrid.first(0, 0, 20));
        assertEquals(10, SectorGrid.first(0, 1, 20));
        assertEquals(-30, SectorGrid.first(0, -1, 20));
        assertEquals(30, SectorGrid.first(0, 2, 20));
        // a sector of five chunks holds -2..2, the next one starts at 3
        assertEquals(-2, SectorGrid.first(0, 0, 5));
        assertEquals(3, SectorGrid.first(0, 1, 5));
    }

    @Test
    void aChunkBelongsToOneSectorOnly() {
        assertTrue(SectorGrid.contains(0, 0, 0, 0, 0, 0, 20), "the computer is in the middle sector");
        assertTrue(SectorGrid.contains(19, 9, 0, 0, 1, 0, 20), "the chunk is in the sector to the east");
        assertFalse(SectorGrid.contains(20, 0, 0, 0, 0, 0, 20), "the chunk is not in the middle sector");
        assertFalse(SectorGrid.contains(-11, 0, 0, 0, 0, 0, 20), "the chunk is not in the middle sector");
        assertFalse(SectorGrid.contains(9, 0, 0, 0, 1, 0, 20), "the chunk is not in the sector to the east");
    }
}
