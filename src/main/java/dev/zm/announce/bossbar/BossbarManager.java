package dev.zm.announce.bossbar;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import dev.zm.announce.ZMAnnouncePlugin;
import dev.zm.announce.config.ConfigManager;
import dev.zm.announce.config.PluginConfig;
import dev.zm.announce.util.ColorUtil;
import dev.zm.announce.util.SoundUtil;
import dev.zm.announce.util.TargetUtil;
import dev.zm.announce.util.TimeUtil;
import java.time.Duration;
import java.util.Collection;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;

public final class BossbarManager {

    private final ZMAnnouncePlugin plugin;
    private final ProxyServer proxyServer;
    private final Logger logger;
    private final ConfigManager configManager;

    private final Map<String, ActiveBossbar> activeBossbars = new ConcurrentHashMap<>();
    private volatile ScheduledTask tickerTask;

    public BossbarManager(
            ZMAnnouncePlugin plugin,
            ProxyServer proxyServer,
            Logger logger,
            ConfigManager configManager
    ) {
        this.plugin = plugin;
        this.proxyServer = proxyServer;
        this.logger = logger;
        this.configManager = configManager;
    }

    /**
     * Returns the current number of active bossbars.
     * Used for security limits.
     */
    public int getActiveBossbarCount() {
        return this.activeBossbars.size();
    }

    public BossbarStartResult startBossbar(String message, Duration duration, String target) {
        Optional<String> resolvedTarget = TargetUtil.resolveTarget(this.proxyServer, target);
        if (resolvedTarget.isEmpty()) {
            return BossbarStartResult.failure("Server '" + target + "' does not exist.");
        }

        String finalTarget = resolvedTarget.get();
        String key = buildKey(finalTarget);
        int maxActiveBossbars = Math.max(1, this.configManager.getConfig().security().maxActiveBossbars());

        ActiveBossbar previous;
        synchronized (this) {
            // Limit check + replacement decision are done atomically.
            previous = this.activeBossbars.get(key);
            if (previous == null && this.activeBossbars.size() >= maxActiveBossbars) {
                return BossbarStartResult.failure("Maximum active bossbars reached.");
            }

            PluginConfig.BossbarSettings settings = this.configManager.getConfig().bossbar();
            BossBar bossBar = BossBar.bossBar(
                    ColorUtil.parse(message),
                    1.0f,
                    parseColor(settings.color()),
                    parseOverlay(settings.overlay())
            );

            ActiveBossbar activeBossbar = new ActiveBossbar(
                    key,
                    finalTarget,
                    message,
                    duration.toMillis(),
                    System.currentTimeMillis(),
                    bossBar
            );

            this.activeBossbars.put(key, activeBossbar);
            ensureTicker();
        }

        if (previous != null) {
            hideFromAllViewers(previous);
        }

        ActiveBossbar activeBossbar = this.activeBossbars.get(key);
        if (activeBossbar == null) {
            return BossbarStartResult.failure("Could not start bossbar due to concurrent update.");
        }
        updateBossbar(activeBossbar, System.currentTimeMillis(), true);

        int recipients = TargetUtil.playersForTarget(this.proxyServer, finalTarget).size();
        return BossbarStartResult.success(finalTarget, recipients);
    }

    public void removeViewer(UUID playerId) {
        for (ActiveBossbar activeBossbar : this.activeBossbars.values()) {
            activeBossbar.viewerIds.remove(playerId);
        }
    }

    public synchronized void shutdown() {
        if (this.tickerTask != null) {
            this.tickerTask.cancel();
            this.tickerTask = null;
        }

        for (ActiveBossbar activeBossbar : this.activeBossbars.values()) {
            hideFromAllViewers(activeBossbar);
        }
        this.activeBossbars.clear();
    }

    private synchronized void ensureTicker() {
        if (this.tickerTask != null) {
            return;
        }

        // Single scheduler updates all active bossbars once per second.
        this.tickerTask = this.proxyServer.getScheduler()
                .buildTask(this.plugin, this::tick)
                .repeat(1L, TimeUnit.SECONDS)
                .schedule();
    }

    private void tick() {
        if (this.activeBossbars.isEmpty()) {
            stopTickerIfIdle();
            return;
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<String, ActiveBossbar> entry : this.activeBossbars.entrySet()) {
            ActiveBossbar activeBossbar = entry.getValue();
            boolean stillActive = updateBossbar(activeBossbar, now, false);
            if (!stillActive) {
                this.activeBossbars.remove(entry.getKey(), activeBossbar);
            }
        }

        stopTickerIfIdle();
    }

    private void stopTickerIfIdle() {
        if (!this.activeBossbars.isEmpty()) {
            return;
        }
        synchronized (this) {
            // Double-check inside lock to avoid cancelling a just-started ticker.
            if (this.activeBossbars.isEmpty() && this.tickerTask != null) {
                this.tickerTask.cancel();
                this.tickerTask = null;
            }
        }
    }

