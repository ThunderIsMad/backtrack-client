package com.yourname.backtrack.module.impl

import com.yourname.backtrack.client.ClientSimulator
import com.yourname.backtrack.module.Category
import com.yourname.backtrack.module.Module
import com.yourname.backtrack.setting.BooleanSetting
import com.yourname.backtrack.setting.NumberSetting
import net.minecraft.client.settings.KeyBinding
import net.minecraft.entity.EntityLivingBase
import net.minecraft.item.ItemBlock
import net.minecraft.util.math.RayTraceResult
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import java.util.*
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class AutoClickerModule : Module("AutoClicker", Category.COMBAT, Keyboard.KEY_NONE) {

    // ── Settings ─────────────────────────────────────────────────
    private val minCps           = NumberSetting("Min CPS", 12, 1, 18, 1)
    private val maxCps           = NumberSetting("Max CPS", 15, 1, 18, 1)
    private val smartAttack      = BooleanSetting("Smart Attack", false)
    private val onlyOnTarget     = BooleanSetting("Only On Target", false)
    private val attackOnNoInput  = BooleanSetting("Attack No Input", false)
    private val pressDelayTicks  = NumberSetting("Press Delay", 3, 0, 10, 1)

    // Right click
    private val useClicker       = BooleanSetting("Use Clicker", false)
    private val minUseCps        = NumberSetting("Min Use CPS", 10, 1, 18, 1)
    private val maxUseCps        = NumberSetting("Max Use CPS", 14, 1, 18, 1)
    private val useOnlyBlock     = BooleanSetting("Use Only Block", false)
    private val ignoreBlocks     = BooleanSetting("Ignore Blocks", true)
    private val useOnNoInput     = BooleanSetting("Use No Input", false)

    // ── Runtime entropy sources ──────────────────────────────────
    private val random = Random()
    private var sessionDrift    = 1.0        // global tempo drift
    private var driftTarget     = 1.0
    private var lastDriftChange = 0L
    private var doubleClickChance = 0.07    // drifts slowly over time

    // ── Timing state ─────────────────────────────────────────────
    private var lastAttackTime   = 0L
    private var lastUseTime      = 0L
    private var nextAttackDelay  = 0L
    private var nextUseDelay     = 0L
    private var attackWasPressed = false
    private var ticksSincePress  = 0
    private var wasLookingAtEntity = false
    private var wasBreaking      = false

    // Double-click state
    private var doubleClickPending = false
    private var doubleClickGap     = 0L

    // Burst protection (Intave Bursts check: >5 consecutive clicks)
    private var consecutiveClicks = 0
    private var lastClickTime     = 0L

    // ── CPS history for self-calibration (Entropy / Deviation) ───
    private val recentIntervals = LinkedList<Long>(List(100) { 80L })

    init {
        addSettings(minCps, maxCps, smartAttack, onlyOnTarget, attackOnNoInput, pressDelayTicks,
            useClicker, minUseCps, maxUseCps, useOnlyBlock, ignoreBlocks, useOnNoInput)
        addHudSettings()
    }

    override fun onDisable() {
        lastAttackTime = 0; lastUseTime = 0
        wasLookingAtEntity = false; wasBreaking = false
        doubleClickPending = false; attackWasPressed = false
        ticksSincePress = 0; consecutiveClicks = 0
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.keyCode, false)
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, false)
    }

    @SubscribeEvent
    fun onRenderTick(event: TickEvent.RenderTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        if (!isEnabled || mc.player == null || mc.world == null) return
        if (mc.currentScreen != null) return

        val now = System.nanoTime() / 1_000_000L
        val delayTicks = pressDelayTicks.value.toInt()

        // ── Attack ────────────────────────────────────────────────
        val attackPressed = Mouse.isButtonDown(0) || attackOnNoInput.value

        if (attackPressed) {
            if (!attackWasPressed) {
                ticksSincePress = 0
                attackWasPressed = true
            } else {
                if (ticksSincePress < delayTicks) ticksSincePress++
            }

            // Reset consecutive-clicks counter on block break
            val breakingNow = mc.playerController.isHittingBlock
            if (wasBreaking && !breakingNow) {
                lastAttackTime = now + random.nextInt(40) + 20
                consecutiveClicks = 0
            }
            wasBreaking = breakingNow

            // Enforce Intave ClickSpeedLimiter (max 18 CPS)
            if (consecutiveClicks > 5 && now - lastClickTime < 55) return

            if (ticksSincePress < delayTicks) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.keyCode, false)
                return
            }

            // Double-click logic
            if (doubleClickPending) {
                if (now >= lastAttackTime + doubleClickGap) {
                    performClick()
                    doubleClickPending = false
                    lastAttackTime = now
                    nextAttackDelay = nextDelay(minCps.value, maxCps.value)
                }
                return
            }

            if (now >= lastAttackTime + nextAttackDelay) {
                // Smart Attack — skip if enemy is in hurt animation
                if (smartAttack.value) {
                    val ray = mc.objectMouseOver
                    if (ray != null && ray.typeOfHit == RayTraceResult.Type.ENTITY) {
                        val entity = ray.entityHit as? EntityLivingBase
                        if (entity != null && entity.hurtTime > 1) return
                    }
                }

                // Only On Target
                if (onlyOnTarget.value) {
                    if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.ENTITY)
                        return
                }

                // Micro-pause on target switch (Intave Repetitive / Fluctuation)
                val lookingAtEntity = mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.ENTITY
                if (lookingAtEntity && !wasLookingAtEntity) {
                    nextAttackDelay += random.nextInt(40) + 20
                }
                wasLookingAtEntity = lookingAtEntity

                // Micro-pause on fast turn (Intave Fluctuation spike)
                val yawDiff = Math.abs(mc.player.rotationYaw - mc.player.prevRotationYaw)
                if (yawDiff > 15.0f && now - lastAttackTime < nextAttackDelay) {
                    nextAttackDelay += 25
                }

                // Anti-burst: enforce a short gap after 5 rapid hits
                if (consecutiveClicks > 5 && now - lastClickTime < 60) {
                    return
                }

                performClick()
                lastAttackTime = now
                nextAttackDelay = nextDelay(minCps.value, maxCps.value)

                // Dynamic double-click chance (drifts between 0.04–0.12)
                if (random.nextDouble() < doubleClickChance) {
                    doubleClickPending = true
                    doubleClickGap = random.nextInt(15) + 10
                }
            } else {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.keyCode, false)
            }
        } else {
            attackWasPressed = false
            ticksSincePress = 0
            consecutiveClicks = 0
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.keyCode, false)
            lastAttackTime = 0
            wasLookingAtEntity = false
            doubleClickPending = false
        }

        // ── Right click ───────────────────────────────────────────
        if (useClicker.value) {
            val usePressed = Mouse.isButtonDown(1) || useOnNoInput.value
            if (usePressed) {
                if (mc.player.isHandActive) return
                if (useOnlyBlock.value) {
                    if (mc.player.heldItemMainhand.isEmpty || mc.player.heldItemMainhand.item !is ItemBlock) return
                }
                if (ignoreBlocks.value && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == RayTraceResult.Type.BLOCK) return

                if (now >= lastUseTime + nextUseDelay) {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, true)
                    KeyBinding.onTick(mc.gameSettings.keyBindUseItem.keyCode)
                    lastUseTime = now
                    nextUseDelay = nextDelay(minUseCps.value, maxUseCps.value)
                } else {
                    KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, false)
                }
            } else {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.keyCode, false)
                lastUseTime = 0
            }
        }

        // ── Global entropy drift (every 4–7 seconds) ─────────────
        if (now - lastDriftChange > 4000 + random.nextInt(3000)) {
            driftTarget = 0.92 + random.nextDouble() * 0.16  // 0.92–1.08
            doubleClickChance = 0.04 + random.nextDouble() * 0.08  // 0.04–0.12
            lastDriftChange = now
        }
        sessionDrift += (driftTarget - sessionDrift) * 0.1  // smooth lerp
    }

    // ── Core: Lognormal delay generator ──────────────────────────

    private fun nextDelay(minCps: Double, maxCps: Double): Long {
        // Target CPS drifts inside the configured range
        val effectiveCps = minCps + (maxCps - minCps) * (0.5 + sessionDrift - 1.0)
        val baseMs = 1000.0 / effectiveCps.coerceIn(1.0, 18.0)

        // Lognormal parameters that drift slowly → constantly changing Entropy / Deviation
        val sigma = 0.15 + (sessionDrift - 1.0) * 0.05  // 0.10–0.20
        val mu = ln(baseMs) - sigma * sigma / 2.0
        val rawMs = exp(mu + sigma * random.nextGaussian())

        // Add "human" micro-jitter (±1.5 ms) — breaks EqualDelay streaks
        val jitter = (random.nextDouble() - 0.5) * 3.0

        // Occasional longer pause (Flutuation spike)
        val spike = if (random.nextInt(25) == 0) random.nextInt(40) + 20L else 0L

        val result = (rawMs + jitter + spike).toLong().coerceIn(50, 250)

        // Update recent intervals for self-analysis
        recentIntervals.addLast(result)
        if (recentIntervals.size > 100) recentIntervals.removeFirst()

        return result
    }

    // ── Click execution with swing ────────────────────────────────

    private fun performClick() {
        val player = mc.player ?: return

        // CRITICAL: send swing BEFORE attack (NoSwingHeuristic bypass)
        player.swingItem()

        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.keyCode, true)
        KeyBinding.onTick(mc.gameSettings.keyBindAttack.keyCode)

        consecutiveClicks++
        lastClickTime = System.currentTimeMillis()
    }

    // ── HUD text ──────────────────────────────────────────────────

    override fun getHudText(): String {
        val text = StringBuilder("AC %.0f-%.0f CPS".format(minCps.value, maxCps.value))
        if (useClicker.value)
            text.append(" | UC %.0f-%.0f CPS".format(minUseCps.value, maxUseCps.value))
        return text.toString()
    }
}