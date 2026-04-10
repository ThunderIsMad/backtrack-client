package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import net.minecraftforge.client.event.InputUpdateEvent;
import org.lwjgl.input.Keyboard;

public class AutoSprintModule extends Module {

    private final BooleanSetting requireForward = new BooleanSetting("RequireForward", true);
    private final BooleanSetting allowSneak = new BooleanSetting("AllowSneak", false);

    public AutoSprintModule() {
        super("AutoSprint", Category.MOVEMENT, Keyboard.KEY_G);
        addSettings(requireForward, allowSneak);
        addHudSettings();
    }

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        // Suppress sprint during knockback arc so Intave's predicted XZ trajectory
        // is not disrupted by early re-sprint. hurtTime > 0 means arc is active.
        if (mc.player.hurtTime > 0) return;

        if (requireForward.getValue() && mc.player.moveForward <= 0) return;
        if (!allowSneak.getValue() && mc.player.isSneaking()) return;

        if (!mc.player.isSprinting()) {
            mc.player.setSprinting(true);
        }
    }
}
