package com.ihanuat.mod.modules;

import java.util.List;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;

public class SuperCrafter {
    public static volatile boolean isCrafting = false;
    private static volatile boolean isCraftingDone = false;
    private static int currentCropIndex = 0;
    private static int craftingStage = 0;
    private static long lastActionTime = 0;
    private static final long ACTION_DELAY_MS = 500;

    private static List<String> getCrops() {
        return MacroConfig.superCraftCrops;
    }

    public static void startSuperCraft(Minecraft client) {
        if (isCrafting) return;
        isCrafting = true;
        isCraftingDone = false;
        currentCropIndex = 0;
        craftingStage = 0;
        lastActionTime = 0;
        ClientUtils.sendDebugMessage(client, "SuperCrafter: Starting for " + getCrops().size() + " crops.");
        sendRecipeCommand(client);
    }

    public static boolean isComplete() {
        return isCraftingDone;
    }

    public static void reset() {
        isCrafting = false;
        isCraftingDone = false;
        currentCropIndex = 0;
        craftingStage = 0;
        lastActionTime = 0;
    }

    public static void handleRecipeGui(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isCrafting) return;

        long now = System.currentTimeMillis();
        if (now - lastActionTime < ACTION_DELAY_MS) return;

        String title = screen.getTitle().getString().toLowerCase();
        String currentCrop = getCrops().get(currentCropIndex).toLowerCase();

        if (!title.contains(currentCrop)) {
            ClientUtils.sendDebugMessage(client, "SuperCrafter: Waiting for GUI - " + currentCrop + " (got: " + title + ")");
            return;
        }

        if (screen.getMenu().slots.size() < 11) {
            ClientUtils.sendDebugMessage(client, "SuperCrafter: GUI not fully loaded yet.");
            return;
        }

        switch (craftingStage) {
            case 0: {
                // Click slot 10 to open supercraft menu
                ClientUtils.sendDebugMessage(client, "SuperCrafter: Clicking slot 10 to open supercraft for " + currentCrop);
                client.gameMode.handleInventoryMouseClick(
                    screen.getMenu().containerId,
                    10,
                    0,
                    ClickType.PICKUP,
                    client.player
                );
                lastActionTime = now;
                craftingStage = 1;
                break;
            }
            case 1: {
                // Shift+click slot 33 to maximize amount
                ClientUtils.sendDebugMessage(client, "SuperCrafter: Shift-clicking slot 33 to maximize for " + currentCrop);
                client.gameMode.handleInventoryMouseClick(
                    screen.getMenu().containerId,
                    32,
                    0,
                    ClickType.QUICK_MOVE,
                    client.player
                );
                lastActionTime = now;
                craftingStage = 2;
                break;
            }
            case 2: {
                // Left-click slot 33 to craft all
                ClientUtils.sendDebugMessage(client, "SuperCrafter: Left-clicking slot 33 to craft all for " + currentCrop);
                client.gameMode.handleInventoryMouseClick(
                    screen.getMenu().containerId,
                    32,
                    0,
                    ClickType.PICKUP,
                    client.player
                );
                lastActionTime = now;
                craftingStage = 3;
                break;
            }
            case 3: {
                // Close GUI and move to next crop
                ClientUtils.sendDebugMessage(client, "SuperCrafter: Done with " + currentCrop + ". Closing GUI.");
                client.setScreen(null);
                lastActionTime = now;
                currentCropIndex++;
                craftingStage = 0;

                if (currentCropIndex >= getCrops().size()) {
                    ClientUtils.sendDebugMessage(client, "SuperCrafter: All crops done!");
                    isCrafting = false;
                    isCraftingDone = true;
                } else {
                    MacroWorkerThread.getInstance().submit("SuperCrafter-Next", () -> {
                        MacroWorkerThread.sleep(600);
                        sendRecipeCommand(client);
                    });
                }
                break;
            }
        }
    }

    private static void sendRecipeCommand(Minecraft client) {
        String crop = getCrops().get(currentCropIndex);
        ClientUtils.sendDebugMessage(client, "SuperCrafter: Sending /recipe " + crop);
        client.execute(() -> ClientUtils.sendCommand(client, "/recipe " + crop));
    }
}