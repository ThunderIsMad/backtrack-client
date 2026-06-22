package com.yourname.backtrack.input

import com.yourname.backtrack.config.ConfigManager
import com.yourname.backtrack.module.ModuleManager
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard

class KeybindHandler(
    private val moduleManager: ModuleManager,
    private val configManager: ConfigManager
) {
    @SubscribeEvent(priority = EventPriority.NORMAL)
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START) {
            moduleManager.onTick()
        }
    }

    @SubscribeEvent
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        if (!Keyboard.getEventKeyState) return

        val pressedKey = Keyboard.run {
            val key = getEventKey()
            if (key == 0) getEventCharacter() + 256 else key
        }

        for (module in moduleManager.modules) {
            if (module.keyCode == pressedKey) {
                module.toggle()
                configManager.saveModuleStates(moduleManager)
                break
            }
        }
    }

    /** Called from ModuleManager.onTick() for polling-based keybinds */
    fun onTick() {
        for (module in moduleManager.modules) {
            if (module.keyBinding.isPressed) {
                module.toggle()
                configManager.saveModuleStates(moduleManager)
                break
            }
        }
    }
}