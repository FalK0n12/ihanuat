package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * ChatRuleManager
 * ================
 * Listens for chat messages through the single authoritative injection point:
 * {@link com.ihanuat.mod.mixin.ChatHudMixin#onAddMessage}.  That mixin fires
 * for EVERY message that reaches the HUD (player chat, system messages, NPC
 * text, Hypixel broadcasts, action-bar is excluded by Minecraft itself before
 * ChatComponent.addMessage is called for most cases).
 *
 * Previous implementation registered both ClientReceiveMessageEvents.GAME and
 * ClientReceiveMessageEvents.CHAT in IhanuatClient AND had the mixin — meaning
 * every matching message fired the webhook three times.  That is now fixed:
 * the Fabric GAME/CHAT registrations have been removed; only the mixin remains.
 *
 * A per-message dedup guard (sentForCurrentMessage) additionally prevents any
 * accidental double-fire from Minecraft's own message pipeline.
 *
 * Rules stored as: "name|matchType|caseSensitive|pingWebhook|enabled|matchText"
 *
 * Discord embed format (Chat Alert):
 *   ||@here||
 *   [Red embed]
 *   Title:       Chat Alert
 *   Description: '{full triggering message}'
 *   Screenshot:  Full screenshot cropped to the BOTTOM-LEFT quadrant:
 *                left 50% of width, bottom 50% of height.
 *                This keeps the chat area clearly visible while removing
 *                the top half (sky / map) and right half (inventory / hotbar).
 */
public class ChatRuleManager {

    // ── Crop ratios ────────────────────────────────────────────────────────────
    // Bottom-left quadrant:
    //   X: 0 % → 50 %  (keep left half)
    //   Y: 50 % → 100 % (keep bottom half)
    private static final double CROP_X_START = 0.00;
    private static final double CROP_WIDTH   = 0.50;   // left 50 %
    private static final double CROP_Y_START = 0.50;   // start at vertical midpoint
    private static final double CROP_Y_END   = 1.00;   // go all the way to the bottom

    // Discord embed colour: red
    private static final int EMBED_COLOR_CHAT = 0xED4245;

    // ── Match type ─────────────────────────────────────────────────────────────

    public enum MatchType {
        Contains, Equals, StartsWith, EndsWith, Regex;

        public static MatchType fromString(String s) {
            for (MatchType t : values()) {
                if (t.name().equalsIgnoreCase(s)) return t;
            }
            return Contains;
        }
    }

    // ── Rule model ─────────────────────────────────────────────────────────────

    public static class ChatRule {
        public final String    name;
        public final MatchType matchType;
        public final boolean   caseSensitive;
        public final boolean   pingWebhook;
        public final boolean   enabled;
        public final String    matchText;

        public ChatRule(String encoded) {
            String[] parts = encoded.split("\\|", 6);
            if (parts.length < 6) {
                this.name          = encoded;
                this.matchType     = MatchType.Contains;
                this.caseSensitive = false;
                this.pingWebhook   = false;
                this.enabled       = false;
                this.matchText     = "";
            } else {
                this.name          = parts[0].trim();
                this.matchType     = MatchType.fromString(parts[1].trim());
                this.caseSensitive = Boolean.parseBoolean(parts[2].trim());
                this.pingWebhook   = Boolean.parseBoolean(parts[3].trim());
                this.enabled       = Boolean.parseBoolean(parts[4].trim());
                this.matchText     = parts[5];
            }
        }

        public boolean matches(String message) {
            if (!enabled || matchText == null || matchText.isEmpty()) return false;
            String msg   = caseSensitive ? message   : message.toLowerCase();
            String match = caseSensitive ? matchText : matchText.toLowerCase();
            return switch (matchType) {
                case Contains   -> msg.contains(match);
                case Equals     -> msg.equals(match);
                case StartsWith -> msg.startsWith(match);
                case EndsWith   -> msg.endsWith(match);
                case Regex      -> {
                    try {
                        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                        yield Pattern.compile(matchText, flags).matcher(message).find();
                    } catch (Exception e) { yield false; }
                }
            };
        }
    }

    // ── Dedup guard ────────────────────────────────────────────────────────────
    // A single webhook per distinct message text; the AtomicBoolean is set true
    // while a send is in-flight so that even if the mixin fires twice for the
    // same message (shouldn't happen but defensive), we don't double-fire.
    private static final java.util.concurrent.ConcurrentHashMap<String, Long> recentlySent =
            new java.util.concurrent.ConcurrentHashMap<>();
    private static final long DEDUP_WINDOW_MS = 3000; // 3 s window per unique message

    // ── Main entry point ───────────────────────────────────────────────────────

    /**
     * Called from {@link com.ihanuat.mod.mixin.ChatHudMixin} — the sole call site.
     * Checks every enabled rule; fires webhook for the first matching rule that
     * has pingWebhook=true.  A dedup guard prevents re-firing for the same text
     * within a 3-second window.
     */
    public static void handleChatMessage(Minecraft client, String plainText) {
        if (MacroConfig.chatRules == null || MacroConfig.chatRules.isEmpty()) return;

        // Purge stale dedup entries older than 10 s
        long now = System.currentTimeMillis();
        recentlySent.entrySet().removeIf(e -> now - e.getValue() > 10_000);

        for (String ruleStr : MacroConfig.chatRules) {
            if (ruleStr == null || ruleStr.isBlank()) continue;
            try {
                ChatRule rule = new ChatRule(ruleStr);
                if (!rule.matches(plainText)) continue;

                if (rule.pingWebhook
                        && MacroConfig.discordWebhookUrl != null
                        && !MacroConfig.discordWebhookUrl.isBlank()) {

                    // Dedup: key = ruleName + first 80 chars of message
                    String dedupKey = rule.name + ":" + plainText.substring(0, Math.min(80, plainText.length()));
                    if (recentlySent.putIfAbsent(dedupKey, now) == null) {
                        sendAlertAsync(client, rule.name, plainText);
                    }
                }
                break; // first match wins
            } catch (Exception ignored) {}
        }
    }

    // ── Async sender ───────────────────────────────────────────────────────────

    private static void sendAlertAsync(Minecraft client, String ruleName, String fullMessage) {
        Thread t = new Thread(() -> {
            // 1. Request screenshot on game thread
            final long captureTime = System.currentTimeMillis();
            client.execute(() -> {
                try {
                    Screenshot.grab(client.gameDirectory, client.getMainRenderTarget(), msg -> {});
                } catch (Exception e) {
                    System.err.println("[Ihanuat] ChatRule screenshot error: " + e.getMessage());
                }
            });

            // 2. Wait for PNG to be written (~2.5 s is generous even on slow disks)
            try { Thread.sleep(2500); } catch (InterruptedException ignored) {}

            // 3. Find the screenshot, crop it to the chat area, send
            File chatCrop = null;
            File original = null;
            try {
                File dir = new File(client.gameDirectory, "screenshots");
                if (dir.exists()) {
                    original = Files.list(dir.toPath())
                            .filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".png"))
                            .filter(p -> !p.getFileName().toString().startsWith("ihanuat_"))
                            .filter(p -> p.toFile().lastModified() >= captureTime - 1_000)
                            .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                            .map(Path::toFile).orElse(null);
                    if (original != null) chatCrop = cropToChatArea(original);
                }
            } catch (Exception e) {
                System.err.println("[Ihanuat] ChatRule crop error: " + e.getMessage());
            }

            // 4. Fire webhook
            try {
                String url = MacroConfig.discordWebhookUrl;
                if (chatCrop != null) {
                    sendMultipartWebhook(url, fullMessage, chatCrop);
                } else {
                    sendTextWebhook(url, fullMessage);
                }
            } catch (Exception e) {
                System.err.println("[Ihanuat] ChatRule webhook send error: " + e.getMessage());
                try { sendTextWebhook(MacroConfig.discordWebhookUrl, fullMessage); } catch (Exception ignored) {}
            }

            // 5. Clean up temp files
            if (chatCrop  != null) { try { chatCrop.delete();  } catch (Exception ignored) {} }
            if (original  != null) { try { original.delete();  } catch (Exception ignored) {} }

        }, "ihanuat-chat-alert-" + ruleName);
        t.setDaemon(true);
        t.start();
    }

    // ── Screenshot crop ────────────────────────────────────────────────────────

    /**
     * Crops the screenshot to the chat area:
     *   - Keep left 60% of width  (removes 40% from right — inventory / hotbar)
     *   - Keep rows 40%–80% of height  (removes top 40% sky/map + bottom 20% hotbar)
     */
    private static File cropToChatArea(File source) throws IOException {
        BufferedImage full = ImageIO.read(source);
        if (full == null) return null;
        int fullW = full.getWidth();
        int fullH = full.getHeight();

        int cropX = (int)(fullW * CROP_X_START);
        int cropY = (int)(fullH * CROP_Y_START);
        int cropW = (int)(fullW * CROP_WIDTH);
        int cropH = (int)(fullH * CROP_Y_END) - cropY;

        if (cropW <= 0 || cropH <= 0) return null;

        BufferedImage cropped = full.getSubimage(cropX, cropY, cropW, cropH);
        File temp = new File(source.getParentFile(), "ihanuat_chat_crop_" + System.currentTimeMillis() + ".png");
        ImageIO.write(cropped, "png", temp);
        return temp;
    }

    // ── Webhook: multipart (embed + image) ────────────────────────────────────

    /**
     * Sends a Discord webhook with a red embed and the chat screenshot attached.
     *
     * Format:
     *   ||@here||
     *   [Red embed]
     *     Title:       Chat Alert
     *     Description: '{fullMessage}'
     *     Image:       (attached screenshot)
     */
    private static void sendMultipartWebhook(String webhookUrl, String fullMessage, File imageFile)
            throws Exception {
        String boundary = "IhanuatBoundary" + System.currentTimeMillis();
        URL url = new URI(webhookUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setRequestProperty("User-Agent", "Java-Ihanuat-ChatAlert/1.0");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        String contentText = "||@here||";
        String embedDesc   = fullMessage;
        String json = "{"
                + "\"content\":\"" + jsonEscape(contentText) + "\","
                + "\"embeds\":[{"
                + "\"title\":\"Chat Alert\","
                + "\"description\":\"" + jsonEscape(embedDesc) + "\","
                + "\"color\":" + EMBED_COLOR_CHAT + ","
                + "\"image\":{\"url\":\"attachment://" + imageFile.getName() + "\"}"
                + "}]}";

        try (OutputStream os = conn.getOutputStream();
             PrintWriter w = new PrintWriter(new OutputStreamWriter(os, "UTF-8"), true)) {
            // Part 1: JSON payload
            w.append("--").append(boundary).append("\r\n");
            w.append("Content-Disposition: form-data; name=\"payload_json\"\r\n");
            w.append("Content-Type: application/json; charset=UTF-8\r\n\r\n");
            w.append(json).append("\r\n");
            // Part 2: PNG attachment
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
        System.out.println("[Ihanuat] ChatRule webhook (multipart): HTTP " + code);
        conn.disconnect();
    }

    // ── Webhook: text-only fallback ────────────────────────────────────────────

    private static void sendTextWebhook(String webhookUrl, String fullMessage) throws Exception {
        String contentText = "||@here||";
        String embedDesc   = fullMessage;
        String payload = "{"
                + "\"content\":\"" + jsonEscape(contentText) + "\","
                + "\"embeds\":[{"
                + "\"title\":\"Chat Alert\","
                + "\"description\":\"" + jsonEscape(embedDesc) + "\","
                + "\"color\":" + EMBED_COLOR_CHAT
                + "}]}";

        URL url = new URI(webhookUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("User-Agent", "Java-Ihanuat-ChatAlert/1.0");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        int code = conn.getResponseCode();
        System.out.println("[Ihanuat] ChatRule webhook (text-only): HTTP " + code);
        conn.disconnect();
    }

    // ── Helper ─────────────────────────────────────────────────────────────────

    private static String jsonEscape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
