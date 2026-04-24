package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraftforge.client.event.InputUpdateEvent;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;

/**
 * Velocity — Augustus-logic adapted for backtrack-client / Forge 1.12.2.
 *
 * Modes: Basic, Legit, PushGround, Push, IntvAC, Reverse, Spoof
 *
 * Packet handling (Basic / Legit / IntvAC / Spoof) → MixinNetHandlerPlayClient
 *   via handleVelocityPacket().
 * Per-tick motion logic (IntvAC jump, Push, Reverse, PushGround, Spoof send)
 *   → onInputUpdate(), which fires right before the outgoing position packet.
 *
 * Augustus EventReadPacket  → handleVelocityPacket()
 * Augustus EventSilentMove  → onInputUpdate()
 * Augustus EventUpdate      → onInputUpdate()
 * Augustus moveFlying()     → manual yaw impulse (moveFlying absent in 1.12.2 MCP)
 */
public class VelocityModule extends Module {

    // ── Mode ──────────────────────────────────────────────────────────────────

    private final ModeSetting mode = new ModeSetting("Mode",
            Arrays.asList("Basic", "Legit", "PushGround", "Push", "IntvAC", "Reverse", "Spoof"),
            "Basic");

    // ── Basic / Legit ─────────────────────────────────────────────────────────

    /** XZ scale 0–100 %; 0 = cancel packet entirely. */
    private final NumberSetting xzVelocity = new NumberSetting("XZVelocity", 20.0, 0.0, 100.0, 1.0);
    /** Y scale 0–100 %; only applied in Basic. */
    private final NumberSetting yVelocity  = new NumberSetting("YVelocity",  20.0, 0.0, 100.0, 1.0);

    // ── IntvAC (Intave KnockbackCheck bypass) ────────────────────────────────

    /** XZ packet scale −100–100 %. Negative = reverse. */
    private final NumberSetting  xzScaleIntvAC = new NumberSetting("IntvACXZScale", 60.0, -100.0, 100.0, 1.0);
    /** Every other KB hit: jump on hurtTime == 9 to satisfy hadYVelocityStartMotion. */
    private final BooleanSetting jumpIntvAC    = new BooleanSetting("IntvACJump", false);

    // ── Push ──────────────────────────────────────────────────────────────────

    /** Forward impulse per tick = pushXZ * 0.01. */
    private final NumberSetting  pushXZ       = new NumberSetting("PushSpeed",  11.0,  1.0, 200.0, 1.0);
    private final NumberSetting  pushStart    = new NumberSetting("PushStart",   9.0,  1.0,  10.0, 1.0);
    private final NumberSetting  pushEnd      = new NumberSetting("PushEnd",     2.0,  1.0,  10.0, 1.0);
    private final BooleanSetting pushOnGround = new BooleanSetting("PushOnGround", false);

    // ── Reverse ───────────────────────────────────────────────────────────────

    private final NumberSetting  reverseStart  = new NumberSetting("ReverseStart",  9.0, 1.0, 10.0, 1.0);
    private final BooleanSetting reverseStrafe = new BooleanSetting("ReverseStrafe", false);

    // ── ReduceOnAttack (LiquidBounce VelocityIntvAC) ─────────────────────────

    private final BooleanSetting reduceOnAttack = new BooleanSetting("ReduceOnAttack", true);
    /** XZ multiplier when ReduceOnAttack fires (0–100 %). */
    private final NumberSetting  reduceFactor   = new NumberSetting("ReduceFactor",   60.0,   0.0, 100.0,   5.0);
    private final NumberSetting  hurtTimeMin    = new NumberSetting("HurtTimeMin",     5.0,   1.0,  10.0,   1.0);
    private final NumberSetting  hurtTimeMax    = new NumberSetting("HurtTimeMax",     7.0,   1.0,  10.0,   1.0);
    private final NumberSetting  attackWindowMs = new NumberSetting("AttackWindowMs", 2000.0, 100.0, 5000.0, 100.0);

    // ── State ─────────────────────────────────────────────────────────────────

