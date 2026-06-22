package com.yourname.backtrack.client

import net.minecraft.client.Minecraft
import kotlin.math.abs
import kotlin.math.sqrt

object MovementPhysicsEngine {

    private const val GRAVITY = 0.08
    private const val Y_MULT = 0.98
    private const val AIR_SLIP = 0.91
    private const val MAX_RELINK = 2

    private val collider = VanillaPlayerCollider()

    fun simulate(s: MovementSimState) {
        val mc = Minecraft.getMinecraft() ?: return
        if (mc.player == null) return

        var mx = s.baseMotionX
        var my = s.baseMotionY
        var mz = s.baseMotionZ

        if (s.reduceTicks > 0) {
            repeat(s.reduceTicks) {
                mx *= 0.6
                mz *= 0.6
            }
            if (abs(mx) < s.resetMotion) mx = 0.0
            if (abs(mz) < s.resetMotion) mz = 0.0
        }

        when {
            s.inWater      -> simulateWater(mc, s, mx, my, mz)
            s.inLava       -> simulateLava(mc, s, mx, my, mz)
            s.onClimbable  -> simulateLadder(mc, s, mx, my, mz)
            else -> {
                val acc = accelerateGroundAir(mc, s, mx, my, mz)
                if (s.jumpKey && (s.onGround || s.lastOnGround)) {
                    acc[1] = s.jumpMotion.toDouble()
                    if (s.sprintingAllowed && s.sprintKey) {
                        acc[0] -= s.yawSin * 0.2
                        acc[2] += s.yawCos * 0.2
                    }
                }
                val out = relink(mc, s, acc[0], acc[1], acc[2])
                s.predictedMotionX = out[0]
                s.predictedMotionY = out[1]
                s.predictedMotionZ = out[2]
            }
        }

        // Entity push: applied after all motion is resolved (vanilla applyEntityCollision)
        if (s.entityPushX != 0.0 || s.entityPushZ != 0.0) {
            s.predictedMotionX += s.entityPushX
            s.predictedMotionZ += s.entityPushZ
        }
        s.reduceTicks = 0
    }

    private fun simulateWater(mc: Minecraft, s: MovementSimState, mx: Double, my: Double, mz: Double) {
        val (mox, moy, moz) = applyWaterAccel(s, mx, my, mz)
        val r = collider.collide(mc, s.verifiedX, s.verifiedY, s.verifiedZ, mox, moy, moz)
        s.collidedHorizontally = s.collidedHorizontally || r.collidedHorizontally
        s.collidedVertically   = s.collidedVertically   || r.collidedVertically
        s.onGround = r.onGround
        s.predictedMotionX = r.motionX
        s.predictedMotionY = r.motionY
        s.predictedMotionZ = r.motionZ
    }

    private fun simulateLava(mc: Minecraft, s: MovementSimState, mx: Double, my: Double, mz: Double) {
        val (mox, moy, moz) = applyWaterAccel(s, mx, my, mz)
        val r = collider.collide(mc, s.verifiedX, s.verifiedY, s.verifiedZ, mox, moy * 0.5, moz)
        s.onGround = r.onGround
        s.predictedMotionX = r.motionX
        s.predictedMotionY = r.motionY
        s.predictedMotionZ = r.motionZ
    }

    private fun applyWaterAccel(s: MovementSimState, mx: Double, my: Double, mz: Double): DoubleArray {
        var fwd = s.forwardKey * 0.98f
        var str = s.strafeKey * 0.98f
        if (s.sneakKey) { fwd *= 0.3f; str *= 0.3f }
        var f = str * str + fwd * fwd
        if (f >= 1e-4f) {
            val friction = 0.02f
            f = friction / maxOf(1f, sqrt(f))
            str *= f
            fwd *= f
        }
        val newMx = mx + str * s.yawCos - fwd * s.yawSin
        val newMy = my + (if (s.jumpKey) 0.04 else 0.0) - (if (s.sneakKey) 0.04 else 0.0)
        val newMz = mz + fwd * s.yawCos + str * s.yawSin
        return doubleArrayOf(newMx, newMy, newMz)
    }

    private fun simulateLadder(mc: Minecraft, s: MovementSimState, mx: Double, my: Double, mz: Double) {
        val acc = accelerateGroundAir(mc, s, mx, my, mz)
        acc[0] = acc[0].coerceIn(-0.15, 0.15)
        acc[2] = acc[2].coerceIn(-0.15, 0.15)
        when {
            s.jumpKey  -> acc[1] = s.jumpMotion.toDouble()
            s.sneakKey -> acc[1] = -0.15
        }
        val r = collider.collide(mc, s.verifiedX, s.verifiedY, s.verifiedZ, acc[0], acc[1], acc[2])
        s.onGround = r.onGround
        s.predictedMotionX = r.motionX
        s.predictedMotionY = r.motionY
        s.predictedMotionZ = r.motionZ
    }

