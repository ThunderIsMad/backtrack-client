package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraftforge.client.event.InputUpdateEvent;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

/**
 * JumpReset — satisfies Intave hadYVelocityStartMotion (needs velocitySqY >= 0.09).
 * motionY = 0.42 gives 0.176 >> threshold. XZ untouched so xzIncreasement stays valid.
 * Smart mode enforces the tick window to prevent velocityVL2 accumulation.
 */
public class JumpResetModule extends Module {

    private final ModeSetting    mode       = new ModeSetting("Mode", Arrays.asList("Smart", "Vanilla"), "Smart");
    private final NumberSetting  resetTicks = new NumberSetting("ResetTicks", 3, 1, 8, 1);
    private final NumberSetting  cooldown   = new NumberSetting("Cooldown", 14, 8, 25, 1);
    private final BooleanSetting debug      = new BooleanSetting("Debug", false);

    private double prevMotionY   = 0.0;
    private int    ticksSinceHit = 0;
    private boolean wasHurt      = false;
    private int    cooldownTimer = 0;

    public JumpResetModule() {
        super("JumpReset", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSettings(mode, resetTicks, cooldown, debug);
        addHudSettings();
    }

    @Override
    public void onEnable()  { resetState(); }

    @Override
    public void onDisable() { resetState(); }

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isEnabled() || mc().player == null || mc().world == null) return;

        if (cooldownTimer > 0) {
            cooldownTimer--;
            prevMotionY = mc().player.motionY;
            return;
        }

        boolean isHurt = mc().player.hurtTime > 0;

        if (isHurt && !wasHurt) ticksSinceHit = 0;
        if (isHurt) ticksSinceHit++; else ticksSinceHit = 0;
        wasHurt = isHurt;

        // Landing: was falling last tick, now on ground
        boolean justLanded = prevMotionY < -0.01 && mc().player.onGround;
        prevMotionY = mc().player.motionY;

        if (!isHurt || !justLanded) return;

        boolean inWindow;
        if ("Smart".equals(mode.getValue())) {
            inWindow = ticksSinceHit >= 1 && ticksSinceHit <= (int) resetTicks.getValue();
        } else {
            inWindow = true;
        }

        if (!inWindow) return;

        mc().player.motionY    = 0.42;
        mc().player.isAirBorne = true;
        cooldownTimer = (int) cooldown.getValue();

        if (debug.getValue()) {
            sendClientMessage("\u00a7aJumpReset \u00a77tick=" + ticksSinceHit
                    + " mode=" + mode.getValue()
                    + " hurt=" + mc().player.hurtTime);
        }
    }

    private void resetState() {
        prevMotionY   = 0.0;
        ticksSinceHit = 0;
        wasHurt       = false;
        cooldownTimer = 0;
    }
}
