package com.ihanuat.mod.gui;

import com.ihanuat.mod.MacroConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import com.ihanuat.mod.ReconnectScheduler;

import org.jetbrains.annotations.NotNull;

public class DynamicRestScreen extends Screen {
    private final long restEndTimeMs;
    private final long totalDurationMs;

    // Matching MacroHudRenderer styles
    private static final int PANEL_W = 230;

    public DynamicRestScreen(long restEndTimeMs, long totalDurationMs) {
        super(Component.literal("Dynamic Rest"));
        this.restEndTimeMs = restEndTimeMs;
        this.totalDurationMs = totalDurationMs;
    }

    @Override
    protected void init() {
        super.init();
        // Move button further down to not overlap our HUD panel
        this.addRenderableWidget(Button.builder(Component.literal("Cancel Rest & Exit to Menu"), button -> {
            ReconnectScheduler.cancel();
            if (this.minecraft != null) {
                this.minecraft.setScreen(new TitleScreen());
            }
        }).bounds(this.width / 2 - 100, this.height / 2 + 50, 200, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Dark background fill
        graphics.fill(0, 0, this.width, this.height, 0xFF0A0A14);

        super.render(graphics, mouseX, mouseY, partialTick);

        // Read colors live from MacroConfig so HUD Colors tab applies here too
        int BG_COLOR      = 0xFF000000 | MacroConfig.hudBgColor;
        int BAR_BG_COLOR  = 0xFF000000 | MacroConfig.hudBarBgColor;
        int BAR_FILL_COLOR= 0xFF000000 | MacroConfig.hudBarFillColor;
        int TITLE_COLOR   = 0xFF000000 | MacroConfig.hudTitleColor;
        int LABEL_COLOR   = 0xFF000000 | MacroConfig.hudLabelColor;
        int ACCENT_COLOR  = 0xFF000000 | MacroConfig.hudAccentColor;

        long now = System.currentTimeMillis();
        long remainingMs = Math.max(0, restEndTimeMs - now);
        float progress = totalDurationMs > 0
                ? (float) (totalDurationMs - remainingMs) / (float) totalDurationMs
                : 1.0f;

        int panelH = 75;
        int x = (this.width - PANEL_W) / 2;
        int y = (this.height - panelH) / 2 - 20;

        // Draw HUD-style background
        fillRoundedRect(graphics, x, y, PANEL_W, panelH, 6, BG_COLOR);

        // Title
        String title = "Dynamic Rest";
        graphics.drawString(this.font, title, x + (PANEL_W - this.font.width(title)) / 2, y + 8, TITLE_COLOR, false);

        // Separator
        graphics.fill(x + 10, y + 22, x + PANEL_W - 10, y + 23, ACCENT_COLOR);

        // Status Row
        String label = "reconnecting in";
        long totalSecs = remainingMs / 1000;
        String timeStr = String.format("%02d:%02d", (totalSecs / 60) % 60, totalSecs % 60);

        graphics.drawString(this.font, label, x + 10, y + 32, LABEL_COLOR, false);
        graphics.drawString(this.font, timeStr, x + PANEL_W - 10 - this.font.width(timeStr), y + 32, 0xFFFFFFFF, false);

        // Progress Bar
        drawProgressBar(graphics, x + 10, y + 50, PANEL_W - 20, 6, progress, BAR_BG_COLOR, BAR_FILL_COLOR);

        if (remainingMs <= 0) {
            String reconnecting = "Reconnecting now...";
            graphics.drawCenteredString(this.font, reconnecting, this.width / 2, y + panelH + 10, 0xFFAA00);
        }
    }

    private void drawProgressBar(GuiGraphics g, int x, int y, int w, int h, float progress, int bgColor,
            int fillColor) {
        int r = h / 2;
        fillRoundedRect(g, x, y, w, h, r, bgColor);

        int fillW = Math.round(w * Math.max(0f, Math.min(1f, progress)));
        if (fillW <= 0)
            return;

        for (int row = 0; row < h; row++) {
            int indent = 0;
            if (row < r) {
                double d = r - row - 0.5;
                indent = (int) Math.ceil(r - 0.5 - Math.sqrt(r * r - d * d));
            } else if (row >= h - r) {
                double d = row - (h - r) + 0.5;
                indent = (int) Math.ceil(r - 0.5 - Math.sqrt(r * r - d * d));
            }
            int rowStart = x + indent;
            int rowEnd = Math.min(x + fillW, x + w - indent);
            if (rowStart < rowEnd)
                g.fill(rowStart, y + row, rowEnd, y + row + 1, fillColor);
        }
    }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        for (int row = 0; row < h; row++) {
            int indent = 0;
            if (row < r) {
                double d = r - row - 0.5;
                indent = (int) Math.ceil(r - 0.5 - Math.sqrt(r * r - d * d));
            } else if (row >= h - r) {
                double d = row - (h - r) + 0.5;
                indent = (int) Math.ceil(r - 0.5 - Math.sqrt(r * r - d * d));
            }
            g.fill(x + indent, y + row, x + w - indent, y + row + 1, color);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
