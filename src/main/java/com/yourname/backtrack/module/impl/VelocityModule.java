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

/**
 * Velocity — multi-mode knockback reduction.
 *
 * Modes:
 *  Normal      — scale XZ/Y on the incoming velocity packet
 *  Intave      — JumpReset on hurtTime==9 every other hit; XZScale multiplies packet
 *  Reverse     — negate motionX/Z at reverseStart hurtTime
 *  Push        — push player forward in a hurtTime window using yaw direction
 *  PushGround  — force onGround=true while hurt (nullifies aerial KB)
 *  Spoof       — cancel velocity packet, send fake position offset
 *  Strict      — scale + send extra position packet
 *  AAC         — scale + send Y+0.0001 position packet
 *
 * ReduceOnAttack (from LiquidBounce VelocityIntave):
 *  Multiply own XZ when attacking while target is in hurtTime window.
 */
public class VelocityModule extends Module {

    // --- Mode ---
    private final ModeSetting    mode           = new ModeSetting("Mode",
            Arrays.asList("Normal", "Intave", "Reverse", "Push", "PushGround", "Spoof", "Strict", "AAC"),
            "Normal");

    // Normal / Strict / AAC
    private final NumberSetting  horizontal     = new NumberSetting("Horizontal",     0.0,  0.0,  100.0, 1.0);
    private final NumberSetting  vertical       = new NumberSetting("Vertical",       0.0,  0.0,  100.0, 1.0);
    private final BooleanSetting onlyOnHit      = new BooleanSetting("OnlyOnHit",     true);

    // Intave
    private final BooleanSetting intaveJump     = new BooleanSetting("IntaveJump",    true);
    private final NumberSetting  intaveXZScale  = new NumberSetting("IntaveXZScale",  0.6,  0.0,  1.0,   0.05);

    // Reverse
    private final NumberSetting  reverseStart   = new NumberSetting("ReverseStart",   9.0,  1.0,  10.0,  1.0);
    private final BooleanSetting reverseStrafe  = new BooleanSetting("ReverseStrafe",  false);

    // Push — applies forward impulse in the yaw direction during a hurtTime window
    private final NumberSetting  pushStrength   = new NumberSetting("PushStrength",   0.011, 0.001, 0.2, 0.001);
    private final NumberSetting  pushStart      = new NumberSetting("PushStart",      9.0,  1.0,  10.0,  1.0);
    private final NumberSetting  pushEnd        = new NumberSetting("PushEnd",        2.0,  1.0,  10.0,  1.0);
    private final BooleanSetting pushOnGround   = new BooleanSetting("PushOnGround",  false);

    // ReduceOnAttack
    private final BooleanSetting reduceOnAttack = new BooleanSetting("ReduceOnAttack", true);
    private final NumberSetting  reduceFactor   = new NumberSetting("ReduceFactor",   0.6,  0.0,  1.0,   0.05);
    private final NumberSetting  hurtTimeMin    = new NumberSetting("HurtTimeMin",    5,    1,    10,    1);
    private final NumberSetting  hurtTimeMax    = new NumberSetting("HurtTimeMax",    7,    1,    10,    1);
    private final NumberSetting  attackWindowMs = new NumberSetting("AttackWindowMs", 2000, 100,  5000,  100);

    // --- State ---
    private int     lastAttackTick   = -100;
    private long    lastAttackTimeMs = 0L;
    private int     intaveCounter    = 0;
    private boolean spoofPending     = false;
    private double  spoofX, spoofY, spoofZ;

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
        intaveCounter = 0;
        spoofPending  = false;
    }

    // -------------------------------------------------------------------------
    // Packet handling — called by MixinNetHandlerPlayClient
    // -------------------------------------------------------------------------

    /**
     * Called BEFORE vanilla applies the velocity packet.
     * Returns true to cancel the packet entirely, false to let it through
     * (possibly with modified field values).
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
            double h = intaveXZScale.getValue();
            packet.setMotionX((int)(packet.getMotionX() * h));
            packet.setMotionZ((int)(packet.getMotionZ() * h));
            return false;
        }

        if (m.equals("Spoof")) {
            spoofX = packet.getMotionX() / 8000.0;
            spoofY = packet.getMotionY() / 8000.0;
            spoofZ = packet.getMotionZ() / 8000.0;
            spoofPending = true;
            return true; // cancel
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

        // Spoof: send fake position on tick after packet cancel
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
            case "Intave":     handleIntave(event); break;
            case "Reverse":    handleReverse();     break;
            case "Push":       handlePush();        break;
            case "PushGround": handlePushGround();  break;
        }
    }

    private void handleIntave(InputUpdateEvent event) {
        if (!intaveJump.getValue()) return;
        // Fire on hurtTime==9 (first ground tick after KB), every other hit
        if (mc().player.hurtTime == 9 && mc().player.onGround && intaveCounter++ % 2 == 0) {
            event.getMovementInput().jump = true;
        }
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
            // Apply forward impulse in facing direction — 1.12.2 has no moveFlying shorthand
            double rad = Math.toRadians(mc().player.rotationYaw);
            double spd = pushStrength.getValue();
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
            double f = reduceFactor.getValue();
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
        double finalX = x * (horizontal.getValue() / 100.0);
        double finalY = y * (vertical.getValue()   / 100.0);
        double finalZ = z * (horizontal.getValue() / 100.0);
        mc().player.motionX = finalX;
        mc().player.motionY = finalY;
        mc().player.motionZ = finalZ;
        if (mode.getValue().equals("Strict")) {
            mc().player.connection.sendPacket(new CPacketPlayer.Position(
                    mc().player.posX, mc().player.posY, mc().player.posZ, mc().player.onGround));
        } else if (mode.getValue().equals("AAC")) {
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

    // Strafe perpendicular to facing direction
    private void strafe() {
        double yaw = Math.toRadians(mc().player.rotationYaw + 90.0);
        double spd = Math.sqrt(mc().player.motionX * mc().player.motionX
                             + mc().player.motionZ * mc().player.motionZ);
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
