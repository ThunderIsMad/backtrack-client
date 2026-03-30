package com.yourname.backtrack.gui;

import com.yourname.backtrack.config.ConfigManager;
import com.yourname.backtrack.hud.HudSettings;
import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.module.ModuleManager;
import com.yourname.backtrack.module.impl.BacktrackModule;
import com.yourname.backtrack.setting.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ClickGuiScreen extends GuiScreen {

    private final ModuleManager moduleManager;
    private final ConfigManager configManager;
    private final HudSettings    hudSettings;
    private final GuiTheme       guiTheme;

    private static final int PANEL_WIDTH      = 120;
    private static final int HEADER_H         = 14;
    private static final int MODULE_H         = 13;
    private static final int ROW_GAP          = 1;
    private static final int PANEL_GAP        = 6;
    private static final int SETTINGS_W       = 170;
    private static final int SETTINGS_ITEM_H  = 13;

    private final Map<Category, PanelState> panels = new LinkedHashMap<>();

    private int startX;
    private int startY;
    private boolean draggingAll;
    private int dragOffsetX, dragOffsetY;

    private Module   selectedModule;
    private boolean  waitingForBind;
    private boolean  settingsPanelOpen;
    private float    settingsOpenAnim;
    private int      settingsScrollOffset;
    private float    settingsScrollVisual;


    private BacktrackSubPanel backtrackSubPanel = BacktrackSubPanel.NONE;
    private long subPanelTransitionStart;

    private boolean typingSearch;
    private String  searchQuery = "";

    private final long openTime;

    public ClickGuiScreen(ModuleManager moduleManager,
                          ConfigManager configManager,
                          HudSettings    hudSettings,
                          GuiTheme       guiTheme) {
        this.moduleManager = moduleManager;
        this.configManager = configManager;
        this.hudSettings   = hudSettings;
        this.guiTheme      = guiTheme;
        this.openTime      = System.currentTimeMillis();

        for (Category cat : Category.values()) {
            if (cat == Category.HUD) continue;
            panels.put(cat, new PanelState());
        }

        this.startX = configManager.loadGuiX();
        this.startY = configManager.loadGuiY();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        updateAnimations();

        float openProg = getOpenProgress();

                int x = startX;
        for (Map.Entry<Category, PanelState> entry : panels.entrySet()) {
            Category   cat   = entry.getKey();
            PanelState state = entry.getValue();
            x = drawCategoryPanel(cat, state, x, mouseX, mouseY, openProg);
            x += PANEL_GAP;
        }

                if (settingsPanelOpen || settingsOpenAnim > 0.01f) {
            drawSettingsPanel(mouseX, mouseY, openProg);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }


    private int drawCategoryPanel(Category cat, PanelState state,
                                  int x, int mouseX, int mouseY,
                                  float openProg) {
                float expand = state.expandAnim; // 0..1

                boolean headerHov = isHovered(x, startY, PANEL_WIDTH, HEADER_H, mouseX, mouseY);
        int headerBg = headerHov
                ? guiTheme.getPanelHoverColor()
                : guiTheme.getTitleColor();
        Gui.drawRect(x, startY, x + PANEL_WIDTH, startY + HEADER_H,
                animated(headerBg, openProg));

                if (state.open || expand > 0.01f) {
            Gui.drawRect(x, startY, x + 2, startY + HEADER_H,
                    animated(guiTheme.getAccentHoverColor(), openProg));
        }

                String arrow = (state.open || expand > 0.5f) ? "v" : ">";
        fontRenderer.drawStringWithShadow(arrow,
                x + PANEL_WIDTH - 9, startY + 3,
                animated(guiTheme.getTextMutedColor(), openProg));

                fontRenderer.drawStringWithShadow(cat.name(),
                x + 5, startY + 3,
                animated(guiTheme.getTextPrimaryColor(), openProg));

        if (expand < 0.01f) return x + PANEL_WIDTH;

        List<Module> modules = getModulesForCategory(cat);
        int contentH = modules.size() * (MODULE_H + ROW_GAP);
        int visibleH  = Math.round(contentH * expand);

        Gui.drawRect(x, startY + HEADER_H,
                x + PANEL_WIDTH, startY + HEADER_H + visibleH,
                animated(guiTheme.getPanelColor(), openProg));

        drawOutline(x, startY, PANEL_WIDTH, HEADER_H + visibleH,
                animated(guiTheme.withAlpha(guiTheme.getStrokeColor(), 80), openProg));

        int scrollY = startY + HEADER_H - Math.round(state.scrollVisual);
        int rowIdx  = 0;

        for (Module module : modules) {
            int rowY = scrollY + rowIdx * (MODULE_H + ROW_GAP);

            if (rowY + MODULE_H < startY + HEADER_H) { rowIdx++; continue; }
            if (rowY > startY + HEADER_H + visibleH)  break;

            boolean hov  = isHovered(x, rowY, PANEL_WIDTH, MODULE_H, mouseX, mouseY)
                    && mouseY >= startY + HEADER_H
                    && mouseY <= startY + HEADER_H + visibleH;
            boolean sel  = module == selectedModule;
            boolean enab = module.isEnabled();

            int rowBg = hov  ? guiTheme.getPanelHoverColor()
                    : sel  ? guiTheme.withAlpha(guiTheme.getAccentColor(), 35)
                      : getRowAlt(rowIdx);
            Gui.drawRect(x, rowY, x + PANEL_WIDTH, rowY + MODULE_H,
                    animated(rowBg, openProg));

            if (sel) {
                Gui.drawRect(x, rowY, x + 2, rowY + MODULE_H,
                        animated(guiTheme.getAccentHoverColor(), openProg));
            }

            int dotColor = enab ? guiTheme.getToggleOnColor() : guiTheme.getToggleOffColor();
            Gui.drawRect(x + 5, rowY + 4, x + 8, rowY + 8,
                    animated(dotColor, openProg));

            int textColor = enab ? guiTheme.getAccentTextColor() : guiTheme.getTextSecondaryColor();
            if (sel) textColor = guiTheme.getTextPrimaryColor();
            fontRenderer.drawStringWithShadow(module.getName(),
                    x + 12, rowY + 3, animated(textColor, openProg));

            rowIdx++;
        }

        return x + PANEL_WIDTH;
    }

    private int getSettingsPanelX() {
        int x = startX;
        for (PanelState s : panels.values()) {
            x += PANEL_WIDTH + PANEL_GAP;
        }
        return x + 4;
    }

    private void drawSettingsPanel(int mouseX, int mouseY, float openProg) {
        float anim    = settingsOpenAnim;          // 0..1
        int   slideOff = Math.round((1f - anim) * 8f);
        int   alpha   = Math.round(anim * 255f);

        if (alpha <= 0) return;

        int sx = getSettingsPanelX() + slideOff;
        int sy = startY;

        Gui.drawRect(sx, sy, sx + SETTINGS_W, sy + HEADER_H,
                animated(guiTheme.withAlpha(guiTheme.getTitleColor(), alpha), openProg));
        Gui.drawRect(sx, sy + HEADER_H - 1, sx + SETTINGS_W, sy + HEADER_H,
                animated(guiTheme.withAlpha(guiTheme.getStrokeColor(), alpha), openProg));

        String title = selectedModule != null ? selectedModule.getName() + " Settings" : "Settings";
        fontRenderer.drawStringWithShadow(title, sx + 5, sy + 3,
                animated(guiTheme.getTextPrimaryColor(), openProg));

        int contentH = getSettingsContentHeight();
        Gui.drawRect(sx, sy + HEADER_H, sx + SETTINGS_W, sy + HEADER_H + contentH,
                animated(guiTheme.withAlpha(guiTheme.getPanelColor(), alpha), openProg));
        drawOutline(sx, sy, SETTINGS_W, HEADER_H + contentH,
                animated(guiTheme.withAlpha(guiTheme.getStrokeColor(), 75), openProg));

        int y       = sy + HEADER_H - Math.round(settingsScrollVisual);
        int rowIdx  = 0;
        int startYS = sy + HEADER_H;
        int visH    = contentH;

        if (isRowVisible(y, SETTINGS_ITEM_H, startYS, visH)) {
            boolean bindHov = isHovered(sx, y, SETTINGS_W, SETTINGS_ITEM_H, mouseX, mouseY)
                    && mouseY >= startYS && mouseY <= startYS + visH;
            String bindVal  = waitingForBind ? "PRESS KEY..." :
                    (selectedModule != null ? selectedModule.getKeyName() : "-");
            drawSettingRow(sx, y, SETTINGS_W, SETTINGS_ITEM_H,
                    "Bind", bindVal, rowIdx, bindHov, 0xFFE082, alpha, openProg);
        }
        y += SETTINGS_ITEM_H + ROW_GAP;
        rowIdx++;

        if (selectedModule == null) return;

        List<Setting> settings = getSettingsForModule();
        if (settings.isEmpty()) {
            if (isRowVisible(y, SETTINGS_ITEM_H, startYS, visH)) {
                drawSettingRow(sx, y, SETTINGS_W, SETTINGS_ITEM_H,
                        "No settings", "", rowIdx, false,
                        guiTheme.getAccentColor(), alpha, openProg);
            }
            return;
        }

        for (Setting setting : settings) {
            if (!isRowVisible(y, SETTINGS_ITEM_