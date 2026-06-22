package com.yourname.backtrack.module.impl

import com.yourname.backtrack.client.ClientSimulator
import com.yourname.backtrack.module.Category
import com.yourname.backtrack.module.Module
import com.yourname.backtrack.setting.*
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.client.CPacketEntityAction
import org.lwjgl.input.Keyboard
import java.util.*
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.toDegrees

/**
 * WTap — briefly interrupt sprint on hit to increase knockback.
 * Intave-aware: randomises timing, respects velocity windows,
 * and mirrors vanilla sprint-drop behaviour.
 */
class WTapModule : Module("WTap", Category.COMBAT, Keyboard.KEY_NONE) {

    private val mode = ModeSetting("Mode", listOf("Packet", "SprintTap", "MoveBlock"), "Packet")

    private val chance           = NumberSetting("Chance", 80, 0, 100, 1)
    private val hurtTimeThreshold = NumberSetting("HurtTimeThreshold", 10, 0, 10, 1)
    private val onlyOnGround     = BooleanSetting("OnlyOnGround", true)
    private val onlyFacing       = BooleanSetting("OnlyFacing", true)
    private val notInWater       = BooleanSetting("NotInWater", true)

    // Packet mode
    private val packetDelayMin   = NumberSetting("PacketDelayMin", 1, 0, 4, 1)
    private val packetDelayMax   = NumberSetting("PacketDelayMax", 2, 0, 4, 1)

    // SprintTap mode
    private val stopTicksMin     = NumberSetting("StopTicksMin", 0, 0, 10, 1)
    private val stopTicksMax     = NumberSetting("StopTicksMax", 1, 0, 10, 1)
    private val reSprintDelayMin = NumberSetting("ReSprintDelayMin", 1, 0, 10, 1)
    private val reSprintDelayMax = NumberSetting("ReSprintDelayMax", 2, 0, 10, 1)

    // MoveBlock mode
    private val moveBlockStartMin    = NumberSetting("MoveBlockStartMin", 0, 0, 3, 1)
    private val moveBlockStartMax    = NumberSetting("MoveBlockStartMax", 1, 0, 3, 1)
    private val moveBlockDurationMin = NumberSetting("MoveBlockDurationMin", 0, 0, 3, 1)
    private val moveBlockDurationMax = NumberSetting("MoveBlockDurationMax", 2, 0, 3, 1)

    // ── Intave-aware: randomised jitter ──────────────────────────
    private val randomiseTimings = BooleanSetting("RandomiseTimings", true)

    private val random = Random()
    private var phase = 0
    private var subTimer = 0
    private var cancelSprint = false
    private var blockMovement = false
    private var lastHurtTime = 0

    // Drifting delay offsets (re-rolled periodically)
    private var packetDrift = 0
    private var sprintDrift = 0

    init {
        addSettings(mode, chance, hurtTimeThreshold,
            onlyOnGround, onlyFacing, notInWater,
            packetDelayMin, packetDelayMax,
            stopTicksMin, stopTicksMax, reSprintDelayMin, reSprintDelayMax,
            moveBlockStartMin, moveBlockStartMax, moveBlockDurationMin, moveBlockDurationMax,
            randomiseTimings)
        addHudSettings()
    }

    override fun onEnable() { reset() }
    override fun onDisable() { reset() }

    private fun reset() {
        phase = 0; subTimer = 0
        cancelSprint = false; blockMovement = false
    }

    override fun onClientTick() {
        if (!isEnabled) return
        val player = mc.player ?: return
        if (mc.world == null) return

        val hurtTime = player.hurtTime
        if (hurtTime > 0 && lastHurtTime == 0) {
            onPlayerHit()
        }

        when (mode.value) {
            "Packet"    -> handlePacket()
            "SprintTap" -> handleSprintTap()
            "MoveBlock" -> handleMoveBlock()
        }
        lastHurtTime = hurtTime
    }

    // ── Trigger ──────────────────────────────────────────────────

