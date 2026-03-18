package com.ihanuat.mod;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Tracks how long the player has actively farmed today (calendar-day-based).
 *
 * Rules:
 *  - Only accumulates time when the macro is in a non-OFF, non-RECOVERING state.
 *  - Automatically resets when the system date changes (new day).
 *  - Excludes Dynamic Rest break time (because MacroStateManager already stops
 *    the running timer during RECOVERING state).
 *  - Persisted across relaunches via MacroConfig.todayDateStr / todayAccumulatedMs.
 */
public class TodayTracker {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Wall-clock ms at which the current active segment started (0 = not running). */
    private static volatile long segmentStartMs = 0;

    /** Already-accumulated ms for today BEFORE the current segment. */
    private static volatile long accumulatedMs = 0;

    /** The calendar date we loaded / reset for. */
    private static volatile String trackedDate = "";

    // ── Initialisation ────────────────────────────────────────────────────────

    /**
     * Called once at client start-up after MacroConfig.load().
     * Reuses the persisted value if the date matches, otherwise resets.
     */
    public static void init() {
        String today = today();
        String saved = MacroConfig.todayDateStr != null ? MacroConfig.todayDateStr : "";
        if (today.equals(saved)) {
            accumulatedMs = Math.max(0, MacroConfig.todayAccumulatedMs);
        } else {
            accumulatedMs = 0;
            MacroConfig.todayDateStr = today;
            MacroConfig.todayAccumulatedMs = 0;
        }
        trackedDate = today;
        segmentStartMs = 0;
    }

    // ── State changes called by MacroStateManager ─────────────────────────────

    /** Called when the macro transitions from an inactive state to an active one. */
    public static void onMacroStart() {
        maybeRolloverDate();
        if (segmentStartMs == 0)
            segmentStartMs = System.currentTimeMillis();
    }

    /** Called when the macro transitions from active to inactive (OFF or RECOVERING). */
    public static void onMacroStop() {
        if (segmentStartMs != 0) {
            accumulatedMs += System.currentTimeMillis() - segmentStartMs;
            segmentStartMs = 0;
            persist();
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /** Total active farming ms for today (safe to call from the render thread). */
    public static long getTodayMs() {
        maybeRolloverDate();
        if (segmentStartMs != 0)
            return accumulatedMs + (System.currentTimeMillis() - segmentStartMs);
        return accumulatedMs;
    }

    // ── Periodic save (called by MacroStateManager.periodicUpdate) ─────────────

    public static void periodicSave() {
        if (segmentStartMs != 0) {
            // Update in-memory config value for the save call that follows
            MacroConfig.todayAccumulatedMs = accumulatedMs + (System.currentTimeMillis() - segmentStartMs);
        } else {
            MacroConfig.todayAccumulatedMs = accumulatedMs;
        }
        MacroConfig.todayDateStr = trackedDate;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void persist() {
        MacroConfig.todayAccumulatedMs = accumulatedMs;
        MacroConfig.todayDateStr = trackedDate;
    }

    private static void maybeRolloverDate() {
        String today = today();
        if (!today.equals(trackedDate)) {
            // New calendar day — stop any running segment and reset
            segmentStartMs = 0;
            accumulatedMs = 0;
            trackedDate = today;
            persist();
        }
    }

    private static String today() {
        return LocalDate.now().format(DATE_FMT);
    }
}
