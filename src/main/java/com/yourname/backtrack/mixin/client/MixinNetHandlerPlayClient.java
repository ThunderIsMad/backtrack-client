package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.module.impl.VelocityModule;
import com.yourname.backtrack.util.FlagLogger;
import com.yourname.backtrack.client.ClientSimulator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraft.network.play.server.SPacketDisconnect;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {

    // ── Teleport setback ──────────────────────────────────────────
    @Inject(method = "handlePlayerPosLook", at = @At("HEAD"))
    private void onHandlePlayerPosLook(SPacketPlayerPosLook packet, CallbackInfo ci) {
        ClientSimulator.INSTANCE.handleTeleport(packet.getX(), packet.getY(), packet.getZ());

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;

        // New comprehensive logger — catches ALL teleport setbacks
        FlagLogger.INSTANCE.logTeleportSetback(mc, packet);

        // Keep the old log for backward compatibility
        SoloBacktrack mod = SoloBacktrack.getInstance();
        if (mod != null) FlagLogger.log(mod, mc, packet);
    }

    // ── Entity velocity (with correction logging) ─────────────────
    @Inject(method = "handleEntityVelocity", at = @At("HEAD"), cancellable = true)
    private void onHandleEntityVelocity(SPacketEntityVelocity packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        if (packet.getEntityID() != mc.player.getEntityId()) return;

        SoloBacktrack mod = SoloBacktrack.getInstance();
        VelocityModule vm = mod != null ? mod.getModuleManager().getModule(VelocityModule.class) : null;
        boolean modifyMode = vm != null && vm.isModifyMode();

        if (!modifyMode) {
            ClientSimulator.INSTANCE.applyVelocity(
                    packet.getMotionX() / 8000.0,
                    packet.getMotionY() / 8000.0,
                    packet.getMotionZ() / 8000.0);
        }

        // Log velocity correction if it looks like an Intave punishment
        FlagLogger.INSTANCE.logVelocityCorrection(mc, packet);

        if (mod == null) return;
        if (vm == null || !vm.isEnabled()) return;

        VelocityModule.SPacketEntityVelocityAccessor accessor =
                (VelocityModule.SPacketEntityVelocityAccessor) (Object) packet;

        if (vm.handleVelocityPacket(accessor)) ci.cancel();
    }

    // ── Explosion ─────────────────────────────────────────────────
    @Inject(method = "handleExplosion", at = @At("HEAD"))
    private void onHandleExplosion(SPacketExplosion packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        ClientSimulator.INSTANCE.applyExplosion(
                packet.getMotionX(), packet.getMotionY(), packet.getMotionZ());
        SoloBacktrack mod = SoloBacktrack.getInstance();
        if (mod == null) return;
        VelocityModule vm = mod.getModuleManager().getModule(VelocityModule.class);
        if (vm != null && vm.isEnabled()) vm.handleExplosion(packet);
    }

    // ── Kick (Intave punishment) ──────────────────────────────────
    @Inject(method = "handleDisconnect", at = @At("HEAD"))
    private void onHandleDisconnect(SPacketDisconnect packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        ITextComponent reason = packet.getReason();
        if (reason != null && mc.player != null) {
            FlagLogger.INSTANCE.logKick(mc, reason.getUnformattedText());
        }
    }
}