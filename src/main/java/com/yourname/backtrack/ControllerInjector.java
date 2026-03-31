package com.yourname.backtrack;

import module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public class ControllerInjector {

    private final ModuleManager moduleManager;
    private boolean injected = false;

    public ControllerInjector(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (injected) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.playerController == null) return;
        if (mc.getConnection() == null) return;

        if (!(mc.playerController instanceof CustomPlayerController)) {
            mc.playerController = new CustomPlayerController(
                    mc,
                    mc.getConnection(),
                    moduleManager
            );
            injected = true;
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        injected = false;
    }
}