package com.yourname.backtrack.gui;

import com.yourname.backtrack.config.ConfigManager;
import com.yourname.backtrack.module.ModuleManager;
import com.yourname.backtrack.module.impl.BacktrackModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class BacktrackInfoEditorScreen extends GuiScreen {

    private final GuiScreen parent;
    private final ModuleManager moduleManager;
    private final ConfigManager configManager;
    private final GuiTheme guiTheme;
    private final int panelWidth = 150;
    private final int panelHeight = 34;
    private final BacktrackModule backtrackModule;
    private boolean dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public BacktrackInfoEditorScreen(GuiScreen parent, BacktrackModule backtrackModule, ModuleManager moduleManager, ConfigManager configManager, GuiTheme guiTheme) {
        this.parent = parent;
        this.backtrackModule = backtrackModule;
        this.moduleManager = moduleManager;
        this.configManager = configManager;
        this.guiTheme = guiTheme;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        Gui.drawRect(20, 20, 255, 74, guiTheme.getTitleColor());
        fontRenderer.drawStringWithShadow("Backtrack Info Editor", 28, 28, 0xFFFFFF);
        fontRenderer.drawStringWithShadow("Drag panel with LMB", 28, 42, 0xFFFFFF);
        fontRenderer.drawStringWithShadow("ESC - back to ClickGUI", 28, 56, 0xAAAAAA);

        if (backtrackModule != null) {
            int x = backtrackModule.getInfoX();
            int y = backtrackModule.getInfoY();

            if (x == 0 && y == 0) {
                x = width / 2 - panelWidth / 2;
                y = 8;
                backtrackModule.setInfoX(x);
                backtrackModule.setInfoY(y);
            }

            Gui.drawRect(x, y, x + panelWidth, y + panelHeight, 0x90000000);
            Gui.drawRect(x, y, x + panelWidth, y + 2, guiTheme.getAccentColor());

            fontRenderer.drawStringWithShadow("Backtrack Debug", x + 5, y + 5, 0xFFFFFF);
            fontRenderer.drawStringWithShadow("Target: DemoTarget (3.4m)", x + 5, y + 15, 0xFFFFFF);
            fontRenderer.drawStringWithShadow("State: WARMUP | 120ms", x + 5, y + 25, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (backtrackModule != null) {
            int x = backtrackModule.getInfoX();
            int y = backtrackModule.getInfoY();

            if (mouseButton == 0 && isHovered(x, y, panelWidth, panelHeight, mouseX, mouseY)) {
                dragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging && backtrackModule != null && clickedMouseButton == 0) {
            backtrackModule.setInfoX(mouseX - dragOffsetX);
            backtrackModule.setInfoY(mouseY - dragOffsetY);
            clampToScreen();
        }

        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (dragging) {
            dragging = false;
            configManager.saveBacktrackInfoPosition(moduleManager);
        }

        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            configManager.saveBacktrackInfoPosition(moduleManager);
            mc.displayGuiScreen(parent);
            return;
        }

        super.keyTyped(typedChar, keyCode);
    }

    private void clampToScreen() {
        if (backtrackModule == null) {
            return;
        }

        if (backtrackModule.getInfoX() < 0) {
            backtrackModule.setInfoX(0);
        }

        if (backtrackModule.getInfoY() < 0) {
            backtrackModule.setInfoY(0);
        }

        if (backtrackModule.getInfoX() + panelWidth > width) {
            backtrackModule.setInfoX(width - panelWidth);
        }

        if (backtrackModule.getInfoY() + panelHeight > height) {
            backtrackModule.setInfoY(height - panelHeight);
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
        configManager.saveBacktrackInfoPosition(moduleManager);
        super.onGuiClosed();
    }
}

