package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class KeepSprintModule extends Module {

    private final BooleanSetting onlyOnHit  = new BooleanSetting("Only On Hit", true);
    private final NumberSetting  healthMin  = new NumberSetting("Min Health", 0.0, 0.0, 20.0, 0.5);

    private float lastHealth = 20.0f;

    public KeepSprintModule() {
        super("KeepSprint", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSettings(onlyOnHit, healthMin);
        addHudSettings();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc().player == null) return;

        float health = mc().player.getHealth();
        lastHealth = health;
    }

    @Override
    public void onDisable() {
        if (mc().player == null) return;
        mc().player.setSprinting(false);
    }

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isEnabled() || mc().player == null || mc().world == null) return;

        if (mc().player.getHealth() < (float) healthMin.getValue()) return;

        if (!mc().player.isSprinting()
                && mc().player.moveForward > 0
                && !mc().player.isSneaking()
                && mc().player.getFoodStats().getFoodLevel() > 6) {
            mc().player.setSprinting(true);
        }
    }
}
