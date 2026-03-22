package com.ihanuat.mod.gui;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroState;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.modules.DynamicRestManager;
import com.ihanuat.mod.modules.QuitThresholdManager;
import com.ihanuat.mod.modules.TodayTimeTracker;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.ihanuat.mod.util.ClientUtils;

/**
 * Renders the macro status panel HUD.
 *
 * Layout (rows beneath separator):
 *   macro state
 *   current session
 *   next rest
 *   total today          ← new (replaces "lifetime session")
 *   [progress bar]
 *   quit in              ← only when threshold > 0
 *
 * All colors are read from MacroConfig at render time so the HUD Colors tab
 * takes effect immediately without a restart.
 */
public class MacroHudRenderer {

    // ── Base layout (at scale = 1.0) ─────────────────────────────────────────
    static final int PANEL_W = 230;
    private static final int PADDING_H = 7;
    private static final int PADDING_V = 5;
    private static final int FONT_H = 9;
    private static final int ROW_HEIGHT = 11;
    private static final int CORNER_RADIUS = 6;

    // Edit-mode border colors (fixed — not user-configurable)
    private static final int BORDER_IDLE   = 0xFF6464B4;
    private static final int BORDER_DRAG   = 0xFFAAAAFF;
    private static final int BORDER_RESIZE = 0xFFFFAA00;

    // ── Title animation ───────────────────────────────────────────────────────
    private static final String TARGET_TITLE = "ihanuat";
    private static final char[] SCRAMBLE_CHARS = { '*', '/', '_', '\\', '|', '#', '!', '%', '&' };
    private static final int CHAR_INTERVAL_MS = 90;
    private static final int SCRAMBLE_MS = 70;
    private static final int STAY_MS = 7000;
    private static long animPhaseStartMs = -1;
    private static int animPhase = 0;

    // ── Drag / resize state ───────────────────────────────────────────────────
    private static boolean isDragging = false;
    private static boolean isResizing = false;
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;
    private static float resizeStartScale = 1f;
    private static double resizeStartMouseX = 0;

    // ── Registration ─────────────────────────────────────────────────────────
    public static void register() {
        HudRenderCallback.EVENT.register((guiGraphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (MacroConfig.showHud && shouldShow(client))
                render(guiGraphics, client, false);
        });
    }

    private static boolean shouldShow(Minecraft client) {
        if (!MacroConfig.guiOnlyInGarden) return true;
        return ClientUtils.getCurrentLocation(client) == MacroState.Location.GARDEN;
    }

    // ── Public API for ScreenEvents ──────────────────────────────────────────
    public static void renderInEditMode(GuiGraphics g, Minecraft client) {
        if (!MacroConfig.showHud || !shouldShow(client)) return;
        render(g, client, true);
    }

    public static boolean isInteracting() { return isDragging || isResizing; }

    public static boolean isHovered(double mouseX, double mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (!shouldShow(client)) return false;
        float scale = MacroConfig.hudScale;
        double localX = (mouseX - MacroConfig.hudX) / scale;
        double localY = (mouseY - MacroConfig.hudY) / scale;
        return localX >= 0 && localX <= PANEL_W && localY >= 0 && localY <= panelH();
    }

    public static void startDrag(double mouseX, double mouseY, boolean ctrl) {
        if (ctrl) {
            isResizing = true;
            resizeStartScale = MacroConfig.hudScale;
            resizeStartMouseX = mouseX;
        } else {
            isDragging = true;
            dragOffsetX = (int)(mouseX - MacroConfig.hudX);
            dragOffsetY = (int)(mouseY - MacroConfig.hudY);
        }
    }

    public static void drag(double mouseX, double mouseY) {
        if (isDragging) {
            MacroConfig.hudX = (int)(mouseX - dragOffsetX);
            MacroConfig.hudY = (int)(mouseY - dragOffsetY);
            Minecraft mc = Minecraft.getInstance();
            if (mc.getWindow() != null) {
                int sw = mc.getWindow().getGuiScaledWidth();
                int sh = mc.getWindow().getGuiScaledHeight();
                float s = MacroConfig.hudScale;
                MacroConfig.hudX = Math.max(0, Math.min(MacroConfig.hudX, sw - (int)(PANEL_W * s)));
                MacroConfig.hudY = Math.max(0, Math.min(MacroConfig.hudY, sh - (int)(panelH() * s)));
            }
        } else if (isResizing) {
            double delta = mouseX - resizeStartMouseX;
            MacroConfig.hudScale = Math.max(0.5f, Math.min(2.5f, resizeStartScale + (float)(delta * 0.005)));
        }
    }

