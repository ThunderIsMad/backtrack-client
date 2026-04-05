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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ClickGuiScreen extends GuiScreen {

    private final ModuleManager  moduleManager;
    private final ConfigManager  configManager;
    private final HudSettings    hudSettings;
    private final GuiTheme       guiTheme;
    private final long           openTime;

    // Vape V4 Minimal Design Constants
    private static final int PANEL_W   = 120;
    private static final int PANEL_GAP = 8;
    private static final int HEADER_H  = 16;
    private static final int MOD_H     = 14;
    private static final int SET_H     = 12;
    private static final int ROW_GAP   = 2;
    private static final int MAX_VIS_H = 240;
    
    // Color Palette - Vape V4 Minimal
    private static final int COLOR_BG        = 0xFF141414;  // rgb(20, 20, 20)
    private static final int COLOR_HOVER     = 0xFF1C1C1C;  // rgb(28, 28, 28)
    private static final int COLOR_OUTLINE   = 0xFF232323;  // rgb(35, 35, 35)
    private static final int COLOR_ACCENT    = 0xFF4DA3FF;  // rgb(77, 163, 255)
    private static final int COLOR_SLIDER_BG = 0xFF1E1E1E;  // rgb(30, 30, 30)
    private static final int COLOR_TEXT      = 0xFFD2D2D2;  // rgb(210, 210, 210)
    private static final int COLOR_HEADER    = 0xFF181818;  // rgb(24, 24, 24)

    private final List<Panel> panels = new ArrayList<>();

    private Module  expandedModule = null;
    private boolean waitingForBind = false;
    private boolean draggingSlider = false;
    private NumberSetting draggedSlider = null;
    private int draggedSliderX = 0;
    private int draggedSliderW = 0;
    private final Map<NumberSetting, Double> sliderAnimations = new HashMap<>();

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

        // Draw flat panel background
        Gui.drawRect(x, y, x + PANEL_W, y + panelH, withAlpha(COLOR_BG, 245));

        // Draw subtle 1px outline
        drawOutline(x, y, PANEL_W, panelH, COLOR_OUTLINE);

        // Draw header (slightly darker)
        Gui.drawRect(x, y, x + PANEL_W, y + HEADER_H, withAlpha(COLOR_HEADER, 250));

        String catName = panel.category.name().charAt(0)
                + panel.category.name().substring(1).toLowerCase(Locale.ROOT);
        
        // Centered category text
        fontRenderer.drawStringWithShadow(catName,
                x + PANEL_W / 2 - fontRenderer.getStringWidth(catName) / 2,
                y + 4,
                COLOR_TEXT);

        // Collapse indicator
        fontRenderer.drawStringWithShadow(panel.collapsed ? "+" : "-",
                x + PANEL_W - 8, y + 4,
                withAlpha(COLOR_TEXT, 120));

        // Thin 2px accent underline below header text
        int textWidth = fontRenderer.getStringWidth(catName);
        int underlineX = x + PANEL_W / 2 - textWidth / 2;
        Gui.drawRect(underlineX, y + HEADER_H - 2, underlineX + textWidth, y + HEADER_H,
                panel.collapsed ? COLOR_OUTLINE : COLOR_ACCENT);

        if (panel.collapsed) {
            return;
        }

        // Content area background
        Gui.drawRect(x, y + HEADER_H, x + PANEL_W, y + HEADER_H + contentH, withAlpha(COLOR_BG, 250));

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
    }

    private void drawModuleRow(int x, int y, Module mod, int mouseX, int mouseY) {
        boolean hov      = isHov(x, y, PANEL_W, MOD_H, mouseX, mouseY);
        boolean expanded = mod == expandedModule;

        // Flat background with subtle hover effect
        int bg = expanded  ? withAlpha(COLOR_HOVER, 255)
                : hov       ? withAlpha(COLOR_HOVER, 200)
                  : withAlpha(COLOR_BG, 255);
        Gui.drawRect(x, y, x + PANEL_W, y + MOD_H, animated(bg));

        // Enabled indicator - subtle accent text, no blue bar
        int textColor = mod.isEnabled() ? COLOR_ACCENT
                : expanded        ? COLOR_TEXT
                  : withAlpha(COLOR_TEXT, 180);
        
        // 6px left padding for clean alignment
        fontRenderer.drawStringWithShadow(mod.getName(), x + 6, y + 3, animated(textColor));

        // Expand indicator
        if (!mod.getSettings().isEmpty()) {
            fontRenderer.drawStringWithShadow(expanded ? "⌃" : "⌄",
                    x + PANEL_W - 8, y + 3,
                    withAlpha(COLOR_TEXT, 100));
        }
    }

    private int drawInlineSettings(int x, int startY, Module mod,
                                   int mouseX, int mouseY,
                                   int clipTop, int clipBot) {
        int y = startY;

        if (y + SET_H >= clipTop && y < clipBot) {
            boolean hov    = isHov(x, y, PANEL_W, SET_H, mouseX, mouseY);
            String  bindTx = waitingForBind ? "PRESS KEY..." : mod.getKeyName();
            drawSettingRow(x, y, "Bind", bindTx, 0, hov, COLOR_ACCENT);
        }
        y += SET_H + ROW_GAP;

        List<Setting> main = getMainSettings(mod);
        for (int i = 0; i < main.size(); i++) {
            Setting s = main.get(i);
            if (y + SET_H >= clipTop && y < clipBot) {
                boolean hov = isHov(x, y, PANEL_W, SET_H, mouseX, mouseY);
                if (s instanceof NumberSetting) {
                    drawNumberSettingSlider(x, y, (NumberSetting) s, i + 1, hov, mouseX);
                } else {
                    drawSettingRow(x, y, s.getName(), getSettingValueText(s),
                            i + 1, hov, COLOR_ACCENT);
                }
            }
            y += SET_H + ROW_GAP;
        }
        return y;
    }

    private void drawSettingRow(int x, int y, String left, String right,
                                int rowIdx, boolean hov, int accent) {
        // Flat background with subtle hover
        int bg = hov ? withAlpha(COLOR_HOVER, 200) : withAlpha(COLOR_BG, 255);
        Gui.drawRect(x, y, x + PANEL_W, y + SET_H, animated(bg));

        // Subtle left accent line
        Gui.drawRect(x, y, x + 2, y + SET_H,
                withAlpha(accent, hov ? 180 : 60));

        int maxLeftW = PANEL_W - (right != null ? fontRenderer.getStringWidth(right) + 10 : 0) - 8;
        String label = left;
        while (label.length() > 1 && fontRenderer.getStringWidth(label) > maxLeftW) {
            label = label.substring(0, label.length() - 1);
        }
        // 6px left padding
        fontRenderer.drawStringWithShadow(label, x + 6, y + 2, COLOR_TEXT);

        if (right != null && !right.isEmpty()) {
            int rw = fontRenderer.getStringWidth(right);
            int rColor = "ON".equals(right)         ? COLOR_ACCENT
                    : "OFF".equals(right)         ? withAlpha(COLOR_TEXT, 100)
                      : "PRESS KEY...".equals(right) ? COLOR_ACCENT
                        : withAlpha(COLOR_TEXT, 140);
            fontRenderer.drawStringWithShadow(right, x + PANEL_W - rw - 6, y + 2, rColor);
        }
    }

    private void drawNumberSettingSlider(int x, int y, NumberSetting ns,
                                         int rowIdx, boolean hov, int mouseX) {
        // Flat background with subtle hover
        int bg = hov ? withAlpha(COLOR_HOVER, 200) : withAlpha(COLOR_BG, 255);
        Gui.drawRect(x, y, x + PANEL_W, y + SET_H, animated(bg));
        
        // Subtle left accent line
        Gui.drawRect(x, y, x + 2, y + SET_H,
                withAlpha(COLOR_ACCENT, hov ? 180 : 60));

        String valStr = String.format(Locale.US, "%.1f", ns.getValue());
        int valWidth = fontRenderer.getStringWidth(valStr);
        int sliderW = 50;
        int sliderGap = 4;
        int rightSectionW = sliderW + sliderGap + valWidth;
        
        int maxLeftW = PANEL_W - rightSectionW - 8;
        String label = ns.getName();
        while (label.length() > 1 && fontRenderer.getStringWidth(label) > maxLeftW) {
            label = label.substring(0, label.length() - 1);
        }
        // 6px left padding
        fontRenderer.drawStringWithShadow(label, x + 6, y + 2, COLOR_TEXT);

        int sliderX = x + PANEL_W - sliderW - valWidth - sliderGap;
        int sliderY = y + SET_H / 2 - 2;  // Center the 4px slider vertically
        int sliderH = 4;  // Height: 4px as per spec

        double realPct = (ns.getValue() - ns.getMin()) / (ns.getMax() - ns.getMin());
        Double animVal = sliderAnimations.get(ns);
        if (animVal == null) {
            animVal = realPct;
            sliderAnimations.put(ns, animVal);
        }
        double animatedPct = animVal + (realPct - animVal) * 0.2;
        sliderAnimations.put(ns, animatedPct);

        int fillW = (int)(sliderW * animatedPct);

        // Thin slider background
        Gui.drawRect(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH,
                withAlpha(COLOR_SLIDER_BG, 255));
        // Thin fill using accent color
        Gui.drawRect(sliderX, sliderY, sliderX + fillW, sliderY + sliderH,
                COLOR_ACCENT);

        // Right-aligned value text
        fontRenderer.drawStringWithShadow(valStr, sliderX + sliderW + sliderGap,
                y + 2, withAlpha(COLOR_TEXT, 140));
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

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int btn, long time) {
        for (Panel panel : panels) {
            if (!panel.dragging) continue;
            panel.x = Math.max(0, Math.min(width  - PANEL_W,  mouseX - panel.dragOffX));
            panel.y = Math.max(0, Math.min(height - HEADER_H, mouseY - panel.dragOffY));
        }
        if (draggingSlider && draggedSlider != null && btn == 0) {
            updateSliderFromMouse(draggedSlider, mouseX, draggedSliderX, draggedSliderW);
        }
        super.mouseClickMove(mouseX, mouseY, btn, time);
    }

    private void updateSliderFromMouse(NumberSetting ns, int mouseX, int sliderX, int sliderW) {
        double range = ns.getMax() - ns.getMin();
        double pct = (double)(mouseX - sliderX) / sliderW;
        pct = Math.max(0, Math.min(1, pct));
        double newValue = ns.getMin() + pct * range;
        double step = ns.getIncrement();
        if (step > 0) {
            newValue = Math.round(newValue / step) * step;
        }
        ns.setValue(newValue);
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
                if (s instanceof NumberSetting) {
                    String valStr = String.format(Locale.US, "%.1f", ((NumberSetting) s).getValue());
                    int valWidth = fontRenderer.getStringWidth(valStr);
                    int sliderW = 50;
                    int sliderGap = 4;
                    int sliderX = x + PANEL_W - sliderW - valWidth - sliderGap;
                    int sliderY = rowY + 3;
                    int sliderH = SET_H - 6;
                    if (isHov(sliderX, sliderY, sliderW, sliderH, mouseX, mouseY)) {
                        if (btn == 0) {
                            draggingSlider = true;
                            draggedSlider = (NumberSetting) s;
                            draggedSliderX = sliderX;
                            draggedSliderW = sliderW;
                            updateSliderFromMouse(draggedSlider, mouseX, sliderX, sliderW);
                            return true;
                        } else {
                            handleSettingClick(mod, s, btn);
                            return true;
                        }
                    }
                }
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
    protected void mouseReleased(int mouseX, int mouseY, int btn) {
        if (btn == 0) {
            for (Panel panel : panels) panel.dragging = false;
            draggingSlider = false;
            draggedSlider = null;
            draggedSliderX = 0;
            draggedSliderW = 0;
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
        return COLOR_ACCENT;
    }

    // Helper method to create color with alpha
    private int withAlpha(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    @Override
    public boolean doesGuiPauseGame() { return false; }
}
