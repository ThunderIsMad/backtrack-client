package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.Setting;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.client.CPacketEntityAction;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * WTap – break sprint to increase knockback, Intave‑proof.
 */
public class WTapModule extends Module {

    private final ModeSetting mode = new ModeSetting("Mode",
            java.util.Arrays.asList("Packet", "SprintTap", "MoveBlock"), "Packet");

    private final NumberSetting chance = new NumberSetting("Chance", 80, 0, 100, 1);
    private final NumberSetting hurtTimeThreshold = new NumberSetting("HurtTimeThreshold", 10, 0, 10, 1);
    private final BooleanSetting onlyOnGround = new BooleanSetting("OnlyOnGround", true);
    private final BooleanSetting onlyFacing = new BooleanSetting("OnlyFacing", true);
    private final BooleanSetting notInWater = new BooleanSetting("NotInWater", true);

    private final NumberSetting packetDelayMin = new NumberSetting("PacketDelayMin", 1, 0, 4, 1);
    private final NumberSetting packetDelayMax = new NumberSetting("PacketDelayMax", 2, 0, 4, 1);

    private final NumberSetting stopTicksMin = new NumberSetting("StopTicksMin", 0, 0, 10, 1);
    private final NumberSetting stopTicksMax = new NumberSetting("StopTicksMax", 1, 0, 10, 1);
    private final NumberSetting reSprintDelayMin = new NumberSetting("ReSprintDelayMin", 1, 0, 10, 1);
    private final NumberSetting reSprintDelayMax = new NumberSetting("ReSprintDelayMax", 2, 0, 10, 1);

    private final NumberSetting moveBlockStartMin = new NumberSetting("MoveBlockStartMin", 0, 0, 3, 1);
    private final NumberSetting moveBlockStartMax = new NumberSetting("MoveBlockStartMax", 1, 0, 3, 1);
    private final NumberSetting moveBlockDurationMin = new NumberSetting("MoveBlockDurationMin", 0, 0, 3, 1);
    private final NumberSetting moveBlockDurationMax = new NumberSetting("MoveBlockDurationMax", 2, 0, 3, 1);

    private final Random random = new Random();
    private int phase;
    private int subTimer;
    private boolean cancelSprint;
    private boolean blockMovement;
    private int lastHurtTime;

