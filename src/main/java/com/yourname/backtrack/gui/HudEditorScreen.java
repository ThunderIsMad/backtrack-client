package com.yourname.backtrack.gui;

import com.yourname.backtrack.config.ConfigManager;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.module.ModuleHudSettings;
import com.yourname.backtrack.module.ModuleManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class HudEditorScreen extends GuiScreen {

    private final GuiScreen parent;
    private final ModuleManager moduleManager;
    private final ConfigManager configManager;
    private final GuiTheme guiTheme;

    private Module draggingModule;
    private Module selectedModule;
    private int dragOffsetX;
    private int dragOffsetY;

    public HudEditorScreen(GuiScreen parent, ModuleManager moduleManager, ConfigManager configManager, GuiTheme guiTheme) {
        this.parent = parent;
        this.moduleManager = moduleManager;
        this.configManager = configManager;
        this.guiTheme = guiTheme;

        if (!moduleManager.getModules().isEmpty()) {
            this.selectedModule = moduleManager.getModules().get(0);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        Gui.drawRect(20, 20, 250, 74, guiTheme.getTitleColor());
        fontRenderer.drawStringWithShadow("HUD Editor", 28, 28, 0xFFFFFF);
        fontRenderer.drawStringWithShadow("LMB - drag selected box", 28, 42, 0xFFFFFF);
        fontRenderer.drawStringWithShadow("ESC - back to ClickGUI", 28, 56, 0xAAAAAA);

        for (Module module : moduleManager.getModules()) {
            drawModulePreview(module);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawModulePreview(Module module) {
        ModuleHudSettings hud = module.getHudSettings();
        String text = module.getName() + " [ON]";
        int x = hud.getX();
        int y = hud.getY();
        int width = fontRenderer.getStringWidth(text) + 8;
        int height = 14;

        int boxColor = module == selectedModule ? guiTheme.getAccentColor() : guiTheme.getPanelColor();
        Gui.drawRect(x, y, x + width, y + height, boxColor);

        if (hud.isBackground()) {
            Gui.drawRect(x - 2, y - 2, x + width + 2, y + height + 2, 0x50000000);
        }

        if (hud.isShadow()) {
            fontRenderer.drawStringWithShadow(text, x + 4, y + 3, hud.getTextColor());
        } else {
            fontRenderer.drawString(text, x + 4, y + 3, hud.getTextColor());
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        for (Module module : moduleManager.getModules()) {
            ModuleHudSettings hud = module.getHudSettings();
            String text = module.getName() + " [ON]";
            int boxWidth = fontRenderer.getStringWidth(text) + 8;
            int boxHeight = 14;

            if (mouseButton == 0 && isHovered(hud.getX(), hud.getY(), boxWidth, boxHeight, mouseX, mouseY)) {
                selectedModule = module;
                draggingModule = module;
                dragOffsetX = mouseX - hud.getX();
                dragOffsetY = mouseY - hud.getY();
                break;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingModule != null && clickedMouseButton == 0) {
            ModuleHudSettings hud = draggingModule.getHudSettings();
            hud.setX(mouseX - dragOffsetX);
            hud.setY(mouseY - dragOffsetY);
            clampHudToScreen(draggingModule);
        }

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (draggingModule != null) {
            draggingModule = null;
            configManager.saveModuleHudSettings(moduleManager);
        }

        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            configManager.saveModuleHudSettings(moduleManager);
            mc.displayGuiScreen(parent);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    private void clampHudToScreen(Module module) {
        ModuleHudSettings hud = module.getHudSettings();
        String text = module.getName() + " [ON]";
        int boxWidth = fontRenderer.getStringWidth(text) + 8;
        int boxHeight = 14;

        if (hud.getX() < 0) {
            hud.setX(0);
        }

        if (hud.getY() < 0) {
            hud.setY(0);
        }

        if (hud.getX() + boxWidth > width) {
            hud.setX(width - boxWidth);
        }

        if (hud.getY() + boxHeight > height) {
            hud.setY(height - boxHeight);
        }
    }

    private boolean isHovered(int x, int y, int width, int height, int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void onGuiClosed() {
        configManager.saveModuleHudSettings(moduleManager);
        super.onGuiClosed();
    }
}

