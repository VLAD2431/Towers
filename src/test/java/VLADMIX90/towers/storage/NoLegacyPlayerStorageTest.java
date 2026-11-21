package VLADMIX90.towers.storage;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NoLegacyPlayerStorageTest {
    @Test void playerRuntimeHasNoYamlOrLazyLoadingFallback() throws Exception {
        String storage = Files.readString(Path.of("src/main/java/VLADMIX90/towers/quest/PlayerDataStorage.java"));
        String manager = Files.readString(Path.of("src/main/java/VLADMIX90/towers/quest/QuestManager.java"));
        assertFalse(storage.contains("YamlConfiguration"));
        assertFalse(manager.contains("computeIfAbsent"));
    }
}
