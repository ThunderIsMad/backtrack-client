package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.module.impl.VelocityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.util.text.TextComponentString;
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
                appX =  rawX * h;
                appY =  0.42;
                appZ =  rawZ * h;
                break;
            }
            case "Legit":
            case "Normal":
            default: {
                appX = rawX * h;
                appY = rawY;
                appZ = rawZ * h;
                break;
            }
        }

        mc.player.motionX = appX;
        mc.player.motionY = appY;
        mc.player.motionZ = appZ;
        ci.cancel();

        vm.recordPacket(rawX, rawY, rawZ, appX, appY, appZ);

        if (vm.isDebug()) {
            String msg = String.format(
                    "[VelDebug] Raw(%.4f, %.4f, %.4f) -> Got(%.4f, %.4f, %.4f)",
                    rawX, rawY, rawZ, appX, appY, appZ);
            mc.player.sendMessage(new TextComponentString(msg));
        }
    }
}
