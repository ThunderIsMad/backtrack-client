package com.yourname.backtrack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.math.MathHelper;

public final class ClientSimulator {

    public static final ClientSimulator INSTANCE = new ClientSimulator();
    public final MovementSimState s = new MovementSimState();

    private static final double SLIPPERINESS_AIR  = 0.91;
    private static final double GRAVITY           = 0.08;
    private static final double Y_MULTIPLIER      = 0.98;
    private static final int    RELINK_ITERATIONS = 3;

    private final SimplePlayerCollider collider = new SimplePlayerCollider();

    private ClientSimulator() {}

    // ---------- PUBLIC API ----------

    public void captureInput(Minecraft mc) {
        if (mc.player == null) return;

        s.forwardKey = mc.player.moveForward > 0 ? 1 : (mc.player.moveForward < 0 ? -1 : 0);
        s.strafeKey  = mc.player.moveStrafing > 0 ? 1 : (mc.player.moveStrafing < 0 ? -1 : 0);
        s.jumpKey    = mc.gameSettings.keyBindJump.isKeyDown();
        s.sneakKey   = mc.player.isSneaking();
        s.sprintKey  = mc.player.isSprinting();
        s.rotationYaw = mc.player.rotationYaw;
        s.onGround   = collider.isOnGround(s.verifiedX, s.verifiedY, s.verifiedZ, mc);
        s.handActive = mc.player.isHandActive();

        updateSprintAllowed(mc);
        updateAttributes(mc);

        float yawRad = s.rotationYaw * 0.017453292f;
        s.yawSin = MathHelper.sin(yawRad);
        s.yawCos = MathHelper.cos(yawRad);
    }

    public void simulate() {
        double simX = s.baseMotionX;
        double simY = s.baseMotionY;
        double simZ = s.baseMotionZ;

        // Attack reduce
        if (s.reduceTicks > 0) {
            for (int i = 0; i < s.reduceTicks; i++) {
                simX *= 0.6;
                simZ *= 0.6;
            }
            if (Math.abs(simX) < s.resetMotion) simX = 0;
            if (Math.abs(simZ) < s.resetMotion) simZ = 0;
        }

        double[] accelerated = applyInputAcceleration(simX, simY, simZ);

        if (s.jumpKey && s.onGround) {
            accelerated[1] = s.jumpMotion;
            if (s.sprintKey) {
                accelerated[0] -= s.yawSin * 0.2;
                accelerated[2] += s.yawCos * 0.2;
            }
        }

        double[] decayed = applyRelinkWithCollision(accelerated);

        s.predictedMotionX = decayed[0];
        s.predictedMotionY = decayed[1];
        s.predictedMotionZ = decayed[2];

        // Flying packet detection
        double motionDist = decayed[0]*decayed[0] + decayed[1]*decayed[1] + decayed[2]*decayed[2];
        if (motionDist < 0.0009) {
            s.pastFlyingPacketAccurate = 0;
        }
    }

    public void prepareNextTick() {
        double slipperiness = s.onGround ? s.blockSlipperiness : SLIPPERINESS_AIR;

        s.baseMotionX = s.predictedMotionX * slipperiness;
        s.baseMotionY = s.onGround ? 0.0 : (s.predictedMotionY - GRAVITY) * Y_MULTIPLIER;
        s.baseMotionZ = s.predictedMotionZ * slipperiness;

        if (Math.abs(s.baseMotionX) < s.resetMotion) s.baseMotionX = 0;
        if (Math.abs(s.baseMotionY) < s.resetMotion) s.baseMotionY = 0;
        if (Math.abs(s.baseMotionZ) < s.resetMotion) s.baseMotionZ = 0;

        s.lastX = s.verifiedX; s.lastY = s.verifiedY; s.lastZ = s.verifiedZ;
        s.verifiedX += s.predictedMotionX;
        s.verifiedY += s.predictedMotionY;
        s.verifiedZ += s.predictedMotionZ;

        s.lastOnGround = s.onGround;
        s.pastPlayerReduceAttackPhysics++;
        if (s.pastPlayerReduceAttackPhysics > 100) s.pastPlayerReduceAttackPhysics = 100;
        s.reduceTicks = 0;

        predictFlyingPacketBeforeVelocity();
        s.pastFlyingPacketAccurate++;
    }

    public void applyVelocity(double vx, double vy, double vz) {
        s.baseMotionXBeforeVelocity = s.baseMotionX;
        s.baseMotionYBeforeVelocity = s.baseMotionY;
        s.baseMotionZBeforeVelocity = s.baseMotionZ;
        s.baseMotionX = vx; s.baseMotionY = vy; s.baseMotionZ = vz;
        s.pastExternalVelocity = 0;
        s.pastVelocity = 0;
    }

    public void handleTeleport(double x, double y, double z) {
        s.verifiedX = x; s.verifiedY = y; s.verifiedZ = z;
        s.lastX = x; s.lastY = y; s.lastZ = z;
        s.baseMotionX = 0; s.baseMotionY = 0; s.baseMotionZ = 0;
        s.predictedMotionX = 0; s.predictedMotionY = 0; s.predictedMotionZ = 0;
        s.pastExternalVelocity = 100;
        s.pastVelocity = 100;
    }

