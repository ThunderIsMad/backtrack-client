package com.yourname.backtrack.util;

import com.yourname.backtrack.SoloBacktrack;
import com.yourname.backtrack.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.server.SPacketPlayerPosLook;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class FlagLogger {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    // Deduplicate: store last logged destination + time
    private static double lastSx = Double.NaN;
    private static double lastSy = Double.NaN;
    private static double lastSz = Double.NaN;
    private static long   lastLogTime = 0;

    public static void log(SoloBacktrack mod, Minecraft mc, SPacketPlayerPosLook packet) {
        try {
            if (mc.player == null) return;

            // Only log when player was recently hit — filters out spawn/arena teleports
            if (mc.player.hurtTime <= 0) return;

            double px = mc.player.posX;
            double py = mc.player.posY;
            double pz = mc.player.posZ;

            double sx = packet.getX();
            double sy = packet.getY();
            double sz = packet.getZ();

            double dist = Math.sqrt(
                    Math.pow(sx - px, 2) +
                    Math.pow(sy - py, 2) +
                    Math.pow(sz - pz, 2)
            );

            // Ignore tiny corrections (normal server sync)
            if (dist < 0.5) return;

            // Deduplicate: same destination within 1 second = double-fire, skip
            long now = System.currentTimeMillis();
            if (now - lastLogTime < 1000
                    && Math.abs(sx - lastSx) < 0.01
                    && Math.abs(sy - lastSy) < 0.01
                    && Math.abs(sz - lastSz) < 0.01) {
                return;
            }

            lastSx = sx; lastSy = sy; lastSz = sz;
            lastLogTime = now;

            File logDir = new File(mc.mcDataDir, "logs");
            if (!logDir.exists()) logDir.mkdirs();
            String fileName = "flaglog-" + DATE_FMT.format(new Date()) + ".txt";
            File logFile = new File(logDir, fileName);

            List<String> active = mod.getModuleManager().getModules().stream()
                    .filter(Module::isEnabled)
                    .map(Module::getName)
                    .collect(Collectors.toList());
            String activeStr = active.isEmpty() ? "none" : String.join(", ", active);

            try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
                pw.println("[" + TIME_FMT.format(new Date()) + "] SETBACK detected");
                pw.println("  Active modules : " + activeStr);
                pw.println("  Player pos     : " + fmt(px) + " " + fmt(py) + " " + fmt(pz));
                pw.println("  Server pos     : " + fmt(sx) + " " + fmt(sy) + " " + fmt(sz));
                pw.println("  Distance       : " + String.format("%.2f", dist) + " blocks");
                pw.println("  Hurt time      : " + mc.player.hurtTime);
                pw.println();
                pw.flush();
            }
        } catch (IOException e) {
            // silently ignore
        }
    }

    private static String fmt(double v) {
        return String.format("%.3f", v);
    }
}
