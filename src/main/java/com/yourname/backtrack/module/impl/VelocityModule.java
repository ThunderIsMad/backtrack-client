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
import java.util.Random;

/**
 * Velocity — full Augustus logic adapted for backtrack-client.
 *
 * Modes: Basic, Legit, PushGround, Push, Intave, Reverse, Spoof
 *
 * Packet handling (Basic / Spoof) is done in MixinNetHandlerPlayClient
 * via handleVelocityPacket(). Per-tick logic runs in onInputUpdate().
 *
 * Augustus EventSilentMove  → onInputUpdate (pre-motion, before pos packet)
 * Augustus EventUpdate       → onInputUpdate tick logic
 * Augustus EventReadPacket   → MixinNetHandlerPlayClient
 * Augustus moveFlying()      → manual yaw impulse (not in 1.12.2 MCP)
 */
public class VelocityModule extends Module {

    // --- Settings (all NumberSetting args are int, matching existing modules) ---

    private final ModeSetting    mode          = new ModeSetting("Mode",
            Arrays.asList("Basic", "Legit", "PushGround", "Push", "Intave", "Reverse", "Spoof"),
            "Basic");

    // Basic
    private final NumberSetting  xzVelocity    = new NumberSetting("XZVelocity",    20,  0, 100, 1);
    private final NumberSetting  yVelocity     = new NumberSetting("YVelocity",     20,  0, 100, 1);
    private final BooleanSetting ignoreExplosion = new BooleanSetting("Explosion",  true);

    // Intave
    // XZVelocity for packet scale: 0..100 stored as int, -100..100 semantics kept
    // by using signed range; divide by 100 when applied
    private final NumberSetting  xzValueIntave = new NumberSetting("IntaveXZScale", 60, -100, 100, 1);
    private final BooleanSetting jumpIntave    = new BooleanSetting("Jump",         false);

    // Push — forward impulse using yaw direction
    // pushXZ * 0.01 = actual speed added per tick (range 0.01..0.20)
    private final NumberSetting  pushXZ        = new NumberSetting("Push",          11,  1, 200, 1);
    private final NumberSetting  pushStart     = new NumberSetting("PushStart",     9,   1,  10, 1);
    private final NumberSetting  pushEnd       = new NumberSetting("PushEnd",       2,   1,  10, 1);
    private final BooleanSetting pushOnGround  = new BooleanSetting("OnGround",     false);

    // Reverse
    private final NumberSetting  reverseStart  = new NumberSetting("ReverseStart",  9,   1,  10, 1);
    private final BooleanSetting reverseStrafe = new BooleanSetting("ReverseStrafe", false);

    // ReduceOnAttack (LiquidBounce VelocityIntave)
    private final BooleanSetting reduceOnAttack  = new BooleanSetting("ReduceOnAttack", true);
    private final NumberSetting  reduceFactor     = new NumberSetting("ReduceFactor",   60,  0, 100, 5);
    private final NumberSetting  hurtTimeMin      = new NumberSetting("HurtTimeMin",    5,   1,  10, 1);
    private final NumberSetting  hurtTimeMax      = new NumberSetting("HurtTimeMax",    7,   1,  10, 1);
    private final NumberSetting  attackWindowMs   = new NumberSetting("AttackWindowMs", 2000, 100, 5000, 100);

    // --- State ---
    private int     counter          = 0;   // Intave jump every-other-hit counter
    private boolean spoofPending     = false;
    private double  spoofX, spoofY, spoofZ;
    private long    lastAttackTimeMs = 0L;
    private int     lastAttackTick   = -100;

    public VelocityModule() {
        super("Velocity", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(
                mode,
                xzVelocity, yVelocity, ignoreExplosion,
                xzValueIntave, jumpIntave,
                pushXZ, pushStart, pushEnd, pushOnGround,
                reverseStart, reverseStrafe,
                reduceOnAttack, reduceFactor, hurtTimeMin, hurtTimeMax, attackWindowMs
        );
        addHudSettings();
    }

    @Override
    public void onDisable() {
        counter      = 0;
        spoofPending = false;
    }

    // =========================================================================
    // Packet handling — called by MixinNetHandlerPlayClient
    // Mirrors Augustus onEventReadPacket for Basic and Spoof modes.
    // Returns true to cancel the packet, false to let it through.
    // =========================================================================

    public boolean handleVelocityPacket(SPacketEntityVelocityAccessor packet) {
        if (!isEnabled() || mc().player == null) return false;

        String m = mode.getValue();

        if (m.equals("Basic")) {
            double xz = xzVelocity.getValue();
            double y  = yVelocity.getValue();
            if (xz > 0.0 || y > 0.0) {
                packet.setMotionX((int)(packet.getMotionX() * xz / 100.0));
                packet.setMotionY((int)(packet.getMotionY() * y  / 100.0));
                packet.setMotionZ((int)(packet.getMotionZ() * xz / 100.0));
                return false;
            }
            return true; // cancel
        }

        if (m.equals("Intave")) {
            double scale = xzValueIntave.getValue() / 100.0;
            packet.setMotionX((int)(packet.getMotionX() * scale));
            packet.setMotionZ((int)(packet.getMotionZ() * scale));
            return false;
        }

        if (m.equals("Spoof")) {
            // Augustus: cancel + send fake position offset
            spoofX = packet.getMotionX() / 8000.0;
            spoofY = packet.getMotionY() / 8000.0;
            spoofZ = packet.getMotionZ() / 8000.0;
            spoofPending = true;
            return true;
        }

        return false;
    }

    // =========================================================================
    // Per-tick logic — onInputUpdate
    // Mirrors Augustus onEventSilentMove + onEventUpdate.
    // Fires before the outgoing position packet, exactly like EventSilentMove.
    // =========================================================================

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isEnabled() || mc().player == null || mc().world == null) return;

