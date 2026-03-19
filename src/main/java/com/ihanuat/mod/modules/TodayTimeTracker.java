package com.ihanuat.mod.modules;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * TodayTimeTracker
 * ================
 * Tracks how long the player has been actively farming today (calendar day,
 * local timezone). Dynamic-rest breaks are excluded because the macro is in
 * RECOVERING state during those periods and the underlying session timer
 * already pauses.
 *
 * Design principles (simple = robust):
 *  - One date string  (yyyy-MM-dd) and one accumulated-ms long in MacroConfig.
 *  - On each tick we check if the calendar date changed; if so we reset.
 *  - We mirror the session-timer pattern: accumulate while FARMING/active,
 *    pause while OFF/RECOVERING.
 *  - All state is in MacroConfig so it survives save/load cycles.
 */
public class TodayTimeTracker {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** epoch-ms of the most recent "farming started for today" moment, or 0. */
    private static volatile long todaySegmentStart = 0;

    // ── Public API ─────────────────────────────────────────────────────────

    /** Called by MacroStateManager when macro becomes active (leaves OFF/RECOVERING). */
    public static void onMacroStart() {
        checkDayRollover();
        if (todaySegmentStart == 0) {
            todaySegmentStart = System.currentTimeMillis();
        }
    }

    /** Called by MacroStateManager when macro stops or enters RECOVERING. */
    public static void onMacroPause() {
        if (todaySegmentStart != 0) {
            MacroConfig.todayAccumulatedMs += System.currentTimeMillis() - todaySegmentStart;
            todaySegmentStart = 0;
            MacroConfig.save();
        }
    }

    /**
     * Called from IhanuatClient tick loop. Handles day-rollover mid-session.
     * Must be cheap — just a date string comparison.
     */
    public static void tick() {
        checkDayRollover();
    }

    /**
     * Returns total active farming ms for today (live, including current segment).
     */
    public static long getTodayMs() {
        checkDayRollover();
        long base = MacroConfig.todayAccumulatedMs;
        if (todaySegmentStart != 0) {
            base += System.currentTimeMillis() - todaySegmentStart;
        }
        return Math.max(0, base);
    }

    /** Called from MacroStateManager.syncFromConfig after config load. */
    public static void syncFromConfig() {
        // Ensure we are not mid-segment after a load — segment will restart
        // via onMacroStart if macro is already running.
        todaySegmentStart = 0;
        checkDayRollover();
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private static void checkDayRollover() {
        String today = LocalDate.now().format(DATE_FMT);
        if (!today.equals(MacroConfig.todayDateStr)) {
            // New day — flush any live segment into the old day (discard it),
            // then reset.
            todaySegmentStart = 0;
            MacroConfig.todayDateStr = today;
            MacroConfig.todayAccumulatedMs = 0;
            // Don't save here to avoid a save every tick on first launch;
            // the next periodic save or stop will persist it.
        }
    }
}
