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

    @Inject(method = "handleEntityVelocity", at = @At("RETURN"))
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

        switch (vm.getMode()) {
            case "Cancel": {
                mc.player.motionX = 0;
                mc.player.motionY = 0;
                mc.player.motionZ = 0;
                break;
            }

            case "Reverse": {
                double h = vm.getHorizontal() / 100.0;
                double v = vm.getVertical() / 100.0;
                mc.player.motionX = -rawX * h;
                mc.player.motionY = rawY * v;
                mc.player.motionZ = -rawZ * h;
                break;
            }

            case "JumpReset": {
                double h = 1.0 - vm.getHorizontal() / 100.0;
                mc.player.motionX = rawX * h;
                mc.player.motionY = 0.42;
                mc.player.motionZ = rawZ * h;
                break;
            }

            case "Legit": {
                double h = 1.0 - (vm.getHorizontal() / 100.0) * 0.65;
                double v = 1.0 - (vm.getVertical() / 100.0) * 0.35;
                mc.player.motionX = rawX * h;
                mc.player.motionY = rawY * v;
                mc.player.motionZ = rawZ * h;
                break;
            }

            case "Normal":
            default: {
                double h = 1.0 - vm.getHorizontal() / 100.0;
                double v = 1.0 - vm.getVertical() / 100.0;
                mc.player.motionX = rawX * h;
                mc.player.motionY = rawY * v;
                mc.player.motionZ = rawZ * h;
                break;
            }
        }
    }
}
