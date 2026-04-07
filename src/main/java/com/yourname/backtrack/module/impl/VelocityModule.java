package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

public class VelocityModule extends Module {

    private final ModeSetting mode = new ModeSetting(
            "Mode",
            Arrays.asList("Normal", "Cancel", "Reverse", "JumpReset", "Legit"),
            "Normal"
    );

    private final NumberSetting horizontal = new NumberSetting("Horizontal", 100, 0, 100, 1);
    private final NumberSetting vertical = new NumberSetting("Vertical", 100, 0, 100, 1);
    private final NumberSetting chance = new NumberSetting("Chance", 100, 0, 100, 1);

    public VelocityModule() {
        super("Velocity", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(mode, horizontal, vertical, chance);
        addHudSettings();
    }

    public String getMode() { return mode.getValue(); }
    public double getHorizontal() { return horizontal.getValue(); }
    public double getVertical() { return vertical.getValue(); }
    public double getChance() { return chance.getValue(); }

    @Override
    public String getHudText() {
        return String.format("Vel %s H:%.0f V:%.0f C:%.0f",
                mode.getValue(), horizontal.getValue(), vertical.getValue(), chance.getValue());
    }
}