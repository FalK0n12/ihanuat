package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.modules.TodayTimeTracker;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.ReconnectScheduler;
import com.ihanuat.mod.util.ClientUtils;
import com.ihanuat.mod.modules.profitTracker.ProfitManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

import javax.imageio.ImageIO;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * QuitThresholdManager
 * =====================
 * Hard-stops the macro after N hours of active farming time.
 *
 * When the threshold is reached:
 *  1. All tasks and scripts are cancelled.
 *  2. /sethome is sent.
 *  3. Macro fully stops; all reconnect timers are cleared.
 *  4. A Discord "Finished Farming" webhook is fired (green embed, full screenshot).
 *  5. Client disconnects to the title screen (intentional — no auto-reconnect).
 *  6. If forceQuitMinecraft is enabled, the JVM exits a few seconds later.
 *
 * The trigger is one-shot per macro session — pressing K to restart calls
 * reset(), re-arming the failsafe for the next session.
 *
 * Discord "Finished Farming" embed format:
 *   ||@here||
 *   [Green embed]
 *     Title:       Finished Farming
 *     Description: "You've farmed X, disconnecting safely."
 *                  (or "...disconnecting and closing your instance safely."
 *                   if forceQuitMinecraft is enabled)
 *   Screenshot: full screen capture taken just before disconnect.
 */
public class QuitThresholdManager {

    // Discord embed colour: green
    private static final int EMBED_COLOR_SESSION_DONE = 0x57F287;

    /** True once the quit sequence has been triggered this session. */
    private static volatile boolean triggered = false;

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Re-arms the failsafe. Call this when the user presses K to start. */
    public static void reset() {
        triggered = false;
    }

    /** Resets timer and re-arms. Zeroes session running time. */
    public static void resetThreshold() {
        triggered = false;
        com.ihanuat.mod.MacroStateManager.resetSession();
    }

    /**
     * Returns remaining ms before threshold, or -1 if disabled.
     */
    public static long getRemainingMs() {
        if (MacroConfig.quitThresholdHours <= 0) return -1L;
        long thresholdMs = (long)(MacroConfig.quitThresholdHours * 3_600_000.0);
        long elapsed     = TodayTimeTracker.getTodayMs();
        return Math.max(0L, thresholdMs - elapsed);
    }

    /**
     * Called every client tick while player is online.
     * Fires the one-shot shutdown sequence when the threshold is reached.
     */
    public static void update(Minecraft client) {
        if (triggered) return;
        if (client.player == null) return;
        if (!MacroStateManager.isMacroRunning()) return;
        if (MacroConfig.quitThresholdHours <= 0) return;

        long thresholdMs = (long)(MacroConfig.quitThresholdHours * 3_600_000.0);
        if (TodayTimeTracker.getTodayMs() < thresholdMs) return;

        triggered = true;
        triggerQuit(client);
    }

    // ── Shutdown sequence ──────────────────────────────────────────────────────

