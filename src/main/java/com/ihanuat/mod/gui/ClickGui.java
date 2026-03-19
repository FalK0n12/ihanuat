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

public class ClickGui extends Screen {

    static int C_BG()     { return MacroConfig.themePanelBg; }
    static int C_HDR()    { return MacroConfig.themePanelHeader; }
    static int C_LINE()   { return MacroConfig.themeAccent; }
    static int C_TXT()    { return MacroConfig.themeText; }
    static int C_DIM()    { return MacroConfig.themeTextDim; }
    static int C_ON()     { return MacroConfig.themeToggleOn; }
    static int C_ON2()    { return brighten(MacroConfig.themeToggleOn, 0x222222); }
    static int C_OFF()    { return MacroConfig.themeToggleOff; }
    static int C_HOVER()  { return darken(MacroConfig.themePanelHeader, 0x060606); }
    static int C_SBGR()   { return darken(MacroConfig.themePanelBg, 0x050505); }
    static int C_SFILL()  { return MacroConfig.themeSliderFill; }
    static int C_SKNOB()  { return brighten(MacroConfig.themeSliderFill, 0x333333); }
    static int C_SPBG()   { return MacroConfig.themePanelBg; }
    static int C_SPBD()   { return MacroConfig.themeAccent; }
    static int C_ACC()    { return MacroConfig.themeAccent; }
    static int C_BTN()    { return MacroConfig.themeButtonHover; }

    static int brighten(int c, int a) {
        return (c & 0xFF000000)
             | (Math.min(255, ((c>>16)&0xFF)+((a>>16)&0xFF)) << 16)
             | (Math.min(255, ((c>>8 )&0xFF)+((a>>8 )&0xFF)) << 8)
             |  Math.min(255, ( c     &0xFF)+( a     &0xFF));
    }
    static int darken(int c, int a) {
        return (c & 0xFF000000)
             | (Math.max(0, ((c>>16)&0xFF)-((a>>16)&0xFF)) << 16)
             | (Math.max(0, ((c>>8 )&0xFF)-((a>>8 )&0xFF)) << 8)
             |  Math.max(0, ( c    &0xFF) -( a     &0xFF));
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

    // theme share code — base64 of 9 hex ints joined by commas
    static String encodeTheme() {
        String s = String.format("%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X,%08X",
            MacroConfig.themePanelBg, MacroConfig.themePanelHeader, MacroConfig.themeAccent,
            MacroConfig.themeText, MacroConfig.themeTextDim, MacroConfig.themeToggleOn,
            MacroConfig.themeToggleOff, MacroConfig.themeSliderFill, MacroConfig.themeButtonHover);
        return Base64.getEncoder().encodeToString(s.getBytes());
    }

    static boolean applyThemeCode(String code) {
        try {
            String s = new String(Base64.getDecoder().decode(code.trim()));
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
            MacroConfig.save();
            return true;
        } catch (Exception e) { return false; }
    }

    private static final int PANEL_W      = 180;
    private static final int HEADER_H     = 18;
    private static final int ENTRY_H      = 18;
    private static final int ENTRY_PAD    = 3;
    private static final int PANEL_RADIUS = 4;

    private boolean shiftHeld = false;

    private final List<Panel> panels = new ArrayList<>();
    private SubPanel activeSubPanel = null;
    private Panel draggingPanel = null;
    private int dragOffX, dragOffY;
    private SliderEntry draggingSlider = null;
    private Panel draggingSliderPanel = null;

    public ClickGui() { super(Component.literal("ihanuat")); }

    @Override
    protected void init() {
        panels.clear();
        buildPanels();
        ScreenMouseEvents.allowMouseClick(this).register((scr, event) -> { handleMouseClicked((int)event.x(), (int)event.y(), event.button()); return true; });
        ScreenMouseEvents.allowMouseRelease(this).register((scr, event) -> { handleMouseReleased(); return true; });
        ScreenMouseEvents.allowMouseDrag(this).register((scr, event, dx, dy) -> { handleMouseDragged((int)event.x(), (int)event.y()); return true; });
        ScreenMouseEvents.allowMouseScroll(this).register((scr, mx, my, hScroll, vScroll) -> { handleMouseScrolled((int)mx, (int)my, hScroll, vScroll, shiftHeld); return true; });
        ScreenKeyboardEvents.allowKeyPress(this).register((scr, event) -> {
            if ((event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0) shiftHeld = true;
            handleKeyPressed(event.key(), event.scancode(), event.modifiers()); return true;
        });
        ScreenKeyboardEvents.allowKeyRelease(this).register((scr, event) -> {
            if (event.key() == 340 || event.key() == 344) shiftHeld = false;
            handleKeyReleased(event.key()); return true;
        });
    }

    public boolean charTyped(char c, int mods) { handleCharTyped(c, mods); return true; }

    private void buildPanels() {
        int startX = 4, startY = 4;
        int[] idx = {0};
        Supplier<int[]> nextPos = () -> { int i = idx[0]++; return new int[]{startX, startY + i * (HEADER_H + 1)}; };
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
        if (saved != null && i < saved.length && saved[i] != null && saved[i].length == 2
                && (saved[i][0] != 0 || saved[i][1] != 0)) return saved[i];
        return fallback.get();
    }


    private Panel generalPanel(int[] pos) {
        Panel p = new Panel("General", pos[0], pos[1]);
        p.add(toggle("Show Macro HUD",          () -> MacroConfig.showHud,                         v -> { MacroConfig.showHud = v; save(); }));
        p.add(toggle("GUI Only in Garden",       () -> MacroConfig.guiOnlyInGarden,                 v -> { MacroConfig.guiOnlyInGarden = v; save(); }));
        p.add(toggle("Enable PlotTP Rewarp",     () -> MacroConfig.enablePlotTpRewarp,              v -> { MacroConfig.enablePlotTpRewarp = v; save(); }));
        p.add(toggle("Hold W Until Wall",        () -> MacroConfig.holdWUntilWall,                  v -> { MacroConfig.holdWUntilWall = v; save(); }));
        p.add(cycleEnum("Unfly Mode", MacroConfig.UnflyMode.values(), () -> MacroConfig.unflyMode,  v -> { MacroConfig.unflyMode = v; save(); }));
        p.add(textSetting("Farm Script",         () -> MacroConfig.restartScript,                   v -> { MacroConfig.restartScript = v; save(); }));
        p.add(textSetting("PlotTP Number",       () -> MacroConfig.plotTpNumber,                    v -> { MacroConfig.plotTpNumber = v; save(); }));
        p.add(button("Capture Rewarp Pos", () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) { MacroConfig.rewarpEndX = mc.player.getX(); MacroConfig.rewarpEndY = mc.player.getY(); MacroConfig.rewarpEndZ = mc.player.getZ(); MacroConfig.rewarpEndPosSet = true; save(); mc.player.displayClientMessage(Component.literal("Rewarp position captured!"), true); }
        }));
        p.add(toggle("Auto-Resume After Rest",   () -> MacroConfig.autoResumeAfterDynamicRest,      v -> { MacroConfig.autoResumeAfterDynamicRest = v; save(); }));
        p.add(toggle("Auto-Recover Disconnect",  () -> MacroConfig.autoRecoverUnexpectedDisconnect, v -> { MacroConfig.autoRecoverUnexpectedDisconnect = v; save(); }));
        p.add(toggle("Persist Session Timer",    () -> MacroConfig.persistSessionTimer,             v -> { MacroConfig.persistSessionTimer = v; save(); }));
        return p;
    }

