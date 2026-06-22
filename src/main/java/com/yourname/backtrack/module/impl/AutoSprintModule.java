package com.yourname.backtrack.module.impl

import com.yourname.backtrack.client.ClientSimulator
import com.yourname.backtrack.module.Category
import com.yourname.backtrack.module.Module
import com.yourname.backtrack.setting.BooleanSetting
import com.yourname.backtrack.setting.ModeSetting
import com.yourname.backtrack.setting.NumberSetting
import net.minecraft.client.settings.KeyBinding
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import kotlin.math.max

class AutoSprintModule : Module("AutoSprint", Category.MOVEMENT, Keyboard.KEY_NONE) {

    private val mode          = ModeSetting("Mode", listOf("Simple", "Intave"), "Intave")
    private val requireForward = BooleanSetting("Require Forward", true)
    private val allowSneak    = BooleanSetting("Allow Sneak", false)
    private val postHurtTicks = NumberSetting("PostHurt Ticks", 10, 0, 20, 1)
    private val hungerCheck   = BooleanSetting("Hunger Check", true)
    private val debug         = BooleanSetting("Debug", false)

    // Intave: randomise the delay so it never repeats exactly
    private val randomiseDelay = BooleanSetting("RandomiseDelay", true)

    private var sprintBlockedTicks = 0
    private var sprintRetryCounter = 0
    private var actualBlockTicks   = 10  // the current (possibly randomised) block duration

    init {
        addSettings(mode, requireForward, allowSneak, postHurtTicks, hungerCheck, debug, randomiseDelay)
        addHudSettings()
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        if (!isEnabled) return
        val player = mc.player ?: return
        if (mc.world == null) return

        // ── Hunger check ──────────────────────────────────────────
        if (hungerCheck.value && player.foodStats.foodLevel <= 6) {
            player.isSprinting = false
            return
        }

        // ── Water / lava — sprinting is impossible, forcing it flags physics ──
        if (player.isInWater || player.isInLava) {
            player.isSprinting = false
            return
        }

        if (mode.value != "Intave") {
            simpleSprint()
            return
        }

        // ── Intave mode ───────────────────────────────────────────

        // Detect fresh damage — block sprint for the configured window
        if (player.hurtTime > 0 && player.isSprinting) {
            val base = postHurtTicks.value.toInt()
            actualBlockTicks = if (randomiseDelay.value) {
                max(1, base + (Math.random() * 3 - 1).toInt()) // ±1 tick jitter
            } else {
                base
            }
            sprintBlockedTicks = actualBlockTicks
            sprintRetryCounter = 0

            if (debug.value) {
                sendClientMessage("§cSprint §7blocked for $sprintBlockedTicks ticks (pev=${ClientSimulator.pastExternalVelocity})")
            }
        }

        // Hold sprint off during the block window
        if (sprintBlockedTicks > 0) {
            player.isSprinting = false
            sprintBlockedTicks--
            sprintRetryCounter = 0
            return
        }

        // Already sprinting — nothing to do
        if (player.isSprinting) return

        // Check conditions for enabling sprint
        if (requireForward.value && player.moveForward <= 0) return
        if (!allowSneak.value && player.isSneaking) return

        // Try to enable sprint
        player.isSprinting = true
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.keyCode, true)

        if (!player.isSprinting) {
            // setSprinting failed (vanilla toggle timer) — retry next tick
            sprintRetryCounter++
            if (debug.value && sprintRetryCounter <= 1) {
                sendClientMessage("§eSprint §7toggle block, retrying (count=$sprintRetryCounter)")
            }
        } else {
            sprintRetryCounter = 0
        }
    }

    private fun simpleSprint() {
        val player = mc.player ?: return

        if (player.hurtTime > 0) {
            player.isSprinting = false
            return
        }

        if (player.isSprinting) return
        if (requireForward.value && player.moveForward <= 0) return
        if (!allowSneak.value && player.isSneaking) return

        player.isSprinting = true
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.keyCode, true)
    }

    override fun getHudText(): String {
        if (sprintBlockedTicks > 0) return "Sprint §7blocked §c$sprintBlockedTicks"
        return "AutoSprint Intave"
    }

    override fun onDisable() {
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindSprint.keyCode, false)
        sprintBlockedTicks = 0
        sprintRetryCounter = 0
    }
}