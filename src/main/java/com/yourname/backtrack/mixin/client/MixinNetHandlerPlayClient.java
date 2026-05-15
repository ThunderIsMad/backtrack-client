package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.module.impl.VelocityModule;
import com.yourname.backtrack.util.FlagLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {

    @Inject(method = "handlePlayerPosLook", at = @At("HEAD"))
    private void onHandlePlayerPosLook(SPacketPlayerPosLook packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        SoloBacktrack mod = SoloBacktrack.getInstance();
        if (mod == null) return;
        FlagLogger.log(mod, mc, packet);
    }

    @Inject(method = "handleEntityVelocity", at = @At("HEAD"), cancellable = true)
    private void onHandleEntityVelocity(SPacketEntityVelocity packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        if (packet.getEntityID() != mc.player.getEntityId()) return;

        SoloBacktrack mod = SoloBacktrack.getInstance();
        if (mod == null) return;

        VelocityModule vm = mod.getModuleManager().getModule(VelocityModule.class);
        if (vm == null) return;

        // Let VelocityModule process the packet (Modify, Reverse trigger, etc.)
        VelocityModule.SPacketEntityVelocityAccessor accessor =
                (VelocityModule.SPacketEntityVelocityAccessor)(Object) packet;

        if (vm.handleVelocityPacket(accessor)) ci.cancel();
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"))
    private void onHandleExplosion(SPacketExplosion packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        SoloBacktrack mod = SoloBacktrack.getInstance();
        if (mod == null) return;

        VelocityModule vm = mod.getModuleManager().getModule(VelocityModule.class);
        if (vm == null) return;

        vm.handleExplosion(packet);
    }
}