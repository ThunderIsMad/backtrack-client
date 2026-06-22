package com.yourname.backtrack.util

import com.yourname.backtrack.client.ClientSimulator
import net.minecraft.client.Minecraft
import net.minecraft.network.play.server.SPacketEntityVelocity
import net.minecraft.network.play.server.SPacketPlayerPosLook
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Comprehensive Intave punishment logger.
 * Captures teleport setbacks, velocity corrections, silent motion
 * resets, sprint resets, chat flags, and kicks — aiming for
 * near-100% coverage of all Intave enforcement actions.
 */
object FlagLogger {

    private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd")
    private val TIME_FMT = SimpleDateFormat("HH:mm:ss.SSS")

    // Deduplication state for teleport setbacks
    private var lastSx = Double.NaN
    private var lastSy = Double.NaN
    private var lastSz = Double.NaN
    private var lastTeleportLogTime = 0L

    // Sprint tracking for silent reset detection
    private var wasSprinting = false

    // ── Chat trigger keywords (case-insensitive) ──────────────────
    private val INTAVE_TRIGGERS = arrayOf(
        "moving incorrectly", "moved incorrectly", "moving too frequently",
        "attacking suspiciously", "attacked too quickly", "attacked too many entities",
        "is clicking statistically", "is sending invalid packets",
        "is interacting suspiciously", "is placing blocks suspiciously",
        "is performing invalid item-operations", "halting game ticks",
        "suspicious clicks", "click pattern", "knockback manipulation",
        "velocity manipulation", "improbable velocity", "physics violation",
        "VL", "violation", "setback", "Intave"
    )

    // ── Public entry points (called from mixins / event handlers) ─

    /** Call from MixinSPacketPlayerPosLook or your network handler. */
    fun logTeleportSetback(mc: Minecraft, packet: SPacketPlayerPosLook) {
        val player = mc.player ?: return

        val px = player.posX
        val py = player.posY
        val pz = player.posZ
        val sx = packet.x
        val sy = packet.y
        val sz = packet.z

        val dist = sqrt((sx - px) * (sx - px) + (sy - py) * (sy - py) + (sz - pz) * (sz - pz))

        // Ignore microscopic sync corrections
        if (dist < 0.3) return

        // Deduplicate: same destination within 1 second → skip
        val now = System.currentTimeMillis()
        if (now - lastTeleportLogTime < 1000
            && abs(sx - lastSx) < 0.01
            && abs(sy - lastSy) < 0.01
            && abs(sz - lastSz) < 0.01
        ) return

        lastSx = sx; lastSy = sy; lastSz = sz
        lastTeleportLogTime = now

        val forcedYaw = packet.yaw
        val forcedPitch = packet.pitch
        val yawDiff = abs(player.rotationYaw - forcedYaw)
        val pitchDiff = abs(player.rotationPitch - forcedPitch)

        val sim = ClientSimulator
        val s = sim.state()
        val simSnapshot = "pev=${s.pastExternalVelocity} " +
            "tolXZ=%.4f tolY=%.4f inWin=${s.isInVelocityWindow()}".format(s.toleranceXZ, s.toleranceY)

        val details = buildString {
            append("dist=${"%.2f".format(dist)} ")
            append("yawDiff=${"%.1f".format(yawDiff)} pitchDiff=${"%.1f".format(pitchDiff)} ")
            append("hurtTime=${player.hurtTime} ")
            append("sim=[$simSnapshot]")
        }

        writeLog(mc, "TELEPORT SETBACK", details)
    }

