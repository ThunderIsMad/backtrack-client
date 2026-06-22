package com.yourname.backtrack.module.impl

import com.yourname.backtrack.module.Category
import com.yourname.backtrack.module.Module
import net.minecraft.client.gui.GuiGameOver
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard

class AutoRespawnModule : Module("AutoRespawn", Category.MISC, Keyboard.KEY_NONE) {

    init { addHudSettings() }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        if (!isEnabled) return

        if (mc.currentScreen is GuiGameOver) {
            mc.player?.respawnPlayer()
            mc.displayGuiScreen(null)
        }
    }
}