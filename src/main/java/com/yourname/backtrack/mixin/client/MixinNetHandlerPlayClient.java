package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.module.impl.VelocityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {

    private static final Logger VELOCITY_LOG = LogManager.getLogger("BacktrackVelocity");

    @Inject(method = "handleEntityVelocity", at = @At("HEAD"), cancellable = true)
    private void onHandleEntityVelocity(SPacketEntityVelocity packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();

        VELOCITY_LOG.info("[Velocity] handleEntityVelocity fired. packetEntityId={} playerEntityId={}",
                packet.getEntityID(),
                mc.player != null ? mc.player.getEntityId() : "null");

        if (mc.player == null || mc.world == null) return;
        if (packet.getEntityID() != mc.player.getEntityId()) return;

        VELOCITY_LOG.info("[Velocity] Packet is for local player. rawX={} rawY={} rawZ={}",
                packet.getMotionX() / 8000.0,
                packet.getMotionY() / 8000.0,
                packet.getMotionZ() / 8000.0);

        SoloBacktrack mod = SoloBacktrack.getInstance();
        if (mod == null) return;

        VelocityModule vm = mod.getModuleManager().getModules().stream()
                .filter(m -> m instanceof VelocityModule && m.isEnabled())
                .map(m -> (VelocityModule) m)
                .findFirst()
                .orElse(null);

        VELOCITY_LOG.info("[Velocity] VelocityModule lookup: {}",
                vm != null ? "found, mode=" + vm.getMode() + " H=" + vm.getHorizontal()
                             + " V=" + vm.getVertical() + " Chance=" + vm.getChance()
                        : "NOT FOUND or not enabled");

        if (vm == null) return;

        double roll = Math.random() * 100;
        boolean chancePass = roll <= vm.getChance();

        VELOCITY_LOG.info("[Velocity] Chance roll={} threshold={} pass={}",
                String.format("%.1f", roll),
                vm.getChance(),
                chancePass);

        if (!chancePass) return;

        double rawX = packet.getMotionX() / 8000.0;
        double rawY = packet.getMotionY() / 8000.0;
        double rawZ = packet.getMotionZ() / 8000.0;

        switch (vm.getMode()) {
            case "Cancel": {
                VELOCITY_LOG.info("[Velocity] Mode=Cancel — dropping packet");
                ci.cancel();
                break;
            }

            case "Reverse": {
                double h = vm.getHorizontal() / 100.0;
                double v = vm.getVertical() / 100.0;
                VELOCITY_LOG.info("[Velocity] Mode=Reverse — h={} v={} assigning motionX={} motionY={} motionZ={}",
                        h, v, -rawX * h, rawY * v, -rawZ * h);
                mc.player.motionX = -rawX * h;
                mc.player.motionY = rawY * v;
                mc.player.motionZ = -rawZ * h;
                ci.cancel();
                break;
            }

            case "JumpReset": {
                double h = vm.getHorizontal() / 100.0;
                VELOCITY_LOG.info("[Velocity] Mode=JumpReset — h={} assigning motionX={} motionY=0.42 motionZ={}",
                        h, rawX * h, rawZ * h);
                mc.player.motionX = rawX * h;
                mc.player.motionY = 0.42;
                mc.player.motionZ = rawZ * h;
                ci.cancel();
                break;
            }

            case "Legit": {
                double h = (vm.getHorizontal() / 100.0) * 0.65;
                double v = (vm.getVertical() / 100.0) * 0.85;
                VELOCITY_LOG.info("[Velocity] Mode=Legit — h={} v={} assigning motionX={} motionY={} motionZ={}",
                        h, v, rawX * h, rawY * v, rawZ * h);
                mc.player.motionX = rawX * h;
                mc.player.motionY = rawY * v;
                mc.player.motionZ = rawZ * h;
                ci.cancel();
                break;
            }

            case "Normal":
            default: {
                double h = vm.getHorizontal() / 100.0;
                double v = vm.getVertical() / 100.0;
                VELOCITY_LOG.info("[Velocity] Mode=Normal — h={} v={} assigning motionX={} motionY={} motionZ={}",
                        h, v, rawX * h, rawY * v, rawZ * h);

                if (h == 0.0 && v == 0.0) {
                    ci.cancel();
                    break;
                }

                mc.player.motionX = rawX * h;
                mc.player.motionY = rawY * v;
                mc.player.motionZ = rawZ * h;
                ci.cancel();
                break;
            }
        }
    }
}