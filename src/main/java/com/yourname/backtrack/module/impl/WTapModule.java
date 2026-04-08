package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class WTapModule extends Module {

    private final NumberSetting ticks = new NumberSetting("Ticks", 1, 1, 3, 1);
    private final BooleanSetting onlyOnGround = new BooleanSetting("OnlyOnGround", false);

    // last observed hurtTime — when it resets to max we know a hit just landed
    private int lastHurtTime = 0;
    private int cancelTicks  = 0;

    public WTapModule() {
        super("WTap", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(ticks, onlyOnGround);
        addHudSettings();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc.player == null) return;

        int hurtTime = mc.player.hurtTime;

        // hurtTime jumps to its max (hurtResistantTime, usually 10) when a hit lands
        if (hurtTime > lastHurtTime) {
            if (!onlyOnGround.getValue() || mc.player.onGround) {
                // cancel sprint for the configured number of ticks
                cancelTicks = (int) ticks.getValue();
            }
        }
        lastHurtTime = hurtTime;

        if (cancelTicks > 0) {
            mc.player.setSprinting(false);
            cancelTicks--;
        }
    }

    @Override
    public void onDisable() {
        cancelTicks  = 0;
        lastHurtTime = 0;
    }
}
