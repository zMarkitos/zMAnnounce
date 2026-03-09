package dev.zm.announce.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public final class ConfigManager {

    private final Logger logger;
    private final Path dataDirectory;
    private final Path configPath;
    private final Class<?> resourceClass;
    private final Yaml yaml;

    private volatile PluginConfig config;

    public ConfigManager(Logger logger, Path dataDirectory, Class<?> resourceClass) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.configPath = dataDirectory.resolve("config.yml");
        this.resourceClass = resourceClass;
        this.yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        this.config = PluginConfig.defaultConfig();
    }

    public synchronized void load() throws IOException {
        ensureDefaultConfigExists();
        this.config = readConfig();
    }

    public synchronized void reload() throws IOException {
        ensureDefaultConfigExists();
        this.config = readConfig();
    }

    public PluginConfig getConfig() {
        return this.config;
    }

    private void ensureDefaultConfigExists() throws IOException {
        Files.createDirectories(this.dataDirectory);
        if (Files.exists(this.configPath)) {
            return;
        }

        try (InputStream inputStream = this.resourceClass.getClassLoader().getResourceAsStream("config.yml")) {
            if (inputStream == null) {
                Files.writeString(this.configPath, defaultConfigText(), StandardCharsets.UTF_8);
            } else {
                Files.copy(inputStream, this.configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private PluginConfig readConfig() throws IOException {
        try (Reader reader = Files.newBufferedReader(this.configPath, StandardCharsets.UTF_8)) {
            Object rootObject = this.yaml.load(reader);
            Map<String, Object> root = asMap(rootObject);

            PluginConfig defaults = PluginConfig.defaultConfig();

            Map<String, Object> announceSection = asMap(root.get("announce-template"));
            PluginConfig.AnnounceTemplate defaultAnnounce = defaults.announceTemplate();

            Map<String, Object> titleSection = asMap(announceSection.get("title"));
            PluginConfig.TitleSettings defaultTitle = defaultAnnounce.title();

            PluginConfig.TitleSettings titleSettings = new PluginConfig.TitleSettings(
                    getBoolean(titleSection, "enabled", defaultTitle.enabled()),
                    getInt(titleSection, "fadein", defaultTitle.fadeIn()),
                    getInt(titleSection, "stay", defaultTitle.stay()),
                    getInt(titleSection, "fadeout", defaultTitle.fadeOut()),
                    getString(titleSection, "title", defaultTitle.title()),
                    getString(titleSection, "subtitle", defaultTitle.subtitle())
            );

            PluginConfig.AnnounceTemplate announceTemplate = new PluginConfig.AnnounceTemplate(
                    getString(announceSection, "sound", defaultAnnounce.sound()),
                    titleSettings,
                    getStringList(announceSection, "messages", defaultAnnounce.messages())
            );

            Map<String, Object> bossbarSection = asMap(root.get("bossbar"));
            PluginConfig.BossbarSettings defaultBossbar = defaults.bossbar();

            // Canonical path since current versions: bossbar.end-title
            Map<String, Object> endTitleSection = asMap(bossbarSection.get("end-title"));
            // Backward compatibility with broken older config layout.
            if (endTitleSection.isEmpty()) {
                Map<String, Object> legacyMessagesSection = asMap(root.get("messages"));
                endTitleSection = asMap(legacyMessagesSection.get("end-title"));
                if (!endTitleSection.isEmpty()) {
                    this.logger.warn("Detected legacy config path 'messages.end-title'. Please move it to 'bossbar.end-title'.");
                }
            }
            PluginConfig.EndTitleSettings defaultEndTitle = defaultBossbar.endTitle();

            PluginConfig.EndTitleSettings endTitleSettings = new PluginConfig.EndTitleSettings(
                    getInt(endTitleSection, "fadein", defaultEndTitle.fadeIn()),
                    getInt(endTitleSection, "stay", defaultEndTitle.stay()),
                    getInt(endTitleSection, "fadeout", defaultEndTitle.fadeOut()),
                    getString(endTitleSection, "title", defaultEndTitle.title()),
                    getString(endTitleSection, "subtitle", defaultEndTitle.subtitle())
            );

            PluginConfig.BossbarSettings bossbarSettings = new PluginConfig.BossbarSettings(
                    getString(bossbarSection, "color", defaultBossbar.color()),
                    getString(bossbarSection, "overlay", defaultBossbar.overlay()),
                    getString(bossbarSection, "sound-start", defaultBossbar.soundStart()),
                    getString(bossbarSection, "sound-end", defaultBossbar.soundEnd()),
                    endTitleSettings
            );

            Map<String, Object> securitySection = asMap(root.get("security"));
            PluginConfig.SecuritySettings defaultSecurity = defaults.security();

            PluginConfig.SecuritySettings securitySettings = new PluginConfig.SecuritySettings(
                    Math.max(1, getInt(securitySection, "max-message-length", defaultSecurity.maxMessageLength())),
                    Math.max(0, getInt(securitySection, "announcement-cooldown-seconds", defaultSecurity.announcementCooldownSeconds())),
                    Math.max(1, getInt(securitySection, "max-bossbar-duration-minutes", defaultSecurity.maxBossbarDurationMinutes())),
                    Math.max(1, getInt(securitySection, "max-active-bossbars", defaultSecurity.maxActiveBossbars()))
            );

            Map<String, Object> performanceSection = asMap(root.get("performance"));
            PluginConfig.PerformanceSettings defaultPerformance = defaults.performance();

            PluginConfig.PerformanceSettings performanceSettings = new PluginConfig.PerformanceSettings(
                    getBoolean(performanceSection, "enable-color-cache", defaultPerformance.enableColorCache()),
                    Math.max(50, getInt(performanceSection, "max-color-cache-size", defaultPerformance.maxColorCacheSize()))
            );

            Map<String, Object> updatesSection = asMap(root.get("updates"));
            PluginConfig.UpdateSettings defaultUpdates = defaults.updates();

            PluginConfig.UpdateSettings updateSettings = new PluginConfig.UpdateSettings(
                    getBoolean(updatesSection, "check-for-updates", defaultUpdates.checkForUpdates()),
                    getBoolean(updatesSection, "notify-players-on-join", defaultUpdates.notifyPlayersOnJoin())
            );

            Map<String, Object> messagesSection = asMap(root.get("messages"));
            PluginConfig.MessagesSettings defaultMessages = defaults.messages();

            PluginConfig.MessagesSettings messagesSettings = new PluginConfig.MessagesSettings(
                    getString(messagesSection, "no-permission-reload", defaultMessages.noPermissionReload()),
                    getString(messagesSection, "no-permission-bossbar", defaultMessages.noPermissionBossbar()),
                    getString(messagesSection, "no-permission-use", defaultMessages.noPermissionUse()),
                    getString(messagesSection, "config-reloaded", defaultMessages.configReloaded()),
                    getString(messagesSection, "config-reload-failed", defaultMessages.configReloadFailed()),
                    getString(messagesSection, "announcement-usage", defaultMessages.announcementUsage()),
                    getString(messagesSection, "announcement-sent", defaultMessages.announcementSent()),
                    getString(messagesSection, "bossbar-usage", defaultMessages.bossbarUsage()),
                    getString(messagesSection, "bossbar-invalid-time", defaultMessages.bossbarInvalidTime()),
                    getString(messagesSection, "bossbar-started", defaultMessages.bossbarStarted()),
                    getString(messagesSection, "message-too-long", defaultMessages.messageTooLong()),
                    getString(messagesSection, "announcement-cooldown", defaultMessages.announcementCooldown()),
                    getString(messagesSection, "bossbar-duration-too-long", defaultMessages.bossbarDurationTooLong()),
                    getString(messagesSection, "too-many-active-bossbars", defaultMessages.tooManyActiveBossbars()),
                    getString(messagesSection, "bossbar-cooldown", defaultMessages.bossbarCooldown()),
                    getString(messagesSection, "help-announce", defaultMessages.helpAnnounce()),
                    getString(messagesSection, "help-bossbar", defaultMessages.helpBossbar()),
                    getString(messagesSection, "help-reload", defaultMessages.helpReload())
            );

            return new PluginConfig(announceTemplate, bossbarSettings, securitySettings, performanceSettings, updateSettings, messagesSettings);
        } catch (Exception exception) {
            this.logger.error("Invalid config.yml detected, using defaults in memory.", exception);
            return PluginConfig.defaultConfig();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, mapValue) -> {
                if (key != null) {
                    result.put(String.valueOf(key), mapValue);
                }
            });
            return result;
        }
        return Collections.emptyMap();
    }

    private String getString(Map<String, Object> section, String key, String defaultValue) {
        Object value = section.get(key);
        if (value == null) {
            return defaultValue;
        }
        return String.valueOf(value);
    }

    private int getInt(Map<String, Object> section, String key, int defaultValue) {
        Object value = section.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private boolean getBoolean(Map<String, Object> section, String key, boolean defaultValue) {
        Object value = section.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return Boolean.parseBoolean(text);
        }
        return defaultValue;
    }

    private List<String> getStringList(Map<String, Object> section, String key, List<String> defaultValue) {
        Object value = section.get(key);
        if (!(value instanceof List<?> list)) {
            return defaultValue;
        }
        return list.stream().map(String::valueOf).toList();
    }

    private String defaultConfigText() {
        return """
                announce-template:
                  sound: "BLOCK_NOTE_BLOCK_PLING"
                  title:
                    enabled: true
                    fadein: 10
                    stay: 70
                    fadeout: 20
                    title: "&#FFC930&lANNOUNCEMENT"
                    subtitle: "&fCheck the chat"

                  messages:
                    - "&8&m-----------------------------------------------------"
                    - "&#FFC930&lSERVER ANNOUNCEMENT"
                    - ""
                    - "&f%message%"
                    - ""
                    - "&7&o(( Stay tuned for server announcements. ))"
                    - "&8&m-----------------------------------------------------"

                bossbar:
                  color: YELLOW
                  overlay: PROGRESS
                  sound-start: "BLOCK_NOTE_BLOCK_PLING"
                  sound-end: "ENTITY_PLAYER_LEVELUP"

                  end-title:
                    fadein: 10
                    stay: 60
                    fadeout: 10
                    title: "&6Event Started!"
                    subtitle: "&fGood luck!"

                security:
                  max-message-length: 500
                  announcement-cooldown-seconds: 2
                  max-bossbar-duration-minutes: 60
                  max-active-bossbars: 3

                performance:
                  enable-color-cache: true
                  max-color-cache-size: 500

                messages:
                  no-permission-reload: "&cYou do not have permission: zmannounce.reload"
                  no-permission-bossbar: "&cYou do not have permission: zmannounce.bossbar"
                  no-permission-use: "&cYou do not have permission: zmannounce.use"
                  config-reloaded: "&aConfig reloaded successfully."
                  config-reload-failed: "&cFailed to reload config.yml. Check console for details."
                  announcement-usage: "&e/zmannounce <message> <server|all>"
                  announcement-sent: "&aAnnouncement sent to &e%target%&a. Recipients: &e%recipientCount%"
                  bossbar-usage: "&cUsage: /zmannounce bossbar <message> <time> <server|all>"
                  bossbar-invalid-time: "&cInvalid time format. Use: 10s, 15m, 1h"
                  bossbar-started: "&aBossbar started for &e%target%&a. Recipients: &e%recipientCount%"
                  message-too-long: "&cMessage too long. Max %maxLength% characters."
                  announcement-cooldown: "&cPlease wait %remainingSeconds% seconds before sending another announcement."
                  bossbar-duration-too-long: "&cBossbar duration too long. Max %maxLimit%."
                  too-many-active-bossbars: "&cToo many active bossbars. Max %maxActiveBossbars% allowed."
                  bossbar-cooldown: "&cPlease wait %remainingSeconds% seconds before starting another bossbar."
                  help-announce: "&e/zmannounce <message> <server|all>"
                  help-bossbar: "&e/zmannounce bossbar <message> <time> <server|all>"
                  help-reload: "&e/zmannounce reload"
                """;
    }
}
