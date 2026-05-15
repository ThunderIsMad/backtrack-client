package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.Random;

public class AutoClickerModule extends Module {

    /* ── Attack ────────────────────────────── */
    private final NumberSetting  minCps           = new NumberSetting("Min CPS", 10, 1, 20, 1);
    private final NumberSetting  maxCps           = new NumberSetting("Max CPS", 14, 1, 20, 1);
    private final BooleanSetting smartAttack      = new BooleanSetting("Smart Attack", false);
    private final BooleanSetting onlyOnTarget     = new BooleanSetting("Only On Target", false);
    private final BooleanSetting attackOnNoInput  = new BooleanSetting("Attack No Input", false);
    private final NumberSetting  pressDelayTicks  = new NumberSetting("Press Delay", 3, 0, 10, 1);

    /* ── Use (right click) ─────────────────── */
    private final BooleanSetting useClicker       = new BooleanSetting("Use Clicker", false);
    private final NumberSetting  minUseCps        = new NumberSetting("Min Use CPS", 8, 1, 20, 1);
    private final NumberSetting  maxUseCps        = new NumberSetting("Max Use CPS", 12, 1, 20, 1);
    private final BooleanSetting useOnlyBlock     = new BooleanSetting("Use Only Block", false);
    private final BooleanSetting ignoreBlocks     = new BooleanSetting("Ignore Blocks", true);
    private final BooleanSetting useOnNoInput     = new BooleanSetting("Use No Input", false);

    /* ── State ─────────────────────────────── */
    private long    lastAttackTime;
    private long    lastUseTime;
    private long    nextAttackDelay;
    private long    nextUseDelay;
    private float   lastYaw;
    private boolean wasLookingAtEntity;
    private boolean wasBreaking;

    private final long[] attackTimestamps = new long[20];
    private int attackTimestampIndex = 0;
    private int recentAttacks = 0;

    private boolean doubleClickPending = false;
    private long    doubleClickGap = 0;

    private double currentCpsOffset = 0.0;
    private long   lastOffsetChange = 0;

    private boolean attackWasPressed = false;
    private int     ticksSincePress  = 0;

    private final Random random = new Random();

