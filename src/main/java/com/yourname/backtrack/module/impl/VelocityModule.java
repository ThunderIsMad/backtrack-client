package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
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
    private final NumberSetting vertical   = new NumberSetting("Vertical",   100, 0, 100, 1);
    private final NumberSetting chance     = new NumberSetting("Chance",     100, 0, 100, 1);
    private final BooleanSetting debug     = new BooleanSetting("Debug", false);

    // Written by the mixin on every intercepted packet
    private volatile double lastRawX, lastRawY, lastRawZ;
    private volatile double lastAppliedX, lastAppliedY, lastAppliedZ;
    private volatile boolean hadPacket = false;

    public VelocityModule() {
        super("Velocity", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(mode, horizontal, vertical, chance, debug);
        addHudSettings();
    }

    public String getMode()       { return mode.getValue(); }
    public double getHorizontal() { return horizontal.getValue(); }
    public double getVertical()   { return vertical.getValue(); }
    public double getChance()     { return chance.getValue(); }
    public boolean isDebug()      { return debug.getValue(); }

    /** Called by the mixin after each intercepted velocity packet. */
    public void recordPacket(double rawX, double rawY, double rawZ,
                             double appX, double appY, double appZ) {
        lastRawX = rawX; lastRawY = rawY; lastRawZ = rawZ;
        lastAppliedX = appX; lastAppliedY = appY; lastAppliedZ = appZ;
        hadPacket = true;
    }

    @Override
    public String getHudText() {
        if (debug.getValue() && hadPacket) {
            return String.format(
                "Vel %s H:%.0f V:%.0f C:%.0f | Raw X:%.3f Y:%.3f Z:%.3f | Got X:%.3f Y:%.3f Z:%.3f",
                mode.getValue(),
                horizontal.getValue(), vertical.getValue(), chance.getValue(),
                lastRawX, lastRawY, lastRawZ,
                lastAppliedX, lastAppliedY, lastAppliedZ);
        }
        return String.format("Vel %s H:%.0f V:%.0f C:%.0f",
                mode.getValue(), horizontal.getValue(), vertical.getValue(), chance.getValue());
    }
}
