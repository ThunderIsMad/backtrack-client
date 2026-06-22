package com.yourname.backtrack.client

import net.minecraft.block.BlockLadder
import net.minecraft.block.BlockVine
import net.minecraft.client.Minecraft
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.init.Blocks
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper

/**
 * Captures the full player input and environment state into [MovementSimState]
 * at the start of each tick, including module-overridden keys.
 */
object MovementInputCapture {

    fun capture(s: MovementSimState, mc: Minecraft) {
        if (mc.player == null) return

        // ── Raw input ───────────────────────────────────────────────
        s.forwardKey   = sign(mc.player.moveForward)
        s.strafeKey    = sign(mc.player.moveStrafing)
        s.jumpKey      = mc.gameSettings.keyBindJump.isKeyDown
        s.sneakKey     = mc.player.isSneaking
        s.sprintKey    = mc.player.isSprinting
        s.sprinting    = mc.player.isSprinting
        s.handActive   = mc.player.isHandActive
        s.rotationYaw  = mc.player.rotationYaw

        overrideModuleKeys(s, mc)

        // ── Sprint eligibility ──────────────────────────────────────
        s.sprintingAllowed = !s.sneakKey
                && mc.player.foodStats.foodLevel > 6
                && mc.currentScreen == null
                && !mc.player.isInWater

        // ── Environment flags ───────────────────────────────────────
        s.inWater     = mc.player.isInWater
        s.inWeb       = isInWeb(mc)
        s.inLava      = mc.player.isInLava
        s.onClimbable = isOnClimbable(mc)

        // ── Rotation trig ───────────────────────────────────────────
        val rad = s.rotationYaw * 0.017453292f
        s.yawSin = MathHelper.sin(rad)
        s.yawCos = MathHelper.cos(rad)

        // ── Ground status ───────────────────────────────────────────
        s.onGround = VanillaPlayerCollider.isOnGround(mc, s.verifiedX, s.verifiedY, s.verifiedZ)

        // ── Movement attributes ─────────────────────────────────────
        s.aiMoveSpeed = MovementEffects.applySpeedEffect(
            mc.player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).attributeValue.toFloat(), mc
        )
        s.jumpMotion = MovementEffects.applyJumpBoost(MovementFriction.jumpMotion(mc), mc)
        s.blockSlipperiness = MovementFriction.groundSlipperinessForDecay(
            mc, s.verifiedX, s.verifiedY, s.verifiedZ
        )

        s.tickEnvironmentCounters()
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * Module keys (e.g. Velocity Reverse) can programmatically press
     * movement keys.  Respect those overrides, fall back to raw input.
     */
    private fun overrideModuleKeys(s: MovementSimState, mc: Minecraft) {
        // Forward / back
        s.forwardKey = when {
            mc.gameSettings.keyBindForward.isKeyDown ->  1
            mc.gameSettings.keyBindBack.isKeyDown    -> -1
            else -> sign(mc.player.moveForward)
        }

        // Strafe left / right
        s.strafeKey = when {
            mc.gameSettings.keyBindLeft.isKeyDown  ->  1
            mc.gameSettings.keyBindRight.isKeyDown -> -1
            else -> sign(mc.player.moveStrafing)
        }

        if (mc.player.isSprinting) s.sprintKey = true
    }

    /** True if any block overlapping the player is a ladder or vine. */
    private fun isOnClimbable(mc: Minecraft): Boolean {
        val player = mc.player ?: return false
        val world  = mc.world  ?: return false
        val bb   = player.entityBoundingBox
        val min  = BlockPos(bb.minX, bb.minY, bb.minZ)
        val max  = BlockPos(bb.maxX, bb.maxY, bb.maxZ)
        for (x in min.x..max.x)
            for (y in min.y..max.y)
                for (z in min.z..max.z) {
                    val block = world.getBlockState(BlockPos(x, y, z)).block
                    if (block is BlockLadder || block is BlockVine) return true
                }
        return false
    }

    /** True if any block overlapping the player is a cobweb. */
    private fun isInWeb(mc: Minecraft): Boolean {
        val player = mc.player ?: return false
        val world  = mc.world  ?: return false
        val bb   = player.entityBoundingBox
        val min  = BlockPos(bb.minX, bb.minY, bb.minZ)
        val max  = BlockPos(bb.maxX, bb.maxY, bb.maxZ)
        for (x in min.x..max.x)
            for (y in min.y..max.y)
                for (z in min.z..max.z)
                    if (world.getBlockState(BlockPos(x, y, z)).block == Blocks.WEB) return true
        return false
    }

    private fun sign(f: Float): Int = when {
        f > 0  ->  1
        f < 0  -> -1
        else   ->  0
    }
}