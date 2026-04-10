package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFence;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.List;

public class JumpResetModule extends Module {

    private final NumberSetting cooldownTicks = new NumberSetting("Cooldown", 14, 10, 20, 1);
    private final BooleanSetting debug = new BooleanSetting("Debug", false);

    private double  prevMotionY  = 0.0;
    private boolean prevOnGround = false;
    private int     jumpCooldown = 0;

    public JumpResetModule() {
        super("JumpReset", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(cooldownTicks, debug);
        addHudSettings();
    }

    @Override
    public void onEnable() { resetState(); }

    @Override
    public void onDisable() { resetState(); }

    private void resetState() {
        prevMotionY  = 0.0;
        prevOnGround = false;
        jumpCooldown = 0;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        final int     curHurtTime = mc.player.hurtTime;
        final double  curMotionY  = mc.player.motionY;
        final boolean curOnGround = mc.player.onGround;
        final boolean collidedV   = mc.player.collidedVertically;

        if (jumpCooldown > 0) jumpCooldown--;

        // GUARD 1: player is actively hurt (works during combos too)
        boolean isHurt = curHurtTime > 0;

        // GUARD 2: player just landed this exact tick
        // prevMotionY was negative (falling), curMotionY zeroed by ground
        boolean justLanded = prevMotionY < -0.01
                && curMotionY > -0.01
                && (curOnGround || collidedV);

        // GUARD 3: motionY zeroed by ground collision
        boolean motionYZero = Math.abs(curMotionY) < 0.005;

        // GUARD 4: safe full-cube terrain
        boolean safeTerrain = isStandingOnSafeTerrain();

        boolean shouldJump = isHurt
                && justLanded
                && motionYZero
                && safeTerrain
                && jumpCooldown == 0;

        if (shouldJump) {
            mc.player.motionY    = 0.42;
            mc.player.isAirBorne = true;
            jumpCooldown = (int) cooldownTicks.getValue();

            if (debug.getValue()) {
                System.out.println("[JumpReset] JUMP FIRED"
                        + " | hurtTime=" + curHurtTime
                        + " | prevMotionY=" + String.format("%.4f", prevMotionY)
                        + " | curMotionY=" + String.format("%.4f", curMotionY));
            }
        } else if (debug.getValue() && curHurtTime > 0) {
            System.out.println("[JumpReset] TICK"
                    + " | hurtTime=" + curHurtTime
                    + " | onGround=" + curOnGround
                    + " | motionY=" + String.format("%.4f", curMotionY)
                    + " | prevMotionY=" + String.format("%.4f", prevMotionY)
                    + " | justLanded=" + justLanded
                    + " | safeTerrain=" + safeTerrain
                    + " | cooldown=" + jumpCooldown);
        }

        prevMotionY  = curMotionY;
        prevOnGround = curOnGround;
    }

    private boolean isStandingOnSafeTerrain() {
        if (mc.world == null || mc.player == null) return false;

        AxisAlignedBB footBox = mc.player.getEntityBoundingBox()
                .offset(0, -0.1, 0)
                .setMaxY(mc.player.getEntityBoundingBox().minY);

        List<AxisAlignedBB> blockBoxes = mc.world.getCollisionBoxes(mc.player, footBox);
        if (blockBoxes.isEmpty()) return false;

        int minX = (int) Math.floor(footBox.minX);
        int maxX = (int) Math.floor(footBox.maxX);
        int minZ = (int) Math.floor(footBox.minZ);
        int maxZ = (int) Math.floor(footBox.maxZ);
        int y    = (int) Math.floor(mc.player.getEntityBoundingBox().minY - 0.1);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                IBlockState state = mc.world.getBlockState(pos);
                Block block = state.getBlock();

                if (block instanceof BlockStairs)    return false;
                if (block instanceof BlockSlab)      return false;
                if (block instanceof BlockFence)     return false;
                if (block instanceof BlockFenceGate) return false;
                if (block instanceof BlockTrapDoor)  return false;

                try {
                    AxisAlignedBB box = state.getCollisionBoundingBox(mc.world, pos);
                    if (box == null || box.maxY < 0.999) return false;
                } catch (Exception e) {
                    return false;
                }
            }
        }

        return true;
    }
}
