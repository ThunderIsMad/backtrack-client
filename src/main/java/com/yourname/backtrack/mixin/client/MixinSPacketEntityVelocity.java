package com.yourname.backtrack.mixin.client;

import com.yourname.backtrack.module.impl.VelocityModule;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes private motionX/Y/Z int fields of SPacketEntityVelocity.
 * @Mutable is a field-level annotation — setters use @Accessor alone.
 */
@Mixin(SPacketEntityVelocity.class)
public abstract class MixinSPacketEntityVelocity implements VelocityModule.SPacketEntityVelocityAccessor {

    @Accessor("motionX") public abstract int  getMotionX();
    @Accessor("motionY") public abstract int  getMotionY();
    @Accessor("motionZ") public abstract int  getMotionZ();

    // Setter @Accessor methods must NOT have @Mutable — @Mutable is for field declarations only
    @Accessor("motionX") public abstract void setMotionX(int v);
    @Accessor("motionY") public abstract void setMotionY(int v);
    @Accessor("motionZ") public abstract void setMotionZ(int v);
}
