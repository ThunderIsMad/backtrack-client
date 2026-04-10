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

public class JumpResetModule extends Module {

    private final NumberSetting cooldownTicks = new NumberSetting("Cooldown", 14, 10, 20, 1);
    private final BooleanSetting debug = new BooleanSetting("Debug", false);

    // State from the previous tick
    private int     prevHurtTime  = 0;
    private double  prevMotionY   = 0.0;
    private boolean prevOnGround  = false;

    private int jumpCooldown = 0;

    public JumpResetModule() {
        super("JumpReset", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(cooldownTicks, debug);
        addHudSettings();
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    private void resetState() {
        prevHurtTime  = 0;
        prevMotionY   = 0.0;
        prevOnGround  = false;
        jumpCooldown  = 0;
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

        // GUARD 1: hurtTime is actively counting down this tick
        boolean hurtTimeDecreased = prevHurtTime > 0
                && curHurtTime == prevHurtTime - 1;

        // GUARD 2: arc is nearly complete (hurtTime <= 2)
        boolean arcNearlyDone = curHurtTime > 0 && curHurtTime <= 2;

        // GUARD 3: player just landed THIS tick
        // prevMotionY < -0.01 = was falling; curMotionY > -0.01 = collision zeroed it
        boolean justLanded = prevMotionY < -0.01
                && curMotionY > -0.01
                && (curOnGround || collidedV);

        // GUARD 4: motionY zeroed by ground collision this tick
        boolean motionYZero = Math.abs(curMotionY) < 0.005;

        // GUARD 5: safe full-cube terrain (stairs/slabs/fences cause desync)
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
                        + " | curMotionY=" + String.format("%.4f", curMotionY)
                        + " | cooldown=" + jumpCooldown);
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
     * Samples 0.01 below feet (not 0.2001 — that was sampling a block too low).
     * Rejects non-full-cube blocks that cause isOnGround() desync with Intave.
     */
    private boolean isStandingOnSafeTerrain() {
        if (mc.world == null || mc.player == null) return false;

        BlockPos below = new BlockPos(
                mc.player.posX,
                mc.player.posY - 0.01,
                mc.player.posZ
        );

        IBlockState state = mc.world.getBlockState(below);
        Block block = state.getBlock();

        if (block instanceof BlockStairs)    return false;
        if (block instanceof BlockSlab)      return false;
        if (block instanceof BlockFence)     return false;
        if (block instanceof BlockFenceGate) return false;
        if (block instanceof BlockTrapDoor)  return false;

        try {
            AxisAlignedBB box = state.getCollisionBoundingBox(mc.world, below);
            return box != null && box.maxY >= 0.999;
        } catch (Exception e) {
            return false;
        }
    }
}
