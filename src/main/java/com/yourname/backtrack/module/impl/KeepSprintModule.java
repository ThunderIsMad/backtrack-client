package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class KeepSprintModule extends Module {

    private int hurtCooldown = 0;
    private float lastHealth = 20f;

    public KeepSprintModule() {
        super("KeepSprint", Category.COMBAT, Keyboard.KEY_NONE);
        addHudSettings();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc.player == null) return;

        float health = mc.player.getHealth();
        if (health < lastHealth) {
            hurtCooldown = 4;
        }
        lastHealth = health;

        if (hurtCooldown > 0) hurtCooldown--;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.world == null) return;
        if (hurtCooldown > 0) return;

        if (!mc.player.isSprinting()
                && mc.player.moveForward > 0
                && !mc.player.isSneaking()
                && mc.player.getFoodStats().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }
    }
}
