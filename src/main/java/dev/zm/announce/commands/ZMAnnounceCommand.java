package dev.zm.announce.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import dev.zm.announce.ZMAnnouncePlugin;
import dev.zm.announce.announce.AnnouncementResult;
import dev.zm.announce.announce.AnnouncementService;
import dev.zm.announce.bossbar.BossbarManager;
import dev.zm.announce.bossbar.BossbarStartResult;
import dev.zm.announce.config.PluginConfig;
import dev.zm.announce.util.ColorUtil;
import dev.zm.announce.util.TimeUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public final class ZMAnnounceCommand implements SimpleCommand {

    private final ZMAnnouncePlugin plugin;
    private final AnnouncementService announcementService;
    private final BossbarManager bossbarManager;
    
    // Security: Rate limiting to prevent spam
    private final ConcurrentHashMap<UUID, Long> lastAnnouncementTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastBossbarTime = new ConcurrentHashMap<>();

    public ZMAnnounceCommand(
            ZMAnnouncePlugin plugin,
            AnnouncementService announcementService,
            BossbarManager bossbarManager
    ) {
        this.plugin = plugin;
        this.announcementService = announcementService;
        this.bossbarManager = bossbarManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            sendUsage(source);
            return;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            handleReload(source);
            return;
        }

        if (args[0].equalsIgnoreCase("bossbar")) {
            handleBossbar(source, args);
            return;
        }

        if (args[0].equalsIgnoreCase("alert")) {
            handleAlert(source, args);
            return;
        }

        sendUsage(source);
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            String current = args[0].toLowerCase();
            if ("reload".startsWith(current)) {
                suggestions.add("reload");
            }
            if ("alert".startsWith(current)) {
                suggestions.add("alert");
            }
            if ("bossbar".startsWith(current)) {
                suggestions.add("bossbar");
            }
            return suggestions;
        }

        if (args.length >= 2) {
            String subcommand = args[0].toLowerCase();

            if (subcommand.equals("alert")) {
                if (args.length == 2) {
                    suggestions.add("\"message\"");
                } else if (args.length >= 3) {
                    suggestions.add("all");
                    this.plugin.getProxyServer().getAllServers().forEach(server ->
                            suggestions.add(server.getServerInfo().getName()));
                }
            } else if (subcommand.equals("bossbar")) {
                if (args.length == 2) {
                    suggestions.add("\"message\"");
                } else if (args.length == 3) {
                    suggestions.add("10s");
                    suggestions.add("15m");
                    suggestions.add("1h");
                    suggestions.add("30s");
                    suggestions.add("5m");
                    suggestions.add("2h");
                } else if (args.length >= 4) {
                    suggestions.add("all");
                    this.plugin.getProxyServer().getAllServers().forEach(server ->
                            suggestions.add(server.getServerInfo().getName()));
                }
            }
        }

        return suggestions;
    }

    private void handleReload(CommandSource source) {
        if (!hasPermission(source, "zmannounce.reload")) {
            sendMessage(source, "no-permission-reload");
            return;
        }

        boolean success = this.plugin.reloadConfig();
        if (success) {
            sendMessage(source, "config-reloaded");
        } else {
            sendMessage(source, "config-reload-failed");
        }
    }

    private void handleBossbar(CommandSource source, String[] args) {
        if (!hasPermission(source, "zmannounce.bossbar")) {
            sendMessage(source, "no-permission-bossbar");
            return;
        }

        if (args.length < 4) {
            sendMessage(source, "bossbar-usage");
            return;
        }

        String timeArg = args[args.length - 2];
        String targetArg = args[args.length - 1];
        String message = stripQuotes(joinArguments(args, 1, args.length - 2));

        Optional<Duration> duration = TimeUtil.parseDuration(timeArg);
        if (duration.isEmpty()) {
            sendMessage(source, "bossbar-invalid-time");
            return;
        }

        Duration parsedDuration = duration.get();
        
        // Security: Check bossbar duration limit based on time unit
        char timeUnit = Character.toLowerCase(timeArg.charAt(timeArg.length() - 1));
        boolean durationValid = switch (timeUnit) {
            case 's' -> parsedDuration.getSeconds() <= 86400L; // 24 hours in seconds
            case 'h' -> parsedDuration.toHours() <= 24L; // 24 hours
            case 'm' -> parsedDuration.toMinutes() <= this.plugin.getConfigManager().getConfig().security().maxBossbarDurationMinutes();
            default -> false;
        };
        
        if (!durationValid) {
            String maxLimit = switch (timeUnit) {
                case 's' -> "86400s (24 hours)";
                case 'h' -> "24h";
                case 'm' -> this.plugin.getConfigManager().getConfig().security().maxBossbarDurationMinutes() + "m";
                default -> "unknown";
            };
            sendMessage(source, "bossbar-duration-too-long", "maxLimit", maxLimit);
            return;
        }
        
        // Security: Rate limiting (console bypass)
        if (!(source instanceof ConsoleCommandSource)) {
            UUID playerId = ((com.velocitypowered.api.proxy.Player) source).getUniqueId();
            int cooldownSeconds = this.plugin.getConfigManager().getConfig().security().announcementCooldownSeconds();
            long cooldownMillis = cooldownSeconds * 1000L;
            long now = System.currentTimeMillis();
            long lastTime = this.lastBossbarTime.getOrDefault(playerId, 0L);
            
            if (now - lastTime < cooldownMillis) {
                long remainingSeconds = (cooldownMillis - (now - lastTime)) / 1000L;
                sendMessage(source, "bossbar-cooldown", "remainingSeconds", remainingSeconds);
                return;
            }
            
            this.lastBossbarTime.put(playerId, now);
        }

        this.plugin.getProxyServer().getScheduler().buildTask(this.plugin, () -> {
            BossbarStartResult result = this.bossbarManager.startBossbar(message, parsedDuration, targetArg);
            if (!result.success()) {
                source.sendMessage(ColorUtil.parse("&c" + result.error()));
                return;
            }

            sendMessage(source, "bossbar-started", "target", result.target(), "recipientCount", result.recipientCount());
        }).schedule();
    }

    private void handleAlert(CommandSource source, String[] args) {
        if (!hasPermission(source, "zmannounce.use")) {
            sendMessage(source, "no-permission-use");
            return;
        }

        if (args.length < 3) {
            sendMessage(source, "announcement-usage");
            return;
        }

        String targetArg = args[args.length - 1];
        String message = stripQuotes(joinArguments(args, 1, args.length - 1));

        // Security: Check message length limit
        int maxLength = this.plugin.getConfigManager().getConfig().security().maxMessageLength();
        if (message.length() > maxLength) {
            sendMessage(source, "message-too-long", "maxLength", maxLength);
            return;
        }

        // Security: Rate limiting (console bypass)
        if (!(source instanceof ConsoleCommandSource)) {
            UUID playerId = ((com.velocitypowered.api.proxy.Player) source).getUniqueId();
            int cooldownSeconds = this.plugin.getConfigManager().getConfig().security().announcementCooldownSeconds();
            long cooldownMillis = cooldownSeconds * 1000L;
            long now = System.currentTimeMillis();
            long lastTime = this.lastAnnouncementTime.getOrDefault(playerId, 0L);
            
            if (now - lastTime < cooldownMillis) {
                long remainingSeconds = (cooldownMillis - (now - lastTime)) / 1000L;
                sendMessage(source, "announcement-cooldown", "remainingSeconds", remainingSeconds);
                return;
            }
            
            this.lastAnnouncementTime.put(playerId, now);
        }

        this.plugin.getProxyServer().getScheduler().buildTask(this.plugin, () -> {
            AnnouncementResult result = this.announcementService.sendAnnouncement(message, targetArg);
            if (!result.success()) {
                source.sendMessage(ColorUtil.parse("&c" + result.error()));
                return;
            }

            sendMessage(source, "announcement-sent", "target", result.target(), "recipientCount", result.recipientCount());
        }).schedule();
    }

    private boolean hasPermission(CommandSource source, String permission) {
        return source instanceof ConsoleCommandSource || source.hasPermission(permission);
    }

    public void cleanupCooldownMaps(int cooldownSeconds) {
        if (cooldownSeconds < 0) {
            cooldownSeconds = 0;
        }
        // Keep only recently-used entries; old cooldowns are irrelevant.
        long threshold = System.currentTimeMillis() - (cooldownSeconds * 1000L);
        this.lastAnnouncementTime.entrySet().removeIf(entry -> entry.getValue() < threshold);
        this.lastBossbarTime.entrySet().removeIf(entry -> entry.getValue() < threshold);
    }

    private void sendUsage(CommandSource source) {
        sendMessage(source, "help-announce");
        sendMessage(source, "help-bossbar");
        sendMessage(source, "help-reload");
    }

    private String joinArguments(String[] args, int startInclusive, int endExclusive) {
        StringBuilder builder = new StringBuilder();
        for (int index = startInclusive; index < endExclusive; index++) {
            if (index > startInclusive) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private String replacePlaceholders(String message, Object... replacements) {
        String result = message;
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                String placeholder = "%" + replacements[i] + "%";
                String value = String.valueOf(replacements[i + 1]);
                result = result.replace(placeholder, value);
            }
        }
        return result;
    }

    private void sendMessage(CommandSource source, String messageKey, Object... replacements) {
        PluginConfig.MessagesSettings messages = this.plugin.getConfigManager().getConfig().messages();
        String message = switch (messageKey) {
            case "no-permission-reload" -> messages.noPermissionReload();
            case "no-permission-bossbar" -> messages.noPermissionBossbar();
            case "no-permission-use" -> messages.noPermissionUse();
            case "config-reloaded" -> messages.configReloaded();
            case "config-reload-failed" -> messages.configReloadFailed();
            case "announcement-usage" -> messages.announcementUsage();
            case "announcement-sent" -> messages.announcementSent();
            case "bossbar-usage" -> messages.bossbarUsage();
            case "bossbar-invalid-time" -> messages.bossbarInvalidTime();
            case "bossbar-started" -> messages.bossbarStarted();
            case "message-too-long" -> messages.messageTooLong();
            case "announcement-cooldown" -> messages.announcementCooldown();
            case "bossbar-duration-too-long" -> messages.bossbarDurationTooLong();
            case "too-many-active-bossbars" -> messages.tooManyActiveBossbars();
            case "bossbar-cooldown" -> messages.bossbarCooldown();
            case "help-announce" -> messages.helpAnnounce();
            case "help-bossbar" -> messages.helpBossbar();
            case "help-reload" -> messages.helpReload();
            default -> messageKey;
        };
        String finalMessage = replacePlaceholders(message, replacements);
        source.sendMessage(ColorUtil.parse(finalMessage));
    }

    private String stripQuotes(String text) {
        String trimmed = text.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }
}
