package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class JumpResetModule extends Module {

    private int jumpCooldown  = 0;
    // >0: still waiting for delay to expire
    // -1: delay expired, waiting for onGround (max 8 ticks)
    private int jumpDelay     = 0;
    private int groundWait    = 0;

    public JumpResetModule() {
        super("JumpReset", Category.COMBAT, Keyboard.KEY_NONE);
        addHudSettings();
    }

    @Override
    public void onDisable() {
        jumpCooldown = 0;
        jumpDelay    = 0;
        groundWait   = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc.player == null) return;

        if (jumpCooldown > 0) jumpCooldown--;

        // Schedule jump when hit lands
        if (mc.player.hurtTime == 10 && jumpCooldown == 0) {
            WTapModule wtap = getWTap();
            int delay = (wtap != null && wtap.isEnabled()) ? wtap.getConfiguredTicks() + 1 : 0;
            if (delay == 0) {
                // No wtap — try immediately
                jumpDelay  = 0;
                groundWait = 8;
            } else {
                jumpDelay  = delay;
                groundWait = 0;
            }
        }

        // Count down the wtap delay
        if (jumpDelay > 0) {
            jumpDelay--;
            if (jumpDelay == 0) {
                // Delay done — now wait up to 8 ticks for ground
                groundWait = 8;
            }
        }

        // Once delay is done, fire the jump on the first tick we're on the ground
        if (groundWait > 0) {
            if (mc.player.onGround) {
                mc.player.motionY = 0.42;
                mc.player.isAirBorne = true;
                jumpCooldown = 12;
                groundWait   = 0;
            } else {
                groundWait--;
            }
        }
    }

    private WTapModule getWTap() {
        for (Module m : SoloBacktrack.getInstance().getModuleManager().getModules()) {
            if (m instanceof WTapModule) return (WTapModule) m;
        }
        return null;
    }
}
