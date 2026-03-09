package dev.zm.announce.announce;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.zm.announce.config.ConfigManager;
import dev.zm.announce.config.PluginConfig;
import dev.zm.announce.util.ColorUtil;
import dev.zm.announce.util.SoundUtil;
import dev.zm.announce.util.TargetUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

public final class AnnouncementService {

    private final ProxyServer proxyServer;
    private final ConfigManager configManager;

    public AnnouncementService(ProxyServer proxyServer, ConfigManager configManager) {
        this.proxyServer = proxyServer;
        this.configManager = configManager;
    }

    public AnnouncementResult sendAnnouncement(String message, String target) {
        Optional<String> resolvedTarget = TargetUtil.resolveTarget(this.proxyServer, target);
        if (resolvedTarget.isEmpty()) {
            return AnnouncementResult.failure("Server '" + target + "' does not exist.");
        }

        String finalTarget = resolvedTarget.get();
        Collection<Player> players = TargetUtil.playersForTarget(this.proxyServer, finalTarget);

        PluginConfig pluginConfig = this.configManager.getConfig();
        PluginConfig.AnnounceTemplate template = pluginConfig.announceTemplate();

        List<Component> components = new ArrayList<>(template.messages().size());
        for (String line : template.messages()) {
            components.add(ColorUtil.parse(line.replace("%message%", message)));
        }

        PluginConfig.TitleSettings titleSettings = template.title();
        Title title = null;
        if (titleSettings.enabled()) {
            title = Title.title(
                    ColorUtil.parse(titleSettings.title().replace("%message%", message)),
                    ColorUtil.parse(titleSettings.subtitle().replace("%message%", message)),
                    Title.Times.times(
                            Duration.ofMillis(titleSettings.fadeIn() * 50L),
                            Duration.ofMillis(titleSettings.stay() * 50L),
                            Duration.ofMillis(titleSettings.fadeOut() * 50L)
                    )
            );
        }

        Optional<Sound> sound = SoundUtil.parseSound(template.sound());

        for (Player player : players) {
            for (Component component : components) {
                player.sendMessage(component);
            }
            if (title != null) {
                player.showTitle(title);
            }
            sound.ifPresent(player::playSound);
        }

        return AnnouncementResult.success(finalTarget, players.size());
    }
}
