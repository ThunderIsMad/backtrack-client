package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.client.ClientSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(NetworkManager.class)
public class MixinNetworkManagerSend {

    private static Field fieldX, fieldY, fieldZ;

    static {
        try {
            fieldX = CPacketPlayer.class.getDeclaredField("x");
            fieldY = CPacketPlayer.class.getDeclaredField("y");
            fieldZ = CPacketPlayer.class.getDeclaredField("z");
            fieldX.setAccessible(true);
            fieldY.setAccessible(true);
            fieldZ.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"))
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof CPacketPlayer)) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        CPacketPlayer p = (CPacketPlayer) packet;
        boolean moves = (p instanceof CPacketPlayer.Position) || (p instanceof CPacketPlayer.PositionRotation);
        boolean rotates = (p instanceof CPacketPlayer.Rotation) || (p instanceof CPacketPlayer.PositionRotation);
        double x = mc.player.posX, y = mc.player.posY, z = mc.player.posZ;

        if (moves) {
            if (p instanceof CPacketPlayer.Position) {
                try {
                    x = fieldX.getDouble(p);
                    y = fieldY.getDouble(p);
                    z = fieldZ.getDouble(p);
                } catch (IllegalAccessException ignored) {}
            } else if (p instanceof CPacketPlayer.PositionRotation) {
                try {
                    x = fieldX.getDouble(p);
                    y = fieldY.getDouble(p);
                    z = fieldZ.getDouble(p);
                } catch (IllegalAccessException ignored) {}
            }
        }

        if (moves || rotates) {
            ClientSimulator.INSTANCE.onOutgoingMovement(moves, x, y, z);
            if (moves) {
                ClientSimulator.INSTANCE.recalculateAfterOutgoingPacket();
            }
        }
    }
}