    /** Call from MixinSPacketEntityVelocity or your network handler. */
    fun logVelocityCorrection(mc: Minecraft, packet: SPacketEntityVelocity) {
        val player = mc.player ?: return
        if (packet.entityID != player.entityId) return

        val vx = packet.motionX / 8000.0
        val vy = packet.motionY / 8000.0
        val vz = packet.motionZ / 8000.0
        val mag = sqrt(vx * vx + vy * vy + vz * vz)

        // Legitimate knockback is usually > 0.15 magnitude.
        // Intave correction typically kills velocity (mag < 0.05)
        // or sends a vector that contradicts expected KB direction.
        if (mag > 0.01 && mag < 0.05 && player.hurtTime > 0) {
            val sim = ClientSimulator.state()
            writeLog(mc, "VELOCITY CORRECTION",
                "vx=%.3f vy=%.3f vz=%.3f mag=%.3f hurtTime=%d pev=%d".format(
                    vx, vy, vz, mag, player.hurtTime, sim.pastExternalVelocity))
        }
    }

    /** Call from IntaveChatLogger or your chat packet handler. */
    fun logChatFlag(mc: Minecraft, plainText: String) {
        val match = INTAVE_TRIGGERS.any { plainText.contains(it, ignoreCase = true) }
        if (match) {
            writeLog(mc, "CHAT FLAG", "message=$plainText")
        }
    }

    /** Call from Mixin on SPacketDisconnect / kick handling. */
    fun logKick(mc: Minecraft, reason: String) {
        val match = INTAVE_TRIGGERS.any { reason.contains(it, ignoreCase = true) }
        if (match) {
            writeLog(mc, "KICK", "reason=$reason")
        }
    }

    // ── Tick-based detection (call from ClientTickHandler.onClientTickEnd) ─

    /** Call every tick to detect silent motion corrections and sprint resets. */
    fun tickCheck(mc: Minecraft) {
        checkSilentMotionCorrection(mc)
        checkSprintReset(mc)
    }

    private fun checkSilentMotionCorrection(mc: Minecraft) {
        val player = mc.player ?: return
        val sim = ClientSimulator
        val s = sim.state()

        // Only active during velocity window — outside it, motion is player-controlled
        if (!s.isInVelocityWindow()) return

        val predX = sim.expectedX
        val predY = sim.expectedY
        val predZ = sim.expectedZ
        val actX  = player.motionX
        val actY  = player.motionY
        val actZ  = player.motionZ

        val dx = abs(actX - predX)
        val dy = abs(actY - predY)
        val dz = abs(actZ - predZ)

        val tolXZ = sim.toleranceXZ * 3.0
        val tolY  = sim.toleranceY * 3.0

        // If deviation exceeds expanded tolerance but is < 0.1 —
        // likely a silent server-side motion adjustment
        if ((dx > tolXZ || dz > tolXZ || dy > tolY) &&
            dx < 0.1 && dz < 0.1 && dy < 0.1) {
            writeLog(mc, "SILENT MOTION CORRECTION",
                "pred=(%.3f,%.3f,%.3f) act=(%.3f,%.3f,%.3f) " +
                "dx=%.4f dz=%.4f dy=%.4f".format(
                    predX, predY, predZ, actX, actY, actZ, dx, dz, dy))
        }
    }

    private fun checkSprintReset(mc: Minecraft) {
        val player = mc.player ?: return

        if (wasSprinting && !player.isSprinting && player.hurtTime > 0) {
            writeLog(mc, "SPRINT RESET", "hurtTime=${player.hurtTime}")
        }
        wasSprinting = player.isSprinting
    }

    // ── Internal file I/O ─────────────────────────────────────────

    private fun writeLog(mc: Minecraft, type: String, details: String) {
        try {
            val logDir = File(mc.mcDataDir, "logs").also { it.mkdirs() }
            val fileName = "flaglog-${DATE_FMT.format(Date())}.txt"
            val logFile = File(logDir, fileName)

            PrintWriter(FileWriter(logFile, true)).use { pw ->
                pw.println("[${TIME_FMT.format(Date())}] $type")
                pw.println("  $details")
                pw.println()
                pw.flush()
            }
        } catch (_: Exception) {
            // Silently ignore I/O errors — logging is non-critical
        }
    }
}