package com.yourname.backtrack.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class MovementFriction {
    private static final float FRICTION_MULTIPLIER = 0.16277137f;

    private MovementFriction() {}

    /**
     * Returns the slipperiness of the block below the player's feet,
     * multiplied by the air slipperiness constant (0.91) for consistency
     * with vanilla Minecraft physics.
     */
    public static float blockSlipperiness(World world, BlockPos below) {
        if (world == null || below == null) return 0.6f * 0.91f;
        Block block = world.getBlockState(below).getBlock();
        if (block == Blocks.AIR) return 0.6f * 0.91f;
        if (block == Blocks.SOUL_SAND) return 0.4f * 0.91f;
        if (block == Blocks.ICE || block == Blocks.PACKED_ICE) return 0.98f * 0.91f;
        return block.slipperiness * 0.91f;
    }

    public static float resolveFriction(Minecraft mc, boolean lastOnGround, boolean sprintingAllowed) {
        if (mc.player == null) return 0.02f;
        return resolveFrictionAt(mc, lastOnGround, sprintingAllowed,
                mc.player.posX, mc.player.posY, mc.player.posZ);
    }

    public static float resolveFrictionAt(Minecraft mc, boolean lastOnGround, boolean sprintingAllowed,
                                          double posX, double posY, double posZ) {
        if (mc.player == null) return 0.02f;
        float aiMoveSpeed = MovementEffects.applySpeedEffect(
                (float) mc.player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue(), mc);
        if (lastOnGround) {
            BlockPos below = new BlockPos(posX, posY - 1.0, posZ);
            float slip = blockSlipperiness(mc.world, below);
            float factor = FRICTION_MULTIPLIER / (slip * slip * slip);
            float speed = aiMoveSpeed * factor;
            if (sprintingAllowed) {
                speed += speed * 0.3f;
            }
            return speed;
        }
        float jumpFactor = 0.02f;
        if (sprintingAllowed) {
            jumpFactor += 0.02f * 0.3f;
        }
        return jumpFactor;
    }

    public static float groundSlipperinessForDecay(Minecraft mc) {
        if (mc.player == null) return 0.91f;
        return groundSlipperinessForDecay(mc, mc.player.posX, mc.player.posY, mc.player.posZ);
    }

    public static float groundSlipperinessForDecay(Minecraft mc, double posX, double posY, double posZ) {
        if (mc.player == null || mc.world == null) return 0.91f;
        BlockPos below = new BlockPos(posX, posY - 1.0, posZ);
        return blockSlipperiness(mc.world, below);
    }

    public static float jumpMotion(Minecraft mc) {
        if (mc.player == null) return 0.42f;
        float jump = 0.42f;
        if (mc.player.isPotionActive(net.minecraft.init.MobEffects.JUMP_BOOST)) {
            int amp = mc.player.getActivePotionEffect(net.minecraft.init.MobEffects.JUMP_BOOST).getAmplifier();
            jump += (amp + 1) * 0.1f;
        }
        return jump;
    }
}