    private Panel delaysPanel(int[] pos) {
        Panel p = new Panel("Delays", pos[0], pos[1]);
        p.add(slider("Rand Delay",    0,   1000, () -> MacroConfig.additionalRandomDelay, v -> { MacroConfig.additionalRandomDelay = v; save(); }, "ms"));
        p.add(slider("Rotation",      100, 3000, () -> MacroConfig.rotationTime,          v -> { MacroConfig.rotationTime = v; save(); }, "ms"));
        p.add(slider("GUI Click",     100, 2000, () -> MacroConfig.guiClickDelay,         v -> { MacroConfig.guiClickDelay = v; save(); }, "ms"));
        p.add(slider("Equip Swap",    100, 300,  () -> MacroConfig.equipmentSwapDelay,    v -> { MacroConfig.equipmentSwapDelay = v; save(); }, "ms"));
        p.add(slider("Rod Swap",      50,  1000, () -> MacroConfig.rodSwapDelay,          v -> { MacroConfig.rodSwapDelay = v; save(); }, "ms"));
        p.add(slider("Garden Warp",   0,   3000, () -> MacroConfig.gardenWarpDelay,       v -> { MacroConfig.gardenWarpDelay = v; save(); }, "ms"));
        p.add(slider("Pest Chat",     0,   3000, () -> MacroConfig.pestChatTriggerDelay,  v -> { MacroConfig.pestChatTriggerDelay = v; save(); }, "ms"));
        p.add(slider("Book Combine",  100, 2000, () -> MacroConfig.bookCombineDelay,      v -> { MacroConfig.bookCombineDelay = v; save(); }, "ms"));
        p.add(slider("Junk Drop",     0,   1000, () -> MacroConfig.junkItemDropDelay,     v -> { MacroConfig.junkItemDropDelay = v; save(); }, "ms"));
        return p;
    }

    private Panel wardrobePanel(int[] pos) {
        Panel p = new Panel("Wardrobe Swap", pos[0], pos[1]);
        p.add(toggle("Auto Wardrobe (Pest)",    () -> MacroConfig.autoWardrobePest,    v -> { MacroConfig.autoWardrobePest = v; save(); }));
        p.add(toggle("Auto Wardrobe (Visitor)", () -> MacroConfig.autoWardrobeVisitor, v -> { MacroConfig.autoWardrobeVisitor = v; save(); }));
        p.add(toggle("Armor Swap (Visitor)",    () -> MacroConfig.armorSwapVisitor,    v -> { MacroConfig.armorSwapVisitor = v; save(); }));
        p.add(slider("Farming Slot", 1, 9, () -> MacroConfig.wardrobeSlotFarming, v -> { MacroConfig.wardrobeSlotFarming = v; save(); }, ""));
        p.add(slider("Pest Slot",    1, 9, () -> MacroConfig.wardrobeSlotPest,    v -> { MacroConfig.wardrobeSlotPest = v; save(); }, ""));
        p.add(slider("Visitor Slot", 1, 9, () -> MacroConfig.wardrobeSlotVisitor, v -> { MacroConfig.wardrobeSlotVisitor = v; save(); }, ""));
        return p;
    }

    private Panel autoRodPanel(int[] pos) {
        Panel p = new Panel("Auto Rod", pos[0], pos[1]);
        p.add(toggle("Rod on Pest CD",        () -> MacroConfig.autoRodPestCd,       v -> { MacroConfig.autoRodPestCd = v; save(); }));
        p.add(toggle("Rod on Pest Spawn",     () -> MacroConfig.autoRodPestSpawn,    v -> { MacroConfig.autoRodPestSpawn = v; save(); }));
        p.add(toggle("Rod on Return to Farm", () -> MacroConfig.autoRodReturnToFarm, v -> { MacroConfig.autoRodReturnToFarm = v; save(); }));
        return p;
    }

    private Panel equipmentPanel(int[] pos) {
        Panel p = new Panel("Equipment Swap", pos[0], pos[1]);
        p.add(toggle("Auto-Equipment",       () -> MacroConfig.autoEquipment,     v -> { MacroConfig.autoEquipment = v; save(); }));
        p.add(slider("Swap Delay", 100, 300, () -> MacroConfig.equipmentSwapDelay, v -> { MacroConfig.equipmentSwapDelay = v; save(); }, "ms"));
        return p;
    }

