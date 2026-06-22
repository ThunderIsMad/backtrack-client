package com.yourname.backtrack.module

import com.yourname.backtrack.input.KeybindHandler
import com.yourname.backtrack.module.impl.*
import net.minecraft.client.Minecraft
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.client.registry.ClientRegistry

class ModuleManager {

    var keybindHandler: KeybindHandler? = null

    private val _modules = mutableListOf<Module>()
    val modules: List<Module> get() = _modules

    init {
        // Register order determines default HUD vertical positions
        listOf(
            ::AutoSprintModule,
            ::FullBrightModule,
            ::AutoRespawnModule,
            ::KeepSprintModule,
            ::WTapModule,
            ::AutoClickerModule,
            ::VelocityModule
        ).forEach { constructor -> registerModule(constructor()) }
    }

    private fun registerModule(module: Module) {
        module.hudSettings.setDefaultPosition(5, 5 + _modules.size * 14)
        _modules += module
        ClientRegistry.registerKeyBinding(module.keyBinding)
        MinecraftForge.EVENT_BUS.register(module)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Module> getModule(clazz: Class<T>): T? =
        _modules.firstOrNull { clazz.isInstance(it) } as? T

    fun onTick() {
        if (Minecraft.getMinecraft().player == null) return

        keybindHandler?.onTick()

        for (module in _modules) {
            if (module.isEnabled) {
                module.onClientTick()
            }
        }
    }
}