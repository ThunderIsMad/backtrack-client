package com.yourname.backtrack.client

import net.minecraft.client.Minecraft
import net.minecraft.util.text.TextComponentString
import kotlin.math.abs

/**
 * In-chat debug logging for the client-side simulator.
 * Toggle [enabled] and [logToChat] at runtime to inspect
 * simulation state, shadow-mode drift, and tolerance windows.
 */
object SimDebug {

    @JvmField var enabled = false
    @JvmField var logToChat = false
    @JvmField var logShadowMismatch = true

    fun logTick(sim: ClientSimulator) {
        if (!enabled) return

        val s = sim.state()
        val mc = Minecraft.getMinecraft() ?: return

        // ── Shadow-mode mismatch warning ────────────────────────────
        if (sim.shadowMode && logShadowMismatch && mc.player != null) {
            val dx = abs(mc.player.motionX - s.predictedMotionX)
            val dy = abs(mc.player.motionY - s.predictedMotionY)
            val dz = abs(mc.player.motionZ - s.predictedMotionZ)
            if (dx > s.toleranceXZ || dz > s.toleranceXZ || dy > s.toleranceY) {
                val warn = "[Sim:shadow] diff=(%.4f,%.4f,%.4f) tol=(%.4f,%.4f) pev=%d".format(
                    dx, dy, dz, s.toleranceXZ, s.toleranceY, s.pastExternalVelocity
                )
                if (logToChat) mc.player.sendMessage(TextComponentString(warn))
            }
        }

        // ── Per-tick state snapshot ─────────────────────────────────
        val line = "[Sim] pev=%d pvel=%d ppra=%d flyVL=%d tolXZ=%.4f tolY=%.4f exp=(%.3f,%.3f,%.3f) base=(%.3f,%.3f,%.3f) og=%d ch=%d".format(
            s.pastExternalVelocity, s.pastVelocity, s.pastPlayerReduceAttackPhysics,
            s.physicsPacketRelinkFlyVL, s.toleranceXZ, s.toleranceY,
            s.predictedMotionX, s.predictedMotionY, s.predictedMotionZ,
            s.baseMotionX, s.baseMotionY, s.baseMotionZ,
            if (s.onGround) 1 else 0,
            if (s.collidedHorizontally) 1 else 0
        )
        if (logToChat && mc.player != null) {
            mc.player.sendMessage(TextComponentString(line))
        }
    }
}