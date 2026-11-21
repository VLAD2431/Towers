package VLADMIX90.towers.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Parses MiniMessage text and keeps compatibility with legacy & color codes. */
public final class Text {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "black"), Map.entry('1', "dark_blue"), Map.entry('2', "dark_green"),
            Map.entry('3', "dark_aqua"), Map.entry('4', "dark_red"), Map.entry('5', "dark_purple"),
            Map.entry('6', "gold"), Map.entry('7', "gray"), Map.entry('8', "dark_gray"),
            Map.entry('9', "blue"), Map.entry('a', "green"), Map.entry('b', "aqua"),
            Map.entry('c', "red"), Map.entry('d', "light_purple"), Map.entry('e', "yellow"),
            Map.entry('f', "white"), Map.entry('k', "obfuscated"), Map.entry('l', "bold"),
            Map.entry('m', "strikethrough"), Map.entry('n', "underlined"), Map.entry('o', "italic"),
            Map.entry('r', "reset")
    );

    private Text() {}

    public static Component parse(String value) {
        if (value == null || value.isBlank()) return Component.empty();
        return MINI_MESSAGE.deserialize(legacyToMiniMessage(value));
    }

    /**
     * Flattens a component into the plain per-character shape the game itself stores.
     * <p>
     * MiniMessage builds a nested tree (empty root, a wrapper carrying the shared style and one
     * child per character), while the game serializes a flat list where the first character is the
     * root and every character carries its own style. Two items that look identical but are built
     * differently have different NBT and therefore never stack, so names and lore are normalized
     * here to the shape of a hand written {@code /give} item.
     */
    public static Component flat(Component component) {
        List<Component> leaves = new ArrayList<>();
        collect(component, Style.empty(), leaves);
        if (leaves.isEmpty()) return component;
        Component first = leaves.get(0);
        List<Component> rest = leaves.subList(1, leaves.size());
        String text = first instanceof TextComponent textComponent ? textComponent.content() : "";
        Component root = Component.text(text, first.style());
        return rest.isEmpty() ? root : root.children(rest);
    }

    private static void collect(Component component, Style inherited, List<Component> leaves) {
        Style merged = component.style().merge(inherited);
        String text = component instanceof TextComponent textComponent ? textComponent.content() : "";
        if (!text.isEmpty()) leaves.add(Component.text(text, merged));
        for (Component child : component.children()) collect(child, merged, leaves);
    }

    private static String legacyToMiniMessage(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if ((current == '&' || current == '\u00a7') && i + 1 < value.length()) {
                String tag = LEGACY_TAGS.get(Character.toLowerCase(value.charAt(i + 1)));
                if (tag != null) {
                    result.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            result.append(current);
        }
        return result.toString();
    }
}
