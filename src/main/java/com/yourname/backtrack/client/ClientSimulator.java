package com.yourname.backtrack.client

import net.minecraft.client.Minecraft
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Enchantments
import net.minecraft.util.math.AxisAlignedBB
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Client-side physical simulator that mirrors Intave's server-side movement
 * prediction.  Runs alongside the real player, computes expected motion,
 * and exposes tolerance windows that [VelocityModule.Reduce] exploits.
 */
object ClientSimulator {

    // ── Sub-components ────────────────────────────────────────────
    private val s = MovementSimState()
    private val inputCapture = MovementInputCapture
    private val physics = MovementPhysicsEngine

    // ── Physics constants ──────────────────────────────────────────
    private const val GRAVITY = 0.08
    private const val Y_MULT = 0.98
    private const val AIR_SLIP = 0.91

    /** Push strength per overlapping player (vanilla: 0.05 per axis). */
    private const val ENTITY_PUSH_STRENGTH = 0.05

    // ── Public state ───────────────────────────────────────────────

    /** When true, Velocity Reduce/Reverse must not modify player motion. */
    @JvmField var shadowMode = false

    // ── Accessors ──────────────────────────────────────────────────

    fun state(): MovementSimState = s

    fun reset() {
        // Replace the entire state with a fresh default instance
        // (Kotlin data class gives us a clean zero-state copy).
        val fresh = MovementSimState()
        // Copy all fields from fresh into s
        s.verifiedX = fresh.verifiedX
        s.verifiedY = fresh.verifiedY
        s.verifiedZ = fresh.verifiedZ
        s.lastX = fresh.lastX
        s.lastY = fresh.lastY
        s.lastZ = fresh.lastZ
        s.baseMotionX = fresh.baseMotionX
        s.baseMotionY = fresh.baseMotionY
        s.baseMotionZ = fresh.baseMotionZ
        s.baseMotionXBeforeVelocity = fresh.baseMotionXBeforeVelocity
        s.baseMotionYBeforeVelocity = fresh.baseMotionYBeforeVelocity
        s.baseMotionZBeforeVelocity = fresh.baseMotionZBeforeVelocity
        s.predictedMotionX = fresh.predictedMotionX
        s.predictedMotionY = fresh.predictedMotionY
        s.predictedMotionZ = fresh.predictedMotionZ
        s.pastExternalVelocity = fresh.pastExternalVelocity
        s.pastVelocity = fresh.pastVelocity
        s.pastPlayerReduceAttackPhysics = fresh.pastPlayerReduceAttackPhysics
        s.reduceTicks = fresh.reduceTicks
        s.pastFlyingPacketAccurate = fresh.pastFlyingPacketAccurate
        s.pastWaterMovement = fresh.pastWaterMovement
        s.pastInWeb = fresh.pastInWeb
        s.webTicks = fresh.webTicks
        s.onGround = fresh.onGround
        s.lastOnGround = fresh.lastOnGround
        s.collidedHorizontally = fresh.collidedHorizontally
        s.collidedVertically = fresh.collidedVertically
        s.sprinting = fresh.sprinting
        s.sprintingAllowed = fresh.sprintingAllowed
        s.sneaking = fresh.sneaking
        s.handActive = fresh.handActive
        s.physicsUnpredictableVelocityExpected = fresh.physicsUnpredictableVelocityExpected
        s.inWater = fresh.inWater
        s.inWeb = fresh.inWeb
        s.inLava = fresh.inLava
        s.onClimbable = fresh.onClimbable
        s.forwardKey = fresh.forwardKey
        s.strafeKey = fresh.strafeKey
        s.jumpKey = fresh.jumpKey
        s.sprintKey = fresh.sprintKey
        s.sneakKey = fresh.sneakKey
        s.rotationYaw = fresh.rotationYaw
        s.yawSin = fresh.yawSin
        s.yawCos = fresh.yawCos
        s.aiMoveSpeed = fresh.aiMoveSpeed
        s.blockSlipperiness = fresh.blockSlipperiness
        s.jumpMotion = fresh.jumpMotion
        s.resetMotion = fresh.resetMotion
        s.toleranceXZ = fresh.toleranceXZ
        s.toleranceY = fresh.toleranceY
        s.lastMotionX = fresh.lastMotionX
        s.lastMotionY = fresh.lastMotionY
        s.lastMotionZ = fresh.lastMotionZ
        s.playerPosX = fresh.playerPosX
        s.playerPosY = fresh.playerPosY
        s.playerPosZ = fresh.playerPosZ
        s.positionInitialized = fresh.positionInitialized
        s.physicsPacketRelinkFlyVL = fresh.physicsPacketRelinkFlyVL
        s.entityPushX = fresh.entityPushX
        s.entityPushZ = fresh.entityPushZ
    }

