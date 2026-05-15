// PacketUtils.java
package com.yourname.backtrack.module.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketEntityAction;
import net.minecraft.network.play.client.CPacketPlayer;

public class PacketUtils {

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    @SuppressWarnings("unchecked")
    public static void receivePacket(Packet<?> packet) {
        if (mc().getConnection() == null) return;
        try {
            ((Packet<INetHandler>) packet).processPacket(mc().getConnection());
        } catch (Exception ignored) {}
    }

    public static void sendPacket(Packet<?> packet) {
        if (mc().getConnection() != null) {
            mc().getConnection().sendPacket(packet);
        }
    }

    public static void sendC0B(boolean sprinting) {
        if (mc().player == null) return;

        CPacketEntityAction.Action action = sprinting
                ? CPacketEntityAction.Action.START_SPRINTING
                : CPacketEntityAction.Action.STOP_SPRINTING;

        sendPacket(new CPacketEntityAction(mc().player, action));
    }

    public static void sendFakeSprint() {
        sendC0B(false); // STOP_SPRINTING
        sendC0B(true);  // START_SPRINTING
    }

    public static void sendPosition(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        sendPacket(new CPacketPlayer.PositionRotation(x, y, z, yaw, pitch, onGround));
    }

    public static void sendRotation(float yaw, float pitch, boolean onGround) {
        sendPacket(new CPacketPlayer.Rotation(yaw, pitch, onGround));
    }

    public static void sendPosition(double x, double y, double z, boolean onGround) {
        sendPacket(new CPacketPlayer.Position(x, y, z, onGround));
    }

    public static void sendPacketNoEvent(Packet<?> packet) {
        if (mc().getConnection() != null) {
            mc().getConnection().sendPacket(packet);
        }
    }
}