package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.client.ClientSimulator;
import com.yourname.backtrack.client.SimDebug;
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
            Arrays.asList("Modify", "JumpReset", "Reverse", "Push", "Legit", "TickZero", "Reduce"), "JumpReset");

    // Modify
    private final NumberSetting xzModify = new NumberSetting("XZ", 0, 0, 100, 1);
    private final NumberSetting yModify  = new NumberSetting("Y",  0, 0, 100, 1);
    private final BooleanSetting ignoreExplosion = new BooleanSetting("IgnoreExplosion", true);

    // JumpReset
    private final BooleanSetting fallDamageCheck = new BooleanSetting("FallDamageCheck", true);
    private final BooleanSetting debug           = new BooleanSetting("Debug", false);
    private final BooleanSetting simDebugLog     = new BooleanSetting("SimDebug", false);
    private final NumberSetting  chance          = new NumberSetting("Chance",          80,  1,   100, 1);
    private final NumberSetting  resetTicks      = new NumberSetting("ResetTicks",      3,   1,   8,   1);
    private final NumberSetting  cooldownTicks   = new NumberSetting("Cooldown",        14,  8,   25,  1);
    private final BooleanSetting randomize       = new BooleanSetting("Randomize", false);
    private final NumberSetting  delayMin        = new NumberSetting("DelayMin",        0,   0,   10,  1);
    private final NumberSetting  delayMax        = new NumberSetting("DelayMax",        3,   0,   10,  1);
    private final NumberSetting  maxDistance     = new NumberSetting("MaxDistance",     2.5, 1.0, 6.0, 0.1);
    private final NumberSetting  jumpMotionVar   = new NumberSetting("JumpMotionVar",   0.04, 0.00, 0.10, 0.01);
    private final BooleanSetting jumpVelWindow   = new BooleanSetting("JumpVelWindow",  true);

    // Reverse
    private final NumberSetting  reverseChance       = new NumberSetting("ReverseChance",      50,  0, 100, 1);
    private final NumberSetting  reverseWaitTicks    = new NumberSetting("CorrectionTicks",    10,  4,  18, 1);
    private final NumberSetting  reverseMoveTicks    = new NumberSetting("MoveTicks",          8,   3,  20, 1);
    private final NumberSetting  reverseStrafeChance = new NumberSetting("StrafeChance",       20,  0, 100, 1);
    private final NumberSetting  reversePatternBreak = new NumberSetting("PatternBreak",       12,  0,  25, 1);
    private final BooleanSetting reverseJump         = new BooleanSetting("ReverseJump",        true);
    private final NumberSetting  reverseJumpMotion   = new NumberSetting("RevJumpMotionVar",   0.04, 0.0, 0.10, 0.01);
    private final BooleanSetting reverseDebug        = new BooleanSetting("ReverseDebug",       false);

    // Push
    private final NumberSetting pushXZ      = new NumberSetting("Push",      1.1, 0.01, 20.0, 2);
    private final NumberSetting pushStart   = new NumberSetting("PushStart", 9.0, 1.0,  10.0, 0);
    private final NumberSetting pushEnd     = new NumberSetting("PushEnd",   2.0, 1.0,  10.0, 0);
    private final BooleanSetting pushOnGround = new BooleanSetting("PushOnGround", false);

    // Legit
    private final BooleanSetting legitStrafe = new BooleanSetting("LegitStrafe", true);

    // TickZero
    private final NumberSetting tickZeroHurtTime = new NumberSetting("TickZeroHurtTime", 4, 1, 10, 0);

    // Reduce (packet-level approach)
    private final NumberSetting reduceXZ     = new NumberSetting("ReduceXZ",     95, 80, 100, 1);
    private final NumberSetting reduceY      = new NumberSetting("ReduceY",      100, 80, 100, 1);
    private final BooleanSetting reduceOnlyWindow = new BooleanSetting("OnlyInWindow", true);
    private final NumberSetting reduceSpread = new NumberSetting("Spread",       2.0, 0.0, 5.0, 0.1);

    // Shared state
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

    // Reverse state
    private enum ReverseState { IDLE, DELAY, CORRECTING, MOVING }
    private ReverseState reverseState        = ReverseState.IDLE;
    private int     reverseTimer             = 0;
    private boolean reverseStrafeLeft        = false;
    private double  reverseOriginalVelY      = 0.0;
    private int     ticksSinceCorrection     = 0;
    private int     restartCount             = 0;
    private long    lastRestartTime          = 0L;
    private long    lastCorrectionStartTime  = 0L;
    private boolean useConservativeProfile   = false;

    public VelocityModule() {
        super("Velocity", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(mode,
                xzModify, yModify, ignoreExplosion,
                fallDamageCheck, debug, simDebugLog, chance, resetTicks, cooldownTicks,
                randomize, delayMin, delayMax, maxDistance,
                jumpMotionVar, jumpVelWindow,
                reverseChance, reverseWaitTicks, reverseMoveTicks,
                reverseStrafeChance, reversePatternBreak,
                reverseJump, reverseJumpMotion, reverseDebug,
                pushXZ, pushStart, pushEnd, pushOnGround,
                legitStrafe,
                tickZeroHurtTime,
                reduceXZ, reduceY, reduceOnlyWindow, reduceSpread);
        addHudSettings();
    }

    // ========================== Packet handling ==========================

    public boolean isModifyMode() {
        return isEnabled() && "Modify".equals(mode.getValue());
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

        String sel = mode.getValue();

        if (debug.getValue()) {
            sendClientMessage("§e" + "Vel vx=" + String.format("%.3f", vx) +
                    " vy=" + String.format("%.3f", vy) +
                    " vz=" + String.format("%.3f", vz) +
                    " fall=" + isFallDamage);
        }

        // Enable shadow mode for the duration of the velocity window
        // to protect the simulator from ClientTickHandler interference.
        if (sel.equals("Modify") || sel.equals("Reduce")) {
            ClientSimulator.INSTANCE.shadowMode = true;
        }

        switch (sel) {
            case "Modify":
                return handleModifyPacket(pkt, rawX, rawY, rawZ);

            case "Reduce":
                handleReducePacket(pkt, vx, vy, vz);
                return false;

            case "Reverse":
                handleReversePacket(vx, vy, vz);
                return false;

            default:
                ClientSimulator.INSTANCE.applyVelocity(vx, vy, vz);
                return false;
        }
    }

    private boolean handleModifyPacket(SPacketEntityVelocityAccessor pkt, int rawX, int rawY, int rawZ) {
        double xzFactor = xzModify.getValue() / 100.0;
        double yFactor  = yModify.getValue()  / 100.0;
        if (xzFactor <= 0.0 && yFactor <= 0.0) {
            pkt.setMotionX(0);
            pkt.setMotionY(0);
            pkt.setMotionZ(0);
            ClientSimulator.INSTANCE.applyVelocity(0, 0, 0);
            return true;
        } else {
            int newX = (int) (rawX * xzFactor);
            int newY = (int) (rawY * yFactor);
            int newZ = (int) (rawZ * xzFactor);
            pkt.setMotionX(newX);
            pkt.setMotionY(newY);
            pkt.setMotionZ(newZ);
            ClientSimulator.INSTANCE.applyVelocity(newX / 8000.0, newY / 8000.0, newZ / 8000.0);
            return false;
        }
    }

    /**
     * Packet-level Reduce — modifies the incoming velocity before the
     * simulator ever sees it, keeping the predicted trajectory clean.
     */
    private void handleReducePacket(SPacketEntityVelocityAccessor pkt, double vx, double vy, double vz) {
        if (reduceOnlyWindow.getValue() && !ClientSimulator.INSTANCE.isInVelocityWindow()) {
            ClientSimulator.INSTANCE.applyVelocity(vx, vy, vz);
            return;
        }

        ClientSimulator sim = ClientSimulator.INSTANCE;
        double expectedMag = Math.sqrt(vx * vx + vz * vz);
        if (expectedMag < 0.001) {
            sim.applyVelocity(vx, vy, vz);
            return;
        }

        double tolXZ = sim.getToleranceXZ();
        double tolY  = sim.getToleranceY();

        double xzFactor = reduceXZ.getValue() / 100.0;
        double yFactor  = reduceY.getValue()  / 100.0;

        // Apply spread ONLY if tolerance is wide enough
        double spread = reduceSpread.getValue() / 100.0;
        if (spread > 0.001 && tolXZ > 0.005) {
            xzFactor += (random.nextDouble() - 0.5) * 2.0 * spread;
            xzFactor = Math.min(0.995, Math.max(0.70, xzFactor));
            yFactor  += (random.nextDouble() - 0.5) * 2.0 * spread;
            yFactor  = Math.min(0.995, Math.max(0.70, yFactor));
        }

        double safeXZ = safeReduceFactor(expectedMag, tolXZ, xzFactor);
        double safeY  = safeReduceFactor(Math.abs(vy), tolY, yFactor);

        double newVx = vx * safeXZ;
        double newVy = vy * safeY;
        double newVz = vz * safeXZ;

        pkt.setMotionX((int) (newVx * 8000.0));
        pkt.setMotionY((int) (newVy * 8000.0));
        pkt.setMotionZ((int) (newVz * 8000.0));

        sim.applyVelocity(newVx, newVy, newVz);

        if (debug.getValue()) {
            double reductionPct = (1.0 - safeXZ) * 100.0;
            sendClientMessage("§dReduce §7vx=" + String.format("%.3f", vx) +
                    " -> " + String.format("%.3f", newVx) +
                    " (" + String.format("%.1f", reductionPct) + "%)" +
                    " tol=" + String.format("%.3f", tolXZ));
        }
    }

    /**
     * Returns a factor (≤1.0) by which the velocity component can be safely
     * reduced without exceeding the simulator's tolerance.
     */
    private double safeReduceFactor(double expectedMag, double tolerance, double userFactor) {
        if (expectedMag < 0.001 || tolerance < 0.000001) return 1.0;
        double requestedReduction = expectedMag * (1.0 - userFactor);
        if (requestedReduction <= tolerance) return userFactor;
        double minFactor = 1.0 - (tolerance / expectedMag);
        return Math.max(userFactor, minFactor);
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

    // ========================== Per-tick update ==========================

    @Override
    public void onClientTick() {
        if (!isEnabled() || mc().player == null || mc().world == null) return;

        SimDebug.enabled = simDebugLog.getValue();
        SimDebug.logToChat = simDebugLog.getValue();

        // Release shadow mode once we are safely outside the velocity window.
        if (!ClientSimulator.INSTANCE.isInVelocityWindow()) {
            ClientSimulator.INSTANCE.shadowMode = false;
        }

        String sel = mode.getValue();

        if (cooldownTimer > 0) {
            cooldownTimer--;
            releaseJumpKey();
        }

        boolean isHurt = mc().player.hurtTime > 0;
        if (isHurt && !wasHurt) {
            ticksSinceHit = 0;
        }
        if (isHurt) {
            ticksSinceHit++;
        } else {
            ticksSinceHit = 0;
        }
        wasHurt = isHurt;

        boolean justLanded = prevMotionY < -0.01 && mc().player.onGround;
        prevMotionY = mc().player.motionY;

        switch (sel) {
            case "JumpReset":
                handleJumpReset(justLanded);
                break;
            case "Reverse":
                handleReverseAutomaton();
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

    // ========================== JumpReset ==========================

    private void handleJumpReset(boolean justLanded) {
        if (ClientSimulator.INSTANCE.isFlyingJumpExpected()) return;

        if (pendingJump && mc().player.onGround && isTargetingEnemyInComboRange()) {
            if (randomize.getValue()) {
                delayCounter++;
                if (delayCounter >= currentDelay) {
                    performJumpWithYVariation();
                    pendingJump = false;
                    delayCounter = 0;
                }
            } else {
                performJumpWithYVariation();
                pendingJump = false;
            }
            return;
        }

        if (cooldownTimer > 0) return;
        if (!mc().player.onGround) return;
        if (!justLanded) return;
        if (fallDamageCheck.getValue() && isFallDamage) return;
        if (ticksSinceHit < 1 || ticksSinceHit > (int) resetTicks.getValue()) return;
        if (jumpVelWindow.getValue() && ClientSimulator.INSTANCE.isInVelocityWindow()) return;
        if (!isTargetingEnemyInComboRange()) return;
        if (random.nextDouble() * 100 >= chance.getValue()) return;

        if (randomize.getValue()) {
            int lo = (int) delayMin.getValue();
            int hi = (int) Math.max(lo, delayMax.getValue());
            currentDelay = (hi > lo) ? lo + random.nextInt(hi - lo + 1) : lo;
            delayCounter = 0;
            if (currentDelay == 0) {
                performJumpWithYVariation();
            } else {
                pendingJump = true;
            }
        } else {
            performJumpWithYVariation();
        }
    }

    private void performJumpWithYVariation() {
        if (mc().player == null) return;

        KeyBinding.setKeyBindState(mc().gameSettings.keyBindJump.getKeyCode(), true);
        jumpKeyHeld = true;
        cooldownTimer = (int) cooldownTicks.getValue();

        double variation = (random.nextDouble() - 0.5) * 2.0 * jumpMotionVar.getValue();
        double jumpMotion = 0.42 + variation;
        mc().player.motionY = jumpMotion;

        if (debug.getValue()) {
            sendClientMessage("§aJR §7tick=" + ticksSinceHit +
                    " hurt=" + mc().player.hurtTime +
                    " yMotion=" + String.format("%.3f", jumpMotion) +
                    " var=" + String.format("%.3f", variation));
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
        if (lastAttacked.isDead) return false;

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

    // ========================== Reverse ==========================

    private void handleReversePacket(double vx, double vy, double vz) {
        if (mc().player == null) return;
        if (random.nextDouble() * 100 >= reverseChance.getValue()) return;

        double mag = Math.sqrt(vx * vx + vz * vz);
        if (mag < 0.015) return;

        long now = System.currentTimeMillis();

        if (reverseState == ReverseState.DELAY) {
            reverseOriginalVelY = vy;
            reverseTimer = 1;
            ticksSinceCorrection = 0;
            lastCorrectionStartTime = now;
            useConservativeProfile = true;
            if (reverseDebug.getValue()) {
                sendClientMessage("§dReverse §7DELAY updated target (conservative)" +
                        " vx=" + String.format("%.3f", vx) +
                        " vy=" + String.format("%.3f", vy) +
                        " vz=" + String.format("%.3f", vz));
            }
            return;
        }

        if (reverseState == ReverseState.CORRECTING || reverseState == ReverseState.MOVING) {
            long elapsed = now - lastCorrectionStartTime;
            if (elapsed < 150) {
                if (reverseDebug.getValue()) {
                    sendClientMessage("§dReverse §7cooldown " + elapsed + "ms, skipping");
                }
                return;
            }
        }

        if (now - lastRestartTime > 500) {
            restartCount = 0;
        }
        restartCount++;
        lastRestartTime = now;
        if (restartCount > 8) {
            if (reverseDebug.getValue()) {
                sendClientMessage("§dReverse §7burst protection, skipping");
            }
            return;
        }

        if (reverseState == ReverseState.CORRECTING || reverseState == ReverseState.MOVING) {
            stopReverseMovement();
            if (reverseDebug.getValue()) {
                sendClientMessage("§dReverse §7new velocity, resetting");
            }
        }

        useConservativeProfile = reverseState != ReverseState.IDLE || (now - lastCorrectionStartTime) < 300;

        reverseOriginalVelY = vy;
        reverseState = ReverseState.DELAY;
        reverseTimer = 1;
        ticksSinceCorrection = 0;
        lastCorrectionStartTime = now;

        if (reverseDebug.getValue()) {
            sendClientMessage("§dReverse §7queued, delaying 1 tick (" +
                    (useConservativeProfile ? "conservative" : "idle") + ")" +
                    " vx=" + String.format("%.3f", vx) +
                    " vy=" + String.format("%.3f", vy) +
                    " vz=" + String.format("%.3f", vz));
        }
    }

    private void handleReverseAutomaton() {
        if (mc().player == null) return;

        if (jumpKeyHeld && mc().player.onGround) {
            releaseJumpKey();
        }

        switch (reverseState) {
            case IDLE:
                break;
            case DELAY:
                if (reverseTimer > 0) {
                    reverseTimer--;
                    if (reverseTimer == 0) {
                        startCorrectionPhase();
                    }
                }
                break;
            case CORRECTING:
                if (reverseTimer > 0) {
                    applyMotionCorrection();
                    reverseTimer--;

                    if (reverseTimer == 0) {
                        double pBreak = reversePatternBreak.getValue() / 100.0;
                        if (random.nextDouble() < pBreak) {
                            reverseTimer = 3;
                            if (reverseDebug.getValue()) {
                                sendClientMessage("§dReverse §7pattern break — shortened to " +
                                        (reverseTimer + 1) + " ticks");
                            }
                            break;
                        }
                        startPostCorrectionMovement();
                    }
                }
                break;
            case MOVING:
                if (reverseTimer > 0) {
                    reverseTimer--;
                } else {
                    stopReverseMovement();
                    resetReverseState();
                    if (reverseDebug.getValue()) {
                        sendClientMessage("§dReverse §7finished");
                    }
                }
                break;
        }
    }

    private void startCorrectionPhase() {
        if (mc().player == null) {
            resetReverseState();
            return;
        }

        reverseTimer = (int) reverseWaitTicks.getValue();
        reverseState = ReverseState.CORRECTING;
        ticksSinceCorrection = 0;

        if (reverseJump.getValue() && mc().player.onGround && Math.abs(reverseOriginalVelY) < 0.2) {
            performReverseJump();
        }

        if (reverseDebug.getValue()) {
            sendClientMessage("§dReverse §7correcting for " + reverseTimer + " ticks (" +
                    (useConservativeProfile ? "conservative" : "idle") + ")" +
                    (reverseJump.getValue() && mc().player.onGround && Math.abs(reverseOriginalVelY) < 0.2 ? " + jump" : ""));
        }
    }

    private void applyMotionCorrection() {
        if (mc().player == null) return;
        if (ClientSimulator.INSTANCE.shadowMode) return;

        ClientSimulator sim = ClientSimulator.INSTANCE;
        double predictedX = sim.getExpectedX();
        double predictedY = sim.getExpectedY();
        double predictedZ = sim.getExpectedZ();
        double tolXZ = sim.getToleranceXZ();
        double tolY  = sim.getToleranceY();

        double dx = Math.abs(mc().player.motionX - predictedX);
        double dy = Math.abs(mc().player.motionY - predictedY);
        double dz = Math.abs(mc().player.motionZ - predictedZ);

        if (dx < tolXZ && dz < tolXZ && dy < tolY) return;

        mc().player.motionX = predictedX;
        mc().player.motionY = predictedY;
        mc().player.motionZ = predictedZ;

        if (reverseDebug.getValue() && ticksSinceCorrection < 5) {
            sendClientMessage("§dCorrection §7t=" + ticksSinceCorrection +
                    " x=" + String.format("%.4f", predictedX) +
                    " y=" + String.format("%.4f", predictedY) +
                    " z=" + String.format("%.4f", predictedZ) +
                    " tolXZ=" + String.format("%.4f", tolXZ) +
                    " tolY=" + String.format("%.4f", tolY));
        }

        ticksSinceCorrection++;
    }

    private void startPostCorrectionMovement() {
        if (mc().player == null) {
            resetReverseState();
            return;
        }

        reverseTimer = (int) reverseMoveTicks.getValue();
        reverseTimer = Math.max(3, (int) (reverseTimer * (0.7f + random.nextFloat() * 0.6f)));
        reverseState = ReverseState.MOVING;

        KeyBinding.setKeyBindState(mc().gameSettings.keyBindForward.getKeyCode(), true);

        double strafeRoll = random.nextDouble() * 100;
        double strafeChance = reverseStrafeChance.getValue();

        if (strafeRoll < strafeChance * 0.33) {
            KeyBinding.setKeyBindState(mc().gameSettings.keyBindForward.getKeyCode(), false);
            applyStrafeKey();
        } else if (strafeRoll < strafeChance) {
            applyStrafeKey();
        }

        if (reverseDebug.getValue()) {
            sendClientMessage("§dReverse §7moving forward " + reverseTimer + " ticks, strafe=" +
                    (reverseStrafeLeft ? "left" : (mc().gameSettings.keyBindRight.isKeyDown() ? "right" : "none")));
        }
    }

    private void applyStrafeKey() {
        if (random.nextBoolean()) {
            KeyBinding.setKeyBindState(mc().gameSettings.keyBindLeft.getKeyCode(), true);
            reverseStrafeLeft = true;
        } else {
            KeyBinding.setKeyBindState(mc().gameSettings.keyBindRight.getKeyCode(), true);
            reverseStrafeLeft = false;
        }
    }

    private void stopReverseMovement() {
        if (mc().player == null) return;
        KeyBinding.setKeyBindState(mc().gameSettings.keyBindForward.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc().gameSettings.keyBindLeft.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc().gameSettings.keyBindRight.getKeyCode(), false);
        reverseStrafeLeft = false;
    }

    private void resetReverseState() {
        stopReverseMovement();
        releaseJumpKey();
        reverseState = ReverseState.IDLE;
        reverseTimer = 0;
        reverseOriginalVelY = 0.0;
        ticksSinceCorrection = 0;
        restartCount = 0;
        lastRestartTime = 0L;
        lastCorrectionStartTime = 0L;
        useConservativeProfile = false;
    }

    private void performReverseJump() {
        if (mc().player == null) return;
        KeyBinding.setKeyBindState(mc().gameSettings.keyBindJump.getKeyCode(), true);
        jumpKeyHeld = true;

        double variation = (random.nextDouble() - 0.5) * 2.0 * reverseJumpMotion.getValue();
        mc().player.motionY = 0.42 + variation;

        if (reverseDebug.getValue()) {
            sendClientMessage("§dReverse §7jump y=" + String.format("%.3f", mc().player.motionY));
        }
    }

    // ========================== Push ==========================

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

    // ========================== Legit ==========================

    private void handleLegit() {
        if (mc().player.hurtTime <= 0) return;
        if (!legitStrafe.getValue()) return;
        if (ClientSimulator.INSTANCE.state().reduceTicks > 0) return;
        mc().player.motionX *= 0.6;
        mc().player.motionZ *= 0.6;
    }

    // ========================== TickZero ==========================

    private void handleTickZero() {
        if (mc().player.hurtTime == (int) tickZeroHurtTime.getValue()) {
            mc().player.motionX *= 0.0;
            mc().player.motionZ *= 0.0;
        }
    }

    // ========================== Settings UI ==========================

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
                filtered.add(debug);
                filtered.add(simDebugLog);
                break;
            case "JumpReset":
                filtered.add(fallDamageCheck);
                filtered.add(debug);
                filtered.add(simDebugLog);
                filtered.add(chance);
                filtered.add(resetTicks);
                filtered.add(cooldownTicks);
                filtered.add(randomize);
                filtered.add(delayMin);
                filtered.add(delayMax);
                filtered.add(maxDistance);
                filtered.add(jumpMotionVar);
                filtered.add(jumpVelWindow);
                break;
            case "Reverse":
                filtered.add(reverseChance);
                filtered.add(reverseWaitTicks);
                filtered.add(reverseMoveTicks);
                filtered.add(reverseStrafeChance);
                filtered.add(reversePatternBreak);
                filtered.add(reverseJump);
                filtered.add(reverseJumpMotion);
                filtered.add(reverseDebug);
                break;
            case "Push":
                filtered.add(pushXZ);
                filtered.add(pushStart);
                filtered.add(pushEnd);
                filtered.add(pushOnGround);
                filtered.add(debug);
                filtered.add(simDebugLog);
                break;
            case "Legit":
                filtered.add(legitStrafe);
                filtered.add(debug);
                filtered.add(simDebugLog);
                break;
            case "TickZero":
                filtered.add(tickZeroHurtTime);
                filtered.add(debug);
                filtered.add(simDebugLog);
                break;
            case "Reduce":
                filtered.add(reduceXZ);
                filtered.add(reduceY);
                filtered.add(reduceOnlyWindow);
                filtered.add(reduceSpread);
                filtered.add(debug);
                filtered.add(simDebugLog);
                break;
        }
        return filtered;
    }

    public void onAttack() {
        if (!isEnabled() || mc().player == null) return;

        if ("JumpReset".equals(mode.getValue()) && ClientSimulator.INSTANCE.isInVelocityWindow()) {
            return;
        }
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        releaseJumpKey();
        stopReverseMovement();
        ClientSimulator.INSTANCE.shadowMode = false;
        SimDebug.enabled = false;
        SimDebug.logToChat = false;
    }
}