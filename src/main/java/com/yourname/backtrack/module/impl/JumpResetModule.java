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

    private int     prevHurtTime = 0;
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
        prevHurtTime = 0;
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

        // GUARD 1: hurtTime actively counting down (arc is live)
        boolean hurtTimeDecreased = prevHurtTime > 0
                && curHurtTime == prevHurtTime - 1;

        // GUARD 2: arc nearly complete
        boolean arcNearlyDone = curHurtTime > 0 && curHurtTime <= 2;

        // GUARD 3: player just landed this exact tick
        boolean justLanded = prevMotionY < -0.01
                && curMotionY > -0.01
                && (curOnGround || collidedV);

        // GUARD 4: motionY zeroed by ground collision
        boolean motionYZero = Math.abs(curMotionY) < 0.005;

        // GUARD 5: safe terrain — uses actual block collision boxes under the
        // player's AABB rather than a single-point Y sample, so it works
        // regardless of posY floating-point position after landing
        boolean safeTerrain = isStandingOnSafeTerrain();

        boolean shouldJump = hurtTimeDecreased
                && arcNearlyDone
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
                        + " | prevHurtTime=" + prevHurtTime
                        + " | prevMotionY=" + String.format("%.4f", prevMotionY)
                        + " | curMotionY=" + String.format("%.4f", curMotionY));
            }
        } else if (debug.getValue() && curHurtTime > 0) {
            System.out.println("[JumpReset] TICK"
                    + " | hurtTime=" + curHurtTime
                    + " | prevHurtTime=" + prevHurtTime
                    + " | onGround=" + curOnGround
                    + " | motionY=" + String.format("%.4f", curMotionY)
                    + " | prevMotionY=" + String.format("%.4f", prevMotionY)
                    + " | justLanded=" + justLanded
                    + " | safeTerrain=" + safeTerrain
                    + " | cooldown=" + jumpCooldown);
        }

        prevHurtTime = curHurtTime;
        prevMotionY  = curMotionY;
        prevOnGround = curOnGround;
    }

    /**
     * Scans all block positions that overlap the player's foot-level AABB
     * (a thin 0.1-block-tall slice at the player's feet). For each position,
     * rejects known non-full-cube blocks and validates the bbox maxY.
     * This approach is immune to posY floating-point sampling errors.
     */
    private boolean isStandingOnSafeTerrain() {
        if (mc.world == null || mc.player == null) return false;

        // Thin AABB slice at foot level: player base Y down to Y-0.1
        AxisAlignedBB footBox = mc.player.getEntityBoundingBox().expand(0, 0, 0)
                .offset(0, -0.1, 0)
                .setMaxY(mc.player.getEntityBoundingBox().minY);

        // Collect all block positions overlapping the foot slice
        List<AxisAlignedBB> blockBoxes = mc.world.getCollisionBoxes(mc.player, footBox);

        if (blockBoxes.isEmpty()) return false;

        // Check each block position under the player
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

                // Reject non-full-cube blocks explicitly
                if (block instanceof BlockStairs)    return false;
                if (block instanceof BlockSlab)      return false;
                if (block instanceof BlockFence)     return false;
                if (block instanceof BlockFenceGate) return false;
                if (block instanceof BlockTrapDoor)  return false;

                // Validate bounding box height
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
