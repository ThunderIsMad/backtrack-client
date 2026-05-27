package com.yourname.backtrack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.init.MobEffects;
import net.minecraft.potion.PotionEffect;

public final class MovementEffects {

    public static float applySpeedEffect(float aiMoveSpeed, Minecraft mc) {
        PotionEffect speed = mc.player.getActivePotionEffect(MobEffects.SPEED);
        if (speed != null) {
            int amp = speed.getAmplifier() + 1;
            aiMoveSpeed *= 1.0f + 0.4f * amp;
        }
        PotionEffect slowness = mc.player.getActivePotionEffect(MobEffects.SLOWNESS);
        if (slowness != null) {
            int amp = slowness.getAmplifier() + 1;
            aiMoveSpeed *= 1.0f - 0.15f * amp;
        }
        return aiMoveSpeed;
    }

    public static float applyJumpBoost(float jumpMotion, Minecraft mc) {
        PotionEffect jump = mc.player.getActivePotionEffect(MobEffects.JUMP_BOOST);
        if (jump != null) {
            int amp = jump.getAmplifier() + 1;
            jumpMotion += amp * 0.1f;
        }
        return jumpMotion;
    }
}