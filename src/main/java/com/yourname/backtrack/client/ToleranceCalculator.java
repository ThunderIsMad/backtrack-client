package com.yourname.backtrack.client

import kotlin.math.abs
import kotlin.math.sqrt

object ToleranceCalculator {

    fun compute(s: MovementSimState) {
        with(s) {
            // Base tolerance — nearly exact match expected
            var xz = 0.0007
            var y  = 0.00001

            // Attack reduce window — server allows wider deviation
            if (pastPlayerReduceAttackPhysics <= 1) {
                xz = if (receivedFlyingPacketIn(4)) 0.03 else 0.015
            }

            // Velocity window — fresh external input, higher uncertainty
            when {
                pastExternalVelocity == 0 -> { xz = 0.005; y = 0.005 }
                pastExternalVelocity < 10 -> { xz = 0.03;  y = 0.01  }
            }

            // Flying packet arriving right after velocity
            if (receivedFlyingPacketIn(1) && pastExternalVelocity <= 4) {
                y = y.coerceAtLeast(0.03)
            }

            // Horizontal collision — position uncertainty increases
            if (collidedHorizontally) {
                xz = xz.coerceAtLeast(0.027)
            }

            // Unpredictable velocity — widest window
            if (physicsUnpredictableVelocityExpected) {
                xz = xz.coerceAtLeast(0.1)
                y  = y.coerceAtLeast(0.05)
            }

            // Flying packet on ground with minimal horizontal movement
            if (receivedFlyingPacketIn(2) && onGround
                && abs(lastMotionX) < 0.05 && abs(lastMotionZ) < 0.05) {
                xz = xz.coerceAtLeast(0.03)
            }

            // Relink flying packet exceeded allowed count
            val horizPred = sqrt(predictedMotionX * predictedMotionX + predictedMotionZ * predictedMotionZ)
            val allowedRelink = if (horizPred < 0.03) 3 else 1
            if (physicsPacketRelinkFlyVL > allowedRelink) {
                xz = xz.coerceAtLeast(0.02)
                y  = y.coerceAtLeast(0.005)
            }

            // Water / lava
            if (inWater || inLava) {
                y = y.coerceAtLeast(0.02)
            }
            if (pastWaterMovement <= 10) {
                y = y.coerceAtLeast(0.05)
            }

            // Cobweb
            if (inWeb) {
                y = y.coerceAtLeast(0.13)
            }
            if (pastInWeb < 10 && !inWeb) {
                y = y.coerceAtLeast(0.1)
            }

            // Ladders / vines
            if (onClimbable) {
                xz = xz.coerceAtLeast(0.15)
            }

            toleranceXZ = xz
            toleranceY  = y
        }
    }
}