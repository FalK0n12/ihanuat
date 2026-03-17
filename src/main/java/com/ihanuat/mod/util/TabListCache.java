package com.ihanuat.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TabListCache {
    private static List<String> cachedLines = new ArrayList<>();
    private static long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 250; // Update 4 times per second

    public static synchronized List<String> getTabLines(Minecraft client) {
        if (client == null || client.getConnection() == null) {
            return new ArrayList<>();
        }

        long now = System.currentTimeMillis();
        if (now - lastUpdateTime >= UPDATE_INTERVAL_MS || cachedLines.isEmpty()) {
            updateCache(client);
            lastUpdateTime = now;
        }
        return new ArrayList<>(cachedLines);
    }

    private static void updateCache(Minecraft client) {
        if (client.getConnection() == null) return;
        
        Collection<PlayerInfo> players = client.getConnection().getListedOnlinePlayers();
        List<String> newLines = new ArrayList<>(players.size());
        
        for (PlayerInfo info : players) {
            if (info == null) continue;
            String raw = (info.getTabListDisplayName() != null) ? info.getTabListDisplayName().getString() : "";
            if (raw.isEmpty()) continue;
            
            // Use optimized color stripping
            String clean = ClientUtils.stripColor(raw).replace('\u00A0', ' ').trim();
            if (!clean.isEmpty()) {
                newLines.add(clean);
            }
        }
        cachedLines = newLines;
    }
    
    public static void forceUpdate(Minecraft client) {
        updateCache(client);
        lastUpdateTime = System.currentTimeMillis();
    }
}
