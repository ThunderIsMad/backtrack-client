package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraftforge.client.event.InputUpdateEvent;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

/**
 * Velocity — multi-mode knockback reduction.
 *
 * Modes:
 *  Normal      — scale XZ/Y on the incoming velocity packet
 *  Intave      — XZ scale on packet + jump on landing while hurt
 *  Reverse     — negate motionX/Z at reverseStart hurtTime
 *  Push        — push forward using yaw direction in a hurtTime window
 *  PushGround  — force onGround=true while hurt (nullifies aerial KB)
 *  Spoof       — cancel velocity packet, send fake position offset
 *  Strict      — scale + send extra position packet
 *  AAC         — scale + send Y+0.0001 position packet
 */
public class VelocityModule extends Module {

    private final ModeSetting    mode          = new ModeSetting("Mode",
            Arrays.asList("Normal", "Intave", "Reverse", "Push", "PushGround", "Spoof", "Strict", "AAC"),
            "Normal");

    // Normal / Strict / AAC — 0 = cancel, 100 = full
    private final NumberSetting  horizontal    = new NumberSetting("Horizontal",    0,   0, 100, 1);
    private final NumberSetting  vertical      = new NumberSetting("Vertical",      0,   0, 100, 1);
    private final BooleanSetting onlyOnHit     = new BooleanSetting("OnlyOnHit",    true);

    // Intave
    private final BooleanSetting intaveJump    = new BooleanSetting("IntaveJump",   true);
    // 0..100 stored as int, divided by 100 when used
    private final NumberSetting  intaveXZScale = new NumberSetting("IntaveXZScale", 60,  0, 100, 5);

    // Reverse
    private final NumberSetting  reverseStart  = new NumberSetting("ReverseStart",  9,   1,  10, 1);
    private final BooleanSetting reverseStrafe = new BooleanSetting("ReverseStrafe", false);

    // Push
    private final NumberSetting  pushStrength  = new NumberSetting("PushStrength",  1,   1,  20, 1); // x0.001 when applied
    private final NumberSetting  pushStart     = new NumberSetting("PushStart",     9,   1,  10, 1);
    private final NumberSetting  pushEnd       = new NumberSetting("PushEnd",       2,   1,  10, 1);
    private final BooleanSetting pushOnGround  = new BooleanSetting("PushOnGround",  false);

    // ReduceOnAttack
    private final BooleanSetting reduceOnAttack = new BooleanSetting("ReduceOnAttack", true);
    private final NumberSetting  reduceFactor   = new NumberSetting("ReduceFactor",   60,  0, 100, 5); // x0.01 when applied
    private final NumberSetting  hurtTimeMin    = new NumberSetting("HurtTimeMin",    5,   1,  10, 1);
    private final NumberSetting  hurtTimeMax    = new NumberSetting("HurtTimeMax",    7,   1,  10, 1);
    private final NumberSetting  attackWindowMs = new NumberSetting("AttackWindowMs", 2000, 100, 5000, 100);

    // --- State ---
    private int     lastAttackTick   = -100;
    private long    lastAttackTimeMs = 0L;
    private int     intaveCounter    = 0;
    private boolean spoofPending     = false;
    private double  spoofX, spoofY, spoofZ;
    // Intave jump state
    private double  prevMotionY      = 0.0;
    private boolean wasHurt          = false;
    private int     intaveCooldown   = 0;

    public VelocityModule() {
        super("Velocity", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(
                mode,
                horizontal, vertical, onlyOnHit,
                intaveJump, intaveXZScale,
                reverseStart, reverseStrafe,
                pushStrength, pushStart, pushEnd, pushOnGround,
                reduceOnAttack, reduceFactor, hurtTimeMin, hurtTimeMax, attackWindowMs
        );
        addHudSettings();
    }

    @Override
    public void onDisable() {
        intaveCounter  = 0;
        spoofPending   = false;
        intaveCooldown = 0;
        wasHurt        = false;
    }

    // -------------------------------------------------------------------------
    // Packet handling — called by MixinNetHandlerPlayClient
    // -------------------------------------------------------------------------

    /**
     * Called BEFORE vanilla applies the velocity packet.
     * Returns true to cancel the packet entirely, false to let it through.
     */
    public boolean handleVelocityPacket(SPacketEntityVelocityAccessor packet) {
        if (!isEnabled() || mc().player == null) return false;

        String m = mode.getValue();

        if (m.equals("Normal") || m.equals("Strict") || m.equals("AAC")) {
            double h = horizontal.getValue() / 100.0;
            double v = vertical.getValue()   / 100.0;
            if (h == 0.0 && v == 0.0) return true;
            packet.setMotionX((int)(packet.getMotionX() * h));
            packet.setMotionY((int)(packet.getMotionY() * v));
            packet.setMotionZ((int)(packet.getMotionZ() * h));
            return false;
        }

        if (m.equals("Intave")) {
            double h = intaveXZScale.getValue() / 100.0;
            packet.setMotionX((int)(packet.getMotionX() * h));
            packet.setMotionZ((int)(packet.getMotionZ() * h));
            return false;
        }

        if (m.equals("Spoof")) {
            spoofX = packet.getMotionX() / 8000.0;
            spoofY = packet.getMotionY() / 8000.0;
            spoofZ = packet.getMotionZ() / 8000.0;
            spoofPending = true;
            return true;
        }

        return false;
    }

    // -------------------------------------------------------------------------
    // Per-tick logic — onInputUpdate
    // -------------------------------------------------------------------------

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isEnabled() || mc().player == null || mc().world == null) return;

