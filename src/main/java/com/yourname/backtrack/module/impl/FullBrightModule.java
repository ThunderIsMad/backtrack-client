package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class FullBrightModule extends Module {

    private final NumberSetting gammaValue = new NumberSetting("Gamma", 10.0, 1.0, 20.0, 0.5);

    private float previousGamma = 1.0f;
    private boolean saved = false;

    public FullBrightModule() {
        super("FullBright", Category.RENDER, Keyboard.KEY_NONE);
        addSettings(gammaValue);
        addHudSettings();
    }

    @Override
    public void onEnable() {
        if (mc().gameSettings != null) {
            GameSettings gs = mc().gameSettings;
            previousGamma = gs.gammaSetting;
            saved = true;
        }
    }

    @Override
    public void onDisable() {
        if (mc().gameSettings != null && saved) {
            mc().gameSettings.gammaSetting = previousGamma;
        }
        saved = false;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (isEnabled() && mc().gameSettings != null) {
            float target = (float) gammaValue.getValue();
            if (mc().gameSettings.gammaSetting != target) {
                mc().gameSettings.gammaSetting = target;
            }
        }
    }
}
