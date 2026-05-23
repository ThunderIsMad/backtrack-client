package com.yourname.backtrack.client;

import net.minecraft.client.Minecraft;

public final class MovementPhysicsEngine {

    private static final double GRAVITY = 0.08, Y_MULT = 0.98, AIR_SLIP = 0.91;
    private static final int MAX_RELINK = 2;
    private final VanillaPlayerCollider collider = new VanillaPlayerCollider();

    public void simulate(MovementSimState s) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        double mx = s.baseMotionX, my = s.baseMotionY, mz = s.baseMotionZ;

        if (s.reduceTicks > 0) {
            for (int i = 0; i < s.reduceTicks; i++) {
                mx *= 0.6;
                mz *= 0.6;
            }
            if (Math.abs(mx) < s.resetMotion) mx = 0;
            if (Math.abs(mz) < s.resetMotion) mz = 0;
        }

        if (s.inWater) {
            simulateWater(mc, s, mx, my, mz);
        } else if (s.inLava) {
            simulateLava(mc, s, mx, my, mz);
        } else if (s.onClimbable) {
            simulateLadder(mc, s, mx, my, mz);
        } else {
            double[] acc = accelerateGroundAir(mc, s, mx, my, mz);
            if (s.jumpKey && (s.onGround || s.lastOnGround)) {
                acc[1] = s.jumpMotion;
                if (s.sprintingAllowed && s.sprintKey) {
                    acc[0] -= s.yawSin * 0.2;
                    acc[2] += s.yawCos * 0.2;
                }
            }
            double[] out = relink(mc, s, acc[0], acc[1], acc[2]);
            s.predictedMotionX = out[0];
            s.predictedMotionY = out[1];
            s.predictedMotionZ = out[2];
        }

