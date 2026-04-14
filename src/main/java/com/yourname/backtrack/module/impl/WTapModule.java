package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraftforge.client.event.InputUpdateEvent;
import org.lwjgl.input.Keyboard;

public class WTapModule extends Module {

    private final BooleanSetting onlyOnGround = new BooleanSetting("Only On Ground", true);
    private final NumberSetting  stopTicks    = new NumberSetting("Stop Ticks", 1.0, 1.0, 3.0, 1.0);

    private int lastHurtTime  = 0;
    private int stopTickTimer = 0;

    public WTapModule() {
        super("WTap", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(onlyOnGround, stopTicks);
        addHudSettings();
    }

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isEnabled() || mc().player == null) return;

        int hurtTime = mc().player.hurtTime;

        if (hurtTime > 0 && lastHurtTime == 0) {
            if (!onlyOnGround.getValue() || mc().player.onGround) {
                stopTickTimer = (int) stopTicks.getValue();
            }
        }

        if (stopTickTimer > 0) {
            mc().player.setSprinting(false);
            stopTickTimer--;
        }

        lastHurtTime = hurtTime;
    }
}