    private fun accelerateGroundAir(mc: Minecraft, s: MovementSimState, mx: Double, my: Double, mz: Double): DoubleArray {
        var fwd = s.forwardKey * 0.98f
        var str = s.strafeKey * 0.98f
        if (s.sneakKey)  { fwd *= 0.3f; str *= 0.3f }
        if (s.handActive) { fwd *= 0.2f; str *= 0.2f }
        var f = str * str + fwd * fwd
        if (f >= 1e-4f) {
            val friction = MovementFriction.resolveFrictionAt(
                mc, s.lastOnGround, s.sprintingAllowed, s.verifiedX, s.verifiedY, s.verifiedZ
            )
            f = friction / maxOf(1f, sqrt(f))
            str *= f
            fwd *= f
        }
        val newMx = mx + str * s.yawCos - fwd * s.yawSin
        val newMz = mz + fwd * s.yawCos + str * s.yawSin
        return doubleArrayOf(newMx, my, newMz)
    }

    private fun relink(mc: Minecraft, s: MovementSimState, mxIn: Double, myIn: Double, mzIn: Double): DoubleArray {
        if (s.inWeb) return doubleArrayOf(0.0, 0.0, 0.0)

        var mx = mxIn
        var my = myIn
        var mz = mzIn
        val inputMy = my

        if (my >= 0) {
            val r = collider.collide(mc, s.verifiedX, s.verifiedY, s.verifiedZ, mx, my, mz)
            s.collidedHorizontally = s.collidedHorizontally || r.collidedHorizontally
            s.collidedVertically   = s.collidedVertically   || r.collidedVertically
            s.onGround = r.onGround
            return doubleArrayOf(r.motionX, r.motionY, r.motionZ)
        }

        var posX = s.verifiedX
        var posY = s.verifiedY
        var posZ = s.verifiedZ
        val slip = if (s.lastOnGround)
            MovementFriction.groundSlipperinessForDecay(mc, posX, posY, posZ)
        else
            AIR_SLIP

        for (i in 0..MAX_RELINK) {
            val r = collider.collide(mc, posX, posY, posZ, mx, my, mz)
            s.collidedHorizontally = s.collidedHorizontally || r.collidedHorizontally
            s.collidedVertically   = s.collidedVertically   || r.collidedVertically

            val nx = posX + r.motionX
            val ny = posY + r.motionY
            val nz = posZ + r.motionZ
            val dx = nx - s.verifiedX
            val dy = ny - s.verifiedY
            val dz = nz - s.verifiedZ
            val flying = VanillaPlayerCollider.flyingDisplacement(dx, dy, dz)

            val jumpLessThanExpected = r.motionY < s.jumpMotion
            val jump = r.onGround
                    && abs((r.motionY + s.jumpMotion) - inputMy) < 0.00001
                    && jumpLessThanExpected

            when {
                !flying && !jump -> {
                    s.physicsPacketRelinkFlyVL = 0
                    mx = r.motionX; my = r.motionY; mz = r.motionZ
                    s.onGround = r.onGround
                    break
                }
                jump && VanillaPlayerCollider.flyingDisplacement(dx * 0.05, 0.0, dz * 0.05) -> {
                    s.physicsPacketRelinkFlyVL = 0
                    mx = r.motionX; my = s.jumpMotion.toDouble(); mz = r.motionZ
                    s.onGround = true
                    break
                }
                r.onGround && my < 0 && r.motionY <= 0 -> {
                    s.physicsPacketRelinkFlyVL = 0
                    mx = r.motionX; my = 0.0; mz = r.motionZ
                    s.onGround = true
                    break
                }
            }

            val allowedPackets = if (sqrt(mx * mx + mz * mz) < 0.03) 3 else 1
            if (flying && s.physicsPacketRelinkFlyVL++ > allowedPackets) {
                mx = r.motionX; my = r.motionY; mz = r.motionZ
                s.onGround = r.onGround
                break
            }

            mx = r.motionX; my = r.motionY; mz = r.motionZ
            posX = nx; posY = ny; posZ = nz
            mx *= slip
            my = (my - GRAVITY) * Y_MULT
            mz *= slip
            if (abs(mx) < s.resetMotion) mx = 0.0
            if (abs(my) < s.resetMotion) my = 0.0
            if (abs(mz) < s.resetMotion) mz = 0.0
        }
        return doubleArrayOf(mx, my, mz)
    }

    /** One-tick horizontal accel for key-search (no relink). */
    fun simulateOneTick(s: MovementSimState, forwardKey: Int, strafeKey: Int): DoubleArray {
        val mc = Minecraft.getMinecraft() ?: return doubleArrayOf(s.baseMotionX, s.baseMotionY, s.baseMotionZ)
        if (mc.player == null) return doubleArrayOf(s.baseMotionX, s.baseMotionY, s.baseMotionZ)

        var fwd = forwardKey * 0.98f
        var str = strafeKey * 0.98f
        if (s.sneakKey)  { fwd *= 0.3f; str *= 0.3f }
        if (s.handActive) { fwd *= 0.2f; str *= 0.2f }
        var f = str * str + fwd * fwd
        if (f >= 1e-4f) {
            val friction = MovementFriction.resolveFrictionAt(
                mc, s.lastOnGround, s.sprintingAllowed, s.verifiedX, s.verifiedY, s.verifiedZ
            )
            f = friction / maxOf(1f, sqrt(f))
            str *= f
            fwd *= f
        }
        val mx = s.baseMotionX + str * s.yawCos - fwd * s.yawSin
        val mz = s.baseMotionZ + fwd * s.yawCos + str * s.yawSin
        return doubleArrayOf(mx, s.baseMotionY, mz)
    }
}