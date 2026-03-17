package com.ihanuat.mod.modules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;

public class PestTabListParser {
    private static final Pattern PESTS_ALIVE_PATTERN = Pattern.compile("(?i)(?:Pests|Alive):?\\s*\\(?(\\d+)\\)?");
    private static final Pattern COOLDOWN_PATTERN = Pattern
            .compile("(?i)Cooldown:\\s*\\(?(READY|MAX\\s*PESTS?|(?:(\\d+)m)?\\s*(?:(\\d+)s)?)\\)?");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern BONUS_INACTIVE_PATTERN = Pattern.compile("(?i)BONUS:\\s*INACTIVE");
    private static final Pattern MAX_PESTS_PATTERN = Pattern.compile("(?i)MAX\\s*PESTS?");

    public static class TabListData {
        public int aliveCount = -1;
        public int cooldownSeconds = -1;
        public boolean bonusFound = false;
        public Set<String> infestedPlots = new HashSet<>();
    }

    public static TabListData parseTabList(Minecraft client) {
        TabListData data = new TabListData();
        
        if (client.getConnection() == null || client.player == null)
            return data;

        List<String> normalizedLines = com.ihanuat.mod.util.TabListCache.getTabLines(client);

        for (String normalized : normalizedLines) {
            // Parse alive count
            Matcher aliveMatcher = PESTS_ALIVE_PATTERN.matcher(normalized);
            if (aliveMatcher.find()) {
                int found = Integer.parseInt(aliveMatcher.group(1));
                if (found > data.aliveCount)
                    data.aliveCount = found;
            }

            if (MAX_PESTS_PATTERN.matcher(normalized).find()) {
                data.aliveCount = 99; // Explicitly high count to ensure threshold is met
            }

            // Parse cooldown
            Matcher cooldownMatcher = COOLDOWN_PATTERN.matcher(normalized);
            if (cooldownMatcher.find()) {
                String cdVal = cooldownMatcher.group(1).toUpperCase();

                if (cdVal.contains("MAX PEST")) {
                    data.aliveCount = 99; // Treat as max threshold met
                    data.cooldownSeconds = 999; // High cooldown value to avoid prep-swap during max state
                } else if (cdVal.equalsIgnoreCase("READY")) {
                    data.cooldownSeconds = 0;
                } else {
                    int m = 0;
                    int s = 0;
                    if (cooldownMatcher.group(2) != null)
                        m = Integer.parseInt(cooldownMatcher.group(2));
                    if (cooldownMatcher.group(3) != null)
                        s = Integer.parseInt(cooldownMatcher.group(3));

                    if (m > 0 || s > 0) {
                        data.cooldownSeconds = (m * 60) + s;
                    }
                }
            }

            // Parse infested plots
            if (normalized.contains("Plot")) {
                Matcher m = DIGIT_PATTERN.matcher(normalized);
                while (m.find()) {
                    data.infestedPlots.add(m.group(1).trim());
                }
            }

            // Check bonus status
            if (BONUS_INACTIVE_PATTERN.matcher(normalized).find()) {
                data.bonusFound = true;
            }
        }

        return data;
    }
}
