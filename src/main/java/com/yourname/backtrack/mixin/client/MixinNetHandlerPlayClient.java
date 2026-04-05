package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.module.impl.VelocityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {

    @Inject(method = "handleEntityVelocity", at = @At("HEAD"), cancellable = true)
    private void onHandleEntityVelocity(SPacketEntityVelocity packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();

        if (mc.player == null || mc.world == null) return;
        if (packet.getEntityID() != mc.player.getEntityId()) return;

        SoloBacktrack mod = SoloBacktrack.getInstance();
        if (mod == null) return;

        VelocityModule vm = mod.getModuleManager().getModules().stream()
                .filter(m -> m instanceof VelocityModule && m.isEnabled())
                .map(m -> (VelocityModule) m)
                .findFirst()
                .orElse(null);

        if (vm == null) return;

        if (Math.random() * 100 > vm.getChance()) return;

        double h = - vm.getHorizontal() / 100.0;
        double v = - vm.getVertical() / 100.0;

        // If both are 0% (full cancel), just cancel the packet — no motion to apply
        if (vm.getHorizontal() == 0 && vm.getVertical() == 0) {
            ci.cancel();
            return;
        }

        // Replace with scaled velocity using MCP 1.12.2 public motion fields
        mc.player.motionX = packet.getMotionX() / 8000.0 * h;
        mc.player.motionY = packet.getMotionY() / 8000.0 * v;
        mc.player.motionZ = packet.getMotionZ() / 8000.0 * h;
        ci.cancel();
    }
}