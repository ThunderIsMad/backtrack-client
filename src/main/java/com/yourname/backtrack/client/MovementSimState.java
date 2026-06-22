package com.yourname.backtrack.client

data class MovementSimState(
    // ── Verified position ────────────────────────────────────────────
    var verifiedX: Double = 0.0,
    var verifiedY: Double = 0.0,
    var verifiedZ: Double = 0.0,
    var lastX: Double = 0.0,
    var lastY: Double = 0.0,
    var lastZ: Double = 0.0,

    // ── Base motion (last tick, before input) ────────────────────────
    var baseMotionX: Double = 0.0,
    var baseMotionY: Double = 0.0,
    var baseMotionZ: Double = 0.0,
    var baseMotionXBeforeVelocity: Double = 0.0,
    var baseMotionYBeforeVelocity: Double = 0.0,
    var baseMotionZBeforeVelocity: Double = 0.0,

    // ── Predicted motion (after simulation) ──────────────────────────
    var predictedMotionX: Double = 0.0,
    var predictedMotionY: Double = 0.0,
    var predictedMotionZ: Double = 0.0,

    // ── Velocity / attack counters ───────────────────────────────────
    var pastExternalVelocity: Int = 100,
    var pastVelocity: Int = 100,
    var pastPlayerReduceAttackPhysics: Int = 100,
    var reduceTicks: Int = 0,

    // ── Flying packet / environment counters ─────────────────────────
    var pastFlyingPacketAccurate: Int = 100,
    var pastWaterMovement: Int = 100,
    var pastInWeb: Int = 100,
    var webTicks: Int = 0,

    // ── Ground / collision flags ─────────────────────────────────────
    var onGround: Boolean = false,
    var lastOnGround: Boolean = false,
    var collidedHorizontally: Boolean = false,
    var collidedVertically: Boolean = false,

    // ── Movement flags ───────────────────────────────────────────────
    var sprinting: Boolean = false,
    var sprintingAllowed: Boolean = false,
    var sneaking: Boolean = false,
    var handActive: Boolean = false,
    var physicsUnpredictableVelocityExpected: Boolean = false,
    var inWater: Boolean = false,
    var inWeb: Boolean = false,
    var inLava: Boolean = false,
    var onClimbable: Boolean = false,

    // ── Input keys ───────────────────────────────────────────────────
    var forwardKey: Int = 0,
    var strafeKey: Int = 0,
    var jumpKey: Boolean = false,
    var sprintKey: Boolean = false,
    var sneakKey: Boolean = false,

    // ── Rotation ─────────────────────────────────────────────────────
    var rotationYaw: Float = 0f,
    var yawSin: Float = 0f,
    var yawCos: Float = 0f,

    // ── Movement attributes ──────────────────────────────────────────
    var aiMoveSpeed: Float = 0.1f,
    var blockSlipperiness: Float = 0.6f,
    var jumpMotion: Float = 0.42f,
    var resetMotion: Float = 0.003f,

    // ── Tolerance (calculated by ToleranceCalculator) ────────────────
    var toleranceXZ: Double = 0.0007,
    var toleranceY: Double = 0.00001,

    // ── Previous motion (for drift detection) ────────────────────────
    var lastMotionX: Double = 0.0,
    var lastMotionY: Double = 0.0,
    var lastMotionZ: Double = 0.0,

    // ── Player position (for sync) ───────────────────────────────────
    var playerPosX: Double = 0.0,
    var playerPosY: Double = 0.0,
    var playerPosZ: Double = 0.0,
    var positionInitialized: Boolean = false,

    // ── Relink / flying ──────────────────────────────────────────────
    var physicsPacketRelinkFlyVL: Int = 0,

    // ── Entity push (accumulated per tick) ───────────────────────────
    var entityPushX: Double = 0.0,
    var entityPushZ: Double = 0.0
) {
    companion object {
        const val FLYING_UNCERTAINTY_RADIUS = 0.03
    }

    /** Reset per-tick flags. Called at the start of each tick. */
    fun beginTick() {
        physicsUnpredictableVelocityExpected = false
        collidedHorizontally = false
        collidedVertically = false
        lastMotionX = predictedMotionX
        lastMotionY = predictedMotionY
        lastMotionZ = predictedMotionZ
        entityPushX = 0.0
        entityPushZ = 0.0
    }

    /** True while the player is inside the velocity window (recent external velocity). */
    fun isInVelocityWindow(): Boolean = pastExternalVelocity < 10

    /** True if a flying packet was received within the given number of ticks. */
    fun receivedFlyingPacketIn(ticks: Int): Boolean = pastFlyingPacketAccurate <= ticks

    /** Advance water/web counters based on current environment flags. */
    fun tickEnvironmentCounters() {
        pastWaterMovement = if (inWater) 0 else (pastWaterMovement + 1).coerceAtMost(100)
        if (inWeb) {
            pastInWeb = 0
            webTicks++
        } else {
            pastInWeb = (pastInWeb + 1).coerceAtMost(100)
            webTicks = 0
        }
    }
}