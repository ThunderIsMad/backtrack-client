package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraftforge.client.event.InputUpdateEvent;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

public class JumpReset extends Module {

    // Smart: only reset within resetTicks of being hit — avoids velocityVL2 air accumulation.
    // Vanilla: reset on any landing while hurtTime > 0.
    private final ModeSetting    mode       = new ModeSetting("Mode", Arrays.asList("Smart", "Vanilla"), "Smart");
    private final NumberSetting  minFall    = new NumberSetting("MinFall",    0.0,  0.0,  0.6,  0.01);
    private final NumberSetting  resetTicks = new NumberSetting("ResetTicks", 3.0,  1.0,  8.0,  1.0);
    private final NumberSetting  cooldown   = new NumberSetting("Cooldown",   14.0, 8.0,  25.0, 1.0);
    private final BooleanSetting debug      = new BooleanSetting("Debug", false);

    private double  prevMotionY   = 0.0;
    private int     cooldownTimer = 0;
    private int     ticksSinceHit = 0;
    private boolean wasHurt       = false;

    public JumpReset() {
        super("JumpReset", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSettings(mode, minFall, resetTicks, cooldown, debug);
        addHudSettings();
    }

    @Override
    public void onEnable()  { resetState(); super.onEnable(); }

    @Override
    public void onDisable() { resetState(); super.onDisable(); }

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isEnabled() || mc.player == null) return;

        boolean isHurt     = mc.player.hurtTime > 0;
        double  curMotionY = mc.player.motionY;

        if (cooldownTimer > 0) cooldownTimer--;

        if (isHurt && !wasHurt) ticksSinceHit = 0;
        if (isHurt) ticksSinceHit++; else ticksSinceHit = 0;
        wasHurt = isHurt;

        // Landing frame: was falling last tick, ground stopped us this tick.
        boolean justLanded = prevMotionY < -0.01
                && curMotionY > -0.01
                && (mc.player.onGround || mc.player.collidedVertically);

        boolean inWindow = mode.getValue().equals("Smart")
                ? (ticksSinceHit >= 1 && ticksSinceHit <= (int) resetTicks.getValue())
                : isHurt;

        if (isHurt && justLanded
                && mc.player.fallDistance >= minFall.getValue()
                && inWindow
                && cooldownTimer == 0) {

            // 0.42 = standard jump force. Satisfies Intave's hadYVelocityStartMotion
            // check (requires velocitySqY >= 0.09; 0.42^2 = 0.176 >> threshold).
            // Ground friction handles XZ naturally — avoids bad5 flag.
            mc.player.motionY    = 0.42;
            mc.player.isAirBorne = true;
            cooldownTimer        = (int) cooldown.getValue();

            if (debug.getValue()) {
                sendClientMessage("\u00a7a[JumpReset] \u00a77tick=" + ticksSinceHit
                        + " fall=" + String.format("%.2f", mc.player.fallDistance));
            }
        }

        prevMotionY = curMotionY;
    }

    private void resetState() {
        prevMotionY   = 0.0;
        cooldownTimer = 0;
        ticksSinceHit = 0;
        wasHurt       = false;
    }
}
