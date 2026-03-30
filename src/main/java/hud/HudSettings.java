package com.yourname.backtrack.hud;

public class HudSettings {

    public static final int TOP_LEFT = 0;
    public static final int TOP_RIGHT = 1;
    public static final int BOTTOM_LEFT = 2;
    public static final int BOTTOM_RIGHT = 3;

    public static final int TEXT_MODE_NAME_ONLY = 0;
    public static final int TEXT_MODE_NAME_STATUS = 1;
    public static final int TEXT_MODE_COMPACT = 2;
    private final int[] colors = {
            0xFFFFFF,
            0x55FF55,
            0x55FFFF,
            0xFFAA00,
            0xFF5555,
            0xAA55FF
    };
    private int x = 5;
    private int y = 5;
    private int lineHeight = 12;
    private boolean visible = true;
    private boolean shadow = true;
    private boolean background = true;
    private int colorIndex = 0;
    private int anchorIndex = TOP_LEFT;
    private int textMode = TEXT_MODE_NAME_STATUS;

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = Math.max(0, x);
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = Math.max(0, y);
    }

    public int getLineHeight() {
        return lineHeight;
    }

    public void setLineHeight(int lineHeight) {
        this.lineHeight = Math.max(8, Math.min(30, lineHeight));
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isShadow() {
        return shadow;
    }

    public void setShadow(boolean shadow) {
        this.shadow = shadow;
    }

    public boolean isBackground() {
        return background;
    }

    public void setBackground(boolean background) {
        this.background = background;
    }

    public int getCurrentColor() {
        return colors[colorIndex];
    }

    public int getColorIndex() {
        return colorIndex;
    }

    public void setColorIndex(int colorIndex) {
        this.colorIndex = Math.max(0, Math.min(colors.length - 1, colorIndex));
    }

    public int getAnchorIndex() {
        return anchorIndex;
    }

    public void setAnchorIndex(int anchorIndex) {
        this.anchorIndex = Math.max(TOP_LEFT, Math.min(BOTTOM_RIGHT, anchorIndex));
    }

    public int getTextMode() {
        return textMode;
    }

    public void setTextMode(int textMode) {
        this.textMode = Math.max(TEXT_MODE_NAME_ONLY, Math.min(TEXT_MODE_COMPACT, textMode));
    }

    public String getAnchorName() {
        switch (anchorIndex) {
            case TOP_RIGHT:
                return "TOP_RIGHT";
            case BOTTOM_LEFT:
                return "BOTTOM_LEFT";
            case BOTTOM_RIGHT:
                return "BOTTOM_RIGHT";
            default:
                return "TOP_LEFT";
        }
    }

    public String getTextModeName() {
        switch (textMode) {
            case TEXT_MODE_NAME_ONLY:
                return "NAME_ONLY";
            case TEXT_MODE_COMPACT:
                return "COMPACT";
            default:
                return "NAME_STATUS";
        }
    }

    public void moveLeft() {
        x = Math.max(0, x - 2);
    }

    public void moveRight() {
        x += 2;
    }

    public void moveUp() {
        y = Math.max(0, y - 2);
    }

    public void moveDown() {
        y += 2;
    }

    public void cycleColor() {
        colorIndex++;
        if (colorIndex >= colors.length) {
            colorIndex = 0;
        }
    }

    public void cycleAnchor() {
        anchorIndex++;
        if (anchorIndex > BOTTOM_RIGHT) {
            anchorIndex = TOP_LEFT;
        }
    }

    public void cycleTextMode() {
        textMode++;
        if (textMode > TEXT_MODE_COMPACT) {
            textMode = TEXT_MODE_NAME_ONLY;
        }
    }

    public void toggleVisible() {
        visible = !visible;
    }

    public void toggleShadow() {
        shadow = !shadow;
    }

    public void toggleBackground() {
        background = !background;
    }

    public void increaseLineHeight() {
        lineHeight = Math.min(30, lineHeight + 1);
    }

    public void decreaseLineHeight() {
        lineHeight = Math.max(8, lineHeight - 1);
    }

    public void reset() {
        x = 5;
        y = 5;
        lineHeight = 12;
        visible = true;
        shadow = true;
        background = true;
        colorIndex = 0;
        anchorIndex = TOP_LEFT;
        textMode = TEXT_MODE_NAME_STATUS;
    }
}
