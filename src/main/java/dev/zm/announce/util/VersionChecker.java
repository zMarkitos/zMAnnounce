package dev.zm.announce.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Checks for plugin updates using the Modrinth API.
 */
public final class VersionChecker {

    private static final String MODRINTH_API_URL = "https://api.modrinth.com/v2/project/%s/version";
    private static final String USER_AGENT = "zMAnnounce/%s (https://github.com/zMarkitos_/zMAnnounce)";

    private final HttpClient httpClient;
    private final Gson gson;
    private final Logger logger;

    public VersionChecker(Logger logger) {
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }

    /**
     * Checks if there's a newer version available on Modrinth.
     * Returns a link to the version page if newer.
     *
     * @param projectId The Modrinth project ID/slug
     * @param currentVersion The current plugin version
     * @return A CompletableFuture with an Optional containing the link to the new version
     */
    public CompletableFuture<Optional<String>> checkForUpdates(String projectId, String currentVersion) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = String.format(MODRINTH_API_URL, projectId);
                String userAgent = String.format(USER_AGENT, currentVersion);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", userAgent)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    // Parse latest version
                    Optional<String> latestVersion = parseLatestVersion(response.body(), currentVersion);
                    // If newer, return nice Modrinth link
                    return latestVersion.map(v -> "https://modrinth.com/plugin/zmannounce/version/" + v);
                } else {
                    logger.warn("Failed to check for updates: HTTP {} from Modrinth API", response.statusCode());
                }

            } catch (IOException | InterruptedException e) {
                logger.warn("Failed to check for updates", e);
                Thread.currentThread().interrupt();
            }

            return Optional.empty();
        });
    }

    private Optional<String> parseLatestVersion(String jsonResponse, String currentVersion) {
        try {
            JsonArray versions = gson.fromJson(jsonResponse, JsonArray.class);
            if (versions.size() == 0) return Optional.empty();

            // Get first (latest) version
            JsonObject latestVersion = versions.get(0).getAsJsonObject();
            String latestVersionNumber = latestVersion.get("version_number").getAsString();

            if (isNewerVersion(latestVersionNumber, currentVersion)) {
                return Optional.of(latestVersionNumber);
            }

        } catch (Exception e) {
            logger.warn("Failed to parse version response from Modrinth", e);
        }

        return Optional.empty();
    }

    private boolean isNewerVersion(String latest, String current) {
        String[] latestParts = latest.split("\\.");
        String[] currentParts = current.split("\\.");

        for (int i = 0; i < Math.max(latestParts.length, currentParts.length); i++) {
            int latestNum = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
            int currentNum = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            if (latestNum > currentNum) return true;
            if (latestNum < currentNum) return false;
        }
        return false;
    }
}