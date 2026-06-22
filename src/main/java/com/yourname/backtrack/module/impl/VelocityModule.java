package com.yourname.backtrack.module.impl

import com.yourname.backtrack.client.ClientSimulator
import com.yourname.backtrack.client.SimDebug
import com.yourname.backtrack.module.Category
import com.yourname.backtrack.module.Module
import com.yourname.backtrack.setting.*
import net.minecraft.client.settings.KeyBinding
import net.minecraft.entity.EntityLivingBase
import net.minecraft.network.play.server.SPacketExplosion
import net.minecraft.util.math.RayTraceResult
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class VelocityModule : Module("Velocity", Category.COMBAT, Keyboard.KEY_NONE) {

    // ── Accessor interfaces (for Mixin binding) ────────────────────
    interface SPacketEntityVelocityAccessor {
        var motionX: Int
        var motionY: Int
        var motionZ: Int
    }

    interface SPacketExplosionAccessor {
        var motionX: Float
        var motionY: Float
        var motionZ: Float
    }

    // ── Mode ──────────────────────────────────────────────────────
    private val mode = ModeSetting("Mode",
        listOf("Modify", "JumpReset", "Reverse", "Push", "Legit", "TickZero", "Reduce"),
        "JumpReset")

    // Modify
    private val xzModify        = NumberSetting("XZ", 0, 0, 100, 1)
    private val yModify         = NumberSetting("Y",  0, 0, 100, 1)
    private val ignoreExplosion = BooleanSetting("IgnoreExplosion", true)

    // JumpReset
    private val fallDamageCheck = BooleanSetting("FallDamageCheck", true)
    private val debug           = BooleanSetting("Debug", false)
    private val simDebugLog     = BooleanSetting("SimDebug", false)
    private val chance          = NumberSetting("Chance",          80,  1,   100, 1)
    private val resetTicks      = NumberSetting("ResetTicks",      3,   1,   8,   1)
    private val cooldownTicks   = NumberSetting("Cooldown",        14,  8,   25,  1)
    private val randomize       = BooleanSetting("Randomize", false)
    private val delayMin        = NumberSetting("DelayMin",        0,   0,   10,  1)
    private val delayMax        = NumberSetting("DelayMax",        3,   0,   10,  1)
    private val maxDistance     = NumberSetting("MaxDistance",     2.5, 1.0, 6.0, 0.1)
    private val jumpMotionVar   = NumberSetting("JumpMotionVar",   0.04, 0.00, 0.10, 0.01)
    private val jumpVelWindow   = BooleanSetting("JumpVelWindow",  true)

    // Reverse
    private val reverseChance       = NumberSetting("ReverseChance",      50,  0, 100, 1)
    private val reverseWaitTicks    = NumberSetting("CorrectionTicks",    10,  4,  18, 1)
    private val reverseMoveTicks    = NumberSetting("MoveTicks",          8,   3,  20, 1)
    private val reverseStrafeChance = NumberSetting("StrafeChance",       20,  0, 100, 1)
    private val reversePatternBreak = NumberSetting("PatternBreak",       12,  0,  25, 1)
    private val reverseJump         = BooleanSetting("ReverseJump",        true)
    private val reverseJumpMotion   = NumberSetting("RevJumpMotionVar",   0.04, 0.0, 0.10, 0.01)
    private val reverseDebug        = BooleanSetting("ReverseDebug",       false)

    // Push
    private val pushXZ       = NumberSetting("Push",      1.1, 0.01, 20.0, 2)
    private val pushStart    = NumberSetting("PushStart", 9.0, 1.0,  10.0, 0)
    private val pushEnd      = NumberSetting("PushEnd",   2.0, 1.0,  10.0, 0)
    private val pushOnGround = BooleanSetting("PushOnGround", false)

    // Legit
    private val legitStrafe = BooleanSetting("LegitStrafe", true)

    // TickZero
    private val tickZeroHurtTime = NumberSetting("TickZeroHurtTime", 4, 1, 10, 0)

    // Reduce (packet-level)
    private val reduceXZ         = NumberSetting("ReduceXZ",     95, 80, 100, 1)
    private val reduceY          = NumberSetting("ReduceY",      100, 80, 100, 1)
    private val reduceOnlyWindow = BooleanSetting("OnlyInWindow", true)
    private val reduceSpread     = NumberSetting("Spread",       2.0, 0.0, 5.0, 0.1)

    // ── Shared runtime state ───────────────────────────────────────
    private val random = Random()
    private var prevMotionY    = 0.0
    private var ticksSinceHit  = 0
    private var wasHurt        = false
    private var cooldownTimer  = 0
    private var isFallDamage   = false
    private var currentDelay   = 0
    private var delayCounter   = 0
    private var pendingJump    = false
    private var jumpKeyHeld    = false

    // Reverse automaton
    private enum class ReverseState { IDLE, DELAY, CORRECTING, MOVING }
    private var reverseState               = ReverseState.IDLE
    private var reverseTimer               = 0
    private var reverseStrafeLeft          = false
    private var reverseOriginalVelY        = 0.0
    private var ticksSinceCorrection       = 0
    private var restartCount               = 0
    private var lastRestartTime            = 0L
    private var lastCorrectionStartTime    = 0L
    private var useConservativeProfile     = false

    init {
        addSettings(mode,
            xzModify, yModify, ignoreExplosion,
            fallDamageCheck, debug, simDebugLog, chance, resetTicks, cooldownTicks,
            randomize, delayMin, delayMax, maxDistance,
            jumpMotionVar, jumpVelWindow,
            reverseChance, reverseWaitTicks, reverseMoveTicks,
            reverseStrafeChance, reversePatternBreak,
            reverseJump, reverseJumpMotion, reverseDebug,
            pushXZ, pushStart, pushEnd, pushOnGround,
            legitStrafe, tickZeroHurtTime,
            reduceXZ, reduceY, reduceOnlyWindow, reduceSpread)
        addHudSettings()
    }

    // ========================== Packet handling ======================

    fun isModifyMode() = isEnabled && mode.value == "Modify"

    fun handleVelocityPacket(pkt: SPacketEntityVelocityAccessor): Boolean {
        val player = mc.player ?: return false

        val rawX = pkt.motionX
        val rawY = pkt.motionY
        val rawZ = pkt.motionZ

        val vx = rawX / 8000.0
        val vy = rawY / 8000.0
        val vz = rawZ / 8000.0

        isFallDamage = (rawX == 0 && rawZ == 0 && rawY < 0)

        val sel = mode.value

        if (debug.value) {
            sendClientMessage("§eVel vx=%.3f vy=%.3f vz=%.3f fall=$isFallDamage".format(vx, vy, vz))
        }

        // Enable shadow mode for Modify/Reduce — protects the simulator
        if (sel == "Modify" || sel == "Reduce") {
            ClientSimulator.shadowMode = true
        }

        return when (sel) {
            "Modify"  -> handleModifyPacket(pkt, rawX, rawY, rawZ)
            "Reduce"  -> { handleReducePacket(pkt, vx, vy, vz); false }
            "Reverse" -> { handleReversePacket(vx, vy, vz); false }
            else      -> { ClientSimulator.applyVelocity(vx, vy, vz); false }
        }
    }

    private fun handleModifyPacket(pkt: SPacketEntityVelocityAccessor,
                                    rawX: Int, rawY: Int, rawZ: Int): Boolean {
        val xzFactor = xzModify.value / 100.0
        val yFactor  = yModify.value  / 100.0

        if (xzFactor <= 0.0 && yFactor <= 0.0) {
            pkt.motionX = 0
            pkt.motionY = 0
            pkt.motionZ = 0
            ClientSimulator.applyVelocity(0.0, 0.0, 0.0)
            return true
        }

        val newX = (rawX * xzFactor).toInt()
        val newY = (rawY * yFactor).toInt()
        val newZ = (rawZ * xzFactor).toInt()
        pkt.motionX = newX
        pkt.motionY = newY
        pkt.motionZ = newZ
        ClientSimulator.applyVelocity(newX / 8000.0, newY / 8000.0, newZ / 8000.0)
        return false
    }

    /**
     * Packet-level Reduce — modifies the incoming velocity before the
     * simulator sees it, keeping the predicted trajectory clean.
     */
    private fun handleReducePacket(pkt: SPacketEntityVelocityAccessor,
                                    vx: Double, vy: Double, vz: Double) {
        if (reduceOnlyWindow.value && !ClientSimulator.isInVelocityWindow()) {
            ClientSimulator.applyVelocity(vx, vy, vz)
            return
        }

        val expectedMag = sqrt(vx * vx + vz * vz)
        if (expectedMag < 0.001) {
            ClientSimulator.applyVelocity(vx, vy, vz)
            return
        }

        val tolXZ = ClientSimulator.toleranceXZ
        val tolY  = ClientSimulator.toleranceY

        var xzFactor = reduceXZ.value / 100.0
        var yFactor  = reduceY.value  / 100.0
        val spread   = reduceSpread.value / 100.0

        // Apply spread only when tolerance is wide enough
        if (spread > 0.001 && tolXZ > 0.005) {
            xzFactor += (random.nextDouble() - 0.5) * 2.0 * spread
            xzFactor = xzFactor.coerceIn(0.70, 0.995)
            yFactor  += (random.nextDouble() - 0.5) * 2.0 * spread
            yFactor  = yFactor.coerceIn(0.70, 0.995)
        }

        // ── Entropy injection (drift + noise) ─────────────────────
        val drift = sin(System.currentTimeMillis() / 1000.0 + random.nextDouble()) * 0.05
        val noise = (random.nextDouble() - 0.5) * 0.08

        val safeXZ = safeReduceFactor(expectedMag, tolXZ, xzFactor)
            .let { max(it + drift + noise, max(0.65, it - 0.15)) }
            .coerceIn(0.65, safeReduceFactor(expectedMag, tolXZ, xzFactor))

        val safeY = safeReduceFactor(abs(vy), tolY, yFactor)
            .let { max(it + drift + noise, max(0.70, it - 0.15)) }
            .coerceIn(0.70, safeReduceFactor(abs(vy), tolY, yFactor))

        // Occasional skip — real players sometimes don't reduce at all
        val (finalXZ, finalY) = if (random.nextDouble() < 0.12) 1.0 to 1.0
                                else safeXZ to safeY

        val newVx = vx * finalXZ
        val newVy = vy * finalY
        val newVz = vz * finalXZ

        pkt.motionX = (newVx * 8000.0).toInt()
        pkt.motionY = (newVy * 8000.0).toInt()
        pkt.motionZ = (newVz * 8000.0).toInt()

        ClientSimulator.applyVelocity(newVx, newVy, newVz)

        if (debug.value) {
            val reductionPct = (1.0 - finalXZ) * 100.0
            sendClientMessage("§dReduce §7vx=%.3f -> %.3f (%.1f%%) tol=%.3f".format(
                vx, newVx, reductionPct, tolXZ))
        }
    }

    /** Returns the largest safe reduction factor for a single axis. */
    private fun safeReduceFactor(expectedMag: Double, tolerance: Double, userFactor: Double): Double {
        if (expectedMag < 0.001 || tolerance < 0.000001) return 1.0
        val requestedReduction = expectedMag * (1.0 - userFactor)
        if (requestedReduction <= tolerance) return userFactor
        val minFactor = 1.0 - (tolerance / expectedMag)
        return max(userFactor, minFactor)
    }

    fun handleExplosion(packet: SPacketExplosion) {
        if (mode.value != "Modify" || !ignoreExplosion.value) return
        val pkt = packet as SPacketExplosionAccessor
        val xzFactor = xzModify.value / 100.0
        val yFactor  = yModify.value  / 100.0
        pkt.motionX = pkt.motionX * xzFactor.toFloat()
        pkt.motionY = pkt.motionY * yFactor.toFloat()
        pkt.motionZ = pkt.motionZ * xzFactor.toFloat()
    }

    // ========================== Per-tick update ======================

    override fun onClientTick() {
        if (!isEnabled) return
        val player = mc.player ?: return
        if (mc.world == null) return

        SimDebug.enabled  = simDebugLog.value
        SimDebug.logToChat = simDebugLog.value

        // Release shadow mode when we exit the velocity window
        if (!ClientSimulator.isInVelocityWindow()) {
            ClientSimulator.shadowMode = false
        }

        if (cooldownTimer > 0) {
            cooldownTimer--
            releaseJumpKey()
        }

        val isHurt = player.hurtTime > 0
        if (isHurt && !wasHurt) ticksSinceHit = 0
        if (isHurt) ticksSinceHit++ else ticksSinceHit = 0
        wasHurt = isHurt

        val justLanded = prevMotionY < -0.01 && player.onGround
        prevMotionY = player.motionY

        when (mode.value) {
            "JumpReset" -> handleJumpReset(justLanded)
            "Reverse"   -> handleReverseAutomaton()
            "Push"      -> handlePush()
            "Legit"     -> handleLegit()
            "TickZero"  -> handleTickZero()
        }
    }

    // ========================== JumpReset ===========================

    private fun handleJumpReset(justLanded: Boolean) {
        if (ClientSimulator.isFlyingJumpExpected()) return
        val player = mc.player ?: return

        if (pendingJump && player.onGround && isTargetingEnemyInComboRange()) {
            if (randomize.value) {
                delayCounter++
                if (delayCounter >= currentDelay) {
                    performJumpWithYVariation()
                    pendingJump = false
                    delayCounter = 0
                }
            } else {
                performJumpWithYVariation()
                pendingJump = false
            }
            return
        }

        if (cooldownTimer > 0) return
        if (!player.onGround) return
        if (!justLanded) return
        if (fallDamageCheck.value && isFallDamage) return
        if (ticksSinceHit !in 1..resetTicks.value.toInt()) return
        if (jumpVelWindow.value && ClientSimulator.isInVelocityWindow()) return
        if (!isTargetingEnemyInComboRange()) return
        if (random.nextDouble() * 100 >= chance.value) return

        if (randomize.value) {
            val lo = delayMin.value.toInt()
            val hi = max(lo, delayMax.value.toInt())
            currentDelay = if (hi > lo) lo + random.nextInt(hi - lo + 1) else lo
            delayCounter = 0
            if (currentDelay == 0) performJumpWithYVariation()
            else pendingJump = true
        } else {
            performJumpWithYVariation()
        }
    }

    private fun performJumpWithYVariation() {
        val player = mc.player ?: return

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.keyCode, true)
        jumpKeyHeld = true
        cooldownTimer = cooldownTicks.value.toInt()

        val variation = (random.nextDouble() - 0.5) * 2.0 * jumpMotionVar.value
        player.motionY = 0.42 + variation

        if (debug.value) {
            sendClientMessage("§aJR §7tick=$ticksSinceHit hurt=${player.hurtTime} yMotion=%.3f var=%.3f".format(
                player.motionY, variation))
        }
    }

    private fun releaseJumpKey() {
        if (jumpKeyHeld) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.keyCode, false)
            jumpKeyHeld = false
        }
    }

    private fun isTargetingEnemyInComboRange(): Boolean {
        val player = mc.player ?: return false
        val world  = mc.world  ?: return false
        val lastAttacked = player.lastAttackedEntity ?: return false
        if (lastAttacked.isDead) return false

        val ray = mc.objectMouseOver ?: return false
        if (ray.typeOfHit != RayTraceResult.Type.ENTITY) return false
        if (ray.entityHit !== lastAttacked) return false

        if (player.getDistance(lastAttacked) > maxDistance.value) return false

        val dx = lastAttacked.posX - player.posX
        val dz = lastAttacked.posZ - player.posZ
        var angleToAttacker = Math.toDegrees(kotlin.math.atan2(dz, dx)) - player.rotationYaw
        angleToAttacker = (angleToAttacker % 360 + 540) % 360 - 180
        if (abs(angleToAttacker) > 90.0) return false

        return Mouse.isButtonDown(0)
    }

    // ========================== Reverse =============================

    private fun handleReversePacket(vx: Double, vy: Double, vz: Double) {
        if (mc.player == null) return
        if (random.nextDouble() * 100 >= reverseChance.value) return

        val mag = sqrt(vx * vx + vz * vz)
        if (mag < 0.015) return

        val now = System.currentTimeMillis()

        if (reverseState == ReverseState.DELAY) {
            reverseOriginalVelY = vy
            reverseTimer = 1
            ticksSinceCorrection = 0
            lastCorrectionStartTime = now
            useConservativeProfile = true
            if (reverseDebug.value) {
                sendClientMessage("§dReverse §7DELAY updated target (conservative) vx=%.3f vy=%.3f vz=%.3f".format(vx, vy, vz))
            }
            return
        }

        if (reverseState == ReverseState.CORRECTING || reverseState == ReverseState.MOVING) {
            if (now - lastCorrectionStartTime < 150) {
                if (reverseDebug.value) sendClientMessage("§dReverse §7cooldown ${now - lastCorrectionStartTime}ms, skipping")
                return
            }
        }

        if (now - lastRestartTime > 500) restartCount = 0
        restartCount++
        lastRestartTime = now
        if (restartCount > 8) {
            if (reverseDebug.value) sendClientMessage("§dReverse §7burst protection, skipping")
            return
        }

        if (reverseState == ReverseState.CORRECTING || reverseState == ReverseState.MOVING) {
            stopReverseMovement()
            if (reverseDebug.value) sendClientMessage("§dReverse §7new velocity, resetting")
        }

        useConservativeProfile = reverseState != ReverseState.IDLE || (now - lastCorrectionStartTime) < 300

        reverseOriginalVelY = vy
        reverseState = ReverseState.DELAY
        reverseTimer = 1
        ticksSinceCorrection = 0
        lastCorrectionStartTime = now

        if (reverseDebug.value) {
            sendClientMessage("§dReverse §7queued, delaying 1 tick (${if (useConservativeProfile) "conservative" else "idle"}) vx=%.3f vy=%.3f vz=%.3f".format(vx, vy, vz))
        }
    }

    private fun handleReverseAutomaton() {
        val player = mc.player ?: return

        if (jumpKeyHeld && player.onGround) releaseJumpKey()

        when (reverseState) {
            ReverseState.IDLE -> {}
            ReverseState.DELAY -> {
                if (reverseTimer > 0) {
                    reverseTimer--
                    if (reverseTimer == 0) startCorrectionPhase()
                }
            }
            ReverseState.CORRECTING -> {
                if (reverseTimer > 0) {
                    applyMotionCorrection()
                    reverseTimer--
                    if (reverseTimer == 0) {
                        if (random.nextDouble() < reversePatternBreak.value / 100.0) {
                            reverseTimer = 3
                            if (reverseDebug.value) sendClientMessage("§dReverse §7pattern break — shortened to ${reverseTimer + 1} ticks")
                            return
                        }
                        startPostCorrectionMovement()
                    }
                }
            }
            ReverseState.MOVING -> {
                if (reverseTimer > 0) reverseTimer--
                else {
                    stopReverseMovement()
                    resetReverseState()
                    if (reverseDebug.value) sendClientMessage("§dReverse §7finished")
                }
            }
        }
    }

    private fun startCorrectionPhase() {
        if (mc.player == null) { resetReverseState(); return }

        reverseTimer = reverseWaitTicks.value.toInt()
        reverseState = ReverseState.CORRECTING
        ticksSinceCorrection = 0

        if (reverseJump.value && mc.player!!.onGround && abs(reverseOriginalVelY) < 0.2) {
            performReverseJump()
        }

        if (reverseDebug.value) {
            val extra = if (reverseJump.value && mc.player!!.onGround && abs(reverseOriginalVelY) < 0.2) " + jump" else ""
            sendClientMessage("§dReverse §7correcting for $reverseTimer ticks (${if (useConservativeProfile) "conservative" else "idle"})$extra")
        }
    }

    private fun applyMotionCorrection() {
        val player = mc.player ?: return
               if (ClientSimulator.shadowMode) return

        val predictedX = ClientSimulator.expectedX
        val predictedY = ClientSimulator.expectedY
        val predictedZ = ClientSimulator.expectedZ
        val tolXZ = ClientSimulator.toleranceXZ
        val tolY  = ClientSimulator.toleranceY

        val dx = abs(player.motionX - predictedX)
        val dy = abs(player.motionY - predictedY)
        val dz = abs(player.motionZ - predictedZ)

        if (dx < tolXZ && dz < tolXZ && dy < tolY) return

        player.motionX = predictedX
        player.motionY = predictedY
        player.motionZ = predictedZ

        if (reverseDebug.value && ticksSinceCorrection < 5) {
            sendClientMessage("§dCorrection §7t=$ticksSinceCorrection x=%.4f y=%.4f z=%.4f tolXZ=%.4f tolY=%.4f".format(
                predictedX, predictedY, predictedZ, tolXZ, tolY))
        }
        ticksSinceCorrection++
    }

    private fun startPostCorrectionMovement() {
        if (mc.player == null) { resetReverseState(); return }

        reverseTimer = (reverseMoveTicks.value.toInt() * (0.7 + random.nextFloat() * 0.6)).toInt()
        reverseTimer = max(3, reverseTimer)
        reverseState = ReverseState.MOVING

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, true)

        val strafeRoll = random.nextDouble() * 100
        val strafeChance = reverseStrafeChance.value

        when {
            strafeRoll < strafeChance * 0.33 -> {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, false)
                applyStrafeKey()
            }
            strafeRoll < strafeChance -> applyStrafeKey()
        }

        if (reverseDebug.value) {
            val dir = when {
                reverseStrafeLeft -> "left"
                mc.gameSettings.keyBindRight.isKeyDown -> "right"
                else -> "none"
            }
            sendClientMessage("§dReverse §7moving forward $reverseTimer ticks, strafe=$dir")
        }
    }

    private fun applyStrafeKey() {
        if (random.nextBoolean()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.keyCode, true)
            reverseStrafeLeft = true
        } else {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.keyCode, true)
            reverseStrafeLeft = false
        }
    }

    private fun stopReverseMovement() {
        mc.player ?: return
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindForward.keyCode, false)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindLeft.keyCode, false)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindRight.keyCode, false)
        reverseStrafeLeft = false
    }

    private fun resetReverseState() {
        stopReverseMovement()
        releaseJumpKey()
        reverseState = ReverseState.IDLE
        reverseTimer = 0
        reverseOriginalVelY = 0.0
        ticksSinceCorrection = 0
        restartCount = 0
        lastRestartTime = 0L
        lastCorrectionStartTime = 0L
        useConservativeProfile = false
    }

    private fun performReverseJump() {
        val player = mc.player ?: return
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.keyCode, true)
        jumpKeyHeld = true
        val variation = (random.nextDouble() - 0.5) * 2.0 * reverseJumpMotion.value
        player.motionY = 0.42 + variation
        if (reverseDebug.value) sendClientMessage("§dReverse §7jump y=%.3f".format(player.motionY))
    }

    // ========================== Push / Legit / TickZero =============

    private fun handlePush() {
        val player = mc.player ?: return
        val start = pushStart.value.toInt()
        val end   = pushEnd.value.toInt()
        if (player.hurtTime in min(start, end)..max(start, end)) {
            val factor = (pushXZ.value / 100.0).toFloat()
            player.moveRelative(0f, 0f, factor, 0.98f)
            if (pushOnGround.value) player.onGround = true
        }
    }

    private fun handleLegit() {
        val player = mc.player ?: return
        if (player.hurtTime <= 0) return
        if (!legitStrafe.value) return
        if (ClientSimulator.state().reduceTicks > 0) return
        player.motionX *= 0.6
        player.motionZ *= 0.6
    }

    private fun handleTickZero() {
        val player = mc.player ?: return
        if (player.hurtTime == tickZeroHurtTime.value.toInt()) {
            player.motionX = 0.0
            player.motionZ = 0.0
        }
    }

    // ========================== Settings UI =========================

    override fun getVisibleSettings(): MutableList<Setting> {
        val filtered = mutableListOf<Setting>()
        filtered += mode
        when (mode.value) {
            "Modify"    -> { filtered += listOf(xzModify, yModify, ignoreExplosion, debug, simDebugLog) }
            "JumpReset" -> { filtered += listOf(fallDamageCheck, debug, simDebugLog, chance, resetTicks,
                cooldownTicks, randomize, delayMin, delayMax, maxDistance, jumpMotionVar, jumpVelWindow) }
            "Reverse"   -> { filtered += listOf(reverseChance, reverseWaitTicks, reverseMoveTicks,
                reverseStrafeChance, reversePatternBreak, reverseJump, reverseJumpMotion, reverseDebug) }
            "Push"      -> { filtered += listOf(pushXZ, pushStart, pushEnd, pushOnGround, debug, simDebugLog) }
            "Legit"     -> { filtered += listOf(legitStrafe, debug, simDebugLog) }
            "TickZero"  -> { filtered += listOf(tickZeroHurtTime, debug, simDebugLog) }
            "Reduce"    -> { filtered += listOf(reduceXZ, reduceY, reduceOnlyWindow, reduceSpread, debug, simDebugLog) }
        }
        return filtered
    }

    fun onAttack() {
        if (!isEnabled || mc.player == null) return
        if (mode.value == "JumpReset" && ClientSimulator.isInVelocityWindow()) return
    }

    override fun onEnable() {}
    override fun onDisable() {
        releaseJumpKey()
        stopReverseMovement()
        ClientSimulator.shadowMode = false
        SimDebug.enabled = false
        SimDebug.logToChat = false
    }
}