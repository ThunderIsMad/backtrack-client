package com.yourname.backtrack.client;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public class SimLifecycle {

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        ClientSimulator.INSTANCE.reset();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        ClientSimulator.INSTANCE.reset();
    }

    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ClientSimulator.INSTANCE.reset();
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.player != null && event.player.world != null && event.player.world.isRemote) {
            ClientSimulator.INSTANCE.reset();
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerLoggedOutEvent event) {
        ClientSimulator.INSTANCE.reset();
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof EntityPlayerSP) {
            ClientSimulator.INSTANCE.reset();
        }
    }
}