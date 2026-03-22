package com.ihanuat.mod.gui;

import java.util.Map;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.modules.profitTracker.ProfitManager;
import com.ihanuat.mod.util.ClientUtils;
import com.ihanuat.mod.MacroState;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders the Profit Tracker HUDs (Session and Lifetime).
 * Daily HUD removed; all colors read from MacroConfig at render time.
 */
public class ProfitHudRenderer {

    static final int PANEL_W = 280;
    private static final int PADDING_H = 7;
    private static final int PADDING_V = 5;
    private static final int FONT_H = 9;
    private static final int ROW_HEIGHT = 11;
    private static final int CORNER_RADIUS = 6;

    // Fixed UI chrome colors (not user-configurable — consistent look)
    private static final int BORDER_IDLE   = 0xFF6464B4;
    private static final int BORDER_DRAG   = 0xFFAAAAFF;
    private static final int BORDER_RESIZE = 0xFFFFAA00;

    // Drag / resize — Session HUD
    private static boolean isDraggingSession = false, isResizingSession = false;
    private static int dragOffsetXSession = 0, dragOffsetYSession = 0;
    private static float resizeStartScaleSession = 1f;
    private static double resizeStartMouseXSession = 0;

    // Drag / resize — Lifetime HUD
    private static boolean isDraggingLifetime = false, isResizingLifetime = false;
    private static int dragOffsetXLifetime = 0, dragOffsetYLifetime = 0;
    private static float resizeStartScaleLifetime = 1f;
    private static double resizeStartMouseXLifetime = 0;

    // ── Registration ─────────────────────────────────────────────────────────
    public static void register() {
        HudRenderCallback.EVENT.register((guiGraphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (MacroConfig.guiOnlyInGarden && ClientUtils.getCurrentLocation(client) != MacroState.Location.GARDEN)
                return;
            boolean showAny = MacroStateManager.isMacroRunning() || MacroConfig.showProfitHudWhileInactive;
            if (showAny) {
                if (MacroConfig.showSessionProfitHud) render(guiGraphics, client, "session", false);
                if (MacroConfig.showLifetimeHud)      render(guiGraphics, client, "lifetime", false);
            }
        });
    }

    // ── Edit-mode render ──────────────────────────────────────────────────────
    public static void renderInEditMode(GuiGraphics g, Minecraft client) {
        if (MacroConfig.guiOnlyInGarden && ClientUtils.getCurrentLocation(client) != MacroState.Location.GARDEN)
            return;
        if (MacroConfig.showSessionProfitHud) render(g, client, "session", true);
        if (MacroConfig.showLifetimeHud)      render(g, client, "lifetime", true);
    }

    // ── Interaction ───────────────────────────────────────────────────────────
    public static boolean isInteracting() {
        return isDraggingSession || isResizingSession || isDraggingLifetime || isResizingLifetime;
    }

    public static boolean isHovered(double mouseX, double mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (MacroConfig.guiOnlyInGarden && ClientUtils.getCurrentLocation(client) != MacroState.Location.GARDEN)
            return false;
        if (MacroConfig.showSessionProfitHud && isHoveredInternal(mouseX, mouseY, "session")) return true;
        if (MacroConfig.showLifetimeHud      && isHoveredInternal(mouseX, mouseY, "lifetime")) return true;
        return false;
    }

    private static boolean isHoveredInternal(double mx, double my, String mode) {
        float scale; int x, y;
        if ("lifetime".equals(mode)) { scale = MacroConfig.lifetimeHudScale; x = MacroConfig.lifetimeHudX; y = MacroConfig.lifetimeHudY; }
        else                         { scale = MacroConfig.sessionProfitHudScale; x = MacroConfig.sessionProfitHudX; y = MacroConfig.sessionProfitHudY; }
        double lx = (mx - x) / scale, ly = (my - y) / scale;
        return lx >= 0 && lx <= PANEL_W && ly >= 0 && ly <= panelH(mode);
    }

