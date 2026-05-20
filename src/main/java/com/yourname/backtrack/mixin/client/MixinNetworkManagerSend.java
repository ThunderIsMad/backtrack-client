package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.client.ClientSimulator;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketPlayer;
import io.netty.channel.ChannelHandlerContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetworkManager.class)
public class MixinNetworkManagerSend {

    @Inject(method = "sendPacket(Lnet/minecraft/network/Packet;)V", at = @At("HEAD"))
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        // Check if packet is a movement packet (any CPacketPlayer subtype)
        if (packet instanceof CPacketPlayer) {
            ClientSimulator.INSTANCE.onOutgoingMovement();
        }
    }
}