    private fun onPlayerHit() {
        if (!meetsConditions()) return
        if (random.nextInt(100) >= chance.value.toInt()) return

        // Intave: don't WTap inside velocity window — sprint changes
        // look like velocity manipulation
        if (ClientSimulator.isInVelocityWindow()) return

        // Re-roll timing drift every activation
        if (randomiseTimings.value) {
            packetDrift = (random.nextDouble() * 2 - 1).toInt() // ±1
            sprintDrift = (random.nextDouble() * 2 - 1).toInt()
        }

        when (mode.value) {
            "Packet"    -> { phase = 1; subTimer = 0 }
            "SprintTap" -> { phase = 1; subTimer = 0; cancelSprint = true }
            "MoveBlock" -> { phase = 1; subTimer = 0 }
        }
    }

    // ── Conditions ───────────────────────────────────────────────

    private fun meetsConditions(): Boolean {
        val player = mc.player ?: return false
        if (onlyOnGround.value && !player.onGround) return false
        if (notInWater.value && (player.isInWater || player.isInLava)) return false

        val target = player.lastAttackedEntity as? EntityLivingBase ?: return false

        if (onlyFacing.value) {
            val dx = player.posX - target.posX
            val dz = player.posZ - target.posZ
            var angle = toDegrees(atan2(dz, dx)) - target.rotationYawHead
            angle = (angle % 360 + 540) % 360 - 180
            if (abs(angle) > 90) return false
        }

        if (target.hurtTime > hurtTimeThreshold.value.toInt()) return false
        return true
    }

    // ── Packet mode ──────────────────────────────────────────────

    private fun handlePacket() {
        if (phase != 1) return
        subTimer++
        val delay = randomBetween(packetDelayMin, packetDelayMax) + packetDrift
        if (subTimer >= delay.coerceAtLeast(0)) {
            val conn = mc.connection ?: return
            conn.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SPRINTING))
            conn.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SPRINTING))
            mc.player?.isSprinting = true
            phase = 0
        }
    }

    // ── SprintTap mode ───────────────────────────────────────────

    private fun handleSprintTap() {
        if (phase == 0) {
            cancelSprint = false
            return
        }
        when (phase) {
            1 -> {
                subTimer++
                if (subTimer >= randomBetween(stopTicksMin, stopTicksMax) + sprintDrift) {
                    phase = 2; subTimer = 0
                }
            }
            2 -> {
                mc.player?.isSprinting = false
                phase = 3; subTimer = 0
            }
            3 -> {
                subTimer++
                if (subTimer >= randomBetween(reSprintDelayMin, reSprintDelayMax) + sprintDrift) {
                    phase = 0; cancelSprint = false
                }
            }
        }
        if (cancelSprint) mc.player?.isSprinting = false
    }

    // ── MoveBlock mode ───────────────────────────────────────────

    private fun handleMoveBlock() {
        if (phase == 0) {
            blockMovement = false
            return
        }
        when (phase) {
            1 -> {
                subTimer++
                if (subTimer >= randomBetween(moveBlockStartMin, moveBlockStartMax)) {
                    phase = 2; subTimer = 0; blockMovement = true
                }
            }
            2 -> {
                subTimer++
                if (subTimer >= randomBetween(moveBlockDurationMin, moveBlockDurationMax)) {
                    phase = 0; blockMovement = false
                }
            }
        }
        if (blockMovement) {
            mc.player?.let {
                it.motionX = 0.0
                it.motionZ = 0.0
                it.isSprinting = false
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun randomBetween(min: NumberSetting, max: NumberSetting): Int {
        val lo = min.value.toInt()
        val hi = max.value.toInt()
        return if (hi <= lo) lo else lo + random.nextInt(hi - lo + 1)
    }

    override fun getVisibleSettings(): MutableList<Setting> {
        val f = mutableListOf<Setting>(mode, chance, hurtTimeThreshold,
            onlyOnGround, onlyFacing, notInWater)
        when (mode.value) {
            "Packet"    -> { f += packetDelayMin; f += packetDelayMax }
            "SprintTap" -> { f += stopTicksMin; f += stopTicksMax; f += reSprintDelayMin; f += reSprintDelayMax }
            "MoveBlock" -> { f += moveBlockStartMin; f += moveBlockStartMax; f += moveBlockDurationMin; f += moveBlockDurationMax }
        }
        f += randomiseTimings
        return f
    }
}