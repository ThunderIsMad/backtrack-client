package com.yourname.backtrack.client;

import com.yourname.backtrack.util.FlagLogger;
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
        sim.simulate();
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onClientTickEnd(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        ClientSimulator sim = ClientSimulator.INSTANCE;

        // During the velocity window, do NOT sync from the player —
        // let the simulator's predicted motion drive the state.
        if (sim.isInVelocityWindow()) {
            // Only update onGround, nothing else.
            sim.updateOnGround(mc);
            sim.prepareNextTick();

            // Detect silent motion corrections while in velocity window
            FlagLogger.INSTANCE.tickCheck(mc);
            return;
        }

        // Normal operation outside the velocity window.
        sim.syncFromPlayer();
        sim.advanceVerifiedFromPlayer(mc);
        sim.updateOnGround(mc);
        sim.prepareNextTick();

        // Always run tick-based checks (sprint resets, motion corrections)
        FlagLogger.INSTANCE.tickCheck(mc);
    }
}