    /** IntvAC: fire jump on every other hit (counter % 2 == 0). */
    private int     intvACCounter    = 0;
    /** Spoof: true for one tick after cancelling the velocity packet. */
    private boolean spoofPending     = false;
    private double  spoofX, spoofY, spoofZ;
    private long    lastAttackTimeMs = 0L;

    // ─────────────────────────────────────────────────────────────────────────

    public VelocityModule() {
        super("Velocity", Category.COMBAT, Keyboard.KEY_NONE);
        addSetting(mode);
        addSetting(xzVelocity);
        addSetting(yVelocity);
        addSetting(xzScaleIntvAC);
        addSetting(jumpIntvAC);
        addSetting(pushXZ);
        addSetting(pushStart);
        addSetting(pushEnd);
        addSetting(pushOnGround);
        addSetting(reverseStart);
        addSetting(reverseStrafe);
        addSetting(reduceOnAttack);
        addSetting(reduceFactor);
        addSetting(hurtTimeMin);
        addSetting(hurtTimeMax);
        addSetting(attackWindowMs);
        addHudSettings();
    }

    @Override
    public void onDisable() {
        intvACCounter = 0;
        spoofPending  = false;
    }

    // =========================================================================
    // Packet layer — called by MixinNetHandlerPlayClient.onHandleEntityVelocity
    // Returns true  → cancel the packet (velocity not applied by vanilla).
    // Returns false → let the (possibly modified) packet through.
    // =========================================================================

    public boolean handleVelocityPacket(SPacketEntityVelocityAccessor packet) {
        if (!isEnabled() || Minecraft.getMinecraft().player == null) return false;

        switch (mode.getValue()) {

            case "Basic": {
                double xz = xzVelocity.getValue();
                double y  = yVelocity.getValue();
                if (xz <= 0.0 && y <= 0.0) return true; // full cancel
                packet.setMotionX((int)(packet.getMotionX() * xz / 100.0));
                packet.setMotionY((int)(packet.getMotionY() * y  / 100.0));
                packet.setMotionZ((int)(packet.getMotionZ() * xz / 100.0));
                return false;
            }

            case "Legit": {
                // Subtle reduce — XZ only, Y untouched so it looks natural.
                double xz = xzVelocity.getValue();
                if (xz <= 0.0) return true;
                packet.setMotionX((int)(packet.getMotionX() * xz / 100.0));
                packet.setMotionZ((int)(packet.getMotionZ() * xz / 100.0));
                return false;
            }

            case "IntvAC": {
                // Scale XZ; may be negative to partially reverse the knockback vector.
                // Satisfies Intave bad5 (xz_increasement) by keeping a non-zero value.
                double scale = xzScaleIntvAC.getValue() / 100.0;
                packet.setMotionX((int)(packet.getMotionX() * scale));
                packet.setMotionZ((int)(packet.getMotionZ() * scale));
                return false;
            }

            case "Spoof": {
                // Augustus Spoof: cancel the real packet, then on the next tick send
                // a fake position offset equal to the velocity delta, so the server
                // thinks we moved but our client ignores the knockback.
                spoofX = packet.getMotionX() / 8000.0;
                spoofY = packet.getMotionY() / 8000.0;
                spoofZ = packet.getMotionZ() / 8000.0;
                spoofPending = true;
                return true;
            }

            default:
                return false;
        }
    }

    // =========================================================================
    // Tick layer — fires before the outgoing C03 position packet.
    // Covers: Spoof deferred send, IntvAC jump, PushGround, Push, Reverse.
    // =========================================================================

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        // Spoof: send the fake position offset one tick after cancelling the packet.
        if (spoofPending) {
            mc.player.connection.sendPacket(new CPacketPlayer.Position(
                    mc.player.posX + spoofX,
                    mc.player.posY + spoofY,
                    mc.player.posZ + spoofZ,
                    false
            ));
            spoofPending = false;
        }

