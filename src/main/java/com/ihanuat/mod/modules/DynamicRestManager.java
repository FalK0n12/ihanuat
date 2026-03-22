package com.ihanuat.mod.modules;

import java.util.Random;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.ReconnectScheduler;
import com.ihanuat.mod.gui.DynamicRestScreen;
import com.ihanuat.mod.util.ClientUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Implements the Dynamic Rest feature.
 * Flow:
 * 1. Timer counts down while macro is running in any active state
 *    (FARMING, CLEANING, VISITING, SPRAYING, etc.) — no longer pauses on
 *    non-farming states.
 * 2. When the timer expires, a staged shutdown begins (still gated on FARMING
 *    for safe execution — it will wait until the current pest/visitor sequence
 *    finishes):
 *    Stage 0 — send /setspawn, stop the macro script, force-release keys.
 *    Stage 1 — disconnect from the server (intentional) and schedule reconnect
 *    via ReconnectScheduler for the configured break duration.
 * 3. After the break the existing reconnect → recovery path runs normally,
 *    which warps back to the Garden and restarts the script.
 */
public class DynamicRestManager {

    // ── State ────────────────────────────────────────────────────────────────

    /** Epoch-ms when the next rest should be triggered. 0 = not scheduled yet. */
    private static long nextRestTriggerMs = 0;

    /**
     * Total duration of the current scripting period (ms). Used to compute
     * progress if ever needed again.
     */
    private static long scheduledDurationMs = 0;

    /**
     * When the timer is paused (macro stopped/off) we store how many ms remain
     * so we can restore the trigger on resume. -1 = not paused.
     */
    private static long pausedRemainingMs = -1;

    private static boolean restSequencePending = false;
    private static int restSequenceStage = 0;
    private static long nextStageActionTime = 0;

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Called when the macro starts (or after a recovery reconnect).
     * If the timer was paused, resumes from where it left off.
     * Otherwise schedules a fresh timer using the configured scripting time ± offset.
     */
    public static void scheduleNextRest() {
        if (pausedRemainingMs >= 0) {
            // Resume from saved remaining time
            nextRestTriggerMs = System.currentTimeMillis() + pausedRemainingMs;
            pausedRemainingMs = -1;
            return;
        }
        if (MacroConfig.persistSessionTimer && nextRestTriggerMs != 0) {
            // Already scheduled and we want to persist
            return;
        }
        long minMs = MacroConfig.restScriptingTimeMin * 60L * 1000L;
        long maxMs = MacroConfig.restScriptingTimeMax * 60L * 1000L;
        if (maxMs < minMs) maxMs = minMs;
        scheduledDurationMs = minMs + (long)(new Random().nextDouble() * (maxMs - minMs));
        nextRestTriggerMs = System.currentTimeMillis() + scheduledDurationMs;
        restSequencePending = false;
        restSequenceStage = 0;
        nextStageActionTime = 0;
    }

    /**
     * Called when the macro is paused/stopped (not a dynamic-rest disconnect).
     * Saves remaining ms so the timer can resume exactly where it left off.
     */
    public static void pauseTimer() {
        if (nextRestTriggerMs > 0 && !restSequencePending) {
            pausedRemainingMs = Math.max(0, nextRestTriggerMs - System.currentTimeMillis());
            nextRestTriggerMs = 0;
        }
    }

    /**
     * Clears the rest timer entirely (called when the macro is stopped manually
     * and persistSessionTimer is off, or during a dynamic-rest disconnect).
     */
    public static void reset() {
        nextRestTriggerMs = 0;
        scheduledDurationMs = 0;
        pausedRemainingMs = -1;
        restSequencePending = false;
        restSequenceStage = 0;
        nextStageActionTime = 0;
    }

    /** Returns true while a rest sequence is actively in progress. */
    public static boolean isRestPending() {
        return restSequencePending;
    }