    private Panel autoPestPanel(int[] pos) {
        Panel p = new Panel("Auto Pest", pos[0], pos[1]);
        p.add(slider("Threshold",        1,  8,  () -> MacroConfig.pestThreshold,              v -> { MacroConfig.pestThreshold = v; save(); }, ""));
        p.add(toggle("Trigger on Chat",  () -> MacroConfig.triggerPestOnChat,                  v -> { MacroConfig.triggerPestOnChat = v; save(); }));
        p.add(toggle("Delay Crop Fever", () -> MacroConfig.delayPestForCropFever,              v -> { MacroConfig.delayPestForCropFever = v; save(); }));
        p.add(toggle("AOTV to Roof",     () -> MacroConfig.aotvToRoof,                         v -> { MacroConfig.aotvToRoof = v; save(); }));
        p.add(csvTextSetting("AOTV Roof Plots",
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
        p.add(toggle("Break Before AOTV",() -> MacroConfig.breakBlocksBeforeAotv,              v -> { MacroConfig.breakBlocksBeforeAotv = v; save(); }));
        p.add(slider("Roof Pitch",       45, 90, () -> MacroConfig.aotvRoofPitch,              v -> { MacroConfig.aotvRoofPitch = v; save(); }, ""));
        p.add(slider("Pitch Human.",     0,  15, () -> MacroConfig.aotvRoofPitchHumanization,  v -> { MacroConfig.aotvRoofPitchHumanization = v; save(); }, ""));
        return p;
    }

    private Panel autoVisitorPanel(int[] pos) {
        Panel p = new Panel("Auto Visitor", pos[0], pos[1]);
        p.add(toggle("Auto-Visitor",  () -> MacroConfig.autoVisitor,       v -> { MacroConfig.autoVisitor = v; save(); }));
        p.add(slider("Threshold",     1, 5, () -> MacroConfig.visitorThreshold, v -> { MacroConfig.visitorThreshold = v; save(); }, ""));
        return p;
    }

    private Panel autoGeorgePanel(int[] pos) {
        Panel p = new Panel("Auto George", pos[0], pos[1]);
        p.add(toggle("Auto George Sell", () -> MacroConfig.autoGeorgeSell,       v -> { MacroConfig.autoGeorgeSell = v; save(); }));
        p.add(slider("Threshold",        1, 35, () -> MacroConfig.georgeSellThreshold, v -> { MacroConfig.georgeSellThreshold = v; save(); }, ""));
        return p;
    }

    private Panel autoSellPanel(int[] pos) {
        Panel p = new Panel("Auto Sell", pos[0], pos[1]);
        p.add(toggle("Custom Autosell", () -> MacroConfig.autoBoosterCookie, v -> { MacroConfig.autoBoosterCookie = v; save(); }));
        p.add(listSetting("Autosell Item List", () -> MacroConfig.boosterCookieItems,
                v -> { MacroConfig.boosterCookieItems = new ArrayList<>(v); save(); }));
        return p;
    }

    private Panel profitPanel(int[] pos) {
        Panel p = new Panel("Profit Calculator", pos[0], pos[1]);
        p.add(toggle("Session HUD",   () -> MacroConfig.showSessionProfitHud,       v -> { MacroConfig.showSessionProfitHud = v; save(); }));
        p.add(toggle("Daily HUD",     () -> MacroConfig.showTotalToday,              v -> { MacroConfig.showTotalToday = v; save(); }));
        p.add(toggle("Lifetime HUD",  () -> MacroConfig.showLifetimeHud,            v -> { MacroConfig.showLifetimeHud = v; save(); }));
        p.add(toggle("HUD While Off", () -> MacroConfig.showProfitHudWhileInactive,  v -> { MacroConfig.showProfitHudWhileInactive = v; save(); }));
        p.add(toggle("Compact",       () -> MacroConfig.compactProfitCalculator,    v -> { MacroConfig.compactProfitCalculator = v; save(); }));
        p.add(listSetting("Pet XP List", () -> MacroConfig.petXpTrackedPets,
                v -> { MacroConfig.petXpTrackedPets = new ArrayList<>(v); ProfitManager.refreshConfiguredPetXpPrices(); save(); },
                "Pet Name, Max Level (100/200), Level 1 Price,",
                "Max Level Price, Rarity" + String.join(", ", java.util.Arrays.stream(MacroConfig.PetRarity.values()).map(Enum::name).toList())));
        p.add(button("Reset Session",  () -> { MacroStateManager.resetSession(); notifyMsg("Session reset!"); }));
        p.add(button("Reset Daily",    () -> { ProfitManager.resetDaily();       notifyMsg("Daily reset!"); }));
        p.add(button("Reset Lifetime", () -> { ProfitManager.resetLifetime();    notifyMsg("Lifetime reset!"); }));
        return p;
    }

    private Panel dynamicRestPanel(int[] pos) {
        Panel p = new Panel("Dynamic Rest", pos[0], pos[1]);
        p.add(intField("Script Time",   () -> MacroConfig.restScriptingTime,       v -> { MacroConfig.restScriptingTime = v; save(); }, "min"));
        p.add(intField("Script Offset", () -> MacroConfig.restScriptingTimeOffset, v -> { MacroConfig.restScriptingTimeOffset = v; save(); }, "min"));
        p.add(intField("Break Time",    () -> MacroConfig.restBreakTime,           v -> { MacroConfig.restBreakTime = v; save(); }, "min"));
        p.add(intField("Break Offset",  () -> MacroConfig.restBreakTimeOffset,     v -> { MacroConfig.restBreakTimeOffset = v; save(); }, "min"));
        return p;
    }

    private Panel qolPanel(int[] pos) {
        Panel p = new Panel("QOL", pos[0], pos[1]);
        p.add(toggle("Book Combine",     () -> MacroConfig.autoBookCombine,    v -> { MacroConfig.autoBookCombine = v; save(); }));
        p.add(toggle("Always Combine",   () -> MacroConfig.alwaysActiveCombine,v -> { MacroConfig.alwaysActiveCombine = v; save(); }));
        p.add(slider("Book Threshold",   1, 35, () -> MacroConfig.bookThreshold, v -> { MacroConfig.bookThreshold = v; save(); }, ""));
        p.add(toggle("Chat Cleanup",     () -> MacroConfig.hideFilteredChat,   v -> { MacroConfig.hideFilteredChat = v; save(); }));
        p.add(toggle("Auto-Drop Junk",   () -> MacroConfig.autoDropJunk,       v -> { MacroConfig.autoDropJunk = v; save(); }));
        p.add(listSetting("Junk List",   () -> MacroConfig.junkItems,
                v -> { MacroConfig.junkItems = new ArrayList<>(v); save(); }));
        p.add(slider("Junk Threshold",   1, 35, () -> MacroConfig.junkThreshold, v -> { MacroConfig.junkThreshold = v; save(); }, ""));
        p.add(textSetting("Junk PlotTP", () -> MacroConfig.dropJunkPlotTp,    v -> { MacroConfig.dropJunkPlotTp = v; save(); }));
        p.add(toggle("Stash Manager",    () -> MacroConfig.autoStashManager,  v -> { MacroConfig.autoStashManager = v; save(); }));
        p.add(toggle("Discord Status",   () -> MacroConfig.sendDiscordStatus, v -> { MacroConfig.sendDiscordStatus = v; save(); }));
        p.add(textSetting("Webhook URL", () -> MacroConfig.discordWebhookUrl, v -> { MacroConfig.discordWebhookUrl = v; save(); }));
        p.add(intField("Discord Interval", () -> MacroConfig.discordStatusUpdateTime, v -> { MacroConfig.discordStatusUpdateTime = v; save(); }, "min"));
        p.add(toggle("Debug Messages",   () -> MacroConfig.showDebug,         v -> { MacroConfig.showDebug = v; save(); }));
        p.add(toggle("Log to File",      () -> MacroConfig.logDebugToFile,    v -> { MacroConfig.logDebugToFile = v; if (!v) com.ihanuat.mod.DebugLogger.getInstance().close(); save(); }));
        p.add(button("Open Log Folder", () -> {
            try { java.awt.Desktop.getDesktop().open(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().toFile()); }
            catch (Exception e) { notifyMsg("Failed: " + e.getMessage()); }
        }));
        return p;
    }

    private Panel themePanel(int[] pos) {
        Panel p = new Panel("Theme", pos[0], pos[1]);
        p.add(colorEntry("Panel BG",     () -> MacroConfig.themePanelBg,     v -> { MacroConfig.themePanelBg = v; save(); }));
        p.add(colorEntry("Panel Header", () -> MacroConfig.themePanelHeader, v -> { MacroConfig.themePanelHeader = v; save(); }));
        p.add(colorEntry("Accent",       () -> MacroConfig.themeAccent,      v -> { MacroConfig.themeAccent = v; save(); }));
        p.add(colorEntry("Text",         () -> MacroConfig.themeText,        v -> { MacroConfig.themeText = v; save(); }));
        p.add(colorEntry("Text Dimmed",  () -> MacroConfig.themeTextDim,     v -> { MacroConfig.themeTextDim = v; save(); }));
        p.add(colorEntry("Toggle ON",    () -> MacroConfig.themeToggleOn,    v -> { MacroConfig.themeToggleOn = v; save(); }));
        p.add(colorEntry("Toggle OFF",   () -> MacroConfig.themeToggleOff,   v -> { MacroConfig.themeToggleOff = v; save(); }));
        p.add(colorEntry("Slider Fill",  () -> MacroConfig.themeSliderFill,  v -> { MacroConfig.themeSliderFill = v; save(); }));
        p.add(colorEntry("Button Hover", () -> MacroConfig.themeButtonHover, v -> { MacroConfig.themeButtonHover = v; save(); }));
        p.add(button("Copy Theme Code", () -> {
            String code = encodeTheme();
            Minecraft mc = Minecraft.getInstance();
            mc.keyboardHandler.setClipboard(code);
            notifyMsg("Theme code copied!");
        }));
        p.add(new ImportCodeEntry());
        p.add(button("Reset to Default", () -> {
            MacroConfig.themePanelBg     = 0xF0101018;
            MacroConfig.themePanelHeader = 0xFF18182C;
            MacroConfig.themeAccent      = 0xFF5050A0;
            MacroConfig.themeText        = 0xFFCCCCCC;
            MacroConfig.themeTextDim     = 0xFF666677;
            MacroConfig.themeToggleOn    = 0xFF4444BB;
            MacroConfig.themeToggleOff   = 0xFF2A2A3A;
            MacroConfig.themeSliderFill  = 0xFF3A3A99;
            MacroConfig.themeButtonHover = 0xFF4444BB;
            save(); notifyMsg("Theme reset!");
        }));
        return p;
    }


    private static ToggleEntry toggle(String l, Supplier<Boolean> g, Consumer<Boolean> s) { return new ToggleEntry(l, g, s); }
    private static SliderEntry slider(String l, int mn, int mx, Supplier<Integer> g, Consumer<Integer> s, String u) { return new SliderEntry(l, mn, mx, g, s, u); }
    private static <E extends Enum<E>> CycleEnumEntry<E> cycleEnum(String l, E[] vs, Supplier<E> g, Consumer<E> s) { return new CycleEnumEntry<>(l, vs, g, s); }
    private static TextSettingEntry textSetting(String l, Supplier<String> g, Consumer<String> s) { return new TextSettingEntry(l, g, s); }
    private static TextSettingEntry csvTextSetting(String l, Supplier<String> g, Consumer<String> s, String placeholder) { return new TextSettingEntry(l, g, s, placeholder); }
    private static ListSettingEntry listSetting(String l, Supplier<List<String>> g, Consumer<List<String>> s) { return new ListSettingEntry(l, g, s); }
    private static ListSettingEntry listSetting(String l, Supplier<List<String>> g, Consumer<List<String>> s, String... hints) { return new ListSettingEntry(l, g, s, hints); }
    private static IntFieldEntry intField(String l, Supplier<Integer> g, Consumer<Integer> s, String u) { return new IntFieldEntry(l, g, s, u); }
    private static ButtonEntry button(String l, Runnable a) { return new ButtonEntry(l, a); }
    private static ColorEntry colorEntry(String l, Supplier<Integer> g, Consumer<Integer> s) { return new ColorEntry(l, g, s); }


    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x99000000);
        for (int i = panels.size() - 1; i >= 0; i--) panels.get(i).render(g, mx, my, font);
        if (activeSubPanel != null) activeSubPanel.render(g, mx, my, font);
        g.drawString(font, "ihanuat  shift+scroll=pan", 3, height - 9, 0xFF333355, false);
    }


