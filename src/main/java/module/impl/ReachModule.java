package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

public class ReachModule extends Module {

    private final NumberSetting reach = new NumberSetting(
            "Reach", 3.5, 3.0, 6.0, 0.1);

    public ReachModule() {
        super("Reach", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(reach);
        addHudSettings();
    }

    public double getReachValue() {
        return reach.getValue();
    }

    @Override
    public String getHudText() {
        return String.format("Reach %.1f", reach.getValue());
    }
}