package com.yourname.backtrack.gui;

import com.yourname.backtrack.config.ConfigManager;
import com.yourname.backtrack.hud.HudSettings;
import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.module.ModuleManager;
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
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClickGuiScreen extends GuiScreen {

    private final ModuleManager  moduleManager;
    private final ConfigManager  configManager;
    private final HudSettings    hudSettings;
    private final GuiTheme       guiTheme;
    private final long           openTime;

    private static final int PANEL_W   = 110;
    private static final int PANEL_GAP = 6;
    private static final int HEADER_H  = 14;
    private static final int MOD_H     = 14;
    private static final int SET_H     = 12;
    private static final int ROW_GAP   = 1;
    private static final int MAX_VIS_H = 220;

    private final List<Panel> panels = new ArrayList<>();

    private Module  expandedModule = null;
    private boolean waitingForBind = false;

    private static class Panel {
        final Category category;
        int     x, y;
        boolean collapsed    = false;
        int     scrollOffset = 0;
        boolean dragging     = false;
        int     dragOffX, dragOffY;

        Panel(Category category, int x, int y) {
            this.category = category;
            this.x = x;
            this.y = y;
        }
    }

    public ClickGuiScreen(ModuleManager moduleManager, ConfigManager configManager,
                          HudSettings hudSettings, GuiTheme guiTheme) {
        this.moduleManager = moduleManager;
        this.configManager = configManager;
        this.hudSettings   = hudSettings;
        this.guiTheme      = guiTheme;
        this.openTime      = System.currentTimeMillis();
    }

    public HudSettings getHudSettings() { return hudSettings; }

    @Override
    public void initGui() {
        panels.clear();
        int count = 0;
        for (Category cat : Category.values()) {
            if (cat != Category.HUD) count++;
        }
        int totalW = count * PANEL_W + (count - 1) * PANEL_GAP;
        int startX = (width  - totalW) / 2;
        int startY =  height / 4;
        for (Category cat : Category.values()) {
            if (cat == Category.HUD) continue;
            panels.add(new Panel(cat, startX, startY));
            startX += PANEL_W + PANEL_GAP;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        for (Panel panel : panels) {
            drawPanel(panel, mouseX, mouseY);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPanel(Panel panel, int mouseX, int mouseY) {
        int x        = panel.x;
        int y        = panel.y;
        int contentH = panel.collapsed ? 0 : getPanelContentHeight(panel);
        int panelH   = HEADER_H + contentH;

        Gui.drawRect(x - 2, y - 2, x + PANEL_W + 2, y + panelH + 2,
                animated(guiTheme.withAlpha(0xFF000000, 55)));

        boolean headerHov = isHov(x, y, PANEL_W, HEADER_H, mouseX, mouseY);
        int headerBg = headerHov
                ? guiTheme.withAlpha(guiTheme.getAccentColor(), 200)
                : guiTheme.getTitleColor();
        Gui.drawRect(x, y, x + PANEL_W, y + HEADER_H, animated(headerBg));

        String catName = panel.category.name().charAt(0)
                + panel.category.name().substring(1).toLowerCase(Locale.ROOT);
        fontRenderer.drawStringWithShadow(catName,
                x + PANEL_W / 2 - fontRenderer.getStringWidth(catName) / 2,
                y + 3,
                animated(guiTheme.getAccentTextColor()));

        fontRenderer.drawStringWithShadow(panel.collapsed ? "+" : "-",
                x + PANEL_W - 9, y + 3,
                animated(guiTheme.getTextMutedColor()));

        Gui.drawRect(x, y + HEADER_H - 1, x + PANEL_W, y + HEADER_H,
                animated(guiTheme.getAccentColor()));

        if (panel.collapsed) {
            drawOutline(x, y, PANEL_W, HEADER_H,
                    animated(guiTheme.withAlpha(guiTheme.getStrokeColor(), 90)));
            return;
        }

        Gui.drawRect(x, y + HEADER_H, x + PANEL_W, y + HEADER_H + contentH,
                animated(guiTheme.getWindowGlassColor()));

        enableScissor(x, y + HEADER_H, PANEL_W, contentH);

        int rowY = y + HEADER_H - panel.scrollOffset;
        for (Module mod : getModulesForCategory(panel.category)) {
            boolean inView = rowY + MOD_H >= y + HEADER_H
                    && rowY       <  y + HEADER_H + contentH;
            if (inView) drawModuleRow(x, rowY, mod, mouseX, mouseY);
            rowY += MOD_H + ROW_GAP;

            if (mod == expandedModule) {
                rowY = drawInlineSettings(x, rowY, mod, mouseX, mouseY,
                        y + HEADER_H, y + HEADER_H + contentH);
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        drawOutline(x, y, PANEL_W, panelH,
                animated(guiTheme.withAlpha(guiTheme.getStrokeColor(), 90)));
    }

    private void drawModuleRow(int x, int y, Module mod, int mouseX, int mouseY) {
        boolean hov      = isHov(x, y, PANEL_W, MOD_H, mouseX, mouseY);
        boolean expanded = mod == expandedModule;

        int bg = expanded  ? guiTheme.withAlpha(guiTheme.getAccentColor(), 28)
                : hov       ? guiTheme.getPanelHoverColor()
                  : guiTheme.getPanelColor();
        Gui.drawRect(x, y, x + PANEL_W, y + MOD_H, animated(bg));

        if (mod.isEnabled()) {
            Gui.drawRect(x, y, x + 2, y + MOD_H, animated(guiTheme.getAccentColor()));
        }

        int textColor = mod.isEnabled() ? guiTheme.getAccentTextColor()
                : expanded        ? guiTheme.getTextPrimaryColor()
                  : guiTheme.getTextSecondaryColor();
        fontRenderer.drawStringWithShadow(mod.getName(), x + 5, y + 3, animated(textColor));

        if (!mod.getSettings().isEmpty()) {
            fontRenderer.drawStringWithShadow(expanded ? "^" : "v",
                    x + PANEL_W - 8, y + 3,
                    animated(guiTheme.getTextMutedColor()));
        }
    }

    private int drawInlineSettings(int x, int startY, Module mod,
                                   int mouseX, int mouseY,
                                   int clipTop, int clipBot) {
        int y = startY;

        if (y + SET_H >= clipTop && y < clipBot) {
            boolean hov    = isHov(x, y, PANEL_W, SET_H, mouseX, mouseY);
            String  bindTx = waitingForBind ? "PRESS KEY..." : mod.getKeyName();
            drawSettingRow(x, y, "Bind", bindTx, 0, hov, 0xFFFFE082);
        }
        y += SET_H + ROW_GAP;

        List<Setting> main = getMainSettings(mod);
        for (int i = 0; i < main.size(); i++) {
            Setting s = main.get(i);
            if (y + SET_H >= clipTop && y < clipBot) {
                boolean hov = isHov(x, y, PANEL_W, SET_H, mouseX, mouseY);
                drawSettingRow(x, y, s.getName(), getSettingValueText(s),
                        i + 1, hov, getSettingAccentColor(s));
            }
            y += SET_H + ROW_GAP;
        }
        return y;
    }

    private void drawSettingRow(int x, int y, String left, String right,
                                int rowIdx, boolean hov, int accent) {
        int bg = hov ? guiTheme.getPanelHoverColor() : guiTheme.getRowAltColor(rowIdx);
        Gui.drawRect(x, y, x + PANEL_W, y + SET_H, animated(bg));
        Gui.drawRect(x, y, x + 2, y + SET_H,
                animated(guiTheme.withAlpha(accent, hov ? 180 : 70)));

        int maxLeftW = PANEL_W - (right != null ? fontRenderer.getStringWidth(right) + 10 : 0) - 8;
        String label = left;
        while (label.length() > 1 && fontRenderer.getStringWidth(label) > maxLeftW) {
            label = label.substring(0, label.length() - 1);
        }
        fontRenderer.drawStringWithShadow(label, x + 5, y + 2,
                guiTheme.getTextPrimaryColor());

        if (right != null && !right.isEmpty()) {
            int rw = fontRenderer.getStringWidth(right);
            int rColor = "ON".equals(right)         ? guiTheme.getToggleOnColor()
                    : "OFF".equals(right)         ? guiTheme.getToggleOffColor()
                      : "PRESS KEY...".equals(right) ? animated(guiTheme.getAccentHoverColor())
                        : guiTheme.getTextSecondaryColor();
            fontRenderer.drawStringWithShadow(right, x + PANEL_W - rw - 4, y + 2, rColor);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int btn) throws IOException {
        for (Panel panel : panels) {
            if (handlePanelClick(panel, mouseX, mouseY, btn)) {
                super.mouseClicked(mouseX, mouseY, btn);
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, btn);
    }

    private boolean handlePanelClick(Panel panel, int mouseX, int mouseY, int btn) {
        int x = panel.x;
        int y = panel.y;

        if (isHov(x, y, PANEL_W, HEADER_H, mouseX, mouseY)) {
            if (btn == 0) {
                panel.dragging = true;
                panel.dragOffX = mouseX - x;
                panel.dragOffY = mouseY - y;
            } else if (btn == 1) {
                panel.collapsed = !panel.collapsed;
                if (panel.collapsed && expandedModule != null
                        && expandedModule.getCategory() == panel.category) {
                    expandedModule = null;
                    waitingForBind = false;
                }
            }
            return true;
        }

        if (panel.collapsed) return false;

        int contentH = getPanelContentHeight(panel);
        if (!isHov(x, y + HEADER_H, PANEL_W, contentH, mouseX, mouseY)) return false;

        int rowY = y + HEADER_H - panel.scrollOffset;
        for (Module mod : getModulesForCategory(panel.category)) {
            if (isHov(x, rowY, PANEL_W, MOD_H, mouseX, mouseY)) {
                if (btn == 0) {
                    mod.toggle();
                } else if (btn == 1) {
                    if (expandedModule == mod) {
                        expandedModule = null;
                        waitingForBind = false;
                    } else {
                        expandedModule = mod;
                        waitingForBind = false;
                    }
                }
                return true;
            }
            rowY += MOD_H + ROW_GAP;

            if (mod != expandedModule) continue;

            if (isHov(x, rowY, PANEL_W, SET_H, mouseX, mouseY)) {
                if (btn == 0) waitingForBind = !waitingForBind;
                return true;
            }
            rowY += SET_H + ROW_GAP;

            for (Setting s : getMainSettings(mod)) {
                if (isHov(x, rowY, PANEL_W, SET_H, mouseX, mouseY)) {
                    handleSettingClick(mod, s, btn);
                    return true;
                }
                rowY += SET_H + ROW_GAP;
            }
        }
        return false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;

        int mx = Mouse.getEventX()  * width  / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        for (Panel panel : panels) {
            int contentH = getPanelContentHeight(panel);
            if (isHov(panel.x, panel.y + HEADER_H, PANEL_W, contentH, mx, my)) {
                panel.scrollOffset += wheel > 0 ? -8 : 8;
                if (panel.scrollOffset < 0) panel.scrollOffset = 0;
                int maxScroll = Math.max(0, getPanelTotalRowHeight(panel) - contentH);
                if (panel.scrollOffset > maxScroll) panel.scrollOffset = maxScroll;
                break;
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int btn, long time) {
        for (Panel panel : panels) {
            if (!panel.dragging) continue;
            panel.x = Math.max(0, Math.min(width  - PANEL_W,  mouseX - panel.dragOffX));
            panel.y = Math.max(0, Math.min(height - HEADER_H, mouseY - panel.dragOffY));
        }
        super.mouseClickMove(mouseX, mouseY, btn, time);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int btn) {
        if (btn == 0) {
            for (Panel panel : panels) panel.dragging = false;
        }
        super.mouseReleased(mouseX, mouseY, btn);
    }

    @Override
    protected void keyTyped(char typed, int keyCode) throws IOException {
        if (waitingForBind && expandedModule != null) {
            expandedModule.setKeyCode(
                    keyCode == Keyboard.KEY_ESCAPE ? Keyboard.KEY_NONE : keyCode);
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
        if (!panels.isEmpty()) {
            configManager.saveGuiPosition(panels.get(0).x, panels.get(0).y);
        }
        mc.displayGuiScreen(null);
    }

    private int getPanelTotalRowHeight(Panel panel) {
        int h = 0;
        for (Module mod : getModulesForCategory(panel.category)) {
            h += MOD_H + ROW_GAP;
            if (mod == expandedModule) {
                int rows = 1 + getMainSettings(mod).size();
                h += rows * (SET_H + ROW_GAP);
            }
        }
        return h;
    }

    private int getPanelContentHeight(Panel panel) {
        return Math.min(getPanelTotalRowHeight(panel), MAX_VIS_H);
    }

    private List<Module> getModulesForCategory(Category cat) {
        List<Module> result = new ArrayList<>();
        for (Module m : moduleManager.getModules()) {
            if (m.getCategory() == cat) result.add(m);
        }
        return result;
    }

    private List<Setting> getMainSettings(Module mod) {
        List<Setting> result = new ArrayList<>();
        for (Setting s : mod.getSettings()) {
            if (s.getGroup() != SettingGroup.HUDTEXT
                    && s.getGroup() != SettingGroup.DEBUG_WINDOW) {
                result.add(s);
            }
        }
        return result;
    }

    private void handleSettingClick(Module mod, Setting s, int btn) {
        if (s instanceof BooleanSetting) {
            ((BooleanSetting) s).toggle();
        } else if (s instanceof ModeSetting) {
            ((ModeSetting) s).cycle();
        } else if (s instanceof NumberSetting) {
            NumberSetting ns = (NumberSetting) s;
            if (btn == 0) ns.increase(); else ns.decrease();
        } else if (s instanceof ActionSetting) {
            ((ActionSetting) s).trigger(
                    new ActionSetting.ActionContext(
                            this, moduleManager, configManager, guiTheme));
        }
        configManager.saveModuleSettings(moduleManager);
        configManager.saveModuleHudSettings(moduleManager);
    }

    private boolean isHov(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawOutline(int x, int y, int w, int h, int color) {
        Gui.drawRect(x,         y,         x + w,     y + 1,     color);
        Gui.drawRect(x,         y + h - 1, x + w,     y + h,     color);
        Gui.drawRect(x,         y,         x + 1,     y + h,     color);
        Gui.drawRect(x + w - 1, y,         x + w,     y + h,     color);
    }

    private void enableScissor(int x, int y, int w, int h) {
        double scaleX = (double) mc.displayWidth  / width;
        double scaleY = (double) mc.displayHeight / height;
        int sx = (int)(x       * scaleX);
        int sy = (int)(mc.displayHeight - (y + h) * scaleY);
        int sw = (int)(w * scaleX);
        int sh = (int)(h * scaleY);
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(sx, sy, sw, sh);
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
