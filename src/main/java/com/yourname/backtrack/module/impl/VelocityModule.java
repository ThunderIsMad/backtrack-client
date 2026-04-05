package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

public class VelocityModule extends Module {

    private final NumberSetting horizontal = new NumberSetting("Horizontal", 100, 0, 100, 1);
    private final NumberSetting vertical = new NumberSetting("Vertical", 100, 0, 100, 1);
    private final NumberSetting chance = new NumberSetting("Chance", 100, 0, 100, 1);

    public VelocityModule() {
        super("Velocity", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(horizontal, vertical, chance);
        addHudSettings();
    }

    public double getHorizontal() { return horizontal.getValue(); }
    public double getVertical() { return vertical.getValue(); }
    public double getChance() { return chance.getValue(); }

    @Override
    public String getHudText() {
        return String.format("Vel H:%.0f V:%.0f C:%.0f",
                horizontal.getValue(), vertical.getValue(), chance.getValue());
    }
}