package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.module.ModuleManager;
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
        ReachModule reachModule = ModuleManager.getModule(ReachModule.class);
        if (reachModule != null && reachModule.isEnabled()) {
            cir.setReturnValue((float) reachModule.getReachValue());
        }
    }
}
