package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class VelocityModule extends Module {

    private final ModeSetting   mode       = new ModeSetting(
            "Mode",
            Arrays.asList("Normal", "Cancel", "Reverse", "JumpReset", "Legit", "GroundStrafe"),
            "Normal"
    );
    private final NumberSetting  horizontal = new NumberSetting("Horizontal", 52, 0, 100, 1);
    private final NumberSetting  vertical   = new NumberSetting("Vertical",   100, 0, 100, 1);
    private final NumberSetting  chance     = new NumberSetting("Chance",     100, 0, 100, 1);
    private final BooleanSetting debug      = new BooleanSetting("Debug", false);

    // GroundStrafe state
    private volatile boolean pendingStrafe = false;
    private volatile double  strafeYaw     = 0.0;
    private boolean          registered    = false;

    private volatile double lastRawX, lastRawY, lastRawZ;
    private volatile double lastAppliedX, lastAppliedY, lastAppliedZ;

    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");
    private PrintWriter logWriter = null;

    public VelocityModule() {
        super("Velocity", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(mode, horizontal, vertical, chance, debug);
        addHudSettings();
    }

    @Override
    public void onEnable() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(this);
            registered = true;
        }
        if (debug.getValue()) openLog();
    }

    @Override
    public void onDisable() {
        if (registered) {
            MinecraftForge.EVENT_BUS.unregister(this);
            registered = false;
        }
        pendingStrafe = false;
        closeLog();
    }

    /**
     * Called by the mixin when a velocity packet arrives in GroundStrafe mode.
     * Stores the KB direction — no motion fields are touched in the mixin.
     */
    public void notifyKnockback(double rawX, double rawZ) {
        if (Math.sqrt(rawX * rawX + rawZ * rawZ) < 0.001) return;
        strafeYaw     = Math.toDegrees(Math.atan2(-rawX, -rawZ));
        pendingStrafe = true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled()) return;
        if (!"GroundStrafe".equals(mode.getValue())) return;
        if (!pendingStrafe) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) return;
        if (!mc.player.onGround) return;

        pendingStrafe = false;

        double strength = (horizontal.getValue() / 100.0) * 0.18;
        double yawRad   = Math.toRadians(strafeYaw);

        mc.player.motionX += Math.sin(yawRad) * strength;
        mc.player.motionZ -= Math.cos(yawRad) * strength;
    }

    public String getMode()       { return mode.getValue(); }
    public double getHorizontal() { return horizontal.getValue(); }
    public double getVertical()   { return vertical.getValue(); }
    public double getChance()     { return chance.getValue(); }
    public boolean isDebug()      { return debug.getValue(); }

    public void recordPacket(double rawX, double rawY, double rawZ,
                             double appX, double appY, double appZ) {
        lastRawX = rawX; lastRawY = rawY; lastRawZ = rawZ;
        lastAppliedX = appX; lastAppliedY = appY; lastAppliedZ = appZ;

        if (!debug.getValue()) return;

        double rawH = Math.sqrt(rawX * rawX + rawZ * rawZ);
        double appH = Math.sqrt(appX * appX + appZ * appZ);
        int pctH = rawH > 0.0001 ? (int) Math.round((appH / rawH) * 100) : 0;
        int pctY = Math.abs(rawY) > 0.0001 ? (int) Math.round((Math.abs(appY) / Math.abs(rawY)) * 100) : 0;

        String line = String.format("[%s] H: %.4f->%.4f (%d%%)  Y: %.4f->%.4f (%d%%)",
                TIME_FMT.format(new Date()),
                rawH, appH, pctH,
                rawY, appY, pctY);

        if (logWriter != null) {
            logWriter.println(line);
            logWriter.flush();
        }
    }

    private void openLog() {
        try {
            File logDir  = new File(Minecraft.getMinecraft().mcDataDir, "logs");
            if (!logDir.exists()) logDir.mkdirs();
            File logFile = new File(logDir, "velocity-debug.log");
            logWriter = new PrintWriter(new FileWriter(logFile, true));
            String header = String.format(
                    "\n=== Session %s  Mode:%s H:%.0f V:%.0f C:%.0f ===",
                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),
                    mode.getValue(), horizontal.getValue(),
                    vertical.getValue(), chance.getValue());
            logWriter.println(header);
            logWriter.flush();
        } catch (IOException e) {
            // silently ignore
        }
    }

    private void closeLog() {
        if (logWriter != null) {
            logWriter.println("=== Session ended ===");
            logWriter.flush();
            logWriter.close();
            logWriter = null;
        }
    }

    @Override
    public String getHudText() {
        return String.format("Vel %s H:%.0f V:%.0f C:%.0f",
                mode.getValue(), horizontal.getValue(), vertical.getValue(), chance.getValue());
    }
}
