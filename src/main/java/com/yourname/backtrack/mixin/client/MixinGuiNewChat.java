package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.module.impl.VelocityModule;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.util.text.ITextComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiNewChat.class)
public class MixinGuiNewChat {

    @Inject(method = "printChatMessage", at = @At("HEAD"), cancellable = true)
    private void onPrintChatMessage(ITextComponent component, CallbackInfo ci) {
        SoloBacktrack mod = SoloBacktrack.getInstance();
        if (mod == null) return;

        VelocityModule vm = mod.getModuleManager().getModules().stream()
                .filter(m -> m instanceof VelocityModule && m.isEnabled())
                .map(m -> (VelocityModule) m)
                .findFirst()
                .orElse(null);

        if (vm == null || !vm.isDebug()) return;

        String text = component.getUnformattedText();
        // Allow only client-side VelDebug messages through
        if (!text.startsWith("[VelDebug]")) {
            ci.cancel();
        }
    }
}
