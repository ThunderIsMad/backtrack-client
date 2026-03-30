package com.yourname.backtrack.module;

public class ModuleHudSettings {

    private final String moduleName;

    private int x = 5;
    private int y = 5;

    private int defaultX = 5;
    private int defaultY = 5;

    private boolean visible = true;
    private boolean shadow = true;
    private boolean background = false;

    private int colorIndex = 0;

    public ModuleHudSettings(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getModuleName() {
        return moduleName;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getDefaultX() {
        return defaultX;
    }

    public int getDefaultY() {
        return defaultY;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void toggleVisible() {
        visible = !visible;
    }

    public boolean isShadow() {
        return shadow;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

    public void toggleShadow() {
        shadow = !shadow;
    }

    public boolean isBackground() {
        return background;
    }

    public void setBackground(boolean background) {
        this.background = background;
    }

    public void toggleBackground() {
        background = !background;
    }

    public int getColorIndex() {
        return colorIndex;
    }

    public void setColorIndex(int colorIndex) {
        this.colorIndex = (colorIndex < 0 || colorIndex > 5) ? 0 : colorIndex;
    }

    public void cycleColor() {
        colorIndex++;

        if (colorIndex > 5) {
            colorIndex = 0;
        }
    }

    public void setDefaultPosition(int x, int y) {
        this.defaultX = x;
        this.defaultY = y;
        this.x = x;
        this.y = y;
    }

    public void resetToDefault() {
        this.x = defaultX;
        this.y = defaultY;
    }

    public String getColorName() {
        switch (colorIndex) {
            case 1:
                return "CYAN";
            case 2:
                return "PURPLE";
            case 3:
                return "GREEN";
            case 4:
                return "RED";
            case 5:
                return "GOLD";
            default:
                return "BLUE";
        }
    }

    public int getTextColor() {
        switch (colorIndex) {
            case 1:
                return 0x3DE1D5;
            case 2:
                return 0x9B59FF;
            case 3:
                return 0x4CD964;
            case 4:
                return 0xFF5A6A;
            case 5:
                return 0xFFB347;
            default:
                return 0x4AA3FF;
        }
    }
}
