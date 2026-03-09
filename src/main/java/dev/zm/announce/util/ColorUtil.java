package dev.zm.announce.util;

import java.util.Iterator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class ColorUtil {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGACY_TO_MINIMESSAGE = buildLegacyMap();

    private static final Object CACHE_LOCK = new Object();
    // Access-order map used as LRU cache.
    private static final LinkedHashMap<String, Component> COLOR_CACHE = new LinkedHashMap<>(256, 0.75f, true);
    private static volatile boolean cacheEnabled = true;
    private static volatile int maxCacheSize = 500;

    private ColorUtil() {
    }

    public static void configureCache(boolean enabled, int configuredMaxSize) {
        cacheEnabled = enabled;
        maxCacheSize = Math.max(50, configuredMaxSize);

        synchronized (CACHE_LOCK) {
            if (!cacheEnabled) {
                COLOR_CACHE.clear();
                return;
            }
            trimCacheIfNeeded();
        }
    }

    public static Component parse(String input) {
        return parse(input, true);
    }

    public static Component parse(String input, boolean cacheable) {
        if (input == null || input.isBlank()) {
            return Component.empty();
        }

        // Dynamic strings (e.g. countdown text) should bypass cache to avoid churn.
        if (!cacheEnabled || !cacheable) {
            return parseDirect(input);
        }

        synchronized (CACHE_LOCK) {
            Component cached = COLOR_CACHE.get(input);
            if (cached != null) {
                return cached;
            }
        }

        Component parsed = parseDirect(input);
        synchronized (CACHE_LOCK) {
            if (!cacheEnabled) {
                return parsed;
            }
            // Put after parsing to keep lock hold-time minimal.
            COLOR_CACHE.put(input, parsed);
            trimCacheIfNeeded();
        }
        return parsed;
    }

    private static Component parseDirect(String input) {
        String normalized = legacyToMiniMessage(input);
        try {
            return MINI_MESSAGE.deserialize(normalized);
        } catch (Exception ignored) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(input);
        }
    }

    public static void cleanupCache() {
        synchronized (CACHE_LOCK) {
            trimCacheIfNeeded();
        }
    }

    private static void trimCacheIfNeeded() {
        if (COLOR_CACHE.size() <= maxCacheSize) {
            return;
        }
        // LinkedHashMap in access-order lets us evict oldest entries first.
        Iterator<Map.Entry<String, Component>> iterator = COLOR_CACHE.entrySet().iterator();
        while (COLOR_CACHE.size() > maxCacheSize && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static String legacyToMiniMessage(String text) {
        StringBuilder builder = new StringBuilder(text.length() + 16);
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current != '&' || index + 1 >= text.length()) {
                builder.append(current);
                continue;
            }

            char next = text.charAt(index + 1);
            if (next == '#' && index + 7 < text.length() && isHex(text, index + 2, index + 8)) {
                builder.append("<#").append(text, index + 2, index + 8).append('>');
                index += 7;
                continue;
            }

            String mapped = LEGACY_TO_MINIMESSAGE.get(Character.toLowerCase(next));
            if (mapped != null) {
                builder.append(mapped);
                index++;
                continue;
            }

            builder.append(current);
        }
        return builder.toString();
    }

    private static boolean isHex(String text, int start, int endExclusive) {
        for (int index = start; index < endExclusive; index++) {
            if (!isHexChar(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexChar(char value) {
        return (value >= '0' && value <= '9')
                || (value >= 'a' && value <= 'f')
                || (value >= 'A' && value <= 'F');
    }

    private static Map<Character, String> buildLegacyMap() {
        Map<Character, String> map = new HashMap<>();
        map.put('0', "<black>");
        map.put('1', "<dark_blue>");
        map.put('2', "<dark_green>");
        map.put('3', "<dark_aqua>");
        map.put('4', "<dark_red>");
        map.put('5', "<dark_purple>");
        map.put('6', "<gold>");
        map.put('7', "<gray>");
        map.put('8', "<dark_gray>");
        map.put('9', "<blue>");
        map.put('a', "<green>");
        map.put('b', "<aqua>");
        map.put('c', "<red>");
        map.put('d', "<light_purple>");
        map.put('e', "<yellow>");
        map.put('f', "<white>");
        map.put('k', "<obfuscated>");
        map.put('l', "<bold>");
        map.put('m', "<strikethrough>");
        map.put('n', "<underlined>");
        map.put('o', "<italic>");
        map.put('r', "<reset>");
        return map;
    }
}
