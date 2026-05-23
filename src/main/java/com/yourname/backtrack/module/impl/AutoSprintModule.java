package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.client.ClientSimulator;
import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

public class AutoSprintModule extends Module {

    private final ModeSetting mode = new ModeSetting("Mode",
            Arrays.asList("Simple", "Intave"), "Intave");
    private final BooleanSetting requireForward = new BooleanSetting("Require Forward", true);
    private final BooleanSetting allowSneak     = new BooleanSetting("Allow Sneak", false);
    private final NumberSetting  postHurtTicks  = new NumberSetting("PostHurt Ticks", 10, 0, 20, 1);
    private final BooleanSetting hungerCheck    = new BooleanSetting("Hunger Check", true);

    public AutoSprintModule() {
        super("AutoSprint", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSettings(mode, requireForward, allowSneak, postHurtTicks, hungerCheck);
        addHudSettings();
    }

    @Override
    public void onClientTick() {
        if (!isEnabled() || mc().player == null || mc().world == null) return;

        if (hungerCheck.getValue() && mc().player.getFoodStats().getFoodLevel() <= 6) {
            mc().player.setSprinting(false);
            return;
        }

        if (mode.getValue().equals("Intave")) {
            if (ClientSimulator.INSTANCE.getPastExternalVelocity() < postHurtTicks.getValue()) {
                mc().player.setSprinting(false);
                return;
            }
        }

        if (requireForward.getValue() && mc().player.moveForward <= 0) return;
        if (!allowSneak.getValue() && mc().player.isSneaking()) return;

        if (!mc().player.isSprinting()) {
            mc().player.setSprinting(true);
        }
    }

    @Override
    public String getHudText() {
        return "AutoSprint " + mode.getValue();
    }
}