package com.yourname.backtrack.module.impl;

import com.yourname.backtrack.gui.BacktrackInfoEditorScreen;
import com.yourname.backtrack.module.Category;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.setting.ActionSetting;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import com.yourname.backtrack.setting.SettingGroup;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.Arrays;
import java.util.Locale;

public class BacktrackModule extends Module {

    // -------------------------------------------------------------------------
    // Settings — MAIN group
    // -------------------------------------------------------------------------

    private final NumberSetting delay = new NumberSetting(
            "Delay", 120.0, 10.0, 400.0, 10.0, SettingGroup.MAIN);

    private final NumberSetting range = new NumberSetting(
            "Range", 4.0, 1.0, 8.0, 0.5, SettingGroup.MAIN);

    private final BooleanSetting onlyPlayers = new BooleanSetting(
            "OnlyPlayers", true, SettingGroup.MAIN);

    private final BooleanSetting requireVisible = new BooleanSetting(
            "RequireVisible", false, SettingGroup.MAIN);

    // autoTarget: show box/tracer and pre-lock target before the first swing
    private final BooleanSetting autoTarget = new BooleanSetting(
            "AutoTarget", true, SettingGroup.MAIN);

    private final BooleanSetting disableOnKill = new BooleanSetting(
            "DisableOnKill", false, SettingGroup.MAIN);

    private final BooleanSetting resetOnWorldChange = new BooleanSetting(
            "ResetOnWorldChange", true, SettingGroup.MAIN);

    private final ModeSetting targetPriority = new ModeSetting(
            "TargetPriority",
            Arrays.asList("Distance", "Health", "Angle"),
            "Distance",
            SettingGroup.MAIN);

    private final BooleanSetting showBox = new BooleanSetting(
            "ShowBox", true, SettingGroup.MAIN);

    private final BooleanSetting showTracer = new BooleanSetting(
            "ShowTracer", true, SettingGroup.MAIN);

    // -------------------------------------------------------------------------
    // Settings — DEBUG_WINDOW group
    // -------------------------------------------------------------------------

    private final BooleanSetting showInfo = new BooleanSetting(
            "ShowInfo", true, SettingGroup.DEBUG_WINDOW);

    private final ActionSetting openInfoEditor = new ActionSetting(
            "Open Backtrack Info Editor",
            context -> mc.displayGuiScreen(new BacktrackInfoEditorScreen(
                    context.getClickGuiScreen(),
                    this,
                    context.getModuleManager(),
                    context.getConfigManager(),
                    context.getGuiTheme())),
            SettingGroup.DEBUG_WINDOW);

    private final ActionSetting resetInfoPositionSetting = new ActionSetting(
            "Reset Backtrack Info Position",
            this::resetInfoPosition,
            SettingGroup.DEBUG_WINDOW);

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------

    private final BacktrackDebugState debugState  = new BacktrackDebugState();
    private static final int          MAX_ATTACK_TICKS = 15;
    private int                       attackTicks = 0;
    private boolean                   lagWindowActive = false;
    private BacktrackPacketHandler    packetHandler   = null;
    private int                       activeDelayMs   = 0;

    // Debug overlay position (0,0 = auto-center on first render)
    private int infoX = 0;
    private int infoY = 0;

    // Pre-allocated color arrays — indexed by render state; never mutated by callers
    private static final float[] COLOR_READY  = {0.20F, 1.00F, 0.35F};
    private static final float[] COLOR_WARMUP = {1.00F, 0.65F, 0.15F};
    private static final float[] COLOR_ACTIVE = {0.35F, 0.65F, 1.00F};

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public BacktrackModule() {
        super("Backtrack", Category.COMBAT, Keyboard.KEY_B);

        addSettings(
                delay,
                range,
                onlyPlayers,
                requireVisible,
                autoTarget,
                disableOnKill,
                resetOnWorldChange,
                targetPriority,
                showBox,
                showTracer,
                showInfo,
                openInfoEditor,
                resetInfoPositionSetting
        );

        addHudSettings();
    }

