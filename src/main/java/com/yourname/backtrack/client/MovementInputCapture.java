package com.yourname.backtrack.client;

import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockVine;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

public final class MovementInputCapture {

    private final VanillaPlayerCollider collider = new VanillaPlayerCollider();

    public void capture(MovementSimState s, Minecraft mc) {
        if (mc.player == null) return;

        s.forwardKey = sign(mc.player.moveForward);
        s.strafeKey = sign(mc.player.moveStrafing);
        s.jumpKey = mc.gameSettings.keyBindJump.isKeyDown();
        s.sneakKey = mc.player.isSneaking();
        s.sprintKey = mc.player.isSprinting();
        s.sprinting = mc.player.isSprinting();
        s.handActive = mc.player.isHandActive();
        s.rotationYaw = mc.player.rotationYaw;

        overrideModuleKeys(s, mc);

        s.sprintingAllowed = !s.sneakKey
                && mc.player.getFoodStats().getFoodLevel() > 6
                && mc.currentScreen == null
                && !mc.player.isInWater();

        s.inWater = mc.player.isInWater();
        s.inWeb = isInWeb(mc);
        s.inLava = mc.player.isInLava();
        s.onClimbable = isOnClimbable(mc);

        float rad = s.rotationYaw * 0.017453292f;
        s.yawSin = MathHelper.sin(rad);
        s.yawCos = MathHelper.cos(rad);

        s.onGround = collider.isOnGround(mc, s.verifiedX, s.verifiedY, s.verifiedZ);

        s.aiMoveSpeed = MovementEffects.applySpeedEffect(
                (float) mc.player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue(), mc);
        s.jumpMotion = MovementEffects.applyJumpBoost(MovementFriction.jumpMotion(mc), mc);
        s.blockSlipperiness = MovementFriction.groundSlipperinessForDecay(mc, s.verifiedX, s.verifiedY, s.verifiedZ);

        s.tickEnvironmentCounters();
    }

    private void overrideModuleKeys(MovementSimState s, Minecraft mc) {
        if (mc.gameSettings.keyBindForward.isKeyDown()) s.forwardKey = 1;
        else if (mc.gameSettings.keyBindBack.isKeyDown()) s.forwardKey = -1;
        else s.forwardKey = sign(mc.player.moveForward);

        if (mc.gameSettings.keyBindLeft.isKeyDown()) s.strafeKey = 1;
        else if (mc.gameSettings.keyBindRight.isKeyDown()) s.strafeKey = -1;
        else s.strafeKey = sign(mc.player.moveStrafing);

        if (mc.player.isSprinting()) s.sprintKey = true;
    }

    private boolean isOnClimbable(Minecraft mc) {
        if (mc.player == null || mc.world == null) return false;
        AxisAlignedBB bb = mc.player.getEntityBoundingBox();
        BlockPos min = new BlockPos(bb.minX, bb.minY, bb.minZ);
        BlockPos max = new BlockPos(bb.maxX, bb.maxY, bb.maxZ);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    IBlockState state = mc.world.getBlockState(new BlockPos(x, y, z));
                    if (state.getBlock() instanceof BlockLadder || state.getBlock() instanceof BlockVine) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean isInWeb(Minecraft mc) {
        if (mc.player == null) return false;
        AxisAlignedBB bb = mc.player.getEntityBoundingBox();
        BlockPos min = new BlockPos(bb.minX, bb.minY, bb.minZ);
        BlockPos max = new BlockPos(bb.maxX, bb.maxY, bb.maxZ);
        for (int x = min.getX(); x <= max.getX(); x++)
            for (int y = min.getY(); y <= max.getY(); y++)
                for (int z = min.getZ(); z <= max.getZ(); z++)
                    if (mc.world.getBlockState(new BlockPos(x, y, z)).getBlock() == Blocks.WEB)
                        return true;
        return false;
    }

    private int sign(float f) { return f > 0 ? 1 : (f < 0 ? -1 : 0); }
}
