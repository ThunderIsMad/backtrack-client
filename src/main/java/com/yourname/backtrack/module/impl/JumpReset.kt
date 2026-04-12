package com.yourname.backtrack.module.impl

import com.yourname.backtrack.module.Category
import com.yourname.backtrack.module.Module
import com.yourname.backtrack.setting.BooleanSetting
import com.yourname.backtrack.setting.ModeSetting
import com.yourname.backtrack.setting.NumberSetting
import net.minecraftforge.client.event.InputUpdateEvent
import org.lwjgl.input.Keyboard
import java.util.Arrays

class JumpReset : Module("JumpReset", Category.MOVEMENT, Keyboard.KEY_NONE) {

    private val mode       = ModeSetting("Mode", Arrays.asList("Smart", "Vanilla"), "Smart")
    private val minFall    = NumberSetting("MinFall", 0.0, 0.6, 0.05, 0.01)
    private val resetTicks = NumberSetting("ResetTicks", 1.0, 8.0, 3.0, 1.0)
    private val cooldown   = NumberSetting("Cooldown", 8.0, 25.0, 14.0, 1.0)
    private val debug      = BooleanSetting("Debug", false)

    private var prevMotionY   = 0.0
    private var cooldownTimer = 0
    private var ticksSinceHit = 0
    private var wasHurt       = false

    init {
        addSettings(mode, minFall, resetTicks, cooldown, debug)
        addHudSettings()
    }

    override fun onEnable()  { resetState(); super.onEnable() }
    override fun onDisable() { resetState(); super.onDisable() }

    override fun onInputUpdate(event: InputUpdateEvent) {
        if (!isEnabled() || mc.player == null) return

        val player = mc.player
        val isHurt = player.hurtTime > 0
        val curMotionY = player.motionY

        if (cooldownTimer > 0) cooldownTimer--

        if (isHurt && !wasHurt) ticksSinceHit = 0
        if (isHurt) ticksSinceHit++ else ticksSinceHit = 0
        wasHurt = isHurt

        val justLanded = prevMotionY < -0.01
                && curMotionY > -0.01
                && (player.onGround || player.collidedVertically)

        val inWindow = when (mode.getValue()) {
            "Smart" -> ticksSinceHit in 1..resetTicks.getValue().toInt()
            else    -> isHurt
        }

        if (isHurt && justLanded && player.fallDistance >= minFall.getValue() && inWindow && cooldownTimer == 0) {
            player.motionY = 0.42
            player.isAirBorne = true
            cooldownTimer = cooldown.getValue().toInt()

            if (debug.getValue()) {
                sendClientMessage("§a[JumpReset] §7tick=$ticksSinceHit fall=%.2f".format(player.fallDistance))
            }
        }

        prevMotionY = curMotionY
    }

    private fun resetState() {
        prevMotionY = 0.0
        cooldownTimer = 0
        ticksSinceHit = 0
        wasHurt = false
    }
}
