package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.network.play.server.SPacketConfirmTransaction;
import net.minecraft.network.play.client.CPacketConfirmTransaction;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Random;

public class VelocityModule extends Module {

    private final ModeSetting mode = new ModeSetting(
            "Mode",
            Arrays.asList("SmartDesync", "TransactionDelay", "Augustus3"),
            "SmartDesync"
    );
    private final NumberSetting  horizontal       = new NumberSetting("Horizontal", 0.0, 1.0, 0.85, 0.01);
    private final NumberSetting  vertical         = new NumberSetting("Vertical",   0.0, 1.0, 1.0,  0.01);
    private final BooleanSetting onlyCombat       = new BooleanSetting("OnlyCombat",       true);
    private final BooleanSetting transactionSpoof = new BooleanSetting("TransactionSpoof", true);

    private final ConcurrentLinkedQueue<TransactionEntry> transactionQueue = new ConcurrentLinkedQueue<>();
    private final TransactionStats stats = new TransactionStats();
    private int    lastAttackTick  = 0;
    private double aggressionLevel = 0.5;
    private final Random patternRandom = new Random();

    public VelocityModule() {
        super("Velocity", Category.COMBAT, Keyboard.KEY_NONE);
        addSettings(mode, horizontal, vertical, onlyCombat, transactionSpoof);
        addHudSettings();
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isEnabled() || mc.player == null || mc.world == null) return;

        stats.update();

        if (mc.player.ticksExisted - lastAttackTick < 20) {
            aggressionLevel = Math.min(0.9, aggressionLevel + 0.1);
        } else {
            aggressionLevel = Math.max(0.2, aggressionLevel - 0.02);
        }

        processQueuedPackets();
    }

    public boolean handleVelocityPacket(SPacketEntityVelocity packet) {
        if (!isEnabled() || mc.player == null) return false;
        if (!shouldReduceVelocity()) return false;

        queueModifiedVelocity(packet);

        if (transactionSpoof.getValue() && mode.getValue().equals("Augustus3")) {
            spoofTransactionPattern();
        }

        return true;
    }

    public boolean handleConfirmTransaction(SPacketConfirmTransaction packet) {
        if (!isEnabled()) return false;

        stats.recordIncoming(packet.getActionNumber(), packet.getWindowId());

        if (mode.getValue().equals("Augustus3") && transactionSpoof.getValue()
                && shouldDelayTransaction()) {
            queueTransactionResponse(packet);
            return true;
        }
        return false;
    }

    private boolean shouldReduceVelocity() {
        if (onlyCombat.getValue()) {
            return mc.player.ticksExisted - lastAttackTick < 40
                    || mc.player.hurtResistantTime > 0;
        }
        return true;
    }

    private void queueModifiedVelocity(SPacketEntityVelocity original) {
        double hReduction = horizontal.getValue();
        double vReduction = vertical.getValue();

        double randomFactor  = 0.98 + patternRandom.nextDouble() * 0.04;
        hReduction *= randomFactor;

        double latencyFactor = Math.max(0.3, 1.0 - stats.getAverageLatency() / 100.0);
        hReduction *= aggressionLevel * latencyFactor;

        final double finalX = (original.getMotionX() / 8000.0) * hReduction;
        final double finalY = (original.getMotionY() / 8000.0) * vReduction;
        final double finalZ = (original.getMotionZ() / 8000.0) * hReduction;

        transactionQueue.add(new TransactionEntry(
                new Runnable() {
                    @Override
                    public void run() {
                        if (mc.player != null) {
                            mc.player.motionX = finalX;
                            mc.player.motionY = finalY;
                            mc.player.motionZ = finalZ;
                        }
                    }
                },
                System.currentTimeMillis() + getOptimalDelay()
        ));
    }

    private long getOptimalDelay() {
        long baseDelay = stats.getOptimalPacketSpacing();
        long jitter    = patternRandom.nextInt(7) - 3;
        return (long) (baseDelay * (1.0 - aggressionLevel * 0.3)) + jitter;
    }

    private void spoofTransactionPattern() {
        int count = 1 + patternRandom.nextInt(3);
        for (int i = 0; i < count; i++) {
            final int windowId = patternRandom.nextBoolean() ? -1 : 0;
            final int actionId = stats.getNextPredictedAction();
            final long delay   = (long) (i * 2 + patternRandom.nextInt(3));
            transactionQueue.add(new TransactionEntry(
                    new Runnable() {
                        @Override
                        public void run() {
                            if (mc.player != null) {
                                mc.player.connection.sendPacket(
                                        new CPacketConfirmTransaction(windowId, (short) actionId, true));
                            }
                        }
                    },
                    System.currentTimeMillis() + delay
            ));
        }
    }

    private void processQueuedPackets() {
        long now = System.currentTimeMillis();
        while (!transactionQueue.isEmpty()) {
            TransactionEntry entry = transactionQueue.peek();
            if (entry.sendTime <= now) {
                transactionQueue.poll();
                entry.task.run();
            } else {
                break;
            }
        }
    }

    private boolean shouldDelayTransaction() {
        if (patternRandom.nextDouble() > 0.3) return false;
        return stats.getTimeSinceLastDelay() > 50;
    }

    private void queueTransactionResponse(SPacketConfirmTransaction packet) {
        final int wid = packet.getWindowId();
        final int aid = packet.getActionNumber();
        transactionQueue.add(new TransactionEntry(
                new Runnable() {
                    @Override
                    public void run() {
                        if (mc.player != null) {
                            mc.player.connection.sendPacket(
                                    new CPacketConfirmTransaction(wid, (short) aid, true));
                        }
                    }
                },
                System.currentTimeMillis() + 1 + patternRandom.nextInt(3)
        ));
    }

    public double getHorizontal() { return horizontal.getValue(); }
    public double getVertical()   { return vertical.getValue();   }

    private static class TransactionEntry {
        final Runnable task;
        final long     sendTime;
        TransactionEntry(Runnable task, long sendTime) {
            this.task     = task;
            this.sendTime = sendTime;
        }
    }

    private static class TransactionStats {
        private long   lastTransactionTime = 0;
        private double averageLatency      = 50.0;
        private long   lastDelayTime       = 0;
        private int    transactionCount    = 0;

        void update() { averageLatency *= 0.99; }

        void recordIncoming(int actionId, int windowId) {
            long now = System.currentTimeMillis();
            if (lastTransactionTime > 0) {
                long latency = now - lastTransactionTime;
                averageLatency = averageLatency * 0.9 + latency * 0.1;
            }
            lastTransactionTime = now;
            transactionCount++;
        }

        double getAverageLatency()       { return averageLatency; }
        long   getOptimalPacketSpacing() { return (long)(averageLatency * 0.7) + 2; }
        long   getTimeSinceLastDelay()   { return System.currentTimeMillis() - lastDelayTime; }
        int    getNextPredictedAction()  { return transactionCount + 1000; }
    }
}
