package VLADMIX90.towers.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the pure part of sound name resolution.
 *
 * <p>Only {@code canonical} is tested: resolving a sound needs the server registry
 * ({@code org.bukkit.Sound} cannot be initialized without a running server).
 */
class SoundsTest {

    @Test
    void normalizesResourcePath() {
        assertEquals("BLOCK_NOTE_BLOCK_BIT", Sounds.canonical("block.note_block.bit"));
    }

    @Test
    void normalizesMinecraftPrefix() {
        assertEquals("BLOCK_NOTE_BLOCK_BIT", Sounds.canonical("minecraft:block.note_block.bit"));
    }

    @Test
    void normalizesConstantName() {
        assertEquals("BLOCK_NOTE_BLOCK_BIT", Sounds.canonical("BLOCK_NOTE_BLOCK_BIT"));
    }

    @Test
    void normalizesUnderscores() {
        assertEquals("BLOCK_NOTE_BLOCK_BIT", Sounds.canonical("block_note_block_bit"));
    }

    @Test
    void blankYieldsEmpty() {
        assertEquals("", Sounds.canonical(null));
        assertEquals("", Sounds.canonical("  "));
    }

    @Test
    void readsThePathOfACustomSound() {
        assertEquals("towers:quest_given", Sounds.resourcePath("towers:quest_given"));
    }

    @Test
    void thePathOfACustomSoundIsLowercase() {
        assertEquals("towers:quest_given", Sounds.resourcePath("Towers:Quest_Given"));
    }

    @Test
    void aNameWithoutANamespaceHasNoPath() {
        assertNull(Sounds.resourcePath("block.note_block.bit"));
        assertNull(Sounds.resourcePath("quest_given"));
    }

    @Test
    void anIncompletePathIsRejected() {
        assertNull(Sounds.resourcePath("towers:"));
        assertNull(Sounds.resourcePath(":quest_given"));
        assertNull(Sounds.resourcePath("towers:bad name"));
        assertNull(Sounds.resourcePath(null));
        assertNull(Sounds.resourcePath("  "));
    }

    @Test
    void aSoundOfTheGameIsNoCustomSound() {
        assertFalse(Sounds.isCustom("block.note_block.bit"));
        assertFalse(Sounds.isCustom("BLOCK_NOTE_BLOCK_BIT"));
        assertFalse(Sounds.isCustom("minecraft:block.note_block.bit"));
        assertFalse(Sounds.isCustom(null));
    }

    @Test
    void aSoundOfTheResourcePackIsACustomSound() {
        assertTrue(Sounds.isCustom("towers:quest_given"));
        assertTrue(Sounds.isCustom("Towers:quest_given"));
    }
}