    private static void triggerQuit(Minecraft client) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "§c§l[Ihanuat] Quit Threshold reached ("
                    + String.format("%.2f", MacroConfig.quitThresholdHours) + "h)! "
                    + "Stopping all processes and disconnecting..."),
                    false);
        }

        MacroWorkerThread.getInstance().cancelCurrent();
        com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
        ClientUtils.forceReleaseKeys(client);
        MacroStateManager.setCurrentState(MacroState.State.OFF);

        MacroWorkerThread.getInstance().submit("QuitThreshold-Shutdown", () -> {
            try {
                MacroWorkerThread.sleep(400);

                // Build the "farmed X" string before /sethome so timing is accurate
                String farmedTime = buildFarmedTimeString();

                // ── /sethome ──────────────────────────────────────────────────
                ClientUtils.sendCommand(client, "/sethome");
                MacroWorkerThread.sleep(3000);

                // ── Send Discord "Finished Farming" webhook with full screenshot ──
                sendSessionDoneWebhookAsync(client, farmedTime);
                // Give the screenshot a moment to be taken before we disconnect
                MacroWorkerThread.sleep(3500);

                // ── Final cleanup ─────────────────────────────────────────────
                ReconnectScheduler.cancel();
                MacroStateManager.setIntentionalDisconnect(true);
                com.ihanuat.mod.modules.PestManager.reset();
                com.ihanuat.mod.modules.GearManager.reset();
                com.ihanuat.mod.modules.GeorgeManager.reset();
                com.ihanuat.mod.modules.BookCombineManager.reset();
                com.ihanuat.mod.modules.JunkManager.reset();
                com.ihanuat.mod.modules.RecoveryManager.reset();
                DynamicRestManager.reset();

                // ── Disconnect ────────────────────────────────────────────────
                client.execute(() -> {
                    try {
                        client.disconnect(new net.minecraft.client.gui.screens.TitleScreen(), false);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                // ── Optional: force-quit the JVM ─────────────────────────────
                if (MacroConfig.forceQuitMinecraft) {
                    MacroWorkerThread.sleep(4000);
                    System.exit(0);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ── "Finished Farming" Discord webhook ────────────────────────────────────────

    /**
     * Formats the total session farming time into "Xh Ym" or "Ym Zs".
     */
    private static String buildFarmedTimeString() {
        long totalMs   = TodayTimeTracker.getTodayMs();
        long totalSecs = totalMs / 1000;
        long hours     = totalSecs / 3600;
        long mins      = (totalSecs % 3600) / 60;
        long secs      = totalSecs % 60;
        if (hours > 0) return hours + "h " + mins + "m";
        if (mins  > 0) return mins  + "m " + secs + "s";
        return secs + "s";
    }

    /** Returns total session profit as a compact coin string. */
    private static String buildTotalProfitStr() {
        long total = ProfitManager.getTotalProfit("session");
        return compactCoins(total);
    }

    /**
     * Calculates average profit/hour over the full session duration.
     * Returns "—" if session is too short to be meaningful.
     */
    private static String buildAvgProfitPerHourStr() {
        long sessionMs = MacroStateManager.getSessionRunningTime();
        if (sessionMs < 5_000) return "\u2014";
        long totalProfit = ProfitManager.getTotalProfit("session");
        double hours = sessionMs / 3_600_000.0;
        long cph = (long) (totalProfit / hours);
        return compactCoins(cph);
    }

    /** Compact coin formatter — mirrors DiscordStatusManager.compactCoins. */
    private static String compactCoins(long value) {
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

    /**
     * Takes a full screenshot and sends a green Discord embed.
     *
     * Embed format:
     *   ||@here||
     *   [Green embed]
     *     Title:       Finished Farming
     *     Description: "You've farmed X, disconnecting safely."
     *                  or "...disconnecting and closing your instance safely."
     *     Image:       full screenshot
     */
    private static void sendSessionDoneWebhookAsync(Minecraft client, String farmedTime) {
        if (MacroConfig.discordWebhookUrl == null || MacroConfig.discordWebhookUrl.isBlank()) return;

        final long captureTime = System.currentTimeMillis();
        client.execute(() -> {
            try {
                Screenshot.grab(client.gameDirectory, client.getMainRenderTarget(), msg -> {});
            } catch (Exception e) {
                System.err.println("[Ihanuat] QuitThreshold screenshot error: " + e.getMessage());
            }
        });

        Thread t = new Thread(() -> {
            try { Thread.sleep(2500); } catch (InterruptedException ignored) {}

            File screenshotFile = null;
            try {
                File dir = new File(client.gameDirectory, "screenshots");
                if (dir.exists()) {
                    screenshotFile = Files.list(dir.toPath())
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".png"))
                            .filter(p -> p.toFile().lastModified() >= captureTime - 500)
                            .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                            .map(Path::toFile).orElse(null);
                }
            } catch (Exception e) {
                System.err.println("[Ihanuat] QuitThreshold screenshot lookup error: " + e.getMessage());
            }

            String description = MacroConfig.forceQuitMinecraft
                    ? "You've farmed " + farmedTime + ", disconnecting and closing your instance safely."
                    : "You've farmed " + farmedTime + ", disconnecting safely.";
            String totalProfitStr    = buildTotalProfitStr();
            String avgProfitPerHour  = buildAvgProfitPerHourStr();

            try {
                if (screenshotFile != null) {
                    sendSessionDoneMultipart(MacroConfig.discordWebhookUrl, description,
                            totalProfitStr, avgProfitPerHour, screenshotFile);
                    screenshotFile.delete();
                } else {
                    sendSessionDoneText(MacroConfig.discordWebhookUrl, description,
                            totalProfitStr, avgProfitPerHour);
                }
            } catch (Exception e) {
                System.err.println("[Ihanuat] QuitThreshold webhook send error: " + e.getMessage());
                try { sendSessionDoneText(MacroConfig.discordWebhookUrl, description,
                        totalProfitStr, avgProfitPerHour); } catch (Exception ignored) {}
            }
        }, "ihanuat-session-done-webhook");
        t.setDaemon(true);
        t.start();
    }

    private static void sendSessionDoneMultipart(String webhookUrl, String description,
            String totalProfit, String avgProfitPerHour, File imageFile)
            throws Exception {
        String boundary = "IhanuatBoundary" + System.currentTimeMillis();
        URL url = new URI(webhookUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("User-Agent", "Java-Ihanuat-SessionDone/1.0");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        // Embed has description + two inline fields: Total Profit | Avg Profit/Hour
        String json = "{"
                + "\"content\":\"||@here||\","
                + "\"embeds\":[{"
                + "\"title\":\"Finished Farming\","
                + "\"description\":\"" + jsonEscape(description) + "\","
                + "\"color\":" + EMBED_COLOR_SESSION_DONE + ","
                + "\"fields\":["
                +   "{\"name\":\"Total Profit\","
                +    "\"value\":\"`" + jsonEscape(totalProfit) + "`\","
                +    "\"inline\":true},"
                +   "{\"name\":\"Avg Profit/Hour\","
                +    "\"value\":\"`" + jsonEscape(avgProfitPerHour) + "`\","
                +    "\"inline\":true}"
                + "],"
                + "\"image\":{\"url\":\"attachment://" + imageFile.getName() + "\"}"
                + "}]}";

        try (OutputStream os = conn.getOutputStream();
             PrintWriter w = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true)) {
            w.append("--").append(boundary).append("\r\n");
            w.append("Content-Disposition: form-data; name=\"payload_json\"\r\n");
            w.append("Content-Type: application/json; charset=UTF-8\r\n\r\n");
            w.append(json).append("\r\n");
            w.append("--").append(boundary).append("\r\n");
            w.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
             .append(imageFile.getName()).append("\"\r\n");
            w.append("Content-Type: image/png\r\n\r\n");
            w.flush();
            Files.copy(imageFile.toPath(), os);
            os.flush();
            w.append("\r\n").append("--").append(boundary).append("--\r\n");
            w.flush();
        }

        int code = conn.getResponseCode();
        System.out.println("[Ihanuat] SessionDone webhook (multipart): HTTP " + code);
        conn.disconnect();
    }

    private static void sendSessionDoneText(String webhookUrl, String description,
            String totalProfit, String avgProfitPerHour) throws Exception {
        String payload = "{"
                + "\"content\":\"||@here||\","
                + "\"embeds\":[{"
                + "\"title\":\"Finished Farming\","
                + "\"description\":\"" + jsonEscape(description) + "\","
                + "\"color\":" + EMBED_COLOR_SESSION_DONE + ","
                + "\"fields\":["
                +   "{\"name\":\"Total Profit\","
                +    "\"value\":\"`" + jsonEscape(totalProfit) + "`\","
                +    "\"inline\":true},"
                +   "{\"name\":\"Avg Profit/Hour\","
                +    "\"value\":\"`" + jsonEscape(avgProfitPerHour) + "`\","
                +    "\"inline\":true}"
                + "]"
                + "}]}";

        URL url = new URI(webhookUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("User-Agent", "Java-Ihanuat-SessionDone/1.0");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        System.out.println("[Ihanuat] SessionDone webhook (text-only): HTTP " + code);
        conn.disconnect();
    }

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