    fun beginTick() { s.beginTick() }

    // ── Sync ──────────────────────────────────────────────────────

    fun syncVerifiedFromPlayer(mc: Minecraft) {
        val player = mc.player ?: return
        s.playerPosX = player.posX
        s.playerPosY = player.posY
        s.playerPosZ = player.posZ

        if (!s.positionInitialized) {
            s.verifiedX = player.posX
            s.verifiedY = player.posY
            s.verifiedZ = player.posZ
            s.lastX = s.verifiedX
            s.lastY = s.verifiedY
            s.lastZ = s.verifiedZ
            s.positionInitialized = true
            return
        }

        val drift = sqrt(
            sq(player.posX - s.verifiedX) +
            sq(player.posY - s.verifiedY) +
            sq(player.posZ - s.verifiedZ)
        )
        if (drift > 8.0 || (drift > 1.0 && s.isInVelocityWindow())) {
            s.verifiedX = player.posX
            s.verifiedY = player.posY
            s.verifiedZ = player.posZ
            s.physicsPacketRelinkFlyVL = 0
        }
    }

    fun predictFlyingPacketBeforeVelocity() {
        if (s.pastVelocity != 0) return
        var mx = s.baseMotionXBeforeVelocity * AIR_SLIP
        var my = (s.baseMotionYBeforeVelocity - GRAVITY) * Y_MULT
        var mz = s.baseMotionZBeforeVelocity * AIR_SLIP
        if (mx == 0.0 || my == 0.0 || mz == 0.0) return

        val mc = Minecraft.getMinecraft() ?: return
        val r = VanillaPlayerCollider.collide(
            mc, s.verifiedX, s.verifiedY, s.verifiedZ, mx, my, mz
        )
        if ((r.onGround || s.onGround) &&
            (r.motionX * r.motionX + r.motionY * r.motionY + r.motionZ * r.motionZ) < 0.009) {
            s.physicsUnpredictableVelocityExpected = true
            s.pastFlyingPacketAccurate = 0
        }
    }

    // ── Entity push ────────────────────────────────────────────────

    /**
     * Scans nearby [EntityPlayer] instances and accumulates the vanilla
     * `applyEntityCollision` push into [MovementSimState.entityPushX]
     * and [MovementSimState.entityPushZ].
     *
     * Called once per tick before [simulate].
     */
    fun collectEntityPush(mc: Minecraft) {
        val player = mc.player ?: return
        val world  = mc.world  ?: return

        var pushX = 0.0
        var pushZ = 0.0
        val selfBox = player.entityBoundingBox.expand(0.2, 0.0, 0.2)

        for (e in world.loadedEntityList) {
            if (e !is EntityPlayer) continue
            if (e === player) continue
            if (e.noClip && player.noClip) continue
            val otherBox = e.entityBoundingBox
            if (!selfBox.intersects(otherBox)) continue

            var dx = e.posX - player.posX
            var dz = e.posZ - player.posZ
            val maxDelta = maxOf(abs(dx), abs(dz))
            if (maxDelta < 0.01) continue

            val dist = sqrt(dx * dx + dz * dz)
            dx /= dist
            dz /= dist
            val scale = minOf(1.0, ENTITY_PUSH_STRENGTH / maxDelta)
            // other pushes us → we subtract (vanilla: attacker subtracts from self)
            pushX -= dx * scale
            pushZ -= dz * scale
        }

        s.entityPushX = pushX
        s.entityPushZ = pushZ
    }

