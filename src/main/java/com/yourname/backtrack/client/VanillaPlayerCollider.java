package com.yourname.backtrack.client

import net.minecraft.client.Minecraft
import net.minecraft.util.math.AxisAlignedBB
import kotlin.math.sqrt

/**
 * Vanilla-order collision resolution (Y → X → Z) against world collision boxes.
 */
object VanillaPlayerCollider {

    private const val WIDTH  = 0.6
    private const val HEIGHT = 1.8

    data class CollideResult(
        val motionX: Double,
        val motionY: Double,
        val motionZ: Double,
        val onGround: Boolean,
        val collidedHorizontally: Boolean,
        val collidedVertically: Boolean
    )

    /**
     * Resolves [motionX, motionY, motionZ] against the world starting from
     * (posX, posY, posZ) using vanilla Minecraft order: Y first, then X, then Z.
     */
    fun collide(
        mc: Minecraft,
        posX: Double, posY: Double, posZ: Double,
        motionX: Double, motionY: Double, motionZ: Double
    ): CollideResult {
        val world = mc.world ?: return CollideResult(motionX, motionY, motionZ, false, false, false)

        // Build player bounding box at current position
        var entityBB = AxisAlignedBB(
            posX - WIDTH / 2, posY, posZ - WIDTH / 2,
            posX + WIDTH / 2, posY + HEIGHT, posZ + WIDTH / 2
        )

        // Expand to include the movement delta and collect overlapping boxes
        val expanded = entityBB.expand(motionX, motionY, motionZ)
        val collidingBoxes = world.getCollisionBoxes(null, expanded)

        var resultX = motionX
        var resultY = motionY
        var resultZ = motionZ
        var collidedH = false
        var collidedV = false

        // ── Y axis ──────────────────────────────────────────────────
        for (bb in collidingBoxes) {
            resultY = bb.calculateYOffset(entityBB, resultY)
        }
        entityBB = entityBB.offset(0.0, resultY, 0.0)
        if (resultY != motionY) collidedV = true

        // ── X axis ──────────────────────────────────────────────────
        for (bb in collidingBoxes) {
            resultX = bb.calculateXOffset(entityBB, resultX)
        }
        entityBB = entityBB.offset(resultX, 0.0, 0.0)
        if (resultX != motionX) collidedH = true

        // ── Z axis ──────────────────────────────────────────────────
        for (bb in collidingBoxes) {
            resultZ = bb.calculateZOffset(entityBB, resultZ)
        }
        if (resultZ != motionZ) collidedH = true

        val ground = collidedV && motionY < 0 && resultY <= 0

        return CollideResult(resultX, resultY, resultZ, ground, collidedH, collidedV)
    }

    /**
     * Returns true if the player is standing on a solid block.
     * Fires a tiny downward movement (-0.500001) — if the result differs,
     * there is a block stopping the fall.
     */
    fun isOnGround(mc: Minecraft, posX: Double, posY: Double, posZ: Double): Boolean {
        val r = collide(mc, posX, posY, posZ, 0.0, -0.500001, 0.0)
        return r.motionY != -0.500001
    }

    /** True if the displacement vector is within the flying-packet uncertainty radius. */
    fun flyingDisplacement(dx: Double, dy: Double, dz: Double): Boolean {
        return sqrt(dx * dx + dy * dy + dz * dz) <= MovementSimState.FLYING_UNCERTAINTY_RADIUS
    }
}