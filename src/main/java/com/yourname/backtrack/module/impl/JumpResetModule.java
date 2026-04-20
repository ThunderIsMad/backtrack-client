package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import io.netty.channel.ChannelHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;
import java.util.Random;

/**
 * JumpReset — Intave KnockbackCheck bypass.
 *
 * Ported logic from LiquidBounce VelocityIntave:
 *  - Chance: only jump reset X% of hits (avoids pattern detection)
 *  - Randomize: optional random tick delay before the jump (0..N ticks)
 *  - Fall damage skip: detects pure-Y velocity packets (vx=0, vz=0, vy<0)
 *    and suppresses the reset, avoiding double-jump on landing from a fall.
 *  - Smart tick window: fires only within resetTicks of the hit to stay
 *    below Intave's ticksVelocityMidAir > 10 threshold.
 */
public class JumpResetModule extends Module {

    private final ModeSetting    mode         = new ModeSetting("Mode", Arrays.asList("Smart", "Vanilla"), "Smart");
    private final NumberSetting  resetTicks   = new NumberSetting("ResetTicks",  3,   1,   8,   1);
    private final NumberSetting  cooldown     = new NumberSetting("Cooldown",    14,  8,   25,  1);
    private final NumberSetting  chance       = new NumberSetting("Chance",      80,  1,   100, 1);
    // Randomize delay
    private final BooleanSetting randomize    = new BooleanSetting("Randomize", false);
    private final NumberSetting  delayMin     = new NumberSetting("DelayMin",   0,   0,   10,  1);
    private final NumberSetting  delayMax     = new NumberSetting("DelayMax",   3,   0,   10,  1);
    private final BooleanSetting debug        = new BooleanSetting("Debug", false);

    private double  prevMotionY    = 0.0;
    private int     ticksSinceHit  = 0;
    private boolean wasHurt        = false;
    private int     cooldownTimer  = 0;
    // Fall damage flag — set true when server sends pure-downward velocity (no XZ)
    private boolean isFallDamage   = false;
    // Randomize delay state
    private int     currentDelay   = 0;
    private int     delayCounter   = 0;
    // Pending jump flag set when delay is active
    private boolean pendingJump    = false;

    private final Random random = new Random();

    public JumpResetModule() {
        super("JumpReset", Category.MOVEMENT, Keyboard.KEY_NONE);
        addSettings(mode, resetTicks, cooldown, chance, randomize, delayMin, delayMax, debug);
        addHudSettings();
    }

    @Override
    public void onEnable()  { resetState(); }

    @Override
    public void onDisable() { resetState(); }

    /**
     * Intercept server velocity packets to detect fall damage.
     * Fall damage packet: vx == 0, vz == 0, vy < 0  (pure downward, no horizontal component).
     * On a knockback hit the server always sends non-zero XZ, so this is a reliable filter.
     */
    @SubscribeEvent
    public void onPacket(net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientCustomPacketEvent event) {
        // Packet interception is handled via MixinNetHandlerPlayClient for SPacketEntityVelocity.
        // The mixin already calls notifyVelocityPacket() below — nothing needed here.
    }

    /** Called by MixinNetHandlerPlayClient when SPacketEntityVelocity arrives for our player. */
    public void notifyVelocityPacket(double vx, double vy, double vz) {
        // Pure downward = fall damage, not knockback. Skip jump reset for this hit.
        isFallDamage = (vx == 0.0 && vz == 0.0 && vy < 0);
        if (debug.getValue()) {
            sendClientMessage("\u00a7eVelPacket vx=" + String.format("%.3f", vx)
                    + " vy=" + String.format("%.3f", vy)
                    + " vz=" + String.format("%.3f", vz)
                    + " fallDmg=" + isFallDamage);
        }
    }

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isEnabled() || mc().player == null || mc().world == null) return;

        if (cooldownTimer > 0) {
            cooldownTimer--;
            prevMotionY = mc().player.motionY;

            // Service pending delayed jump while on cooldown is not running
            return;
        }

        boolean isHurt = mc().player.hurtTime > 0;

        if (isHurt && !wasHurt) ticksSinceHit = 0;
        if (isHurt) ticksSinceHit++; else ticksSinceHit = 0;
        wasHurt = isHurt;

        boolean justLanded = prevMotionY < -0.01 && mc().player.onGround;
        prevMotionY = mc().player.motionY;

        // Service pending randomized jump
        if (pendingJump && mc().player.onGround) {
            if (randomize.getValue()) {
                delayCounter++;
                if (delayCounter >= currentDelay) {
                    doJump();
                    pendingJump  = false;
                    delayCounter = 0;
                }
            } else {
                doJump();
                pendingJump = false;
            }
            return;
        }

        if (!isHurt || !justLanded) return;
        // Skip if this velocity was fall damage, not a knockback hit
        if (isFallDamage) return;

        boolean inWindow = !"Smart".equals(mode.getValue())
                || (ticksSinceHit >= 1 && ticksSinceHit <= (int) resetTicks.getValue());
        if (!inWindow) return;

        // Chance check — mirrors LiquidBounce's Math.random() * 100 < chance
        if (random.nextDouble() * 100 >= chance.getValue()) return;

        if (randomize.getValue()) {
            int lo = (int) delayMin.getValue();
            int hi = (int) Math.max(lo, delayMax.getValue());
            currentDelay = (hi > lo) ? lo + random.nextInt(hi - lo + 1) : lo;
            delayCounter = 0;
            if (currentDelay == 0) {
                doJump();
            } else {
                pendingJump = true;
            }
        } else {
            doJump();
        }
    }

    private void doJump() {
        if (mc().player == null) return;
        mc().player.motionY    = 0.42;
        mc().player.isAirBorne = true;
        cooldownTimer = (int) cooldown.getValue();

        if (debug.getValue()) {
            sendClientMessage("\u00a7aJumpReset \u00a77tick=" + ticksSinceHit
                    + " mode=" + mode.getValue()
                    + " hurt=" + mc().player.hurtTime
                    + " delay=" + currentDelay);
        }
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
