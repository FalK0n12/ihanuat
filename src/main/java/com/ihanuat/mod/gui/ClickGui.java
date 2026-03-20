package com.ihanuat.mod.gui;

import com.ihanuat.mod.MacroConfig;
import com.ihanuat.mod.MacroStateManager;
import com.ihanuat.mod.modules.ProfitManager;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ClickGui extends Screen {

    static int C_BG() {
        return MacroConfig.themePanelBg;
    }

    static int C_HDR() {
        return MacroConfig.themePanelHeader;
    }

    static int C_LINE() {
        return MacroConfig.themeAccent;
    }

    static int C_TXT() {
        return MacroConfig.themeText;
    }

    static int C_DIM() {
        return MacroConfig.themeTextDim;
    }

    static int C_ON() {
        return MacroConfig.themeToggleOn;
    }

    static int C_ON2() {
        return brighten(MacroConfig.themeToggleOn, 0x222222);
    }

    static int C_OFF() {
        return MacroConfig.themeToggleOff;
    }

    static int C_HOVER() {
        return darken(MacroConfig.themePanelHeader, 0x060606);
    }

    static int C_SBGR() {
        return darken(MacroConfig.themePanelBg, 0x050505);
    }

    static int C_SFILL() {
        return MacroConfig.themeSliderFill;
    }

    static int C_SKNOB() {
        return brighten(MacroConfig.themeSliderFill, 0x333333);
    }

    static int C_SPBG() {
        return MacroConfig.themePanelBg;
    }

    static int C_SPBD() {
        return MacroConfig.themeAccent;
    }

    static int C_ACC() {
        return MacroConfig.themeAccent;
    }

    static int C_BTN() {
        return MacroConfig.themeButtonHover;
    }

    static int brighten(int c, int a) {
        return (c & 0xFF000000)
                | (Math.min(255, ((c >> 16) & 0xFF) + ((a >> 16) & 0xFF)) << 16)
                | (Math.min(255, ((c >> 8) & 0xFF) + ((a >> 8) & 0xFF)) << 8)
                | Math.min(255, (c & 0xFF) + (a & 0xFF));
    }

    static int darken(int c, int a) {
        return (c & 0xFF000000)
                | (Math.max(0, ((c >> 16) & 0xFF) - ((a >> 16) & 0xFF)) << 16)
                | (Math.max(0, ((c >> 8) & 0xFF) - ((a >> 8) & 0xFF)) << 8)
                | Math.max(0, (c & 0xFF) - (a & 0xFF));
    }

    static Character fallbackCharFromKey(int key, int scan, int mods) {
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0) return null;
        String name = GLFW.glfwGetKeyName(key, scan);
        if (name == null || name.length() != 1) return null;
        char c = name.charAt(0);
        if ((mods & GLFW.GLFW_MOD_SHIFT) == 0) return c;
        if (Character.isLetter(c)) return Character.toUpperCase(c);
        return switch (c) {
            case '`' -> '~';
            case '1' -> '!';
            case '2' -> '@';
            case '3' -> '#';
            case '4' -> '$';
            case '5' -> '%';
            case '6' -> '^';
            case '7' -> '&';
            case '8' -> '*';
            case '9' -> '(';
            case '0' -> ')';
            case '-' -> '_';
            case '=' -> '+';
            case '[' -> '{';
            case ']' -> '}';
            case '\\' -> '|';
            case ';' -> ':';
            case '\'' -> '"';
            case ',' -> '<';
            case '.' -> '>';
            case '/' -> '?';
            default -> c;
        };
    }

    // theme share code — base64 encoded string
    // v4 = 23 colors + text style name + outline size + shadow opacity
    // backwards compat: v3/v2/legacy decode fine, just skip the new fields
    static String encodeTheme() {
        String s = String.format("v4:%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%s,%d,%d",
                MacroConfig.themePanelBg, MacroConfig.themePanelHeader, MacroConfig.themeAccent,
                MacroConfig.themeText, MacroConfig.themeTextDim, MacroConfig.themeToggleOn,
                MacroConfig.themeToggleOff, MacroConfig.themeSliderFill, MacroConfig.themeButtonHover,
                MacroConfig.hudBgColor, MacroConfig.hudAccentColor, MacroConfig.hudTitleColor,
                MacroConfig.hudLabelColor, MacroConfig.hudValueColor, MacroConfig.hudBarBgColor,
                MacroConfig.hudBarFillColor, MacroConfig.hudStateOffColor, MacroConfig.hudStateFarmingColor,
                MacroConfig.hudStateCleaningColor, MacroConfig.hudStateRecoveringColor,
                MacroConfig.hudStateVisitingColor, MacroConfig.hudStateAutosellingColor,
                MacroConfig.hudStateSprayingColor, MacroConfig.themeTextStyle.name(),
                MacroConfig.themeOutlineSize, MacroConfig.themeShadowOpacity);
        return Base64.getEncoder().encodeToString(s.getBytes());
    }

    static boolean applyThemeCode(String code) {
        try {
            String s = new String(Base64.getDecoder().decode(code.trim()));
            if (s.startsWith("v4:")) {
                // v4 has 23 colors + text style + outline size + shadow opacity
                String[] parts = s.substring(3).split(",");
                if (parts.length != 26) return false;
                MacroConfig.themePanelBg            = (int) Long.parseLong(parts[0], 16);
                MacroConfig.themePanelHeader        = (int) Long.parseLong(parts[1], 16);
                MacroConfig.themeAccent             = (int) Long.parseLong(parts[2], 16);
                MacroConfig.themeText               = (int) Long.parseLong(parts[3], 16);
                MacroConfig.themeTextDim            = (int) Long.parseLong(parts[4], 16);
                MacroConfig.themeToggleOn           = (int) Long.parseLong(parts[5], 16);
                MacroConfig.themeToggleOff          = (int) Long.parseLong(parts[6], 16);
                MacroConfig.themeSliderFill         = (int) Long.parseLong(parts[7], 16);
                MacroConfig.themeButtonHover        = (int) Long.parseLong(parts[8], 16);
                MacroConfig.hudBgColor              = (int) Long.parseLong(parts[9], 16);
                MacroConfig.hudAccentColor          = (int) Long.parseLong(parts[10], 16);
                MacroConfig.hudTitleColor           = (int) Long.parseLong(parts[11], 16);
                MacroConfig.hudLabelColor           = (int) Long.parseLong(parts[12], 16);
                MacroConfig.hudValueColor           = (int) Long.parseLong(parts[13], 16);
                MacroConfig.hudBarBgColor           = (int) Long.parseLong(parts[14], 16);
                MacroConfig.hudBarFillColor         = (int) Long.parseLong(parts[15], 16);
                MacroConfig.hudStateOffColor        = (int) Long.parseLong(parts[16], 16);
                MacroConfig.hudStateFarmingColor    = (int) Long.parseLong(parts[17], 16);
                MacroConfig.hudStateCleaningColor   = (int) Long.parseLong(parts[18], 16);
                MacroConfig.hudStateRecoveringColor = (int) Long.parseLong(parts[19], 16);
                MacroConfig.hudStateVisitingColor   = (int) Long.parseLong(parts[20], 16);
                MacroConfig.hudStateAutosellingColor = (int) Long.parseLong(parts[21], 16);
                MacroConfig.hudStateSprayingColor   = (int) Long.parseLong(parts[22], 16);
                try { MacroConfig.themeTextStyle = MacroConfig.TextStyle.valueOf(parts[23]); }
                catch (IllegalArgumentException ignored) { MacroConfig.themeTextStyle = MacroConfig.TextStyle.NONE; }
                try { MacroConfig.themeOutlineSize   = Math.max(1, Math.min(3, Integer.parseInt(parts[24].trim()))); }
                catch (NumberFormatException ignored) { MacroConfig.themeOutlineSize = 1; }
                try { MacroConfig.themeShadowOpacity = Math.max(0, Math.min(255, Integer.parseInt(parts[25].trim()))); }
                catch (NumberFormatException ignored) { MacroConfig.themeShadowOpacity = 180; }
            } else if (s.startsWith("v3:")) {
                // v3 has 23 colors + text style name
                String[] parts = s.substring(3).split(",");
                if (parts.length != 24) return false;
                MacroConfig.themePanelBg            = (int) Long.parseLong(parts[0], 16);
                MacroConfig.themePanelHeader        = (int) Long.parseLong(parts[1], 16);
                MacroConfig.themeAccent             = (int) Long.parseLong(parts[2], 16);
                MacroConfig.themeText               = (int) Long.parseLong(parts[3], 16);
                MacroConfig.themeTextDim            = (int) Long.parseLong(parts[4], 16);
                MacroConfig.themeToggleOn           = (int) Long.parseLong(parts[5], 16);
                MacroConfig.themeToggleOff          = (int) Long.parseLong(parts[6], 16);
                MacroConfig.themeSliderFill         = (int) Long.parseLong(parts[7], 16);
                MacroConfig.themeButtonHover        = (int) Long.parseLong(parts[8], 16);
                MacroConfig.hudBgColor              = (int) Long.parseLong(parts[9], 16);
                MacroConfig.hudAccentColor          = (int) Long.parseLong(parts[10], 16);
                MacroConfig.hudTitleColor           = (int) Long.parseLong(parts[11], 16);
                MacroConfig.hudLabelColor           = (int) Long.parseLong(parts[12], 16);
                MacroConfig.hudValueColor           = (int) Long.parseLong(parts[13], 16);
                MacroConfig.hudBarBgColor           = (int) Long.parseLong(parts[14], 16);
                MacroConfig.hudBarFillColor         = (int) Long.parseLong(parts[15], 16);
                MacroConfig.hudStateOffColor        = (int) Long.parseLong(parts[16], 16);
                MacroConfig.hudStateFarmingColor    = (int) Long.parseLong(parts[17], 16);
                MacroConfig.hudStateCleaningColor   = (int) Long.parseLong(parts[18], 16);
                MacroConfig.hudStateRecoveringColor = (int) Long.parseLong(parts[19], 16);
                MacroConfig.hudStateVisitingColor   = (int) Long.parseLong(parts[20], 16);
                MacroConfig.hudStateAutosellingColor = (int) Long.parseLong(parts[21], 16);
                MacroConfig.hudStateSprayingColor   = (int) Long.parseLong(parts[22], 16);
                // parse text style, fall back to NONE if unknown
                try { MacroConfig.themeTextStyle = MacroConfig.TextStyle.valueOf(parts[23]); }
                catch (IllegalArgumentException ignored) { MacroConfig.themeTextStyle = MacroConfig.TextStyle.NONE; }
            } else if (s.startsWith("v2:")) {
                // v2 — no text style, leave it as-is
                String[] parts = s.substring(3).split(",");
                if (parts.length != 23) return false;
                MacroConfig.themePanelBg            = (int) Long.parseLong(parts[0], 16);
                MacroConfig.themePanelHeader        = (int) Long.parseLong(parts[1], 16);
                MacroConfig.themeAccent             = (int) Long.parseLong(parts[2], 16);
                MacroConfig.themeText               = (int) Long.parseLong(parts[3], 16);
                MacroConfig.themeTextDim            = (int) Long.parseLong(parts[4], 16);
                MacroConfig.themeToggleOn           = (int) Long.parseLong(parts[5], 16);
                MacroConfig.themeToggleOff          = (int) Long.parseLong(parts[6], 16);
                MacroConfig.themeSliderFill         = (int) Long.parseLong(parts[7], 16);
                MacroConfig.themeButtonHover        = (int) Long.parseLong(parts[8], 16);
                MacroConfig.hudBgColor              = (int) Long.parseLong(parts[9], 16);
                MacroConfig.hudAccentColor          = (int) Long.parseLong(parts[10], 16);
                MacroConfig.hudTitleColor           = (int) Long.parseLong(parts[11], 16);
                MacroConfig.hudLabelColor           = (int) Long.parseLong(parts[12], 16);
                MacroConfig.hudValueColor           = (int) Long.parseLong(parts[13], 16);
                MacroConfig.hudBarBgColor           = (int) Long.parseLong(parts[14], 16);
                MacroConfig.hudBarFillColor         = (int) Long.parseLong(parts[15], 16);
                MacroConfig.hudStateOffColor        = (int) Long.parseLong(parts[16], 16);
                MacroConfig.hudStateFarmingColor    = (int) Long.parseLong(parts[17], 16);
                MacroConfig.hudStateCleaningColor   = (int) Long.parseLong(parts[18], 16);
                MacroConfig.hudStateRecoveringColor = (int) Long.parseLong(parts[19], 16);
                MacroConfig.hudStateVisitingColor   = (int) Long.parseLong(parts[20], 16);
                MacroConfig.hudStateAutosellingColor = (int) Long.parseLong(parts[21], 16);
                MacroConfig.hudStateSprayingColor   = (int) Long.parseLong(parts[22], 16);
            } else {
                // legacy — 9 colors only
                String[] parts = s.split(",");
                if (parts.length != 9) return false;
                MacroConfig.themePanelBg     = (int) Long.parseLong(parts[0], 16);
                MacroConfig.themePanelHeader = (int) Long.parseLong(parts[1], 16);
                MacroConfig.themeAccent      = (int) Long.parseLong(parts[2], 16);
                MacroConfig.themeText        = (int) Long.parseLong(parts[3], 16);
                MacroConfig.themeTextDim     = (int) Long.parseLong(parts[4], 16);
                MacroConfig.themeToggleOn    = (int) Long.parseLong(parts[5], 16);
                MacroConfig.themeToggleOff   = (int) Long.parseLong(parts[6], 16);
                MacroConfig.themeSliderFill  = (int) Long.parseLong(parts[7], 16);
                MacroConfig.themeButtonHover = (int) Long.parseLong(parts[8], 16);
            }
            MacroConfig.save();
            return true;
        } catch (Exception e) { return false; }
    }

    private static final int PANEL_W = 180;
    private static final int HEADER_H = 18;
    private static final int ENTRY_H = 18;
    private static final int ENTRY_PAD = 3;
    private static final int PANEL_RADIUS = 4;
    private static final int SEARCH_H = 16;

    private boolean shiftHeld = false;
    private String searchQuery = "";
    private boolean searchActive = false;

    private final List<Panel> panels = new ArrayList<>();
    private SubPanel activeSubPanel = null;
    private Panel draggingPanel = null;
    private int dragOffX, dragOffY;
    private boolean dragMovedPanel = false;
    private SliderEntry draggingSlider = null;
    private Panel draggingSliderPanel = null;
    private Panel scrollbarPanel = null;
    private int scrollbarDragStartY, scrollbarDragStartOffset;

    public ClickGui() {
        super(Component.literal("ihanuat"));
    }

    @Override
    protected void init() {
        panels.clear();
        buildPanels();
        ScreenMouseEvents.allowMouseClick(this).register((scr, event) -> {
            handleMouseClicked((int) event.x(), (int) event.y(), event.button());
            return true;
        });
        ScreenMouseEvents.allowMouseRelease(this).register((scr, event) -> {
            handleMouseReleased();
            return true;
        });
        ScreenMouseEvents.allowMouseDrag(this).register((scr, event, dx, dy) -> {
            handleMouseDragged((int) event.x(), (int) event.y());
            return true;
        });
        ScreenMouseEvents.allowMouseScroll(this).register((scr, mx, my, hScroll, vScroll) -> {
            handleMouseScrolled((int) mx, (int) my, hScroll, vScroll, shiftHeld);
            return true;
        });
        ScreenKeyboardEvents.allowKeyPress(this).register((scr, event) -> {
            if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) shiftHeld = true;
            handleKeyPressed(event.key(), event.scancode(), event.modifiers()); return true;
        });
        ScreenKeyboardEvents.allowKeyRelease(this).register((scr, event) -> {
            if (event.key() == 340 || event.key() == 344) shiftHeld = false;
            handleKeyReleased(event.key());
            return true;
        });
    }

    public boolean charTyped(char c, int mods) {
        handleCharTyped(c, mods);
        return true;
    }

    private void buildPanels() {
        int startX = 4, startY = 4;
        int[] idx = {0};
        Supplier<int[]> nextPos = () -> {
            int i = idx[0]++;
            return new int[]{startX, startY + i * (HEADER_H + 1)};
        };
        int[][] saved = MacroConfig.clickGuiPanelPositions;
        panels.add(generalPanel(pos(saved, 0, nextPos)));
        panels.add(delaysPanel(pos(saved, 1, nextPos)));
        panels.add(wardrobePanel(pos(saved, 2, nextPos)));
        panels.add(autoRodPanel(pos(saved, 3, nextPos)));
        panels.add(equipmentPanel(pos(saved, 4, nextPos)));
        panels.add(autoPestPanel(pos(saved, 5, nextPos)));
        panels.add(autoVisitorPanel(pos(saved, 6, nextPos)));
        panels.add(autoGeorgePanel(pos(saved, 7, nextPos)));
        panels.add(autoSellPanel(pos(saved, 8, nextPos)));
        panels.add(profitPanel(pos(saved, 9, nextPos)));
        panels.add(dynamicRestPanel(pos(saved, 10, nextPos)));
        panels.add(qolPanel(pos(saved, 11, nextPos)));
        panels.add(themePanel(pos(saved, 12, nextPos)));
    }

    private static int[] pos(int[][] saved, int i, Supplier<int[]> fallback) {
        if (saved != null && i < saved.length && saved[i] != null && saved[i].length >= 2
                && (saved[i][0] != 0 || saved[i][1] != 0)) return saved[i];
        return fallback.get();
    }

    private static Panel makePanel(String title, int[] pos) {
        Panel p = new Panel(title, pos[0], pos[1]);
        if (pos.length >= 3) p.collapsed = pos[2] == 1;
        return p;
    }


    private Panel generalPanel(int[] pos) {
        Panel p = makePanel("General", pos);
        p.add(toggle("Show Macro HUD", () -> MacroConfig.showHud, v -> {
            MacroConfig.showHud = v;
            save();
        }));
        p.add(toggle("GUI Only in Garden", () -> MacroConfig.guiOnlyInGarden, v -> {
            MacroConfig.guiOnlyInGarden = v;
            save();
        }));
        p.add(toggle("Enable PlotTP Rewarp", () -> MacroConfig.enablePlotTpRewarp, v -> {
            MacroConfig.enablePlotTpRewarp = v;
            save();
        }));
        p.add(toggle("Hold W Until Wall", () -> MacroConfig.holdWUntilWall, v -> {
            MacroConfig.holdWUntilWall = v;
            save();
        }));
        p.add(cycleEnum("Unfly Mode", MacroConfig.UnflyMode.values(), () -> MacroConfig.unflyMode, v -> {
            MacroConfig.unflyMode = v;
            save();
        }));
        p.add(new ScriptSelectorEntry());
        p.add(textSetting("PlotTP Number", "plotTpNumber", () -> MacroConfig.plotTpNumber, v -> {
            MacroConfig.plotTpNumber = v;
            save();
        }));
        p.add(button("Capture Rewarp Pos", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                MacroConfig.rewarpEndX = mc.player.getX();
                MacroConfig.rewarpEndY = mc.player.getY();
                MacroConfig.rewarpEndZ = mc.player.getZ();
                MacroConfig.rewarpEndPosSet = true;
                save();
                mc.player.displayClientMessage(Component.literal("Rewarp position captured!"), true);
            }
        }));
        p.add(toggle("Auto-Resume After Rest", () -> MacroConfig.autoResumeAfterDynamicRest, v -> {
            MacroConfig.autoResumeAfterDynamicRest = v;
            save();
        }));
        p.add(toggle("Auto-Recover Disconnect", () -> MacroConfig.autoRecoverUnexpectedDisconnect, v -> {
            MacroConfig.autoRecoverUnexpectedDisconnect = v;
            save();
        }));
        p.add(toggle("Persist Session Timer", () -> MacroConfig.persistSessionTimer, v -> {
            MacroConfig.persistSessionTimer = v;
            save();
        }));
        return p;
    }

    private Panel delaysPanel(int[] pos) {
        Panel p = makePanel("Delays", pos);
        p.add(slider("Rand Delay", "additionalRandomDelay", 0, 1000, () -> MacroConfig.additionalRandomDelay, v -> {
            MacroConfig.additionalRandomDelay = v;
            save();
        }, "ms"));
        p.add(slider("Rotation", "rotationTime", 100, 3000, () -> MacroConfig.rotationTime, v -> {
            MacroConfig.rotationTime = v;
            save();
        }, "ms"));
        p.add(slider("GUI Click", "guiClickDelay", 100, 2000, () -> MacroConfig.guiClickDelay, v -> {
            MacroConfig.guiClickDelay = v;
            save();
        }, "ms"));
        p.add(slider("Equip Swap", "equipmentSwapDelay", 100, 300, () -> MacroConfig.equipmentSwapDelay, v -> {
            MacroConfig.equipmentSwapDelay = v;
            save();
        }, "ms"));
        p.add(slider("Rod Swap", "rodSwapDelay", 50, 1000, () -> MacroConfig.rodSwapDelay, v -> {
            MacroConfig.rodSwapDelay = v;
            save();
        }, "ms"));
        p.add(slider("Garden Warp", "gardenWarpDelay", 0, 3000, () -> MacroConfig.gardenWarpDelay, v -> {
            MacroConfig.gardenWarpDelay = v;
            save();
        }, "ms"));
        p.add(slider("Pest Chat", "pestChatTriggerDelay", 0, 3000, () -> MacroConfig.pestChatTriggerDelay, v -> {
            MacroConfig.pestChatTriggerDelay = v;
            save();
        }, "ms"));
        p.add(slider("Book Combine", "bookCombineDelay", 100, 2000, () -> MacroConfig.bookCombineDelay, v -> {
            MacroConfig.bookCombineDelay = v;
            save();
        }, "ms"));
        p.add(slider("Junk Drop", "junkItemDropDelay", 0, 1000, () -> MacroConfig.junkItemDropDelay, v -> {
            MacroConfig.junkItemDropDelay = v;
            save();
        }, "ms"));
        p.add(slider("Autosell Click", "autosellClickDelay", 100, 2000, () -> MacroConfig.autosellClickDelay, v -> {
            MacroConfig.autosellClickDelay = v;
            save();
        }, "ms"));
        return p;
    }

    private Panel wardrobePanel(int[] pos) {
        Panel p = makePanel("Wardrobe Swap", pos);
        p.add(toggle("Auto Wardrobe (Pest)", () -> MacroConfig.autoWardrobePest, v -> {
            MacroConfig.autoWardrobePest = v;
            save();
        }));
        p.add(toggle("Auto Wardrobe (Visitor)", () -> MacroConfig.autoWardrobeVisitor, v -> {
            MacroConfig.autoWardrobeVisitor = v;
            save();
        }));
        p.add(toggle("Armor Swap (Visitor)", () -> MacroConfig.armorSwapVisitor, v -> {
            MacroConfig.armorSwapVisitor = v;
            save();
        }));
        p.add(slider("Farming Slot", "wardrobeSlotFarming", 1, 9, () -> MacroConfig.wardrobeSlotFarming, v -> {
            MacroConfig.wardrobeSlotFarming = v;
            save();
        }, ""));
        p.add(slider("Pest Slot", "wardrobeSlotPest", 1, 9, () -> MacroConfig.wardrobeSlotPest, v -> {
            MacroConfig.wardrobeSlotPest = v;
            save();
        }, ""));
        p.add(slider("Visitor Slot", "wardrobeSlotVisitor", 1, 9, () -> MacroConfig.wardrobeSlotVisitor, v -> {
            MacroConfig.wardrobeSlotVisitor = v;
            save();
        }, ""));
        return p;
    }

    private Panel autoRodPanel(int[] pos) {
        Panel p = makePanel("Auto Rod", pos);
        p.add(toggle("Rod on Pest CD", () -> MacroConfig.autoRodPestCd, v -> {
            MacroConfig.autoRodPestCd = v;
            save();
        }));
        p.add(toggle("Rod on Pest Spawn", () -> MacroConfig.autoRodPestSpawn, v -> {
            MacroConfig.autoRodPestSpawn = v;
            save();
        }));
        p.add(toggle("Rod on Return to Farm", () -> MacroConfig.autoRodReturnToFarm, v -> {
            MacroConfig.autoRodReturnToFarm = v;
            save();
        }));
        return p;
    }

    private Panel equipmentPanel(int[] pos) {
        Panel p = makePanel("Equipment Swap", pos);
        p.add(toggle("Auto-Equipment", () -> MacroConfig.autoEquipment, v -> {
            MacroConfig.autoEquipment = v;
            save();
        }));
        return p;
    }

    private Panel autoPestPanel(int[] pos) {
        Panel p = makePanel("Auto Pest", pos);
        p.add(slider("Threshold", "pestThreshold", 1, 8, () -> MacroConfig.pestThreshold, v -> {
            MacroConfig.pestThreshold = v;
            save();
        }, ""));
        p.add(toggle("Trigger on Chat", () -> MacroConfig.triggerPestOnChat, v -> {
            MacroConfig.triggerPestOnChat = v;
            save();
        }));
        p.add(toggle("Delay Crop Fever", () -> MacroConfig.delayPestForCropFever, v -> {
            MacroConfig.delayPestForCropFever = v;
            save();
        }));
        p.add(toggle("AOTV to Roof", () -> MacroConfig.aotvToRoof, v -> {
            MacroConfig.aotvToRoof = v;
            save();
        }));
        p.add(csvTextSetting("AOTV Roof Plots", "aotvRoofPlots",
                () -> String.join(", ", MacroConfig.aotvRoofPlots),
                v -> {
                    List<String> plots = new ArrayList<>();
                    for (String part : v.split(",")) {
                        String trimmed = part.trim();
                        if (!trimmed.isEmpty()) plots.add(trimmed);
                    }
                    MacroConfig.aotvRoofPlots = plots;
                    save();
                },
                "Comma separated, e.g. 1, 2, 3"));
        p.add(toggle("Break Before AOTV", () -> MacroConfig.breakBlocksBeforeAotv, v -> {
            MacroConfig.breakBlocksBeforeAotv = v;
            save();
        }));
        p.add(slider("Roof Pitch", "aotvRoofPitch", 45, 90, () -> MacroConfig.aotvRoofPitch, v -> {
            MacroConfig.aotvRoofPitch = v;
            save();
        }, ""));
        p.add(slider("Pitch Human.", "aotvRoofPitchHumanization", 0, 15, () -> MacroConfig.aotvRoofPitchHumanization, v -> {
            MacroConfig.aotvRoofPitchHumanization = v;
            save();
        }, ""));
        return p;
    }

    private Panel autoVisitorPanel(int[] pos) {
        Panel p = makePanel("Auto Visitor", pos);
        p.add(toggle("Auto-Visitor", () -> MacroConfig.autoVisitor, v -> {
            MacroConfig.autoVisitor = v;
            save();
        }));
        p.add(slider("Threshold", "visitorThreshold", 1, 5, () -> MacroConfig.visitorThreshold, v -> {
            MacroConfig.visitorThreshold = v;
            save();
        }, ""));
        return p;
    }

    private Panel autoGeorgePanel(int[] pos) {
        Panel p = makePanel("Auto George", pos);
        p.add(toggle("Auto George Sell", () -> MacroConfig.autoGeorgeSell, v -> {
            MacroConfig.autoGeorgeSell = v;
            save();
        }));
        p.add(slider("Threshold", "georgeSellThreshold", 1, 35, () -> MacroConfig.georgeSellThreshold, v -> {
            MacroConfig.georgeSellThreshold = v;
            save();
        }, ""));
        return p;
    }

    private Panel autoSellPanel(int[] pos) {
        Panel p = makePanel("Auto Sell", pos);
        p.add(toggle("Custom Autosell", () -> MacroConfig.autoBoosterCookie, v -> {
            MacroConfig.autoBoosterCookie = v;
            save();
        }));
        p.add(listSetting("Autosell Item List", "boosterCookieItems", () -> MacroConfig.boosterCookieItems,
                v -> {
                    MacroConfig.boosterCookieItems = new ArrayList<>(v);
                    save();
                }));
        return p;
    }

    private Panel profitPanel(int[] pos) {
        Panel p = makePanel("Profit Calculator", pos);
        p.add(toggle("Session HUD", () -> MacroConfig.showSessionProfitHud, v -> {
            MacroConfig.showSessionProfitHud = v;
            save();
        }));
        p.add(toggle("Daily HUD", () -> MacroConfig.showTotalToday, v -> {
            MacroConfig.showTotalToday = v;
            save();
        }));
        p.add(toggle("Lifetime HUD", () -> MacroConfig.showLifetimeHud, v -> {
            MacroConfig.showLifetimeHud = v;
            save();
        }));
        p.add(toggle("HUD While Off", () -> MacroConfig.showProfitHudWhileInactive, v -> {
            MacroConfig.showProfitHudWhileInactive = v;
            save();
        }));
        p.add(toggle("Compact", () -> MacroConfig.compactProfitCalculator, v -> {
            MacroConfig.compactProfitCalculator = v;
            save();
        }));
        p.add(listSetting("Pet XP List", "petXpTrackedPets", () -> MacroConfig.petXpTrackedPets,
                v -> {
                    MacroConfig.petXpTrackedPets = new ArrayList<>(v);
                    ProfitManager.refreshConfiguredPetXpPrices();
                    save();
                },
                "Pet Name, Max Level (100/200), Level 1 Price,",
                "Max Level Price, Rarity" ));
        p.add(button("Reset Session", () -> {
            MacroStateManager.resetSession();
            notifyMsg("Session reset!");
        }));
        p.add(button("Reset Daily", () -> {
            ProfitManager.resetDaily();
            notifyMsg("Daily reset!");
        }));
        p.add(button("Reset Lifetime", () -> {
            ProfitManager.resetLifetime();
            notifyMsg("Lifetime reset!");
        }));
        return p;
    }

    private Panel dynamicRestPanel(int[] pos) {
        Panel p = makePanel("Dynamic Rest", pos);
        p.add(intField("Script Time", "restScriptingTime", () -> MacroConfig.restScriptingTime, v -> {
            MacroConfig.restScriptingTime = v;
            save();
        }, "min"));
        p.add(intField("Script Offset", "restScriptingTimeOffset", () -> MacroConfig.restScriptingTimeOffset, v -> {
            MacroConfig.restScriptingTimeOffset = v;
            save();
        }, "min"));
        p.add(intField("Break Time", "restBreakTime", () -> MacroConfig.restBreakTime, v -> {
            MacroConfig.restBreakTime = v;
            save();
        }, "min"));
        p.add(intField("Break Offset", "restBreakTimeOffset", () -> MacroConfig.restBreakTimeOffset, v -> {
            MacroConfig.restBreakTimeOffset = v;
            save();
        }, "min"));
        p.add(toggle("Show Daily Total", () -> MacroConfig.showTotalToday, v -> {
            MacroConfig.showTotalToday = v;
            save();
        }));
        p.add(doubleField("Quit Threshold", "quitThresholdHours", () -> MacroConfig.quitThresholdHours, v -> {
            MacroConfig.quitThresholdHours = Math.max(0.0, v);
            save();
        }, "hr"));
        return p;
    }

    private Panel qolPanel(int[] pos) {
        Panel p = makePanel("QOL", pos);
        p.add(toggle("Book Combine", () -> MacroConfig.autoBookCombine, v -> {
            MacroConfig.autoBookCombine = v;
            save();
        }));
        p.add(toggle("Always Combine", () -> MacroConfig.alwaysActiveCombine, v -> {
            MacroConfig.alwaysActiveCombine = v;
            save();
        }));
        p.add(slider("Book Threshold", "bookThreshold", 1, 35, () -> MacroConfig.bookThreshold, v -> {
            MacroConfig.bookThreshold = v;
            save();
        }, ""));
        p.add(toggle("Chat Cleanup", () -> MacroConfig.hideFilteredChat, v -> {
            MacroConfig.hideFilteredChat = v;
            save();
        }));
        p.add(toggle("Auto-Drop Junk", () -> MacroConfig.autoDropJunk, v -> {
            MacroConfig.autoDropJunk = v;
            save();
        }));
        p.add(listSetting("Junk List", "junkItems", () -> MacroConfig.junkItems,
                v -> {
                    MacroConfig.junkItems = new ArrayList<>(v);
                    save();
                }));
        p.add(slider("Junk Threshold", "junkThreshold", 1, 35, () -> MacroConfig.junkThreshold, v -> {
            MacroConfig.junkThreshold = v;
            save();
        }, ""));
        p.add(textSetting("Junk PlotTP", "dropJunkPlotTp", () -> MacroConfig.dropJunkPlotTp, v -> {
            MacroConfig.dropJunkPlotTp = v;
            save();
        }));
        p.add(toggle("Stash Manager", () -> MacroConfig.autoStashManager, v -> {
            MacroConfig.autoStashManager = v;
            save();
        }));
        p.add(toggle("Discord Status", () -> MacroConfig.sendDiscordStatus, v -> {
            MacroConfig.sendDiscordStatus = v;
            save();
        }));
        p.add(textSetting("Webhook URL", "discordWebhookUrl", () -> MacroConfig.discordWebhookUrl, v -> {
            MacroConfig.discordWebhookUrl = v;
            save();
        }));
        p.add(intField("Discord Interval", "discordStatusUpdateTime", () -> MacroConfig.discordStatusUpdateTime, v -> {
            MacroConfig.discordStatusUpdateTime = v;
            save();
        }, "min"));
        p.add(toggle("Debug Messages", () -> MacroConfig.showDebug, v -> {
            MacroConfig.showDebug = v;
            save();
        }));
        p.add(toggle("Log to File", () -> MacroConfig.logDebugToFile, v -> {
            MacroConfig.logDebugToFile = v;
            if (!v) com.ihanuat.mod.DebugLogger.getInstance().close();
            save();
        }));
        p.add(button("Open Log Folder", () -> {
            try {
                java.awt.Desktop.getDesktop().open(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toFile());
            } catch (Exception e) {
                notifyMsg("Failed: " + e.getMessage());
            }
        }));
        return p;
    }

    private Panel themePanel(int[] pos) {
        Panel p = makePanel("Theme", pos);
        // text style option — none, drop shadow, or outline
        p.add(cycleEnum("Text Style", MacroConfig.TextStyle.values(), () -> MacroConfig.themeTextStyle, v -> { MacroConfig.themeTextStyle = v; save(); }));
        // outline size only matters when style is OUTLINE
        p.add(slider("Outline Size", "themeOutlineSize",   1, 3, () -> MacroConfig.themeOutlineSize,   v -> { MacroConfig.themeOutlineSize = v;   save(); }, "px"));
        // shadow opacity applies to both SHADOW and OUTLINE modes
        p.add(slider("Shadow Opacity", "themeShadowOpacity", 0, 255, () -> MacroConfig.themeShadowOpacity, v -> { MacroConfig.themeShadowOpacity = v; save(); }, ""));
        p.add(colorEntry("Panel BG", () -> MacroConfig.themePanelBg, v -> {
            MacroConfig.themePanelBg = v;
            save();
        }));
        p.add(colorEntry("Panel Header", () -> MacroConfig.themePanelHeader, v -> {
            MacroConfig.themePanelHeader = v;
            save();
        }));
        p.add(colorEntry("Accent", () -> MacroConfig.themeAccent, v -> {
            MacroConfig.themeAccent = v;
            save();
        }));
        p.add(colorEntry("Text", () -> MacroConfig.themeText, v -> {
            MacroConfig.themeText = v;
            save();
        }));
        p.add(colorEntry("Text Dimmed", () -> MacroConfig.themeTextDim, v -> {
            MacroConfig.themeTextDim = v;
            save();
        }));
        p.add(colorEntry("Toggle ON", () -> MacroConfig.themeToggleOn, v -> {
            MacroConfig.themeToggleOn = v;
            save();
        }));
        p.add(colorEntry("Toggle OFF", () -> MacroConfig.themeToggleOff, v -> {
            MacroConfig.themeToggleOff = v;
            save();
        }));
        p.add(colorEntry("Slider Fill", () -> MacroConfig.themeSliderFill, v -> {
            MacroConfig.themeSliderFill = v;
            save();
        }));
        p.add(colorEntry("Button Hover", () -> MacroConfig.themeButtonHover, v -> {
            MacroConfig.themeButtonHover = v;
            save();
        }));
        p.add(colorEntry("HUD Background", () -> MacroConfig.toArgb(MacroConfig.hudBgColor), v -> {
            MacroConfig.hudBgColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD Accent", () -> MacroConfig.toArgb(MacroConfig.hudAccentColor), v -> {
            MacroConfig.hudAccentColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD Title", () -> MacroConfig.toArgb(MacroConfig.hudTitleColor), v -> {
            MacroConfig.hudTitleColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD Label", () -> MacroConfig.toArgb(MacroConfig.hudLabelColor), v -> {
            MacroConfig.hudLabelColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD Value", () -> MacroConfig.toArgb(MacroConfig.hudValueColor), v -> {
            MacroConfig.hudValueColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD Bar BG", () -> MacroConfig.toArgb(MacroConfig.hudBarBgColor), v -> {
            MacroConfig.hudBarBgColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD Bar Fill", () -> MacroConfig.toArgb(MacroConfig.hudBarFillColor), v -> {
            MacroConfig.hudBarFillColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD State Off", () -> MacroConfig.toArgb(MacroConfig.hudStateOffColor), v -> {
            MacroConfig.hudStateOffColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD State Farm", () -> MacroConfig.toArgb(MacroConfig.hudStateFarmingColor), v -> {
            MacroConfig.hudStateFarmingColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD State Clean", () -> MacroConfig.toArgb(MacroConfig.hudStateCleaningColor), v -> {
            MacroConfig.hudStateCleaningColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD State Rec", () -> MacroConfig.toArgb(MacroConfig.hudStateRecoveringColor), v -> {
            MacroConfig.hudStateRecoveringColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD State Visit", () -> MacroConfig.toArgb(MacroConfig.hudStateVisitingColor), v -> {
            MacroConfig.hudStateVisitingColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD State Sell", () -> MacroConfig.toArgb(MacroConfig.hudStateAutosellingColor), v -> {
            MacroConfig.hudStateAutosellingColor = v & 0xFFFFFF;
            save();
        }));
        p.add(colorEntry("HUD State Spray", () -> MacroConfig.toArgb(MacroConfig.hudStateSprayingColor), v -> {
            MacroConfig.hudStateSprayingColor = v & 0xFFFFFF;
            save();
        }));
        p.add(button("Copy Theme Code", () -> {
            String code = encodeTheme();
            Minecraft mc = Minecraft.getInstance();
            mc.keyboardHandler.setClipboard(code);
            notifyMsg("Theme code copied!");
        }));
        p.add(new ImportCodeEntry());
        p.add(button("Reset to Default", () -> {
            MacroConfig.themePanelBg = 0xF0101018;
            MacroConfig.themePanelHeader = 0xFF18182C;
            MacroConfig.themeAccent = 0xFF5050A0;
            MacroConfig.themeText = 0xFFCCCCCC;
            MacroConfig.themeTextDim = 0xFF666677;
            MacroConfig.themeToggleOn = 0xFF4444BB;
            MacroConfig.themeToggleOff = 0xFF2A2A3A;
            MacroConfig.themeSliderFill = 0xFF3A3A99;
            MacroConfig.themeButtonHover = 0xFF4444BB;
            MacroConfig.hudBgColor = MacroConfig.DEFAULT_HUD_BG_COLOR;
            MacroConfig.hudAccentColor = MacroConfig.DEFAULT_HUD_ACCENT_COLOR;
            MacroConfig.hudTitleColor = MacroConfig.DEFAULT_HUD_TITLE_COLOR;
            MacroConfig.hudLabelColor = MacroConfig.DEFAULT_HUD_LABEL_COLOR;
            MacroConfig.hudValueColor = MacroConfig.DEFAULT_HUD_VALUE_COLOR;
            MacroConfig.hudBarBgColor = MacroConfig.DEFAULT_HUD_BAR_BG_COLOR;
            MacroConfig.hudBarFillColor = MacroConfig.DEFAULT_HUD_BAR_FILL_COLOR;
            MacroConfig.hudStateOffColor = MacroConfig.DEFAULT_HUD_STATE_OFF_COLOR;
            MacroConfig.hudStateFarmingColor = MacroConfig.DEFAULT_HUD_STATE_FARMING_COLOR;
            MacroConfig.hudStateCleaningColor = MacroConfig.DEFAULT_HUD_STATE_CLEANING_COLOR;
            MacroConfig.hudStateRecoveringColor = MacroConfig.DEFAULT_HUD_STATE_RECOVERING_COLOR;
            MacroConfig.hudStateVisitingColor = MacroConfig.DEFAULT_HUD_STATE_VISITING_COLOR;
            MacroConfig.hudStateAutosellingColor = MacroConfig.DEFAULT_HUD_STATE_AUTOSELLING_COLOR;
            MacroConfig.hudStateSprayingColor = MacroConfig.DEFAULT_HUD_STATE_SPRAYING_COLOR;
            save();
            notifyMsg("Theme reset!");
        }));
        p.add(button("Reset Panel Positions", () -> {
            MacroConfig.clickGuiPanelPositions = new int[13][3];
            save();
            panels.clear();
            buildPanels();
            notifyMsg("Panel positions reset!");
        }));
        return p;
    }


    private static ToggleEntry toggle(String l, Supplier<Boolean> g, Consumer<Boolean> s) {
        return new ToggleEntry(l, g, s);
    }

    private static SliderEntry slider(String l, String field, int mn, int mx, Supplier<Integer> g, Consumer<Integer> s, String u) {
        return new SliderEntry(l, field, mn, mx, g, s, u);
    }

    private static <E extends Enum<E>> CycleEnumEntry<E> cycleEnum(String l, E[] vs, Supplier<E> g, Consumer<E> s) {
        return new CycleEnumEntry<>(l, vs, g, s);
    }

    private static TextSettingEntry textSetting(String l, String field, Supplier<String> g, Consumer<String> s) {
        return new TextSettingEntry(l, field, g, s);
    }

    private static TextSettingEntry csvTextSetting(String l, String field, Supplier<String> g, Consumer<String> s, String placeholder) {
        return new TextSettingEntry(l, field, g, s, placeholder);
    }

    private static ListSettingEntry listSetting(String l, String field, Supplier<List<String>> g, Consumer<List<String>> s) {
        return new ListSettingEntry(l, field, g, s);
    }

    private static ListSettingEntry listSetting(String l, String field, Supplier<List<String>> g, Consumer<List<String>> s, String... hints) {
        return new ListSettingEntry(l, field, g, s, hints);
    }

    private static IntFieldEntry intField(String l, String field, Supplier<Integer> g, Consumer<Integer> s, String u) {
        return new IntFieldEntry(l, field, g, s, u);
    }

    private static DoubleFieldEntry doubleField(String l, String field, Supplier<Double> g, Consumer<Double> s, String u) {
        return new DoubleFieldEntry(l, field, g, s, u);
    }

    private static ButtonEntry button(String l, Runnable a) {
        return new ButtonEntry(l, a);
    }

    private static ColorEntry colorEntry(String l, Supplier<Integer> g, Consumer<Integer> s) {
        return new ColorEntry(l, g, s);
    }


    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x99000000);
        int searchW = 160;
        int searchX = width / 2 - searchW / 2;
        int searchY = 4;
        fillRoundRect(g, searchX - 1, searchY - 1, searchW + 2, SEARCH_H + 2, 4, searchActive ? C_ACC() : C_OFF());
        fillRoundRect(g, searchX, searchY, searchW, SEARCH_H, 3, C_BG());
        String searchText = searchQuery.isEmpty() && !searchActive
                ? "Search modules..."
                : searchQuery + (searchActive && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "");
        g.drawString(font, searchText, searchX + 5, searchY + 4, searchQuery.isEmpty() && !searchActive ? C_DIM() : C_TXT(), false);
        for (int i = panels.size() - 1; i >= 0; i--) panels.get(i).render(g, mx, my, font, searchQuery);
        if (activeSubPanel != null) activeSubPanel.render(g, mx, my, font);
        g.drawString(font, "ihanuat  shift+scroll=pan", 3, height - 9, 0xFF333355, false);
    }


    void handleMouseClicked(int x, int y, int btn) {
        if (activeSubPanel != null) {
            if (activeSubPanel.contains(x, y)) {
                activeSubPanel.mouseClicked(x, y, btn, font);
                return;
            } else {
                activeSubPanel.commit();
                activeSubPanel = null;
                save();
                return;
            }
        }
        int searchW = 160;
        int searchX = width / 2 - searchW / 2;
        int searchY = 4;
        searchActive = x >= searchX && x <= searchX + searchW && y >= searchY && y <= searchY + SEARCH_H;
        if (searchActive) return;
        for (Panel panel : new ArrayList<>(panels)) {
            if (panel.headerContains(x, y)) {
                if (btn == 0) {
                    draggingPanel = panel;
                    dragOffX = x - panel.x;
                    dragOffY = y - panel.y;
                    dragMovedPanel = false;
                } else if (btn == 1) {
                    panel.collapsed = !panel.collapsed;
                    savePanelPositions();
                }
                panels.remove(panel);
                panels.add(0, panel);
                return;
            }
            if (panel.contains(x, y, searchQuery)) {
                panels.remove(panel);
                panels.add(0, panel);
                if (btn == 0 && panel.scrollbarContains(x, y, searchQuery)) {
                    scrollbarPanel = panel;
                    scrollbarDragStartY = y;
                    scrollbarDragStartOffset = panel.scrollOffset;
                    return;
                }
                Entry hit = panel.entryAt(x, y, searchQuery);
                if (hit != null) {
                    if (btn == 0) {
                        if (hit instanceof SliderEntry se && !se.valueContains(x, y, panel.x + ENTRY_PAD, panel.entryY(hit, searchQuery), PANEL_W - ENTRY_PAD * 2, ENTRY_H, font)) {
                            draggingSlider = se;
                            draggingSliderPanel = panel;
                            se.onDrag(x, panel.x + ENTRY_PAD + 2, PANEL_W - ENTRY_PAD * 2 - 4);
                        } else {
                            SubPanel sp = hit.openSubPanel(x, y, width, height);
                            if (sp != null) activeSubPanel = sp;
                            else hit.onClick(x, y);
                        }
                    } else if (btn == 1) {
                        SubPanel sp = hit.openSubPanel(x, y, width, height);
                        if (sp != null) activeSubPanel = sp;
                    }
                }
                return;
            }
        }
    }

    void handleMouseReleased() {
        if (activeSubPanel instanceof ColorSubPanel cs) {
            cs.draggingSlider = -1;
        }
        if (draggingPanel != null || scrollbarPanel != null) savePanelPositions();
        draggingPanel = null;
        draggingSlider = null;
        draggingSliderPanel = null;
        scrollbarPanel = null;
    }

    void handleMouseDragged(int x, int y) {
        if (activeSubPanel instanceof ColorSubPanel cs) {
            cs.drag(x);
            return;
        }
        if (draggingPanel != null) {
            draggingPanel.x = Math.max(0, Math.min(width - PANEL_W, x - dragOffX));
            draggingPanel.y = Math.max(0, Math.min(height - HEADER_H, y - dragOffY));
            dragMovedPanel = true;
        } else if (scrollbarPanel != null) {
            scrollbarPanel.dragScrollbar(y, scrollbarDragStartY, scrollbarDragStartOffset, searchQuery);
        } else if (draggingSlider != null && draggingSliderPanel != null) {
            draggingSlider.onDrag(x, draggingSliderPanel.x + ENTRY_PAD + 2, PANEL_W - ENTRY_PAD * 2 - 4);
        }
    }

    void handleMouseScrolled(int x, int y, double hScroll, double vScroll, boolean shift) {
        if (hScroll != 0 || shift) {
            int pan = (int) ((hScroll != 0 ? hScroll : vScroll) * 20);
            for (Panel p : panels) p.x += pan;
            return;
        }
        if (activeSubPanel != null && activeSubPanel.contains(x, y)) {
            activeSubPanel.scroll((int) -vScroll);
            return;
        }
        for (Panel panel : panels) {
            if (panel.contains(x, y, searchQuery)) {
                panel.scroll((int) -vScroll, searchQuery);
                return;
            }
        }
    }

    void handleKeyPressed(int key, int scan, int mods) {
        if (activeSubPanel != null) {
            if (activeSubPanel.keyPressed(key, scan, mods)) return;
            if (key == 256) {
                activeSubPanel.commit();
                activeSubPanel = null;
                save();
                return;
            }
        }
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 && key == GLFW.GLFW_KEY_F) {
            searchActive = true;
            return;
        }
        if (searchActive) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                return;
            }
            if (key == GLFW.GLFW_KEY_V && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null) searchQuery += clip;
                return;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (!searchQuery.isEmpty()) searchQuery = "";
                else searchActive = false;
                return;
            }
            if (key == 32) {
                searchQuery += ' ';
                return;
            }
            if ((mods & GLFW.GLFW_MOD_CONTROL) == 0) {
                String name = GLFW.glfwGetKeyName(key, scan);
                if (name != null && name.length() == 1) {
                    char c = name.charAt(0);
                    if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) c = Character.toUpperCase(c);
                    searchQuery += c;
                    return;
                }
            }
            return;
        }
        if (key == 256) onClose();
    }

    void handleKeyReleased(int key) {
        if (key == 340 || key == 344) shiftHeld = false;
    }

    void handleCharTyped(char c, int mods) {
        if (activeSubPanel != null) {
            activeSubPanel.charTyped(c, mods);
            return;
        }
        if (searchActive && !Character.isISOControl(c)) searchQuery += c;
    }

    @Override
    public void onClose() {
        savePanelPositions();
        save();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static void save() {
        MacroConfig.save();
    }

    private void notifyMsg(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.literal(msg), true);
    }

    private void savePanelPositions() {
        String[] order = {"General", "Delays", "Wardrobe Swap", "Auto Rod", "Equipment Swap", "Auto Pest", "Auto Visitor", "Auto George", "Auto Sell", "Profit Calculator", "Dynamic Rest", "QOL", "Theme"};
        int[][] positions = new int[order.length][3];
        for (int i = 0; i < order.length; i++)
            for (Panel p : panels)
                if (p.title.equals(order[i])) {
                    positions[i] = new int[]{p.x, p.y, p.collapsed ? 1 : 0};
                    break;
                }
        MacroConfig.clickGuiPanelPositions = positions;
        MacroConfig.save();
    }

    static void fillRoundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        for (int row = 0; row < h; row++) {
            int ind = 0;
            if (row < r) {
                double d = r - row - 0.5;
                ind = (int) Math.ceil(r - 0.5 - Math.sqrt(r * r - d * d));
            } else if (row >= h - r) {
                double d = row - (h - r) + 0.5;
                ind = (int) Math.ceil(r - 0.5 - Math.sqrt(r * r - d * d));
            }
            g.fill(x + ind, y + row, x + w - ind, y + row + 1, color);
        }
    }


    static class Panel {
        String title;
        int x, y;
        boolean collapsed = true;
        int scrollOffset = 0;
        final List<Entry> entries = new ArrayList<>();

        Panel(String title, int x, int y) {
            this.title = title;
            this.x = x;
            this.y = y;
        }

        void add(Entry e) {
            entries.add(e);
        }

        List<Entry> filtered(String q) {
            if (q == null || q.isBlank()) return entries;
            String query = q.toLowerCase();
            if (title.toLowerCase().contains(query)) return entries;
            return entries.stream().filter(e -> entryLabel(e).toLowerCase().contains(query)).collect(Collectors.toList());
        }

        String entryLabel(Entry e) {
            if (e instanceof ToggleEntry te) return te.label;
            if (e instanceof SliderEntry se) return se.label;
            if (e instanceof CycleEnumEntry<?> ce) return ce.label;
            if (e instanceof TextSettingEntry te) return te.label;
            if (e instanceof ListSettingEntry le) return le.label;
            if (e instanceof IntFieldEntry ie) return ie.label;
            if (e instanceof DoubleFieldEntry de) return de.label;
            if (e instanceof ButtonEntry be) return be.label;
            if (e instanceof ScriptSelectorEntry) return "Farm Script";
            if (e instanceof ColorEntry ce) return ce.label;
            if (e instanceof ImportCodeEntry) return "Paste Theme Code";
            return "";
        }

        int contentHeight(String q) {
            return filtered(q).size() * (ENTRY_H + ENTRY_PAD) + ENTRY_PAD;
        }

        int visibleHeight() {
            return 126;
        }

        int maxScroll(String q) {
            return Math.max(0, contentHeight(q) - visibleHeight());
        }

        boolean headerContains(int mx, int my) {
            return mx >= x && mx <= x + PANEL_W && my >= y && my <= y + HEADER_H;
        }

        boolean contains(int mx, int my, String q) {
            if (collapsed || filtered(q).isEmpty()) return false;
            return mx >= x && mx <= x + PANEL_W && my >= y + HEADER_H && my <= y + HEADER_H + Math.min(contentHeight(q), visibleHeight());
        }

        Entry entryAt(int mx, int my, String q) {
            if (collapsed || filtered(q).isEmpty() || mx > x + PANEL_W - 4) return null;
            int ey = y + HEADER_H + ENTRY_PAD - scrollOffset;
            for (Entry e : filtered(q)) {
                if (my >= ey && my < ey + ENTRY_H && mx >= x && mx <= x + PANEL_W) return e;
                ey += ENTRY_H + ENTRY_PAD;
            }
            return null;
        }

        int entryY(Entry target, String q) {
            int ey = y + HEADER_H + ENTRY_PAD - scrollOffset;
            for (Entry e : filtered(q)) {
                if (e == target) return ey;
                ey += ENTRY_H + ENTRY_PAD;
            }
            return y + HEADER_H + ENTRY_PAD;
        }

        boolean scrollbarContains(int mx, int my, String q) {
            if (contentHeight(q) <= visibleHeight()) return false;
            int clipY = y + HEADER_H;
            int clipH = Math.min(contentHeight(q), visibleHeight());
            return mx >= x + PANEL_W - 4 && mx <= x + PANEL_W && my >= clipY && my <= clipY + clipH;
        }

        void dragScrollbar(int mouseY, int startMouseY, int startOffset, String q) {
            int content = contentHeight(q);
            int clipH = Math.min(content, visibleHeight());
            int thumbH = Math.max(10, clipH * clipH / content);
            int travel = Math.max(1, clipH - thumbH);
            int max = Math.max(1, maxScroll(q));
            int delta = mouseY - startMouseY;
            scrollOffset = Math.max(0, Math.min(maxScroll(q), startOffset + Math.round(delta * (max / (float) travel))));
        }

        void scroll(int dir, String q) {
            scrollOffset = Math.max(0, Math.min(maxScroll(q), scrollOffset + dir * 10));
        }

        void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font, String q) {
            List<Entry> filtered = filtered(q);
            if (!q.isEmpty() && filtered.isEmpty() && !title.toLowerCase().contains(q.toLowerCase())) return;
            boolean effectiveCollapsed = collapsed || (!q.isEmpty() && filtered.isEmpty());
            int totalH = effectiveCollapsed ? HEADER_H : HEADER_H + Math.min(contentHeight(q), visibleHeight());
            fillRoundRect(g, x, y, PANEL_W, totalH, PANEL_RADIUS, C_BG());
            fillRoundRect(g, x, y, PANEL_W, HEADER_H, PANEL_RADIUS, C_HDR());
            if (!effectiveCollapsed) g.fill(x, y + HEADER_H - PANEL_RADIUS, x + PANEL_W, y + HEADER_H, C_HDR());
            g.fill(x + 2, y + HEADER_H - 1, x + PANEL_W - 2, y + HEADER_H, C_LINE());
            MacroConfig.drawStyledText(g, font, title, x + 5, y + HEADER_H / 2 - 4, !q.isEmpty() && title.toLowerCase().contains(q.toLowerCase()) ? C_ON2() : C_TXT());
            MacroConfig.drawStyledText(g, font, effectiveCollapsed ? ">" : "v", x + PANEL_W - 10, y + HEADER_H / 2 - 4, C_DIM());
            if (effectiveCollapsed) return;

            int clipY = y + HEADER_H, clipH = Math.min(contentHeight(q), visibleHeight());
            g.enableScissor(x, clipY, x + PANEL_W, clipY + clipH);
            int ey = clipY + ENTRY_PAD - scrollOffset;
            for (Entry e : filtered) {
                boolean hov = mx >= x && mx <= x + PANEL_W && my >= ey && my < ey + ENTRY_H;
                if (hov) g.fill(x + 1, ey, x + PANEL_W - 1, ey + ENTRY_H, C_HOVER());
                e.render(g, x + ENTRY_PAD, ey, PANEL_W - ENTRY_PAD * 2, ENTRY_H, hov, font);
                ey += ENTRY_H + ENTRY_PAD;
            }
            g.disableScissor();
            if (maxScroll(q) > 0) {
                float frac = (float) scrollOffset / maxScroll(q);
                int bh = Math.max(10, clipH * clipH / contentHeight(q));
                int by = clipY + (int) ((clipH - bh) * frac);
                g.fill(x + PANEL_W - 3, by, x + PANEL_W - 1, by + bh, C_ACC());
            }
        }
    }


    interface Entry {
        void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font);

        void onClick(int mx, int my);

        default SubPanel openSubPanel(int mx, int my, int sw, int sh) {
            return null;
        }
    }

    static class ToggleEntry implements Entry {
        final String label;
        final Supplier<Boolean> getter;
        final Consumer<Boolean> setter;

        ToggleEntry(String l, Supplier<Boolean> g, Consumer<Boolean> s) {
            label = l;
            getter = g;
            setter = s;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            boolean on = getter.get();
            int mid = y + h / 2;
            int dw = 12, dh = 7, dx = x + w - dw - 2, dy = mid - dh / 2;
            fillRoundRect(g, dx, dy, dw, dh, 3, on ? C_ON() : C_OFF());
            if (on) fillRoundRect(g, dx + 5, dy + 1, 5, 5, 2, C_ON2());
            MacroConfig.drawStyledText(g, font, label, x + 2, mid - 4, hov ? C_TXT() : C_DIM());
        }

        @Override
        public void onClick(int mx, int my) {
            setter.accept(!getter.get());
        }
    }

    static class SliderEntry implements Entry {
        final String label, fieldName;
        final int min, max;
        final Supplier<Integer> getter;
        final Consumer<Integer> setter;
        final String unit;

        SliderEntry(String l, String field, int mn, int mx, Supplier<Integer> g, Consumer<Integer> s, String u) {
            label = l; fieldName = field; min = mn; max = mx; getter = g; setter = s; unit = u;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            int val = getter.get();
            String vs = val + unit;
            int vw = font.width(vs);
            MacroConfig.drawStyledText(g, font, label, x + 2, y + 2, hov ? C_TXT() : C_DIM());
            MacroConfig.drawStyledText(g, font, vs, x + w - vw - 2, y + 2, C_ON2());
            int by = y + h - 5, bh = 3;
            fillRoundRect(g, x + 2, by, w - 4, bh, 1, C_SBGR());
            float frac = (float) (val - min) / (max - min);
            int fw = (int) (frac * (w - 4));
            if (fw > 0) fillRoundRect(g, x + 2, by, fw, bh, 1, C_SFILL());
            int kx = x + 2 + fw - 2;
            g.fill(Math.max(x + 2, kx), by - 1, Math.max(x + 2, kx) + 4, by + bh + 1, C_SKNOB());
        }

        boolean valueContains(int mx, int my, int x, int y, int w, int h, net.minecraft.client.gui.Font font) {
            String vs = getter.get() + unit;
            int vw = font.width(vs);
            return mx >= x + w - vw - 2 && mx <= x + w - 2 && my >= y && my <= y + h;
        }

        void onDrag(int mx, int barX, int barW) {
            float f = Math.max(0f, Math.min(1f, (float) (mx - barX) / barW));
            setter.accept(min + Math.round(f * (max - min)));
        }

        @Override
        public void onClick(int mx, int my) {
        }

        @Override
        public SubPanel openSubPanel(int mx, int my, int sw, int sh) {
            return new IntInputSubPanel(mx, my, sw, sh, label, getter.get(), MacroConfig.getDefaultInt(fieldName), min, max, v -> {
                setter.accept(v);
                save();
            });
        }
    }

    static class CycleEnumEntry<E extends Enum<E>> implements Entry {
        final String label;
        final E[] values;
        final Supplier<E> getter;
        final Consumer<E> setter;

        CycleEnumEntry(String l, E[] vs, Supplier<E> g, Consumer<E> s) {
            label = l;
            values = vs;
            getter = g;
            setter = s;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            String val = getter.get().name();
            int vw = font.width(val);
            int mid = y + h / 2 - 4;
            MacroConfig.drawStyledText(g, font, label, x + 2, mid, hov ? C_TXT() : C_DIM());
            MacroConfig.drawStyledText(g, font, val, x + w - vw - 2, mid, C_ON2());
        }

        @Override
        public void onClick(int mx, int my) {
            E cur = getter.get();
            setter.accept(values[(cur.ordinal() + 1) % values.length]);
        }
    }

    static class TextSettingEntry implements Entry {
        final String label, fieldName;
        final Supplier<String> getter;
        final Consumer<String> setter;
        final String placeholder;

        TextSettingEntry(String l, String field, Supplier<String> g, Consumer<String> s) {
            this(l, field, g, s, "");
        }

        TextSettingEntry(String l, String field, Supplier<String> g, Consumer<String> s, String p) {
            label = l; fieldName = field; getter = g; setter = s; placeholder = p;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            String val = getter.get();
            if (val.length() > 11) val = val.substring(0, 9) + "..";
            int vw = font.width(val);
            int mid = y + h / 2 - 4;
            MacroConfig.drawStyledText(g, font, label, x + 2, mid, hov ? C_TXT() : C_DIM());
            MacroConfig.drawStyledText(g, font, val, x + w - vw - 6, mid, C_DIM());
        }

        @Override
        public void onClick(int mx, int my) {
        }

        @Override
        public SubPanel openSubPanel(int mx, int my, int sw, int sh) {
            return new StringInputSubPanel(mx, my, sw, sh, label, getter.get(), MacroConfig.getDefaultString(fieldName), setter, placeholder);
        }
    }

    static class ListSettingEntry implements Entry {
        final String label, fieldName;
        final Supplier<List<String>> getter;
        final Consumer<List<String>> setter;
        final String[] hints;

        ListSettingEntry(String l, String field, Supplier<List<String>> g, Consumer<List<String>> s, String... hints) {
            label = l; fieldName = field;
            getter = g;
            setter = s;
            this.hints = hints == null ? new String[0] : hints;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            List<String> values = getter.get();
            String summary;
            if (values == null || values.isEmpty()) summary = "(empty)";
            else if (values.size() == 1) summary = values.get(0);
            else summary = values.get(0) + " +" + (values.size() - 1);
            if (summary.length() > 11) summary = summary.substring(0, 9) + "..";
            int vw = font.width(summary);
            int mid = y + h / 2 - 4;
            MacroConfig.drawStyledText(g, font, label, x + 2, mid, hov ? C_TXT() : C_DIM());
            MacroConfig.drawStyledText(g, font, summary, x + w - vw - 6, mid, C_DIM());
        }

        @Override
        public void onClick(int mx, int my) {
        }

        @Override
        public SubPanel openSubPanel(int mx, int my, int sw, int sh) {
            return new ListInputSubPanel(mx, my, sw, sh, label, MacroConfig.getDefaultList(fieldName), getter.get(), setter, hints);
        }
    }

    static class IntFieldEntry implements Entry {
        final String label, fieldName;
        final Supplier<Integer> getter;
        final Consumer<Integer> setter;
        final String unit;

        IntFieldEntry(String l, String field, Supplier<Integer> g, Consumer<Integer> s, String u) {
            label = l; fieldName = field; getter = g; setter = s; unit = u;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            String val = getter.get() + " " + unit;
            int vw = font.width(val);
            int mid = y + h / 2 - 4;
            MacroConfig.drawStyledText(g, font, label, x + 2, mid, hov ? C_TXT() : C_DIM());
            MacroConfig.drawStyledText(g, font, val, x + w - vw - 2, mid, C_ON2());
        }

        @Override
        public void onClick(int mx, int my) {
        }

        @Override
        public SubPanel openSubPanel(int mx, int my, int sw, int sh) {
            return new IntInputSubPanel(mx, my, sw, sh, label, getter.get(), MacroConfig.getDefaultInt(fieldName), Integer.MIN_VALUE, Integer.MAX_VALUE, v -> {
                setter.accept(v);
                save();
            });
        }
    }

    static class DoubleFieldEntry implements Entry {
        final String label, fieldName;
        final Supplier<Double> getter;
        final Consumer<Double> setter;
        final String unit;

        DoubleFieldEntry(String l, String field, Supplier<Double> g, Consumer<Double> s, String u) {
            label = l; fieldName = field; getter = g; setter = s; unit = u;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            String val = String.format("%.2f %s", getter.get(), unit);
            int vw = font.width(val);
            int mid = y + h / 2 - 4;
            MacroConfig.drawStyledText(g, font, label, x + 2, mid, hov ? C_TXT() : C_DIM());
            MacroConfig.drawStyledText(g, font, val, x + w - vw - 2, mid, C_ON2());
        }

        @Override
        public void onClick(int mx, int my) {
        }

        @Override
        public SubPanel openSubPanel(int mx, int my, int sw, int sh) {
            return new DoubleInputSubPanel(mx, my, sw, sh, label, getter.get(), MacroConfig.getDefaultDouble(fieldName), v -> {
                setter.accept(v);
                save();
            });
        }
    }

    static class ButtonEntry implements Entry {
        final String label;
        final Runnable action;

        ButtonEntry(String l, Runnable a) {
            label = l;
            action = a;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            fillRoundRect(g, x + 2, y + 2, w - 4, h - 4, 3, hov ? C_BTN() : C_OFF());
            int tw = font.width(label);
            MacroConfig.drawStyledText(g, font, label, x + (w - tw) / 2, y + h / 2 - 4, C_TXT());
        }

        @Override
        public void onClick(int mx, int my) {
            action.run();
        }
    }

    // color swatch entry — shows label + color preview, right-click opens RGB sliders
    static class ColorEntry implements Entry {
        final String label;
        final Supplier<Integer> getter;
        final Consumer<Integer> setter;

        ColorEntry(String l, Supplier<Integer> g, Consumer<Integer> s) {
            label = l;
            getter = g;
            setter = s;
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            int col = getter.get();
            int mid = y + h / 2 - 4;
            MacroConfig.drawStyledText(g, font, label, x + 2, mid, hov ? C_TXT() : C_DIM());
            // color swatch
            int sw = 16, sx = x + w - sw - 2, sy = y + 2;
            g.fill(sx - 1, sy - 1, sx + sw + 1, sy + h - 2, 0xFF555555);
            g.fill(sx, sy, sx + sw, sy + h - 4, col | 0xFF000000);
            // hex label
            String hex = String.format("#%06X", col & 0xFFFFFF);
            int hw = font.width(hex);
            MacroConfig.drawStyledText(g, font, hex, x + w - sw - hw - 6, mid, C_DIM());
        }

        @Override
        public void onClick(int mx, int my) {
        }

        @Override
        public SubPanel openSubPanel(int mx, int my, int sw, int sh) {
            return new ColorSubPanel(mx, my, sw, sh, label, getter.get(), setter);
        }
    }

    // "Paste Theme Code" entry — text field + apply button inline
    static class ImportCodeEntry implements Entry {
        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            fillRoundRect(g, x + 2, y + 2, w - 4, h - 4, 3, hov ? C_BTN() : C_OFF());
            String lbl = "Paste Theme Code";
            int tw = font.width(lbl);
            MacroConfig.drawStyledText(g, font, lbl, x + (w - tw) / 2, y + h / 2 - 4, C_TXT());
        }

        @Override
        public void onClick(int mx, int my) {
        }

        @Override
        public SubPanel openSubPanel(int mx, int my, int sw, int sh) {
            return new StringInputSubPanel(mx, my, sw, sh, "Paste Theme Code", "", "", v -> {
                if (applyThemeCode(v)) {
                    Minecraft.getInstance().player.displayClientMessage(Component.literal("Theme applied!"), true);
                } else {
                    Minecraft.getInstance().player.displayClientMessage(Component.literal("Invalid theme code!"), true);
                }
            });
        }
    }


    static class ScriptSelectorEntry implements Entry {
        static final String[][] SCRIPTS = {
                {"netherwart:1", "Wart/Crops - S-Shape"},
                {"netherwart:0", "Wart/Crops - Vertical"},
                {"sugarcane:classical", "Sugarcane/Flowers - Classical"},
                {"sugarcane:sshape", "Sugarcane/Flowers - S-Shape"},
                {"cocoa", "Cocoa"},
                {"cactus", "Cactus"},
                {"mushroom:0", "Mushroom - Classical"},
                {"mushroom:1", "Mushroom - Staircase"},
                {"pumpkin:1", "Pumpkin/Melon"},
                {"echo", "Echo"}
        };

        static String displayName() {
            for (String[] s : SCRIPTS)
                if (s[0].equals(MacroConfig.restartScript)) return s[1];
            return MacroConfig.restartScript;
        }

        static void cycle(int dir) {
            int cur = 0;
            for (int i = 0; i < SCRIPTS.length; i++)
                if (SCRIPTS[i][0].equals(MacroConfig.restartScript)) {
                    cur = i;
                    break;
                }
            int next = (cur + dir + SCRIPTS.length) % SCRIPTS.length;
            MacroConfig.restartScript = SCRIPTS[next][0];
            save();
        }

        @Override
        public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            MacroConfig.drawStyledText(g, font, "Farm Script", x + 2, y + h / 2 - 4, hov ? C_TXT() : C_DIM());
            String disp = displayName();
            int dw = font.width(disp);
            // shrink if too long
            while (dw > w - font.width("Farm Script") - 20 && disp.length() > 4) {
                disp = disp.substring(0, disp.length() - 1);
                dw = font.width(disp + "..");
            }
            if (!disp.equals(displayName())) disp += "..";
            MacroConfig.drawStyledText(g, font, disp, x + w - font.width(disp) - 2, y + h / 2 - 4, C_ON2());
        }

        @Override
        public void onClick(int mx, int my) {
            cycle(1);
        }

        @Override
        public SubPanel openSubPanel(int mx, int my, int sw, int sh) {
            return new ScriptPickerSubPanel(mx, my, sw, sh);
        }
    }

    static class ScriptPickerSubPanel implements SubPanel {
        final int x, y, w = 240;
        static final int ROW_H = 16, PAD = 3;

        int h() {
            return PAD + ScriptSelectorEntry.SCRIPTS.length * (ROW_H + PAD) + PAD + 14;
        }

        ScriptPickerSubPanel(int mx, int my, int sw, int sh) {
            this.x = Math.min(mx, sw - w - 4);
            this.y = Math.min(my, sh - h() - 4);
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            int h = h();
            fillRoundRect(g, x - 2, y - 2, w + 4, h + 4, 4, C_SPBD());
            fillRoundRect(g, x, y, w, h, 3, C_SPBG());
            MacroConfig.drawStyledText(g, font, "Farm Script", x + 4, y + 4, C_TXT());
            g.fill(x + 4, y + 14, x + w - 4, y + 15, C_ACC());
            int ey = y + 18;
            for (String[] s : ScriptSelectorEntry.SCRIPTS) {
                boolean selected = s[0].equals(MacroConfig.restartScript);
                boolean hov = mx >= x + 4 && mx <= x + w - 4 && my >= ey && my <= ey + ROW_H;
                if (hov || selected) fillRoundRect(g, x + 4, ey, w - 8, ROW_H, 2, selected ? C_ON() : C_HOVER());
                MacroConfig.drawStyledText(g, font, s[1], x + 8, ey + ROW_H / 2 - 4, selected ? C_TXT() : C_DIM());
                ey += ROW_H + PAD;
            }
        }

        @Override
        public boolean contains(int mx, int my) {
            return mx >= x - 2 && mx <= x + w + 2 && my >= y - 2 && my <= y + h() + 2;
        }

        @Override
        public boolean mouseClicked(int mx, int my, int btn, net.minecraft.client.gui.Font font) {
            int ey = y + 18;
            for (String[] s : ScriptSelectorEntry.SCRIPTS) {
                if (my >= ey && my <= ey + ROW_H && mx >= x + 4 && mx <= x + w - 4) {
                    MacroConfig.restartScript = s[0];
                    save();
                    return true;
                }
                ey += ROW_H + ScriptPickerSubPanel.PAD;
            }
            return true;
        }

        @Override
        public void scroll(int dir) {
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            return false;
        }

        @Override
        public boolean charTyped(char c, int mods) {
            return false;
        }

        @Override
        public void commit() {
        }
    }

    interface SubPanel {
        void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font);

        boolean contains(int mx, int my);

        boolean mouseClicked(int mx, int my, int btn, net.minecraft.client.gui.Font font);

        boolean keyPressed(int key, int scan, int mods);

        boolean charTyped(char c, int mods);

        void scroll(int dir);

        void commit();
    }

    // color picker sub-panel with R/G/B/A sliders
    static class ColorSubPanel implements SubPanel {
        final String label;
        int r, g, b, a;
        final Consumer<Integer> setter;
        final int x, y, w = 260, h = 110;
        int draggingSlider = -1; // 0=R,1=G,2=B,3=A
        boolean editingHex = false;
        String hexBuffer = "";
        boolean cursorVisible = true;
        long lastBlink = System.currentTimeMillis();

        ColorSubPanel(int mx, int my, int sw, int sh, String label, int initial, Consumer<Integer> setter) {
            this.label = label;
            this.setter = setter;
            a = (initial >> 24) & 0xFF;
            r = (initial >> 16) & 0xFF;
            g = (initial >> 8) & 0xFF;
            b = initial & 0xFF;
            if (a == 0) a = 255;
            this.x = Math.min(mx, sw - w - 4);
            this.y = Math.min(my, sh - h - 4);
        }

        int packed() {
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        int getChannel(int i) {
            return i == 0 ? r : i == 1 ? g : i == 2 ? b : a;
        }

        void setChannel(int i, int v) {
            if (i == 0) r = v;
            else if (i == 1) g = v;
            else if (i == 2) b = v;
            else a = v;
        }

        int sliderBx() {
            return x + 16;
        }

        int sliderBw() {
            return w - 50;
        }

        int sliderSy(int i) {
            return y + 32 + i * 18;
        }

        void applyDrag(int mx) {
            if (draggingSlider < 0) return;
            int bx = sliderBx(), bw2 = sliderBw();
            int val = Math.max(0, Math.min(255, Math.round((mx - bx) / (float) bw2 * 255)));
            setChannel(draggingSlider, val);
            setter.accept(packed());
            save();
        }

        @Override
        public void render(GuiGraphics g2, int mx, int my, net.minecraft.client.gui.Font font) {
            fillRoundRect(g2, x - 2, y - 2, w + 4, h + 4, 4, C_SPBD());
            fillRoundRect(g2, x, y, w, h, 3, C_SPBG());

            // label
            g2.drawString(font, label, x + 4, y + 4, C_TXT(), false);

            // swatch
            int sw2 = 20;
            int sx = x + w - sw2 - 4, sy0 = y + 4;
            g2.fill(sx - 1, sy0 - 1, sx + sw2 + 1, sy0 + sw2 + 1, 0xFF555555);
            g2.fill(sx, sy0, sx + sw2, sy0 + sw2, packed());

            // hex field — clickable to edit
            if (System.currentTimeMillis() - lastBlink > 500) {
                cursorVisible = !cursorVisible;
                lastBlink = System.currentTimeMillis();
            }
            String hexDisplay = editingHex ? hexBuffer + (cursorVisible ? "|" : "") : String.format("#%08X", packed());
            int hexColor = editingHex ? C_TXT() : C_DIM();
            g2.fill(x + 4, y + 15, x + w - sw2 - 10, y + 27, C_SBGR());
            g2.fill(x + 4, y + 27, x + w - sw2 - 10, y + 28, editingHex ? C_ACC() : 0xFF333355);
            g2.drawString(font, hexDisplay, x + 6, y + 17, hexColor, false);
            // small hint
            if (!editingHex)
                g2.drawString(font, "click hex to edit", x + w - sw2 - 9 - font.width("click hex to edit"), y + 18, C_DIM(), false);

            // R/G/B/A sliders
            int[] cols = {0xFFCC4444, 0xFF44CC44, 0xFF4444CC, 0xFFAAAAAA};
            String[] names = {"R", "G", "B", "A"};
            int bx = sliderBx(), bw2 = sliderBw();
            for (int i = 0; i < 4; i++) {
                int val = getChannel(i);
                int sy = sliderSy(i);
                g2.drawString(font, names[i], x + 4, sy + 1, cols[i], false);
                fillRoundRect(g2, bx, sy + 3, bw2, 6, 2, C_SBGR());
                int fw = (int) (val / 255f * bw2);
                if (fw > 0) fillRoundRect(g2, bx, sy + 3, fw, 6, 2, cols[i]);
                boolean hov = draggingSlider == i || (mx >= bx && mx <= bx + bw2 && my >= sy && my <= sy + 10);
                int kx = bx + fw - 3;
                g2.fill(Math.max(bx, kx), sy + 1, Math.max(bx, kx) + 6, sy + 9, hov ? 0xFFFFFFFF : 0xFFCCCCCC);
                g2.drawString(font, String.valueOf(val), bx + bw2 + 4, sy + 1, cols[i], false);
            }
        }

        @Override
        public boolean contains(int mx, int my) {
            return mx >= x - 2 && mx <= x + w + 2 && my >= y - 2 && my <= y + h + 2;
        }

        @Override
        public boolean mouseClicked(int mx, int my, int btn, net.minecraft.client.gui.Font font) {
            // click on hex field
            int sw2 = 20;
            if (mx >= x + 4 && mx <= x + w - sw2 - 10 && my >= y + 15 && my <= y + 28) {
                editingHex = !editingHex;
                if (editingHex) hexBuffer = String.format("%08X", packed());
                return true;
            }
            editingHex = false;
            // click on slider
            int bx = sliderBx(), bw2 = sliderBw();
            for (int i = 0; i < 4; i++) {
                int sy = sliderSy(i);
                if (my >= sy && my <= sy + 10 && mx >= bx && mx <= bx + bw2) {
                    draggingSlider = i;
                    applyDrag(mx);
                    return true;
                }
            }
            return true;
        }

        // called from handleMouseDragged via activeSubPanel
        public void drag(int mx) {
            applyDrag(mx);
        }

        @Override
        public void scroll(int dir) {
        }

        @Override
        public boolean keyPressed(int key, int scan, int mods) {
            if (editingHex) {
                if (key == 259 && !hexBuffer.isEmpty()) {
                    hexBuffer = hexBuffer.substring(0, hexBuffer.length() - 1);
                    return true;
                }
                if (key == 257 || key == 335) {
                    applyHex();
                    editingHex = false;
                    return true;
                }
                if (key == 256) {
                    editingHex = false;
                    return true;
                }
                if (key == 86 && (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null) hexBuffer += clip.replaceAll("[^0-9a-fA-F#]", "");
                    return true;
                }
                // GLFW fallback for typing since charTyped may not fire
                Character c = fallbackCharFromKey(key, scan, mods);
                if (c != null && (Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                    if (hexBuffer.length() < 8) hexBuffer += c;
                    return true;
                }
                return true; // swallow all keys while editing hex
            }
            return false;
        }

        @Override
        public boolean charTyped(char c, int mods) {
            if (editingHex && (Character.isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                if (hexBuffer.length() < 8) hexBuffer += c;
                return true;
            }
            return false;
        }

        void applyHex() {
            try {
                String h = hexBuffer.replaceAll("#", "");
                long v = Long.parseLong(h, 16);
                if (h.length() <= 6) {
                    r = (int) ((v >> 16) & 0xFF);
                    g = (int) ((v >> 8) & 0xFF);
                    b = (int) (v & 0xFF);
                } else {
                    a = (int) ((v >> 24) & 0xFF);
                    r = (int) ((v >> 16) & 0xFF);
                    g = (int) ((v >> 8) & 0xFF);
                    b = (int) (v & 0xFF);
                }
                setter.accept(packed());
                save();
            } catch (Exception ignored) {
            }
        }

        @Override
        public void commit() {
            setter.accept(packed());
            save();
        }
    }

    static class StringInputSubPanel implements SubPanel {
        final String label;
        String value;
        final String defaultValue;
        final Consumer<String> setter;
        final String placeholder;
        int w = -1;
        final int x, y, h = 50, screenW;
        boolean cursorVisible = true;
        long lastBlink = System.currentTimeMillis();

        StringInputSubPanel(int mx, int my, int sw, int sh, String label, String initial, Consumer<String> setter) {
            this(mx, my, sw, sh, label, initial, initial, setter, "");
        }
        StringInputSubPanel(int mx, int my, int sw, int sh, String label, String initial, String defaultVal, Consumer<String> setter) {
            this(mx, my, sw, sh, label, initial, defaultVal, setter, "");
        }
        StringInputSubPanel(int mx, int my, int sw, int sh, String label, String initial, String defaultVal, Consumer<String> setter, String placeholder) {
            this.label = label; this.value = initial; this.defaultValue = defaultVal;
            this.setter = setter; this.placeholder = placeholder == null ? "" : placeholder;
            this.screenW = sw; this.x = mx; this.y = Math.min(my, sh - h - 4);
        }

        private void ensureWidth(net.minecraft.client.gui.Font font) {
            if (w < 0) w = Math.max(220, font.width(label) + 80);
        }
        private int rx() { return Math.min(x, screenW - w - 4); }
        private int resetBtnX(net.minecraft.client.gui.Font font) { return rx() + w - font.width("reset") - 8; }
        private boolean overReset(int mx, int my, net.minecraft.client.gui.Font font) {
            int bx = resetBtnX(font);
            return mx >= bx - 2 && mx <= bx + font.width("reset") + 4 && my >= y + 3 && my <= y + 14;
        }

        @Override public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            ensureWidth(font);
            int rx = rx();
            fillRoundRect(g, rx - 2, y - 2, w + 4, h + 4, 4, C_SPBD());
            fillRoundRect(g, rx, y, w, h, 3, C_SPBG());
            MacroConfig.drawStyledText(g, font, label, rx + 5, y + 6, C_TXT());
            MacroConfig.drawStyledText(g, font, "reset", resetBtnX(font), y + 4, overReset(mx, my, font) ? 0xFFFF5555 : C_DIM());
            g.fill(rx + 4, y + 20, rx + w - 4, y + 42, C_SBGR());
            g.fill(rx + 4, y + 42, rx + w - 4, y + 43, C_ACC());
            if (System.currentTimeMillis() - lastBlink > 500) { cursorVisible = !cursorVisible; lastBlink = System.currentTimeMillis(); }
            if (value.isEmpty() && !placeholder.isEmpty()) {
                MacroConfig.drawStyledText(g, font, placeholder, rx + 6, y + 26, C_DIM());
            } else {
                String disp = value.length() > 38 ? value.substring(value.length() - 38) : value;
                MacroConfig.drawStyledText(g, font, disp + (cursorVisible ? "|" : ""), rx + 6, y + 26, C_TXT());
            }
        }
        @Override public boolean contains(int mx, int my) {
            if (w < 0) return false;
            int rx = rx(); return mx >= rx - 2 && mx <= rx + w + 2 && my >= y - 2 && my <= y + h + 2;
        }
        @Override public boolean mouseClicked(int mx, int my, int btn, net.minecraft.client.gui.Font font) {
            if (overReset(mx, my, font)) { value = defaultValue; commit(); }
            return true;
        }
        @Override public void scroll(int dir) {}
        @Override public boolean keyPressed(int key, int scan, int mods) {
            if (key == 259 && !value.isEmpty()) { value = value.substring(0, value.length() - 1); return true; }
            if (key == 257 || key == 335) { commit(); return true; }
            if (key == 86 && (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null) value += clip; return true;
            }
            Character c = fallbackCharFromKey(key, scan, mods);
            if (c != null) { value += c; return true; }
            return false;
        }
        @Override public boolean charTyped(char c, int mods) { value += c; return true; }
        @Override public void commit() { setter.accept(value); }
    }

    static class IntInputSubPanel implements SubPanel {
        final String label;
        String raw;
        final String defaultRaw;
        final int min, max;
        final Consumer<Integer> setter;
        int w = -1;
        final int x, y, h = 50, screenW;
        boolean cursorVisible = true;
        long lastBlink = System.currentTimeMillis();

        IntInputSubPanel(int mx, int my, int sw, int sh, String label, int initial, String defaultVal, int min, int max, Consumer<Integer> setter) {
            this.label = label; this.raw = String.valueOf(initial); this.defaultRaw = defaultVal;
            this.min = min; this.max = max; this.setter = setter;
            this.screenW = sw; this.x = mx; this.y = Math.min(my, sh - h - 4);
        }

        private void ensureWidth(net.minecraft.client.gui.Font font) {
            if (w < 0) {
                String range = min == Integer.MIN_VALUE ? "" : "[" + min + "-" + max + "]";
                w = Math.max(200, font.width(label) + font.width(range) + 60);
            }
        }
        private int rx() { return Math.min(x, screenW - w - 4); }
        private int resetBtnX(net.minecraft.client.gui.Font font) { return rx() + w - font.width("reset") - 8; }
        private boolean overReset(int mx, int my, net.minecraft.client.gui.Font font) {
            int bx = resetBtnX(font);
            return mx >= bx - 2 && mx <= bx + font.width("reset") + 4 && my >= y + 3 && my <= y + 14;
        }

        @Override public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            ensureWidth(font);
            int rx = rx();
            fillRoundRect(g, rx - 2, y - 2, w + 4, h + 4, 4, C_SPBD());
            fillRoundRect(g, rx, y, w, h, 3, C_SPBG());
            MacroConfig.drawStyledText(g, font, label, rx + 5, y + 6, C_TXT());
            String range = min == Integer.MIN_VALUE ? "" : "[" + min + "-" + max + "]";
            MacroConfig.drawStyledText(g, font, range, rx + w - font.width(range) - font.width("reset") - 18, y + 6, C_DIM());
            MacroConfig.drawStyledText(g, font, "reset", resetBtnX(font), y + 4, overReset(mx, my, font) ? 0xFFFF5555 : C_DIM());
            g.fill(rx + 4, y + 20, rx + w - 4, y + 42, C_SBGR());
            g.fill(rx + 4, y + 42, rx + w - 4, y + 43, C_ACC());
            if (System.currentTimeMillis() - lastBlink > 500) { cursorVisible = !cursorVisible; lastBlink = System.currentTimeMillis(); }
            MacroConfig.drawStyledText(g, font, raw + (cursorVisible ? "|" : ""), rx + 6, y + 26, C_TXT());
        }
        @Override public boolean contains(int mx, int my) {
            if (w < 0) return false;
            int rx = rx(); return mx >= rx - 2 && mx <= rx + w + 2 && my >= y - 2 && my <= y + h + 2;
        }
        @Override public boolean mouseClicked(int mx, int my, int btn, net.minecraft.client.gui.Font font) {
            if (overReset(mx, my, font)) { raw = defaultRaw; commit(); }
            return true;
        }
        @Override public void scroll(int dir) {}
        @Override public boolean keyPressed(int key, int scan, int mods) {
            if (key == 259 && !raw.isEmpty()) { raw = raw.substring(0, raw.length() - 1); return true; }
            if (key == 257 || key == 335) { commit(); return true; }
            if (key == 86 && (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
                String clip = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null) for (char ch : clip.toCharArray()) charTyped(ch, 0);
                return true;
            }
            if ((mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) == 0) {
                // Primary path: unshifted key name (works for QWERTY and numpad)
                String name = org.lwjgl.glfw.GLFW.glfwGetKeyName(key, scan);
                if (name != null && name.length() == 1) {
                    char c = name.charAt(0);
                    if (Character.isDigit(c) || (c == '-' && raw.isEmpty())) { raw += c; return true; }
                }
                // Fallback for layouts where digits require Shift (e.g. French/AZERTY):
                // GLFW key codes 48-57 are the top-row digit keys 0-9 regardless of layout.
                if ((mods & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0 && key >= 48 && key <= 57) {
                    char c = (char) key; // key code 48='0'...57='9'
                    raw += c; return true;
                }
                // Numpad digits (GLFW_KEY_KP_0=320 .. GLFW_KEY_KP_9=329)
                if (key >= 320 && key <= 329) {
                    raw += (char)('0' + key - 320); return true;
                }
            }
            return false;
        }
        @Override public boolean charTyped(char c, int mods) {
            if (Character.isDigit(c) || (c == '-' && raw.isEmpty())) { raw += c; return true; }
            return false;
        }
        @Override public void commit() {
            try { int v = Integer.parseInt(raw); setter.accept(min == Integer.MIN_VALUE ? v : Math.max(min, Math.min(max, v))); }
            catch (NumberFormatException ignored) {}
        }
    }

    static class DoubleInputSubPanel implements SubPanel {
        final String label;
        String raw;
        final String defaultRaw;
        final Consumer<Double> setter;
        int w = -1;
        final int x, y, h = 50, screenW;
        boolean cursorVisible = true;
        long lastBlink = System.currentTimeMillis();

        DoubleInputSubPanel(int mx, int my, int sw, int sh, String label, double initial, String defaultVal, Consumer<Double> setter) {
            this.label = label; this.raw = trimDouble(initial); this.defaultRaw = defaultVal;
            this.setter = setter; this.screenW = sw; this.x = mx; this.y = Math.min(my, sh - h - 4);
        }

        private void ensureWidth(net.minecraft.client.gui.Font font) {
            if (w < 0) w = Math.max(200, font.width(label) + 80);
        }
        private int rx() { return Math.min(x, screenW - w - 4); }
        private int resetBtnX(net.minecraft.client.gui.Font font) { return rx() + w - font.width("reset") - 8; }
        private boolean overReset(int mx, int my, net.minecraft.client.gui.Font font) {
            int bx = resetBtnX(font);
            return mx >= bx - 2 && mx <= bx + font.width("reset") + 4 && my >= y + 3 && my <= y + 14;
        }

        @Override public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            ensureWidth(font);
            int rx = rx();
            fillRoundRect(g, rx - 2, y - 2, w + 4, h + 4, 4, C_SPBD());
            fillRoundRect(g, rx, y, w, h, 3, C_SPBG());
            MacroConfig.drawStyledText(g, font, label, rx + 5, y + 6, C_TXT());
            MacroConfig.drawStyledText(g, font, "reset", resetBtnX(font), y + 4, overReset(mx, my, font) ? 0xFFFF5555 : C_DIM());
            g.fill(rx + 4, y + 20, rx + w - 4, y + 42, C_SBGR());
            g.fill(rx + 4, y + 42, rx + w - 4, y + 43, C_ACC());
            if (System.currentTimeMillis() - lastBlink > 500) { cursorVisible = !cursorVisible; lastBlink = System.currentTimeMillis(); }
            MacroConfig.drawStyledText(g, font, raw + (cursorVisible ? "|" : ""), rx + 6, y + 26, C_TXT());
        }
        @Override public boolean contains(int mx, int my) {
            if (w < 0) return false;
            int rx = rx(); return mx >= rx - 2 && mx <= rx + w + 2 && my >= y - 2 && my <= y + h + 2;
        }
        @Override public boolean mouseClicked(int mx, int my, int btn, net.minecraft.client.gui.Font font) {
            if (overReset(mx, my, font)) { raw = defaultRaw; commit(); }
            return true;
        }
        @Override public void scroll(int dir) {}
        @Override public boolean keyPressed(int key, int scan, int mods) {
            if (key == 259 && !raw.isEmpty()) { raw = raw.substring(0, raw.length() - 1); return true; }
            if (key == 257 || key == 335) { commit(); return true; }
            if (key == 86 && (mods & GLFW.GLFW_MOD_CONTROL) != 0) {
                String clip = net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null) for (char ch : clip.toCharArray()) charTyped(ch, 0);
                return true;
            }
            if ((mods & GLFW.GLFW_MOD_CONTROL) == 0) {
                String name = GLFW.glfwGetKeyName(key, scan);
                if (name != null && name.length() == 1) {
                    char c = name.charAt(0);
                    if (Character.isDigit(c) || (c == '-' && raw.isEmpty()) || (c == '.' && !raw.contains("."))) { raw += c; return true; }
                }
                // French/AZERTY: Shift+numrow produces digits; GLFW key 48-57 = '0'-'9'
                if ((mods & GLFW.GLFW_MOD_SHIFT) != 0 && key >= 48 && key <= 57) {
                    raw += (char) key; return true;
                }
                if (key >= 320 && key <= 329) {
                    raw += (char)('0' + key - 320); return true;
                }
                // Decimal point: numpad decimal (330) or period key
                if ((key == 330 || key == 46) && !raw.contains(".")) {
                    raw += '.'; return true;
                }
            }
            return false;
        }
        @Override public boolean charTyped(char c, int mods) {
            if (Character.isDigit(c) || (c == '-' && raw.isEmpty()) || (c == '.' && !raw.contains("."))) { raw += c; return true; }
            return false;
        }
        @Override public void commit() {
            try { setter.accept(Double.parseDouble(raw)); } catch (NumberFormatException ignored) {}
        }
        private static String trimDouble(double value) {
            String formatted = String.format("%.2f", value);
            if (formatted.contains(".")) formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
            return formatted;
        }
    }

    static class ListInputSubPanel implements SubPanel {
        final String label;
        final StringBuilder value;
        final String defaultValue;
        final Consumer<List<String>> setter;
        final String[] hints;
        int w = -1;
        final int x, y, h = 122, screenW;
        boolean cursorVisible = true;
        long lastBlink = System.currentTimeMillis();
        int cursorIndex;
        int scrollLine = 0;

        ListInputSubPanel(int mx, int my, int sw, int sh, String label, String defaultVal, List<String> initial, Consumer<List<String>> setter, String... hints) {
            this.label = label; this.setter = setter;
            this.hints = hints == null ? new String[0] : hints;
            String joined = initial == null ? "" : String.join("\n", initial);
            this.value = new StringBuilder(joined);
            this.defaultValue = defaultVal.isEmpty() ? joined : defaultVal;
            this.cursorIndex = this.value.length();
            this.screenW = sw; this.x = mx; this.y = Math.min(my, sh - h - 4);
        }

        private void ensureWidth(net.minecraft.client.gui.Font font) {
            if (w < 0) {
                int base = font.width(label) + 60;
                for (String hint : hints) base = Math.max(base, font.width(hint) + 20);
                w = Math.max(260, base);
            }
        }
        private int rx() { return Math.min(x, screenW - w - 4); }
        private int resetBtnX(net.minecraft.client.gui.Font font) { return rx() + w - font.width("reset") - 8; }
        private boolean overReset(int mx, int my, net.minecraft.client.gui.Font font) {
            int bx = resetBtnX(font);
            return mx >= bx - 2 && mx <= bx + font.width("reset") + 4 && my >= y + 3 && my <= y + 14;
        }

        private int textLeft() { return rx() + 6; }
        private int textTop()  { return y + 22 + hints.length * 10; }
        private int textRight() { return rx() + w - 6; }
        private int textBottom() { return y + h - 8; }
        private int lineStep(net.minecraft.client.gui.Font font) { return font.lineHeight + 1; }
        private List<String> lines() { return java.util.Arrays.asList(value.toString().split("\n", -1)); }

        private int cursorLine() {
            int line = 0;
            for (int i = 0; i < Math.min(cursorIndex, value.length()); i++) if (value.charAt(i) == '\n') line++;
            return line;
        }
        private int cursorColumn() {
            int col = 0;
            for (int i = Math.min(cursorIndex, value.length()) - 1; i >= 0; i--) { if (value.charAt(i) == '\n') break; col++; }
            return col;
        }
        private int lineStartIndex(int targetLine) {
            int line = 0;
            for (int i = 0; i < value.length(); i++) { if (line == targetLine) return i; if (value.charAt(i) == '\n') line++; }
            return line == targetLine ? value.length() : value.length();
        }
        private int lineEndIndex(int startIndex) { int idx = value.indexOf("\n", startIndex); return idx == -1 ? value.length() : idx; }
        private int visibleLineCount(net.minecraft.client.gui.Font font) { return Math.max(1, (textBottom() - textTop()) / lineStep(font)); }
        private int maxScrollLine(net.minecraft.client.gui.Font font) { return Math.max(0, lines().size() - visibleLineCount(font)); }
        private void clampScroll(net.minecraft.client.gui.Font font) { scrollLine = Math.max(0, Math.min(maxScrollLine(font), scrollLine)); }
        private void ensureCursorVisible(net.minecraft.client.gui.Font font) {
            int cl = cursorLine(), vis = visibleLineCount(font);
            if (cl < scrollLine) scrollLine = cl;
            else if (cl >= scrollLine + vis) scrollLine = cl - vis + 1;
            clampScroll(font);
        }
        private void moveCursorHorizontal(int delta) { cursorIndex = Math.max(0, Math.min(value.length(), cursorIndex + delta)); }
        private void moveCursorVertical(int delta) {
            int tl = Math.max(0, cursorLine() + delta), tc = cursorColumn();
            int ts = lineStartIndex(tl);
            cursorIndex = Math.min(ts + tc, lineEndIndex(ts));
        }
        private void insertText(String text) { if (text == null || text.isEmpty()) return; value.insert(cursorIndex, text); cursorIndex += text.length(); }

        @Override public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            ensureWidth(font);
            int rx = rx();
            fillRoundRect(g, rx - 2, y - 2, w + 4, h + 4, 4, C_SPBD());
            fillRoundRect(g, rx, y, w, h, 3, C_SPBG());
            MacroConfig.drawStyledText(g, font, label, rx + 5, y + 6, C_TXT());
            MacroConfig.drawStyledText(g, font, "reset", resetBtnX(font), y + 4, overReset(mx, my, font) ? 0xFFFF5555 : C_DIM());
            int hintY = y + 16;
            for (String hint : hints) { MacroConfig.drawStyledText(g, font, hint, rx + 6, hintY, C_DIM()); hintY += 10; }
            g.fill(rx + 4, y + 18 + hints.length * 10, rx + w - 4, y + h - 6, C_SBGR());
            g.fill(rx + 4, y + h - 6, rx + w - 4, y + h - 5, C_ACC());
            if (System.currentTimeMillis() - lastBlink > 500) { cursorVisible = !cursorVisible; lastBlink = System.currentTimeMillis(); }
            clampScroll(font);
            List<String> lines = lines();
            int step = lineStep(font), drawY = textTop(), visible = visibleLineCount(font);
            int caretLine = cursorLine(), caretCol = cursorColumn();
            for (int li = scrollLine; li < lines.size() && li < scrollLine + visible; li++) {
                String line = lines.get(li);
                MacroConfig.drawStyledText(g, font, line, textLeft(), drawY, C_TXT());
                if (cursorVisible && li == caretLine) {
                    int caretX = textLeft() + font.width(line.substring(0, Math.min(caretCol, line.length())));
                    g.fill(caretX, drawY - 1, caretX + 1, drawY + font.lineHeight, C_TXT());
                }
                drawY += step;
            }
            if (maxScrollLine(font) > 0) {
                int tx0 = rx + w - 5, tx1 = rx + w - 3, ty0 = y + 20 + hints.length * 10, ty1 = y + h - 8;
                int th = ty1 - ty0, total = lines.size(), thumbH = Math.max(10, th * visible / total);
                int travel = Math.max(0, th - thumbH), maxSc = maxScrollLine(font);
                int thumbY = ty0 + (maxSc == 0 ? 0 : travel * scrollLine / maxSc);
                g.fill(tx0, ty0, tx1, ty1, C_OFF());
                g.fill(tx0, thumbY, tx1, thumbY + thumbH, C_ACC());
            }
        }

        @Override public boolean contains(int mx, int my) {
            if (w < 0) return false;
            int rx = rx(); return mx >= rx - 2 && mx <= rx + w + 2 && my >= y - 2 && my <= y + h + 2;
        }
        @Override public boolean mouseClicked(int mx, int my, int btn, net.minecraft.client.gui.Font font) {
            if (overReset(mx, my, font)) {
                value.setLength(0); value.append(defaultValue); cursorIndex = value.length(); commit(); return true;
            }
            int rx = rx();
            if (mx >= rx + 4 && mx <= rx + w - 4 && my >= y + 18 + hints.length * 10 && my <= y + h - 6) {
                clampScroll(font);
                int line = scrollLine + Math.max(0, (my - textTop()) / Math.max(1, lineStep(font)));
                List<String> lines = lines();
                line = Math.max(0, Math.min(lines.size() - 1, line));
                String textLine = lines.get(line);
                int clickedX = Math.max(textLeft(), Math.min(textRight(), mx)), column = 0;
                for (int i = 1; i <= textLine.length(); i++) { if (textLeft() + font.width(textLine.substring(0, i)) > clickedX) break; column = i; }
                int ls = lineStartIndex(line);
                cursorIndex = Math.min(ls + column, ls + textLine.length());
                ensureCursorVisible(font);
            }
            return true;
        }
        @Override public void scroll(int dir) {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            scrollLine += dir; clampScroll(font);
        }
        @Override public boolean keyPressed(int key, int scan, int mods) {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            if (key == GLFW.GLFW_KEY_SPACE) { insertText(" "); ensureCursorVisible(font); return true; }
            if (key == 259 && value.length() > 0) {
                if (cursorIndex > 0) { value.deleteCharAt(cursorIndex - 1); cursorIndex--; ensureCursorVisible(font); } return true;
            }
            if (key == 261 && cursorIndex < value.length()) { value.deleteCharAt(cursorIndex); ensureCursorVisible(font); return true; }
            if (key == 257 || key == 335) { insertText("\n"); ensureCursorVisible(font); return true; }
            if (key == GLFW.GLFW_KEY_LEFT)  { moveCursorHorizontal(-1); ensureCursorVisible(font); return true; }
            if (key == GLFW.GLFW_KEY_RIGHT) { moveCursorHorizontal(1);  ensureCursorVisible(font); return true; }
            if (key == GLFW.GLFW_KEY_UP)    { moveCursorVertical(-1);   ensureCursorVisible(font); return true; }
            if (key == GLFW.GLFW_KEY_DOWN)  { moveCursorVertical(1);    ensureCursorVisible(font); return true; }
            if (key == 86 && (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null) insertText(clip.replace("\r", "")); ensureCursorVisible(font); return true;
            }
            Character c = fallbackCharFromKey(key, scan, mods);
            if (c != null) { insertText(String.valueOf(c)); ensureCursorVisible(font); return true; }
            return false;
        }
        @Override public boolean charTyped(char c, int mods) {
            if (c == '\r') return true;
            insertText(String.valueOf(c)); ensureCursorVisible(Minecraft.getInstance().font); return true;
        }
        @Override public void commit() {
            List<String> values = new ArrayList<>();
            for (String line : value.toString().split("\\R")) { String t = line.trim(); if (!t.isEmpty()) values.add(t); }
            setter.accept(values);
        }
    }
}