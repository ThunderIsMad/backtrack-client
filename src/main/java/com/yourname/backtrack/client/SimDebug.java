package com.yourname.backtrack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;

public final class SimDebug {
    public static boolean enabled = false;
    public static boolean logToChat = false;
    public static boolean logShadowMismatch = true;

    public static void logTick(ClientSimulator sim) {
        if (!enabled) return;
        MovementSimState s = sim.state();
        Minecraft mc = Minecraft.getMinecraft();

        if (sim.shadowMode && logShadowMismatch && mc.player != null) {
            double dx = Math.abs(mc.player.motionX - s.predictedMotionX);
            double dy = Math.abs(mc.player.motionY - s.predictedMotionY);
            double dz = Math.abs(mc.player.motionZ - s.predictedMotionZ);
            if (dx > s.toleranceXZ || dz > s.toleranceXZ || dy > s.toleranceY) {
                String warn = String.format(
                        "[Sim:shadow] diff=(%.4f,%.4f,%.4f) tol=(%.4f,%.4f) pev=%d",
                        dx, dy, dz, s.toleranceXZ, s.toleranceY, s.pastExternalVelocity);
                if (logToChat) {
                    mc.player.sendMessage(new TextComponentString(warn));
                }
            }
        }

        String line = String.format(
                "[Sim] pev=%d pvel=%d ppra=%d flyVL=%d tolXZ=%.4f tolY=%.4f exp=(%.3f,%.3f,%.3f) base=(%.3f,%.3f,%.3f) og=%d ch=%d",
                s.pastExternalVelocity, s.pastVelocity, s.pastPlayerReduceAttackPhysics,
                s.physicsPacketRelinkFlyVL, s.toleranceXZ, s.toleranceY,
                s.predictedMotionX, s.predictedMotionY, s.predictedMotionZ,
                s.baseMotionX, s.baseMotionY, s.baseMotionZ,
                s.onGround ? 1 : 0, s.collidedHorizontally ? 1 : 0);
        if (logToChat && mc.player != null) {
            mc.player.sendMessage(new TextComponentString(line));
        }
    }
}
