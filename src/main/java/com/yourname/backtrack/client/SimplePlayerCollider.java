package com.yourname.backtrack.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class SimplePlayerCollider {
    private static final double WIDTH = 0.6, HEIGHT = 1.8;

    public double[] collide(double posX, double posY, double posZ,
                            double motionX, double motionY, double motionZ,
                            boolean[] outFlags) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null) return new double[]{motionX, motionY, motionZ};

        AxisAlignedBB bb = new AxisAlignedBB(
                posX - WIDTH/2, posY, posZ - WIDTH/2,
                posX + WIDTH/2, posY + HEIGHT, posZ + WIDTH/2);
        AxisAlignedBB exp = bb.expand(motionX, motionY, motionZ);

        int minX = (int)Math.floor(exp.minX), maxX = (int)Math.floor(exp.maxX);
        int minY = (int)Math.floor(exp.minY), maxY = (int)Math.floor(exp.maxY);
        int minZ = (int)Math.floor(exp.minZ), maxZ = (int)Math.floor(exp.maxZ);

        double rx = motionX, ry = motionY, rz = motionZ;
        boolean collH = false, collV = false;

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos bp = new BlockPos(x, y, z);
                    Block b = world.getBlockState(bp).getBlock();
                    if (b.isPassable(world, bp)) continue;
                    AxisAlignedBB bb2 = world.getBlockState(bp).getCollisionBoundingBox(world, bp);
                    if (bb2 == null) continue;

                    if (ry != 0) {
                        ry = bb2.calculateYOffset(bb, ry); bb = bb.offset(0, ry, 0);
                        if (ry != motionY) collV = true;
                    }
                    if (rx != 0) {
                        rx = bb2.calculateXOffset(bb, rx); bb = bb.offset(rx, 0, 0);
                        if (rx != motionX) collH = true;
                    }
                    if (rz != 0) {
                        rz = bb2.calculateZOffset(bb, rz); bb = bb.offset(0, 0, rz);
                        if (rz != motionZ) collH = true;
                    }
                }

        if (outFlags != null && outFlags.length >= 2) {
            outFlags[0] = collH;
            outFlags[1] = collV;
        }
        return new double[]{rx, ry, rz};
    }

    public boolean isOnGround(double posX, double posY, double posZ, Minecraft mc) {
        if (mc.world == null) return false;
        AxisAlignedBB feet = new AxisAlignedBB(
                posX - 0.3, posY - 0.01, posZ - 0.3,
                posX + 0.3, posY, posZ + 0.3);
        int minX = (int)Math.floor(feet.minX), maxX = (int)Math.floor(feet.maxX);
        int minZ = (int)Math.floor(feet.minZ), maxZ = (int)Math.floor(feet.maxZ);
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos bp = new BlockPos(x, (int)Math.floor(posY - 0.01), z);
                Block b = mc.world.getBlockState(bp).getBlock();
                if (!b.isPassable(mc.world, bp)) {
                    AxisAlignedBB bb = mc.world.getBlockState(bp).getCollisionBoundingBox(mc.world, bp);
                    if (bb != null && bb.intersects(feet)) return true;
                }
            }
        return false;
    }
}