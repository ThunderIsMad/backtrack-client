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

        double rawX = packet.getMotionX() / 8000.0;
        double rawY = packet.getMotionY() / 8000.0;
        double rawZ = packet.getMotionZ() / 8000.0;

        // H=100 -> full vanilla KB, H=0 -> no KB
        double h = vm.getHorizontal() / 100.0;
        // V=100 -> full vanilla KB, V=0 -> no KB
        double v = vm.getVertical() / 100.0;

        switch (vm.getMode()) {
            case "Cancel": {
                mc.player.motionX = 0;
                mc.player.motionY = 0;
                mc.player.motionZ = 0;
                ci.cancel();
                break;
            }

            case "Reverse": {
                mc.player.motionX = -rawX * h;
                mc.player.motionY = rawY * v;
                mc.player.motionZ = -rawZ * h;
                ci.cancel();
                break;
            }

            case "JumpReset": {
                mc.player.motionX = rawX * h;
                mc.player.motionY = 0.42;
                mc.player.motionZ = rawZ * h;
                ci.cancel();
                break;
            }

            case "Legit":
            case "Normal":
            default: {
                // motionY always set to full rawY — Intave expects exact Y response.
                // Reducing Y causes Flight flags. Only horizontal is safe to reduce.
                mc.player.motionX = rawX * h;
                mc.player.motionY = rawY;
                mc.player.motionZ = rawZ * h;
                ci.cancel();
                break;
            }
        }
    }
}
