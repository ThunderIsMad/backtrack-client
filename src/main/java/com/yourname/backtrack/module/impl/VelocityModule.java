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
            Arrays.asList("Modify", "JumpReset", "Reverse", "Push", "Legit", "TickZero", "Reduce"), "JumpReset");

    // Modify
    private final NumberSetting xzModify = new NumberSetting("XZ", 0, 0, 100, 1);
    private final NumberSetting yModify  = new NumberSetting("Y",  0, 0, 100, 1);
    private final BooleanSetting ignoreExplosion = new BooleanSetting("IgnoreExplosion", true);

    // JumpReset
    private final BooleanSetting fallDamageCheck = new BooleanSetting("FallDamageCheck", true);
    private final BooleanSetting debug           = new BooleanSetting("Debug", false);
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

    // Reduce - tied to Intave legitimateDeviation bands from SimulationEvaluator
    private final NumberSetting reduceXZ     = new NumberSetting("ReduceXZ",     75, 50, 95, 1);
    private final NumberSetting reduceY      = new NumberSetting("ReduceY",      65, 40, 90, 1);
    private final NumberSetting reduceWindow = new NumberSetting("ReduceWindow", 8,  2,  10, 1);

    // --- State (shared) ---
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

    private boolean velocityReceived   = false;
    private int     ticksSinceVelocity = 0;

    // Reduce state - tied to pastExternalVelocity window from MovementMetadata
    private int reduceCounter = 0;

    // Reverse state
    private enum ReverseState { IDLE, DELAY, CORRECTING, MOVING }
    private ReverseState reverseState        = ReverseState.IDLE;
    private int     reverseTimer             = 0;
    private boolean reverseStrafeLeft        = false;
    private double  reverseOriginalVelX      = 0.0;
    private double  reverseOriginalVelZ      = 0.0;
    private double  reverseOriginalVelY      = 0.0;
    private int     ticksSinceCorrection     = 0;
    private int     restartCount             = 0;
    private long    lastRestartTime          = 0L;
    private long    lastCorrectionStartTime  = 0L;
    private boolean useConservativeProfile   = false;

    // ========================== Constructor ==========================

    public VelocityModule() {
        super("Velocity", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(new Setting[] { mode,
                xzModify, yModify, ignoreExplosion,
                fallDamageCheck, debug, chance, resetTicks, cooldownTicks,
                randomize, delayMin, delayMax, maxDistance,
                jumpMotionVar, jumpVelWindow,
                reverseChance, reverseWaitTicks, reverseMoveTicks,
                reverseStrafeChance, reversePatternBreak,
                reverseJump, reverseJumpMotion, reverseDebug,
                pushXZ, pushStart, pushEnd, pushOnGround,
                legitStrafe,
                tickZeroHurtTime,
                reduceXZ, reduceY, reduceWindow });
        addHudSettings();
    }

    // ========================== Packet handling ==========================

    public boolean handleVelocityPacket(SPacketEntityVelocityAccessor pkt) {
        if (mc().player == null) return false;

        int rawX = pkt.getMotionX();
        int rawY = pkt.getMotionY();
        int rawZ = pkt.getMotionZ();

        double vx = rawX / 8000.0;
        double vz = rawZ / 8000.0;

        isFallDamage = (rawX == 0 && rawZ == 0 && rawY < 0);

        velocityReceived = true;
        ticksSinceVelocity = 0;
        reduceCounter = 0; // Intave Reduce: reset on new velocity packet (MovementDispatcher pendingVelocityPackets logic)

        String sel = mode.getValue();

        if (debug.getValue()) {
            sendClientMessage("\u00a7eVel vx=" + String.format("%.3f", vx) +
                    " vy=" + String.format("%.3f", rawY / 8000.0) +
                    " vz=" + String.format("%.3f", vz) +
                    " fall=" + isFallDamage);
        }

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
        } else if ("Reverse".equals(sel)) {
            handleReversePacket(vx, rawY / 8000.0, vz);
        } else if ("Reduce".equals(sel)) {
            // Intave Reduce: leave first evaluated tick (pastExternalVelocity == 0) untouched to avoid aggressive velocityDetected + *20 multiplier in evaluateBestSimulation; only init counter for later ticks
            reduceCounter = (int) reduceWindow.getValue();
            return false;
        }

        return false;
    }

    // ========================== Reverse — Packet Handler ==========================

    private void handleReversePacket(double vx, double vy, double vz) {
        if (mc().player == null) return;
        if (random.nextDouble() * 100 >= reverseChance.getValue()) return;

        double mag = Math.sqrt(vx * vx + vz * vz);
        if (mag < 0.015) return;

        long now = System.currentTimeMillis();

        if (reverseState == ReverseState.DELAY) {
            reverseOriginalVelX = vx;
            reverseOriginalVelZ = vz;
            reverseOriginalVelY = vy;
            reverseTimer = 1;
            ticksSinceCorrection = 0;
            lastCorrectionStartTime = now;
            useConservativeProfile = true;
            if (reverseDebug.getValue()) {
                sendClientMessage("\u00a7dReverse \u00a77DELAY updated target (conservative)" +
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
                    sendClientMessage("\u00a7dReverse \u00a77cooldown " +
                            elapsed + "ms, skipping");
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
                sendClientMessage("\u00a7dReverse \u00a77burst protection, skipping");
            }
            return;
        }

        if (reverseState == ReverseState.CORRECTING || reverseState == ReverseState.MOVING) {
            stopReverseMovement();
            if (reverseDebug.getValue()) {
                sendClientMessage("\u00a7dReverse \u00a77new velocity, resetting");
            }
        }

        if (reverseState == ReverseState.IDLE && (now - lastCorrectionStartTime) >= 300) {
            useConservativeProfile = false;
        } else {
            useConservativeProfile = true;
        }

        reverseOriginalVelX = vx;
        reverseOriginalVelZ = vz;
        reverseOriginalVelY = vy;

        reverseState = ReverseState.DELAY;
        reverseTimer = 1;
        ticksSinceCorrection = 0;
        lastCorrectionStartTime = now;

        if (reverseDebug.getValue()) {
            sendClientMessage("\u00a7dReverse \u00a77queued, delaying 1 tick (" +
                    (useConservativeProfile ? "conservative" : "idle") + ")" +
                    " vx=" + String.format("%.3f", vx) +
                    " vy=" + String.format("%.3f", vy) +
                    " vz=" + String.format("%.3f", vz));
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

    // ========================== Per-tick update ==========================

    @Override
    public void onInputUpdate(InputUpdateEvent event) {
        if (!isEnabled() || mc().player == null || mc().world == null) return;

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

        if (velocityReceived) {
            ticksSinceVelocity++;
        }

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
            case "Reduce":
                handleReduce(); // Intave Reduce: dispatched only for Reduce mode, matches existing onInputUpdate pattern
                break;
        }
    }

    private void handleReduce() {
        // Intave Reduce: apply multipliers only on ticks after first evaluated tick (ticksSinceVelocity > 0 && reduceCounter > 0); window covers pastExternalVelocity 1–9 exactly as derived from SimulationEvaluator
        if (reduceCounter <= 0) return;

        double xzFactor = reduceXZ.getValue() / 100.0;
        double yFactor  = reduceY.getValue()  / 100.0;

        mc().player.motionX *= xzFactor;
        mc().player.motionY *= yFactor;
        mc().player.motionZ *= xzFactor;

        reduceCounter--;
    }

    // ========================== JumpReset ==========================

    private void handleJumpReset(boolean justLanded) {
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
        if (jumpVelWindow.getValue() && velocityReceived && ticksSinceVelocity <= 10) return;
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
            sendClientMessage("\u00a7aJR \u00a77tick=" + ticksSinceHit +
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

    // ========================== Reverse ==========================

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
                                sendClientMessage("\u00a7dReverse \u00a77pattern break — shortened to " +
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
                        sendClientMessage("\u00a7dReverse \u00a77finished");
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

        reverseTimer = Math.min((int) reverseWaitTicks.getValue(), 6);
        reverseState = ReverseState.CORRECTING;
        ticksSinceCorrection = 0;

        if (reverseJump.getValue() && mc().player.onGround
                && Math.abs(reverseOriginalVelY) < 0.2) {
            performReverseJump();
        }

        if (reverseDebug.getValue()) {
            sendClientMessage("\u00a7dReverse \u00a77correcting for " + reverseTimer + " ticks (" +
                    (useConservativeProfile ? "conservative" : "idle") + ")" +
                    (reverseJump.getValue() && mc().player.onGround
                            && Math.abs(reverseOriginalVelY) < 0.2 ? " + jump" : ""));
        }
    }

    private void applyMotionCorrection() {
        if (mc().player == null) return;

        double predictedX, predictedZ;

        if (useConservativeProfile) {
            switch (ticksSinceCorrection) {
                case 0:
                    predictedX = reverseOriginalVelX * 0.33;
                    predictedZ = reverseOriginalVelZ * 0.33;
                    break;
                case 1:
                    predictedX = reverseOriginalVelX * 0.34;
                    predictedZ = reverseOriginalVelZ * 0.34;
                    break;
                case 2:
                    predictedX = reverseOriginalVelX * 0.36;
                    predictedZ = reverseOriginalVelZ * 0.36;
                    break;
                default:
                    double baseX = reverseOriginalVelX * 0.36;
                    double baseZ = reverseOriginalVelZ * 0.36;
                    int extraTicks = ticksSinceCorrection - 2;
                    predictedX = baseX * Math.pow(0.91, extraTicks);
                    predictedZ = baseZ * Math.pow(0.91, extraTicks);
                    break;
            }
        } else {
            if (ticksSinceCorrection == 0) {
                predictedX = reverseOriginalVelX * 0.538;
                predictedZ = reverseOriginalVelZ * 0.538;
            } else {
                predictedX = reverseOriginalVelX * 0.538 * Math.pow(0.91, ticksSinceCorrection);
                predictedZ = reverseOriginalVelZ * 0.538 * Math.pow(0.91, ticksSinceCorrection);
            }
        }

        double H = Math.sqrt(predictedX * predictedX + predictedZ * predictedZ);
        if (H < 0.06) {
            predictedX = 0.0;
            predictedZ = 0.0;
        }

        double predictedY = reverseOriginalVelY;
        for (int i = 0; i < ticksSinceCorrection + 1; i++) {
            predictedY = (predictedY - 0.08) * 0.98;
        }

        if (Math.abs(predictedX) < 0.003) predictedX = 0.0;
        if (Math.abs(predictedY) < 0.003) predictedY = 0.0;
        if (Math.abs(predictedZ) < 0.003) predictedZ = 0.0;

        mc().player.motionX = predictedX;
        mc().player.motionY = predictedY;
        mc().player.motionZ = predictedZ;

        if (reverseDebug.getValue() && ticksSinceCorrection < 5) {
            sendClientMessage("\u00a7dCorrection \u00a77t=" + ticksSinceCorrection +
                    " x=" + String.format("%.4f", predictedX) +
                    " y=" + String.format("%.4f", predictedY) +
                    " z=" + String.format("%.4f", predictedZ) +
                    " H=" + String.format("%.4f", H));
        }

        ticksSinceCorrection++;

        if (predictedX == 0.0 && predictedZ == 0.0 && predictedY == 0.0) {
            reverseTimer = 0;
            if (reverseDebug.getValue()) {
                sendClientMessage("\u00a7dCorrection \u00a77converged at tick " + ticksSinceCorrection +
                        (H < 0.06 ? " (cutoff)" : ""));
            }
        }
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
            if (random.nextBoolean()) {
                KeyBinding.setKeyBindState(mc().gameSettings.keyBindLeft.getKeyCode(), true);
                reverseStrafeLeft = true;
            } else {
                KeyBinding.setKeyBindState(mc().gameSettings.keyBindRight.getKeyCode(), true);
                reverseStrafeLeft = false;
            }
        } else if (strafeRoll < strafeChance) {
            if (random.nextBoolean()) {
                KeyBinding.setKeyBindState(mc().gameSettings.keyBindLeft.getKeyCode(), true);
                reverseStrafeLeft = true;
            } else {
                KeyBinding.setKeyBindState(mc().gameSettings.keyBindRight.getKeyCode(), true);
                reverseStrafeLeft = false;
            }
        }

        if (reverseDebug.getValue()) {
            sendClientMessage("\u00a7dReverse \u00a77moving forward " + reverseTimer + " ticks, strafe=" +
                    (reverseStrafeLeft ? "left" : (mc().gameSettings.keyBindRight.isKeyDown() ? "right" : "none")));
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
        reverseOriginalVelX = 0.0;
        reverseOriginalVelZ = 0.0;
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
            sendClientMessage("\u00a7dReverse \u00a77jump y=" +
                    String.format("%.3f", mc().player.motionY));
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

    // ========================== On Attack ==========================

    public void onAttack() {
        if (mc().player == null || mc().player.getLastAttackedEntity() == null) return;

        if ("JumpReset".equals(mode.getValue()) && jumpVelWindow.getValue()
                && velocityReceived && ticksSinceVelocity <= 10) {
            return;
        }
        if ("Reverse".equals(mode.getValue()) && reverseState == ReverseState.CORRECTING) {
            return;
        }

        if (mc().player.getLastAttackedEntity().hurtTime > 0 && mc().player.hurtTime > 0) {
            mc().player.motionX *= 0.6;
            mc().player.motionZ *= 0.6;
            if (debug.getValue()) {
                sendClientMessage("\u00a7dReduceOnAttack \u00a77motionX=" + String.format("%.3f", mc().player.motionX) +
                        " motionZ=" + String.format("%.3f", mc().player.motionZ));
            }
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
                break;
            case "Legit":
                filtered.add(legitStrafe);
                break;
            case "TickZero":
                filtered.add(tickZeroHurtTime);
                break;
            case "Reduce":
                filtered.add(reduceXZ);
                filtered.add(reduceY);
                filtered.add(reduceWindow);
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
        stopReverseMovement();
        resetState();
    }

    private void resetState() {
        prevMotionY             = 0.0;
        ticksSinceHit           = 0;
        wasHurt                 = false;
        cooldownTimer           = 0;
        isFallDamage            = false;
        pendingJump             = false;
        delayCounter            = 0;
        currentDelay            = 0;
        velocityReceived        = false;
        ticksSinceVelocity      = 0;
        reduceCounter           = 0; // Intave Reduce: reset on enable/disable (matches MovementMetadata past* resets)
        reverseState            = ReverseState.IDLE;
        reverseTimer            = 0;
        reverseStrafeLeft       = false;
        reverseOriginalVelX     = 0.0;
        reverseOriginalVelZ     = 0.0;
        reverseOriginalVelY     = 0.0;
        ticksSinceCorrection    = 0;
        restartCount            = 0;
        lastRestartTime         = 0L;
        lastCorrectionStartTime = 0L;
        useConservativeProfile  = false;
    }
}