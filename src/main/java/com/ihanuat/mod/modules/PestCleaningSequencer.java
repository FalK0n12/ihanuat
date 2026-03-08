package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class PestCleaningSequencer {
    
    public static void startCleaningSequence(Minecraft client, String plot, String currentInfestedPlot, int currentPestSessionId) {
        if (PestManager.isCleaningInProgress || WardrobeManager.isSwappingWardrobe || EquipmentManager.isSwappingEquipment)
            return;

        ClientUtils.sendDebugMessage(client,
                "Stopping script: Pest threshold reached, starting cleaning sequence for plot " + plot);
        com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
        PestManager.isCleaningInProgress = true;
        WardrobeManager.shouldRestartFarmingAfterSwap = false;
        com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.CLEANING);
        final int sessionId = currentPestSessionId;
        final String currentPlot = ClientUtils.getCurrentPlot(client);

        MacroWorkerThread.getInstance().submit("CleaningSequence-" + plot, () -> {
            try {
                com.ihanuat.mod.util.CommandUtils.setSpawn(client);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                MacroWorkerThread.sleep(850);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                if (sessionId != PestManager.currentPestSessionId)
                    return;

                if (!restoreGearForCleaning(client))
                    return;

                PestPrepSwapManager.prepSwappedForCurrentPestCycle = false;
                client.player.displayClientMessage(
                        Component.literal("§6Starting Pest Cleaner script (" + currentInfestedPlot + ")..."), true);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;

                if (PestBonusManager.isBonusInactive) {
                    client.player.displayClientMessage(
                            Component.literal("§dBonus is INACTIVE! Triggering Phillip reactivation..."), true);
                    PestBonusManager.isReactivatingBonus = true;
                    com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:pestCleaner", 0);
                    return;
                }

                boolean isSamePlot = currentInfestedPlot != null && currentInfestedPlot.equals(currentPlot);
                boolean shouldDoAotv = PestAotvManager.shouldDoAotvOnCurrentPlot(client, currentInfestedPlot, isSamePlot);

                if (!warpToInfestedPlotIfNeeded(client, currentInfestedPlot, isSamePlot)) {
                    shouldDoAotv = false;
                }

                if (shouldDoAotv) {
                    PestAotvManager.performAotvToRoof(client);
                }

                startPestCleanerScript(client, currentInfestedPlot);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static boolean restoreGearForCleaning(Minecraft client) throws InterruptedException {
        if (MacroConfig.autoWardrobePest) {
            int targetSlot = MacroConfig.wardrobeSlotFarming;
            if ((PestPrepSwapManager.prepSwappedForCurrentPestCycle || WardrobeManager.trackedWardrobeSlot != targetSlot)
                    && targetSlot > 0) {
                client.player.displayClientMessage(
                        Component.literal("§eRestoring Farming Wardrobe (Slot " + targetSlot + ") for Vacuuming..."),
                        true);
                client.execute(() -> GearManager.ensureWardrobeSlot(client, targetSlot));
                ClientUtils.waitForWardrobeGui(client);
                while (WardrobeManager.isSwappingWardrobe)
                    MacroWorkerThread.sleep(50);
                while (WardrobeManager.wardrobeCleanupTicks > 0)
                    MacroWorkerThread.sleep(50);
                MacroWorkerThread.sleep(250);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return false;
            }
        }

        if (MacroConfig.autoEquipment) {
            GearManager.ensureEquipment(client, true);
            ClientUtils.waitForEquipmentGui(client);
            while (EquipmentManager.isSwappingEquipment)
                MacroWorkerThread.sleep(50);
            MacroWorkerThread.sleep(250);
            if (MacroWorkerThread.shouldAbortTask(client))
                return false;
        }
        return true;
    }

    private static boolean warpToInfestedPlotIfNeeded(Minecraft client, String currentInfestedPlot, boolean isSamePlot) throws InterruptedException {
        if (isSamePlot || currentInfestedPlot == null || currentInfestedPlot.equals("0"))
            return true;

        if (com.ihanuat.mod.util.CommandUtils.plotTp(client, currentInfestedPlot)) {
            Thread.sleep(250);
            return !MacroWorkerThread.shouldAbortTask(client);
        }

        client.player.displayClientMessage(Component.literal("§cFailed to warp to plot " + currentInfestedPlot + "!"), true);
        return false;
    }

    private static void startPestCleanerScript(Minecraft client, String currentInfestedPlot) {
        ClientUtils.sendDebugMessage(client, "Ready to start pest cleaner");
        com.ihanuat.mod.util.CommandUtils.stopScript(client, 50);
        GearManager.swapToFarmingToolSync(client);
        if (MacroWorkerThread.shouldAbortTask(client))
            return;

        ClientUtils.sendDebugMessage(client, "Starting pest cleaner script for plot " + currentInfestedPlot);
        if (MacroConfig.autoRodPestSpawn) {
            ClientUtils.sendDebugMessage(client, "Auto Rod: Triggering rod cast on pest spawn.");
            RodManager.executeRodSequence(client);
        }
        com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:pestCleaner", 0);
    }
}
