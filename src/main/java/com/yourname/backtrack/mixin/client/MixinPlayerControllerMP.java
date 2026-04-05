package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.module.impl.ReachModule;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerControllerMP.class)
public class MixinPlayerControllerMP {

    @Inject(method = "getBlockReachDistance", at = @At("HEAD"), cancellable = true)
    private void onGetBlockReachDistance(CallbackInfoReturnable<Float> cir) {
        SoloBacktrack mod = SoloBacktrack.getInstance();
        if (mod == null) return;

        mod.getModuleManager().getModules().stream()
            .filter(m -> m instanceof ReachModule && m.isEnabled())
            .map(m -> (ReachModule) m)
            .findFirst()
            .ifPresent(reach -> cir.setReturnValue((float) reach.getReachValue()));
    }
}
