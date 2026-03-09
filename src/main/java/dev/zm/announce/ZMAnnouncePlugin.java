package dev.zm.announce;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import dev.zm.announce.announce.AnnouncementService;
import dev.zm.announce.bossbar.BossbarManager;
import dev.zm.announce.commands.ZMAnnounceCommand;
import dev.zm.announce.config.ConfigManager;
import dev.zm.announce.config.PluginConfig;
import dev.zm.announce.util.ColorUtil;
import dev.zm.announce.util.VersionChecker;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

@Plugin(
        id = "zmannounce",
        name = "zMAnnounce",
        version = "1.0.0",
        description = "Global and server-specific announcements for Velocity",
        authors = {"zMarkitos_"}
)
public final class ZMAnnouncePlugin {

    private final ProxyServer proxyServer;
    private final Logger logger;
    private final Path dataDirectory;

    private ConfigManager configManager;
    private AnnouncementService announcementService;
    private BossbarManager bossbarManager;
    private ZMAnnounceCommand announceCommand;
    private ScheduledTask cacheCleanupTask;
    private ScheduledTask cooldownCleanupTask;
    private VersionChecker versionChecker;
    private volatile String latestVersion; // Store latest version for player notifications

    private final String version = "1.0.0"; // coincide con @Plugin version
    private final String modrinthProjectId = "zmannounce"; // TODO: Replace with actual Modrinth project ID

    private static final int BANNER_WIDTH = 46; // ancho visible de la caja

    private void showEnableBanner() {
        String version = "1.0.0";

        String yellow = "\u001B[33m";
        String gold = "\u001B[33;1m";
        String gray = "\u001B[37m";
        String reset = "\u001B[0m";

        logger.info("");
        logger.info(gold + "╔" + "═".repeat(BANNER_WIDTH) + "╗" + reset);
        logger.info(gold + "║" + centerText("zMAnnounce", BANNER_WIDTH, yellow) + gold + "║" + reset);
        logger.info(gold + "║" + centerText("Velocity Announcement System", BANNER_WIDTH, gray) + gold + "║" + reset);
        logger.info(gold + "║" + " ".repeat(BANNER_WIDTH) + "║" + reset);
        logger.info(gold + "║" + leftText("Author  : zMarkitos_", BANNER_WIDTH, yellow, gray) + gold + "║" + reset);
        logger.info(gold + "║" + leftText("Version : " + version, BANNER_WIDTH, yellow, gray) + gold + "║" + reset);
        logger.info(gold + "║" + leftText("Systems : Announcements | Bossbars", BANNER_WIDTH, yellow, gray) + gold + "║" + reset);
        logger.info(gold + "╚" + "═".repeat(BANNER_WIDTH) + "╝" + reset);
        logger.info("");
    }

    private void showDisableBanner() {
        String red = "\u001B[31m";
        String yellow = "\u001B[33m";
        String gray = "\u001B[37m";
        String reset = "\u001B[0m";

        logger.info("");
        logger.info(red + "╔" + "═".repeat(BANNER_WIDTH) + "╗" + reset);
        logger.info(red + "║" + centerText("zMAnnounce", BANNER_WIDTH, yellow) + red + "║" + reset);
        logger.info(red + "║" + centerText("Plugin Shutting Down", BANNER_WIDTH, gray) + red + "║" + reset);
        logger.info(red + "║" + " ".repeat(BANNER_WIDTH) + "║" + reset);
        logger.info(red + "║" + leftText("Cleaning announcements...", BANNER_WIDTH, yellow, red) + red + "║" + reset);
        logger.info(red + "║" + leftText("Disabling bossbars...", BANNER_WIDTH, yellow, red) + red + "║" + reset);
        logger.info(red + "╚" + "═".repeat(BANNER_WIDTH) + "╝" + reset);
        logger.info("");
    }

    // centra un texto dentro del ancho del banner
    private String centerText(String text, int width, String color) {
        int padding = (width - text.length()) / 2;
        return " ".repeat(Math.max(padding, 0)) + color + text + " ".repeat(Math.max(width - text.length() - padding, 0));
    }

    // alinea texto a la izquierda y rellena el resto con espacios
    private String leftText(String text, int width, String labelColor, String valueColor) {
        String[] parts = text.split(":", 2);
        if (parts.length < 2) {
            return labelColor + text + " ".repeat(Math.max(width - text.length(), 0));
        }
        String label = parts[0] + ":";
        String value = parts[1];
        int spaces = width - (label.length() + value.length());
        return labelColor + label + valueColor + value + " ".repeat(Math.max(spaces, 0));
    }

