package com.yourname.backtrack.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class ClientTickHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onClientTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        ClientSimulator sim = ClientSimulator.INSTANCE;
        sim.beginTick();
        sim.syncVerifiedFromPlayer(mc);
        sim.predictFlyingPacketBeforeVelocity();
        sim.simulate(); // internally calls inputCapture.capture()
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onClientTickEnd(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        ClientSimulator sim = ClientSimulator.INSTANCE;
        sim.syncFromPlayer();
        sim.advanceVerifiedFromPlayer(mc);
        sim.prepareNextTick();
    }
}