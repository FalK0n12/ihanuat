package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.modules.profitTracker.ProfitManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * DiscordStatusManager
 * =====================
 * Periodically posts a Status Update embed to the configured Discord webhook.
 *
 * Embed format (blue, no description):
 *   Title:  "Status Update"
 *   Fields (all inline, left-to-right):
 *     1. Session Time  — HH:MM:SS or MM:SS depending on length
 *     2. Next Rest     — countdown to next Dynamic Rest, "Resting now…", or "—"
 *     3. Profit/Hour   — compact coins-per-hour from the session profit tracker
 *                        e.g. 55,300,120 → "55.3m", 4,200,000 → "4.2m",
 *                             980,000 → "980k", 50,000 → "50k"
 *   Image: full screenshot attached
 *
 * "Current State" field removed — redundant; the screenshot shows it.
 * "Here is your latest Ihanuat status update! :rocket:" description removed.
 * "Time Until Next Rest" renamed to "Next Rest" for compactness.
 */
public class DiscordStatusManager {

    // Blue embed colour matching the Status Manager aesthetic
    private static final int EMBED_COLOR_STATUS = 0x5865F2;

    private static long lastUpdateTime = System.currentTimeMillis();
    private static boolean isTakingScreenshot = false;
    private static long screenshotRequestTime = 0;

    public static void update(Minecraft client) {
        if (!MacroConfig.sendDiscordStatus
                || MacroConfig.discordWebhookUrl == null
                || MacroConfig.discordWebhookUrl.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!isTakingScreenshot && now - lastUpdateTime >= MacroConfig.discordStatusUpdateTime * 60_000L) {
            lastUpdateTime = now;
            takeAndSendScreenshot(client);
        }

        // 2-second wait after requesting screenshot gives Minecraft time to flush PNG
        if (isTakingScreenshot && now - screenshotRequestTime > 2000) {
            isTakingScreenshot = false;
            sendScreenshotAsync(client);
        }
    }

    private static void takeAndSendScreenshot(Minecraft client) {
        isTakingScreenshot = true;
        screenshotRequestTime = System.currentTimeMillis();
        client.execute(() ->
            Screenshot.grab(client.gameDirectory, client.getMainRenderTarget(), msg -> {})
        );
    }

