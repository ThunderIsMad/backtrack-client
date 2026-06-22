package com.yourname.backtrack.client

import kotlin.math.sqrt

/**
 * Lightweight key-search: when client forward/strafe are unreliable
 * (modules overriding keys, lag), test alternatives and pick the
 * combination whose predicted motion minimizes distance to actual motion.
 */
object InputKeySearcher {

    /** All plausible (forward, strafe) combinations to search. */
    private val KEY_COMBOS = arrayOf(
        intArrayOf( 1,  0), intArrayOf( 0,  0),
        intArrayOf( 1, -1), intArrayOf( 1,  1),
        intArrayOf( 0, -1), intArrayOf( 0,  1),
        intArrayOf(-1, -1), intArrayOf(-1,  0), intArrayOf(-1,  1)
    )

    /** Maximum horizontal error to accept current keys without searching further. */
    private const val MAX_ERROR = 0.008

    /**
     * Searches forward/strafe combinations and returns the best match.
     * If the current input is good enough, returns `null` (no override needed).
     *
     * @return `int[2] { forward, strafe }` or `null`
     */
    fun search(
        s: MovementSimState,
        physics: MovementPhysicsEngine,
        actualX: Double,
        actualZ: Double
    ): IntArray? {
        // Test current keys first
        val pred = physics.simulateOneTick(s, s.forwardKey, s.strafeKey)
        val err  = error(pred[0], pred[2], actualX, actualZ)
        if (err < MAX_ERROR) return null  // current input is fine

        var bestError = Double.MAX_VALUE
        var bestFwd   = s.forwardKey
        var bestStr   = s.strafeKey

        for (combo in KEY_COMBOS) {
            val pred2 = physics.simulateOneTick(s, combo[0], combo[1])
            val err2  = error(pred2[0], pred2[2], actualX, actualZ)
            if (err2 < bestError) {
                bestError = err2
                bestFwd   = combo[0]
                bestStr   = combo[1]
            }
        }

        // Only override if significantly better
        return if (bestError < err - 0.005) {
            intArrayOf(bestFwd, bestStr)
        } else {
            null
        }
    }

    /** Horizontal distance between predicted and actual motion. */
    private fun error(px: Double, pz: Double, ax: Double, az: Double): Double {
        val dx = px - ax
        val dz = pz - az
        return sqrt(dx * dx + dz * dz)
    }
}