    public static void startDrag(double mx, double my, boolean ctrl) {
        if (isHoveredInternal(mx, my, "lifetime")) {
            if (ctrl) { isResizingLifetime = true; resizeStartScaleLifetime = MacroConfig.lifetimeHudScale; resizeStartMouseXLifetime = mx; }
            else       { isDraggingLifetime = true; dragOffsetXLifetime = (int)(mx - MacroConfig.lifetimeHudX); dragOffsetYLifetime = (int)(my - MacroConfig.lifetimeHudY); }
        } else if (isHoveredInternal(mx, my, "session")) {
            if (ctrl) { isResizingSession = true; resizeStartScaleSession = MacroConfig.sessionProfitHudScale; resizeStartMouseXSession = mx; }
            else       { isDraggingSession = true; dragOffsetXSession = (int)(mx - MacroConfig.sessionProfitHudX); dragOffsetYSession = (int)(my - MacroConfig.sessionProfitHudY); }
        }
    }

    public static void drag(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        if (isDraggingSession) {
            MacroConfig.sessionProfitHudX = Math.max(0, Math.min((int)(mx - dragOffsetXSession), sw - (int)(PANEL_W * MacroConfig.sessionProfitHudScale)));
            MacroConfig.sessionProfitHudY = Math.max(0, Math.min((int)(my - dragOffsetYSession), sh - (int)(panelH("session") * MacroConfig.sessionProfitHudScale)));
        } else if (isResizingSession) {
            MacroConfig.sessionProfitHudScale = Math.max(0.5f, Math.min(2.5f, resizeStartScaleSession + (float)((mx - resizeStartMouseXSession) * 0.005)));
        } else if (isDraggingLifetime) {
            MacroConfig.lifetimeHudX = Math.max(0, Math.min((int)(mx - dragOffsetXLifetime), sw - (int)(PANEL_W * MacroConfig.lifetimeHudScale)));
            MacroConfig.lifetimeHudY = Math.max(0, Math.min((int)(my - dragOffsetYLifetime), sh - (int)(panelH("lifetime") * MacroConfig.lifetimeHudScale)));
        } else if (isResizingLifetime) {
            MacroConfig.lifetimeHudScale = Math.max(0.5f, Math.min(2.5f, resizeStartScaleLifetime + (float)((mx - resizeStartMouseXLifetime) * 0.005)));
        }
    }

    public static void endDrag() {
        if (isInteracting()) {
            isDraggingSession = false; isResizingSession = false;
            isDraggingLifetime = false; isResizingLifetime = false;
            MacroConfig.save();
        }
    }

    // ── Rendering ────────────────────────────────────────────────────────────
    private static void render(GuiGraphics g, Minecraft client, String mode, boolean editMode) {
        if (client.player == null) return;
        ProfitManager.ProfitHudSnapshot snapshot = ProfitManager.getHudSnapshot(mode);

        // Resolve colors from config
        int bgColor    = MacroConfig.toArgb(MacroConfig.hudBgColor);
        int sepColor   = MacroConfig.toArgb(MacroConfig.hudAccentColor);
        int titleColor = MacroConfig.toArgb(MacroConfig.hudTitleColor);
        int labelColor = MacroConfig.toArgb(MacroConfig.hudLabelColor);
        int valueColor = MacroConfig.toArgb(MacroConfig.hudValueColor);

        float scale; int x, y;
        if ("lifetime".equals(mode)) { scale = MacroConfig.lifetimeHudScale; x = MacroConfig.lifetimeHudX; y = MacroConfig.lifetimeHudY; }
        else                         { scale = MacroConfig.sessionProfitHudScale; x = MacroConfig.sessionProfitHudX; y = MacroConfig.sessionProfitHudY; }

        int panelH = panelH(mode, snapshot);
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);

        if (editMode) {
            boolean drag = "lifetime".equals(mode) ? isDraggingLifetime : isDraggingSession;
            boolean resi = "lifetime".equals(mode) ? isResizingLifetime : isResizingSession;
            int bColor = drag ? BORDER_DRAG : resi ? BORDER_RESIZE : BORDER_IDLE;
            fillRoundedRect(g, -1, -1, PANEL_W + 2, panelH + 2, CORNER_RADIUS + 1, bColor);
        }

        fillRoundedRect(g, 0, 0, PANEL_W, panelH, CORNER_RADIUS, bgColor);

        String title = "lifetime".equals(mode) ? "Lifetime Profit" : "Session Profit";
        g.drawString(client.font, title, (PANEL_W - client.font.width(title)) / 2, PADDING_V, titleColor, false);

        int rowY = PADDING_V + FONT_H + 3;
        g.fill(PADDING_H, rowY, PANEL_W - PADDING_H, rowY + 1, sepColor);
        rowY += 4;

