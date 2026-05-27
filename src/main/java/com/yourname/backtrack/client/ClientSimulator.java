package com.yourname.backtrack.client;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.util.math.AxisAlignedBB;

public final class ClientSimulator {
    public static final ClientSimulator INSTANCE = new ClientSimulator();

    private final MovementSimState s = new MovementSimState();
    private final VanillaPlayerCollider collider = new VanillaPlayerCollider();
    private final MovementInputCapture inputCapture = new MovementInputCapture();
    private final MovementPhysicsEngine physics = new MovementPhysicsEngine();

    private static final double GRAVITY = 0.08, Y_MULT = 0.98, AIR_SLIP = 0.91;
    /** Push strength applied per overlapping player (vanilla: 0.05 per axis). */
    private static final double ENTITY_PUSH_STRENGTH = 0.05;

    /** When true, Velocity Reduce/Reverse do not modify player motion (debug/shadow). */
    public boolean shadowMode = false;

    private ClientSimulator() {}

    public MovementSimState state() { return s; }
    public void reset() { s.reset(); }

    public void beginTick() { s.beginTick(); }

    public void syncVerifiedFromPlayer(Minecraft mc) {
        if (mc.player == null) return;
        s.playerPosX = mc.player.posX;
        s.playerPosY = mc.player.posY;
        s.playerPosZ = mc.player.posZ;
        if (!s.positionInitialized) {
            s.verifiedX = mc.player.posX;
            s.verifiedY = mc.player.posY;
            s.verifiedZ = mc.player.posZ;
            s.lastX = s.verifiedX;
            s.lastY = s.verifiedY;
            s.lastZ = s.verifiedZ;
            s.positionInitialized = true;
            return;
        }
        double drift = Math.sqrt(sq(mc.player.posX - s.verifiedX) + sq(mc.player.posY - s.verifiedY)
                + sq(mc.player.posZ - s.verifiedZ));
        if (drift > 8.0 || (drift > 1.0 && s.isInVelocityWindow())) {
            s.verifiedX = mc.player.posX;
            s.verifiedY = mc.player.posY;
            s.verifiedZ = mc.player.posZ;
            s.physicsPacketRelinkFlyVL = 0;
        }
    }

    public void predictFlyingPacketBeforeVelocity() {
        if (s.pastVelocity != 0) return;
        double mx = s.baseMotionXBeforeVelocity * AIR_SLIP;
        double my = (s.baseMotionYBeforeVelocity - GRAVITY) * Y_MULT;
        double mz = s.baseMotionZBeforeVelocity * AIR_SLIP;
        if (mx == 0 || my == 0 || mz == 0) return;

        Minecraft mc = Minecraft.getMinecraft();
        VanillaPlayerCollider.CollideResult r = collider.collide(mc, s.verifiedX, s.verifiedY, s.verifiedZ, mx, my, mz);
        if ((r.onGround || s.onGround) && (r.motionX * r.motionX + r.motionY * r.motionY + r.motionZ * r.motionZ) < 0.009) {
            s.physicsUnpredictableVelocityExpected = true;
            s.pastFlyingPacketAccurate = 0;
        }
    }

