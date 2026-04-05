package com.yourname.backtrack.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;

public class PacketUtils {
    private static final Minecraft mc = Minecraft.getMinecraft();

    @SuppressWarnings("unchecked")
    public static void receivePacket(Packet<?> packet) {
        if (mc.getConnection() == null) return;
        try {
            ((Packet<INetHandler>) packet).processPacket(mc.getConnection());
        } catch (Exception ignored) {}
    }
}

