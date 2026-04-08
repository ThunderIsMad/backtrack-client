package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class JumpResetModule extends Module {

    private int lastHurtTime = 0;
    private int jumpCooldown = 0;

    public JumpResetModule() {
        super("JumpReset", Category.COMBAT, Keyboard.KEY_NONE);
        addHudSettings();
    }

    @Override
    public void onDisable() {
        lastHurtTime = 0;
        jumpCooldown = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc.player == null) return;

        if (jumpCooldown > 0) jumpCooldown--;

        int hurtTime = mc.player.hurtTime;

        // Detect incoming hit: hurtTime jumps back up to max on damage
        if (hurtTime > lastHurtTime && jumpCooldown == 0) {
            if (mc.player.onGround) {
                // Vanilla jump impulse — server simulation expects this as legal
                mc.player.motionY = 0.42;
                mc.player.isAirBorne = true;
                // Full jump arc is ~12 ticks; cooldown prevents re-trigger during rapid hits
                jumpCooldown = 12;
            }
        }

        lastHurtTime = hurtTime;
    }
}