    /**
     * Scans nearby EntityPlayer instances and accumulates the vanilla applyEntityCollision
     * push into s.entityPushX / s.entityPushZ.  Called once per tick before simulate().
     * Vanilla logic (EntityLivingBase.collideWithNearbyEntities):
     *   For each other player whose AABB intersects ours (expanded by 0.2 on XZ):
     *     dx = other.posX - self.posX
     *     dz = other.posZ - self.posZ
     *     dist = max(|dx|, |dz|)
     *     if dist < 0.01: use random offsets
     *     dist = sqrt(dx*dx + dz*dz)
     *     normalize, scale by 0.05, divide by dist
     *     self.motionX -= pushX; self.motionZ -= pushZ
     */
    public void collectEntityPush(Minecraft mc) {
        if (mc.player == null || mc.world == null) return;

        double pushX = 0, pushZ = 0;
        AxisAlignedBB selfBox = mc.player.getEntityBoundingBox().expand(0.2, 0.0, 0.2);

        for (net.minecraft.entity.Entity e : mc.world.loadedEntityList) {
            if (!(e instanceof EntityPlayer)) continue;
            if (e == mc.player) continue;
            if (!e.noClip && !mc.player.noClip) {
                AxisAlignedBB otherBox = e.getEntityBoundingBox();
                if (selfBox.intersects(otherBox)) {
                    double dx = e.posX - mc.player.posX;
                    double dz = e.posZ - mc.player.posZ;
                    double maxDelta = Math.max(Math.abs(dx), Math.abs(dz));
                    if (maxDelta >= 0.01) {
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        dx /= dist;
                        dz /= dist;
                        double scale = Math.min(1.0, ENTITY_PUSH_STRENGTH / maxDelta);
                        // other pushes us: we subtract (vanilla: attacker subtracts from self)
                        pushX -= dx * scale;
                        pushZ -= dz * scale;
                    }
                }
            }
        }

        s.entityPushX = pushX;
        s.entityPushZ = pushZ;
    }

    public void simulate() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;