    void handleMouseClicked(int x, int y, int btn) {
        if (activeSubPanel != null) {
            if (activeSubPanel.contains(x, y)) { activeSubPanel.mouseClicked(x, y, btn, font); return; }
            else { activeSubPanel.commit(); activeSubPanel = null; save(); return; }
        }
        for (Panel panel : new ArrayList<>(panels)) {
            if (panel.headerContains(x, y)) {
                if (btn == 0) { draggingPanel = panel; dragOffX = x - panel.x; dragOffY = y - panel.y; }
                else if (btn == 1) { panel.collapsed = !panel.collapsed; }
                panels.remove(panel); panels.add(0, panel);
                return;
            }
            if (panel.contains(x, y)) {
                panels.remove(panel); panels.add(0, panel);
                Entry hit = panel.entryAt(x, y);
                if (hit != null) {
                    if (btn == 0) {
                        if (hit instanceof SliderEntry se) { draggingSlider = se; draggingSliderPanel = panel; se.onDrag(x, panel.x + ENTRY_PAD + 2, PANEL_W - ENTRY_PAD * 2 - 4); }
                        else if (hit instanceof ColorEntry || hit instanceof ImportCodeEntry) { SubPanel sp = hit.openSubPanel(x, y, width, height); if (sp != null) activeSubPanel = sp; }
                        else hit.onClick(x, y);
                    } else if (btn == 1) { SubPanel sp = hit.openSubPanel(x, y, width, height); if (sp != null) activeSubPanel = sp; }
                }
                return;
            }
        }
    }

    void handleMouseReleased() {
        if (activeSubPanel instanceof ColorSubPanel cs) { cs.draggingSlider = -1; }
        if (draggingPanel != null) savePanelPositions();
        draggingPanel = null; draggingSlider = null; draggingSliderPanel = null;
    }

    void handleMouseDragged(int x, int y) {
        if (activeSubPanel instanceof ColorSubPanel cs) { cs.drag(x); return; }
        if (draggingPanel != null) {
            draggingPanel.x = Math.max(0, Math.min(width - PANEL_W, x - dragOffX));
            draggingPanel.y = Math.max(0, Math.min(height - HEADER_H, y - dragOffY));
        } else if (draggingSlider != null && draggingSliderPanel != null) {
            draggingSlider.onDrag(x, draggingSliderPanel.x + ENTRY_PAD + 2, PANEL_W - ENTRY_PAD * 2 - 4);
        }
    }

    void handleMouseScrolled(int x, int y, double hScroll, double vScroll, boolean shift) {
        if (hScroll != 0 || shift) { int pan = (int)((hScroll != 0 ? hScroll : vScroll) * 20); for (Panel p : panels) p.x += pan; return; }
        if (activeSubPanel != null && activeSubPanel.contains(x, y)) { activeSubPanel.scroll((int)-vScroll); return; }
        for (Panel panel : panels) { if (panel.contains(x, y)) { panel.scroll((int)-vScroll); return; } }
    }

    void handleKeyPressed(int key, int scan, int mods) {
        if (activeSubPanel != null) {
            if (activeSubPanel.keyPressed(key, scan, mods)) return;
            if (key == 256) { activeSubPanel.commit(); activeSubPanel = null; save(); return; }
        }
        if (key == 256) onClose();
    }

    void handleKeyReleased(int key) { if (key == 340 || key == 344) shiftHeld = false; }
    void handleCharTyped(char c, int mods) { if (activeSubPanel != null) activeSubPanel.charTyped(c, mods); }

    @Override public void onClose() { savePanelPositions(); save(); super.onClose(); }
    @Override public boolean isPauseScreen() { return false; }

    private static void save() { MacroConfig.save(); }
    private void notifyMsg(String msg) { Minecraft mc = Minecraft.getInstance(); if (mc.player != null) mc.player.displayClientMessage(Component.literal(msg), true); }

    private void savePanelPositions() {
        String[] order = {"General","Delays","Wardrobe Swap","Auto Rod","Equipment Swap","Auto Pest","Auto Visitor","Auto George","Auto Sell","Profit Calculator","Dynamic Rest","QOL","Theme"};
        int[][] positions = new int[order.length][2];
        for (int i = 0; i < order.length; i++)
            for (Panel p : panels)
                if (p.title.equals(order[i])) { positions[i] = new int[]{p.x, p.y}; break; }
        MacroConfig.clickGuiPanelPositions = positions;
        MacroConfig.save();
    }

