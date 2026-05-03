package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.Setting;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.network.play.server.SPacketExplosion;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.client.event.InputUpdateEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class VelocityModule extends Module {

    public interface SPacketEntityVelocityAccessor {
        int getMotionX();
        int getMotionY();
        int getMotionZ();
        void setMotionX(int v);
        void setMotionY(int v);
        void setMotionZ(int v);
    }

    public interface SPacketExplosionAccessor {
        float getMotionX();
        float getMotionY();
        float getMotionZ();
        void setMotionX(float v);
        void setMotionY(float v);
        void setMotionZ(float v);
    }

    private final ModeSetting mode = new ModeSetting("Mode",
            Arrays.asList("Modify", "JumpReset", "Reverse", "Push", "Legit", "TickZero"), "JumpReset");

    private final NumberSetting xzModify = new NumberSetting("XZ", 0, 0, 100, 1);
    private final NumberSetting yModify  = new NumberSetting("Y",  0, 0, 100, 1);
    private final BooleanSetting ignoreExplosion = new BooleanSetting("IgnoreExplosion", true);

    private final BooleanSetting fallDamageCheck = new BooleanSetting("FallDamageCheck", true);
    private final BooleanSetting debug          = new BooleanSetting("Debug", false);
    private final NumberSetting  chance         = new NumberSetting("Chance",      80,  1,   100, 1);
    private final NumberSetting  resetTicks     = new NumberSetting("ResetTicks",  3,   1,   8,   1);
    private final NumberSetting  cooldownTicks  = new NumberSetting("Cooldown",    14,  8,   25,  1);
    private final BooleanSetting randomize      = new BooleanSetting("Randomize", false);
    private final NumberSetting  delayMin       = new NumberSetting("DelayMin",    0,   0,   10,  1);
    private final NumberSetting  delayMax       = new NumberSetting("DelayMax",    3,   0,   10,  1);
    private final NumberSetting  maxDistance    = new NumberSetting("MaxDistance",  2.5, 1.0, 6.0, 0.1);

    private final NumberSetting reverseStart = new NumberSetting("ReverseStart", 9, 1, 10, 0);
    private final BooleanSetting reverseStrafe = new BooleanSetting("ReverseStrafe", false);

    private final NumberSetting pushXZ      = new NumberSetting("Push",      1.1, 0.01, 20.0, 2);
    private final NumberSetting pushStart   = new NumberSetting("PushStart", 9.0, 1.0,  10.0, 0);
    private final NumberSetting pushEnd     = new NumberSetting("PushEnd",   2.0, 1.0,  10.0, 0);
    private final BooleanSetting pushOnGround = new BooleanSetting("PushOnGround", false);

    private final BooleanSetting legitStrafe = new BooleanSetting("LegitStrafe", true);

    private final NumberSetting tickZeroHurtTime = new NumberSetting("TickZeroHurtTime", 4, 1, 10, 0);

    private final Random random = new Random();
    private double  prevMotionY    = 0.0;
    private int     ticksSinceHit  = 0;
    private boolean wasHurt        = false;
    private int     cooldownTimer  = 0;
    private boolean isFallDamage   = false;
    private int     currentDelay   = 0;
    private int     delayCounter   = 0;
    private boolean pendingJump    = false;
    private boolean jumpKeyHeld    = false;

    public VelocityModule() {
        super("Velocity", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(new Setting[] { mode,
                xzModify, yModify, ignoreExplosion,
                fallDamageCheck, debug, chance, resetTicks, cooldownTicks,
                randomize, delayMin, delayMax, maxDistance,
                reverseStart, reverseStrafe,
                pushXZ, pushStart, pushEnd, pushOnGround,
                legitStrafe,
                tickZeroHurtTime });
    }

    public boolean handleVelocityPacket(SPacketEntityVelocityAccessor pkt) {
        if (mc().player == null) return false;

        int rawX = pkt.getMotionX();
        int rawY = pkt.getMotionY();
        int rawZ = pkt.getMotionZ();

        double vx = rawX / 8000.0;
        double vy = rawY / 8000.0;
        double vz = rawZ / 8000.0;

        isFallDamage = (rawX == 0 && rawZ == 0 && rawY < 0);

        if (debug.getValue()) {
            sendClientMessage("\u00a7eVel vx=" + String.format("%.3f", vx) +
                    " vy=" + String.format("%.3f", vy) +
                    " vz=" + String.format("%.3f", vz) +
                    " fall=" + isFallDamage);
        }

        String sel = mode.getValue();
        if ("Modify".equals(sel)) {
            double xzFactor = xzModify.getValue() / 100.0;
            double yFactor  = yModify.getValue()  / 100.0;
            if (xzFactor <= 0.0 && yFactor <= 0.0) {
                pkt.setMotionX(0);
                pkt.setMotionY(0);
                pkt.setMotionZ(0);
                return true;
            } else {
                pkt.setMotionX((int) (rawX * xzFactor));
                pkt.setMotionY((int) (rawY * yFactor));
                pkt.setMotionZ((int) (rawZ * xzFactor));
            }
        }

        return false;
    }

    public void notifyVelocityPacket(double vx, double vy, double vz) {
        isFallDamage = (vx == 0.0 && vz == 0.0 && vy < 0);
        if (debug.getValue()) {
            sendClientMessage("\u00a7eNotifyVel vx=" + String.format("%.3f", vx) +
                    " vy=" + String.format("%.3f", vy) +
                    " vz=" + String.format("%.3f", vz) +
                    " fallDmg=" + isFallDamage);
        }
    }

    public void handleExplosion(SPacketExplosion packet) {
        if (!"Modify".equals(mode.getValue()) || !ignoreExplosion.getValue()) return;
        SPacketExplosionAccessor pkt = (SPacketExplosionAccessor) packet;
        double xzFactor = xzModify.getValue() / 100.0;
        double yFactor  = yModify.getValue()  / 100.0;
        pkt.setMotionX((float)(pkt.getMotionX() * xzFactor));
        pkt.setMotionY((float)(pkt.getMotionY() * yFactor));
        pkt.setMotionZ((float)(pkt.getMotionZ() * xzFactor));
    }

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isEnabled() || mc().player == null || mc().world == null) return;

        String sel = mode.getValue();

        if (cooldownTimer > 0) {
            cooldownTimer--;
            releaseJumpKey();
        }

        boolean isHurt = mc().player.hurtTime > 0;
        if (isHurt && !wasHurt) ticksSinceHit = 0;
        if (isHurt) ticksSinceHit++; else ticksSinceHit = 0;
        wasHurt = isHurt;

        boolean justLanded = prevMotionY < -0.01 && mc().player.onGround;
        prevMotionY = mc().player.motionY;

        switch (sel) {
            case "JumpReset":
                handleJumpReset(justLanded);
                break;
            case "Reverse":
                handleReverse();
                break;
            case "Push":
                handlePush();
                break;
            case "Legit":
                handleLegit();
                break;
            case "TickZero":
                handleTickZero();
                break;
        }
    }

    private void handleJumpReset(boolean justLanded) {
        if (pendingJump && mc().player.onGround && isTargetingEnemyInComboRange()) {
            if (randomize.getValue()) {
                delayCounter++;
                if (delayCounter >= currentDelay) {
                    pressJumpKey();
                    pendingJump = false;
                    delayCounter = 0;
                }
            } else {
                pressJumpKey();
                pendingJump = false;
            }
            return;
        }

        if (cooldownTimer > 0) return;
        if (!mc().player.onGround) return;
        if (!justLanded) return;
        if (fallDamageCheck.getValue() && isFallDamage) return;
        if (ticksSinceHit < 1 || ticksSinceHit > (int) resetTicks.getValue()) return;
        if (!isTargetingEnemyInComboRange()) return;
        if (random.nextDouble() * 100 >= chance.getValue()) return;

        if (randomize.getValue()) {
            int lo = (int) delayMin.getValue();
            int hi = (int) Math.max(lo, delayMax.getValue());
            currentDelay = (hi > lo) ? lo + random.nextInt(hi - lo + 1) : lo;
            delayCounter = 0;
            if (currentDelay == 0) {
                pressJumpKey();
            } else {
                pendingJump = true;
            }
        } else {
            pressJumpKey();
        }
    }

    private void pressJumpKey() {
        if (mc().player == null) return;
        KeyBinding.setKeyBindState(mc().gameSettings.keyBindJump.getKeyCode(), true);
        jumpKeyHeld = true;
        cooldownTimer = (int) cooldownTicks.getValue();

        if (debug.getValue()) {
            sendClientMessage("\u00a7aJR \u00a77tick=" + ticksSinceHit +
                    " hurt=" + mc().player.hurtTime +
                    " input=SPACE emulated");
        }
    }

    private void releaseJumpKey() {
        if (jumpKeyHeld) {
            KeyBinding.setKeyBindState(mc().gameSettings.keyBindJump.getKeyCode(), false);
            jumpKeyHeld = false;
        }
    }

    private boolean isTargetingEnemyInComboRange() {
        if (mc().player == null || mc().world == null) return false;

        EntityLivingBase lastAttacked = mc().player.getLastAttackedEntity();
        if (lastAttacked == null || lastAttacked.isDead) return false;

        if (mc().objectMouseOver == null) return false;
        if (mc().objectMouseOver.typeOfHit != RayTraceResult.Type.ENTITY) return false;
        if (!mc().objectMouseOver.entityHit.equals(lastAttacked)) return false;

        double dist = mc().player.getDistance(lastAttacked);
        if (dist > maxDistance.getValue()) return false;

        double dx = lastAttacked.posX - mc().player.posX;
        double dz = lastAttacked.posZ - mc().player.posZ;
        double angleToAttacker = Math.toDegrees(Math.atan2(dz, dx)) - mc().player.rotationYaw;
        angleToAttacker = (angleToAttacker % 360 + 540) % 360 - 180;
        if (Math.abs(angleToAttacker) > 90.0) return false;

        return Mouse.isButtonDown(0);
    }

    private void handleReverse() {
        if (mc().player.hurtTime == (int) reverseStart.getValue()) {
            mc().player.motionX *= -1.0;
            mc().player.motionZ *= -1.0;
            if (reverseStrafe.getValue()) {
                mc().player.motionX *= 0.2;
                mc().player.motionZ *= 0.2;
                float yaw = mc().player.rotationYaw;
                mc().player.motionX -= Math.sin(yaw * Math.PI / 180.0) * 0.2;
                mc().player.motionZ += Math.cos(yaw * Math.PI / 180.0) * 0.2;
            }
        }
    }

    private void handlePush() {
        int start = (int) pushStart.getValue();
        int end   = (int) pushEnd.getValue();
        if (mc().player.hurtTime >= Math.min(start, end) &&
                mc().player.hurtTime <= Math.max(start, end)) {
            float factor = (float)(pushXZ.getValue() / 100.0);
            mc().player.moveRelative(0.0f, 0.0f, factor, 0.98f);
            if (pushOnGround.getValue()) {
                mc().player.onGround = true;
            }
        }
    }

    private void handleLegit() {
        if (mc().player.hurtTime <= 0) return;
        if (!legitStrafe.getValue()) return;
        mc().player.motionX *= 0.6;
        mc().player.motionZ *= 0.6;
    }

    private void handleTickZero() {
        if (mc().player.hurtTime == (int) tickZeroHurtTime.getValue()) {
            mc().player.motionX *= 0.0;
            mc().player.motionZ *= 0.0;
        }
    }

    public void onAttack() {
        if (mc().player == null || mc().player.getLastAttackedEntity() == null) return;
        if (mc().player.getLastAttackedEntity().hurtTime > 0 && mc().player.hurtTime > 0) {
            mc().player.motionX *= 0.6;
            mc().player.motionZ *= 0.6;
            if (debug.getValue()) {
                sendClientMessage("\u00a7dReduceOnAttack \u00a77motionX=" + String.format("%.3f", mc().player.motionX) +
                        " motionZ=" + String.format("%.3f", mc().player.motionZ));
            }
        }
    }

    @Override
    public List<Setting> getVisibleSettings() {
        List<Setting> filtered = new ArrayList<>();
        filtered.add(mode);
        String currentMode = mode.getValue();
        switch (currentMode) {
            case "Modify":
                filtered.add(xzModify);
                filtered.add(yModify);
                filtered.add(ignoreExplosion);
                break;
            case "JumpReset":
                filtered.add(fallDamageCheck);
                filtered.add(debug);
                filtered.add(chance);
                filtered.add(resetTicks);
                filtered.add(cooldownTicks);
                filtered.add(randomize);
                filtered.add(delayMin);
                filtered.add(delayMax);
                filtered.add(maxDistance);
                break;
            case "Reverse":
                filtered.add(reverseStart);
                filtered.add(reverseStrafe);
                break;
            case "Push":
                filtered.add(pushXZ);
                filtered.add(pushStart);
                filtered.add(pushEnd);
                filtered.add(pushOnGround);
                break;
            case "Legit":
                filtered.add(legitStrafe);
                break;
            case "TickZero":
                filtered.add(tickZeroHurtTime);
                break;
        }
        return filtered;
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        releaseJumpKey();
        resetState();
    }

    private void resetState() {
        prevMotionY   = 0.0;
        ticksSinceHit = 0;
        wasHurt       = false;
        cooldownTimer = 0;
        isFallDamage  = false;
        pendingJump   = false;
        delayCounter  = 0;
        currentDelay  = 0;
    }
}