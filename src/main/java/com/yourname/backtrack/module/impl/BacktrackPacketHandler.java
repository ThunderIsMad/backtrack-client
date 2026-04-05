package com.yourname.backtrack.module.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketEntity;
import net.minecraft.network.play.server.SPacketEntityHeadLook;
import net.minecraft.network.play.server.SPacketEntityTeleport;
import net.minecraft.network.play.server.SPacketPlayerPosLook;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BacktrackPacketHandler extends ChannelInboundHandlerAdapter {

    private static final Logger LOGGER       = LogManager.getLogger("BacktrackMod");
    private static final int    MAX_QUEUE_SIZE = 100;

    private static Field ENTITY_ID_FIELD           = null;
    private static Field HEAD_LOOK_ENTITY_ID_FIELD = null;

    static {
        try {
            // Using ReflectionHelper to support both MCP and SRG names for obfuscated environments
            ENTITY_ID_FIELD = ReflectionHelper.findField(SPacketEntity.class, "entityId", "field_149074_a");
            ENTITY_ID_FIELD.setAccessible(true);
        } catch (Exception e) {
            LOGGER.warn("[Backtrack] SPacketEntity.entityId reflection failed: " + e.getMessage());
        }

        try {
            // Using ReflectionHelper to support both MCP and SRG names for obfuscated environments
            HEAD_LOOK_ENTITY_ID_FIELD = ReflectionHelper.findField(SPacketEntityHeadLook.class, "entityId", "field_149370_a");
            HEAD_LOOK_ENTITY_ID_FIELD.setAccessible(true);
        } catch (Exception e) {
            LOGGER.warn("[Backtrack] SPacketEntityHeadLook.entityId reflection failed: " + e.getMessage());
        }
    }

    private final BacktrackModule module;
    public final ConcurrentLinkedQueue<TimedPacket> packetQueue = new ConcurrentLinkedQueue<>();

    public BacktrackPacketHandler(BacktrackModule module) {
        this.module = module;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (msg instanceof SPacketPlayerPosLook) {
                packetQueue.clear();
                super.channelRead(ctx, msg);
                return;
            }

            if (!module.isLagWindowActive()) {
                super.channelRead(ctx, msg);
                return;
            }

            int targetId = module.getTrackedEntityId();
            if (targetId != -1) {
                boolean shouldBuffer = false;

                if (msg instanceof SPacketEntity
                        && getEntityId(msg) == targetId) {
                    shouldBuffer = true;
                } else if (msg instanceof SPacketEntityTeleport
                        && ((SPacketEntityTeleport) msg).getEntityId() == targetId) {
                    shouldBuffer = true;
                } else if (msg instanceof SPacketEntityHeadLook
                        && getHeadLookEntityId(msg) == targetId) {
                    shouldBuffer = true;
                }

                if (shouldBuffer) {
                    if (packetQueue.size() >= MAX_QUEUE_SIZE) {
                        TimedPacket oldest = packetQueue.poll();
                        if (oldest != null) {
                            final TimedPacket o = oldest;
                            Minecraft.getMinecraft().addScheduledTask(
                                    () -> PacketUtils.receivePacket(o.getPacket()));
                        }
                    }

                    packetQueue.add(new TimedPacket((Packet<?>) msg));
                    return;
                }
            }
        } catch (Exception ignored) {}

        super.channelRead(ctx, msg);
    }

    public void clearBuffer() {
        packetQueue.clear();
    }

    private int getEntityId(Object packet) {
        try {
            if (ENTITY_ID_FIELD != null) {
                return ENTITY_ID_FIELD.getInt(packet);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private int getHeadLookEntityId(Object packet) {
        try {
            if (HEAD_LOOK_ENTITY_ID_FIELD != null) {
                return HEAD_LOOK_ENTITY_ID_FIELD.getInt(packet);
            }
        } catch (Exception ignored) {}
        return -1;
    }
}
