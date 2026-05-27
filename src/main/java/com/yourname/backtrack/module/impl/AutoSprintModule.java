package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.client.ClientSimulator;
import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.client.settings.KeyBinding;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

public class AutoSprintModule extends Module {

    private final ModeSetting mode = new ModeSetting("Mode",
            Arrays.asList("Simple", "Intave"), "Intave");
    private final BooleanSetting requireForward = new BooleanSetting("Require Forward", true);
    private final BooleanSetting allowSneak     = new BooleanSetting("Allow Sneak", false);
    private final NumberSetting  postHurtTicks  = new NumberSetting("PostHurt Ticks", 10, 0, 20, 1);
    private final BooleanSetting hungerCheck    = new BooleanSetting("Hunger Check", true);
    private final BooleanSetting debug          = new BooleanSetting("Debug", false);

    private int sprintBlockedTicks = 0;
    private int sprintRetryCounter = 0;

    public AutoSprintModule() {
        super("AutoSprint", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSettings(mode, requireForward, allowSneak, postHurtTicks, hungerCheck, debug);
        addHudSettings();
    }

    @Override
    public void onClientTick() {
        if (!isEnabled() || mc().player == null || mc().world == null) return;

        // Disable sprint if food level is too low
        if (hungerCheck.getValue() && mc().player.getFoodStats().getFoodLevel() <= 6) {
            mc().player.setSprinting(false);
            return;
        }

        if (!mode.getValue().equals("Intave")) {
            simpleSprint();
            return;
        }

        // Intave mode: block sprint after taking damage for the configured window
        int pev = ClientSimulator.INSTANCE.getPastExternalVelocity();

        if (mc().player.hurtTime > 0 && mc().player.isSprinting()) {
            sprintBlockedTicks = (int) postHurtTicks.getValue();
            sprintRetryCounter = 0;

            if (debug.getValue()) {
                sendClientMessage("§cSprint §7blocked for " + sprintBlockedTicks + " ticks (pev=" + pev + ")");
            }
        }

        // Hold sprint off during the block window
        if (sprintBlockedTicks > 0) {
            mc().player.setSprinting(false);
            sprintBlockedTicks--;
            sprintRetryCounter = 0;
            return;
        }

        // Already sprinting — nothing to do
        if (mc().player.isSprinting()) return;

        // Check conditions for enabling sprint
        if (requireForward.getValue() && mc().player.moveForward <= 0) return;
        if (!allowSneak.getValue() && mc().player.isSneaking()) return;

        // Try to enable sprint
        mc().player.setSprinting(true);
        // Hold the virtual sprint key for persistence
        KeyBinding.setKeyBindState(mc().gameSettings.keyBindSprint.getKeyCode(), true);

        if (!mc().player.isSprinting()) {
            // setSprinting failed (vanilla toggle timer active) — retry next tick
            sprintRetryCounter++;
            if (debug.getValue() && sprintRetryCounter <= 1) {
                sendClientMessage("§eSprint §7toggle block, retrying (count=" + sprintRetryCounter + ")");
            }
        } else {
            sprintRetryCounter = 0;
        }
    }

    private void simpleSprint() {
        if (mc().player.hurtTime > 0) {
            mc().player.setSprinting(false);
            return;
        }

        if (mc().player.isSprinting()) return;
        if (requireForward.getValue() && mc().player.moveForward <= 0) return;
        if (!allowSneak.getValue() && mc().player.isSneaking()) return;

        mc().player.setSprinting(true);
        KeyBinding.setKeyBindState(mc().gameSettings.keyBindSprint.getKeyCode(), true);
    }

    @Override
    public String getHudText() {
        if (sprintBlockedTicks > 0) {
            return "Sprint §7blocked §c" + sprintBlockedTicks;
        }
        return "AutoSprint Intave";
    }

    @Override
    public void onDisable() {
        KeyBinding.setKeyBindState(mc().gameSettings.keyBindSprint.getKeyCode(), false);
        sprintBlockedTicks = 0;
        sprintRetryCounter = 0;
    }
}