        // Entity push: applied after all motion is resolved, same as vanilla applyEntityCollision
        if (s.entityPushX != 0 || s.entityPushZ != 0) {
            s.predictedMotionX += s.entityPushX;
            s.predictedMotionZ += s.entityPushZ;
        }
    }

    private void simulateWater(Minecraft mc, MovementSimState s, double mx, double my, double mz) {
        double[] motion = applyWaterAccel(s, mx, my, mz);
        VanillaPlayerCollider.CollideResult r = collider.collide(mc, s.verifiedX, s.verifiedY, s.verifiedZ,
                motion[0], motion[1], motion[2]);
        s.collidedHorizontally |= r.collidedHorizontally;
        s.collidedVertically |= r.collidedVertically;
        s.onGround = r.onGround;
        s.predictedMotionX = r.motionX;
        s.predictedMotionY = r.motionY;
        s.predictedMotionZ = r.motionZ;
    }

    private void simulateLava(Minecraft mc, MovementSimState s, double mx, double my, double mz) {
        double[] motion = applyWaterAccel(s, mx, my, mz);
        VanillaPlayerCollider.CollideResult r = collider.collide(mc, s.verifiedX, s.verifiedY, s.verifiedZ,
                motion[0], motion[1] * 0.5, motion[2]);
        s.onGround = r.onGround;
        s.predictedMotionX = r.motionX;
        s.predictedMotionY = r.motionY;
        s.predictedMotionZ = r.motionZ;
    }

    private double[] applyWaterAccel(MovementSimState s, double mx, double my, double mz) {
        float fwd = s.forwardKey * 0.98f, str = s.strafeKey * 0.98f;
        if (s.sneakKey) { fwd *= 0.3f; str *= 0.3f; }
        float f = str * str + fwd * fwd;
        if (f >= 1e-4f) {
            float friction = 0.02f;
            f = friction / Math.max(1f, (float) Math.sqrt(f));
            str *= f;
            fwd *= f;
            mx += str * s.yawCos - fwd * s.yawSin;
            mz += fwd * s.yawCos + str * s.yawSin;
        }
        if (s.jumpKey) my += 0.04;
        if (s.sneakKey) my -= 0.04;
        return new double[]{mx, my, mz};
    }

    private void simulateLadder(Minecraft mc, MovementSimState s, double mx, double my, double mz) {
        double[] acc = accelerateGroundAir(mc, s, mx, my, mz);
        acc[0] = Math.max(-0.15, Math.min(0.15, acc[0]));
        acc[2] = Math.max(-0.15, Math.min(0.15, acc[2]));
        if (s.jumpKey) acc[1] = s.jumpMotion;
        else if (s.sneakKey) acc[1] = -0.15;
        VanillaPlayerCollider.CollideResult r = collider.collide(mc, s.verifiedX, s.verifiedY, s.verifiedZ,
                acc[0], acc[1], acc[2]);
        s.onGround = r.onGround;
        s.predictedMotionX = r.motionX;
        s.predictedMotionY = r.motionY;
        s.predictedMotionZ = r.motionZ;
    }

    private double[] accelerateGroundAir(Minecraft mc, MovementSimState s, double mx, double my, double mz) {
        float fwd = s.forwardKey * 0.98f, str = s.strafeKey * 0.98f;
        if (s.sneakKey) { fwd *= 0.3f; str *= 0.3f; }
        if (s.handActive) { fwd *= 0.2f; str *= 0.2f; }
        float friction = MovementFriction.resolveFrictionAt(mc, s.lastOnGround, s.sprintingAllowed,
                s.verifiedX, s.verifiedY, s.verifiedZ);
        float f = str * str + fwd * fwd;
        if (f >= 1e-4f) {
            f = friction / Math.max(1f, (float) Math.sqrt(f));
            str *= f;
            fwd *= f;
            mx += str * s.yawCos - fwd * s.yawSin;
            mz += fwd * s.yawCos + str * s.yawSin;
        }
        return new double[]{mx, my, mz};
    }

    private double[] relink(Minecraft mc, MovementSimState s, double mx, double my, double mz) {
        if (s.inWeb) {
            return new double[]{0, 0, 0};
        }
        if (my >= 0) {
            VanillaPlayerCollider.CollideResult r = collider.collide(mc, s.verifiedX, s.verifiedY, s.verifiedZ, mx, my, mz);
            s.collidedHorizontally |= r.collidedHorizontally;
            s.collidedVertically |= r.collidedVertically;
            s.onGround = r.onGround;
            return new double[]{r.motionX, r.motionY, r.motionZ};
        }

        double posX = s.verifiedX, posY = s.verifiedY, posZ = s.verifiedZ;
        double slip = MovementFriction.groundSlipperinessForDecay(mc, posX, posY, posZ);
        double inputMy = my;

        for (int i = 0; i <= MAX_RELINK; i++) {
            VanillaPlayerCollider.CollideResult r = collider.collide(mc, posX, posY, posZ, mx, my, mz);
            s.collidedHorizontally |= r.collidedHorizontally;
            s.collidedVertically |= r.collidedVertically;

            double nx = posX + r.motionX, ny = posY + r.motionY, nz = posZ + r.motionZ;
            double dx = nx - s.verifiedX, dy = ny - s.verifiedY, dz = nz - s.verifiedZ;
            boolean flying = VanillaPlayerCollider.flyingDisplacement(dx, dy, dz);

            boolean jumpLessThanExpected = r.motionY < s.jumpMotion;
            boolean jump = r.onGround
                    && Math.abs((r.motionY + s.jumpMotion) - inputMy) < 0.00001
                    && jumpLessThanExpected;

            if (!flying && !jump) {
                s.physicsPacketRelinkFlyVL = 0;
                mx = r.motionX;
                my = r.motionY;
                mz = r.motionZ;
                s.onGround = r.onGround;
                break;
            }
            if (jump && VanillaPlayerCollider.flyingDisplacement(dx * 0.05, 0, dz * 0.05)) {
                s.physicsPacketRelinkFlyVL = 0;
                mx = r.motionX;
                my = s.jumpMotion;
                mz = r.motionZ;
                s.onGround = true;
                break;
            }
            if (r.onGround && my < 0 && r.motionY <= 0) {
                s.physicsPacketRelinkFlyVL = 0;
                mx = r.motionX;
                my = 0;
                mz = r.motionZ;
                s.onGround = true;
                break;
            }

            int allowedPackets = Math.sqrt(mx * mx + mz * mz) < 0.03 ? 3 : 1;
            if (flying && s.physicsPacketRelinkFlyVL++ > allowedPackets) {
                mx = r.motionX;
                my = r.motionY;
                mz = r.motionZ;
                s.onGround = r.onGround;
                break;
            }

            mx = r.motionX;
            my = r.motionY;
            mz = r.motionZ;
            posX = nx;
            posY = ny;
            posZ = nz;
            mx *= slip;
            my = (my - GRAVITY) * Y_MULT;
            mz *= slip;
            if (Math.abs(mx) < s.resetMotion) mx = 0;
            if (Math.abs(my) < s.resetMotion) my = 0;
            if (Math.abs(mz) < s.resetMotion) mz = 0;
        }
        return new double[]{mx, my, mz};
    }

    /** One-tick horizontal accel for key-search (no relink). */
    public double[] simulateOneTick(MovementSimState s, int forwardKey, int strafeKey) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return new double[]{s.baseMotionX, s.baseMotionY, s.baseMotionZ};

        double mx = s.baseMotionX, my = s.baseMotionY, mz = s.baseMotionZ;
        float fwd = forwardKey * 0.98f, str = strafeKey * 0.98f;
        if (s.sneakKey) { fwd *= 0.3f; str *= 0.3f; }
        if (s.handActive) { fwd *= 0.2f; str *= 0.2f; }
        float friction = MovementFriction.resolveFrictionAt(mc, s.lastOnGround, s.sprintingAllowed,
                s.verifiedX, s.verifiedY, s.verifiedZ);
        float f = str * str + fwd * fwd;
        if (f >= 1e-4f) {
            f = friction / Math.max(1f, (float) Math.sqrt(f));
            str *= f;
            fwd *= f;
            mx += str * s.yawCos - fwd * s.yawSin;
            mz += fwd * s.yawCos + str * s.yawSin;
        }
        return new double[]{mx, my, mz};
    }
}
