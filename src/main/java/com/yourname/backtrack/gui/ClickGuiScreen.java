package com.yourname.backtrack.gui;

import com.yourname.backtrack.config.ConfigManager;
import com.yourname.backtrack.hud.HudSettings;
import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import module.ModuleManager;
import com.yourname.backtrack.module.impl.BacktrackModule;
import com.yourname.backtrack.setting.ActionSetting;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import com.yourname.backtrack.setting.Setting;
import setting.SettingGroup;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClickGuiScreen extends GuiScreen {

    private final ModuleManager moduleManager;
    private final ConfigManager configManager;
    private final HudSettings hudSettings;
    private final GuiTheme guiTheme;
    private final long openTime;

    // --- Layout ---
    private static final int WIN_W    = 300;   // full window width
    private static final int WIN_H    = 220;   // full window height
    private static final int CAT_W    = 72;    // left sidebar width
    private static final int HEADER_H = 16;    // title bar
    private static final int CAT_H    = 20;    // each category tab height
    private static final int MOD_H    = 18;    // module row height
    private static final int SET_H    = 16;    // setting row height
    private static final int GAP      = 1;

    // --- Window state ---
    private int winX, winY;
    private boolean dragging;
    private int dragOffX, dragOffY;

    // --- Content state ---
    private Category selectedCategory;
    private Module expandedModule = null;
    private boolean expandTextSettings = false;
    private boolean waitingForBind = false;
    private int scrollOffset = 0;

    public HudSettings getHudSettings() { return hudSettings; }

    public ClickGuiScreen(ModuleManager moduleManager, ConfigManager configManager,
                          HudSettings hudSettings, GuiTheme guiTheme) {
        this.moduleManager = moduleManager;
        this.configManager = configManager;
        this.hudSettings = hudSettings;
        this.guiTheme = guiTheme;
        this.openTime = System.currentTimeMillis();
        // select first non-HUD category by default
        for (Category cat : Category.values()) {
            if (cat != Category.HUD) { selectedCategory = cat; break; }
        }
    }

    // =========================================================
    //  initGui
    // =========================================================
    @Override
    public void initGui() {
        // default position — center of screen
        winX = (width  - WIN_W) / 2;
        winY = (height - WIN_H) / 2;

        // load saved position if exists
        int[] saved = configManager.loadGuiPosition(); // returns null or int[]{x, y}
        if (saved != null) {
            winX = saved[0];
            winY = saved[1];
            // clamp — in case screen resolution changed
            winX = Math.max(0, Math.min(width  - WIN_W, winX));
            winY = Math.max(0, Math.min(height - WIN_H, winY));
        }
    }

    // =========================================================
    //  drawScreen
    // =========================================================
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        Gui.drawRect(0, 0, width, height, animated(guiTheme.getBackdropColor()));

        // outer glow / shadow
        Gui.drawRect(winX - 2, winY - 2, winX + WIN_W + 2, winY + WIN_H + 2,
                animated(guiTheme.getSoftShadowColor()));

        // window body
        Gui.drawRect(winX, winY, winX + WIN_W, winY + WIN_H,
                animated(guiTheme.getWindowGlassColor()));

        // ── title bar ──────────────────────────────────────────
        Gui.drawRect(winX, winY, winX + WIN_W, winY + HEADER_H,
                animated(guiTheme.getTitleColor()));
        String title = "Backtrack";
        fontRenderer.drawStringWithShadow(title,
                winX + WIN_W / 2 - fontRenderer.getStringWidth(title) / 2,
                winY + 4,
                animated(guiTheme.getAccentTextColor()));
        Gui.drawRect(winX, winY + HEADER_H, winX + WIN_W, winY + HEADER_H + 1,
                animated(guiTheme.getStrokeColor()));

        // ── left sidebar ───────────────────────────────────────
        int sideX = winX;
        int sideY = winY + HEADER_H + 1;
        int sideH = WIN_H - HEADER_H - 1;
        Gui.drawRect(sideX, sideY, sideX + CAT_W, sideY + sideH,
                animated(guiTheme.withAlpha(guiTheme.getTitleColor(), 140)));
        // sidebar right border
        Gui.drawRect(sideX + CAT_W, sideY, sideX + CAT_W + 1, sideY + sideH,
                animated(guiTheme.getStrokeColor()));

        drawCategoryTabs(sideX, sideY, mouseX, mouseY);

        // ── right content (modules) ─────────────────────────────
        int cntX = winX + CAT_W + 1;
        int cntY = sideY;
        int cntW = WIN_W - CAT_W - 1;
        drawModuleContent(cntX, cntY, cntW, sideH, mouseX, mouseY);

        // outline
        drawOutline(winX, winY, WIN_W, WIN_H,
                animated(guiTheme.withAlpha(guiTheme.getStrokeColor(), 90)));

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    // =========================================================
    //  Category tabs
    // =========================================================
    private void drawCategoryTabs(int x, int startY, int mouseX, int mouseY) {
        int y = startY + 4;
        for (Category cat : Category.values()) {
            if (cat == Category.HUD) continue;
            boolean selected = cat == selectedCategory;
            boolean hov = isHov(x, y, CAT_W, CAT_H, mouseX, mouseY);

            if (selected) {
                Gui.drawRect(x, y, x + CAT_W, y + CAT_H,
                        animated(guiTheme.withAlpha(guiTheme.getAccentColor(), 45)));
                Gui.drawRect(x, y, x + 2, y + CAT_H,
                        animated(guiTheme.getAccentColor()));
            } else if (hov) {
                Gui.drawRect(x, y, x + CAT_W, y + CAT_H,
                        animated(guiTheme.getPanelHoverColor()));
            }

            int textColor = selected ? guiTheme.getAccentTextColor()
                    : hov     ? guiTheme.getTextPrimaryColor()
                      :           guiTheme.getTextSecondaryColor();
            // Capitalize first letter
            String name = cat.name().charAt(0) + cat.name().substring(1).toLowerCase();
            fontRenderer.drawStringWithShadow(name, x + 8, y + 6, animated(textColor));

            y += CAT_H + GAP;
        }
    }

    // =========================================================
    //  Module list (with scissor clipping + scroll)
    // =========================================================
    private void drawModuleContent(int x, int startY, int cntW, int cntH,
                                   int mouseX, int mouseY) {
        if (selectedCategory == null) return;

        List<Module> modules = getModulesForCategory(selectedCategory);
        int totalH = getModuleListHeight(modules);

        // clamp scroll
        int maxScroll = Math.max(0, totalH - cntH);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // GL scissor so rows don't bleed outside the panel
        enableScissor(x, startY, cntW, cntH);

        int y = startY - scrollOffset;
        for (Module mod : modules) {
            if (y + MOD_H >= startY && y < startY + cntH) {
                drawModuleRow(x, y, cntW, mod, mouseX, mouseY);
            }
            y += MOD_H + GAP;

            if (mod == expandedModule) {
                y = drawInlineSettings(x, y, cntW, mod, mouseX, mouseY,
                        startY, startY + cntH);
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    private void drawModuleRow(int x, int y, int w, Module mod,
                               int mouseX, int mouseY) {
        boolean hov      = isHov(x, y, w, MOD_H, mouseX, mouseY);
        boolean expanded = mod == expandedModule;

        // background
        int bg = expanded ? guiTheme.withAlpha(guiTheme.getAccentColor(), 28)
                : hov      ? guiTheme.getPanelHoverColor()
                  :             guiTheme.getPanelColor();
        Gui.drawRect(x, y, x + w, y + MOD_H, animated(bg));

        // enabled accent bar on left
        if (mod.isEnabled()) {
            Gui.drawRect(x, y, x + 2, y + MOD_H, animated(guiTheme.getAccentColor()));
        }

        // module name
        int textColor = mod.isEnabled() ? guiTheme.getAccentTextColor()
                : expanded        ? guiTheme.getTextPrimaryColor()
                  :                   guiTheme.getTextSecondaryColor();
        fontRenderer.drawStringWithShadow(mod.getName(), x + 7, y + 5, animated(textColor));

        // "ON" label if enabled
        if (mod.isEnabled()) {
            int sw = fontRenderer.getStringWidth("ON");
            fontRenderer.drawStringWithShadow("ON",
                    x + w - sw - (mod.getSettings().isEmpty() ? 5 : 14),
                    y + 5, animated(guiTheme.getToggleOnColor()));
        }

        // expand arrow
        if (!mod.getSettings().isEmpty()) {
            fontRenderer.drawStringWithShadow(expanded ? "^" : "v",
                    x + w - 9, y + 5, animated(guiTheme.getTextMutedColor()));
        }
    }

    // =========================================================
    //  Inline settings
    // =========================================================
    private int drawInlineSettings(int x, int startY, int w, Module mod,
                                   int mouseX, int mouseY,
                                   int clipTop, int clipBot) {
        int y = startY;

        // bind row
        if (y + SET_H >= clipTop && y < clipBot) {
            boolean hov = isHov(x, y, w, SET_H, mouseX, mouseY);
            String bindVal = waitingForBind ? "PRESS KEY..." : mod.getKeyName();
            drawSettingRow(x, y, w, "Bind", bindVal, 0, hov, 0xFFFFE082);
        }
        y += SET_H + GAP;

        List<Setting> main = getMainSettings(mod);
        for (int i = 0; i < main.size(); i++) {
            Setting s = main.get(i);
            if (y + SET_H >= clipTop && y < clipBot) {
                boolean hov = isHov(x, y, w, SET_H, mouseX, mouseY);
                drawSettingRow(x, y, w, s.getName(), getSettingValueText(s),
                        i + 1, hov, getSettingAccentColor(s));
            }
            y += SET_H + GAP;
        }

        if (mod instanceof BacktrackModule) {
            if (y + SET_H >= clipTop && y < clipBot) {
                boolean hov = isHov(x, y, w, SET_H, mouseX, mouseY);
                drawSettingRow(x, y, w, "Text Settings",
                        expandTextSettings ? "^" : ">",
                        main.size() + 1, hov, 0xFF56CCF2);
            }
            y += SET_H + GAP;

            if (expandTextSettings) {
                List<Setting> txt = getTextSettings(mod);
                for (int i = 0; i < txt.size(); i++) {
                    Setting s = txt.get(i);
                    if (y + SET_H >= clipTop && y < clipBot) {
                        boolean hov = isHov(x, y, w, SET_H, mouseX, mouseY);
                        drawSettingRow(x, y, w, s.getName(), getSettingValueText(s),
                                main.size() + 2 + i, hov, getSettingAccentColor(s));
                    }
                    y += SET_H + GAP;
                }
            }
        }
        return y;
    }

    private void drawSettingRow(int x, int y, int w, String left, String right,
                                int rowIdx, boolean hov, int accent) {
        int bg = hov ? guiTheme.getPanelHoverColor() : guiTheme.getRowAltColor(rowIdx);
        Gui.drawRect(x, y, x + w, y + SET_H, animated(bg));
        Gui.drawRect(x, y, x + 2, y + SET_H,
                animated(guiTheme.withAlpha(accent, hov ? 180 : 70)));

        fontRenderer.drawStringWithShadow(left, x + 6, y + 4,
                guiTheme.getTextPrimaryColor());

        if (right != null && !right.isEmpty()) {
            int rw = fontRenderer.getStringWidth(right);
            int rColor = "ON".equals(right)           ? guiTheme.getToggleOnColor()
                    : "OFF".equals(right)          ? guiTheme.getToggleOffColor()
                      : "PRESS KEY...".equals(right) ? animated(guiTheme.getAccentHoverColor())
                        :                                guiTheme.getTextSecondaryColor();
            fontRenderer.drawStringWithShadow(right, x + w - rw - 5, y + 4, rColor);
        }
    }

    // =========================================================
    //  Mouse click
    // =========================================================
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int btn) throws IOException {
        // title bar → drag
        if (btn == 0 && isHov(winX, winY, WIN_W, HEADER_H, mouseX, mouseY)) {
            dragging = true;
            dragOffX = mouseX - winX;
            dragOffY = mouseY - winY;
            super.mouseClicked(mouseX, mouseY, btn);
            return;
        }

        // category tabs
        int sideY = winY + HEADER_H + 1;
        int ty = sideY + 4;
        for (Category cat : Category.values()) {
            if (cat == Category.HUD) continue;
            if (isHov(winX, ty, CAT_W, CAT_H, mouseX, mouseY)) {
                if (selectedCategory != cat) {
                    selectedCategory = cat;
                    expandedModule   = null;
                    expandTextSettings = false;
                    waitingForBind   = false;
                    scrollOffset     = 0;
                }
                super.mouseClicked(mouseX, mouseY, btn);
                return;
            }
            ty += CAT_H + GAP;
        }

        // module content area
        int cntX = winX + CAT_W + 1;
        int cntY = sideY;
        int cntW = WIN_W - CAT_W - 1;
        int cntH = WIN_H - HEADER_H - 1;
        if (isHov(cntX, cntY, cntW, cntH, mouseX, mouseY)) {
            handleModuleContentClick(cntX, cntY, cntW, mouseX, mouseY, btn);
        }

        super.mouseClicked(mouseX, mouseY, btn);
    }

    private void handleModuleContentClick(int x, int startY, int cntW,
                                          int mouseX, int mouseY, int btn) {
        if (selectedCategory == null) return;
        int y = startY - scrollOffset;

        for (Module mod : getModulesForCategory(selectedCategory)) {
            if (isHov(x, y, cntW, MOD_H, mouseX, mouseY)) {
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
                return;
            }
            y += MOD_H + GAP;

            if (mod != expandedModule) continue;

            // bind
            if (isHov(x, y, cntW, SET_H, mouseX, mouseY)) {
                if (btn == 0) waitingForBind = !waitingForBind;
                return;
            }
            y += SET_H + GAP;

            for (Setting s : getMainSettings(mod)) {
                if (isHov(x, y, cntW, SET_H, mouseX, mouseY)) {
                    handleSettingClick(mod, s, btn);
                    return;
                }
                y += SET_H + GAP;
            }

            if (mod instanceof BacktrackModule) {
                if (isHov(x, y, cntW, SET_H, mouseX, mouseY)) {
                    expandTextSettings = !expandTextSettings;
                    return;
                }
                y += SET_H + GAP;

                if (expandTextSettings) {
                    for (Setting s : getTextSettings(mod)) {
                        if (isHov(x, y, cntW, SET_H, mouseX, mouseY)) {
                            handleSettingClick(mod, s, btn);
                            return;
                        }
                        y += SET_H + GAP;
                    }
                }
            }
        }
    }

    // =========================================================
    //  Scroll
    // =========================================================
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) return;

        int mx = Mouse.getEventX() * width / mc.displayWidth;
        int my = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        int cntX = winX + CAT_W + 1;
        int cntY = winY + HEADER_H + 1;
        int cntW = WIN_W - CAT_W - 1;
        int cntH = WIN_H - HEADER_H - 1;

        if (isHov(cntX, cntY, cntW, cntH, mx, my)) {
            scrollOffset += wheel > 0 ? -8 : 8;
            if (scrollOffset < 0) scrollOffset = 0;
        }
    }

    // =========================================================
    //  Drag
    // =========================================================
    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int btn, long time) {
        if (dragging) {
            winX = Math.max(0, Math.min(width  - WIN_W, mouseX - dragOffX));
            winY = Math.max(0, Math.min(height - WIN_H, mouseY - dragOffY));
        }
        super.mouseClickMove(mouseX, mouseY, btn, time);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int btn) {
        if (btn == 0) dragging = false;
        super.mouseReleased(mouseX, mouseY, btn);
    }

    // =========================================================
    //  Keyboard
    // =========================================================
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
        configManager.saveGuiPosition(winX, winY);
        mc.displayGuiScreen(null);
    }

    // =========================================================
    //  Helpers
    // =========================================================
    private int getModuleListHeight(List<Module> modules) {
        int h = 0;
        for (Module mod : modules) {
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
            if (m.getCategory() == cat) result.add(m);
        }
        return result;
    }

    private List<Setting> getMainSettings(Module mod) {
        List<Setting> result = new ArrayList<>();
        for (Setting s : mod.getSettings()) {
            if (s.getGroup() != SettingGroup.HUDTEXT
                    && s.getGroup() != SettingGroup.DEBUGWINDOW) {
                result.add(s);
            }
        }
        return result;
    }

    private List<Setting> getTextSettings(Module mod) {
        List<Setting> result = new ArrayList<>();
        for (Setting s : mod.getSettings()) {
            if (s.getGroup() == SettingGroup.HUDTEXT) result.add(s);
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
        configManager.saveBacktrackInfoPosition(moduleManager);
    }

    private boolean isHov(int x, int y, int w, int h, int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void drawOutline(int x, int y, int w, int h, int color) {
        Gui.drawRect(x,         y,         x + w, y + 1,     color);
        Gui.drawRect(x,         y + h - 1, x + w, y + h,     color);
        Gui.drawRect(x,         y,         x + 1, y + h,     color);
        Gui.drawRect(x + w - 1, y,         x + w, y + h,     color);
    }

    private void enableScissor(int x, int y, int w, int h) {
        double scaleX = (double) mc.displayWidth  / width;
        double scaleY = (double) mc.displayHeight / height;
        int sx = (int)(x * scaleX);
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