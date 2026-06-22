package com.yourname.backtrack.module.impl

import net.minecraft.client.Minecraft
import net.minecraft.network.INetHandler
import net.minecraft.network.Packet
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayer

/**
 * Stateless packet helpers.  Every method requires a valid connection
 * or player and silently returns otherwise.
 */
object PacketUtils {

    @JvmStatic
    fun receivePacket(packet: Packet<*>) {
        val conn = Minecraft.getMinecraft().connection ?: return
        try {
            @Suppress("UNCHECKED_CAST")
            (packet as Packet<INetHandler>).processPacket(conn)
        } catch (_: Exception) {}
    }

    @JvmStatic
    fun sendPacket(packet: Packet<*>) {
        Minecraft.getMinecraft().connection?.sendPacket(packet)
    }

    @JvmStatic
    fun sendC0B(sprinting: Boolean) {
        val player = Minecraft.getMinecraft().player ?: return
        val action = if (sprinting) CPacketEntityAction.Action.START_SPRINTING
                     else CPacketEntityAction.Action.STOP_SPRINTING
        sendPacket(CPacketEntityAction(player, action))
    }

    @JvmStatic
    fun sendFakeSprint() {
        sendC0B(false)
        sendC0B(true)
    }

    @JvmStatic
    fun sendPosition(x: Double, y: Double, z: Double, yaw: Float, pitch: Float, onGround: Boolean) {
        sendPacket(CPacketPlayer.PositionRotation(x, y, z, yaw, pitch, onGround))
    }

    @JvmStatic
    fun sendRotation(yaw: Float, pitch: Float, onGround: Boolean) {
        sendPacket(CPacketPlayer.Rotation(yaw, pitch, onGround))
    }

    @JvmStatic
    fun sendPosition(x: Double, y: Double, z: Double, onGround: Boolean) {
        sendPacket(CPacketPlayer.Position(x, y, z, onGround))
    }

    @JvmStatic
    fun sendPacketNoEvent(packet: Packet<*>) {
        Minecraft.getMinecraft().connection?.sendPacket(packet)
    }
}