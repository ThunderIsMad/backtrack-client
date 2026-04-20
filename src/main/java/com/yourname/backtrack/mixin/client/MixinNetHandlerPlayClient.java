package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.module.impl.JumpResetModule;
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
     * Intercept incoming velocity packets so JumpResetModule can detect fall damage.
     * Fall damage packet: vx == 0, vz == 0, vy < 0 (pure downward, no horizontal KB).
     * MCP 1.12.2: getter is getEntityID() (capital ID), not getEntityId().
     */
    @Inject(method = "handleEntityVelocity", at = @At("HEAD"))
    private void onHandleEntityVelocity(SPacketEntityVelocity packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        // MCP 1.12.2 mapping: getEntityID() — capital ID
        if (packet.getEntityID() != mc.player.getEntityId()) return;

        SoloBacktrack mod = SoloBacktrack.getInstance();
        if (mod == null) return;

        JumpResetModule jr = mod.getModuleManager().getModule(JumpResetModule.class);
        if (jr == null) return;

        // SPacketEntityVelocity stores velocity as integer (value * 8000)
        double vx = packet.getMotionX() / 8000.0;
        double vy = packet.getMotionY() / 8000.0;
        double vz = packet.getMotionZ() / 8000.0;
        jr.notifyVelocityPacket(vx, vy, vz);
    }
}
