package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.module.impl.VelocityModule;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the private motionX/Y/Z int fields of SPacketEntityVelocity
 * so VelocityModule can modify them in-place before vanilla applies them.
 */
@Mixin(SPacketEntityVelocity.class)
public abstract class MixinSPacketEntityVelocity implements VelocityModule.SPacketEntityVelocityAccessor {

    @Accessor("motionX") public abstract int  getMotionX();
    @Accessor("motionY") public abstract int  getMotionY();
    @Accessor("motionZ") public abstract int  getMotionZ();

    @Mutable @Accessor("motionX") public abstract void setMotionX(int v);
    @Mutable @Accessor("motionY") public abstract void setMotionY(int v);
    @Mutable @Accessor("motionZ") public abstract void setMotionZ(int v);
}
