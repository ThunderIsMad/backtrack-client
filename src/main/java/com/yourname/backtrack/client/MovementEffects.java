package com.yourname.backtrack.client

import net.minecraft.client.Minecraft
import net.minecraft.init.MobEffects

/**
 * Potion-effect aware attribute modifiers.
 * Applies Speed, Slowness, and Jump Boost adjustments exactly as the
 * vanilla server would when computing movement attributes.
 */
object MovementEffects {

    /**
     * Applies Speed (× 1.0 + 0.4 × amplifier) and Slowness (× 1.0 − 0.15 × amplifier)
     * multipliers to the base [aiMoveSpeed].
     */
    fun applySpeedEffect(aiMoveSpeed: Float, mc: Minecraft): Float {
        var speed = aiMoveSpeed

        val speedEffect = mc.player?.getActivePotionEffect(MobEffects.SPEED)
        if (speedEffect != null) {
            speed *= 1.0f + 0.4f * (speedEffect.amplifier + 1)
        }

        val slownessEffect = mc.player?.getActivePotionEffect(MobEffects.SLOWNESS)
        if (slownessEffect != null) {
            speed *= 1.0f - 0.15f * (slownessEffect.amplifier + 1)
        }

        return speed
    }

    /**
     * Adds +0.1f per Jump Boost amplifier level to the base [jumpMotion].
     */
    fun applyJumpBoost(jumpMotion: Float, mc: Minecraft): Float {
        var jump = jumpMotion

        val jumpEffect = mc.player?.getActivePotionEffect(MobEffects.JUMP_BOOST)
        if (jumpEffect != null) {
            jump += (jumpEffect.amplifier + 1) * 0.1f
        }

        return jump
    }
}