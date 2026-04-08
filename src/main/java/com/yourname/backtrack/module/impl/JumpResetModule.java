package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class JumpResetModule extends Module {

    private int jumpCooldown = 0;

    public JumpResetModule() {
        super("JumpReset", Category.COMBAT, Keyboard.KEY_NONE);
        addHudSettings();
    }

    @Override
    public void onDisable() {
        jumpCooldown = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc.player == null) return;

        if (jumpCooldown > 0) jumpCooldown--;

        // Fire exactly when hurtTime is at its peak (fresh hit this tick)
        // This matches the same tick the server begins simulating the jump arc
        if (mc.player.hurtTime == 10 && jumpCooldown == 0) {
            if (mc.player.onGround) {
                mc.player.motionY = 0.42;
                mc.player.isAirBorne = true;
                jumpCooldown = 12;
            }
        }
    }
}
