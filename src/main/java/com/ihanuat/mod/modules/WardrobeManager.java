package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class WardrobeManager {
    public static volatile boolean isSwappingWardrobe = false;
    public static volatile long wardrobeInteractionTime = 0;
    public static volatile int wardrobeInteractionStage = 0;
    public static volatile int wardrobeCleanupTicks = 0;
    public static volatile int trackedWardrobeSlot = -1;
    public static volatile int targetWardrobeSlot = -1;
    public static volatile boolean shouldRestartFarmingAfterSwap = false;
    public static volatile long wardrobeOpenPendingTime = 0;
    public static volatile boolean wardrobeGuiDetected = false;

    public static void resetState() {
        isSwappingWardrobe = false;
        shouldRestartFarmingAfterSwap = false;
        wardrobeCleanupTicks = 0;
        trackedWardrobeSlot = -1;
        targetWardrobeSlot = -1;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        wardrobeGuiDetected = false;
        wardrobeOpenPendingTime = 0;
    }

    public static void triggerWardrobeSwap(Minecraft client, int slot) {
        if (trackedWardrobeSlot == slot) {
            ClientUtils.sendDebugMessage(client, "Stopping script: Wardrobe already on target slot, restarting");
            com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
            MacroWorkerThread.getInstance().submit("Wardrobe-AlreadyOnSlot-FastResume", () -> {
                if (MacroWorkerThread.shouldAbortTask(client, com.ihanuat.mod.MacroState.State.FARMING))
                    return;
                MacroWorkerThread.sleep(400);
                if (MacroWorkerThread.shouldAbortTask(client, com.ihanuat.mod.MacroState.State.FARMING))
                    return;
                client.execute(() -> GearManager.swapToFarmingTool(client));
                MacroWorkerThread.sleep(250);
                if (MacroWorkerThread.shouldAbortTask(client, com.ihanuat.mod.MacroState.State.FARMING))
                    return;
                ClientUtils.sendDebugMessage(client,
                        "Starting farming script after wardrobe swap: " + MacroConfig.getFullRestartCommand());
                com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
            });
            return;
        }

        targetWardrobeSlot = slot;
        isSwappingWardrobe = true;
        if (EquipmentManager.isSwappingEquipment) {
            EquipmentManager.isSwappingEquipment = false;
            ClientUtils.sendDebugMessage(client, "§eInterrupted equipment swap for wardrobe priority.");
        }
        wardrobeGuiDetected = false;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        shouldRestartFarmingAfterSwap = true;
        ClientUtils.sendDebugMessage(client, "Stopping script: Triggering wardrobe swap to slot " + slot);
        com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
        MacroWorkerThread.getInstance().submit("Wardrobe-OpenGui", () -> {
            if (MacroWorkerThread.shouldAbortTask(client))
                return;
            MacroWorkerThread.sleep(375);
            if (MacroWorkerThread.shouldAbortTask(client))
                return;
            client.execute(() -> ClientUtils.sendCommand(client, "/wardrobe"));
            ClientUtils.waitForWardrobeGui(client);
        });
    }

    public static void ensureWardrobeSlot(Minecraft client, int slot) {
        if (trackedWardrobeSlot == slot)
            return;
        targetWardrobeSlot = slot;
        isSwappingWardrobe = true;
        if (EquipmentManager.isSwappingEquipment) {
            EquipmentManager.isSwappingEquipment = false;
            ClientUtils.sendDebugMessage(client, "§eInterrupted equipment swap for wardrobe priority.");
        }
        wardrobeGuiDetected = false;
        wardrobeInteractionTime = 0;
        wardrobeInteractionStage = 0;
        ClientUtils.sendCommand(client, "/wardrobe");
    }

    public static void handleWardrobeMenu(Minecraft client, AbstractContainerScreen<?> screen) {
        if (!isSwappingWardrobe || targetWardrobeSlot == -1)
            return;

        long now = System.currentTimeMillis();
        if (now - wardrobeInteractionTime < MacroConfig.getRandomizedDelay(MacroConfig.guiClickDelay))
            return;

        String title = screen.getTitle().getString().toLowerCase();
        if (!title.contains("wardrobe"))
            return;

        int slotIdx = 35 + targetWardrobeSlot;
        if (slotIdx >= screen.getMenu().slots.size()) {
            return;
        }

        Slot slot = screen.getMenu().slots.get(slotIdx);
        ItemStack stack = slot.getItem();

        if (stack.isEmpty() || stack.getItem().toString().toLowerCase().contains("air")
                || stack.getItem().toString().toLowerCase().contains("gray_dye")
                || stack.getHoverName().getString().toLowerCase().contains("gray dye")) {
            ClientUtils.sendDebugMessage(client, "Wardrobe GUI open but data not loaded yet (gray dye/empty detected)");
            return;
        }

        if (!wardrobeGuiDetected) {
            wardrobeGuiDetected = true;
            ClientUtils.sendDebugMessage(client, "Wardrobe GUI detected AND VALIDATED as functional");
            wardrobeInteractionTime = System.currentTimeMillis();
        }

        if (wardrobeInteractionStage == 0) {
            String itemName = stack.getItem().toString().toLowerCase();
            String hoverName = stack.getHoverName().getString().toLowerCase();

            if (itemName.contains("green_dye") || hoverName.contains("green dye") || itemName.contains("lime_dye")
                    || hoverName.contains("lime dye")) {
                client.player.displayClientMessage(
                        Component.literal("§aWardrobe Slot " + targetWardrobeSlot + " is already active."), true);
                trackedWardrobeSlot = targetWardrobeSlot;
                isSwappingWardrobe = false;
                client.player.closeContainer();
                handleWardrobeCompletion(client);
                return;
            }

            client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot.index, 0, ClickType.PICKUP,
                    client.player);
            wardrobeInteractionTime = now;
            wardrobeInteractionStage = 1;
        } else if (wardrobeInteractionStage == 1) {
            long lastClickElapsed = now - wardrobeInteractionTime;
            if (lastClickElapsed < 150)
                return;

            int confirmSlotIdx = 35 + targetWardrobeSlot;
            if (confirmSlotIdx >= screen.getMenu().slots.size())
                return;

            ItemStack confirmStack = screen.getMenu().slots.get(confirmSlotIdx).getItem();
            if (confirmStack.isEmpty())
                return;

            String itemName = confirmStack.getItem().toString().toLowerCase();
            String hoverName = confirmStack.getHoverName().getString().toLowerCase();

            if (itemName.contains("green_dye") || hoverName.contains("green dye") || itemName.contains("lime_dye")
                    || hoverName.contains("lime dye")) {
                ClientUtils.sendDebugMessage(client, "Wardrobe swap successful");
                trackedWardrobeSlot = targetWardrobeSlot;
                isSwappingWardrobe = false;
                client.player.closeContainer();
                handleWardrobeCompletion(client);
            }
        }
    }

    private static void handleWardrobeCompletion(Minecraft client) {
        if (shouldRestartFarmingAfterSwap) {
            shouldRestartFarmingAfterSwap = false;

            if (PestManager.isCleaningInProgress) {
                client.player.displayClientMessage(
                        Component.literal("§aWardrobe swap finished. Cleaning in progress, skipping restart."), true);
                return;
            }

            client.player.displayClientMessage(Component.literal("§aWardrobe swap finished. Restarting farming..."),
                    true);
            client.execute(() -> GearManager.swapToFarmingTool(client));
            MacroWorkerThread.getInstance().submit("WardrobeCompletion-Resume", () -> {
                if (MacroWorkerThread.shouldAbortTask(client, com.ihanuat.mod.MacroState.State.FARMING))
                    return;
                if (PestManager.isCleaningInProgress)
                    return;

                GearManager.finalResume(client);
            });
        }
    }
}
