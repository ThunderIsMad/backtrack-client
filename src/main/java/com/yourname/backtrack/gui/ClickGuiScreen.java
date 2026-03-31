package com.yourname.backtrack.gui;

import com.yourname.backtrack.config.ConfigManager;
import com.yourname.backtrack.hud.HudSettings;
import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.module.ModuleManager;
import com.yourname.backtrack.module.impl.BacktrackModule;
import com.yourname.backtrack.setting.ActionSetting;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import com.yourname.backtrack.setting.Setting;
import setting.SettingGroup;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ClickGuiScreen extends GuiScreen {

    private final ModuleManager moduleManager;
    private final ConfigManager configManager;
    private final HudSettings hudSettings;
    private final GuiTheme guiTheme;
    private final long openTime;

    private static final int PANEL_W  = 120;
    private static final int HEADER_H = 14;
    private static final int MOD_H    = 18;
    private static final int SET_H    = 16;
    private static final int GAP      = 1;

    private final List<Panel> panels = new ArrayList<>();
    private Module expandedModule    = null;
    private boolean expandTextSettings = false;
    private boolean waitingForBind   = false;
    private Panel draggingPanel      = null;
    private int dragOffsetX, dragOffsetY;

    public HudSettings getHudSettings() {
        return hudSettings;
    }

    private static final class Panel {
        final Category category;
        int x, y;
        boolean collapsed = false;

        Panel(Category category, int x, int y) {
            this.category = category;
            this.x = x;
            this.y = y;
        }

        boolean isGuiPanel() { return category == null; }
    }

    public ClickGuiScreen(ModuleManager moduleManager, ConfigManager configManager,
                          HudSettings hudSettings, GuiTheme guiTheme) {
        this.moduleManager = moduleManager;
        this.configManager = configManager;
        this.hudSettings   = hudSettings;
        this.guiTheme      = guiTheme;
        this.openTime      = System.currentTimeMillis();
    }

    @Override
    public void initGui() {
        Map<Category, int[]> savedPos = new HashMap<>();
        int[] savedGuiPos = null;
        for (Panel p : panels) {
            if (p.isGuiPanel()) savedGuiPos = new int[]{p.x, p.y};
            else savedPos.put(p.category, new int[]{p.x, p.y});
        }

        panels.clear();
        int x = 5, y = 5;
        for (Category cat : Category.values()) {
            if (cat == Category.HUD) continue;
            int[] pos = savedPos.get(cat);
            panels.add(new Panel(cat, pos != null ? pos[0] : x, pos != null ? pos[1] : y));
            x += PANEL_W + 4;
            if (x + PANEL_W > width - 5) { x = 5; y += 200; }
        }
        panels.add(new Panel(null,
                savedGuiPos != null ? savedGuiPos[0] : x,
                savedGuiPos != null ? savedGuiPos[1] : y));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Gui.drawRect(0, 0, width, height, animated(guiTheme.getBackdropColor()));

        for (Panel panel : panels) {
            drawPanel(panel, mouseX, mouseY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPanel(Panel panel, int mouseX, int mouseY) {
        int panelH = getPanelHeight(panel);
        int x = panel.x, y = panel.y;

        Gui.drawRect(x - 2, y - 2, x + PANEL_W + 2, y + panelH + 2,
                animated(guiTheme.getSoftShadowColor()));

        Gui.drawRect(x, y, x + PANEL_W, y + panelH,
                animated(guiTheme.getWindowGlassColor()));

        Gui.drawRect(x, y, x + PANEL_W, y + HEADER_H,
                animated(guiTheme.getTitleColor()));
        Gui.drawRect(x, y + HEADER_H - 1, x + PANEL_W, y + HEADER_H,
                animated(guiTheme.getStrokeColor()));

        String title = panel.isGuiPanel() ? "GUI" : panel.category.name();
        int titleColor = panel.isGuiPanel()
                ? guiTheme.getAccentTextColor()
                : guiTheme.getTextPrimaryColor();
        fontRenderer.drawStringWithShadow(title, x + 5, y + 3, titleColor);

        String indicator = panel.collapsed ? "+" : "-";
        fontRenderer.drawStringWithShadow(indicator, x + PANEL_W - 8, y + 3,
                animated(guiTheme.getTextMutedColor()));

        drawOutline(x, y, PANEL_W, panelH,
                animated(guiTheme.withAlpha(guiTheme.getStrokeColor(), 80)));

        if (panel.collapsed) return;

        if (panel.isGuiPanel()) {
            drawGuiPanelContent(x, y + HEADER_H, mouseX, mouseY);
        } else {
            drawModuleList(panel, x, y + HEADER_H, mouseX, mouseY);
        }
    }

    private void drawModuleList(Panel panel, int x, int startY, int mouseX, int mouseY) {
        int y = startY;
        for (Module mod : getModulesForCategory(panel.category)) {
            boolean hov      = isHov(x, y, PANEL_W, MOD_H, mouseX, mouseY);
            boolean expanded = mod == expandedModule;

            int bg = hov ? guiTheme.getPanelHoverColor() : guiTheme.getPanelColor();
            Gui.drawRect(x, y, x + PANEL_W, y + MOD_H, animated(bg));

            if (expanded) {
                Gui.drawRect(x, y, x + PANEL_W, y + MOD_H,
                        animated(guiTheme.withAlpha(guiTheme.getAccentColor(), 30)));
                Gui.drawRect(x, y, x + 2, y + MOD_H,
                        animated(guiTheme.getAccentHoverColor()));
            }

            int dotColor = mod.isEnabled()
                    ? guiTheme.getToggleOnColor()
                    : guiTheme.getToggleOffColor();
            Gui.drawRect(x + 5, y + 7, x + 8, y + 10, animated(dotColor));

            int textColor = expanded
                    ? guiTheme.getTextPrimaryColor()
                    : mod.isEnabled()
                      ? guiTheme.getAccentTextColor()
                      : guiTheme.getTextSecondaryColor();
            fontRenderer.drawStringWithShadow(mod.getName(), x + 12, y + 5, textColor);

            if (!mod.getSettings().isEmpty()) {
                String arr = expanded ? "^" : "v";
                fontRenderer.drawStringWithShadow(arr, x + PANEL_W - 9, y + 5,
                        animated(guiTheme.getTextMutedColor()));
            }

            y += MOD_H + GAP;

            if (expanded) {
                y = drawInlineSettings(x, y, mod, mouseX, mouseY);
            }
        }
    }

    private int drawInlineSettings(int x, int startY, Module mod, int mouseX, int mouseY) {
        int y = startY;

        boolean bindHov = isHov(x, y, PANEL_W, SET_H, mouseX, mouseY);
        String bindVal  = waitingForBind ? "PRESS KEY..." : mod.getKeyName();
        drawSettingRow(x, y, "Bind", bindVal, 0, bindHov, 0xFFFFE082);
        y += SET_H + GAP;

        List<com.yourname.backtrack.setting.Setting> mainSettings = getMainSettings(mod);
        for (int i = 0; i < mainSettings.size(); i++) {
            Setting s   = mainSettings.get(i);
            boolean hov = isHov(x, y, PANEL_W, SET_H, mouseX, mouseY);
            drawSettingRow(x, y, s.getName(), getSettingValueText(s),
                    i + 1, hov, getSettingAccentColor(s));
            y += SET_H + GAP;
        }

        if (mod instanceof BacktrackModule) {
            boolean hov = isHov(x, y, PANEL_W, SET_H, mouseX, mouseY);
            drawSettingRow(x, y, "Text Settings",
                    expandTextSettings ? "^" : ">",
                    mainSettings.size() + 1, hov, 0xFF56CCF2);
            y += SET_H + GAP;

            if (expandTextSettings) {
                List<Setting> textSettings = getTextSettings(mod);
                for (int i = 0; i < textSettings.size(); i++) {
                    Setting s   = textSettings.get(i);
                    boolean hov2 = isHov(x, y, PANEL_W, SET_H, mouseX, mouseY);
                    drawSettingRow(x, y, s.getName(), getSettingValueText(s),
                            mainSettings.size() + 2 + i, hov2, getSettingAccentColor(s));
                    y += SET_H + GAP;
                }
            }
        }

        return y;
    }

    private void drawSettingRow(int x, int y, String left, String right,
                                int rowIdx, boolean hov, int accent) {
        int bg = hov ? guiTheme.getPanelHoverColor() : guiTheme.getRowAltColor(rowIdx);
        Gui.drawRect(x, y, x + PANEL_W, y + SET_H, animated(bg));
        Gui.drawRect(x, y, x + 2, y + SET_H,
                animated(guiTheme.withAlpha(accent, hov ? 180 : 70)));

        fontRenderer.drawStringWithShadow(left, x + 6, y + 4,
                guiTheme.getTextPrimaryColor());

        if (right != null && !right.isEmpty()) {
            int rw    = fontRenderer.getStringWidth(right);
            int rColor = "ON".equals(right)           ? guiTheme.getToggleOnColor()
                    : "OFF".equals(right)          ? guiTheme.getToggleOffColor()
                      : "PRESS KEY...".equals(right) ? animated(guiTheme.getAccentHoverColor())
                        : guiTheme.getTextSecondaryColor();
            fontRenderer.drawStringWithShadow(right, x + PANEL_W - rw - 5, y + 4, rColor);
        }
    }

    private void drawGuiPanelContent(int x, int startY, int mouseX, int mouseY) {
        int y = startY;
        String[] rows = {
                "Accent: "    + guiTheme.getAccentName(),
                "Background: " + guiTheme.getBackgroundName(),
                "Reset Colors"
        };
        for (int i = 0; i < rows.length; i++) {
            boolean hov = isHov(x, y, PANEL_W, SET_H, mouseX, mouseY);
            drawSettingRow(x, y, rows[i], "", i, hov, guiTheme.getAccentHoverColor());
            y += SET_H + GAP;
        }
    }

    private int getPanelHeight(Panel panel) {
        if (panel.collapsed) return HEADER_H;

        if (panel.isGuiPanel()) {
            return HEADER_H + 3 * (SET_H + GAP);
        }

        int h = HEADER_H;
        for (Module mod : getModulesForCategory(panel.category)) {
            h += MOD_H + GAP;
            if (mod == expandedModule) {
                int rows = 1 + getMainSettings(mod).size();
                if (mod instanceof BacktrackModule) {
                    rows++;
                    if (expandTextSettings) rows += getTextSettings(mod).size();
                }
                h += rows * (SET_H + GAP);
            }
        }
        return h;
    }

    private List<Module> getModulesForCategory(Category cat) {
        List<Module> result = new ArrayList<>();
        for (Module m : moduleManager.getModules()) {
            if (m.getCategory() == cat) {
                result.add(m);
            }
        }
        return result;
    }

    private List<Setting> getMainSettings(Module mod) {
        ArrayList<Setting> result = new ArrayList<>();
        for (Setting s : mod.getSettings()) {
            if (s.getGroup() != SettingGroup.HUDTEXT
                    && s.getGroup() != SettingGroup.DEBUGWINDOW) {
                result.add(s);
            }
        }
        return result;
    }

    private List<Setting> getTextSettings(Module mod) {
        ArrayList<Setting> result = new ArrayList<>();
        for (Setting s : mod.getSettings()) {
            if (s.getGroup() == SettingGroup.HUDTEXT) result.add(s);
        }
        return result;
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int btn) throws IOException {
        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel panel = panels.get(i);
            if (handlePanelClick(panel, mouseX, mouseY, btn)) {
                panels.remove(i);
                panels.add(panel);
                break;
            }
        }
        super.mouseClicked(mouseX, mouseY, btn);
    }

    private boolean handlePanelClick(Panel panel, int mouseX, int mouseY, int btn) {
        int panelH = getPanelHeight(panel);
        if (!isHov(panel.x, panel.y, PANEL_W, panelH, mouseX, mouseY)) return false;

        if (isHov(panel.x, panel.y, PANEL_W, HEADER_H, mouseX, mouseY)) {
            if (btn == 0) {
                draggingPanel = panel;
                dragOffsetX   = mouseX - panel.x;
                dragOffsetY   = mouseY - panel.y;
            } else if (btn == 1) {
                panel.collapsed = !panel.collapsed;
                if (panel.collapsed) {
                    expandedModule     = null;
                    expandTextSettings = false;
                    waitingForBind     = false;
                }
            }
            return true;
        }

        if (panel.collapsed) return true;

        if (panel.isGuiPanel()) {
            handleGuiPanelClick(panel.x, panel.y + HEADER_H, mouseX, mouseY, btn);
            return true;
        }

        int y = panel.y + HEADER_H;
        for (Module mod : getModulesForCategory(panel.category)) {
            if (isHov(panel.x, y, PANEL_W, MOD_H, mouseX, mouseY)) {
                if (btn == 0) {
                    mod.toggle();
                } else if (btn == 1) {
                    if (expandedModule == mod) {
                        expandedModule     = null;
                        expandTextSettings = false;
                        waitingForBind     = false;
                    } else {
                        expandedModule     = mod;
                        expandTextSettings = false;
                        waitingForBind     = false;
                    }
                }
                return true;
            }
            y += MOD_H + GAP;

            if (mod != expandedModule) continue;

            if (isHov(panel.x, y, PANEL_W, SET_H, mouseX, mouseY)) {
                if (btn == 0) waitingForBind = !waitingForBind;
                return true;
            }
            y += SET_H + GAP;

            for (Setting s : getMainSettings(mod)) {
                if (isHov(panel.x, y, PANEL_W, SET_H, mouseX, mouseY)) {
                    handleSettingClick(mod, s, btn);
                    return true;
                }
                y += SET_H + GAP;
            }

            if (mod instanceof BacktrackModule) {
                if (isHov(panel.x, y, PANEL_W, SET_H, mouseX, mouseY)) {
                    expandTextSettings = !expandTextSettings;
                    return true;
                }
                y += SET_H + GAP;

                if (expandTextSettings) {
                    for (Setting s : getTextSettings(mod)) {
                        if (isHov(panel.x, y, PANEL_W, SET_H, mouseX, mouseY)) {
                            handleSettingClick(mod, s, btn);
                            return true;
                        }
                        y += SET_H + GAP;
                    }
                }
            }
        }

        return true;
    }

    private void handleGuiPanelClick(int x, int startY, int mouseX, int mouseY, int btn) {
        if (btn != 0) return;
        int y = startY;
        if (isHov(x, y, PANEL_W, SET_H, mouseX, mouseY)) { guiTheme.cycleAccentColor();    return; }
        y += SET_H + GAP;
        if (isHov(x, y, PANEL_W, SET_H, mouseX, mouseY)) { guiTheme.cycleBackgroundStyle(); return; }
        y += SET_H + GAP;
        if (isHov(x, y, PANEL_W, SET_H, mouseX, mouseY)) { guiTheme.reset(); }
    }

    private void handleSettingClick(Module mod, Setting s, int btn) {
        if (s instanceof BooleanSetting) {
            ((BooleanSetting) s).toggle();
        } else if (s instanceof ModeSetting) {
            ((ModeSetting) s).cycle();
        } else if (s instanceof NumberSetting) {
            NumberSetting ns = (NumberSetting) s;
            if (btn == 0) ns.increase();
            else          ns.decrease();
        } else if (s instanceof ActionSetting) {
            ((ActionSetting) s).trigger(
                    new ActionSetting.ActionContext(
                            this, moduleManager, configManager, guiTheme));
        }
        configManager.saveModuleSettings(moduleManager);
        configManager.saveModuleHudSettings(moduleManager);
        configManager.saveBacktrackInfoPosition(moduleManager);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();

        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;

        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        for (Panel panel : panels) {
            int panelH = getPanelHeight(panel);
            if (!isHov(panel.x, panel.y, PANEL_W, panelH, mouseX, mouseY)) continue;
            if (panel.collapsed || panel.isGuiPanel()) continue;

            int delta = wheel > 0 ? -15 : 15;
            panel.y = Math.max(0, Math.min(height - panelH, panel.y + delta));
            break;
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int btn, long time) {
        if (draggingPanel != null) {
            draggingPanel.x = mouseX - dragOffsetX;
            draggingPanel.y = mouseY - dragOffsetY;
            clampPanel(draggingPanel);
        }
        super.mouseClickMove(mouseX, mouseY, btn, time);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int btn) {
        if (btn == 0) draggingPanel = null;
        super.mouseReleased(mouseX, mouseY, btn);
    }

    @Override
    protected void keyTyped(char typed, int keyCode) throws IOException {
        if (waitingForBind && expandedModule != null) {
            if (keyCode == Keyboard.KEY_ESCAPE) {
                expandedModule.setKeyCode(Keyboard.KEY_NONE);
            } else {
                expandedModule.setKeyCode(keyCode);
            }
            waitingForBind = false;
            return;
        }

        if (keyCode == Keyboard.KEY_ESCAPE) {
            saveAndClose();
            return;
        }

        super.keyTyped(typed, keyCode);
    }

    private void saveAndClose() {
        for (Panel panel : panels) {
            if (!panel.isGuiPanel()) {
                configManager.saveGuiPosition(panel.x, panel.y);
                break;
            }
        }
        mc.displayGuiScreen(null);
    }

    private boolean isHov(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void clampPanel(Panel panel) {
        int panelH = getPanelHeight(panel);
        panel.x = Math.max(0, Math.min(width  - PANEL_W, panel.x));
        panel.y = Math.max(0, Math.min(height - panelH,  panel.y));
    }

    private void drawOutline(int x, int y, int w, int h, int color) {
        Gui.drawRect(x,         y,         x + w,     y + 1,     color);
        Gui.drawRect(x,         y + h - 1, x + w,     y + h,     color);
        Gui.drawRect(x,         y,         x + 1,     y + h,     color);
        Gui.drawRect(x + w - 1, y,         x + w,     y + h,     color);
    }

    private int animated(int color) {
        float progress = Math.min(1.0f,
                (System.currentTimeMillis() - openTime) / 220.0f);
        int alpha = (color >> 24) & 0xFF;
        return ((int)(alpha * progress) << 24) | (color & 0x00FFFFFF);
    }

    private String getSettingValueText(Setting s) {
        if (s instanceof BooleanSetting)
            return ((BooleanSetting) s).getValue() ? "ON" : "OFF";
        if (s instanceof NumberSetting)
            return String.format(Locale.US, "%.1f", ((NumberSetting) s).getValue());
        if (s instanceof ModeSetting)
            return ((ModeSetting) s).getValue();
        if (s instanceof ActionSetting)
            return ">";
        return "";
    }

    private int getSettingAccentColor(Setting s) {
        return guiTheme.getAccentColor();
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}