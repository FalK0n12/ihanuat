package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.ReconnectScheduler;
import com.ihanuat.mod.util.ClientUtils;
import net.minecraft.client.Minecraft;
import com.ihanuat.mod.gui.DynamicRestScreen;
import net.minecraft.network.chat.Component;

import java.util.Random;

/**
 * Implements the Dynamic Rest feature.
 *
 * Flow:
 * 1. While in FARMING state, the timer is shown in the action bar (countdown).
 * 2. When the timer expires, a staged shutdown begins (still gated on FARMING):
 * Stage 0 — send /setspawn, stop the macro script, force-release keys.
 * Stage 1 — disconnect from the server (intentional) and schedule reconnect
 * via ReconnectScheduler for the configured break duration.
 * 3. After the break the existing reconnect → recovery path runs normally,
 * which warps back to the Garden and restarts the script.
 */
public class DynamicRestManager {

    // ── State ────────────────────────────────────────────────────────────────

    /** Epoch-ms when the next rest should be triggered. 0 = not scheduled yet. */
    private static long nextRestTriggerMs = 0;

    /**
     * Total duration of the current scripting period (ms). Used for the progress
     * bar.
     */
    private static long scheduledDurationMs = 0;

    private static boolean restSequencePending = false;
    private static int restSequenceStage = 0;
    private static long nextStageActionTime = 0;
    private static long lastTimeUpdate = 0;

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Called when the macro starts (or after a recovery reconnect).
     * Schedules the next rest timer using the configured scripting time ± offset.
     */
    public static void scheduleNextRest() {
        if (MacroConfig.persistSessionTimer && nextRestTriggerMs != 0) {
            // Already scheduled and we want to persist
            return;
        }
        long baseMs = MacroConfig.restScriptingTime * 60L * 1000L;
        long offsetMs = MacroConfig.restScriptingTimeOffset * 60L * 1000L;
        long randomOffsetMs = (offsetMs > 0) ? (long) (new java.util.Random().nextDouble() * offsetMs) : 0;
        scheduledDurationMs = baseMs + randomOffsetMs;
        nextRestTriggerMs = System.currentTimeMillis() + scheduledDurationMs;
        lastTimeUpdate = System.currentTimeMillis();
        restSequencePending = false;
        restSequenceStage = 0;
        nextStageActionTime = 0;
    }

    /**
     * Clears the rest timer entirely (called when the macro is stopped manually).
     */
    public static void reset() {
        nextRestTriggerMs = 0;
        scheduledDurationMs = 0;
        restSequencePending = false;
        restSequenceStage = 0;
        nextStageActionTime = 0;
        lastTimeUpdate = 0;
    }

    /** Returns true while a rest sequence is actively in progress. */
    public static boolean isRestPending() {
        return restSequencePending;
    }

    /** Returns the scheduled rest trigger time (epoch ms), or 0 if not set. */
    public static long getNextRestTriggerMs() {
        return nextRestTriggerMs;
    }

    /**
     * Returns the total scripting duration that was scheduled (ms), or 0 if not
     * set.
     */
    public static long getScheduledDurationMs() {
        return scheduledDurationMs;
    }

    // ── Tick update ──────────────────────────────────────────────────────────

    /**
     * Must be called every client END_CLIENT_TICK while player != null.
     * Handles both the countdown HUD and the shutdown sequence.
     */
    public static void update(Minecraft client) {
        if (client.player == null)
            return;

        MacroState.State currentState = MacroStateManager.getCurrentState();
        boolean isFarming = currentState == MacroState.State.FARMING;
        boolean isCleaning = currentState == MacroState.State.CLEANING;
        long now = System.currentTimeMillis();

        // === Timer Logic ===
        if (nextRestTriggerMs > 0 && !restSequencePending) {
            if (isFarming || isCleaning) {
                // Ticking state: check if we should trigger
                long remaining = nextRestTriggerMs - now;
                if (remaining <= 0 && isFarming) {
                    // Only trigger shutdown sequence once we are actively farming
                    restSequencePending = true;
                    restSequenceStage = 0;
                    nextStageActionTime = now;
                    if (client.player != null) {
                        client.player.displayClientMessage(
                                Component.literal("§6[Ihanuat] Dynamic Rest triggered! Starting shutdown sequence..."),
                                false);
                    }
                }
            } else {
                // Paused state (OFF, RECOVERING): shift the trigger forward to pause the
                // countdown
                if (lastTimeUpdate != 0) {
                    long gap = now - lastTimeUpdate;
                    if (gap > 0) {
                        nextRestTriggerMs += gap;
                    }
                }
            }
            lastTimeUpdate = now;
        }

        // === Shutdown sequence — runs once pending and in Farming state ===
        if (!restSequencePending)
            return;

        if (now < nextStageActionTime)
            return;

        switch (restSequenceStage) {
            case 0: {
                // Wait until we are farming again to execute the first stage
                if (!isFarming) {
                    return;
                }

                // Stop the farming script, release all keys, /setspawn
                ClientUtils.sendDebugMessage(client, "Stopping script: Initiating dynamic rest sequence");
                com.ihanuat.mod.util.CommandUtils.stopScript(client, 0);
                ClientUtils.forceReleaseKeys(client);
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("§c[Ihanuat] Dynamic Rest: running /setspawn..."), false);
                }
                com.ihanuat.mod.util.CommandUtils.initiateSetSpawn(client);
                MacroStateManager.setCurrentState(MacroState.State.OFF);

                restSequenceStage = 1;
                nextStageActionTime = System.currentTimeMillis() + 3000; // Fallback timeout of 3 seconds
                break;
            }
            case 1: {
                // Check if spawn was set, or wait for fallback timeout
                if (!com.ihanuat.mod.util.CommandUtils.hasSpawnBeenSet()
                        && System.currentTimeMillis() < nextStageActionTime) {
                    return; // Still waiting for spawn confirmation
                }

                // Disconnect and schedule the reconnect after the break duration
                long baseSecs = MacroConfig.restBreakTime * 60L;
                long offsetSecs = MacroConfig.restBreakTimeOffset * 60L;
                long randomOffsetSecs = (offsetSecs > 0) ? (long) (new java.util.Random().nextDouble() * offsetSecs)
                        : 0;
                long breakSeconds = baseSecs + randomOffsetSecs;

                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal(String.format(
                                    "§c[Ihanuat] Dynamic Rest: disconnecting. Reconnecting in %.1f minutes...",
                                    (double) breakSeconds / 60.0)),
                            false);
                }

                // Mark as intentional so the disconnect mixin does not trigger an
                // unexpected-kick reconnect on top of ours.
                MacroStateManager.setIntentionalDisconnect(true);

                // Schedule reconnect (shouldResume = true → recovery will fire on rejoin)
                ReconnectScheduler.scheduleReconnect(breakSeconds, true);

                // Actually disconnect — use Minecraft#disconnect which cleanly
                // tears down the connection and returns to our custom rest screen.
                // We wrap this in mc.execute to avoid potential nested rendering/blur crashes
                // in modern Minecraft versions (1.21.x).
                long durationMs = breakSeconds * 1000;
                long restEndTimeMs = System.currentTimeMillis() + durationMs;

                // Reset the trigger so scheduleNextRest() can pick up a new time after recovery
                nextRestTriggerMs = 0;

                client.execute(() -> client.disconnect(new DynamicRestScreen(restEndTimeMs, durationMs), false));

                restSequenceStage = 2;
                nextStageActionTime = Long.MAX_VALUE; // no further stages needed
                break;
            }
            default:
                break;
        }
    }
}