        String m = mode.getValue();

        if (spoofPending) {
            mc().player.connection.sendPacket(new CPacketPlayer.Position(
                    mc().player.posX + spoofX,
                    mc().player.posY + spoofY,
                    mc().player.posZ + spoofZ,
                    false
            ));
            spoofPending = false;
        }

        switch (m) {
            case "Intave":     handleIntave(); break;
            case "Reverse":    handleReverse(); break;
            case "Push":       handlePush();    break;
            case "PushGround": handlePushGround(); break;
        }

        prevMotionY = mc().player.motionY;
    }

    private void handleIntave() {
        if (!intaveJump.getValue()) return;
        if (intaveCooldown > 0) { intaveCooldown--; return; }

        boolean isHurt    = mc().player.hurtTime > 0;
        boolean landed    = prevMotionY < -0.01 && mc().player.onGround;

        // Jump on landing while hurt, every other hit
        if (isHurt && landed && intaveCounter++ % 2 == 0) {
            mc().player.motionY    = 0.42;
            mc().player.isAirBorne = true;
            intaveCooldown = 14;
        }
        wasHurt = isHurt;
    }

    private void handleReverse() {
        int ht = mc().player.hurtTime;
        if (ht == (int) reverseStart.getValue()) {
            mc().player.motionX *= -1.0;
            mc().player.motionZ *= -1.0;
        }
        if (reverseStrafe.getValue() && ht > 0 && ht <= (int) reverseStart.getValue()) {
            strafe();
        }
    }

    private void handlePush() {
        int ht = mc().player.hurtTime;
        int lo = (int) Math.min(pushStart.getValue(), pushEnd.getValue());
        int hi = (int) Math.max(pushStart.getValue(), pushEnd.getValue());
        if (ht >= lo && ht <= hi) {
            double rad = Math.toRadians(mc().player.rotationYaw);
            double spd = pushStrength.getValue() * 0.001;
            mc().player.motionX += -Math.sin(rad) * spd;
            mc().player.motionZ +=  Math.cos(rad) * spd;
            if (pushOnGround.getValue()) mc().player.onGround = true;
        }
    }

    private void handlePushGround() {
        if (mc().player.hurtTime > 0) {
            mc().player.onGround = true;
        }
    }

    // -------------------------------------------------------------------------
    // ReduceOnAttack — called by MixinPlayerControllerMP
    // -------------------------------------------------------------------------

    public void onAttack() {
        if (!isEnabled() || mc().player == null) return;
        if (!reduceOnAttack.getValue()) return;

        int  ht  = mc().player.hurtTime;
        int  lo  = (int) hurtTimeMin.getValue();
        int  hi  = (int) hurtTimeMax.getValue();
        long now = System.currentTimeMillis();

        if (ht >= lo && ht <= hi && (now - lastAttackTimeMs) <= (long) attackWindowMs.getValue()) {
            double f = reduceFactor.getValue() / 100.0;
            mc().player.motionX *= f;
            mc().player.motionZ *= f;
        }
        lastAttackTimeMs = now;
        setLastAttackTick();
    }

    // -------------------------------------------------------------------------
    // Legacy applyVelocity — kept for mixin backward compat
    // -------------------------------------------------------------------------

    public void applyVelocity(double x, double y, double z) {
        if (mc().player == null) return;
        double h = horizontal.getValue() / 100.0;
        double v = vertical.getValue()   / 100.0;
        mc().player.motionX = x * h;
        mc().player.motionY = y * v;
        mc().player.motionZ = z * h;
        String m = mode.getValue();
        if (m.equals("Strict")) {
            mc().player.connection.sendPacket(new CPacketPlayer.Position(
                    mc().player.posX, mc().player.posY, mc().player.posZ, mc().player.onGround));
        } else if (m.equals("AAC")) {
            mc().player.connection.sendPacket(new CPacketPlayer.Position(
                    mc().player.posX, mc().player.posY + 0.0001, mc().player.posZ, false));
        }
    }

    public boolean shouldCancel() {
        if (!isEnabled() || mc().player == null) return false;
        if (!onlyOnHit.getValue()) return true;
        return mc().player.ticksExisted - lastAttackTick < 40
                || mc().player.hurtResistantTime > 0;
    }

    public void setLastAttackTick() {
        if (mc().player != null) lastAttackTick = mc().player.ticksExisted;
    }

    private void strafe() {
        double yaw = Math.toRadians(mc().player.rotationYaw + 90.0);
        double spd = Math.sqrt(
                mc().player.motionX * mc().player.motionX +
                mc().player.motionZ * mc().player.motionZ);
        mc().player.motionX = Math.cos(yaw) * spd;
        mc().player.motionZ = Math.sin(yaw) * spd;
    }

    // Accessor interface implemented by MixinSPacketEntityVelocity
    public interface SPacketEntityVelocityAccessor {
        int  getMotionX();
        int  getMotionY();
        int  getMotionZ();
        void setMotionX(int v);
        void setMotionY(int v);
        void setMotionZ(int v);
    }
}