    private static void sendScreenshotAsync(Minecraft client) {
        // Capture the exact request timestamp so the async thread can filter precisely.
        final long captureTime = screenshotRequestTime;
        new Thread(() -> {
            try {
                File screenshotsDir = new File(client.gameDirectory, "screenshots");
                if (!screenshotsDir.exists()) return;

                // Only pick a screenshot written AFTER this status request was made
                // (allow 1 s tolerance for slow disks).
                // Explicitly exclude any ihanuat temp/crop files (e.g. ihanuat_chat_tmp.png)
                // so a recently-fired chat alert can never be mistaken for the status shot.
                File latestScreenshot = Files.list(screenshotsDir.toPath())
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".png"))
                        .filter(p -> !p.getFileName().toString().startsWith("ihanuat_"))
                        .filter(p -> p.toFile().lastModified() >= captureTime - 1_000)
                        .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                        .map(Path::toFile).orElse(null);

                if (latestScreenshot == null) return;

                sendWebhook(MacroConfig.discordWebhookUrl, latestScreenshot);
                try { Files.deleteIfExists(latestScreenshot.toPath()); } catch (Exception ignored) {}

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // ── Embed data builders ────────────────────────────────────────────────────

    /** Formats session running time as HH:MM:SS or MM:SS. */
    private static String buildSessionStr() {
        long totalSecs = MacroStateManager.getSessionRunningTime() / 1000;
        long h = totalSecs / 3600;
        long m = (totalSecs % 3600) / 60;
        long s = totalSecs % 60;
        return h > 0
                ? String.format("%02d:%02d:%02d", h, m, s)
                : String.format("%02d:%02d", m, s);
    }

    /** Formats the time remaining until the next Dynamic Rest. */
    private static String buildNextRestStr() {
        long nextRestTriggerMs = DynamicRestManager.getNextRestTriggerMs();
        if (DynamicRestManager.isRestPending()) return "Resting now\u2026";
        if (nextRestTriggerMs <= 0) return "\u2014";

        long remaining = nextRestTriggerMs - System.currentTimeMillis();
        if (remaining <= 0) return "Starting soon\u2026";

        long totalSecs = remaining / 1000;
        long h = totalSecs / 3600;
        long m = (totalSecs % 3600) / 60;
        long s = totalSecs % 60;
        return h > 0
                ? String.format("%02d:%02d:%02d", h, m, s)
                : String.format("%02d:%02d", m, s);
    }

    /**
     * Computes coins-per-hour from the session profit tracker and formats it
     * compactly:
     *   ≥ 1,000,000,000 → "X.Xb"
     *   ≥ 1,000,000     → "X.Xm"
     *   ≥ 1,000         → "Xk"
     *   otherwise       → raw number
     */
    private static String buildProfitPerHourStr() {
        long sessionMs = MacroStateManager.getSessionRunningTime();
        if (sessionMs < 5_000) return "\u2014"; // need at least 5 s of data

        long totalProfit = ProfitManager.getTotalProfit("session");
        double hours = sessionMs / 3_600_000.0;
        long cph = (long) (totalProfit / hours);

        return compactCoins(cph);
    }

    /**
     * Compact coin formatter shared across all status embeds.
     *   1_234_567_890  → "1.2b"
     *     55_300_120   → "55.3m"
     *      4_200_000   → "4.2m"
     *        980_000   → "980k"
     *         50_000   → "50k"
     *          1_234   → "1.2k"
     *            999   → "999"
     */
    public static String compactCoins(long value) {
        if (value < 0) return "-" + compactCoins(-value);
        if (value >= 1_000_000_000L) {
            String s = String.format("%.1fb", value / 1_000_000_000.0);
            return s.endsWith(".0b") ? s.replace(".0b", "b") : s;
        }
        if (value >= 1_000_000L) {
            String s = String.format("%.1fm", value / 1_000_000.0);
            return s.endsWith(".0m") ? s.replace(".0m", "m") : s;
        }
        if (value >= 1_000L) {
            String s = String.format("%.1fk", value / 1_000.0);
            return s.endsWith(".0k") ? s.replace(".0k", "k") : s;
        }
        return String.valueOf(value);
    }

    // ── Webhook sender ─────────────────────────────────────────────────────────

    private static void sendWebhook(String webhookUrl, File imageFile) throws Exception {
        String boundary = "===" + System.currentTimeMillis() + "===";
        URL url = new java.net.URI(webhookUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("User-Agent", "Java-Ihanuat-Status/1.0");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        String sessionStr     = buildSessionStr();
        String nextRestStr    = buildNextRestStr();
        String profitPerHour  = buildProfitPerHourStr();
        String jsonFileName   = imageFile.getName();

        // Clean, no-description embed: title only + 3 inline fields + attached image.
        // Fields order: Session Time | Next Rest | Profit/Hour
        String json = "{"
                + "\"embeds\":[{"
                + "\"title\":\"Status Update\","
                + "\"color\":" + EMBED_COLOR_STATUS + ","
                + "\"fields\":["
                +   "{\"name\":\"Session Time\","
                +    "\"value\":\"`" + jsonEscape(sessionStr)    + "`\","
                +    "\"inline\":true},"
                +   "{\"name\":\"Next Rest\","
                +    "\"value\":\"`" + jsonEscape(nextRestStr)   + "`\","
                +    "\"inline\":true},"
                +   "{\"name\":\"Profit/Hour\","
                +    "\"value\":\"`" + jsonEscape(profitPerHour) + "`\","
                +    "\"inline\":true}"
                + "],"
                + "\"image\":{\"url\":\"attachment://" + jsonFileName + "\"}"
                + "}]}";

        try (OutputStream os = conn.getOutputStream();
             PrintWriter w = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true)) {

            // Part 1: JSON payload
            w.append("--").append(boundary).append("\r\n");
            w.append("Content-Disposition: form-data; name=\"payload_json\"\r\n");
            w.append("Content-Type: application/json; charset=UTF-8\r\n\r\n");
            w.append(json).append("\r\n");

            // Part 2: screenshot
            w.append("--").append(boundary).append("\r\n");
            w.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
             .append(jsonFileName).append("\"\r\n");
            w.append("Content-Type: image/png\r\n\r\n");
            w.flush();
            Files.copy(imageFile.toPath(), os);
            os.flush();
            w.append("\r\n").append("--").append(boundary).append("--\r\n");
            w.flush();
        }

        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            System.out.println("[Ihanuat] Status webhook sent: HTTP " + code);
        } else {
            System.err.println("[Ihanuat] Status webhook failed: HTTP " + code);
        }
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
