package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.MacroWorkerThread;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class RestartManager {
    private static boolean isRestartPending = false;
    private static long restartExecutionTime = 0;
    private static int restartSequenceStage = 0;
    private static long nextRestartActionTime = 0;

    private static boolean isSafeToRunRestartAbort(MacroState.State state) {
        if (state == MacroState.State.OFF || state == MacroState.State.RECOVERING) {
            return false;
        }

        // Never interrupt active pest/visitor flows; wait until they finish naturally.
        if (PestManager.isCleaningInProgress
                || PestPrepSwapManager.isPrepSwapping
                || PestReturnManager.isFinishingInProgress
                || PestReturnManager.isReturnToLocationActive
                || PestReturnManager.isReturningFromPestVisitor
                || state == MacroState.State.CLEANING
                || state == MacroState.State.SPRAYING
                || state == MacroState.State.VISITING) {
            return false;
        }

        return state == MacroState.State.FARMING;
    }

    public static void handleRestartMessage(Minecraft client) {
        if (MacroStateManager.getCurrentState() != MacroState.State.OFF
                && MacroStateManager.getCurrentState() != MacroState.State.RECOVERING && !isRestartPending) {
            long contestMs = ClientUtils.getContestRemainingMs(client);
            if (contestMs > 0) {
                client.player.displayClientMessage(
                        Component.literal(
                                "§c[Ihanuat] Server restart detected! Delaying abort until Jacob's contest ends..."),
                        false);
                restartExecutionTime = System.currentTimeMillis() + contestMs + 10000;
            } else {
                client.player.displayClientMessage(Component.literal(
                        "§c[Ihanuat] Server restart/evacuation detected! Initiating abort sequence..."), false);
                restartExecutionTime = System.currentTimeMillis();
            }
            // Defer interruption to stage 0 so active pest/visitor flows can finish safely.
            isRestartPending = true;
            restartSequenceStage = 0;
        }
    }

    public static void update(Minecraft client) {
        if (!isRestartPending)
            return;

        MacroState.State state = MacroStateManager.getCurrentState();

        if (restartSequenceStage == 0 && System.currentTimeMillis() >= restartExecutionTime) {
            if (!isSafeToRunRestartAbort(state)) {
                return;
            }

            client.player.displayClientMessage(
                    Component.literal("§c[Ihanuat] Executing delayed restart abort sequence..."), false);
            ClientUtils.sendDebugMessage(client, "Stopping script: Server restart/evacuation detected");
            // Cancel worker tasks right before abort execution.
            MacroWorkerThread.getInstance().cancelCurrent();
            com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
            ClientUtils.forceReleaseKeys(client);
            com.ihanuat.mod.util.CommandUtils.initiateSetSpawn(client);
            restartSequenceStage = 1;
            nextRestartActionTime = System.currentTimeMillis() + 5000; // Fallback timeout
        } else if (restartSequenceStage == 1) {
            if (com.ihanuat.mod.util.CommandUtils.hasSpawnBeenSet()
                    || System.currentTimeMillis() >= nextRestartActionTime) {
                // Go to main menu instead of /hub — /hub causes a disconnect back to the
                // title screen mid-flight which triggers the unexpected-disconnect failsafe.
                // Instead: mark intentional, switch to RECOVERING (which also pauses the
                // dynamic-rest timer so "next rest" is preserved), schedule a 3-second
                // reconnect, then disconnect cleanly.
                client.player.displayClientMessage(
                        Component.literal("§c[Ihanuat] Reboot: disconnecting to main menu, reconnecting in 3s..."), false);
                MacroStateManager.setIntentionalDisconnect(true);
                // RECOVERING triggers DynamicRestManager.pauseTimer() via setCurrentState,
                // ensuring the "next rest" timer is NOT reset.
                MacroStateManager.setCurrentState(MacroState.State.RECOVERING);
                com.ihanuat.mod.ReconnectScheduler.scheduleReconnect(3, true);
                client.execute(() -> client.disconnect(
                        new net.minecraft.client.gui.screens.TitleScreen(), false));
                restartSequenceStage = 0;
                isRestartPending = false;
            }
        }
    }

    public static boolean isRestartPending() {
        return isRestartPending;
    }
}
