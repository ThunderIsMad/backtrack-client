package com.yourname.backtrack.module.impl

import com.yourname.backtrack.client.ClientSimulator
import com.yourname.backtrack.module.Category
import com.yourname.backtrack.module.Module
import com.yourname.backtrack.setting.BooleanSetting
import com.yourname.backtrack.setting.NumberSetting
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import kotlin.math.max

class KeepSprintModule : Module("KeepSprint", Category.MOVEMENT, Keyboard.KEY_NONE) {

    private val onlyOnHit    = BooleanSetting("Only On Hit", true)
    private val healthMin    = NumberSetting("Min Health", 4.0, 0.0, 20.0, 0.5)
    private val postHurtDelay = NumberSetting("PostHurtDelay", 4, 0, 10, 1)

    // Intave bypass: randomise the delay slightly so the pattern doesn't repeat exactly
    private val randomiseDelay = BooleanSetting("RandomiseDelay", true)

    private var ticksSinceHurt = 999
    private var lastHealth     = 20.0f
    private var wasAttacking   = false
    private var actualDelay    = 4

    init {
        addSettings(onlyOnHit, healthMin, postHurtDelay, randomiseDelay)
        addHudSettings()
    }

    override fun onEnable() {
        ticksSinceHurt = 999
        actualDelay = postHurtDelay.value.toInt()
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        if (!isEnabled) return
        val player = mc.player ?: return

        // ── Track hurt ──────────────────────────────────────────
        val currentHealth = player.health
        if (currentHealth < lastHealth) {
            ticksSinceHurt = 0
            // Re-roll the delay each time we take damage
            if (randomiseDelay.value) {
                val base = postHurtDelay.value.toInt()
                actualDelay = max(0, base + (Math.random() * 3 - 1).toInt()) // ±1 tick
            }
        }
        lastHealth = currentHealth

        if (ticksSinceHurt < 999) ticksSinceHurt++

        // ── Attack detection ────────────────────────────────────
        val attacking = mc.gameSettings.keyBindAttack.isKeyDown
        val justAttacked = attacking && !wasAttacking
        wasAttacking = attacking

        // ── Sprint logic ────────────────────────────────────────

        // Health check — don't sprint when low (Intave: low-HP sprint is suspicious)
        if (currentHealth < healthMin.value) {
            player.isSprinting = false
            return
        }

        // Post-hurt delay — Intave checks sprint-prediction mismatch after knockback
        if (ticksSinceHurt < actualDelay) {
            player.isSprinting = false
            return
        }

        // Only-on-hit mode: only keep sprint right after attacking
        if (onlyOnHit.value && !justAttacked && !player.isSprinting) {
            return
        }

        // Standard vanilla sprint eligibility
        if (!player.isSprinting
            && player.moveForward > 0
            && !player.isSneaking
            && player.foodStats.foodLevel > 6
            && !player.isInWater) {

            player.isSprinting = true
        }

        // If player stopped moving forward — let vanilla handle the stop
    }
}