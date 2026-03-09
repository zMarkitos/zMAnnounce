package dev.zm.announce.util;

import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

public final class SoundUtil {

    private SoundUtil() {
    }

    public static Optional<Sound> parseSound(String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(soundName);
        try {
            Key key = Key.key(normalized);
            return Optional.of(Sound.sound(key, Sound.Source.MASTER, 1.0f, 1.0f));
        } catch (InvalidKeyException ignored) {
            return Optional.empty();
        }
    }

    private static String normalize(String input) {
        String value = input.trim();
        if (value.contains(":")) {
            return value.toLowerCase(Locale.ROOT);
        }
        if (value.contains(".")) {
            return "minecraft:" + value.toLowerCase(Locale.ROOT);
        }
        return "minecraft:" + value.toLowerCase(Locale.ROOT).replace('_', '.');
    }
}
