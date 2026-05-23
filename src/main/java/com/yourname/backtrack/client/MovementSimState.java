package com.yourname.backtrack.client;

public class MovementSimState {
    public double verifiedX, verifiedY, verifiedZ;
    public double lastX, lastY, lastZ;
    public double baseMotionX, baseMotionY, baseMotionZ;
    public double baseMotionXBeforeVelocity, baseMotionYBeforeVelocity, baseMotionZBeforeVelocity;
    public double predictedMotionX, predictedMotionY, predictedMotionZ;
    public int pastExternalVelocity, pastVelocity;
    public int pastPlayerReduceAttackPhysics, reduceTicks;
    public int pastFlyingPacketAccurate;
    public int pastWaterMovement = 100;
    public int pastInWeb = 100;
    public int webTicks;
    public boolean onGround, lastOnGround;
    public boolean collidedHorizontally, collidedVertically;
    public boolean sprinting, sprintingAllowed, sneaking, handActive;
    public boolean physicsUnpredictableVelocityExpected, inWater, inWeb, inLava, onClimbable;
    public int forwardKey, strafeKey;
    public boolean jumpKey, sprintKey, sneakKey;
    public float rotationYaw, yawSin, yawCos;
    public float aiMoveSpeed, blockSlipperiness, jumpMotion, resetMotion;
    public double toleranceXZ, toleranceY;
    public double lastMotionX, lastMotionY, lastMotionZ;
    public double playerPosX, playerPosY, playerPosZ;
    public boolean positionInitialized;
    public static final double FLYING_UNCERTAINTY_RADIUS = 0.03;
    public int physicsPacketRelinkFlyVL;

    /** Accumulated entity-push delta for this tick (added after collision). */
    public double entityPushX, entityPushZ;

    public void beginTick() {
        physicsUnpredictableVelocityExpected = false;
        collidedHorizontally = false;
        collidedVertically = false;
        lastMotionX = predictedMotionX;
        lastMotionY = predictedMotionY;
        lastMotionZ = predictedMotionZ;
        entityPushX = 0;
        entityPushZ = 0;
    }

    public boolean isInVelocityWindow() { return pastExternalVelocity < 10; }

    public boolean receivedFlyingPacketIn(int ticks) {
        return pastFlyingPacketAccurate <= ticks;
    }

    public void tickEnvironmentCounters() {
        if (inWater) pastWaterMovement = 0;
        else if (pastWaterMovement < 100) pastWaterMovement++;

        if (inWeb) {
            pastInWeb = 0;
            webTicks++;
        } else {
            if (pastInWeb < 100) pastInWeb++;
            webTicks = 0;
        }
    }

    public void reset() {
        verifiedX = 0; verifiedY = 0; verifiedZ = 0;
        lastX = 0; lastY = 0; lastZ = 0;
        baseMotionX = 0; baseMotionY = 0; baseMotionZ = 0;
        baseMotionXBeforeVelocity = 0; baseMotionYBeforeVelocity = 0; baseMotionZBeforeVelocity = 0;
        predictedMotionX = 0; predictedMotionY = 0; predictedMotionZ = 0;
        pastExternalVelocity = 100; pastVelocity = 100;
        pastPlayerReduceAttackPhysics = 100; reduceTicks = 0;
        pastFlyingPacketAccurate = 100;
        pastWaterMovement = 100; pastInWeb = 100; webTicks = 0;
        onGround = false; lastOnGround = false;
        collidedHorizontally = false; collidedVertically = false;
        sprinting = false; sprintingAllowed = false; sneaking = false; handActive = false;
        physicsUnpredictableVelocityExpected = false; inWater = false; inWeb = false; inLava = false; onClimbable = false;
        forwardKey = 0; strafeKey = 0;
        jumpKey = false; sprintKey = false; sneakKey = false;
        rotationYaw = 0; yawSin = 0; yawCos = 0;
        aiMoveSpeed = 0.1f; blockSlipperiness = 0.6f; jumpMotion = 0.42f; resetMotion = 0.003f;
        toleranceXZ = 0.0007; toleranceY = 0.00001;
        lastMotionX = 0; lastMotionY = 0; lastMotionZ = 0;
        playerPosX = 0; playerPosY = 0; playerPosZ = 0;
        positionInitialized = false;
        physicsPacketRelinkFlyVL = 0;
        entityPushX = 0; entityPushZ = 0;
    }
}