    @Inject
    public ZMAnnouncePlugin(ProxyServer proxyServer, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        showEnableBanner();
        this.configManager = new ConfigManager(this.logger, this.dataDirectory, ZMAnnouncePlugin.class);

        try {
            this.configManager.load();
        } catch (IOException exception) {
            this.logger.error("Failed to load config.yml for zMAnnounce.", exception);
            return;
        }

        applyRuntimeSettings(this.configManager.getConfig());

        this.announcementService = new AnnouncementService(this.proxyServer, this.configManager);
        this.bossbarManager = new BossbarManager(this, this.proxyServer, this.logger, this.configManager);
        this.versionChecker = new VersionChecker(this.logger);

        CommandManager commandManager = this.proxyServer.getCommandManager();
        this.announceCommand = new ZMAnnounceCommand(this, this.announcementService, this.bossbarManager);
        commandManager.register(
                commandManager.metaBuilder("zmannounce")
                        .aliases("announce")
                        .build(),
                this.announceCommand
        );

        this.cacheCleanupTask = this.proxyServer.getScheduler()
                .buildTask(this, () -> ColorUtil.cleanupCache())
                .repeat(5L, TimeUnit.MINUTES)
                .schedule();

        // Prevent long-running proxies from retaining stale UUID cooldown entries.
        this.cooldownCleanupTask = this.proxyServer.getScheduler()
                .buildTask(this, () -> {
                    if (this.announceCommand != null) {
                        int cooldownSeconds = this.configManager.getConfig().security().announcementCooldownSeconds();
                        this.announceCommand.cleanupCooldownMaps(cooldownSeconds);
                    }
                })
                .repeat(5L, TimeUnit.MINUTES)
                .schedule();

        this.logger.info("zMAnnounce enabled successfully.");

        // Check for updates asynchronously
        checkForUpdates();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        if (this.bossbarManager != null) {
            this.bossbarManager.removeViewer(event.getPlayer().getUniqueId());
        }
    }

    @Subscribe
    public void onPlayerChooseInitialServer(PlayerChooseInitialServerEvent event) {
        // Check if update notifications are enabled and there's a newer version
        if (this.configManager.getConfig().updates().notifyPlayersOnJoin() &&
            this.configManager.getConfig().updates().checkForUpdates() &&
            this.latestVersion != null &&
            !this.version.equals(this.latestVersion)) {

            // Send update notification to player
            event.getPlayer().sendMessage(ColorUtil.parse(
                "&8[&6zMAnnounce&8] &eA new version is available! &f(&7" + this.latestVersion + "&f)"
            ));

            // Send title notification
            event.getPlayer().showTitle(net.kyori.adventure.title.Title.title(
                ColorUtil.parse("&6✨ Update Available!"),
                ColorUtil.parse("&eNew version: &f" + this.latestVersion),
                net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis(500),  // fade in
                    java.time.Duration.ofMillis(3000), // stay
                    java.time.Duration.ofMillis(500)   // fade out
                )
            ));
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        showDisableBanner();
        if (this.cacheCleanupTask != null) {
            this.cacheCleanupTask.cancel();
        }
        if (this.cooldownCleanupTask != null) {
            this.cooldownCleanupTask.cancel();
        }
        if (this.bossbarManager != null) {
            this.bossbarManager.shutdown();
        }
    }

    public boolean reloadConfig() {
        if (this.configManager == null) {
            return false;
        }

        try {
            this.configManager.reload();
            applyRuntimeSettings(this.configManager.getConfig());
            return true;
        } catch (IOException exception) {
            this.logger.error("Failed to reload config.yml for zMAnnounce.", exception);
            return false;
        }
    }

    public Logger getLogger() {
        return this.logger;
    }

    public ProxyServer getProxyServer() {
        return this.proxyServer;
    }

    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    private void checkForUpdates() {
        if (this.versionChecker == null || !this.configManager.getConfig().updates().checkForUpdates()) {
            return;
        }

        this.versionChecker.checkForUpdates(this.modrinthProjectId, this.version)
                .thenAccept(latestVersion -> {
                    if (latestVersion.isPresent()) {
                        this.latestVersion = latestVersion.get(); // Store for player notifications
                        this.logger.info("");
                        this.logger.info("╔══════════════════════════════════════════════════════════════╗");
                        this.logger.info("║                          UPDATE AVAILABLE!                     ║");
                        this.logger.info("║                                                              ║");
                        this.logger.info("║  Current version: {}" + String.format("%" + (43 - this.version.length()) + "s", "") + "║", this.version);
                        this.logger.info("║  Latest version:  {}" + String.format("%" + (43 - latestVersion.get().length()) + "s", "") + "║", latestVersion.get());
                        this.logger.info("║                                                              ║");
                        this.logger.info("║  Download: https://modrinth.com/plugin/zmannounce           ║");
                        this.logger.info("╚══════════════════════════════════════════════════════════════╝");
                        this.logger.info("");
                    } else {
                        this.latestVersion = null; // No update available
                    }
                })
                .exceptionally(throwable -> {
                    this.logger.debug("Failed to check for updates", throwable);
                    this.latestVersion = null;
                    return null;
                });
    }

    private void applyRuntimeSettings(PluginConfig pluginConfig) {
        ColorUtil.configureCache(
                pluginConfig.performance().enableColorCache(),
                pluginConfig.performance().maxColorCacheSize()
        );
    }
}
