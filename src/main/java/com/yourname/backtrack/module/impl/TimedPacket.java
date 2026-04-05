package com.yourname.backtrack.module.impl;

import net.minecraft.network.Packet;

public class TimedPacket {

    private final Packet<?> packet;
    private final long timestamp;

    public TimedPacket(Packet<?> packet) {
        this.packet = packet;
        this.timestamp = System.currentTimeMillis();
    }

    public Packet<?> getPacket() {
        return packet;
    }

    public boolean hasExpired(long delayMs) {
        return System.currentTimeMillis() - timestamp >= delayMs;
    }
}