    private boolean updateBossbar(ActiveBossbar activeBossbar, long now, boolean playStartSound) {
        long elapsedMillis = now - activeBossbar.startMillis;
        long remainingMillis = activeBossbar.totalMillis - elapsedMillis;

        Collection<Player> targetedPlayers = TargetUtil.playersForTarget(this.proxyServer, activeBossbar.target);

        // Prune stale viewers without creating temporary sets each tick.
        Iterator<UUID> viewerIterator = activeBossbar.viewerIds.iterator();
        while (viewerIterator.hasNext()) {
            UUID viewerId = viewerIterator.next();
            Optional<Player> viewer = this.proxyServer.getPlayer(viewerId);
            if (viewer.isEmpty()) {
                viewerIterator.remove();
                continue;
            }
            if (!isPlayerInTarget(viewer.get(), activeBossbar.target)) {
                viewer.get().hideBossBar(activeBossbar.bossBar);
                viewerIterator.remove();
            }
        }

        for (Player player : targetedPlayers) {
            if (activeBossbar.viewerIds.add(player.getUniqueId())) {
                player.showBossBar(activeBossbar.bossBar);
            }
        }

        if (playStartSound) {
            Optional<Sound> startSound = SoundUtil.parseSound(this.configManager.getConfig().bossbar().soundStart());
            for (Player player : targetedPlayers) {
                startSound.ifPresent(player::playSound);
            }
        }

        if (remainingMillis <= 0L) {
            finishBossbar(activeBossbar);
            return false;
        }

        float progress = (float) Math.max(0.0D, Math.min(1.0D, (double) remainingMillis / (double) activeBossbar.totalMillis));
        activeBossbar.bossBar.progress(progress);

        String formattedRemaining = TimeUtil.formatRemaining(Duration.ofMillis(remainingMillis));
        String displayMessage = activeBossbar.rawMessage.contains("%time%")
                ? activeBossbar.rawMessage.replace("%time%", formattedRemaining)
                : activeBossbar.rawMessage + " &7(" + formattedRemaining + ")";
        // Do not cache dynamic countdown text; avoids cache pollution.
        activeBossbar.bossBar.name(ColorUtil.parse(displayMessage, false));

        return true;
    }

    private void finishBossbar(ActiveBossbar activeBossbar) {
        PluginConfig.BossbarSettings bossbarSettings = this.configManager.getConfig().bossbar();

        Iterator<UUID> iterator = activeBossbar.viewerIds.iterator();
        while (iterator.hasNext()) {
            UUID viewerId = iterator.next();
            this.proxyServer.getPlayer(viewerId).ifPresent(player -> player.hideBossBar(activeBossbar.bossBar));
            iterator.remove();
        }

        Collection<Player> targetedPlayers = TargetUtil.playersForTarget(this.proxyServer, activeBossbar.target);

        PluginConfig.EndTitleSettings endTitleSettings = bossbarSettings.endTitle();
        Title endTitle = Title.title(
                ColorUtil.parse(endTitleSettings.title()),
                ColorUtil.parse(endTitleSettings.subtitle()),
                Title.Times.times(
                        Duration.ofMillis(endTitleSettings.fadeIn() * 50L),
                        Duration.ofMillis(endTitleSettings.stay() * 50L),
                        Duration.ofMillis(endTitleSettings.fadeOut() * 50L)
                )
        );

        Optional<Sound> endSound = SoundUtil.parseSound(bossbarSettings.soundEnd());
        for (Player player : targetedPlayers) {
            player.showTitle(endTitle);
            endSound.ifPresent(player::playSound);
        }
    }

    private void hideFromAllViewers(ActiveBossbar activeBossbar) {
        Iterator<UUID> iterator = activeBossbar.viewerIds.iterator();
        while (iterator.hasNext()) {
            UUID viewerId = iterator.next();
            this.proxyServer.getPlayer(viewerId).ifPresent(player -> player.hideBossBar(activeBossbar.bossBar));
            iterator.remove();
        }
    }

    private boolean isPlayerInTarget(Player player, String target) {
        if (target.equalsIgnoreCase("all")) {
            return true;
        }
        return player.getCurrentServer()
                .map(serverConnection -> serverConnection.getServerInfo().getName().equalsIgnoreCase(target))
                .orElse(false);
    }

    private String buildKey(String resolvedTarget) {
        if (resolvedTarget.equalsIgnoreCase("all")) {
            return "all";
        }
        return "server:" + resolvedTarget.toLowerCase(Locale.ROOT);
    }

    private BossBar.Color parseColor(String colorName) {
        try {
            return BossBar.Color.valueOf(colorName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            this.logger.warn("Invalid bossbar color '{}' in config.yml. Using YELLOW.", colorName);
            return BossBar.Color.YELLOW;
        }
    }

    private BossBar.Overlay parseOverlay(String overlayName) {
        try {
            return BossBar.Overlay.valueOf(overlayName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            this.logger.warn("Invalid bossbar overlay '{}' in config.yml. Using PROGRESS.", overlayName);
            return BossBar.Overlay.PROGRESS;
        }
    }

    private static final class ActiveBossbar {
        private final String key;
        private final String target;
        private final String rawMessage;
        private final long totalMillis;
        private final long startMillis;
        private final BossBar bossBar;
        private final Set<UUID> viewerIds = ConcurrentHashMap.newKeySet();

        private ActiveBossbar(
                String key,
                String target,
                String rawMessage,
                long totalMillis,
                long startMillis,
                BossBar bossBar
        ) {
            this.key = key;
            this.target = target;
            this.rawMessage = rawMessage;
            this.totalMillis = totalMillis;
            this.startMillis = startMillis;
            this.bossBar = bossBar;
        }
    }
}
