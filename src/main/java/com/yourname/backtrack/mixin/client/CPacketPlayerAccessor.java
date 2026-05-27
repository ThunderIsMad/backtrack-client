package com.yourname.backtrack.mixin.client;

import net.minecraft.network.play.client.CPacketPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CPacketPlayer.class)
public interface CPacketPlayerAccessor {
    @Accessor("x") double getX();
    @Accessor("y") double getY();
    @Accessor("z") double getZ();
}
