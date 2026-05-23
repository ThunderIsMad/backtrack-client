package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.client.ClientSimulator;
import com.yourname.backtrack.module.impl.VelocityModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerControllerMP.class)
public class MixinPlayerControllerMP {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void onAttackEntity(EntityPlayer player, Entity targetEntity, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        
        if (targetEntity instanceof EntityLivingBase) {
            ClientSimulator.INSTANCE.onAttack(player, (EntityLivingBase) targetEntity);
        }

        SoloBacktrack mod = SoloBacktrack.getInstance();
        if (mod == null) return;
        VelocityModule vm = mod.getModuleManager().getModule(VelocityModule.class);
        if (vm != null && vm.isEnabled()) {
            vm.onAttack();
        }
    }
}