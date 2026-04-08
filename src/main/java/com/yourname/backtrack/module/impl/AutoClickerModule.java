package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;

public class AutoClickerModule extends Module {

    private final NumberSetting minCps = new NumberSetting("Min CPS", 10, 1, 20, 1);
    private final NumberSetting maxCps = new NumberSetting("Max CPS", 14, 1, 20, 1);

    // ticks to wait before next click (20 ticks = 1 second)
    private int ticksUntilClick = 0;

    public AutoClickerModule() {
        super("AutoClicker", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(minCps, maxCps);
        addHudSettings();
    }

    @Override
    public void onDisable() {
        ticksUntilClick = 0;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Only fire while left mouse button is held
        if (!Mouse.isButtonDown(0)) {
            ticksUntilClick = 0;
            return;
        }

        // Don't click while a GUI is open
        if (mc.currentScreen != null) return;

        if (ticksUntilClick > 0) {
            ticksUntilClick--;
            return;
        }

        // Must have a target in crosshair
        if (mc.objectMouseOver == null) return;
        if (mc.objectMouseOver.entityHit == null) return;

        // Attack
        mc.playerController.attackEntity(mc.player, mc.objectMouseOver.entityHit);
        mc.player.swingArm(net.minecraft.util.EnumHand.MAIN_HAND);

        // Schedule next click with humanized jitter
        double min = Math.max(1, minCps.getValue());
        double max = Math.max(min, maxCps.getValue());
        double cps = min + Math.random() * (max - min);
        // ticks between clicks = 20 / cps, with ±1 tick noise
        int delay = (int) Math.round(20.0 / cps);
        delay += (int) (Math.random() * 3) - 1;  // -1, 0, or +1 tick jitter
        ticksUntilClick = Math.max(1, delay);
    }

    @Override
    public String getHudText() {
        return String.format("AC %.0f-%.0f CPS", minCps.getValue(), maxCps.getValue());
    }
}