        // Spoof: send the fake offset position on the tick after cancelling the packet
        if (spoofPending) {
            mc().player.connection.sendPacket(new CPacketPlayer.Position(
                    mc().player.posX + spoofX,
                    mc().player.posY + spoofY,
                    mc().player.posZ + spoofZ,
                    false
            ));
            spoofPending = false;
        }

        switch (mode.getValue()) {
            case "Intave":     handleIntave();     break;
            case "PushGround": handlePushGround(); break;
            case "Push":       handlePush();       break;
            case "Reverse":    handleReverse();    break;
        }
    }

    // Augustus case "Intave" in onEventSilentMove:
    // if (jumpIntave && hurtTime == 9 && onGround && counter++ % 2 == 0) jump = true
    // In 1.12.2 we cannot set movementInput.jump from InputUpdateEvent directly,
    // so we replicate the effect by setting motionY = 0.42 (vanilla jump force).
    private void handleIntave() {
        if (!jumpIntave.getValue()) return;
        if (mc().player.hurtTime == 9 && mc().player.onGround && counter++ % 2 == 0) {
            mc().player.motionY    = 0.42;
            mc().player.isAirBorne = true;
        }
    }

    // Augustus pushGround()
    private void handlePushGround() {
        if (mc().player.hurtTime > 0) {
            mc().player.onGround = true;
        }
    }

    // Augustus push() — moveFlying does not exist in 1.12.2 MCP.
    // Equivalent: apply yaw-direction impulse scaled by pushXZ * 0.01.
    private void handlePush() {
        int ht = mc().player.hurtTime;
        int lo = (int) Math.min(pushStart.getValue(), pushEnd.getValue());
        int hi = (int) Math.max(pushStart.getValue(), pushEnd.getValue());
        if (ht >= lo && ht <= hi) {
            double rad = Math.toRadians(mc().player.rotationYaw);
            double spd = pushXZ.getValue() * 0.01;
            mc().player.motionX += -Math.sin(rad) * spd;
            mc().player.motionZ +=  Math.cos(rad) * spd;
            if (pushOnGround.getValue()) {
                mc().player.onGround = true;
            }
        }
    }

    // Augustus reverse() + onEventPostMotion strafe
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

    // =========================================================================
    // ReduceOnAttack — called by MixinPlayerControllerMP on attack
    // Adapted from LiquidBounce VelocityIntave ReduceOnAttack.
    // =========================================================================

    public void onAttack() {
        if (!isEnabled() || mc().player == null) return;
        if (!reduceOnAttack.getValue()) return;

        int  ht  = mc().player.hurtTime;
        int  lo  = (int) hurtTimeMin.getValue();
        int  hi  = (int) hurtTimeMax.getValue();
        long now = System.currentTimeMillis();

        if (ht >= lo && ht <= hi
                && (now - lastAttackTimeMs) <= (long) attackWindowMs.getValue()) {
            double f = reduceFactor.getValue() / 100.0;
            mc().player.motionX *= f;
            mc().player.motionZ *= f;
        }
        lastAttackTimeMs = now;
        if (mc().player != null) lastAttackTick = mc().player.ticksExisted;
    }

    // =========================================================================
    // Legacy helpers — kept for mixin backward compatibility
    // =========================================================================

    public boolean shouldCancel() {
        if (!isEnabled() || mc().player == null) return false;
        return mc().player.hurtResistantTime > 0
                || mc().player.ticksExisted - lastAttackTick < 40;
    }

    public void setLastAttackTick() {
        if (mc().player != null) lastAttackTick = mc().player.ticksExisted;
    }

    // Strafe perpendicular to current facing (Augustus MoveUtil.strafe equivalent)
    private void strafe() {
        double yaw = Math.toRadians(mc().player.rotationYaw + 90.0);
        double spd = Math.sqrt(
                mc().player.motionX * mc().player.motionX +
                mc().player.motionZ * mc().player.motionZ);
        mc().player.motionX = Math.cos(yaw) * spd;
        mc().player.motionZ = Math.sin(yaw) * spd;
    }

    // =========================================================================
    // Accessor interface — implemented by MixinSPacketEntityVelocity
    // =========================================================================

    public interface SPacketEntityVelocityAccessor {
        int  getMotionX();
        int  getMotionY();
        int  getMotionZ();
        void setMotionX(int v);
        void setMotionY(int v);
        void setMotionZ(int v);
    }
}
