package com.yourname.backtrack.client

import net.minecraft.client.entity.EntityPlayerSP
import net.minecraftforge.event.entity.living.LivingDeathEvent
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent
import net.minecraftforge.fml.common.network.FMLNetworkEvent

/**
 * Resets the [ClientSimulator] on lifecycle events so stale state
 * never survives a world change, disconnection, or player death.
 */
object SimLifecycle {

    @SubscribeEvent
    fun onWorldUnload(event: WorldEvent.Unload) {
        ClientSimulator.reset()
    }

    @SubscribeEvent
    fun onWorldLoad(event: WorldEvent.Load) {
        ClientSimulator.reset()
    }

    @SubscribeEvent
    fun onClientDisconnect(event: FMLNetworkEvent.ClientDisconnectionFromServerEvent) {
        ClientSimulator.reset()
    }

    @SubscribeEvent
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        if (event.player?.world?.isRemote == true) {
            ClientSimulator.reset()
        }
    }

    @SubscribeEvent
    fun onPlayerLogout(event: PlayerLoggedOutEvent) {
        ClientSimulator.reset()
    }

    @SubscribeEvent
    fun onPlayerDeath(event: LivingDeathEvent) {
        if (event.entity is EntityPlayerSP) {
            ClientSimulator.reset()
        }
    }
}