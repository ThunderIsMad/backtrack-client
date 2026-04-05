package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.client.event.InputUpdateEvent;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

public class FullBrightModule extends Module {

    private final ModeSetting preset = new ModeSetting(
            "Preset",
            Arrays.asList("Custom", "Soft", "Normal", "Max"),
            "Custom"
    );

    private final NumberSetting gammaValue = new NumberSetting("Gamma", 100.0, 1.0, 100.0, 1.0);

    private float previousGamma = 1.0F;
    private boolean saved = false;

    public FullBrightModule() {
        super("FullBright", Category.RENDER, Keyboard.KEY_H);

        addSettings(
                preset,
                gammaValue
        );

        addHudSettings();
    }

    private float getTargetGamma() {
        if (preset.getValue().equals("Soft")) {
            return 25.0F;
        }

        if (preset.getValue().equals("Normal")) {
            return 50.0F;
        }

        if (preset.getValue().equals("Max")) {
            return 100.0F;
        }

        return (float) gammaValue.getValue();
    }

    @Override
    protected void onEnable() {
        super.onEnable();

        if (mc.gameSettings != null) {
            GameSettings gs = mc.gameSettings;

            if (!saved) {
                previousGamma = gs.gammaSetting;
                saved = true;
            }

            gs.gammaSetting = getTargetGamma();
        }
    }

    @Override
    protected void onDisable() {
        super.onDisable();

        if (mc.gameSettings != null && saved) {
            mc.gameSettings.gammaSetting = previousGamma;
        }
    }

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        if (isEnabled() && mc.gameSettings != null) {
            float target = getTargetGamma();
            if (mc.gameSettings.gammaSetting != target) {
                mc.gameSettings.gammaSetting = target;
            }
        }
    }
}

