package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.client.settings.KeyBinding;
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
            // Sync key state to actual mouse button on disable
            KeyBinding.setKeyBindState(mc().gameSettings.keyBindAttack.getKeyCode(), Mouse.isButtonDown(0));
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc().player == null || mc().world == null) return;
        if (mc().currentScreen != null) return;

        if (!Mouse.isButtonDown(0)) {
            lastClickTime = 0;
            KeyBinding.setKeyBindState(mc().gameSettings.keyBindAttack.getKeyCode(), false);
            return;
        }

        // nanoTime / 1M = high-res ms; avoids Windows 15ms currentTimeMillis chunks
        // that flag ClickSpeedCheck diff < 20L.
        long currentTime = System.nanoTime() / 1_000_000L;

        // Target Acquisition Hesitation: mimic human reaction when crosshair lands on entity.
        // Breaks Intave's 5-hit timing arrays.
        boolean isLookingAtEntity = (mc().objectMouseOver != null
                && mc().objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY);
        if (isLookingAtEntity && !wasLookingAtEntity) {
            nextDelay += random.nextInt(40) + 20;
        }
        wasLookingAtEntity = isLookingAtEntity;

        // Rotation Sync Safety: Intave flags USE_ENTITY < 23ms after a heavy POSITION_LOOK.
        float yawDiff = Math.abs(mc().player.rotationYaw - lastYaw);
        if (yawDiff > 15.0f && currentTime - lastClickTime < nextDelay) {
            nextDelay += 25;
        }
        lastYaw = mc().player.rotationYaw;

        if (lastClickTime == 0 || currentTime - lastClickTime >= nextDelay) {
            KeyBinding.setKeyBindState(mc().gameSettings.keyBindAttack.getKeyCode(), true);
            KeyBinding.onTick(mc().gameSettings.keyBindAttack.getKeyCode());

            lastClickTime = currentTime;

            double min = Math.max(1.0, minCps.getValue());
            double max = Math.max(min, maxCps.getValue());

            double averageCps  = (min + max) / 2.0;
            double gaussianCps = averageCps + (random.nextGaussian() * ((max - min) / 3.0));
            double finalCps    = Math.max(min, Math.min(max, gaussianCps));

            long dropVariance = (random.nextInt(100) > 95) ? (long)(random.nextDouble() * 45) : 0L;
            nextDelay = (long)(1000.0 / finalCps) + dropVariance;
        } else {
            KeyBinding.setKeyBindState(mc().gameSettings.keyBindAttack.getKeyCode(), false);
        }
    }

    @Override
    public String getHudText() {
        return String.format("AC %.0f-%.0f CPS", minCps.getValue(), maxCps.getValue());
    }
}
