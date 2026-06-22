package com.yourname.backtrack

import com.yourname.backtrack.client.ClientSimulator
import com.yourname.backtrack.client.ClientTickHandler
import com.yourname.backtrack.client.SimDebug
import com.yourname.backtrack.client.SimLifecycle
import com.yourname.backtrack.config.ConfigManager
import com.yourname.backtrack.gui.GuiOpener
import com.yourname.backtrack.gui.GuiTheme
import com.yourname.backtrack.hud.HudControlHandler
import com.yourname.backtrack.hud.HudRenderer
import com.yourname.backtrack.hud.HudSettings
import com.yourname.backtrack.input.KeybindHandler
import com.yourname.backtrack.module.ModuleManager
import com.yourname.backtrack.util.IntaveChatLogger
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent

@Mod(modid = "solobacktrack", name = "Solo Backtrack", version = "1.0")
object SoloBacktrack {

    lateinit var moduleManager: ModuleManager
        private set
    lateinit var hudSettings: HudSettings
        private set
    lateinit var configManager: ConfigManager
        private set
    lateinit var guiTheme: GuiTheme
        private set

    @Mod.EventHandler
    fun init(event: FMLInitializationEvent) {
        // Instance is now 'this' (Kotlin object)
    }

    @Mod.EventHandler
    fun postInit(event: FMLPostInitializationEvent) {
        moduleManager = ModuleManager()
        hudSettings = HudSettings()
        configManager = ConfigManager()
        guiTheme = GuiTheme()

        with(configManager) {
            loadHudSettings(hudSettings)
            loadModuleKeybinds(moduleManager)
            loadModuleSettings(moduleManager)
            loadModuleHudSettings(moduleManager)
            loadModuleStates(moduleManager)
        }

        val keybindHandler = KeybindHandler(moduleManager, configManager)
        moduleManager.keybindHandler = keybindHandler
        MinecraftForge.EVENT_BUS.register(keybindHandler)

        MinecraftForge.EVENT_BUS.register(HudRenderer(moduleManager, hudSettings))
        MinecraftForge.EVENT_BUS.register(HudControlHandler(hudSettings, configManager))
        MinecraftForge.EVENT_BUS.register(GuiOpener(moduleManager, configManager, hudSettings, guiTheme))
        MinecraftForge.EVENT_BUS.register(IntaveChatLogger())
        MinecraftForge.EVENT_BUS.register(ClientTickHandler())
        MinecraftForge.EVENT_BUS.register(SimLifecycle)

        // Load simulator settings
        ClientSimulator.shadowMode = configManager.loadSimulatorShadow()
        SimDebug.enabled = configManager.loadSimulatorDebug()
        SimDebug.logToChat = configManager.loadSimulatorDebugChat()
    }
}