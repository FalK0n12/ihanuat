package com.ihanuat.mod.modules.profitTracker;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks crop yields via the Cultivating enchantment counter on the held tool,
 * detects crop type from inventory changes, and monitors purse balance.
 */
public class InventoryTracker {

    private static final Map<String, Long> prevInventoryCounts = new LinkedHashMap<>();
    private static long lastCultivatingValue = -1;
    private static String currentFarmedCrop = "Wheat";
    private static long lastPurseBalance = -1;

    /**
     * Called every tick from ProfitManager.update().
     * Scans inventory for crop changes, reads cultivating counter, and tracks purse.
     */
    public static void update(Minecraft client) {
        if (client.player == null)
            return;

        // 1. Detect which crop increased in inventory
        String detectedCrop = null;
        long maxIncrease = 0;

        Map<String, Long> currentCounts = new LinkedHashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (stack == null || stack.isEmpty())
                continue;
            String name = ClientUtils.stripColor(stack.getHoverName().getString()).trim();
            if (ItemConstants.BASE_CROPS.contains(name)) {
                currentCounts.put(name, currentCounts.getOrDefault(name, 0L) + stack.getCount());
            }
        }

        for (Map.Entry<String, Long> entry : currentCounts.entrySet()) {
            String name = entry.getKey();
            long count = entry.getValue();
            long prev = prevInventoryCounts.getOrDefault(name, 0L);
            if (count > prev) {
                long diff = count - prev;
                if (diff > maxIncrease) {
                    maxIncrease = diff;
                    detectedCrop = name;
                }
            }
        }
        prevInventoryCounts.clear();
        prevInventoryCounts.putAll(currentCounts);

        if (detectedCrop != null) {
            currentFarmedCrop = detectedCrop;
        }

        // 2. Track Cultivating counter on held item
        ItemStack held = client.player.getMainHandItem();
        if (held != null && !held.isEmpty()) {
            long newValue = -1;

            // Hypixel 1.21 stores this in custom_data
            CustomData custom = held.get(DataComponents.CUSTOM_DATA);
            if (custom != null) {
                CompoundTag tag = custom.copyTag();
                if (tag.contains("farmed_cultivating")) {
                    newValue = tag.getLong("farmed_cultivating").get();
                }
            }

            if (newValue != -1) {
                if (lastCultivatingValue != -1 && newValue > lastCultivatingValue) {
                    long delta = newValue - lastCultivatingValue;
                    if (delta <= ItemConstants.MAX_CULTIVATING_DELTA && currentFarmedCrop != null) {
                        if (currentFarmedCrop.equalsIgnoreCase("Wheat")
                                || currentFarmedCrop.equalsIgnoreCase("Seeds")) {
                            // Ratio 1 Wheat : 1.5 Seeds (Total 2.5)
                            long wheatDelta = Math.round(delta / 2.5);
                            long seedsDelta = delta - wheatDelta;
                            if (wheatDelta > 0)
                                ProfitManager.addDrop("Wheat", wheatDelta);
                            if (seedsDelta > 0)
                                ProfitManager.addDrop("Seeds", seedsDelta);
                        } else {
                            ProfitManager.addDrop(currentFarmedCrop, delta);
                        }
                    } else if (delta > ItemConstants.MAX_CULTIVATING_DELTA && MacroConfig.showDebug) {
                        ClientUtils.sendDebugMessage(client, "Dismissed large cultivating delta: +" + delta);
                    }
                }
                lastCultivatingValue = newValue;
            } else {
                lastCultivatingValue = -1;
            }
        } else {
            lastCultivatingValue = -1;
        }

        // 3. Track Purse
        long currentPurse = ClientUtils.getPurse(client);
        if (currentPurse != -1) {
            if (lastPurseBalance != -1) {
                if (currentPurse > lastPurseBalance) {
                    if (MacroStateManager.getCurrentState() != MacroState.State.OFF &&
                            MacroStateManager.getCurrentState() != MacroState.State.AUTOSELLING) {
                        long delta = currentPurse - lastPurseBalance;
                        if (delta <= 50000) {
                            ProfitManager.addDrop("Purse", delta);
                        } else if (MacroConfig.showDebug) {
                            ClientUtils.sendDebugMessage(client, "Dismissed large purse change: +" + delta);
                        }
                    }
                }
            }
            lastPurseBalance = currentPurse;
        }
    }

    /**
     * Resets tracking state (called on macro start/restart).
     */
    public static void reset() {
        lastPurseBalance = -1;
    }
}
