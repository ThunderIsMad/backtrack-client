package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;
import java.util.Random;

public class AutoClickerModule extends Module {

    private final NumberSetting minCps = new NumberSetting("Min CPS", 10, 1, 20, 1);
    private final NumberSetting maxCps = new NumberSetting("Max CPS", 14, 1, 20, 1);

    private long lastClickTime = 0;
    private long nextDelay = 0;
    private float lastYaw = 0f;
    private boolean wasLookingAtEntity = false;
    private final Random random = new Random();

    public AutoClickerModule() {
        super("AutoClicker", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(minCps, maxCps);
        addHudSettings();
    }

    @Override
    public void onDisable() {
        lastClickTime = 0;
        nextDelay = 0;
        wasLookingAtEntity = false;
        if (mc().gameSettings != null) {
            mc().gameSettings.keyBindAttack.pressed = Mouse.isButtonDown(0);
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc().player == null || mc().world == null) return;
        if (mc().currentScreen != null) return;

        if (!Mouse.isButtonDown(0)) {
            lastClickTime = 0;
            if (mc().gameSettings.keyBindAttack.pressed) {
                mc().gameSettings.keyBindAttack.pressed = false;
            }
            return;
        }

        // Use nanoTime divided by 1M to get high-resolution milliseconds.
        // System.currentTimeMillis() on Windows jumps in 15ms chunks, which flags ClickSpeedCheck diff < 20L.
        long currentTime = System.nanoTime() / 1_000_000L;

        // Target Acquisition Hesitation: Mimic human reaction time when crosshair lands on an entity
        boolean isLookingAtEntity = (mc().objectMouseOver != null && mc().objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY);
        if (isLookingAtEntity && !wasLookingAtEntity) {
            nextDelay += random.nextInt(40) + 20; // Add 20-60ms hesitation to shatter Intave's 5-hit arrays
        }
        wasLookingAtEntity = isLookingAtEntity;

        // Rotation Sync Safety: Prevent deltaVL flags in Heuristics.java
        // Intave checks if USE_ENTITY is sent < 23ms after a heavy POSITION_LOOK packet.
        float yawDiff = Math.abs(mc().player.rotationYaw - lastYaw);
        if (yawDiff > 15.0f && currentTime - lastClickTime < nextDelay) {
            nextDelay += 25; // Force the click to wait until the rotation packet is fully flushed
        }
        lastYaw = mc().player.rotationYaw;

        if (lastClickTime == 0 || currentTime - lastClickTime >= nextDelay) {
            mc().gameSettings.keyBindAttack.pressed = true;
            net.minecraft.client.settings.KeyBinding.onTick(mc().gameSettings.keyBindAttack.getKeyCode());

            lastClickTime = currentTime;

            double min = Math.max(1.0, minCps.getValue());
            double max = Math.max(min, maxCps.getValue());

            double averageCps = (min + max) / 2.0;
            double gaussianCps = averageCps + (random.nextGaussian() * ((max - min) / 3.0));
            double finalCps = Math.max(min, Math.min(max, gaussianCps));

            long dropVariance = (random.nextInt(100) > 95) ? (long)(random.nextDouble() * 45) : 0L;
            nextDelay = (long) (1000.0 / finalCps) + dropVariance;

        } else {
            mc().gameSettings.keyBindAttack.pressed = false;
        }
    }

    @Override
    public String getHudText() {
        return String.format("AC %.0f-%.0f CPS", minCps.getValue(), maxCps.getValue());
    }
}
