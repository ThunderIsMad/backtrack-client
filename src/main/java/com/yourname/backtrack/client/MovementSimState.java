package com.yourname.backtrack.client;

/**
 * Mirrors key fields from Intave's MovementMetadata.
 * Single source of truth for the client-side simulation state.
 */
public class MovementSimState {

    // ── Position ──────────────────────────────────────────────
    public double verifiedX, verifiedY, verifiedZ;
    public double lastX, lastY, lastZ;

    // ── Motion for physics check ─────────────────────────────
    public double baseMotionX, baseMotionY, baseMotionZ;
    public double baseMotionXBeforeVelocity, baseMotionYBeforeVelocity, baseMotionZBeforeVelocity;
    public double predictedMotionX, predictedMotionY, predictedMotionZ;

    // ── Counters (match Intave exactly) ──────────────────────
    public int pastExternalVelocity;
    public int pastVelocity;
    public int pastPlayerReduceAttackPhysics;
    public int reduceTicks;

    // ── Ground / collision flags ─────────────────────────────
    public boolean onGround, lastOnGround;
    public boolean collidedHorizontally, collidedVertically;

    // ── Input state (filled by captureInput) ─────────────────
    public int     keyForward, keyStrafe;
    public boolean jumpKey, sneakKey, sprintKey;
    public float   rotationYaw;
    public float   yawSin, yawCos;

    // ── Sprint / hand / physics flags ────────────────────────
    public boolean sprintingAllowed;
    public boolean handActive;
    public boolean physicsUnpredictableVelocityExpected;

    // ── Attributes ────────────────────────────────────────────
    public float aiMoveSpeed;
    public float blockSlipperiness;
    public float jumpMotion;
    public float resetMotion;

    // ── Tolerances (computed) ────────────────────────────────
    public double toleranceXZ;
    public double toleranceY;
    public int pastFlyingPacketAccurate;
    public boolean receivedFlyingPacketIn(int ticks) {
        return pastFlyingPacketAccurate <= ticks;
    }

    public void reset() {
        verifiedX = 0; verifiedY = 0; verifiedZ = 0;
        lastX = 0; lastY = 0; lastZ = 0;
        baseMotionX = 0; baseMotionY = 0; baseMotionZ = 0;
        baseMotionXBeforeVelocity = 0; baseMotionYBeforeVelocity = 0; baseMotionZBeforeVelocity = 0;
        predictedMotionX = 0; predictedMotionY = 0; predictedMotionZ = 0;
        pastExternalVelocity = 100;
        pastVelocity = 100;
        pastPlayerReduceAttackPhysics = 100;
        reduceTicks = 0;
        onGround = false; lastOnGround = false;
        collidedHorizontally = false; collidedVertically = false;
        keyForward = 0; keyStrafe = 0;
        jumpKey = false; sneakKey = false; sprintKey = false;
        rotationYaw = 0; yawSin = 0; yawCos = 0;
        sprintingAllowed = false;
        handActive = false;
        physicsUnpredictableVelocityExpected = false;
        aiMoveSpeed = 0.1f;
        blockSlipperiness = 0.6f;
        jumpMotion = 0.42f;
        resetMotion = 0.003f;
        toleranceXZ = 0.0007;
        toleranceY = 0.00001;
    }
}