        if (MacroConfig.compactProfitCalculator) {
            Map<String, Long> drops = snapshot.compactDrops();
            for (Map.Entry<String, Long> e : drops.entrySet()) {
                if (e.getValue() != 0) {
                    int vc = e.getKey().equals("Costs") ? 0xFFFF5555 : 0xFFFFFF55;
                    drawRow(g, client, rowY, ProfitManager.getCompactCategoryLabel(e.getKey()), formatProfit(e.getValue()), vc, labelColor);
                    rowY += ROW_HEIGHT;
                }
            }
        } else {
            Map<String, Long> drops = snapshot.activeDrops();
            for (Map.Entry<String, Long> e : drops.entrySet()) {
                String item = e.getKey(); long count = e.getValue();
                double price = ProfitManager.getItemPrice(item);
                long lineProfit = (long)(price * count);
                String countDisplay;
                if (item.equals("[Spray] Sprayonator")) countDisplay = "x" + String.format("%,d", ProfitManager.getSprayQuantity(mode));
                else if (item.startsWith("Pet XP ("))   countDisplay = String.format("%,d XP", count);
                else                                     countDisplay = "x" + String.format("%,d", count);
                String lbl = ProfitManager.getCategorizedName(item) + " §r(" + countDisplay + ")";
                int vc;
                if (item.equals("[Visitor] Visitor Cost") || item.equals("[Spray] Sprayonator")) vc = 0xFFFF5555;
                else if (item.startsWith("[Visitor] ")) vc = 0xFFFFFF55;
                else vc = ProfitManager.isPredefinedTrackedItem(item) ? 0xFFFFFF55 : valueColor;
                drawRow(g, client, rowY, lbl, formatProfit(lineProfit), vc, labelColor);
                rowY += ROW_HEIGHT;
            }
        }

        // Total / CPH rows
        if (rowY > PADDING_V + FONT_H + 3 + 4) {
            g.fill(PADDING_H, rowY + 1, PANEL_W - PADDING_H, rowY + 2, sepColor);
            rowY += 4;
            long total = snapshot.totalProfit();
            drawRow(g, client, rowY, "Total Profit", formatProfit(total), 0xFFFFAA00, labelColor);
            rowY += ROW_HEIGHT;
            if ("session".equals(mode)) {
                long sessionMs = MacroStateManager.getSessionRunningTime();
                long cph = sessionMs > 0 ? (long)(total / (sessionMs / 3_600_000.0)) : 0;
                drawRow(g, client, rowY, "Coins per Hour", formatProfit(cph), 0xFF55FFFF, labelColor);
            }
        }

        g.pose().popMatrix();
    }

    private static int panelH(String mode) {
        return panelH(mode, ProfitManager.getHudSnapshot(mode));
    }

    private static int panelH(String mode, ProfitManager.ProfitHudSnapshot snapshot) {
        int baseH = PADDING_V + FONT_H + 3 + 4;
        int itemCount = MacroConfig.compactProfitCalculator
                ? snapshot.compactNonZeroCount()
                : snapshot.activeItemCount();
        if (itemCount > 0) {
            int extra = "session".equals(mode) ? 1 : 0;
            baseH += itemCount * ROW_HEIGHT + 4 + ROW_HEIGHT + extra * ROW_HEIGHT + PADDING_V;
        } else {
            baseH += ROW_HEIGHT + PADDING_V;
        }
        return baseH;
    }

    private static void drawRow(GuiGraphics g, Minecraft client, int y, String label, String value, int valueColor, int labelColor) {
        g.drawString(client.font, label, PADDING_H, y, labelColor, false);
        g.drawString(client.font, value, PANEL_W - PADDING_H - client.font.width(value), y, valueColor, false);
    }

    private static String formatProfit(long amount) { return String.format("%,d", amount); }

    private static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        for (int row = 0; row < h; row++) {
            int indent = 0;
            if (row < r) { double d = r - row - 0.5; indent = (int)Math.ceil(r - 0.5 - Math.sqrt(r*r - d*d)); }
            else if (row >= h - r) { double d = row - (h - r) + 0.5; indent = (int)Math.ceil(r - 0.5 - Math.sqrt(r*r - d*d)); }
            g.fill(x + indent, y + row, x + w - indent, y + row + 1, color);
        }
    }
}
