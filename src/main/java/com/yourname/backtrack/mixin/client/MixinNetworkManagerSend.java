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

@Mixin(NetworkManager.class)
public class MixinNetworkManagerSend {

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
            CPacketPlayerAccessor acc = (CPacketPlayerAccessor) p;
            x = acc.getX();
            y = acc.getY();
            z = acc.getZ();
        }

        if (moves || rotates) {
            ClientSimulator.INSTANCE.onOutgoingMovement(moves, x, y, z);
            if (moves) ClientSimulator.INSTANCE.recalculateAfterOutgoingPacket();
        }
    }
}
