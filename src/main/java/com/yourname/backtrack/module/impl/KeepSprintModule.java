package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.client.event.InputUpdateEvent;
import org.lwjgl.input.Keyboard;

public class KeepSprintModule extends Module {

    public KeepSprintModule() {
        super("KeepSprint", Category.COMBAT, Keyboard.KEY_NONE);
        addHudSettings();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.isSprinting()
                && mc.player.moveForward > 0
                && !mc.player.isSneaking()
                && mc.player.getFoodStats().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }
    }
}