    // -------------------------------------------------------------------------
    // Getters (used by GUI, ConfigManager, BacktrackPacketHandler)
    // -------------------------------------------------------------------------

    public NumberSetting  getDelay()             { return delay; }
    public NumberSetting  getRange()             { return range; }
    public BooleanSetting getOnlyPlayers()       { return onlyPlayers; }
    public BooleanSetting getRequireVisible()    { return requireVisible; }
    public BooleanSetting getDisableOnKill()     { return disableOnKill; }
    public BooleanSetting getResetOnWorldChange(){ return resetOnWorldChange; }
    public ModeSetting    getTargetPriority()    { return targetPriority; }
    public BooleanSetting getShowBox()           { return showBox; }
    public BooleanSetting getShowTracer()        { return showTracer; }
    public BooleanSetting getShowInfo()          { return showInfo; }

    public boolean isTracking(int entityId) {
        return debugState.isActive() && debugState.getTargetEntityId() == entityId;
    }

    public int getTrackedEntityId() {
        return debugState.isActive() ? debugState.getTargetEntityId() : -1;
    }

    public boolean isLagWindowActive() { return lagWindowActive; }
    public long    getDelayMs()        { return (long) delay.getValue(); }

    public BacktrackDebugState getDebugState() { return debugState; }

    public int  getInfoX()               { return infoX; }
    public void setInfoX(int x)          { this.infoX = x; }
    public int  getInfoY()               { return infoY; }
    public void setInfoY(int y)          { this.infoY = y; }
    public void setInfoPosition(int x, int y) { infoX = x; infoY = y; }
    public int  getInfoPanelWidth()      { return 150; }
    public int  getInfoPanelHeight()     { return 34; }

    // -------------------------------------------------------------------------
    // Enable / Disable
    // -------------------------------------------------------------------------

    @Override
    protected void onEnable() {
        clearTracking("SEARCH");
        injectPipelineHandler();
        super.onEnable();
    }

    @Override
    protected void onDisable() {
        removePipelineHandler();
        clearTracking("OFF");
        super.onDisable();
    }

    // -------------------------------------------------------------------------
    // HUD text
    // -------------------------------------------------------------------------

    @Override
    public String getHudText() {
        if (!isEnabled()) return "Backtrack [OFF]";
        if (!debugState.isActive()) return "Backtrack [IDLE]";

        // When no lag window is open (autoTarget pre-lock), show READY
        if (!lagWindowActive) {
            return String.format(Locale.US, "Backtrack [%s | %.1fm | READY]",
                    debugState.getTargetName(), debugState.getDistance());
        }

        // During the active lag window, show the remaining tick countdown
        return String.format(Locale.US, "Backtrack [%s | %.1fm | %dt]",
                debugState.getTargetName(),
                debugState.getDistance(),
                Math.max(0, MAX_ATTACK_TICKS - attackTicks)); // clamped — never goes negative
    }

