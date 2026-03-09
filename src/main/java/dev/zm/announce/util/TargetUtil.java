package dev.zm.announce.util;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class TargetUtil {

    private TargetUtil() {
    }

    public static Optional<String> resolveTarget(ProxyServer proxyServer, String target) {
        if (isAll(target)) {
            return Optional.of("all");
        }

        if (target == null || target.isBlank()) {
            return Optional.empty();
        }

        // Fast-path exact match.
        if (proxyServer.getServer(target).isPresent()) {
            return Optional.of(target);
        }

        // Case-insensitive fallback.
        return proxyServer.getAllServers()
                .stream()
                .map(RegisteredServer::getServerInfo)
                .map(serverInfo -> serverInfo.getName())
                .filter(serverName -> serverName.equalsIgnoreCase(target))
                .findFirst();
    }

    public static Collection<Player> playersForTarget(ProxyServer proxyServer, String resolvedTarget) {
        if (isAll(resolvedTarget)) {
            return proxyServer.getAllPlayers();
        }

        return proxyServer.getServer(resolvedTarget)
                .map(RegisteredServer::getPlayersConnected)
                .orElse(List.of());
    }

    private static boolean isAll(String value) {
        return value != null && value.equalsIgnoreCase("all");
    }
}