    /** Returns the scheduled rest trigger time (epoch ms), or 0 if not set.
     *  When the timer is paused, returns a synthetic epoch-ms based on the saved remainder. */
    public static long getNextRestTriggerMs() {
        if (nextRestTriggerMs > 0) return nextRestTriggerMs;
        if (pausedRemainingMs >= 0) return System.currentTimeMillis() + pausedRemainingMs;
        return 0;
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
     *
     * The timer now ticks during ALL active macro states (FARMING, CLEANING,
     * VISITING, SPRAYING, AUTOSELLING) — it only stops when the macro is OFF
     * or RECOVERING. This ensures the dynamic rest schedule reflects total
     * active macro time, not just pure farming time.
     */
    public static void update(Minecraft client) {
        if (client.player == null)
            return;

        MacroState.State currentState = MacroStateManager.getCurrentState();
        boolean isFarming = currentState == MacroState.State.FARMING;
        boolean macroActive = currentState != MacroState.State.OFF
                && currentState != MacroState.State.RECOVERING;
        long now = System.currentTimeMillis();

        // === Timer Logic ===
        // Timer ticks whenever the macro is in any active state.
        // No longer pauses during CLEANING, VISITING, SPRAYING, etc.
        if (nextRestTriggerMs > 0 && !restSequencePending && macroActive) {
            if (now >= nextRestTriggerMs) {
                restSequencePending = true;
                restSequenceStage = 0;
                nextStageActionTime = now;
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("§6[Ihanuat] Dynamic Rest triggered! Starting shutdown sequence..."),
                            false);
                }
            }
        }

        // === Shutdown sequence — waits until in FARMING state to execute safely ===
        if (!restSequencePending)
            return;

        if (now < nextStageActionTime)
            return;

        switch (restSequenceStage) {
            case 0: {
                // Wait until we are back in FARMING state before executing.
                // This prevents interrupting active pest/visitor sequences.
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
                //nextStageActionTime = System.currentTimeMillis() + 3000; // Fallback timeout of 3 seconds
                break;
            }
            case 1: {
                // Supercraft before leaving
                if (MacroConfig.superCraftBeforeRest) {
                    if (!SuperCrafter.isCrafting && !SuperCrafter.isComplete()) {
                        SuperCrafter.startSuperCraft(client);
                        return;
                    }

                    if (!SuperCrafter.isComplete()) {
                        return;
                    }

                    SuperCrafter.reset();
                }
                // Check if spawn was set, or wait for fallback timeout
                if (!com.ihanuat.mod.util.CommandUtils.hasSpawnBeenSet()
                        && System.currentTimeMillis() < nextStageActionTime) {
                    return; // Still waiting for spawn confirmation
                }

                // Disconnect and schedule the reconnect after the break duration
                long minSecs = MacroConfig.restBreakTimeMin * 60L;
                long maxSecs = MacroConfig.restBreakTimeMax * 60L;
                if (maxSecs < minSecs) maxSecs = minSecs;
                long breakSeconds = minSecs + (long)(new Random().nextDouble() * (maxSecs - minSecs));

                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal(String.format(
                                    "§c[Ihanuat] Dynamic Rest: disconnecting. Reconnecting in %.1f minutes...",
                                    (double) breakSeconds / 60.0)),
                            false);
                }

                long durationMs = breakSeconds * 1000;
                long restEndTimeMs = System.currentTimeMillis() + durationMs;

                // Reset the trigger so scheduleNextRest() can pick up a new time after recovery
                nextRestTriggerMs = 0;

                client.execute(() -> {
                    MacroStateManager.setIntentionalDisconnect(true);
                    ReconnectScheduler.scheduleReconnect(breakSeconds, true);
                    client.disconnect(new DynamicRestScreen(restEndTimeMs, durationMs), false);
                });

                restSequenceStage = 2;
                nextStageActionTime = Long.MAX_VALUE; // no further stages needed
                break;
            }
            default:
                break;
        }
    }
}