    // ── Simulation pipeline ────────────────────────────────────────

    fun simulate() {
        val mc = Minecraft.getMinecraft() ?: return
        if (mc.player == null) return

        collectEntityPush(mc)
        inputCapture.capture(s, mc)
        maybeSearchKeys(mc)
        physics.simulate(s)
        ToleranceCalculator.compute(s)
        SimDebug.logTick(this)
    }

    private fun maybeSearchKeys(mc: Minecraft) {
        val pred = physics.simulateOneTick(s, s.forwardKey, s.strafeKey)
        val err  = sqrt(sq(pred[0] - mc.player.motionX) + sq(pred[2] - mc.player.motionZ))
        ToleranceCalculator.compute(s)

        val needSearch = err > maxOf(s.toleranceXZ * 2.0, 0.008)
            || (s.pastVelocity > 0 && s.pastVelocity < 25)
        if (!needSearch) return

        val search = InputKeySearcher.search(s, physics, mc.player.motionX, mc.player.motionZ)
        if (search != null) {
            s.forwardKey = search[0]
            s.strafeKey = search[1]
        }
    }

    fun recalculateAfterOutgoingPacket() {
        val mc = Minecraft.getMinecraft() ?: return
        if (mc.player == null) return
        predictFlyingPacketBeforeVelocity()
        inputCapture.capture(s, mc)
        physics.simulate(s)
        ToleranceCalculator.compute(s)
    }

    fun syncFromPlayer() {
        val mc = Minecraft.getMinecraft() ?: return
        val player = mc.player ?: return
        s.baseMotionX = player.motionX
        s.baseMotionY = player.motionY
        s.baseMotionZ = player.motionZ
    }

    fun advanceVerifiedFromPlayer(mc: Minecraft) {
        val player = mc.player ?: return
        val dx = player.posX - s.lastX
        val dy = player.posY - s.lastY
        val dz = player.posZ - s.lastZ
        if (abs(dx) + abs(dy) + abs(dz) > 1e-6) {
            s.verifiedX = player.posX
            s.verifiedY = player.posY
            s.verifiedZ = player.posZ
        }
        s.lastX = player.posX
        s.lastY = player.posY
        s.lastZ = player.posZ
    }

    fun updateOnGround(mc: Minecraft) {
        s.onGround = VanillaPlayerCollider.isOnGround(mc, s.verifiedX, s.verifiedY, s.verifiedZ)
    }

    // ── Tick transition ────────────────────────────────────────────

    fun prepareNextTick() {
        val mc = Minecraft.getMinecraft() ?: return
        val slip = if (s.lastOnGround)
            MovementFriction.groundSlipperinessForDecay(mc, s.verifiedX, s.verifiedY, s.verifiedZ)
        else
            AIR_SLIP

        s.baseMotionX *= slip
        s.baseMotionY = if (s.lastOnGround) 0.0 else (s.baseMotionY - GRAVITY) * Y_MULT
        s.baseMotionZ *= slip

        if (abs(s.baseMotionX) < s.resetMotion) s.baseMotionX = 0.0
        if (abs(s.baseMotionY) < s.resetMotion) s.baseMotionY = 0.0
        if (abs(s.baseMotionZ) < s.resetMotion) s.baseMotionZ = 0.0

        if (s.inWater) {
            s.baseMotionX *= 0.8
            s.baseMotionZ *= 0.8
        }
        if (s.inWeb) {
            s.baseMotionX = 0.0
            s.baseMotionY = 0.0
            s.baseMotionZ = 0.0
        }

        s.lastOnGround = s.onGround
        if (s.pastPlayerReduceAttackPhysics < 100) s.pastPlayerReduceAttackPhysics++
        s.pastFlyingPacketAccurate++
    }

