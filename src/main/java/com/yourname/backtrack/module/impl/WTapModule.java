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

    private int lastHurtTime = 0;
    private int cancelTicks  = 0;

    public WTapModule() {
        super("WTap", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(ticks, onlyOnGround);
        addHudSettings();
    }

    /** How many sprint-cancel ticks are still pending (0 = wtap finished). */
    public int getCancelTicks() {
        return cancelTicks;
    }

    /** How many ticks WTap is configured to cancel sprint for. */
    public int getConfiguredTicks() {
        return (int) ticks.getValue();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc.player == null) return;

        int hurtTime = mc.player.hurtTime;

        if (hurtTime > lastHurtTime) {
            if (!onlyOnGround.getValue() || mc.player.onGround) {
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
