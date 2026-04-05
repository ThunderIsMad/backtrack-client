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

        // Only intercept on main thread — let network thread dispatch happen normally
        if (!mc.isCallingFromMinecraftThread()) return;
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

        // Chance check — if fails, let original packet run unmodified
        if (Math.random() * 100 > vm.getChance()) return;

        double h = 1.0 - vm.getHorizontal() / 100.0;
        double v = 1.0 - vm.getVertical()   / 100.0;

        // Cancel original and apply reduced velocity directly to player
        mc.player.setVelocity(
                packet.getMotionX() / 8000.0 * h,
                packet.getMotionY() / 8000.0 * v,
                packet.getMotionZ() / 8000.0 * h
        );
        ci.cancel();
    }
}