    // -------------------------------------------------------------------------
    // Tick
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!isEnabled()) return;

        if (mc.player == null || mc.world == null) {
            if (resetOnWorldChange.getValue()) clearTracking("NO WORLD");
            return;
        }

        final long tickDelayMs = (long) delay.getValue();

        // Re-inject if the Netty channel was replaced after a disconnect/reconnect
        // while the module stayed enabled. The old handler is gone with the old Channel.
        if (mc.getConnection() != null) {
            io.netty.channel.Channel ch = mc.getConnection().getNetworkManager().channel();
            if (ch.pipeline().get("backtrack_handler") == null) {
                injectPipelineHandler();
            }
        }

        // Lag window tick countdown — only runs after a real attack
        if (lagWindowActive) {
            attackTicks++;
            if (attackTicks > MAX_ATTACK_TICKS) {
                lagWindowActive = false;
                attackTicks = 0;
                releaseAll();
                debugState.reset("IDLE");
            }
        }

        // Kill detection
        EntityLivingBase tracked = getTrackedTargetRaw();
        if (disableOnKill.getValue() && tracked != null
                && (tracked.isDead || tracked.getHealth() <= 0.0F)) {
            setEnabled(false);
            return;
        }

        // Auto-acquire/update the nearest valid target for box/tracer display before any attack.
        // delayMs=0 → begin() sets status to READY (green) immediately.
        // lagWindowActive stays false, so no packet buffering occurs yet.
        if (!lagWindowActive && autoTarget.getValue()) {
            EntityLivingBase best = findBestTarget();
            if (best != null) {
                if (!debugState.isActive() || debugState.getTargetEntityId() != best.getEntityId()) {
                    activeDelayMs = (int) tickDelayMs;
                    debugState.begin(best.getEntityId(), best.getName(),
                            mc.player.getDistance(best), 0);
                }
            } else if (debugState.isActive()) {
                debugState.reset("SEARCH");
            }
        }

        // Packet drain — TickEvent.Phase.END is on the game thread, so
        // processPacket() is called directly. No addScheduledTask needed here;
        // using it would add a full tick of phantom-hit latency.
        if (packetHandler != null && mc.getConnection() != null) {
            while (!packetHandler.packetQueue.isEmpty()) {
                TimedPacket tp = packetHandler.packetQueue.peek();
                if (tp != null && tp.hasExpired(tickDelayMs)) {
                    packetHandler.packetQueue.poll();
                    PacketUtils.receivePacket(tp.getPacket());
                } else {
                    break;
                }
            }
        }

        // Update debug overlay state
        if (debugState.isActive()) {
            debugState.updateRemaining(System.currentTimeMillis(), activeDelayMs);
            EntityLivingBase t = getTrackedTarget();
            if (t != null) debugState.updateDistance(mc.player.getDistance(t));
        }
    }

    // -------------------------------------------------------------------------
    // Attack event
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onAttackInput(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (!isEnabled()) return;
        if (!(event.getTarget() instanceof EntityLivingBase)) return;
        onAttack(event.getTarget().getEntityId());
    }

    public void onAttack(int entityId) {
        if (!isEnabled()) return;
        attackTicks    = 0;
        lagWindowActive = true;
        activeDelayMs  = (int) delay.getValue();

        // Always refresh debugState on attack — even if the target is the same —
        // so the countdown window starts from now, not from the autoTarget acquire time
        if (mc.world != null) {
            Entity e = mc.world.getEntityByID(entityId);
            if (e instanceof EntityLivingBase) {
                EntityLivingBase living = (EntityLivingBase) e;
                debugState.begin(entityId, living.getName(),
                        mc.player.getDistance(living), activeDelayMs);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Render — world (box + tracer)
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.world == null) return;

        EntityLivingBase target = getTrackedTarget();
        if (target == null) return;

        float[] color = getStateColor();

        if (showBox.getValue()) {
            drawEntityBox(target, event.getPartialTicks(),
                    color[0], color[1], color[2]);
        }

        if (showTracer.getValue()) {
            drawTracer(target, event.getPartialTicks(),
                    color[0], color[1], color[2]);
        }
    }

    // -------------------------------------------------------------------------
    // Render — debug overlay
    // -------------------------------------------------------------------------

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Text event) {
        if (!isEnabled() || !showInfo.getValue()) return;
        if (mc.player == null || mc.world == null) return;

        int panelWidth = getInfoPanelWidth();
        int panelX     = infoX;
        int panelY     = infoY;

        // Auto-center on the first render when the position hasn't been set yet
        if (panelX == 0 && panelY == 0) {
            panelX = event.getResolution().getScaledWidth() / 2 - panelWidth / 2;
            panelY = 8;
            infoX  = panelX;
            infoY  = panelY;
        }

        String line1;
        String line2;
        String line3;

        if (!debugState.isActive()) {
            line1 = "Backtrack Debug";
            line2 = "Status: " + debugState.getStatus();
            line3 = "Target: NONE";
        } else {
            line1 = "Backtrack Debug";
            line2 = String.format(Locale.US, "Target: %s (%.1fm)",
                    debugState.getTargetName(), debugState.getDistance());
            line3 = "State: " + debugState.getStatus() + " | " + getDelayText();
        }

        Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + 34, 0x90000000);
        Gui.drawRect(panelX, panelY, panelX + panelWidth, panelY + 2, getPanelAccentColor());

        mc.fontRenderer.drawStringWithShadow(line1, panelX + 5, panelY + 5,  0xFFFFFF);
        mc.fontRenderer.drawStringWithShadow(line2, panelX + 5, panelY + 15, 0xFFFFFF);
        mc.fontRenderer.drawStringWithShadow(line3, panelX + 5, panelY + 25, 0xFFFFFF);
    }

    // -------------------------------------------------------------------------
    // Targeting helpers
    // -------------------------------------------------------------------------

    private EntityLivingBase findBestTarget() {
        EntityLivingBase tracked = getTrackedTarget();
        if (tracked != null && isValidTarget(tracked, 0.0)) return tracked;

        EntityLivingBase best      = null;
        double           bestScore = Double.MAX_VALUE;
        final String     priority  = targetPriority.getValue();

        for (Entity entity : mc.world.loadedEntityList) {
            if (!(entity instanceof EntityLivingBase)) continue;
            EntityLivingBase living = (EntityLivingBase) entity;
            if (!isValidTarget(living, 0.0)) continue;

            double score = getTargetScore(living, priority);
            if (score < bestScore) {
                bestScore = score;
                best      = living;
            }
        }

        return best;
    }

    private boolean isValidTarget(EntityLivingBase living, double extraRange) {
        if (living == null)                                          return false;
        if (living == mc.player)                                     return false;
        if (living.isDead || living.getHealth() <= 0.0F)            return false;
        if (onlyPlayers.getValue() && !(living instanceof EntityPlayer)) return false;
        if (requireVisible.getValue() && !mc.player.canEntityBeSeen(living)) return false;
        if (mc.player.getDistance(living) > range.getValue() + extraRange) return false;
        return isWithinFov(living);
    }

    private boolean isWithinFov(EntityLivingBase living) {
        return true; // expand later if a FOV setting is added
    }

    private double getTargetScore(EntityLivingBase living, String priority) {
        if ("Health".equalsIgnoreCase(priority)) {
            return living.getHealth();
        }
        if ("Angle".equalsIgnoreCase(priority)) {
            return Math.abs(getAngleDifference(mc.player.rotationYaw,
                    getYawToEntity(living)));
        }
        return mc.player.getDistance(living);
    }

    private float getYawToEntity(EntityLivingBase entity) {
        double dx = entity.posX - mc.player.posX;
        double dz = entity.posZ - mc.player.posZ;
        return (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
    }

    private float getAngleDifference(float current, float target) {
        float diff = (target - current) % 360.0F;
        while (diff <= -180.0F) diff += 360.0F;
        while (diff >   180.0F) diff -= 360.0F;
        return diff;
    }

    // -------------------------------------------------------------------------
    // State management
    // -------------------------------------------------------------------------

    private void clearTracking(String status) {
        activeDelayMs   = 0;
        attackTicks     = 0;
        lagWindowActive = false;
        debugState.reset(status);
    }

    public void releaseAll() {
        if (packetHandler == null) return;
        // releaseAll() is called from onDisable() which runs on the game thread,
        // so processPacket() directly — no addScheduledTask needed
        TimedPacket tp;
        while ((tp = packetHandler.packetQueue.poll()) != null) {
            PacketUtils.receivePacket(tp.getPacket());
        }
    }

    public void resetInfoPosition() {
        ScaledResolution sr = new ScaledResolution(mc);
        infoX = sr.getScaledWidth() / 2 - getInfoPanelWidth() / 2;
        infoY = 8;
        sendClientMessage("Backtrack info position reset");
    }

    // -------------------------------------------------------------------------
    // Pipeline injection
    // -------------------------------------------------------------------------

    private void injectPipelineHandler() {
        if (mc.getConnection() == null) return;
        io.netty.channel.Channel channel =
                mc.getConnection().getNetworkManager().channel();
        if (channel.pipeline().get("backtrack_handler") != null) return;
        packetHandler = new BacktrackPacketHandler(this);
        channel.pipeline().addBefore("packet_handler", "backtrack_handler", packetHandler);
    }

    private void removePipelineHandler() {
        if (mc.getConnection() == null) return;
        io.netty.channel.Channel channel =
                mc.getConnection().getNetworkManager().channel();
        if (channel.pipeline().get("backtrack_handler") == null) return;
        if (packetHandler != null) packetHandler.clearBuffer();
        channel.pipeline().remove("backtrack_handler");
        packetHandler = null;
    }

    // -------------------------------------------------------------------------
    // Entity lookups
    // -------------------------------------------------------------------------

    private EntityLivingBase getTrackedTarget() {
        EntityLivingBase living = getTrackedTargetRaw();
        if (living == null) return null;
        if (living.isDead || living.getHealth() <= 0.0F) return null;
        return living;
    }

    private EntityLivingBase getTrackedTargetRaw() {
        if (!debugState.isActive() || mc.world == null) return null;
        Entity e = mc.world.getEntityByID(debugState.getTargetEntityId());
        return (e instanceof EntityLivingBase) ? (EntityLivingBase) e : null;
    }

    // -------------------------------------------------------------------------
    // Render utilities
    // -------------------------------------------------------------------------

    private String getDelayText() {
        if (activeDelayMs > 0) {
            return debugState.getRemainingDelay() + "/" + activeDelayMs + "ms";
        }
        return debugState.getRemainingDelay() + "ms";
    }

    private float[] getStateColor() {
        if ("READY".equals(debugState.getStatus()))  return COLOR_READY;
        if ("WARMUP".equals(debugState.getStatus())) return COLOR_WARMUP;
        return COLOR_ACTIVE;
    }

    private int getPanelAccentColor() {
        if ("READY".equals(debugState.getStatus()))  return 0xAA33DD66;
        if ("WARMUP".equals(debugState.getStatus())) return 0xAAFFAA33;
        return 0xAA55AAFF;
    }

    private void drawEntityBox(EntityLivingBase entity, float pt,
                               float r, float g, float b) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * pt
                - mc.getRenderManager().viewerPosX;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * pt
                - mc.getRenderManager().viewerPosY;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * pt
                - mc.getRenderManager().viewerPosZ;

        AxisAlignedBB bb = entity.getEntityBoundingBox()
                .offset(-entity.posX, -entity.posY, -entity.posZ)
                .offset(x, y, z)
                .grow(0.05D);

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.glLineWidth(2.0F);

        RenderGlobal.drawSelectionBoundingBox(bb, r, g, b, (float) 0.9);

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }

    private void drawTracer(EntityLivingBase entity, float pt,
                            float r, float g, float b) {
        double sx = mc.player.lastTickPosX + (mc.player.posX - mc.player.lastTickPosX) * pt
                - mc.getRenderManager().viewerPosX;
        double sy = mc.player.lastTickPosY + (mc.player.posY - mc.player.lastTickPosY) * pt
                - mc.getRenderManager().viewerPosY + mc.player.getEyeHeight();
        double sz = mc.player.lastTickPosZ + (mc.player.posZ - mc.player.lastTickPosZ) * pt
                - mc.getRenderManager().viewerPosZ;

        double ex = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * pt
                - mc.getRenderManager().viewerPosX;
        double ey = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * pt
                - mc.getRenderManager().viewerPosY + entity.height * 0.5D;
        double ez = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * pt
                - mc.getRenderManager().viewerPosZ;

        GlStateManager.pushMatrix();
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        GL11.glLineWidth(1.5F);
        GL11.glColor4f(r, g, b, (float) 0.85);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(sx, sy, sz);
        GL11.glVertex3d(ex, ey, ez);
        GL11.glEnd();

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
        GlStateManager.popMatrix();
    }
}