package com.yourname.backtrack.module.impl

import net.minecraft.network.Packet

data class TimedPacket(
    val packet: Packet<*>,
    private val timestamp: Long = System.currentTimeMillis()
) {
    fun hasExpired(delayMs: Long): Boolean = System.currentTimeMillis() - timestamp >= delayMs
}