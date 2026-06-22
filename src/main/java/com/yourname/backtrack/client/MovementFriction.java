package com.yourname.backtrack.client

import net.minecraft.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.entity.SharedMonsterAttributes
import net.minecraft.init.Blocks
import net.minecraft.init.MobEffects
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Vanilla-accurate friction and slipperiness helpers.
 * Block-slipperiness values multiplied by 0.91 for air-drag consistency
 * matching Intave's `currentSlipperiness()`.
 */
object MovementFriction {

    private const val FRICTION_MULTIPLIER = 0.16277137f

    /**
     * Returns the slipperiness of the block directly below the player's feet,
     * pre-multiplied by 0.91 (air slipperiness) for vanilla parity.
     */
    fun blockSlipperiness(world: World?, below: BlockPos?): Float {
        if (world == null || below == null) return 0.6f * 0.91f
        val block = world.getBlockState(below).block
        return when (block) {
            Blocks.AIR        -> 0.6f
            Blocks.SOUL_SAND  -> 0.4f
            Blocks.ICE,
            Blocks.PACKED_ICE -> 0.98f
            else              -> block.slipperiness
        } * 0.91f
    }

    /**
     * Resolves the friction acceleration factor for the current tick.
     * On ground uses the block below; in air uses a jump-factor baseline.
     */
    fun resolveFrictionAt(
        mc: Minecraft,
        lastOnGround: Boolean,
        sprintingAllowed: Boolean,
        posX: Double, posY: Double, posZ: Double
    ): Float {
        val player = mc.player ?: return 0.02f
        val aiMoveSpeed = MovementEffects.applySpeedEffect(
            player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).attributeValue.toFloat(), mc
        )
        return if (lastOnGround) {
            val below  = BlockPos(posX, posY - 1.0, posZ)
            val slip   = blockSlipperiness(mc.world, below)
            val factor = FRICTION_MULTIPLIER / (slip * slip * slip)
            val speed  = aiMoveSpeed * factor
            if (sprintingAllowed) speed + speed * 0.3f else speed
        } else {
            var jumpFactor = 0.02f
            if (sprintingAllowed) jumpFactor += 0.02f * 0.3f
            jumpFactor
        }
    }

    /**
     * Slipperiness of the block below (posX, posY, posZ) for motion-decay
     * calculations.  Falls back to 0.91 when the world is unavailable.
     */
    fun groundSlipperinessForDecay(mc: Minecraft, posX: Double, posY: Double, posZ: Double): Float {
        if (mc.player == null || mc.world == null) return 0.91f
        return blockSlipperiness(mc.world, BlockPos(posX, posY - 1.0, posZ))
    }

    /**
     * Returns the base jump motion, accounting for Jump Boost potion effect.
     */
    fun jumpMotion(mc: Minecraft): Float {
        val player = mc.player ?: return 0.42f
        var jump = 0.42f
        val jumpEffect = player.getActivePotionEffect(MobEffects.JUMP_BOOST)
        if (jumpEffect != null) {
            jump += (jumpEffect.amplifier + 1) * 0.1f
        }
        return jump
    }
}
