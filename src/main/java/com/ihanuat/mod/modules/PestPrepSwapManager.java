package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;

public class PestPrepSwapManager {
    public static volatile boolean prepSwappedForCurrentPestCycle = false;
    public static volatile boolean isPrepSwapping = false;

    public static void resetState() {
        prepSwappedForCurrentPestCycle = false;
        isPrepSwapping = false;
    }

    public static void updatePrepSwapFlag(int cooldownSeconds, boolean isCleaningInProgress) {
        if (MacroConfig.autoEquipment) {
            if (cooldownSeconds > 170 && prepSwappedForCurrentPestCycle && !isCleaningInProgress) {
                prepSwappedForCurrentPestCycle = false;
            }
        } else {
            if (cooldownSeconds > 3 && prepSwappedForCurrentPestCycle && !isCleaningInProgress) {
                prepSwappedForCurrentPestCycle = false;
            }
        }
    }

    public static boolean shouldTriggerPrepSwap(MacroState.State currentState, int cooldownSeconds, 
                                                 boolean isCleaningInProgress, boolean isReturnToLocationActive) {
        if (currentState != MacroState.State.FARMING)
            return false;
        if (cooldownSeconds == -1 || cooldownSeconds < 0)
            return false;
        if (prepSwappedForCurrentPestCycle || isCleaningInProgress || isReturnToLocationActive)
            return false;

        if (MacroConfig.autoEquipment) {
            return cooldownSeconds <= 170;
        } else {
            return cooldownSeconds <= 3;
        }
    }

    public static void triggerPrepSwap(Minecraft client) {
        prepSwappedForCurrentPestCycle = true;
        isPrepSwapping = true;
        ClientUtils.sendDebugMessage(client, "Pest cooldown detected. Triggering prep-swap...");
        MacroWorkerThread.getInstance().submit("PrepSwap", () -> {
            try {
                if (shouldAbortPrepSwap(client))
                    return;
                ClientUtils.sendDebugMessage(client, "Stopping script: Triggering prep-swap");
                com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
                MacroWorkerThread.sleep(400);
                if (shouldAbortPrepSwap(client))
                    return;

                if (!runPrepWardrobeSwap(client))
                    return;
                if (!runPrepEquipmentSwap(client))
                    return;

                if (MacroConfig.autoRodPestCd) {
                    RodManager.executeRodSequence(client);
                }

                if (!PestManager.isCleaningInProgress) {
                    // Skip farming tool check entirely — farming tool was never changed during prep swap.
                    ClientUtils.sendDebugMessage(client,
                            "PrepSwap: skipping finalResume, restarting farming directly.");
                    client.execute(() -> {
                        if (!PestManager.isCleaningInProgress) {
                            com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
                            GearManager.swapToFarmingTool(client);
                            com.ihanuat.mod.util.CommandUtils.startScript(
                                    client, MacroConfig.getFullRestartCommand(), 0);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isPrepSwapping = false;
            }
        });
    }

    private static boolean shouldAbortPrepSwap(Minecraft client) {
        if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING) || PestManager.isCleaningInProgress) {
            prepSwappedForCurrentPestCycle = false;
            return true;
        }
        return false;
    }

    private static boolean runPrepWardrobeSwap(Minecraft client) throws InterruptedException {
        if (!MacroConfig.autoWardrobePest || MacroConfig.wardrobeSlotPest <= 0)
            return !shouldAbortPrepSwap(client);

        ClientUtils.sendDebugMessage(client,
                "Prep-swap: Initiating wardrobe swap to slot " + MacroConfig.wardrobeSlotPest);
        GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotPest);
        if (!WardrobeManager.isSwappingWardrobe) {
            ClientUtils.sendDebugMessage(client, "Prep-swap: Wardrobe swap not needed (already on correct slot).");
            return !shouldAbortPrepSwap(client);
        }

        ClientUtils.sendDebugMessage(client, "Prep-swap: Waiting for wardrobe GUI...");
        ClientUtils.waitForWardrobeGui(client);
        if (!WardrobeManager.wardrobeGuiDetected) {
            ClientUtils.sendDebugMessage(client, "§cPrep-swap: Wardrobe GUI not detected! Retrying in 1 second...");
            MacroWorkerThread.sleep(1000);
            if (shouldAbortPrepSwap(client))
                return false;

            GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotPest);
            if (WardrobeManager.isSwappingWardrobe) {
                ClientUtils.sendDebugMessage(client, "Prep-swap: Retry - Waiting for wardrobe GUI...");
                ClientUtils.waitForWardrobeGui(client);
                if (!WardrobeManager.wardrobeGuiDetected) {
                    ClientUtils.sendDebugMessage(client,
                            "§cPrep-swap: Wardrobe GUI still not detected after retry! Aborting prep-swap.");
                    prepSwappedForCurrentPestCycle = false;
                    return false;
                }
            }
        }

        while (WardrobeManager.isSwappingWardrobe && !PestManager.isCleaningInProgress) {
            MacroWorkerThread.sleep(50);
        }
        MacroWorkerThread.sleep(250);
        if (shouldAbortPrepSwap(client))
            return false;

        ClientUtils.sendDebugMessage(client, "Prep-swap: Wardrobe swap completed.");
        return true;
    }

    private static boolean runPrepEquipmentSwap(Minecraft client) throws InterruptedException {
        if (!MacroConfig.autoEquipment)
            return !shouldAbortPrepSwap(client);

        ClientUtils.sendDebugMessage(client, "Prep-swap: Initiating equipment swap to pest gear");
        GearManager.ensureEquipment(client, false);
        MacroWorkerThread.sleep(200);
        if (shouldAbortPrepSwap(client))
            return false;

        ClientUtils.sendDebugMessage(client, "Prep-swap: Waiting for equipment GUI...");
        ClientUtils.waitForEquipmentGui(client);
        if (!EquipmentManager.equipmentGuiDetected) {
            ClientUtils.sendDebugMessage(client,
                    "§cPrep-swap: Equipment GUI not detected! Retrying in 1 second...");
            MacroWorkerThread.sleep(1000);
            if (shouldAbortPrepSwap(client))
                return false;

            GearManager.ensureEquipment(client, false);
            MacroWorkerThread.sleep(200);
            ClientUtils.sendDebugMessage(client, "Prep-swap: Retry - Waiting for equipment GUI...");
            ClientUtils.waitForEquipmentGui(client);
            if (!EquipmentManager.equipmentGuiDetected) {
                ClientUtils.sendDebugMessage(client,
                        "§cPrep-swap: Equipment GUI still not detected after retry! Aborting prep-swap.");
                prepSwappedForCurrentPestCycle = false;
                return false;
            }
        }

        while (EquipmentManager.isSwappingEquipment && !PestManager.isCleaningInProgress) {
            MacroWorkerThread.sleep(50);
        }
        while (client.screen != null && !PestManager.isCleaningInProgress) {
            MacroWorkerThread.sleep(50);
        }
        MacroWorkerThread.sleep(250);
        if (shouldAbortPrepSwap(client))
            return false;

        ClientUtils.sendDebugMessage(client, "Prep-swap: Equipment swap completed.");
        return true;
    }
}
