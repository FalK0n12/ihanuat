package com.ihanuat.mod.modules;

import com.ihanuat.mod.mixin.AccessorInventory;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class SprayonatorManager {

    public static boolean needsSpraying = true;

    /**
     * Finds the Sprayonator in the player's hotbar by display name (anvil-renamed).
     *
     * @return slot index (0–8) if found, -1 if not found
     */
    private static int findSprayonatorSlot(Minecraft client) {
        if (client.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = client.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            // Match exact anvil-rename "Sprayonator" (case-insensitive for safety)
            String name = stack.getHoverName().getString();
            if (name.equalsIgnoreCase("Sprayonator")) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Switches to the Sprayonator in the hotbar and right-clicks once, then
     * restores the farming tool. Must be called from the MacroWorkerThread.
     */
    public static void executeSpraySequence(Minecraft client) throws InterruptedException {
        if (client.player == null) return;

        int spraySlot = findSprayonatorSlot(client);
        if (spraySlot == -1) {
            client.player.displayClientMessage(
                    Component.literal("§cSprayonator not found in hotbar!"), true);
            ClientUtils.sendDebugMessage(client, "[SprayonatorManager] Sprayonator not found in hotbar — skipping spray.");
            return;
        }

        ClientUtils.sendDebugMessage(client, "[SprayonatorManager] Found Sprayonator in slot " + spraySlot + ", spraying plot.");
        client.player.displayClientMessage(Component.literal("§aSprayonator: spraying plot..."), true);

        final int finalSpraySlot = spraySlot;

        // 1. Select the Sprayonator slot
        client.execute(() -> ((AccessorInventory) client.player.getInventory()).setSelected(finalSpraySlot));

        // 2. Wait for the slot selection to be confirmed
        long swapWaitStart = System.currentTimeMillis();
        boolean confirmed = false;
        while (System.currentTimeMillis() - swapWaitStart < 2000) {
            if (((AccessorInventory) client.player.getInventory()).getSelected() == finalSpraySlot) {
                ItemStack current = client.player.getInventory().getItem(finalSpraySlot);
                if (current.getHoverName().getString().equalsIgnoreCase("Sprayonator")) {
                    confirmed = true;
                    break;
                }
            }
            Thread.sleep(20);
        }

        if (!confirmed) {
            ClientUtils.sendDebugMessage(client, "[SprayonatorManager] §cSlot selection timed out — aborting spray.");
            return;
        }

        // 3. Short buffer before use
        Thread.sleep(150);

        // 4. Right-click (use item) on the game thread
        client.execute(() -> {
            if (client.player != null
                    && ((AccessorInventory) client.player.getInventory()).getSelected() == finalSpraySlot) {
                client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        });

        // 5. Small buffer after click
        Thread.sleep(100);

        // 6. Restore farming tool
        GearManager.swapToFarmingTool(client);

        ClientUtils.sendDebugMessage(client, "[SprayonatorManager] Spray complete.");
        needsSpraying = false;
    }
}
