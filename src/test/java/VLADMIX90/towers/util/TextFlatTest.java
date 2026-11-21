package VLADMIX90.towers.util;

import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The chrono token must be byte identical to the item a player can create by hand with
 * {@code /give}, otherwise the two look the same but never stack.
 */
class TextFlatTest {

    private static final GsonComponentSerializer GSON = GsonComponentSerializer.gson();

    private static final String REFERENCE_NAME = "[{\"text\":\"Х\",\"italic\":false,\"color\":\"#f7fdff\"},"
            + "{\"text\":\"р\",\"italic\":false,\"color\":\"#def0fa\"},{\"text\":\"о\",\"italic\":false,\"color\":\"#c6e2f5\"},"
            + "{\"text\":\"н\",\"italic\":false,\"color\":\"#add5f0\"},{\"text\":\"о\",\"italic\":false,\"color\":\"#94c8eb\"},"
            + "{\"text\":\"-\",\"italic\":false,\"color\":\"#7cbbe7\"},{\"text\":\"ж\",\"italic\":false,\"color\":\"#63ade2\"},"
            + "{\"text\":\"е\",\"italic\":false,\"color\":\"#4aa0dd\"},{\"text\":\"т\",\"italic\":false,\"color\":\"#3193d8\"},"
            + "{\"text\":\"о\",\"italic\":false,\"color\":\"#1985d3\"},{\"text\":\"н\",\"italic\":false,\"color\":\"#0078ce\"}]";

    private static final String REFERENCE_LORE_SECOND = "[{\"text\":\"•\",\"italic\":false,\"color\":\"#f7fdff\"},"
            + "{\"text\":\" \",\"italic\":false,\"color\":\"#ebf6fd\"},{\"text\":\"Р\",\"italic\":false,\"color\":\"#def0fa\"},"
            + "{\"text\":\"а\",\"italic\":false,\"color\":\"#d2e9f8\"},{\"text\":\"з\",\"italic\":false,\"color\":\"#c6e2f5\"},"
            + "{\"text\":\"л\",\"italic\":false,\"color\":\"#b9dcf3\"},{\"text\":\"и\",\"italic\":false,\"color\":\"#add5f0\"},"
            + "{\"text\":\"ч\",\"italic\":false,\"color\":\"#a1ceee\"},{\"text\":\"н\",\"italic\":false,\"color\":\"#94c8eb\"},"
            + "{\"text\":\"а\",\"italic\":false,\"color\":\"#88c1e9\"},{\"text\":\"я\",\"italic\":false,\"color\":\"#7cbbe7\"},"
            + "{\"text\":\" \",\"italic\":false,\"color\":\"#6fb4e4\"},{\"text\":\"к\",\"italic\":false,\"color\":\"#63ade2\"},"
            + "{\"text\":\"о\",\"italic\":false,\"color\":\"#56a7df\"},{\"text\":\"с\",\"italic\":false,\"color\":\"#4aa0dd\"},"
            + "{\"text\":\"м\",\"italic\":false,\"color\":\"#3e99da\"},{\"text\":\"е\",\"italic\":false,\"color\":\"#3193d8\"},"
            + "{\"text\":\"т\",\"italic\":false,\"color\":\"#258cd5\"},{\"text\":\"и\",\"italic\":false,\"color\":\"#1985d3\"},"
            + "{\"text\":\"к\",\"italic\":false,\"color\":\"#0c7fd0\"},{\"text\":\"а\",\"italic\":false,\"color\":\"#0078ce\"}]";

    @Test
    void chronoTokenFromConfigMatchesTheHandWrittenGiveItem() {
        Map<?, ?> entry = shippedChronoToken();
        String name = String.valueOf(entry.get("name"));
        List<?> lore = (List<?>) entry.get("lore");

        assertEquals(GSON.serialize(GSON.deserialize(REFERENCE_NAME)),
                GSON.serialize(Text.flat(Text.parse(name))));
        assertEquals(GSON.serialize(GSON.deserialize(REFERENCE_LORE_SECOND)),
                GSON.serialize(Text.flat(Text.parse(String.valueOf(lore.get(1))))));
    }

    @Test
    void plainLoreLineIsNotChanged() {
        assertEquals("{\"italic\":false,\"color\":\"gray\",\"text\":\"Можно обменять на:\"}",
                GSON.serialize(Text.flat(Text.parse("<italic:false><gray>Можно обменять на:</gray></italic:false>"))));
    }

    private static Map<?, ?> shippedChronoToken() {
        InputStream stream = TextFlatTest.class.getResourceAsStream("/config.yml");
        assertEquals(true, stream != null, "config.yml must be on the classpath");
        YamlConfiguration shipped = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        return shipped.getMapList("rift-loot").get(0);
    }
}
