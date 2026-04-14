package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

public class VelocityModule extends Module {

    private final ModeSetting    mode       = new ModeSetting("Mode", Arrays.asList("Normal", "Strict", "AAC"), "Normal");
    private final NumberSetting  horizontal = new NumberSetting("Horizontal", 0.0, 0.0, 100.0, 1.0);
    private final NumberSetting  vertical   = new NumberSetting("Vertical",   0.0, 0.0, 100.0, 1.0);
    private final BooleanSetting onlyOnHit  = new BooleanSetting("Only On Hit", true);

    private int lastAttackTick = -100;

    public VelocityModule() {
        super("Velocity", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(mode, horizontal, vertical, onlyOnHit);
        addHudSettings();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc().player == null || mc().world == null) return;

        if (mc().player.ticksExisted - lastAttackTick < 20) {
            lastAttackTick = mc().player.ticksExisted;
        }
    }

    public boolean shouldCancel() {
        if (!isEnabled() || mc().player == null) return false;

        if (!onlyOnHit.getValue()) return true;

        return mc().player.ticksExisted - lastAttackTick < 40
                || mc().player.hurtResistantTime > 0;
    }

    public void applyVelocity(double x, double y, double z) {
        double finalX = x * (horizontal.getValue() / 100.0);
        double finalY = y * (vertical.getValue()   / 100.0);
        double finalZ = z * (horizontal.getValue() / 100.0);

        switch (mode.getValue()) {
            case "Normal":
                if (mc().player != null) {
                    mc().player.motionX = finalX;
                    mc().player.motionY = finalY;
                    mc().player.motionZ = finalZ;
                }
                break;

            case "Strict":
                if (mc().player != null) {
                    mc().player.motionX = finalX;
                    mc().player.motionY = finalY;
                    mc().player.motionZ = finalZ;
                    mc().player.connection.sendPacket(
                            new CPacketPlayer.Position(
                                    mc().player.posX,
                                    mc().player.posY,
                                    mc().player.posZ,
                                    mc().player.onGround
                            )
                    );
                }
                break;

            case "AAC":
                if (mc().player != null) {
                    mc().player.motionX = finalX;
                    mc().player.motionY = finalY;
                    mc().player.motionZ = finalZ;
                    mc().player.connection.sendPacket(
                            new CPacketPlayer.Position(
                                    mc().player.posX,
                                    mc().player.posY + 0.0001,
                                    mc().player.posZ,
                                    false
                            )
                    );
                }
                break;
        }
    }

    public void setLastAttackTick() {
        if (mc().player != null) {
            lastAttackTick = mc().player.ticksExisted;
        }
    }
}
