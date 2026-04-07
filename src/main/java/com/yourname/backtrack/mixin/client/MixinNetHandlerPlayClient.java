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

    @Inject(method = "handleEntityVelocity", at = @At("TAIL"))
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

        double rawX = mc.player.motionX;
        double rawY = mc.player.motionY;
        double rawZ = mc.player.motionZ;

        double h = vm.getHorizontal() / 100.0;
        double v = vm.getVertical()   / 100.0;

        double appX, appY, appZ;

        switch (vm.getMode()) {
            case "Cancel": {
                appX = 0; appY = 0; appZ = 0;
                break;
            }
            case "Reverse": {
                appX = -rawX * h;
                appY =  rawY * v;
                appZ = -rawZ * h;
                break;
            }
            case "JumpReset": {
                appX = rawX * h;
                appY = 0.42;
                appZ = rawZ * h;
                break;
            }
            case "Legit":
            case "Normal":
            default: {
                // Skip entirely if h is effectively vanilla — nothing to do,
                // and touching motion at all creates a detectable timing signature.
                if (h >= 1.0) return;

                // Jitter the multiplier ±8% but never allow amplification above
                // the raw value (no hJittered > 1.0) and never below 0.
                double noise = (Math.random() - 0.5) * 0.16;
                double hJittered = Math.max(0.0, Math.min(h, h + noise));
                appX = rawX * hJittered;
                appY = rawY * v;
                appZ = rawZ * hJittered;
                break;
            }
        }

        mc.player.motionX = appX;
        mc.player.motionY = appY;
        mc.player.motionZ = appZ;

        vm.recordPacket(rawX, rawY, rawZ, appX, appY, appZ);
    }
}