    public void onOutgoingMovement() {
        s.pastExternalVelocity++;
        s.pastVelocity++;
        s.reduceTicks = 0;
    }

    public void onAttack() {
        s.pastPlayerReduceAttackPhysics = 0;
        if (s.reduceTicks < 3) s.reduceTicks++;
    }

    public void applyExplosion(double vx, double vy, double vz) {
        s.baseMotionX += vx;
        s.baseMotionY += vy;
        s.baseMotionZ += vz;
    }

    public void syncFromPlayer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            s.baseMotionX = mc.player.motionX;
            s.baseMotionY = mc.player.motionY;
            s.baseMotionZ = mc.player.motionZ;
        }
    }

    // ---------- GETTERS ----------

    public double getExpectedX()  { return s.predictedMotionX; }
    public double getExpectedY()  { return s.predictedMotionY; }
    public double getExpectedZ()  { return s.predictedMotionZ; }
    public double getExpectedMag() {
        double ex = s.predictedMotionX, ez = s.predictedMotionZ;
        return Math.sqrt(ex * ex + ez * ez);
    }
    public double getToleranceXZ() { computeTolerances(); return s.toleranceXZ; }
    public double getToleranceY()  { computeTolerances(); return s.toleranceY; }
    public int getPastExternalVelocity() { return s.pastExternalVelocity; }
    public int getPastVelocity()         { return s.pastVelocity; }
    public boolean isInVelocityWindow()  { return s.pastExternalVelocity < 10; }

    // ---------- INTERNALS ----------

    private void updateSprintAllowed(Minecraft mc) {
        s.sprintingAllowed = !s.sneakKey
                && mc.player.getFoodStats().getFoodLevel() > 6
                && mc.currentScreen == null;
    }

    private void updateAttributes(Minecraft mc) {
        s.aiMoveSpeed = (float) mc.player.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue();
        s.jumpMotion = 0.42f;
    }

    private double[] applyInputAcceleration(double mx, double my, double mz) {
        float forward = s.forwardKey * 0.98f;
        float strafe  = s.strafeKey * 0.98f;

        if (s.sneakKey) { forward *= 0.3f; strafe *= 0.3f; }
        if (s.handActive) { forward *= 0.2f; strafe *= 0.2f; }

        float friction = s.onGround ? s.blockSlipperiness : (float)SLIPPERINESS_AIR;
        float f = strafe * strafe + forward * forward;
        if (f >= 0.0001f) {
            f = friction / Math.max(1.0f, (float) Math.sqrt(f));
            strafe  *= f;
            forward *= f;
            mx += strafe * s.yawCos - forward * s.yawSin;
            mz += forward * s.yawCos + strafe * s.yawSin;
        }

        return new double[]{mx, my, mz};
    }

    private double[] applyRelinkWithCollision(double[] motion) {
        double mx = motion[0], my = motion[1], mz = motion[2];
        double posX = s.verifiedX, posY = s.verifiedY, posZ = s.verifiedZ;
        Minecraft mc = Minecraft.getMinecraft();

        for (int i = 0; i < RELINK_ITERATIONS; i++) {
            mx *= SLIPPERINESS_AIR;
            my = (my - GRAVITY) * Y_MULTIPLIER;
            mz *= SLIPPERINESS_AIR;

            if (Math.abs(mx) < s.resetMotion) mx = 0;
            if (Math.abs(my) < s.resetMotion) my = 0;
            if (Math.abs(mz) < s.resetMotion) mz = 0;

            boolean[] flags = new boolean[2];
            double[] resolved = collider.collide(posX, posY, posZ, mx, my, mz, flags);
            mx = resolved[0]; my = resolved[1]; mz = resolved[2];

            posX += mx; posY += my; posZ += mz;

            if (flags[1] && my <= 0) {
                s.onGround = true;
                s.collidedVertically = true;
                if (my < 0) my = 0;
            } else {
                s.collidedVertically = false;
            }
            s.collidedHorizontally = flags[0];
        }

        return new double[]{mx, my, mz};
    }

    private void predictFlyingPacketBeforeVelocity() {
        if (s.pastVelocity != 0) return;
        double mx = s.baseMotionXBeforeVelocity * 0.91;
        double my = (s.baseMotionYBeforeVelocity - 0.08) * 0.98;
        double mz = s.baseMotionZBeforeVelocity * 0.91;
        double dist = mx*mx + my*my + mz*mz;
        if (s.onGround && dist < 0.009) {
            s.physicsUnpredictableVelocityExpected = true;
            s.pastFlyingPacketAccurate = 0;
        }
    }

    private void computeTolerances() {
        double xz = 0.0007;
        double y  = 0.00001;

        if (s.pastPlayerReduceAttackPhysics <= 1) {
            xz = s.pastFlyingPacketAccurate <= 2 ? 0.03 : 0.015;
        }

        if (s.pastExternalVelocity == 0) {
            xz = 0.005; y = 0.005;
        } else if (s.pastExternalVelocity < 10) {
            xz = Math.max(xz, 0.015);
            y  = Math.max(y, 0.005);
        }

        if (s.collidedHorizontally) xz = Math.max(xz, 0.027);
        if (s.physicsUnpredictableVelocityExpected) xz = Math.max(xz, 0.1);

        s.toleranceXZ = xz;
        s.toleranceY  = y;
    }

    public void reset() { s.reset(); }
}