    public static void endDrag() {
        if (isDragging || isResizing) {
            isDragging = false;
            isResizing = false;
            MacroConfig.save();
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────────
    private static void render(GuiGraphics g, Minecraft client, boolean editMode) {
        if (client.player == null) return;

        // -- Resolve colors from config (always fresh) --
        int bgColor    = MacroConfig.toArgb(MacroConfig.hudBgColor);
        int sepColor   = MacroConfig.toArgb(MacroConfig.hudAccentColor);
        int titleColor = MacroConfig.toArgb(MacroConfig.hudTitleColor);
        int labelColor = MacroConfig.toArgb(MacroConfig.hudLabelColor);
        int valueColor = MacroConfig.toArgb(MacroConfig.hudValueColor);

        MacroState.State state = MacroStateManager.getCurrentState();
        String stateStr;
        int stateColor;
        switch (state) {
            case FARMING:    stateStr = "farming";     stateColor = MacroConfig.toArgb(MacroConfig.hudStateFarmingColor); break;
            case CLEANING:   stateStr = "cleaning";    stateColor = MacroConfig.toArgb(MacroConfig.hudStateCleaningColor); break;
            case RECOVERING: stateStr = "recovering";  stateColor = MacroConfig.toArgb(MacroConfig.hudStateRecoveringColor); break;
            case VISITING:   stateStr = "visitor";     stateColor = MacroConfig.toArgb(MacroConfig.hudStateVisitingColor); break;
            case AUTOSELLING:stateStr = "autoselling"; stateColor = MacroConfig.toArgb(MacroConfig.hudStateAutosellingColor); break;
            case SPRAYING:   stateStr = "sprayonator"; stateColor = MacroConfig.toArgb(MacroConfig.hudStateSprayingColor); break;
            default:         stateStr = "off";         stateColor = MacroConfig.toArgb(MacroConfig.hudStateOffColor); break;
        }

        long sessionMs  = MacroStateManager.getSessionRunningTime();
        long todayMs    = TodayTimeTracker.getTodayMs();
        long restTriggerMs = DynamicRestManager.getNextRestTriggerMs();
        String nextRestStr = restTriggerMs <= 0 ? "---"
                : formatTime(Math.max(0, restTriggerMs - System.currentTimeMillis()));

        int panelH = panelH();
        float scale = MacroConfig.hudScale;

        g.pose().pushMatrix();
        g.pose().translate(MacroConfig.hudX, MacroConfig.hudY);
        g.pose().scale(scale, scale);

        if (editMode) {
            int borderColor = isDragging ? BORDER_DRAG : isResizing ? BORDER_RESIZE : BORDER_IDLE;
            fillRoundedRect(g, -1, -1, PANEL_W + 2, panelH + 2, CORNER_RADIUS + 1, borderColor);
        }

        fillRoundedRect(g, 0, 0, PANEL_W, panelH, CORNER_RADIUS, bgColor);

        int titleAnchorX = (PANEL_W - client.font.width(TARGET_TITLE)) / 2;
        int titleY = PADDING_V;
        g.drawString(client.font, getAnimatedTitle(), titleAnchorX, titleY, titleColor, false);

        int sepY = titleY + FONT_H + 3;
        g.fill(PADDING_H, sepY, PANEL_W - PADDING_H, sepY + 1, sepColor);

        int rowY = sepY + 1 + 3;
        drawRow(g, client, rowY, "macro state",     stateStr,           stateColor, labelColor);
        rowY += ROW_HEIGHT;
        drawRow(g, client, rowY, "current session", formatTime(sessionMs), valueColor, labelColor);
        rowY += ROW_HEIGHT;
        drawRow(g, client, rowY, "next rest",       nextRestStr,        valueColor, labelColor);
        rowY += ROW_HEIGHT;

        // total today
        if (MacroConfig.showTotalToday) {
            drawRow(g, client, rowY, "total today", formatTime(todayMs), valueColor, labelColor);
            rowY += ROW_HEIGHT;
        }

        // quit threshold (based on total today)
        if (MacroConfig.quitThresholdHours > 0) {
            long quitRemainingMs = QuitThresholdManager.getRemainingMs();
            String quitStr;
            int quitColor;
            if (quitRemainingMs < 0) {
                quitStr = "---"; quitColor = valueColor;
            } else if (quitRemainingMs == 0) {
                quitStr = "stopping..."; quitColor = MacroConfig.toArgb(MacroConfig.hudStateRecoveringColor);
            } else {
                quitStr = formatTime(quitRemainingMs);
                quitColor = quitRemainingMs < 600_000L  ? MacroConfig.toArgb(MacroConfig.hudStateRecoveringColor)
                          : quitRemainingMs < 1_800_000L ? MacroConfig.toArgb(MacroConfig.hudStateCleaningColor)
                          : valueColor;
            }
            drawRow(g, client, rowY, "quit in", quitStr, quitColor, labelColor);
            rowY += ROW_HEIGHT;
        }

        if (editMode) {
            String hint = isDragging ? "moving..." : isResizing ? "resizing..." : "drag \u2022 ctrl+drag to resize";
            int hintX = (PANEL_W - client.font.width(hint)) / 2;
            g.drawString(client.font, hint, hintX, panelH + 3, labelColor, false);
        }

        g.pose().popMatrix();
    }

    // ── Panel height helper ───────────────────────────────────────────────────
    static int panelH() {
        int rows = 3; // state + session + next rest
        if (MacroConfig.showTotalToday) rows++;
        if (MacroConfig.quitThresholdHours > 0) rows++;
        return PADDING_V + FONT_H + 3 + 1 + 3 + rows * ROW_HEIGHT + PADDING_V;
    }

    // ── Title animation ───────────────────────────────────────────────────────
    private static String getAnimatedTitle() {
        long now = System.currentTimeMillis();
        if (animPhaseStartMs < 0) { animPhaseStartMs = now; animPhase = 0; }
        int n = TARGET_TITLE.length();
        long phaseDuration = (long)(n - 1) * CHAR_INTERVAL_MS + SCRAMBLE_MS;
        long elapsed = now - animPhaseStartMs;
        if (animPhase == 0 && elapsed >= phaseDuration)  { animPhase = 1; animPhaseStartMs = now; }
        else if (animPhase == 1 && elapsed >= STAY_MS)   { animPhase = 2; animPhaseStartMs = now; }
        else if (animPhase == 2 && elapsed >= phaseDuration) { animPhase = 0; animPhaseStartMs = now; }
        elapsed = now - animPhaseStartMs;
        if (animPhase == 1) return TARGET_TITLE;
        StringBuilder sb = new StringBuilder();
        if (animPhase == 0) {
            for (int i = 0; i < n; i++) {
                long cs = (long)i * CHAR_INTERVAL_MS;
                if (elapsed < cs) break;
                if (elapsed < cs + SCRAMBLE_MS) { sb.append(SCRAMBLE_CHARS[(int)((elapsed - cs) / 20) % SCRAMBLE_CHARS.length]); break; }
                sb.append(TARGET_TITLE.charAt(i));
            }
        } else {
            for (int i = 0; i < n; i++) {
                long cs = (long)(n - 1 - i) * CHAR_INTERVAL_MS;
                if (elapsed < cs) sb.append(TARGET_TITLE.charAt(i));
                else if (elapsed < cs + SCRAMBLE_MS) { sb.append(SCRAMBLE_CHARS[(int)((elapsed - cs) / 20) % SCRAMBLE_CHARS.length]); break; }
                else break;
            }
        }
        return sb.toString();
    }

    // ── Draw helpers ─────────────────────────────────────────────────────────
    private static void drawRow(GuiGraphics g, Minecraft client,
            int y, String label, String value, int valueColor, int labelColor) {
        g.drawString(client.font, label, PADDING_H, y, labelColor, false);
        int valueX = PANEL_W - PADDING_H - client.font.width(value);
        g.drawString(client.font, value, valueX, y, valueColor, false);
    }

    private static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        for (int row = 0; row < h; row++) {
            int indent = 0;
            if (row < r) { double d = r - row - 0.5; indent = (int)Math.ceil(r - 0.5 - Math.sqrt(r*r - d*d)); }
            else if (row >= h - r) { double d = row - (h - r) + 0.5; indent = (int)Math.ceil(r - 0.5 - Math.sqrt(r*r - d*d)); }
            g.fill(x + indent, y + row, x + w - indent, y + row + 1, color);
        }
    }

    private static String formatTime(long ms) {
        long totalSecs = ms / 1000;
        long hours = totalSecs / 3600, mins = (totalSecs % 3600) / 60, secs = totalSecs % 60;
        return hours > 0 ? String.format("%d:%02d:%02d", hours, mins, secs) : String.format("%02d:%02d", mins, secs);
    }
}
