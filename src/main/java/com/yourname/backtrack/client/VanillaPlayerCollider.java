package com.yourname.backtrack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

import java.util.List;

/**
 * Vanilla-order collision (Y, X, Z) using world collision boxes.
 */
public final class VanillaPlayerCollider {
    private static final double WIDTH = 0.6, HEIGHT = 1.8;

    public static final class CollideResult {
        public final double motionX, motionY, motionZ;
        public final boolean onGround, collidedHorizontally, collidedVertically;

        CollideResult(double mx, double my, double mz, boolean ground, boolean collH, boolean collV) {
            motionX = mx;
            motionY = my;
            motionZ = mz;
            onGround = ground;
            collidedHorizontally = collH;
            collidedVertically = collV;
        }
    }

    public CollideResult collide(Minecraft mc, double posX, double posY, double posZ,
                                 double motionX, double motionY, double motionZ) {
        World world = mc.world;
        if (world == null) {
            return new CollideResult(motionX, motionY, motionZ, false, false, false);
        }

        AxisAlignedBB entityBB = new AxisAlignedBB(
                posX - WIDTH / 2, posY, posZ - WIDTH / 2,
                posX + WIDTH / 2, posY + HEIGHT, posZ + WIDTH / 2);

        AxisAlignedBB expanded = entityBB.expand(motionX, motionY, motionZ);
        List<AxisAlignedBB> collidingBoxes = world.getCollisionBoxes(null, expanded);

        double resultX = motionX, resultY = motionY, resultZ = motionZ;
        boolean collidedH = false, collidedV = false;

        for (AxisAlignedBB bb : collidingBoxes) {
            resultY = bb.calculateYOffset(entityBB, resultY);
        }
        entityBB = entityBB.offset(0, resultY, 0);
        if (resultY != motionY) collidedV = true;

        for (AxisAlignedBB bb : collidingBoxes) {
            resultX = bb.calculateXOffset(entityBB, resultX);
        }
        entityBB = entityBB.offset(resultX, 0, 0);
        if (resultX != motionX) collidedH = true;

        for (AxisAlignedBB bb : collidingBoxes) {
            resultZ = bb.calculateZOffset(entityBB, resultZ);
        }
        if (resultZ != motionZ) collidedH = true;

        boolean ground = collidedV && motionY < 0 && resultY <= 0;
        return new CollideResult(resultX, resultY, resultZ, ground, collidedH, collidedV);
    }

    public boolean isOnGround(Minecraft mc, double posX, double posY, double posZ) {
        CollideResult r = collide(mc, posX, posY, posZ, 0, -0.500001, 0);
        return r.motionY != -0.500001;
    }

    public static boolean flyingDisplacement(double dx, double dy, double dz) {
        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= MovementSimState.FLYING_UNCERTAINTY_RADIUS;
    }
}
