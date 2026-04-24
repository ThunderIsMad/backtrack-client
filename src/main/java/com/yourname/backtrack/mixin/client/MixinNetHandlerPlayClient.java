package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.module.impl.JumpResetModule;
import com.yourname.backtrack.module.impl.VelocityModule;
import com.yourname.backtrack.util.FlagLogger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketEntityVelocity;
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

    /**
     * Intercept SPacketEntityVelocity before vanilla applies it.
     *
     * 1. Notify JumpResetModule (fall-damage / knockback detection).
     * 2. Let VelocityModule modify or cancel the packet.
     *
     * MCP 1.12.2: getter is getEntityID() (capital ID).
     */
    @Inject(method = "handleEntityVelocity", at = @At("HEAD"), cancellable = true)
    private void onHandleEntityVelocity(SPacketEntityVelocity packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        if (packet.getEntityID() != mc.player.getEntityId()) return;

        SoloBacktrack mod = SoloBacktrack.getInstance();
        if (mod == null) return;

        // Notify JumpResetModule of the incoming velocity packet.
        JumpResetModule jr = mod.getModuleManager().getModule(JumpResetModule.class);
        if (jr != null) {
            double vx = packet.getMotionX() / 8000.0;
            double vy = packet.getMotionY() / 8000.0;
            double vz = packet.getMotionZ() / 8000.0;
            jr.notifyVelocityPacket(vx, vy, vz);
        }

        // Let VelocityModule modify or cancel the packet.
        VelocityModule vm = mod.getModuleManager().getModule(VelocityModule.class);
        if (vm == null) return;

        VelocityModule.SPacketEntityVelocityAccessor accessor =
                (VelocityModule.SPacketEntityVelocityAccessor)(Object) packet;

        if (vm.handleVelocityPacket(accessor)) ci.cancel();
    }
}
