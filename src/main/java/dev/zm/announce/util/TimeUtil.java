package dev.zm.announce.util;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TimeUtil {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhSMH])$");

    private TimeUtil() {
    }

    public static Optional<Duration> parseDuration(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = DURATION_PATTERN.matcher(input.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }

        long amount = Long.parseLong(matcher.group(1));
        if (amount <= 0L) {
            return Optional.empty();
        }

        char unit = Character.toLowerCase(matcher.group(2).charAt(0));
        return switch (unit) {
            case 's' -> Optional.of(Duration.ofSeconds(amount));
            case 'm' -> Optional.of(Duration.ofMinutes(amount));
            case 'h' -> Optional.of(Duration.ofHours(amount));
            default -> Optional.empty();
        };
    }

    public static String formatRemaining(Duration duration) {
        long seconds = Math.max(duration.toSeconds(), 0L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;

        if (hours > 0L) {
            return String.format("%dh %02dm %02ds", hours, minutes, remainingSeconds);
        }
        if (minutes > 0L) {
            return String.format("%dm %02ds", minutes, remainingSeconds);
        }
        return remainingSeconds + "s";
    }
}