        switch (mode.getValue()) {
            case "IntvAC":     handleIntvAC(mc);     break;
            case "PushGround": handlePushGround(mc); break;
            case "Push":       handlePush(mc);       break;
            case "Reverse":    handleReverse(mc);    break;
        }
    }

    // ── Per-mode tick handlers ────────────────────────────────────────────────

    /**
     * Augustus IntvAC case in onEventSilentMove:
     *   if (jump && hurtTime == 9 && onGround && counter++ % 2 == 0) jump = true
     *
     * Setting motionY = 0.42 replicates vanilla jump force, satisfying Intave's
     * hadYVelocityStartMotion check (requires velocitySqY >= 0.09; 0.42² = 0.176).
     * Firing only every other hit avoids velocityVL2 accumulation.
     */
    private void handleIntvAC(Minecraft mc) {
        if (!jumpIntvAC.getValue()) return;
        if (mc.player.hurtTime == 9 && mc.player.onGround && intvACCounter++ % 2 == 0) {
            mc.player.motionY    = 0.42;
            mc.player.isAirBorne = true;
        }
    }

    /**
     * Augustus pushGround(): forces onGround = true while hurt so the server
     * treats the player as grounded, bypassing air-velocity checks.
     */
    private void handlePushGround(Minecraft mc) {
        if (mc.player.hurtTime > 0) {
            mc.player.onGround = true;
        }
    }

    /**
     * Augustus push(): apply a forward yaw-direction impulse during the hurtTime
     * window. moveFlying() doesn't exist in 1.12.2 MCP; yaw-based trig is equivalent.
     */
    private void handlePush(Minecraft mc) {
        int ht = mc.player.hurtTime;
        int lo = (int) Math.min(pushStart.getValue(), pushEnd.getValue());
        int hi = (int) Math.max(pushStart.getValue(), pushEnd.getValue());
        if (ht >= lo && ht <= hi) {
            double rad = Math.toRadians(mc.player.rotationYaw);
            double spd = pushXZ.getValue() * 0.01;
            mc.player.motionX += -Math.sin(rad) * spd;
            mc.player.motionZ +=  Math.cos(rad) * spd;
            if (pushOnGround.getValue()) {
                mc.player.onGround = true;
            }
        }
    }

    /**
     * Augustus reverse(): invert XZ velocity on the trigger tick, then optionally
     * strafe perpendicular to facing for the remainder of the hurtTime window.
     */
    private void handleReverse(Minecraft mc) {
        int ht = mc.player.hurtTime;
        if (ht == (int) reverseStart.getValue()) {
            mc.player.motionX *= -1.0;
            mc.player.motionZ *= -1.0;
        }
        if (reverseStrafe.getValue() && ht > 0 && ht <= (int) reverseStart.getValue()) {
            strafe(mc);
        }
    }

    // =========================================================================
    // ReduceOnAttack — called by MixinPlayerControllerMP.onAttackEntity
    // Adapted from LiquidBounce VelocityIntvAC ReduceOnAttack.
    // Multiplies XZ velocity when we attack while the target is in hurtTime window.
    // =========================================================================

    public void onAttack() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!isEnabled() || mc.player == null) return;
        if (!reduceOnAttack.getValue()) return;

        long now = System.currentTimeMillis();
        int  ht  = mc.player.hurtTime;
        int  lo  = (int) hurtTimeMin.getValue();
        int  hi  = (int) hurtTimeMax.getValue();

        if (ht >= lo && ht <= hi && (now - lastAttackTimeMs) <= (long) attackWindowMs.getValue()) {
            double f = reduceFactor.getValue() / 100.0;
            mc.player.motionX *= f;
            mc.player.motionZ *= f;
        }
        lastAttackTimeMs = now;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Augustus MoveUtil.strafe(): redirect XZ at 90° to current facing, same speed. */
    private void strafe(Minecraft mc) {
        double yaw = Math.toRadians(mc.player.rotationYaw + 90.0);
        double spd = Math.sqrt(mc.player.motionX * mc.player.motionX
                             + mc.player.motionZ * mc.player.motionZ);
        mc.player.motionX = Math.cos(yaw) * spd;
        mc.player.motionZ = Math.sin(yaw) * spd;
    }

    // =========================================================================
    // Accessor interface — implemented by MixinSPacketEntityVelocity via @Accessor
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
