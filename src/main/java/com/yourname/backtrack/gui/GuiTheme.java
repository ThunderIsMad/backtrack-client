package com.yourname.backtrack.gui;

public class GuiTheme {

    private int accentIndex = 0;
    private int backgroundIndex = 0;

    // ─── Accent ───────────────────────────────────────────────────────────────

    public int getAccentIndex() {
        return accentIndex;
    }

    public void setAccentIndex(int accentIndex) {
        this.accentIndex = Math.max(0, Math.min(5, accentIndex));
    }

    public void cycleAccentColor() {
        accentIndex = (accentIndex + 1) % 6;
    }

    public String getAccentName() {
        switch (accentIndex) {
            case 1: return "CYAN";
            case 2: return "PURPLE";
            case 3: return "GREEN";
            case 4: return "RED";
            case 5: return "GOLD";
            default: return "BLUE";
        }
    }

    // ─── Background ───────────────────────────────────────────────────────────

    public int getBackgroundIndex() {
        return backgroundIndex;
    }

    public void setBackgroundIndex(int backgroundIndex) {
        this.backgroundIndex = Math.max(0, Math.min(3, backgroundIndex));
    }

    public void cycleBackgroundStyle() {
        backgroundIndex = (backgroundIndex + 1) % 4;
    }

    public String getBackgroundName() {
        switch (backgroundIndex) {
            case 1: return "SLATE";
            case 2: return "PLUM";
            case 3: return "FOREST";
            default: return "OBSIDIAN";
        }
    }

    public void reset() {
        accentIndex = 0;
        backgroundIndex = 0;
    }

    // ─── Accent colors ────────────────────────────────────────────────────────

    public int getAccentTextColor() {
        return getAccentRgb();
    }

    public int getAccentColor() {
        return withAlpha(getAccentRgb(), 160);
    }

    public int getAccentHoverColor() {
        return withAlpha(getAccentRgb(), 210);
    }

    public int getToggleOnColor() {
        return withAlpha(getAccentRgb(), 230);
    }

    public int getStrokeColor() {
        return withAlpha(getAccentRgb(), 92);
    }

    public int getHoverGlowColor(float strength) {
        int alpha = (int) (15 + 35 * clamp01(strength));
        return withAlpha(getAccentRgb(), alpha);
    }

    // ─── Background colors ────────────────────────────────────────────────────

    public int getTitleColor() {
        switch (backgroundIndex) {
            case 1: return 0xE6181E28;
            case 2: return 0xE6221528;
            case 3: return 0xE6131E19;
            default: return 0xE6131519;
        }
    }

    public int getPanelColor() {
        switch (backgroundIndex) {
            case 1: return 0xD81A222D;
            case 2: return 0xD81A101F;
            case 3: return 0xD80F1712;
            default: return 0xD8141720;
        }
    }

    public int getPanelHoverColor() {
        switch (backgroundIndex) {
            case 1: return 0xE0232D38;
            case 2: return 0xE023172B;
            case 3: return 0xE016241C;
            default: return 0xE01D212A;
        }
    }

    public int getWindowGlassColor() {
        switch (backgroundIndex) {
            case 1: return 0xCC161D27;
            case 2: return 0xCC1E1222;
            case 3: return 0xCC101B14;
            default: return 0xCC0F1218;
        }
    }

    public int getBackdropColor() {
        switch (backgroundIndex) {
            case 1: return 0xB00B0E14;
            case 2: return 0xB0100A12;
            case 3: return 0xB00A110C;
            default: return 0xB0080A0E;
        }
    }

    public int getSoftShadowColor() {
        return 0x60000000;
    }

    public int getToggleOffColor() {
        return 0x80485564;
    }

    // ─── Text colors ──────────────────────────────────────────────────────────

    public int getTextPrimaryColor() {
        return 0xFFECEFF7;
    }

    public int getTextSecondaryColor() {
        return 0xFFB8C2D6;
    }

    public int getTextMutedColor() {
        return 0xFF92A0BA;
    }

    public int getRowAltColor(int rowIndex) {
        int base = getPanelColor();
        int offset = rowIndex % 2 == 0 ? 0 : -10;
        return shiftColor(base, offset);
    }

    // ─── Animation helpers ────────────────────────────────────────────────────

    public int blend(int a, int b, float t) {
        float x = clamp01(t);
        int aa = (a >> 24) & 255, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = (b >> 24) & 255, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        int ca = (int) (aa + (ba - aa) * x);
        int cr = (int) (ar + (br - ar) * x);
        int cg = (int) (ag + (bg - ag) * x);
        int cb = (int) (ab + (bb - ab) * x);
        return (ca << 24) | (cr << 16) | (cg << 8) | cb;
    }

    public float animateTowards(float current, float target, float speed) {
        return current + (target - current) * clamp01(speed);
    }

    public int pulseAlpha(int color, float speed, float phaseOffset, long timeMs) {
        float wave = (float) ((Math.sin((timeMs * speed) + phaseOffset) + 1.0D) * 0.5D);
        int alpha = (int) (((color >> 24) & 255) * (0.62F + 0.38F * wave));
        return withAlpha(color, alpha);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private int getAccentRgb() {
        switch (accentIndex) {
            case 1: return 0x3DE1D5;
            case 2: return 0x9B59FF;
            case 3: return 0x4CD964;
            case 4: return 0xFF5A6A;
            case 5: return 0xFFB347;
            default: return 0x4AA3FF;
        }
    }

    public int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private int shiftColor(int color, int amount) {
        int a = (color >> 24) & 255;
        int r = clampRgb(((color >> 16) & 255) + amount);
        int g = clampRgb(((color >> 8) & 255) + amount);
        int b = clampRgb((color & 255) + amount);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int clampRgb(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}