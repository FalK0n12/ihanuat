package com.ihanuat.mod.modules;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.mixin.AccessorInventory;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class PestManager {
    private static final Pattern PESTS_ALIVE_PATTERN = Pattern.compile("(?i)(?:Pests|Alive):?\\s*\\(?(\\d+)\\)?");
    private static final Pattern COOLDOWN_PATTERN = Pattern
            .compile("(?i)Cooldown:\\s*\\(?(READY|MAX\\s*PESTS?|(?:(\\d+)m)?\\s*(?:(\\d+)s)?)\\)?");

    public static volatile boolean isCleaningInProgress = false;
    public static volatile String currentInfestedPlot = null;
    public static volatile boolean prepSwappedForCurrentPestCycle = false;
    public static volatile int currentPestSessionId = 0;
    public static volatile boolean isReturningFromPestVisitor = false;
    public static volatile boolean isReturnToLocationActive = false;
    public static volatile boolean isStoppingFlight = false;
    public static volatile boolean isSneakingForAotv = false;
    public static int flightStopStage = 0;
    public static int flightStopTicks = 0;
    public static volatile boolean isPrepSwapping = false;
    public static volatile boolean isBonusInactive = false;
    public static volatile boolean isReactivatingBonus = false;
    private static long lastZeroPestTime = 0;

    public static void reset() {
        isCleaningInProgress = false;
        prepSwappedForCurrentPestCycle = false;
        currentInfestedPlot = null;
        isReturningFromPestVisitor = false;
        isReturnToLocationActive = false;
        isStoppingFlight = false;
        isSneakingForAotv = false;
        flightStopStage = 0;
        flightStopTicks = 0;
        isPrepSwapping = false;
        isReactivatingBonus = false;
        lastZeroPestTime = 0;
        currentPestSessionId++;
    }

    public static void checkTabListForPests(Minecraft client, MacroState.State currentState) {
        if (client.getConnection() == null || client.player == null || !MacroStateManager.isMacroRunning())
            return;

        if (isCleaningInProgress && currentState == MacroState.State.FARMING) {
            isCleaningInProgress = false;
        }

        int aliveCount = -1;
        boolean bonusFound = false;
        Set<String> infestedPlots = new HashSet<>();
        Collection<PlayerInfo> players = client.getConnection().getListedOnlinePlayers();

        for (PlayerInfo info : players) {
            String name = "";
            if (info.getTabListDisplayName() != null) {
                name = info.getTabListDisplayName().getString();
            } else if (info.getProfile() != null) {
                name = String.valueOf(info.getProfile());
            }

            String clean = name.replaceAll("(?i)\u00A7[0-9a-fk-or]", "").trim();
            // Replace non-breaking spaces with normal spaces for easier matching
            String normalized = clean.replace('\u00A0', ' ');

            Matcher aliveMatcher = PESTS_ALIVE_PATTERN.matcher(normalized);
            if (aliveMatcher.find()) {
                int found = Integer.parseInt(aliveMatcher.group(1));
                if (found > aliveCount)
                    aliveCount = found;
            }

            if (normalized.toUpperCase().contains("MAX PESTS")) {
                aliveCount = 99; // Explicitly high count to ensure threshold is met
            }

            Matcher cooldownMatcher = COOLDOWN_PATTERN.matcher(normalized);
            if (cooldownMatcher.find()) {
                String cdVal = cooldownMatcher.group(1).toUpperCase();
                int cooldownSeconds = -1;

                if (cdVal.contains("MAX PEST")) {
                    aliveCount = 99; // Treat as max threshold met
                    cooldownSeconds = 999; // High cooldown value to avoid prep-swap during max state
                } else if (cdVal.equalsIgnoreCase("READY")) {
                    cooldownSeconds = 0;
                } else {
                    int m = 0;
                    int s = 0;
                    if (cooldownMatcher.group(2) != null)
                        m = Integer.parseInt(cooldownMatcher.group(2));
                    if (cooldownMatcher.group(3) != null)
                        s = Integer.parseInt(cooldownMatcher.group(3));

                    if (m > 0 || s > 0) {
                        cooldownSeconds = (m * 60) + s;
                    }
                }

                if (MacroConfig.autoEquipment) {
                    if (cooldownSeconds > 170 && prepSwappedForCurrentPestCycle
                            && !isCleaningInProgress) {
                        prepSwappedForCurrentPestCycle = false;
                    }
                } else {
                    if (cooldownSeconds > 3 && prepSwappedForCurrentPestCycle && !isCleaningInProgress) {
                        prepSwappedForCurrentPestCycle = false;
                    }
                }

                // Prep swap logic
                if (currentState == MacroState.State.FARMING && cooldownSeconds != -1 && cooldownSeconds >= 0
                        && !prepSwappedForCurrentPestCycle && !isCleaningInProgress && !isReturnToLocationActive) {

                    boolean thresholdMet = (aliveCount >= MacroConfig.pestThreshold || aliveCount >= 8);
                    if (!thresholdMet) {
                        if (MacroConfig.autoEquipment) {
                            if (cooldownSeconds <= 170)
                                triggerPrepSwap(client);
                        } else if (cooldownSeconds <= 3) {
                            triggerPrepSwap(client);
                        }
                    } else {
                        // Threshold met, prep will be skipped and startCleaningSequence will be called
                        // after loop
                    }
                }
            }

            if (normalized.contains("Plot")) {
                Matcher m = Pattern.compile("(\\d+)").matcher(normalized);
                while (m.find()) {
                    infestedPlots.add(m.group(1).trim());
                }
            }

            if (normalized.toUpperCase().contains("BONUS: INACTIVE")) {
                bonusFound = true;
            }
        }

        isBonusInactive = bonusFound;

        // Failsafe: if cleaning/spraying and 0 pests for 10s, return to farming
        if (currentState == MacroState.State.CLEANING || currentState == MacroState.State.SPRAYING) {
            if (aliveCount <= 0) {
                if (lastZeroPestTime == 0) {
                    lastZeroPestTime = System.currentTimeMillis();
                } else if (System.currentTimeMillis() - lastZeroPestTime > 10000) {
                    client.player.displayClientMessage(
                            Component.literal("§cFail-safe: No pests detected for 10s. Returning to farm."), true);
                    lastZeroPestTime = 0;
                    handlePestCleaningFinished(client);
                    return;
                }
            } else {
                lastZeroPestTime = 0;
            }
        } else {
            lastZeroPestTime = 0;
        }

        if (isCleaningInProgress)
            return;

        if (aliveCount >= MacroConfig.pestThreshold || aliveCount >= 8) {
            if (aliveCount >= 8 && aliveCount < 99) {
                client.player.displayClientMessage(Component.literal("§eMax Pests (8) reached. Starting cleaning..."),
                        true);
            }
            String targetPlot = infestedPlots.isEmpty() ? "0" : infestedPlots.iterator().next();
            startCleaningSequence(client, targetPlot);
        }
    }

    public static void handlePestCleaningFinished(Minecraft client) {
        ClientUtils.sendDebugMessage(client, "Pest cleaning finished sequence started.");
        client.player.displayClientMessage(Component.literal("§aPest cleaning finished detected."), true);
        MacroWorkerThread.getInstance().submit("PestCleaning-Finished", () -> {
            try {
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                if (MacroConfig.unflyMode == MacroConfig.UnflyMode.DOUBLE_TAP_SPACE) {
                    ClientUtils.sendDebugMessage(client, "Finisher: Performing unfly (Double Tap Space)...");
                    performUnfly(client);
                    MacroWorkerThread.sleep(150);
                    if (MacroWorkerThread.shouldAbortTask(client))
                        return;
                }

                int visitors = VisitorManager.getVisitorCount(client);
                ClientUtils.sendDebugMessage(client, "Finisher: Visitor count check: " + visitors + " (Threshold: "
                        + MacroConfig.visitorThreshold + ")");
                if (visitors >= MacroConfig.visitorThreshold) {
                    MacroState.Location loc = ClientUtils.getCurrentLocation(client);
                    if (loc != MacroState.Location.GARDEN) {
                        client.player.displayClientMessage(
                                Component.literal(
                                        "\u00A7dVisitor Threshold Met (" + visitors + "). Warping to Garden..."),
                                true);
                        com.ihanuat.mod.util.CommandUtils.warpGarden(client);
                        MacroWorkerThread.sleep(250);
                    } else {
                        ClientUtils.sendDebugMessage(client, "Already in Garden, skipping /warp garden for visitors");
                    }

                    GearManager.swapToFarmingToolSync(client);

                    if (MacroConfig.autoWardrobeVisitor && MacroConfig.wardrobeSlotVisitor > 0
                            && WardrobeManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
                        client.player.displayClientMessage(Component.literal(
                                "\u00A7eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor
                                        + ")..."),
                                true);
                        GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor);
                        if (WardrobeManager.isSwappingWardrobe) {
                            ClientUtils.sendDebugMessage(client, "Finisher (Visitor): Waiting for wardrobe GUI...");
                            ClientUtils.waitForWardrobeGui(client);
                            ClientUtils.sendDebugMessage(client,
                                    "Finisher (Visitor): Wardrobe GUI cleared, waiting for swap completion...");
                            while (WardrobeManager.isSwappingWardrobe)
                                MacroWorkerThread.sleep(50);
                            while (WardrobeManager.wardrobeCleanupTicks > 0)
                                MacroWorkerThread.sleep(50);
                            MacroWorkerThread.sleep(250);
                        }
                    }

                    ClientUtils.sendDebugMessage(client,
                            "Finisher (Visitor): Gear restoration done, waiting for stability...");
                    ClientUtils.waitForGearAndGui(client);
                    ClientUtils.sendDebugMessage(client,
                            "Finisher (Visitor): stability reached, transitioning to VISITING.");
                    ClientUtils.sendDebugMessage(client,
                            "Wardrobe swap done, now triggering visitor macro. Next state: VISITING");
                    MacroStateManager.setCurrentState(MacroState.State.VISITING);
                    ClientUtils.sendDebugMessage(client, "Stopping script: Visitor threshold reached");
                    com.ihanuat.mod.util.CommandUtils.stopScript(client, 250);
                    ClientUtils.sendDebugMessage(client, "Starting visitor macro script");
                    com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:visitor", 0);
                    isCleaningInProgress = false;
                    client.player.displayClientMessage(
                            Component.literal("§ePest cleaner finished (visitors)."), false);
                    return;
                }

                MacroWorkerThread.sleep(150);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                ClientUtils.sendDebugMessage(client, "Finisher: Warping to garden (Return to Farm)...");
                com.ihanuat.mod.util.CommandUtils.warpGarden(client);
                MacroWorkerThread.sleep(250);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                isReturningFromPestVisitor = true;
                ClientUtils.sendDebugMessage(client, "Finisher: Calling finalizeReturnToFarm...");
                finalizeReturnToFarm(client);
            } catch (Exception e) {
                e.printStackTrace();
                ClientUtils.sendDebugMessage(client,
                        "§cCRITICAL ERROR in handlePestCleaningFinished: " + e.getMessage());
                ClientUtils.sendDebugMessage(client, "§6Triggering failsafe: Returning to farming...");
                isCleaningInProgress = false;
                isPrepSwapping = false;
                MacroStateManager.setCurrentState(MacroState.State.FARMING);
                ClientUtils.sendDebugMessage(client, "§6Failsafe: Warping to garden...");
                com.ihanuat.mod.util.CommandUtils.warpGarden(client);
                MacroWorkerThread.sleep(250);
                client.execute(() -> {
                    GearManager.swapToFarmingTool(client);
                    com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
                });
            }
        });

    }

    public static void performUnfly(Minecraft client) throws InterruptedException {
        if (client.player == null)
            return;

        if (MacroConfig.unflyMode == MacroConfig.UnflyMode.DOUBLE_TAP_SPACE) {
            isStoppingFlight = true;
            flightStopStage = 0;
            flightStopTicks = 0;

            long deadline = System.currentTimeMillis() + 3000;
            while (isStoppingFlight && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
        } else {
            // SNEAK mode
            client.execute(() -> {
                if (client.options != null)
                    client.options.keyShift.setDown(true);
            });
            Thread.sleep(150);
            client.execute(() -> {
                if (client.options != null)
                    client.options.keyShift.setDown(false);
            });
        }
    }

    private static void finalizeReturnToFarm(Minecraft client) {
        if (!com.ihanuat.mod.MacroStateManager.isMacroRunning())
            return;

        try {
            ClientUtils.sendDebugMessage(client, "Finalize: Starting return sequence.");
            // Already handled in handlePestCleaningFinished but just in case it's called
            // from elsewhere
            if (MacroConfig.unflyMode == MacroConfig.UnflyMode.SNEAK) {
                ClientUtils.sendDebugMessage(client, "Finalize: Performing unfly (Sneak)...");
                performUnfly(client);
                Thread.sleep(150);
            }

            int visitors = VisitorManager.getVisitorCount(client);
            ClientUtils.sendDebugMessage(client, "Finalize: Visitor count check: " + visitors);
            if (visitors >= MacroConfig.visitorThreshold) {
                client.execute(() -> {
                    GearManager.swapToFarmingTool(client);
                });

                if (MacroConfig.autoWardrobeVisitor && MacroConfig.wardrobeSlotVisitor > 0
                        && WardrobeManager.trackedWardrobeSlot != MacroConfig.wardrobeSlotVisitor) {
                    client.player.displayClientMessage(Component.literal(
                            "\u00A7eSwapping to Visitor Wardrobe (Slot " + MacroConfig.wardrobeSlotVisitor + ")..."),
                            true);
                    GearManager.ensureWardrobeSlot(client, MacroConfig.wardrobeSlotVisitor);
                    if (WardrobeManager.isSwappingWardrobe) {
                        ClientUtils.sendDebugMessage(client, "Finalize (Visitor): Waiting for wardrobe GUI...");
                        ClientUtils.waitForWardrobeGui(client);
                        ClientUtils.sendDebugMessage(client,
                                "Finalize (Visitor): Wardrobe GUI cleared, waiting for swap completion...");
                        while (WardrobeManager.isSwappingWardrobe)
                            Thread.sleep(50);
                        while (WardrobeManager.wardrobeCleanupTicks > 0)
                            Thread.sleep(50);
                        Thread.sleep(250);
                    }
                }

                // Wait for any remaining GUIs and wardrobe swap (equipment swap not done for
                // visitors)
                try {
                    ClientUtils.sendDebugMessage(client, "Finalize (Visitor): Waiting for final stability...");
                    while (WardrobeManager.isSwappingWardrobe)
                        Thread.sleep(50);
                    long guiStart = System.currentTimeMillis();
                    while (client.screen != null && System.currentTimeMillis() - guiStart < 5000) {
                        Thread.sleep(100);
                    }
                    Thread.sleep(250);
                } catch (InterruptedException ignored) {
                }
                ClientUtils.sendDebugMessage(client,
                        "Wardrobe swap done, now triggering visitor macro. Next state: VISITING");
                ClientUtils.sendDebugMessage(client, "Stopping script: Returning to visitor macro");
                com.ihanuat.mod.util.CommandUtils.stopScript(client, 250);
                ClientUtils.sendDebugMessage(client, "Starting visitor macro script");
                com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:visitor", 0);
                isCleaningInProgress = false;
                return;
            }

            ClientUtils.sendDebugMessage(client, "Finalize: Swapping to farming tool...");
            GearManager.swapToFarmingToolSync(client);
            ClientUtils.sendDebugMessage(client, "Finalize: Tool swap done.");
            if (MacroConfig.autoRodReturnToFarm) {
                ClientUtils.sendDebugMessage(client, "Finalize: Auto Rod - Triggering second rod cast.");
                RodManager.executeRodSequence(client);
            }

            // Only wait for gear swap if equipment swap is enabled (since it's only done
            // during cleaning if enabled)
            if (MacroConfig.autoEquipment) {
                ClientUtils.sendDebugMessage(client, "Finalize: Waiting for gear/gui checks...");
                ClientUtils.waitForGearAndGui(client);
                ClientUtils.sendDebugMessage(client, "Finalize: Gear/gui wait done.");
            }

            ClientUtils.sendDebugMessage(client, "Pest cleaning sequence completed. Next state: FARMING");
            com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.FARMING);
            prepSwappedForCurrentPestCycle = false; // Ensure flag is reset when returning
            ClientUtils.sendDebugMessage(client, "Stopping script: Pest cleaning finished, returning to farming");
            com.ihanuat.mod.util.CommandUtils.stopScript(client, 250);
            isCleaningInProgress = false;
            if (client.player != null) {
                ClientUtils.sendDebugMessage(client, "Pest cleaner finished.");
            }
            com.ihanuat.mod.util.ClientUtils.sendDebugMessage(client,
                    "Pest cleaning sequence finished. Restarting farming...");
            ClientUtils.sendDebugMessage(client, "Starting farming script: " + MacroConfig.getFullRestartCommand());
            com.ihanuat.mod.util.CommandUtils.startScript(client, MacroConfig.getFullRestartCommand(), 0);
        } catch (InterruptedException ignored) {
        }
    }

    public static void update(Minecraft client) {
        checkTabListForPests(client, com.ihanuat.mod.MacroStateManager.getCurrentState());
    }

    private static void triggerPrepSwap(Minecraft client) {
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

                if (!isCleaningInProgress) {
                    GearManager.finalResume(client);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isPrepSwapping = false;
            }
        });
    }

    private static boolean shouldAbortPrepSwap(Minecraft client) {
        if (MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING) || isCleaningInProgress) {
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

        while (WardrobeManager.isSwappingWardrobe && !isCleaningInProgress) {
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

        while (EquipmentManager.isSwappingEquipment && !isCleaningInProgress) {
            MacroWorkerThread.sleep(50);
        }
        while (client.screen != null && !isCleaningInProgress) {
            MacroWorkerThread.sleep(50);
        }
        MacroWorkerThread.sleep(250);
        if (shouldAbortPrepSwap(client))
            return false;

        ClientUtils.sendDebugMessage(client, "Prep-swap: Equipment swap completed.");
        return true;
    }

    public static void startCleaningSequence(Minecraft client, String plot) {
        if (isCleaningInProgress || WardrobeManager.isSwappingWardrobe || EquipmentManager.isSwappingEquipment)
            return;

        ClientUtils.sendDebugMessage(client,
                "Stopping script: Pest threshold reached, starting cleaning sequence for plot " + plot);
        com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
        isCleaningInProgress = true;
        WardrobeManager.shouldRestartFarmingAfterSwap = false;
        com.ihanuat.mod.MacroStateManager.setCurrentState(com.ihanuat.mod.MacroState.State.CLEANING);
        currentInfestedPlot = plot;
        final int sessionId = ++currentPestSessionId;
        final String currentPlot = ClientUtils.getCurrentPlot(client);

        MacroWorkerThread.getInstance().submit("CleaningSequence-" + plot, () -> {
            try {
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                MacroWorkerThread.sleep(850);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;
                if (sessionId != currentPestSessionId)
                    return;

                if (!restoreGearForCleaning(client))
                    return;

                prepSwappedForCurrentPestCycle = false;
                client.player.displayClientMessage(
                        Component.literal("§6Starting Pest Cleaner script (" + currentInfestedPlot + ")..."), true);
                com.ihanuat.mod.util.CommandUtils.setSpawn(client);
                if (MacroWorkerThread.shouldAbortTask(client))
                    return;

                if (isBonusInactive) {
                    client.player.displayClientMessage(
                            Component.literal("§dBonus is INACTIVE! Triggering Phillip reactivation..."), true);
                    isReactivatingBonus = true;
                    com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:pestCleaner", 0);
                    return;
                }

                boolean isSamePlot = currentInfestedPlot != null && currentInfestedPlot.equals(currentPlot);
                boolean shouldDoAotv = shouldDoAotvOnCurrentPlot(client, isSamePlot);

                if (!warpToInfestedPlotIfNeeded(client, isSamePlot)) {
                    shouldDoAotv = false;
                }

                if (shouldDoAotv) {
                    performAotvToRoof(client);
                }

                startPestCleanerScript(client);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private static boolean restoreGearForCleaning(Minecraft client) throws InterruptedException {
        if (MacroConfig.autoWardrobePest) {
            int targetSlot = MacroConfig.wardrobeSlotFarming;
            if ((prepSwappedForCurrentPestCycle || WardrobeManager.trackedWardrobeSlot != targetSlot)
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

    private static boolean shouldDoAotvOnCurrentPlot(Minecraft client, boolean isSamePlot) {
        if (!MacroConfig.aotvToRoof)
            return false;

        if (MacroConfig.aotvRoofPlots.isEmpty())
            return isSamePlot;

        boolean inAllowedList = MacroConfig.aotvRoofPlots.contains(currentInfestedPlot);
        ClientUtils.sendDebugMessage(client, inAllowedList ? "plot in list, performing aotv" : "plot not in list, skipping aotv");
        return inAllowedList;
    }

    private static boolean warpToInfestedPlotIfNeeded(Minecraft client, boolean isSamePlot) throws InterruptedException {
        if (isSamePlot || currentInfestedPlot == null || currentInfestedPlot.equals("0"))
            return true;

        if (com.ihanuat.mod.util.CommandUtils.plotTp(client, currentInfestedPlot)) {
            Thread.sleep(250);
            return !MacroWorkerThread.shouldAbortTask(client);
        }

        client.player.displayClientMessage(Component.literal("§cFailed to warp to plot " + currentInfestedPlot + "!"), true);
        return false;
    }

    private static void performAotvToRoof(Minecraft client) throws InterruptedException {
        isSneakingForAotv = true;
        Vec3 eyePos = client.player.getEyePosition();
        float yawRad = (float) Math.toRadians(client.player.getYRot());
        int baseUpPitch = Math.max(45, Math.min(90, MacroConfig.aotvRoofPitch));
        int humanization = Math.max(0, Math.min(15, MacroConfig.aotvRoofPitchHumanization));
        double randomizedUpPitch = baseUpPitch + ((Math.random() * 2.0) - 1.0) * humanization;
        randomizedUpPitch = Math.max(45.0, Math.min(90.0, randomizedUpPitch));
        float targetMcPitch = (float) -randomizedUpPitch;
        double pitchRad = Math.toRadians(targetMcPitch);

        double dirX = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * Math.cos(pitchRad);
        Vec3 targetPos = eyePos.add(dirX * 100.0, dirY * 100.0, dirZ * 100.0);
        int rotTime = (int) (MacroConfig.rotationTime * (0.92 + Math.random() * 0.16));

        RotationManager.initiateRotation(client, targetPos, rotTime);
        ClientUtils.waitForRotationToComplete(client, targetMcPitch, rotTime);

        int aotvSlot = ClientUtils.findAspectOfTheVoidSlot(client);
        if (aotvSlot != -1 && aotvSlot < 9) {
            client.execute(() -> ((AccessorInventory) client.player.getInventory()).setSelected(aotvSlot));
            Thread.sleep(150);
            double startY = client.player.getY();
            Thread.sleep(50 + (long) (Math.random() * 80));
            client.execute(() -> client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND));

            ClientUtils.waitForYChange(client, startY, 1500);
            Thread.sleep(40 + (long) (Math.random() * 60));
            isSneakingForAotv = false;
            client.execute(() -> client.options.keyShift.setDown(false));
            Thread.sleep(30 + (long) (Math.random() * 50));

            Vec3 postEyePos = client.player.getEyePosition();
            float yawPost = (float) Math.toRadians(client.player.getYRot());
            double targetPitch = -5.0 + Math.random() * 10.0;
            Vec3 forward = new Vec3(postEyePos.x - Math.sin(yawPost) * 100,
                    postEyePos.y + Math.tan(Math.toRadians(-targetPitch)) * 5.0,
                    postEyePos.z + Math.cos(yawPost) * 100);
            RotationManager.initiateRotation(client, forward, 250 + (int) (Math.random() * 150));
        } else {
            isSneakingForAotv = false;
            client.execute(() -> client.options.keyShift.setDown(false));
        }
    }

    private static void startPestCleanerScript(Minecraft client) {
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

    private static void triggerCleaningNow(Minecraft client, Set<String> infestedPlots) {
        String targetPlot = infestedPlots.isEmpty() ? "0" : infestedPlots.iterator().next();
        startCleaningSequence(client, targetPlot);
    }

    public static void handlePhillipMessage(Minecraft client, String text) {
        if (!isReactivatingBonus || client.player == null)
            return;

        String plain = text.replaceAll("(?i)§[0-9a-fk-or]", "").trim();
        if (plain.toLowerCase().contains("pesthunter phillip") && plain.toLowerCase().contains("thanks for the")) {
            client.player.displayClientMessage(Component.literal(
                    "§aPhillip message detected! Returning to plot §e" + currentInfestedPlot + "..."), true);
            MacroWorkerThread.getInstance().submit("PhillipReactivation", () -> {
                try {
                    if (MacroWorkerThread.shouldAbortTask(client))
                        return;
                    ClientUtils.sendDebugMessage(client,
                            "Stopping script: Phillip message detected, reactivating bonus");
                    com.ihanuat.mod.util.CommandUtils.stopScript(client, MacroConfig.getRandomizedDelay(250));
                    if (MacroWorkerThread.shouldAbortTask(client))
                        return;
                    ClientUtils.sendDebugMessage(client, "Teleporting back to plot " + currentInfestedPlot);
                    com.ihanuat.mod.util.CommandUtils.plotTp(client, currentInfestedPlot);
                    if (MacroWorkerThread.shouldAbortTask(client))
                        return;
                    ClientUtils.sendDebugMessage(client, "Starting pest cleaner script after Phillip message");
                    com.ihanuat.mod.util.CommandUtils.startScript(client, ".ez-startscript misc:pestCleaner", 250);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    isReactivatingBonus = false;
                }
            });
        }
    }
}
