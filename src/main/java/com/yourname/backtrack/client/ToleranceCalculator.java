package com.yourname.backtrack.client;

public final class ToleranceCalculator {
    private ToleranceCalculator() {}

    public static void compute(MovementSimState s) {
        double xz = 0.0007;
        double y = 0.00001;

        if (s.pastPlayerReduceAttackPhysics <= 1) {
            xz = s.receivedFlyingPacketIn(4) ? 0.03 : 0.015;
        }

        if (s.pastExternalVelocity == 0) {
            xz = 0.005;
            y = 0.005;
        } else if (s.pastExternalVelocity < 10) {
            xz = Math.max(xz, 0.015);
            y = Math.max(y, 0.005);
            if (s.pastExternalVelocity >= 1 && s.pastExternalVelocity < 10) {
                xz = Math.max(xz, 0.03);
                y = Math.max(y, 0.01);
            }
        }

        if (s.receivedFlyingPacketIn(1) && s.pastExternalVelocity <= 4) {
            y = Math.max(y, 0.03);
        }

        if (s.collidedHorizontally) xz = Math.max(xz, 0.027);

        if (s.physicsUnpredictableVelocityExpected) {
            xz = Math.max(xz, 0.1);
            y = Math.max(y, 0.05);
        }

        if (s.receivedFlyingPacketIn(2) && s.onGround
                && Math.abs(s.lastMotionX) < 0.05 && Math.abs(s.lastMotionZ) < 0.05) {
            xz = Math.max(xz, 0.03);
        }

        double horizPred = Math.sqrt(s.predictedMotionX * s.predictedMotionX + s.predictedMotionZ * s.predictedMotionZ);
        int allowedRelink = horizPred < 0.03 ? 3 : 1;
        if (s.physicsPacketRelinkFlyVL > allowedRelink) {
            xz = Math.max(xz, 0.02);
            y = Math.max(y, 0.005);
        }

        if (s.inWater || s.inLava) y = Math.max(y, 0.02);
        if (s.pastWaterMovement <= 10) y = Math.max(y, 0.05);
        if (s.inWeb) y = Math.max(y, 0.13);
        if (s.pastInWeb < 10 && !s.inWeb) y = Math.max(y, 0.1);
        if (s.onClimbable) xz = Math.max(xz, 0.15);

        s.toleranceXZ = xz;
        s.toleranceY = y;
    }
}