        collectEntityPush(mc);
        inputCapture.capture(s, mc);
        maybeSearchKeys(mc);
        physics.simulate(s);
        ToleranceCalculator.compute(s);
        SimDebug.logTick(this);
    }

    private void maybeSearchKeys(Minecraft mc) {
        double[] pred = physics.simulateOneTick(s, s.forwardKey, s.strafeKey);
        double err = Math.sqrt(sq(pred[0] - mc.player.motionX) + sq(pred[2] - mc.player.motionZ));
        ToleranceCalculator.compute(s);
        boolean needSearch = err > Math.max(s.toleranceXZ * 2, 0.008)
                || (s.pastVelocity > 0 && s.pastVelocity < 25);
        if (!needSearch) return;

        int[] search = InputKeySearcher.search(s, physics, mc.player.motionX, mc.player.motionZ);
        if (search != null) {
            s.forwardKey = search[0];
            s.strafeKey = search[1];
        }
    }

    public void recalculateAfterOutgoingPacket() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        predictFlyingPacketBeforeVelocity();
        inputCapture.capture(s, mc);
        physics.simulate(s);
        ToleranceCalculator.compute(s);
    }

    public void syncFromPlayer() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        s.baseMotionX = mc.player.motionX;
        s.baseMotionY = mc.player.motionY;
        s.baseMotionZ = mc.player.motionZ;
    }

    public void advanceVerifiedFromPlayer(Minecraft mc) {
        if (mc.player == null) return;
        double dx = mc.player.posX - s.lastX;
        double dy = mc.player.posY - s.lastY;
        double dz = mc.player.posZ - s.lastZ;
        if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) > 1e-6) {
            s.verifiedX = mc.player.posX;
            s.verifiedY = mc.player.posY;
            s.verifiedZ = mc.player.posZ;
        }
        s.lastX = mc.player.posX;
        s.lastY = mc.player.posY;
        s.lastZ = mc.player.posZ;
    }

    public void updateOnGround(Minecraft mc) {
        s.onGround = collider.isOnGround(mc, s.verifiedX, s.verifiedY, s.verifiedZ);
    }

    public void prepareNextTick() {
        Minecraft mc = Minecraft.getMinecraft();
        double slip = s.lastOnGround
                ? MovementFriction.groundSlipperinessForDecay(mc, s.verifiedX, s.verifiedY, s.verifiedZ)
                : AIR_SLIP;
        s.baseMotionX *= slip;
        s.baseMotionY = s.lastOnGround ? 0.0 : (s.baseMotionY - GRAVITY) * Y_MULT;
        s.baseMotionZ *= slip;
        if (Math.abs(s.baseMotionX) < s.resetMotion) s.baseMotionX = 0;
        if (Math.abs(s.baseMotionY) < s.resetMotion) s.baseMotionY = 0;
        if (Math.abs(s.baseMotionZ) < s.resetMotion) s.baseMotionZ = 0;
        if (s.inWater) {
            s.baseMotionX *= 0.8;
            s.baseMotionZ *= 0.8;
        }
        if (s.inWeb) {
            s.baseMotionX = 0;
            s.baseMotionY = 0;
            s.baseMotionZ = 0;
        }
        s.lastOnGround = s.onGround;
        if (s.pastPlayerReduceAttackPhysics < 100) s.pastPlayerReduceAttackPhysics++;
        s.pastFlyingPacketAccurate++;
    }

    public void applyVelocity(double vx, double vy, double vz) {
        s.baseMotionXBeforeVelocity = s.baseMotionX;
        s.baseMotionYBeforeVelocity = s.baseMotionY;
        s.baseMotionZBeforeVelocity = s.baseMotionZ;
        s.baseMotionX = vx;
        s.baseMotionY = vy;
        s.baseMotionZ = vz;
        s.pastExternalVelocity = 0;
        s.pastVelocity = 0;
        s.reduceTicks = 0;
    }

    public void applyExplosion(double vx, double vy, double vz) {
        s.baseMotionX += vx;
        s.baseMotionY += vy;
        s.baseMotionZ += vz;
    }

    public void handleTeleport(double x, double y, double z) {
        s.verifiedX = x;
        s.verifiedY = y;
        s.verifiedZ = z;
        s.lastX = x;
        s.lastY = y;
        s.lastZ = z;
        s.baseMotionX = 0;
        s.baseMotionY = 0;
        s.baseMotionZ = 0;
        s.predictedMotionX = 0;
        s.predictedMotionY = 0;
        s.predictedMotionZ = 0;
        s.pastExternalVelocity = 100;
        s.pastVelocity = 100;
        s.reduceTicks = 0;
        s.physicsPacketRelinkFlyVL = 0;
        s.physicsUnpredictableVelocityExpected = false;
    }

    public void onOutgoingMovement(boolean posChanged, double x, double y, double z) {
        if (posChanged) {
            s.verifiedX = x;
            s.verifiedY = y;
            s.verifiedZ = z;
            s.positionInitialized = true;
        }
        s.pastExternalVelocity++;
        s.pastVelocity++;
        s.reduceTicks = 0;
    }

    public void onAttack(EntityPlayer player, EntityLivingBase target) {
        if (player == null || target == null) return;
        if (!(target instanceof EntityPlayer)) return;

        boolean sprint = player.isSprinting();
        int kbLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.KNOCKBACK, player.getHeldItemMainhand());
        double attackDamage = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).getAttributeValue();
        boolean hasWeaponDamage = attackDamage > 0.0;

        if (hasWeaponDamage && (sprint || kbLevel > 0)) {
            s.pastPlayerReduceAttackPhysics = 0;
            boolean limitedToOneAttack = kbLevel == 0;
            if (s.reduceTicks == 0 || !limitedToOneAttack) {
                if (s.reduceTicks < 3) s.reduceTicks++;
            }
        }
    }

    public boolean isInVelocityWindow() { return s.isInVelocityWindow(); }

    public boolean isFlyingJumpExpected() {
        double px = s.predictedMotionX, py = s.predictedMotionY, pz = s.predictedMotionZ;
        if (Math.abs(px) >= 0.1 || Math.abs(pz) >= 0.1) return false;
        if (Math.abs(py - s.jumpMotion) >= 0.05) return false;
        if (!s.onGround && !s.lastOnGround) return false;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return false;
        double diffY = py - mc.player.motionY;
        return diffY > 0.01 && diffY < 0.03;
    }

    public double getExpectedX() { return s.predictedMotionX; }
    public double getExpectedY() { return s.predictedMotionY; }
    public double getExpectedZ() { return s.predictedMotionZ; }
    public double getExpectedMag() {
        return Math.sqrt(s.predictedMotionX * s.predictedMotionX + s.predictedMotionZ * s.predictedMotionZ);
    }
    public double getToleranceXZ() { ToleranceCalculator.compute(s); return s.toleranceXZ; }
    public double getToleranceY() { ToleranceCalculator.compute(s); return s.toleranceY; }
    public int getPastExternalVelocity() { return s.pastExternalVelocity; }

    private double sq(double v) { return v * v; }
}
