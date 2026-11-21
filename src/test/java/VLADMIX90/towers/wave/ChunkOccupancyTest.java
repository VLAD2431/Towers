package VLADMIX90.towers.wave;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks which chunks the waves occupy.
 *
 * <p>Every player is remembered separately: a chunk can host the waves of several players at the
 * same time and is only free again when the last wave leaves it.
 */
class ChunkOccupancyTest {

    private static final UUID WORLD = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final ChunkKey CHUNK = new ChunkKey(WORLD, 3, 3);
    private static final ChunkKey OTHER = new ChunkKey(WORLD, 4, 4);

    @Test
    void aFreeChunkIsNotOccupied() {
        assertFalse(new ChunkOccupancy().isOccupied(CHUNK));
    }

    @Test
    void aMarkedChunkIsOccupied() {
        ChunkOccupancy occupancy = new ChunkOccupancy();
        occupancy.mark(CHUNK, ALICE);
        assertTrue(occupancy.isOccupied(CHUNK));
    }

    @Test
    void aChunkIsFreeAgainAfterTheWaveLeaves() {
        ChunkOccupancy occupancy = new ChunkOccupancy();
        occupancy.mark(CHUNK, ALICE);
        occupancy.unmark(CHUNK, ALICE);
        assertFalse(occupancy.isOccupied(CHUNK));
    }

    @Test
    void aChunkStaysTakenWhileAnotherWaveUsesIt() {
        ChunkOccupancy occupancy = new ChunkOccupancy();
        occupancy.mark(CHUNK, ALICE);
        occupancy.mark(CHUNK, BOB);
        occupancy.unmark(CHUNK, ALICE);
        assertTrue(occupancy.isOccupied(CHUNK), "the wave of the second player still runs here");
        occupancy.unmark(CHUNK, BOB);
        assertFalse(occupancy.isOccupied(CHUNK), "the chunk is free when the last wave left it");
    }

    @Test
    void theChunkOfAnotherPlayerIsTaken() {
        ChunkOccupancy occupancy = new ChunkOccupancy();
        occupancy.mark(CHUNK, ALICE);
        assertTrue(occupancy.occupiedByOther(CHUNK, BOB));
    }

    @Test
    void theOwnChunkIsNotTakenByOthers() {
        ChunkOccupancy occupancy = new ChunkOccupancy();
        occupancy.mark(CHUNK, ALICE);
        assertFalse(occupancy.occupiedByOther(CHUNK, ALICE));
    }

    @Test
    void onlyTheChunksOfOtherPlayersAreTaken() {
        ChunkOccupancy occupancy = new ChunkOccupancy();
        occupancy.mark(CHUNK, ALICE);
        occupancy.mark(OTHER, BOB);
        assertEquals(Set.of(OTHER), occupancy.takenByOthers(ALICE));
        assertEquals(Set.of(CHUNK), occupancy.takenByOthers(BOB));
    }

    @Test
    void aSharedChunkIsTakenByBothPlayers() {
        ChunkOccupancy occupancy = new ChunkOccupancy();
        occupancy.mark(CHUNK, ALICE);
        occupancy.mark(CHUNK, BOB);
        assertTrue(occupancy.occupiedByOther(CHUNK, ALICE));
        assertTrue(occupancy.occupiedByOther(CHUNK, BOB));
    }

    @Test
    void forgettingAnUnknownChunkIsSafe() {
        ChunkOccupancy occupancy = new ChunkOccupancy();
        occupancy.unmark(CHUNK, ALICE);
        assertFalse(occupancy.isOccupied(CHUNK));
    }

    @Test
    void clearingForgetsEveryWave() {
        ChunkOccupancy occupancy = new ChunkOccupancy();
        occupancy.mark(CHUNK, ALICE);
        occupancy.clear();
        assertFalse(occupancy.isOccupied(CHUNK));
    }
}
