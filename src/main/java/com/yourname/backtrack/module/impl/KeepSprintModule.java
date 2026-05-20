package com.yourname.backtrack.module.impl;

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
        if (!isEnabled() || mc().player == null || mc().world == null) return;

        // Track health
        float health = mc().player.getHealth();
        lastHealth = health;

        // Track hurt timer to delay sprint after being hit (Intave sprint/flight checks)
        if (mc().player.hurtTime > 0) {
            ticksSinceHurt = 0;
        } else if (ticksSinceHurt < 999) {
            ticksSinceHurt++;
        }

        if (mc().player.getHealth() < (float) healthMin.getValue()) return;
        if (mc().player.getFoodStats().getFoodLevel() <= 6) return;

        // Intave bypass: respect sprint delay window after being hit
        if (ticksSinceHurt < (int) postHurtDelay.getValue()) return;

        // Standard sprint logic
        if (!mc().player.isSprinting()
                && mc().player.moveForward > 0
                && !mc().player.isSneaking()) {
            mc().player.setSprinting(true);
        }
    }

    @Override
    public void onDisable() {
        if (mc().player == null) return;
        mc().player.setSprinting(false);
        ticksSinceHurt = 999;
    }
}