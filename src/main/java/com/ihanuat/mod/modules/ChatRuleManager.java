package com.ihanuat.mod.modules;

import com.ihanuat.mod.I18n;
import com.ihanuat.mod.MacroConfig;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
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
 *   No screenshot — text-only embed for speed and reliability.
 */
public class ChatRuleManager {

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
     * Checks every enabled rule; fires webhook for the first matching rule.
     * A dedup guard prevents re-firing for the same text within a 3-second window.
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

                if (MacroConfig.discordWebhookUrl != null
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
            try {
                sendTextWebhook(MacroConfig.discordWebhookUrl, ruleName, fullMessage);
            } catch (Exception e) {
                System.err.println("[Ihanuat] ChatRule webhook send error: " + e.getMessage());
            }
        }, "ihanuat-chat-alert-" + ruleName);
        t.setDaemon(true);
        t.start();
    }

    // ── Webhook ────────────────────────────────────────────────────────────────

    // ── Webhook: text-only ───────────────────────────────────────────────────── ────────────────────────────────────────────

    private static void sendTextWebhook(String webhookUrl, String ruleName, String fullMessage) throws Exception {
        String contentText = "||@here||";
        String embedTitle  = I18n.tr("Chat Alert: ", "聊天提醒：") + ruleName;
        String payload = "{"
                + "\"content\":\"" + jsonEscape(contentText) + "\","
                + "\"embeds\":[{"
                + "\"title\":\"" + jsonEscape(embedTitle) + "\","
                + "\"description\":\"" + jsonEscape(fullMessage) + "\","
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
