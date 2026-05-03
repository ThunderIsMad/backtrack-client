package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraftforge.client.event.InputUpdateEvent;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

public class AutoSprintModule extends Module {

    private final ModeSetting mode = new ModeSetting(
            "Mode",
            Arrays.asList("Simple", "Intave"),
            "Intave"
    );

    private final BooleanSetting requireForward = new BooleanSetting("Require Forward", true);
    private final BooleanSetting allowSneak     = new BooleanSetting("Allow Sneak", false);

    // Intave: number of ticks after being hit during which sprint is blocked
    // 10 ticks = exactly the pastExternalVelocity < 10 window that Intave uses for
    // velocity tolerance. Keeping sprint off for this window makes the player's
    // horizontal speed match the server's "no-sprint" prediction and avoids
    // accumulating physicsVelocityVL.
    private final NumberSetting  postHurtTicks  = new NumberSetting("PostHurt Ticks", 10, 0, 20, 1);
    private final BooleanSetting hungerCheck    = new BooleanSetting("Hunger Check", true);

    private int ticksSinceHurt = 999;   // large so sprint is allowed initially
    private boolean sprintBlocked = false;

    public AutoSprintModule() {
        super("AutoSprint", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSettings(mode, requireForward, allowSneak, postHurtTicks, hungerCheck);
        addHudSettings();
    }

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        if (mc().player == null || mc().world == null) return;

        if (hungerCheck.getValue() && mc().player.getFoodStats().getFoodLevel() <= 6) {
            mc().player.setSprinting(false);
            return;
        }

        // Track hurt timer (10 = just hit, counts down to 0)
        if (mc().player.hurtTime == 10) {
            ticksSinceHurt = 0;
            sprintBlocked = true;
            mc().player.setSprinting(false);
        } else if (mc().player.hurtTime > 0) {
            ticksSinceHurt++;
        } else {
            // hurtTime == 0 reset counter only when not actively hurt
            if (ticksSinceHurt < 999) ticksSinceHurt++;
        }

        // Intave mode: block sprint for postHurtTicks ticks after being hit
        if (mode.getValue().equals("Intave")) {
            if (sprintBlocked) {
                if (ticksSinceHurt >= (int) postHurtTicks.getValue()) {
                    sprintBlocked = false;
                } else {
                    mc().player.setSprinting(false);
                    return;
                }
            }
        }

        // Standard sprint logic
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