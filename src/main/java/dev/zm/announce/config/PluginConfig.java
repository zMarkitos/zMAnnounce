package dev.zm.announce.config;

import java.util.List;

public record PluginConfig(
        AnnounceTemplate announceTemplate,
        BossbarSettings bossbar,
        SecuritySettings security,
        PerformanceSettings performance,
        UpdateSettings updates,
        MessagesSettings messages
) {

    public static PluginConfig defaultConfig() {
        return new PluginConfig(
                new AnnounceTemplate(
                        "BLOCK_NOTE_BLOCK_PLING",
                        new TitleSettings(true, 10, 70, 20, "&#FFC930&lANNOUNCEMENT", "&fCheck the chat"),
                        List.of(
                                "&8&m-----------------------------------------------------",
                                "&#FFC930&lSERVER ANNOUNCEMENT",
                                "",
                                "&f%message%",
                                "",
                                "&7&o(( Stay tuned for server announcements. ))",
                                "&8&m-----------------------------------------------------"
                        )
                ),
                new BossbarSettings(
                        "YELLOW",
                        "PROGRESS",
                        "BLOCK_NOTE_BLOCK_PLING",
                        "ENTITY_PLAYER_LEVELUP",
                        new EndTitleSettings(10, 60, 10, "&6Event Started!", "&fGood luck!")
                ),
                new SecuritySettings(500, 2, 60, 5),
                new PerformanceSettings(true, 500),
                new UpdateSettings(true, true),
                new MessagesSettings(
                        "&cYou do not have permission: zmannounce.reload",
                        "&cYou do not have permission: zmannounce.bossbar",
                        "&cYou do not have permission: zmannounce.use",
                        "&aConfig reloaded successfully.",
                        "&cFailed to reload config.yml. Check console for details.",
                        "&e/zmannounce <message> <server|all>",
                        "&aAnnouncement sent to &e%target%&a. Recipients: &e%recipientCount%",
                        "&cUsage: /zmannounce bossbar <message> <time> <server|all>",
                        "&cInvalid time format. Use: 10s, 15m, 1h",
                        "&aBossbar started for &e%target%&a. Recipients: &e%recipientCount%",
                        "&cMessage too long. Max %maxLength% characters.",
                        "&cPlease wait %remainingSeconds% seconds before sending another announcement.",
                        "&cBossbar duration too long. Max %maxMinutes% minutes.",
                        "&cToo many active bossbars. Max %maxActiveBossbars% allowed.",
                        "&cPlease wait %remainingSeconds% seconds before starting another bossbar.",
                        "&e/zmannounce <message> <server|all>",
                        "&e/zmannounce bossbar <message> <time> <server|all>",
                        "&e/zmannounce reload"
                )
        );
    }

    public record AnnounceTemplate(
            String sound,
            TitleSettings title,
            List<String> messages
    ) {
    }

    public record TitleSettings(
            boolean enabled,
            int fadeIn,
            int stay,
            int fadeOut,
            String title,
            String subtitle
    ) {
    }

    public record BossbarSettings(
            String color,
            String overlay,
            String soundStart,
            String soundEnd,
            EndTitleSettings endTitle
    ) {
    }

    public record EndTitleSettings(
            int fadeIn,
            int stay,
            int fadeOut,
            String title,
            String subtitle
    ) {
    }

    public record SecuritySettings(
            int maxMessageLength,
            int announcementCooldownSeconds,
            int maxBossbarDurationMinutes,
            int maxActiveBossbars
    ) {
    }

    public record PerformanceSettings(
            boolean enableColorCache,
            int maxColorCacheSize
    ) {
    }

    public record MessagesSettings(
            String noPermissionReload,
            String noPermissionBossbar,
            String noPermissionUse,
            String configReloaded,
            String configReloadFailed,
            String announcementUsage,
            String announcementSent,
            String bossbarUsage,
            String bossbarInvalidTime,
            String bossbarStarted,
            String messageTooLong,
            String announcementCooldown,
            String bossbarDurationTooLong,
            String tooManyActiveBossbars,
            String bossbarCooldown,
            String helpAnnounce,
            String helpBossbar,
            String helpReload
    ) {
    }

    public record UpdateSettings(
            boolean checkForUpdates,
            boolean notifyPlayersOnJoin
    ) {
    }
}