    // ── External events ────────────────────────────────────────────

    fun applyVelocity(vx: Double, vy: Double, vz: Double) {
        s.baseMotionXBeforeVelocity = s.baseMotionX
        s.baseMotionYBeforeVelocity = s.baseMotionY
        s.baseMotionZBeforeVelocity = s.baseMotionZ
        s.baseMotionX = vx
        s.baseMotionY = vy
        s.baseMotionZ = vz
        s.pastExternalVelocity = 0
        s.pastVelocity = 0
        s.reduceTicks = 0
    }

    fun applyExplosion(vx: Double, vy: Double, vz: Double) {
        s.baseMotionX += vx
        s.baseMotionY += vy
        s.baseMotionZ += vz
    }

    fun handleTeleport(x: Double, y: Double, z: Double) {
        s.verifiedX = x
        s.verifiedY = y
        s.verifiedZ = z
        s.lastX = x
        s.lastY = y
        s.lastZ = z
        s.baseMotionX = 0.0
        s.baseMotionY = 0.0
        s.baseMotionZ = 0.0
        s.predictedMotionX = 0.0
        s.predictedMotionY = 0.0
        s.predictedMotionZ = 0.0
        s.pastExternalVelocity = 100
        s.pastVelocity = 100
        s.reduceTicks = 0
        s.physicsPacketRelinkFlyVL = 0
        s.physicsUnpredictableVelocityExpected = false
    }

    fun onOutgoingMovement(posChanged: Boolean, x: Double, y: Double, z: Double) {
        if (posChanged) {
            s.verifiedX = x
            s.verifiedY = y
            s.verifiedZ = z
            s.positionInitialized = true
        }
        s.pastExternalVelocity++
        s.pastVelocity++
        s.reduceTicks = 0
    }

    fun onAttack(player: EntityPlayer?, target: EntityLivingBase?) {
        if (player == null || target == null) return
        if (target !is EntityPlayer) return

        val sprint  = player.isSprinting
        val kbLevel = EnchantmentHelper.getEnchantmentLevel(
            Enchantments.KNOCKBACK, player.heldItemMainhand
        )
        val attackDamage = player
            .getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE)
            .attributeValue
        val hasWeaponDamage = attackDamage > 0.0

        if (hasWeaponDamage && (sprint || kbLevel > 0)) {
            s.pastPlayerReduceAttackPhysics = 0
            val limitedToOneAttack = kbLevel == 0
            if (s.reduceTicks == 0 || !limitedToOneAttack) {
                if (s.reduceTicks < 3) s.reduceTicks++
            }
        }
    }

    // ── Convenience queries ────────────────────────────────────────

    fun isInVelocityWindow(): Boolean = s.isInVelocityWindow()

    fun isFlyingJumpExpected(): Boolean {
        val px = s.predictedMotionX
        val py = s.predictedMotionY
        val pz = s.predictedMotionZ
        if (abs(px) >= 0.1 || abs(pz) >= 0.1) return false
        if (abs(py - s.jumpMotion) >= 0.05) return false
        if (!s.onGround && !s.lastOnGround) return false

        val mc = Minecraft.getMinecraft() ?: return false
        val player = mc.player ?: return false
        val diffY = py - player.motionY
        return diffY > 0.01 && diffY < 0.03
    }

    val expectedX: Double get() = s.predictedMotionX
    val expectedY: Double get() = s.predictedMotionY
    val expectedZ: Double get() = s.predictedMotionZ
    val expectedMag: Double get() = sqrt(
        s.predictedMotionX * s.predictedMotionX + s.predictedMotionZ * s.predictedMotionZ
    )

    val toleranceXZ: Double get() { ToleranceCalculator.compute(s); return s.toleranceXZ }
    val toleranceY: Double  get() { ToleranceCalculator.compute(s); return s.toleranceY }

    val pastExternalVelocity: Int get() = s.pastExternalVelocity

    // ── Internal helpers ───────────────────────────────────────────

    private fun sq(v: Double): Double = v * v
}