    static void fillRoundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        for (int row = 0; row < h; row++) {
            int ind = 0;
            if (row < r) { double d = r - row - 0.5; ind = (int)Math.ceil(r - 0.5 - Math.sqrt(r*r - d*d)); }
            else if (row >= h - r) { double d = row-(h-r)+0.5; ind = (int)Math.ceil(r - 0.5 - Math.sqrt(r*r - d*d)); }
            g.fill(x+ind, y+row, x+w-ind, y+row+1, color);
        }
    }


    static class Panel {
        String title; int x, y; boolean collapsed = true; int scrollOffset = 0;
        final List<Entry> entries = new ArrayList<>();
        Panel(String title, int x, int y) { this.title = title; this.x = x; this.y = y; }
        void add(Entry e) { entries.add(e); }
        int contentHeight() { return entries.size() * (ENTRY_H + ENTRY_PAD) + ENTRY_PAD; }
        int visibleHeight()  { return 126; }
        int maxScroll()      { return Math.max(0, contentHeight() - visibleHeight()); }
        boolean headerContains(int mx, int my) { return mx >= x && mx <= x + PANEL_W && my >= y && my <= y + HEADER_H; }
        boolean contains(int mx, int my) {
            if (collapsed) return false;
            return mx >= x && mx <= x + PANEL_W && my >= y + HEADER_H && my <= y + HEADER_H + Math.min(contentHeight(), visibleHeight());
        }
        Entry entryAt(int mx, int my) {
            if (collapsed) return null;
            int ey = y + HEADER_H + ENTRY_PAD - scrollOffset;
            for (Entry e : entries) { if (my >= ey && my < ey + ENTRY_H && mx >= x && mx <= x + PANEL_W) return e; ey += ENTRY_H + ENTRY_PAD; }
            return null;
        }
        void scroll(int dir) { scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset + dir * 10)); }

        void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            int totalH = collapsed ? HEADER_H : HEADER_H + Math.min(contentHeight(), visibleHeight());
            fillRoundRect(g, x, y, PANEL_W, totalH, PANEL_RADIUS, C_BG());
            fillRoundRect(g, x, y, PANEL_W, HEADER_H, PANEL_RADIUS, C_HDR());
            if (!collapsed) g.fill(x, y + HEADER_H - PANEL_RADIUS, x + PANEL_W, y + HEADER_H, C_HDR());
            g.fill(x + 2, y + HEADER_H - 1, x + PANEL_W - 2, y + HEADER_H, C_LINE());
            g.drawString(font, title, x + 5, y + HEADER_H / 2 - 4, C_TXT(), false);
            g.drawString(font, collapsed ? ">" : "v", x + PANEL_W - 10, y + HEADER_H / 2 - 4, C_DIM(), false);
            if (collapsed) return;

            int clipY = y + HEADER_H, clipH = Math.min(contentHeight(), visibleHeight());
            g.enableScissor(x, clipY, x + PANEL_W, clipY + clipH);
            int ey = clipY + ENTRY_PAD - scrollOffset;
            for (Entry e : entries) {
                boolean hov = mx >= x && mx <= x + PANEL_W && my >= ey && my < ey + ENTRY_H;
                if (hov) g.fill(x + 1, ey, x + PANEL_W - 1, ey + ENTRY_H, C_HOVER());
                e.render(g, x + ENTRY_PAD, ey, PANEL_W - ENTRY_PAD * 2, ENTRY_H, hov, font);
                ey += ENTRY_H + ENTRY_PAD;
            }
            g.disableScissor();
            if (maxScroll() > 0) {
                float frac = (float) scrollOffset / maxScroll();
                int bh = Math.max(10, clipH * clipH / contentHeight());
                int by = clipY + (int)((clipH - bh) * frac);
                g.fill(x + PANEL_W - 3, by, x + PANEL_W - 1, by + bh, C_ACC());
            }
        }
    }


    interface Entry {
        void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font);
        void onClick(int mx, int my);
        default SubPanel openSubPanel(int mx, int my, int sw, int sh) { return null; }
    }

    static class ToggleEntry implements Entry {
        final String label; final Supplier<Boolean> getter; final Consumer<Boolean> setter;
        ToggleEntry(String l, Supplier<Boolean> g, Consumer<Boolean> s) { label=l; getter=g; setter=s; }
        @Override public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            boolean on = getter.get(); int mid = y + h / 2;
            int dw=12, dh=7, dx=x+w-dw-2, dy=mid-dh/2;
            fillRoundRect(g, dx, dy, dw, dh, 3, on ? C_ON() : C_OFF());
            if (on) fillRoundRect(g, dx+5, dy+1, 5, 5, 2, C_ON2());
            g.drawString(font, label, x+2, mid-4, hov ? C_TXT() : C_DIM(), false);
        }
        @Override public void onClick(int mx, int my) { setter.accept(!getter.get()); }
    }

    static class SliderEntry implements Entry {
        final String label; final int min, max; final Supplier<Integer> getter; final Consumer<Integer> setter; final String unit;
        SliderEntry(String l, int mn, int mx, Supplier<Integer> g, Consumer<Integer> s, String u) { label=l; min=mn; max=mx; getter=g; setter=s; unit=u; }
        @Override public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            int val = getter.get(); String vs = val+unit; int vw = font.width(vs);
            g.drawString(font, label, x+2, y+2, hov ? C_TXT() : C_DIM(), false);
            g.drawString(font, vs, x+w-vw-2, y+2, C_ON2(), false);
            int by = y+h-5, bh=3;
            fillRoundRect(g, x+2, by, w-4, bh, 1, C_SBGR());
            float frac = (float)(val-min)/(max-min);
            int fw = (int)(frac*(w-4));
            if (fw > 0) fillRoundRect(g, x+2, by, fw, bh, 1, C_SFILL());
            int kx = x+2+fw-2;
            g.fill(Math.max(x+2,kx), by-1, Math.max(x+2,kx)+4, by+bh+1, C_SKNOB());
        }
        void onDrag(int mx, int barX, int barW) { float f=Math.max(0f,Math.min(1f,(float)(mx-barX)/barW)); setter.accept(min+Math.round(f*(max-min))); }
        @Override public void onClick(int mx, int my) {}
        @Override public SubPanel openSubPanel(int mx, int my, int sw, int sh) { return new IntInputSubPanel(mx, my, sw, sh, label, getter.get(), min, max, v -> { setter.accept(v); save(); }); }
    }

    static class CycleEnumEntry<E extends Enum<E>> implements Entry {
        final String label; final E[] values; final Supplier<E> getter; final Consumer<E> setter;
        CycleEnumEntry(String l, E[] vs, Supplier<E> g, Consumer<E> s) { label=l; values=vs; getter=g; setter=s; }
        @Override public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            String val=getter.get().name(); int vw=font.width(val); int mid=y+h/2-4;
            g.drawString(font, label, x+2, mid, hov ? C_TXT() : C_DIM(), false);
            g.drawString(font, val, x+w-vw-2, mid, C_ON2(), false);
        }
        @Override public void onClick(int mx, int my) { E cur=getter.get(); setter.accept(values[(cur.ordinal()+1)%values.length]); }
    }

    static class TextSettingEntry implements Entry {
        final String label; final Supplier<String> getter; final Consumer<String> setter; final String placeholder;
        TextSettingEntry(String l, Supplier<String> g, Consumer<String> s) { this(l, g, s, ""); }
        TextSettingEntry(String l, Supplier<String> g, Consumer<String> s, String p) { label=l; getter=g; setter=s; placeholder=p; }
        @Override public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            String val=getter.get(); if(val.length()>11) val=val.substring(0,9)+".."; int vw=font.width(val); int mid=y+h/2-4;
            g.drawString(font, label, x+2, mid, hov ? C_TXT() : C_DIM(), false);
            g.drawString(font, val, x+w-vw-6, mid, C_DIM(), false);
        }
        @Override public void onClick(int mx, int my) {}
        @Override public SubPanel openSubPanel(int mx, int my, int sw, int sh) { return new StringInputSubPanel(mx, my, sw, sh, label, getter.get(), setter, placeholder); }
    }

    static class ListSettingEntry implements Entry {
        final String label; final Supplier<List<String>> getter; final Consumer<List<String>> setter; final String[] hints;
        ListSettingEntry(String l, Supplier<List<String>> g, Consumer<List<String>> s, String... hints) { label=l; getter=g; setter=s; this.hints = hints == null ? new String[0] : hints; }
        @Override public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            List<String> values = getter.get();
            String summary;
            if (values == null || values.isEmpty()) summary = "(empty)";
            else if (values.size() == 1) summary = values.get(0);
            else summary = values.get(0) + " +" + (values.size() - 1);
            if (summary.length() > 11) summary = summary.substring(0, 9) + "..";
            int vw = font.width(summary);
            int mid = y + h / 2 - 4;
            g.drawString(font, label, x + 2, mid, hov ? C_TXT() : C_DIM(), false);
            g.drawString(font, summary, x + w - vw - 6, mid, C_DIM(), false);
        }
        @Override public void onClick(int mx, int my) {}
        @Override public SubPanel openSubPanel(int mx, int my, int sw, int sh) {
            return new ListInputSubPanel(mx, my, sw, sh, label, getter.get(), setter, hints);
        }
    }

    static class IntFieldEntry implements Entry {
        final String label; final Supplier<Integer> getter; final Consumer<Integer> setter; final String unit;
        IntFieldEntry(String l, Supplier<Integer> g, Consumer<Integer> s, String u) { label=l; getter=g; setter=s; unit=u; }
        @Override public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            String val=getter.get()+" "+unit; int vw=font.width(val); int mid=y+h/2-4;
            g.drawString(font, label, x+2, mid, hov ? C_TXT() : C_DIM(), false);
            g.drawString(font, val, x+w-vw-2, mid, C_ON2(), false);
        }
        @Override public void onClick(int mx, int my) {}
        @Override public SubPanel openSubPanel(int mx, int my, int sw, int sh) { return new IntInputSubPanel(mx, my, sw, sh, label, getter.get(), Integer.MIN_VALUE, Integer.MAX_VALUE, v -> { setter.accept(v); save(); }); }
    }

    static class ButtonEntry implements Entry {
        final String label; final Runnable action;
        ButtonEntry(String l, Runnable a) { label=l; action=a; }
        @Override public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            fillRoundRect(g, x+2, y+2, w-4, h-4, 3, hov ? C_BTN() : C_OFF());
            int tw=font.width(label);
            g.drawString(font, label, x+(w-tw)/2, y+h/2-4, C_TXT(), false);
        }
        @Override public void onClick(int mx, int my) { action.run(); }
    }

    // color swatch entry — shows label + color preview, right-click opens RGB sliders
    static class ColorEntry implements Entry {
        final String label; final Supplier<Integer> getter; final Consumer<Integer> setter;
        ColorEntry(String l, Supplier<Integer> g, Consumer<Integer> s) { label=l; getter=g; setter=s; }
        @Override public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            int col = getter.get(); int mid = y+h/2-4;
            g.drawString(font, label, x+2, mid, hov ? C_TXT() : C_DIM(), false);
            // color swatch
            int sw=16, sx=x+w-sw-2, sy=y+2;
            g.fill(sx-1, sy-1, sx+sw+1, sy+h-2, 0xFF555555);
            g.fill(sx, sy, sx+sw, sy+h-4, col | 0xFF000000);
            // hex label
            String hex = String.format("#%06X", col & 0xFFFFFF);
            int hw = font.width(hex);
            g.drawString(font, hex, x+w-sw-hw-6, mid, C_DIM(), false);
        }
        @Override public void onClick(int mx, int my) {}
        @Override public SubPanel openSubPanel(int mx, int my, int sw, int sh) {
            return new ColorSubPanel(mx, my, sw, sh, label, getter.get(), setter);
        }
    }

    // "Paste Theme Code" entry — text field + apply button inline
    static class ImportCodeEntry implements Entry {
        @Override public void render(GuiGraphics g, int x, int y, int w, int h, boolean hov, net.minecraft.client.gui.Font font) {
            fillRoundRect(g, x+2, y+2, w-4, h-4, 3, hov ? C_BTN() : C_OFF());
            String lbl = "Paste Theme Code";
            int tw = font.width(lbl);
            g.drawString(font, lbl, x+(w-tw)/2, y+h/2-4, C_TXT(), false);
        }
        @Override public void onClick(int mx, int my) {}
        @Override public SubPanel openSubPanel(int mx, int my, int sw, int sh) {
            return new StringInputSubPanel(mx, my, sw, sh, "Paste Theme Code", "", v -> {
                if (applyThemeCode(v)) { Minecraft.getInstance().player.displayClientMessage(Component.literal("Theme applied!"), true); }
                else { Minecraft.getInstance().player.displayClientMessage(Component.literal("Invalid theme code!"), true); }
            });
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
        boolean cursorVisible = true; long lastBlink = System.currentTimeMillis();

        ColorSubPanel(int mx, int my, int sw, int sh, String label, int initial, Consumer<Integer> setter) {
            this.label = label; this.setter = setter;
            a = (initial >> 24) & 0xFF; r = (initial >> 16) & 0xFF; g = (initial >> 8) & 0xFF; b = initial & 0xFF;
            if (a == 0) a = 255;
            this.x = Math.min(mx, sw - w - 4); this.y = Math.min(my, sh - h - 4);
        }

        int packed() { return (a << 24) | (r << 16) | (g << 8) | b; }

        int getChannel(int i) { return i==0?r:i==1?g:i==2?b:a; }
        void setChannel(int i, int v) { if(i==0)r=v; else if(i==1)g=v; else if(i==2)b=v; else a=v; }

        int sliderBx() { return x + 16; }
        int sliderBw() { return w - 50; }
        int sliderSy(int i) { return y + 32 + i * 18; }

        void applyDrag(int mx) {
            if (draggingSlider < 0) return;
            int bx = sliderBx(), bw2 = sliderBw();
            int val = Math.max(0, Math.min(255, Math.round((mx - bx) / (float) bw2 * 255)));
            setChannel(draggingSlider, val);
            setter.accept(packed()); save();
        }

        @Override public void render(GuiGraphics g2, int mx, int my, net.minecraft.client.gui.Font font) {
            fillRoundRect(g2, x-2, y-2, w+4, h+4, 4, C_SPBD());
            fillRoundRect(g2, x, y, w, h, 3, C_SPBG());

            // label
            g2.drawString(font, label, x+4, y+4, C_TXT(), false);

            // swatch
            int sw2=20; int sx=x+w-sw2-4, sy0=y+4;
            g2.fill(sx-1,sy0-1,sx+sw2+1,sy0+sw2+1,0xFF555555);
            g2.fill(sx,sy0,sx+sw2,sy0+sw2, packed());

            // hex field — clickable to edit
            if (System.currentTimeMillis()-lastBlink>500) { cursorVisible=!cursorVisible; lastBlink=System.currentTimeMillis(); }
            String hexDisplay = editingHex ? hexBuffer+(cursorVisible?"|":"") : String.format("#%08X", packed());
            int hexColor = editingHex ? C_TXT() : C_DIM();
            g2.fill(x+4, y+15, x+w-sw2-10, y+27, C_SBGR());
            g2.fill(x+4, y+27, x+w-sw2-10, y+28, editingHex ? C_ACC() : 0xFF333355);
            g2.drawString(font, hexDisplay, x+6, y+17, hexColor, false);
            // small hint
            if (!editingHex) g2.drawString(font, "click hex to edit", x+w-sw2-9-font.width("click hex to edit"), y+18, C_DIM(), false);

            // R/G/B/A sliders
            int[] cols = {0xFFCC4444, 0xFF44CC44, 0xFF4444CC, 0xFFAAAAAA};
            String[] names = {"R","G","B","A"};
            int bx = sliderBx(), bw2 = sliderBw();
            for (int i = 0; i < 4; i++) {
                int val = getChannel(i);
                int sy = sliderSy(i);
                g2.drawString(font, names[i], x+4, sy+1, cols[i], false);
                fillRoundRect(g2, bx, sy+3, bw2, 6, 2, C_SBGR());
                int fw = (int)(val / 255f * bw2);
                if (fw > 0) fillRoundRect(g2, bx, sy+3, fw, 6, 2, cols[i]);
                boolean hov = draggingSlider == i || (mx >= bx && mx <= bx+bw2 && my >= sy && my <= sy+10);
                int kx = bx + fw - 3;
                g2.fill(Math.max(bx,kx), sy+1, Math.max(bx,kx)+6, sy+9, hov ? 0xFFFFFFFF : 0xFFCCCCCC);
                g2.drawString(font, String.valueOf(val), bx+bw2+4, sy+1, cols[i], false);
            }
        }

        @Override public boolean contains(int mx, int my) { return mx >= x-2 && mx <= x+w+2 && my >= y-2 && my <= y+h+2; }

        @Override public boolean mouseClicked(int mx, int my, int btn, net.minecraft.client.gui.Font font) {
            // click on hex field
            int sw2=20;
            if (mx >= x+4 && mx <= x+w-sw2-10 && my >= y+15 && my <= y+28) {
                editingHex = !editingHex;
                if (editingHex) hexBuffer = String.format("%08X", packed());
                return true;
            }
            editingHex = false;
            // click on slider
            int bx = sliderBx(), bw2 = sliderBw();
            for (int i = 0; i < 4; i++) {
                int sy = sliderSy(i);
                if (my >= sy && my <= sy+10 && mx >= bx && mx <= bx+bw2) {
                    draggingSlider = i;
                    applyDrag(mx);
                    return true;
                }
            }
            return true;
        }

        // called from handleMouseDragged via activeSubPanel
        public void drag(int mx) { applyDrag(mx); }

        @Override public void scroll(int dir) {}

        @Override public boolean keyPressed(int key, int scan, int mods) {
            if (editingHex) {
                if (key == 259 && !hexBuffer.isEmpty()) { hexBuffer = hexBuffer.substring(0, hexBuffer.length()-1); return true; }
                if (key == 257 || key == 335) { applyHex(); editingHex = false; return true; }
                if (key == 256) { editingHex = false; return true; }
                if (key == 86 && (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
                    String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                    if (clip != null) hexBuffer += clip.replaceAll("[^0-9a-fA-F#]","");
                    return true;
                }
                // GLFW fallback for typing since charTyped may not fire
                Character c = fallbackCharFromKey(key, scan, mods);
                if (c != null && (Character.isDigit(c) || (c>='a'&&c<='f') || (c>='A'&&c<='F'))) {
                    if (hexBuffer.length() < 8) hexBuffer += c;
                    return true;
                }
                return true; // swallow all keys while editing hex
            }
            return false;
        }

        @Override public boolean charTyped(char c, int mods) {
            if (editingHex && (Character.isDigit(c)||(c>='a'&&c<='f')||(c>='A'&&c<='F'))) {
                if (hexBuffer.length() < 8) hexBuffer += c;
                return true;
            }
            return false;
        }

        void applyHex() {
            try {
                String h = hexBuffer.replaceAll("#","");
                long v = Long.parseLong(h, 16);
                if (h.length() <= 6) { r=(int)((v>>16)&0xFF); g=(int)((v>>8)&0xFF); b=(int)(v&0xFF); }
                else { a=(int)((v>>24)&0xFF); r=(int)((v>>16)&0xFF); g=(int)((v>>8)&0xFF); b=(int)(v&0xFF); }
                setter.accept(packed()); save();
            } catch (Exception ignored) {}
        }

        @Override public void commit() { setter.accept(packed()); save(); }
    }

    static class StringInputSubPanel implements SubPanel {
        final String label; String value; final Consumer<String> setter;
        final String placeholder;
        final int x, y, w = 300, h = 50;
        boolean cursorVisible = true; long lastBlink = System.currentTimeMillis();
        StringInputSubPanel(int mx, int my, int sw, int sh, String label, String initial, Consumer<String> setter) {
            this(mx, my, sw, sh, label, initial, setter, "");
        }
        StringInputSubPanel(int mx, int my, int sw, int sh, String label, String initial, Consumer<String> setter, String placeholder) {
            this.label=label; this.value=initial; this.setter=setter;
            this.placeholder = placeholder == null ? "" : placeholder;
            this.x=Math.min(mx, sw - w - 4); this.y=Math.min(my, sh - h - 4);
        }
        @Override public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            fillRoundRect(g, x-2, y-2, w+4, h+4, 4, C_SPBD());
            fillRoundRect(g, x, y, w, h, 3, C_SPBG());
            g.drawString(font, label, x+5, y+6, C_TXT(), false);
            g.fill(x+4, y+20, x+w-4, y+42, C_SBGR());
            g.fill(x+4, y+42, x+w-4, y+43, C_ACC());
            if (System.currentTimeMillis()-lastBlink>500) { cursorVisible=!cursorVisible; lastBlink=System.currentTimeMillis(); }
            if (value.isEmpty() && !placeholder.isEmpty()) {
                g.drawString(font, placeholder, x+6, y+26, C_DIM(), false);
            } else {
                String disp = value.length() > 38 ? value.substring(value.length()-38) : value;
                g.drawString(font, disp+(cursorVisible?"|":""), x+6, y+26, C_TXT(), false);
            }
        }
        @Override public boolean contains(int mx, int my) { return mx>=x-2&&mx<=x+w+2&&my>=y-2&&my<=y+h+2; }
        @Override public boolean mouseClicked(int mx, int my, int btn, net.minecraft.client.gui.Font font) { return true; }
        @Override public void scroll(int dir) {}
        @Override public boolean keyPressed(int key, int scan, int mods) {
            if (key==259 && !value.isEmpty()) { value=value.substring(0,value.length()-1); return true; }
            if (key==257 || key==335) { commit(); return true; }
            if (key==86 && (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null) value += clip;
                return true;
            }
            Character c = fallbackCharFromKey(key, scan, mods);
            if (c != null) { value += c; return true; }
            return false;
        }
        @Override public boolean charTyped(char c, int mods) { value+=c; return true; }
        @Override public void commit() { setter.accept(value); }
    }

    static class IntInputSubPanel implements SubPanel {
        final String label; String raw; final int min, max; final Consumer<Integer> setter;
        final int x, y, w = 220, h = 50;
        boolean cursorVisible = true; long lastBlink = System.currentTimeMillis();
        IntInputSubPanel(int mx, int my, int sw, int sh, String label, int initial, int min, int max, Consumer<Integer> setter) {
            this.label=label; this.raw=String.valueOf(initial); this.min=min; this.max=max; this.setter=setter;
            this.x=Math.min(mx,sw-w-4); this.y=Math.min(my,sh-h-4);
        }
        @Override public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            fillRoundRect(g, x-2, y-2, w+4, h+4, 4, C_SPBD());
            fillRoundRect(g, x, y, w, h, 3, C_SPBG());
            g.drawString(font, label, x+5, y+6, C_TXT(), false);
            String range=min==Integer.MIN_VALUE?"":"["+min+"-"+max+"]"; int rw=font.width(range);
            g.drawString(font, range, x+w-rw-5, y+6, C_DIM(), false);
            g.fill(x+4, y+20, x+w-4, y+42, C_SBGR());
            g.fill(x+4, y+42, x+w-4, y+43, C_ACC());
            if (System.currentTimeMillis()-lastBlink>500) { cursorVisible=!cursorVisible; lastBlink=System.currentTimeMillis(); }
            g.drawString(font, raw+(cursorVisible?"|":""), x+6, y+26, C_TXT(), false);
        }
        @Override public boolean contains(int mx, int my) { return mx>=x-2&&mx<=x+w+2&&my>=y-2&&my<=y+h+2; }
        @Override public boolean mouseClicked(int mx, int my, int btn, net.minecraft.client.gui.Font font) { return true; }
        @Override public void scroll(int dir) {}
        @Override public boolean keyPressed(int key, int scan, int mods) {
            if (key==259&&!raw.isEmpty()) { raw=raw.substring(0,raw.length()-1); return true; }
            if (key==257||key==335) { commit(); return true; }
            if ((mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) == 0) {
                String name = org.lwjgl.glfw.GLFW.glfwGetKeyName(key, scan);
                if (name != null && name.length() == 1) {
                    char c = name.charAt(0);
                    if (Character.isDigit(c) || (c == '-' && raw.isEmpty())) { raw += c; return true; }
                }
            }
            return false;
        }
        @Override public boolean charTyped(char c, int mods) {
            if (Character.isDigit(c)||(c=='-'&&raw.isEmpty())) { raw+=c; return true; }
            return false;
        }
        @Override public void commit() {
            try { int v=Integer.parseInt(raw); setter.accept(min==Integer.MIN_VALUE?v:Math.max(min,Math.min(max,v))); }
            catch (NumberFormatException ignored) {}
        }
    }

    static class ListInputSubPanel implements SubPanel {
        final String label;
        final StringBuilder value;
        final Consumer<List<String>> setter;
        final String[] hints;
        final int x, y, w = 300, h = 122;
        boolean cursorVisible = true;
        long lastBlink = System.currentTimeMillis();
        int cursorIndex;
        int scrollLine = 0;

        ListInputSubPanel(int mx, int my, int sw, int sh, String label, List<String> initial, Consumer<List<String>> setter, String... hints) {
            this.label = label;
            this.setter = setter;
            this.hints = hints == null ? new String[0] : hints;
            this.value = new StringBuilder(initial == null ? "" : String.join("\n", initial));
            this.cursorIndex = this.value.length();
            this.x = Math.min(mx, sw - w - 4);
            this.y = Math.min(my, sh - h - 4);
        }

        private int textLeft() { return x + 6; }
        private int textTop() { return y + 22 + hints.length * 10; }
        private int textRight() { return x + w - 6; }
        private int textBottom() { return y + h - 8; }
        private int lineStep(net.minecraft.client.gui.Font font) { return font.lineHeight + 1; }

        private List<String> lines() {
            return java.util.Arrays.asList(value.toString().split("\n", -1));
        }

        private int cursorLine() {
            int line = 0;
            for (int i = 0; i < Math.min(cursorIndex, value.length()); i++) {
                if (value.charAt(i) == '\n') line++;
            }
            return line;
        }

        private int cursorColumn() {
            int col = 0;
            for (int i = Math.min(cursorIndex, value.length()) - 1; i >= 0; i--) {
                if (value.charAt(i) == '\n') break;
                col++;
            }
            return col;
        }

        private int lineStartIndex(int targetLine) {
            int line = 0;
            for (int i = 0; i < value.length(); i++) {
                if (line == targetLine) return i;
                if (value.charAt(i) == '\n') line++;
            }
            return line == targetLine ? value.length() : value.length();
        }

        private int lineEndIndex(int startIndex) {
            int idx = value.indexOf("\n", startIndex);
            return idx == -1 ? value.length() : idx;
        }

        private int visibleLineCount(net.minecraft.client.gui.Font font) {
            return Math.max(1, (textBottom() - textTop()) / lineStep(font));
        }

        private int maxScrollLine(net.minecraft.client.gui.Font font) {
            return Math.max(0, lines().size() - visibleLineCount(font));
        }

        private void clampScroll(net.minecraft.client.gui.Font font) {
            scrollLine = Math.max(0, Math.min(maxScrollLine(font), scrollLine));
        }

        private void ensureCursorVisible(net.minecraft.client.gui.Font font) {
            int cursorLine = cursorLine();
            int visible = visibleLineCount(font);
            if (cursorLine < scrollLine) scrollLine = cursorLine;
            else if (cursorLine >= scrollLine + visible) scrollLine = cursorLine - visible + 1;
            clampScroll(font);
        }

        private void moveCursorHorizontal(int delta) {
            cursorIndex = Math.max(0, Math.min(value.length(), cursorIndex + delta));
        }

        private void moveCursorVertical(int delta) {
            int currentLine = cursorLine();
            int targetLine = Math.max(0, currentLine + delta);
            int targetCol = cursorColumn();
            int targetStart = lineStartIndex(targetLine);
            int targetEnd = lineEndIndex(targetStart);
            cursorIndex = Math.min(targetStart + targetCol, targetEnd);
        }

        private void insertText(String text) {
            if (text == null || text.isEmpty()) return;
            value.insert(cursorIndex, text);
            cursorIndex += text.length();
        }

        @Override public void render(GuiGraphics g, int mx, int my, net.minecraft.client.gui.Font font) {
            fillRoundRect(g, x-2, y-2, w+4, h+4, 4, C_SPBD());
            fillRoundRect(g, x, y, w, h, 3, C_SPBG());
            g.drawString(font, label, x+5, y+6, C_TXT(), false);
            int hintY = y + 16;
            for (String hint : hints) {
                g.drawString(font, hint, x + 6, hintY, C_DIM(), false);
                hintY += 10;
            }
            g.fill(x+4, y+18 + hints.length * 10, x+w-4, y+h-6, C_SBGR());
            g.fill(x+4, y+h-6, x+w-4, y+h-5, C_ACC());
            if (System.currentTimeMillis() - lastBlink > 500) { cursorVisible = !cursorVisible; lastBlink = System.currentTimeMillis(); }

            clampScroll(font);
            List<String> lines = lines();
            int step = lineStep(font);
            int drawY = textTop();
            int visible = visibleLineCount(font);
            int caretLine = cursorLine();
            int caretColumn = cursorColumn();

            for (int lineIndex = scrollLine; lineIndex < lines.size() && lineIndex < scrollLine + visible; lineIndex++) {
                String line = lines.get(lineIndex);
                g.drawString(font, line, textLeft(), drawY, C_TXT(), false);
                if (cursorVisible && lineIndex == caretLine) {
                    int caretX = textLeft() + font.width(line.substring(0, Math.min(caretColumn, line.length())));
                    g.fill(caretX, drawY - 1, caretX + 1, drawY + font.lineHeight, C_TXT());
                }
                drawY += step;
            }

            if (maxScrollLine(font) > 0) {
                int trackX0 = x + w - 5;
                int trackX1 = x + w - 3;
                int trackY0 = y + 20 + hints.length * 10;
                int trackY1 = y + h - 8;
                int trackH = trackY1 - trackY0;
                int totalLines = lines.size();
                int thumbH = Math.max(10, trackH * visible / totalLines);
                int travel = Math.max(0, trackH - thumbH);
                int thumbY = trackY0 + (maxScrollLine(font) == 0 ? 0 : (travel * scrollLine / maxScrollLine(font)));
                g.fill(trackX0, trackY0, trackX1, trackY1, C_OFF());
                g.fill(trackX0, thumbY, trackX1, thumbY + thumbH, C_ACC());
            }
        }

        @Override public boolean contains(int mx, int my) { return mx >= x-2 && mx <= x+w+2 && my >= y-2 && my <= y+h+2; }
        @Override public boolean mouseClicked(int mx, int my, int btn, net.minecraft.client.gui.Font font) {
            if (mx >= x + 4 && mx <= x + w - 4 && my >= y + 18 + hints.length * 10 && my <= y + h - 6) {
                int step = lineStep(font);
                clampScroll(font);
                int line = scrollLine + Math.max(0, (my - textTop()) / Math.max(1, step));
                List<String> lines = lines();
                line = Math.max(0, Math.min(lines.size() - 1, line));
                String textLine = lines.get(line);
                int clickedX = Math.max(textLeft(), Math.min(textRight(), mx));
                int column = 0;
                for (int i = 1; i <= textLine.length(); i++) {
                    if (textLeft() + font.width(textLine.substring(0, i)) > clickedX) break;
                    column = i;
                }
                int lineStart = lineStartIndex(line);
                cursorIndex = Math.min(lineStart + column, lineStart + textLine.length());
                ensureCursorVisible(font);
            }
            return true;
        }
        @Override public void scroll(int dir) {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            scrollLine += dir;
            clampScroll(font);
        }

        @Override public boolean keyPressed(int key, int scan, int mods) {
            net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
            if (key == GLFW.GLFW_KEY_SPACE) {
                insertText(" ");
                ensureCursorVisible(font);
                return true;
            }
            if (key == 259 && value.length() > 0) {
                if (cursorIndex > 0) {
                    value.deleteCharAt(cursorIndex - 1);
                    cursorIndex--;
                    ensureCursorVisible(font);
                }
                return true;
            }
            if (key == 261 && cursorIndex < value.length()) {
                value.deleteCharAt(cursorIndex);
                ensureCursorVisible(font);
                return true;
            }
            if (key == 257 || key == 335) {
                insertText("\n");
                ensureCursorVisible(font);
                return true;
            }
            if (key == GLFW.GLFW_KEY_LEFT) {
                moveCursorHorizontal(-1);
                ensureCursorVisible(font);
                return true;
            }
            if (key == GLFW.GLFW_KEY_RIGHT) {
                moveCursorHorizontal(1);
                ensureCursorVisible(font);
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP) {
                moveCursorVertical(-1);
                ensureCursorVisible(font);
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN) {
                moveCursorVertical(1);
                ensureCursorVisible(font);
                return true;
            }
            if (key == 86 && (mods & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
                String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                if (clip != null) insertText(clip.replace("\r", ""));
                ensureCursorVisible(font);
                return true;
            }
            Character c = fallbackCharFromKey(key, scan, mods);
            if (c != null) {
                insertText(String.valueOf(c));
                ensureCursorVisible(font);
                return true;
            }
            return false;
        }

        @Override public boolean charTyped(char c, int mods) {
            if (c == '\r') return true;
            insertText(String.valueOf(c));
            ensureCursorVisible(Minecraft.getInstance().font);
            return true;
        }

        @Override public void commit() {
            List<String> values = new ArrayList<>();
            for (String line : value.toString().split("\\R")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) values.add(trimmed);
            }
            setter.accept(values);
        }
    }
}
