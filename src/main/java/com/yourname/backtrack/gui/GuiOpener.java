package com.yourname.backtrack.gui

import com.yourname.backtrack.config.ConfigManager
import com.yourname.backtrack.hud.HudSettings
import com.yourname.backtrack.module.ModuleManager
import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.lwjgl.input.Keyboard

class GuiOpener(
    private val moduleManager: ModuleManager,
    private val configManager: ConfigManager,
    private val hudSettings: HudSettings,
    private val guiTheme: GuiTheme
) {
    private val openGuiKey = KeyBinding("Open ClickGUI", Keyboard.KEY_RSHIFT, "Solo Backtrack")

    init {
        ClientRegistry.registerKeyBinding(openGuiKey)
    }

    @SubscribeEvent
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        if (openGuiKey.isPressed) {
            Minecraft.getMinecraft().displayGuiScreen(
                ClickGuiScreen(moduleManager, configManager, hudSettings, guiTheme)
            )
        }
    }
}