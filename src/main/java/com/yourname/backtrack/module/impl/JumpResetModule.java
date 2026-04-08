package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class JumpResetModule extends Module {

    private int jumpCooldown = 0;
    private int jumpDelay    = 0;

    public JumpResetModule() {
        super("JumpReset", Category.COMBAT, Keyboard.KEY_NONE);
        addHudSettings();
    }

    @Override
    public void onDisable() {
        jumpCooldown = 0;
        jumpDelay    = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc.player == null) return;

        if (jumpCooldown > 0) jumpCooldown--;

        if (mc.player.hurtTime == 10 && jumpCooldown == 0) {
            WTapModule wtap = getWTap();
            int delay = (wtap != null && wtap.isEnabled()) ? wtap.getConfiguredTicks() + 1 : 0;
            jumpDelay = delay;
        }

        if (jumpDelay > 0) {
            jumpDelay--;
            if (jumpDelay == 0 && mc.player.onGround) {
                mc.player.motionY = 0.42;
                mc.player.isAirBorne = true;
                jumpCooldown = 12;
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
