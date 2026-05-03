package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.module.impl.VelocityModule;
import net.minecraft.network.play.server.SPacketExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SPacketExplosion.class)
public interface MixinSPacketExplosion extends VelocityModule.SPacketExplosionAccessor {

    @Accessor("motionX") float getMotionX();
    @Accessor("motionY") float getMotionY();
    @Accessor("motionZ") float getMotionZ();

    @Accessor("motionX") void setMotionX(float v);
    @Accessor("motionY") void setMotionY(float v);
    @Accessor("motionZ") void setMotionZ(float v);
}