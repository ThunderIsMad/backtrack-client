package com.yourname.backtrack.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class ClientTickHandler {

    @SubscribeEvent
    public void onClientTickStart(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        ClientSimulator sim = ClientSimulator.INSTANCE;
        sim.captureInput(mc);
        sim.simulate();
    }

    @SubscribeEvent
    public void onClientTickEnd(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        ClientSimulator sim = ClientSimulator.INSTANCE;
        sim.syncFromPlayer();
        sim.prepareNextTick();
    }
}