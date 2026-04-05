package com.yourname.backtrack;

import com.yourname.backtrack.module.ModuleManager;
import com.yourname.backtrack.module.impl.ReachModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public class CustomPlayerController extends PlayerControllerMP {

    private final ModuleManager moduleManager;

    public CustomPlayerController(Minecraft mc,
                                  NetHandlerPlayClient netHandler,
                                  ModuleManager moduleManager) {
        super(mc, netHandler);
        this.moduleManager = moduleManager;
    }

    @Override
    public float getBlockReachDistance() {
        return super.getBlockReachDistance();
    }

    @Override
    public void attackEntity(EntityPlayer player, Entity targetEntity) {
        ReachModule reach = getReachModule();

        if (reach != null && reach.isEnabled()) {
            double reachDist = reach.getReachValue();
            double dist = player.getDistance(targetEntity);

            if (dist > 3.0 && dist <= reachDist) {
                double dx = targetEntity.posX - player.posX;
                double dy = targetEntity.posY - player.posY;
                double dz = targetEntity.posZ - player.posZ;
                double scale = (dist - 2.9D) / dist;

                double oldX = player.posX;
                double oldY = player.posY;
                double oldZ = player.posZ;

                player.posX += dx * scale;
                player.posY += dy * scale;
                player.posZ += dz * scale;

                super.attackEntity(player, targetEntity);

                player.posX = oldX;
                player.posY = oldY;
                player.posZ = oldZ;
                return;
            }
        }

        super.attackEntity(player, targetEntity);
    }

    private ReachModule getReachModule() {
        if (moduleManager == null) return null;

        for (com.yourname.backtrack.module.Module m : moduleManager.getModules()) {
            if (m instanceof ReachModule) {
                return (ReachModule) m;
            }
        }

        return null;
    }
}