    public WTapModule() {
        super("WTap", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(mode, chance, hurtTimeThreshold,
                onlyOnGround, onlyFacing, notInWater,
                packetDelayMin, packetDelayMax,
                stopTicksMin, stopTicksMax, reSprintDelayMin, reSprintDelayMax,
                moveBlockStartMin, moveBlockStartMax, moveBlockDurationMin, moveBlockDurationMax);
        addHudSettings();
    }

    @Override
    public void onClientTick() {
        if (!isEnabled() || mc().player == null || mc().world == null) return;

        int hurtTime = mc().player.hurtTime;
        if (hurtTime > 0 && lastHurtTime == 0) {
            onPlayerHit();
        }

        String m = mode.getValue();
        switch (m) {
            case "Packet": handlePacket(); break;
            case "SprintTap": handleSprintTap(); break;
            case "MoveBlock": handleMoveBlock(); break;
        }
        lastHurtTime = hurtTime;
    }

    private void onPlayerHit() {
        if (!meetsConditions()) return;
        if (random.nextInt(100) >= (int) chance.getValue()) return;

        switch (mode.getValue()) {
            case "Packet":
                phase = 1; subTimer = 0; break;
            case "SprintTap":
                phase = 1; subTimer = 0; cancelSprint = true; break;
            case "MoveBlock":
                phase = 1; subTimer = 0; break;
        }
    }

    private boolean meetsConditions() {
        if (mc().player == null) return false;
        if (onlyOnGround.getValue() && !mc().player.onGround) return false;
        if (notInWater.getValue() && (mc().player.isInWater() || mc().player.isInLava())) return false;

        EntityLivingBase target = mc().player.getLastAttackedEntity();
        if (target == null) return false;

        if (onlyFacing.getValue()) {
            double dx = mc().player.posX - target.posX;
            double dz = mc().player.posZ - target.posZ;
            double angle = Math.toDegrees(Math.atan2(dz, dx)) - target.rotationYawHead;
            angle = (angle % 360 + 540) % 360 - 180;
            if (Math.abs(angle) > 90) return false;
        }

        if (target.hurtTime > (int) hurtTimeThreshold.getValue()) return false;
        return true;
    }

    private void handlePacket() {
        if (phase == 0) return;
        if (phase == 1) {
            subTimer++;
            int delay = randomBetween(packetDelayMin, packetDelayMax);
            if (subTimer >= delay) {
                if (mc().getConnection() != null) {
                    mc().getConnection().sendPacket(
                            new CPacketEntityAction(mc().player, CPacketEntityAction.Action.STOP_SPRINTING));
                    mc().getConnection().sendPacket(
                            new CPacketEntityAction(mc().player, CPacketEntityAction.Action.START_SPRINTING));
                    mc().player.setSprinting(true);
                }
                phase = 0;
            }
        }
    }

    private void handleSprintTap() {
        if (phase == 0) {
            cancelSprint = false;
            return;
        }
        switch (phase) {
            case 1:
                subTimer++;
                int wait = randomBetween(stopTicksMin, stopTicksMax);
                if (subTimer >= wait) {
                    phase = 2;
                    subTimer = 0;
                }
                break;
            case 2:
                mc().player.setSprinting(false);
                phase = 3;
                subTimer = 0;
                break;
            case 3:
                subTimer++;
                int delay = randomBetween(reSprintDelayMin, reSprintDelayMax);
                if (subTimer >= delay) {
                    phase = 0;
                    cancelSprint = false;
                }
                break;
        }
        if (cancelSprint && mc().player.isSprinting()) {
            mc().player.setSprinting(false);
        }
    }

    private void handleMoveBlock() {
        if (phase == 0) {
            blockMovement = false;
            return;
        }
        switch (phase) {
            case 1:
                subTimer++;
                int delay = randomBetween(moveBlockStartMin, moveBlockStartMax);
                if (subTimer >= delay) {
                    phase = 2;
                    subTimer = 0;
                    blockMovement = true;
                }
                break;
            case 2:
                subTimer++;
                int dur = randomBetween(moveBlockDurationMin, moveBlockDurationMax);
                if (subTimer >= dur) {
                    phase = 0;
                    blockMovement = false;
                }
                break;
        }
        if (blockMovement) {
            mc().player.motionX = 0.0;
            mc().player.motionZ = 0.0;
            mc().player.setSprinting(false);
        }
    }

    private int randomBetween(NumberSetting min, NumberSetting max) {
        int lo = (int) min.getValue();
        int hi = (int) max.getValue();
        if (hi <= lo) return lo;
        return lo + random.nextInt(hi - lo + 1);
    }

    @Override
    public void onEnable() { reset(); }
    @Override
    public void onDisable() { reset(); }

    private void reset() {
        phase = 0; subTimer = 0;
        cancelSprint = false; blockMovement = false;
    }

    @Override
    public List<Setting> getVisibleSettings() {
        List<Setting> f = new ArrayList<>();
        f.add(mode); f.add(chance); f.add(hurtTimeThreshold);
        f.add(onlyOnGround); f.add(onlyFacing); f.add(notInWater);
        String m = mode.getValue();
        switch (m) {
            case "Packet":
                f.add(packetDelayMin); f.add(packetDelayMax);
                break;
            case "SprintTap":
                f.add(stopTicksMin); f.add(stopTicksMax);
                f.add(reSprintDelayMin); f.add(reSprintDelayMax);
                break;
            case "MoveBlock":
                f.add(moveBlockStartMin); f.add(moveBlockStartMax);
                f.add(moveBlockDurationMin); f.add(moveBlockDurationMax);
                break;
        }
        return f;
    }
}