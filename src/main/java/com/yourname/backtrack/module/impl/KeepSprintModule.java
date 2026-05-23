package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.client.ClientSimulator;
import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.NumberSetting;
import org.lwjgl.input.Keyboard;

public class KeepSprintModule extends Module {

    private final BooleanSetting onlyOnHit  = new BooleanSetting("Only On Hit", true);
    private final NumberSetting  healthMin  = new NumberSetting("Min Health", 0.0, 0.0, 20.0, 0.5);

    // Intave bypass: delay sprint after taking damage to avoid sprint-prediction mismatch
    private final NumberSetting  postHurtDelay = new NumberSetting("PostHurtDelay", 4, 0, 10, 1);

    private int ticksSinceHurt = 999; // large: allow sprint initially
    private float lastHealth = 20.0f;

    public KeepSprintModule() {
        super("KeepSprint", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSettings(onlyOnHit, healthMin, postHurtDelay);
        addHudSettings();
    }

    @Override
    public void onClientTick() {
        if (!isEnabled() || mc().player == null) return;
        if (ClientSimulator.INSTANCE.getPastExternalVelocity() < (int) postHurtDelay.getValue()) {
                mc().player.setSprinting(false);
                return;
            }

            // Standard sprint logic
        if (!mc().player.isSprinting() && mc().player.moveForward > 0
                && !mc().player.isSneaking()
                && mc().player.getFoodStats().getFoodLevel() > 6) {
                mc().player.setSprinting(true);
            }
        }
    }