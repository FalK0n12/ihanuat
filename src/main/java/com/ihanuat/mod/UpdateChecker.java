package com.ihanuat.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

// checks github for a newer version and tells the player in chat
public final class UpdateChecker {

    private static final String API_URL =
            "https://api.github.com/repos/jetzalel/ihanuat/releases/latest";
    private static final String RELEASES_URL =
            "https://github.com/jetzalel/ihanuat/releases/latest";
    private static final int TIMEOUT_MS = 8_000;

    private static volatile String cachedLatestVersion = null;
    private static volatile boolean fetchStarted = false;

    // call this once after the player joins a server
    public static void checkAndNotify(Minecraft client) {
        if (!fetchStarted) {
            fetchStarted = true;
            CompletableFuture.runAsync(() -> {
                String latest = fetchLatestTag();
                cachedLatestVersion = (latest != null) ? latest : "";
                if (latest != null) {
                    notifyIfOutdated(client, latest);
                }
            });
        } else if (cachedLatestVersion != null && !cachedLatestVersion.isEmpty()) {
            // already fetched, just re-notify on rejoin
            notifyIfOutdated(client, cachedLatestVersion);
        }
    }

    public static String getCachedLatestVersion() {
        return cachedLatestVersion;
    }

    private static String fetchLatestTag() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(API_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", "ihanuat-mod");
            connection.setRequestProperty("Accept", "application/vnd.github+json");

            int status = connection.getResponseCode();
            if (status != 200) {
                Ihanuat.LOGGER.warn("[ihanuat] UpdateChecker: GitHub API returned HTTP {}", status);
                return null;
            }

            try (InputStream in = connection.getInputStream();
                 InputStreamReader reader = new InputStreamReader(in)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                if (obj.has("tag_name")) {
                    return obj.get("tag_name").getAsString().trim();
                }
            }
        } catch (IOException e) {
            Ihanuat.LOGGER.warn("[ihanuat] UpdateChecker: failed to reach GitHub – {}", e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
        return null;
    }

    private static void notifyIfOutdated(Minecraft client, String latestTag) {
        String currentVersion = getCurrentVersion();
        String normalizedLatest  = normalizeTag(latestTag);
        String normalizedCurrent = normalizeTag(currentVersion);

        // only notify if latest is actually newer, not just different
        if (!isNewer(normalizedLatest, normalizedCurrent)) {
            Ihanuat.LOGGER.info("[ihanuat] UpdateChecker: up to date ({})", currentVersion);
            return;
        }

        Ihanuat.LOGGER.info("[ihanuat] UpdateChecker: update available – current={} latest={}",
                currentVersion, latestTag);

        client.execute(() -> {
            if (client.player == null) return;

            MutableComponent prefix = Component.literal("§8[§6ihanuat§8] ");
            MutableComponent message = Component.literal(
                    "§eUpdate available! §7Current: §c" + currentVersion
                    + " §7→ Latest: §a" + latestTag + " ");
            MutableComponent link = Component.literal("§b§n[Download]")
                    .withStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent.OpenUrl(java.net.URI.create(RELEASES_URL)))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(
                                    Component.literal("§7Open GitHub releases page"))));

            client.player.displayClientMessage(prefix.append(message).append(link), false);
        });
    }

    // reads version from fabric.mod.json
    private static String getCurrentVersion() {
        return FabricLoader.getInstance()
                .getModContainer("ihanuat")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    // strips leading "v" so "v1.2.3" and "1.2.3" match
    private static String normalizeTag(String tag) {
        if (tag == null) return "";
        return tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
    }

    // returns true if candidate is strictly newer than current using semver comparison
    // e.g. isNewer("3.0.0-r2", "3.0.0-r1") = true, isNewer("3.1.0-r1", "3.0.9-r9") = true
    static boolean isNewer(String candidate, String current) {
        VersionParts candidateParts = parseVersion(candidate);
        VersionParts currentParts = parseVersion(current);

        for (int i = 0; i < Math.max(candidateParts.numbers.length, currentParts.numbers.length); i++) {
            int a = i < candidateParts.numbers.length ? candidateParts.numbers[i] : 0;
            int b = i < currentParts.numbers.length ? currentParts.numbers[i] : 0;
            if (a > b) return true;
            if (a < b) return false;
        }

        return candidateParts.revision > currentParts.revision;
    }

    private static VersionParts parseVersion(String version) {
        if (version == null || version.isEmpty()) return new VersionParts(new int[]{0}, 0);

        String clean = version.split("\\+")[0];
        int revision = 0;

        int revisionMarker = clean.lastIndexOf("-r");
        if (revisionMarker >= 0) {
            String revisionPart = clean.substring(revisionMarker + 2);
            try {
                revision = Integer.parseInt(revisionPart);
                clean = clean.substring(0, revisionMarker);
            } catch (NumberFormatException ignored) {
                // Leave revision at 0 if the suffix is malformed.
            }
        } else {
            int suffixMarker = clean.indexOf('-');
            if (suffixMarker >= 0) {
                clean = clean.substring(0, suffixMarker);
            }
        }

        String[] parts = clean.split("\\.");
        int[] nums = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try { nums[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException e) { nums[i] = 0; }
        }
        return new VersionParts(nums, revision);
    }

    private record VersionParts(int[] numbers, int revision) {
    }
}
