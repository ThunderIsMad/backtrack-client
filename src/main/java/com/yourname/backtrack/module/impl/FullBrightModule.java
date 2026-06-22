package com.yourname.backtrack.module.impl

import com.yourname.backtrack.module.Category
import com.yourname.backtrack.module.Module
import com.yourname.backtrack.setting.NumberSetting
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard

class FullBrightModule : Module("FullBright", Category.RENDER, Keyboard.KEY_NONE) {

    private val gammaValue = NumberSetting("Gamma", 10.0, 1.0, 20.0, 0.5)

    private var previousGamma = 1.0f
    private var saved = false

    init {
        addSettings(gammaValue)
        addHudSettings()
    }

    override fun onEnable() {
        mc.gameSettings?.let {
            previousGamma = it.gammaSetting
            saved = true
        }
    }

    override fun onDisable() {
        if (saved) mc.gameSettings?.gammaSetting = previousGamma
        saved = false
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        if (isEnabled) {
            val target = gammaValue.value.toFloat()
            mc.gameSettings?.let {
                if (it.gammaSetting != target) it.gammaSetting = target
            }
        }
    }
}