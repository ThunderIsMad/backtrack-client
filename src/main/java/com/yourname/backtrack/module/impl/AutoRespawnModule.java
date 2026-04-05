package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class AutoRespawnModule extends Module {

    private final NumberSetting delay = new NumberSetting("Delay", 0.0, 0.0, 3000.0, 100.0);
    private long deathTime = -1L;

    public AutoRespawnModule() {
        super("AutoRespawn", Category.MISC, Keyboard.KEY_J);

        addSettings(
                delay
        );

        addHudSettings();
    }

    @Override
    public String getHudText() {
        return "AutoRespawn [" + (int) delay.getValue() + "ms]";
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (!isEnabled() || mc.player == null) {
            deathTime = -1L;
            return;
        }

        if (mc.currentScreen instanceof GuiGameOver) {
            if (deathTime == -1L) {
                deathTime = System.currentTimeMillis();
            }

            if (System.currentTimeMillis() - deathTime >= (long) delay.getValue()) {
                mc.player.respawnPlayer();
                mc.displayGuiScreen(null);
                deathTime = -1L;
            }
        } else {
            deathTime = -1L;
        }
    }
}

