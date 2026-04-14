package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

public class AutoRespawnModule extends Module {

    public AutoRespawnModule() {
        super("AutoRespawn", Category.MISC, Keyboard.KEY_NONE);
        addHudSettings();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc().player == null) {
            return;
        }

        if (mc().currentScreen instanceof GuiGameOver) {
            mc().player.respawnPlayer();
            mc().displayGuiScreen(null);
        }
    }
}