    public AutoClickerModule() {
        super("AutoClicker", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(minCps, maxCps, smartAttack, onlyOnTarget, attackOnNoInput, pressDelayTicks,
                useClicker, minUseCps, maxUseCps, useOnlyBlock, ignoreBlocks, useOnNoInput);
        addHudSettings();
    }

    @Override
    public void onDisable() {
        lastAttackTime    = 0;
        lastUseTime       = 0;
        wasLookingAtEntity = false;
        wasBreaking       = false;
        doubleClickPending = false;
        attackWasPressed   = false;
        ticksSincePress    = 0;
        KeyBinding.setKeyBindState(mc().gameSettings.keyBindAttack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc().gameSettings.keyBindUseItem.getKeyCode(), false);
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        if (!isEnabled() || mc().player == null || mc().world == null) return;
        if (mc().currentScreen != null) return;

        long now = System.nanoTime() / 1_000_000L;
        int delayTicks = (int) pressDelayTicks.getValue();

        /* ── ATTACK ── */
        boolean attackPressed = Mouse.isButtonDown(0) || attackOnNoInput.getValue();

        if (attackPressed) {
            if (!attackWasPressed) {
                ticksSincePress = 0;
                attackWasPressed = true;
            } else {
                if (ticksSincePress < delayTicks) {
                    ticksSincePress++;
                }
            }

            boolean breakingNow = mc().playerController.getIsHittingBlock();
            if (wasBreaking && !breakingNow) {
                lastAttackTime = now + random.nextInt(40) + 20;
            }
            wasBreaking = breakingNow;

            updateAttackWindow(now);

            if (ticksSincePress < delayTicks) {
                KeyBinding.setKeyBindState(mc().gameSettings.keyBindAttack.getKeyCode(), false);
                return;
            }

            if (doubleClickPending) {
                if (now >= lastAttackTime + doubleClickGap) {
                    performClick();
                    doubleClickPending = false;
                    lastAttackTime = now;
                    nextAttackDelay = calculateHumanDelay(minCps.getValue(), maxCps.getValue());
                }
                return;
            }

            if (now >= lastAttackTime + nextAttackDelay) {
                if (smartAttack.getValue()) {
                    if (mc().objectMouseOver != null && mc().objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY) {
                        if (mc().objectMouseOver.entityHit instanceof EntityLivingBase) {
                            EntityLivingBase entity = (EntityLivingBase) mc().objectMouseOver.entityHit;
                            if (entity.hurtTime > 1) return;
                        }
                    }
                }

                if (onlyOnTarget.getValue()) {
                    if (mc().objectMouseOver == null || mc().objectMouseOver.typeOfHit != RayTraceResult.Type.ENTITY)
                        return;
                }

                boolean lookingAtEntity = mc().objectMouseOver != null
                        && mc().objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY;
                if (lookingAtEntity && !wasLookingAtEntity) {
                    nextAttackDelay += random.nextInt(40) + 20;
                }
                wasLookingAtEntity = lookingAtEntity;

                float yawDiff = Math.abs(mc().player.rotationYaw - lastYaw);
                if (yawDiff > 15.0f && now - lastAttackTime < nextAttackDelay) {
                    nextAttackDelay += 25;
                }
                lastYaw = mc().player.rotationYaw;

                if (recentAttacks >= 19) {
                    return;
                }

                performClick();
                lastAttackTime = now;
                nextAttackDelay = calculateHumanDelay(minCps.getValue(), maxCps.getValue());

                if (random.nextDouble() < 0.08) {
                    doubleClickPending = true;
                    doubleClickGap = random.nextInt(15) + 10;
                }
            } else {
                KeyBinding.setKeyBindState(mc().gameSettings.keyBindAttack.getKeyCode(), false);
            }
        } else {
            attackWasPressed = false;
            ticksSincePress = 0;
            KeyBinding.setKeyBindState(mc().gameSettings.keyBindAttack.getKeyCode(), false);
            lastAttackTime = 0;
            wasLookingAtEntity = false;
            doubleClickPending = false;
        }

        /* ── (right click) ── */
        if (useClicker.getValue()) {
            boolean usePressed = Mouse.isButtonDown(1) || useOnNoInput.getValue();
            if (usePressed) {
                if (mc().player.isHandActive()) return;

                if (useOnlyBlock.getValue()) {
                    if (mc().player.getHeldItemMainhand().isEmpty() ||
                            !(mc().player.getHeldItemMainhand().getItem() instanceof ItemBlock)) {
                        return;
                    }
                }

                if (ignoreBlocks.getValue()) {
                    if (mc().objectMouseOver != null && mc().objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) {
                        return;
                    }
                }

                if (now >= lastUseTime + nextUseDelay) {
                    KeyBinding.setKeyBindState(mc().gameSettings.keyBindUseItem.getKeyCode(), true);
                    KeyBinding.onTick(mc().gameSettings.keyBindUseItem.getKeyCode());
                    lastUseTime = now;
                    nextUseDelay = calculateHumanDelay(minUseCps.getValue(), maxUseCps.getValue());
                } else {
                    KeyBinding.setKeyBindState(mc().gameSettings.keyBindUseItem.getKeyCode(), false);
                }
            } else {
                KeyBinding.setKeyBindState(mc().gameSettings.keyBindUseItem.getKeyCode(), false);
                lastUseTime = 0;
            }
        }
    }

    private void performClick() {
        KeyBinding.setKeyBindState(mc().gameSettings.keyBindAttack.getKeyCode(), true);
        KeyBinding.onTick(mc().gameSettings.keyBindAttack.getKeyCode());

        long now = System.currentTimeMillis();
        while (recentAttacks > 0 && now - attackTimestamps[(attackTimestampIndex - recentAttacks + 20) % 20] > 1000) {
            recentAttacks--;
        }
        attackTimestamps[attackTimestampIndex] = now;
        attackTimestampIndex = (attackTimestampIndex + 1) % 20;
        recentAttacks++;
        if (recentAttacks > 20) recentAttacks = 20;
    }

    private void updateAttackWindow(long now) {
        long currentMillis = System.currentTimeMillis();
        while (recentAttacks > 0 && currentMillis - attackTimestamps[(attackTimestampIndex - recentAttacks + 20) % 20] > 1000) {
            recentAttacks--;
        }
    }

    private long calculateHumanDelay(double minCps, double maxCps) {
        long now = System.currentTimeMillis();
        if (now - lastOffsetChange > 3000 + random.nextInt(2000)) {
            currentCpsOffset = (random.nextDouble() - 0.5) * 2.0;
            lastOffsetChange = now;
        }

        double min = Math.max(1.0, minCps);
        double max = Math.max(min, maxCps);
        double effectiveCps = (min + max) / 2.0 + currentCpsOffset;
        effectiveCps = Math.max(min, Math.min(max, effectiveCps));

        double beta = betaSample(2.0, 5.0);
        double baseDelayMs = 1000.0 / effectiveCps * (0.85 + 0.3 * beta);

        double noise = (random.nextGaussian() * 8);
        long delay = Math.round(baseDelayMs + noise);

        delay = Math.max(20, Math.min(1000, delay));

        if (random.nextInt(40) == 0) {
            delay += random.nextInt(40) + 20;
        }

        return delay;
    }

    private double betaSample(double alpha, double beta) {
        double x = gammaSample(alpha);
        double y = gammaSample(beta);
        return x / (x + y);
    }

    private double gammaSample(double alpha) {
        if (alpha < 1) {
            double u = random.nextDouble();
            return gammaSample(alpha + 1) * Math.pow(u, 1.0 / alpha);
        }
        double d = alpha - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        for (;;) {
            double x, v;
            do {
                x = random.nextGaussian();
                v = 1.0 + c * x;
            } while (v <= 0.0);
            v = v * v * v;
            double u = random.nextDouble();
            if (u < 1.0 - 0.0331 * x * x * x * x) return d * v;
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) return d * v;
        }
    }

    @Override
    public String getHudText() {
        String text = String.format("AC %.0f-%.0f CPS", minCps.getValue(), maxCps.getValue());
        if (useClicker.getValue()) {
            text += String.format(" | UC %.0f-%.0f CPS", minUseCps.getValue(), maxUseCps.getValue());
        }
        return text;
    }
}