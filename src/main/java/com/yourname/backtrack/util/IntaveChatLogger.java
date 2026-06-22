package com.yourname.backtrack.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class IntaveChatLogger {

    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss.SSS");

    private static final String[] TRIGGERS = {
            "moved incorrectly",
            "moving incorrectly",
            "halting game ticks",
            "moved too frequently",
            "attacked too quickly",
            "attacked too many entities",
            "is clicking statistically",
            "is sending invalid packets",
            "is interacting suspiciously",
            "is placing blocks suspiciously",
            "is performing invalid item-operations",
            "has been removed",
            "suspicious clicks",
            "click pattern",
            "knockback manipulation",
            "velocity manipulation",
            "improbable velocity",
            "physics violation",
            "VL",
            "violation"
    };

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        ITextComponent root = event.getMessage();
        String plain = root.getFormattedText().replaceAll("§[0-9a-fk-orA-FK-OR]", "").trim();

        boolean match = false;
        for (String trigger : TRIGGERS) {
            if (plain.toLowerCase().contains(trigger.toLowerCase())) { match = true; break; }
        }
        if (!match) return;

        // Route to unified FlagLogger first — catches VL numbers, trust factor, etc.
        FlagLogger.INSTANCE.logChatFlag(Minecraft.getMinecraft(), plain);

        String hover = extractHover(root);

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(TIME_FMT.format(new Date())).append("] ").append(plain);
        if (hover != null && !hover.isEmpty()) {
            sb.append("\n  ").append(hover.replace("\n", "\n  "));
        }

        File logDir = new File(Minecraft.getMinecraft().mcDataDir, "logs");
        if (!logDir.exists()) logDir.mkdirs();
        File logFile = new File(logDir, "intave-" + DATE_FMT.format(new Date()) + ".log");
        try (PrintWriter pw = new PrintWriter(new FileWriter(logFile, true))) {
            pw.println(sb.toString());
        } catch (IOException e) {
            // silent
        }
    }

    private String extractHover(ITextComponent component) {
        Style s = component.getStyle();
        if (s != null) {
            HoverEvent h = s.getHoverEvent();
            if (h != null && h.getAction() == HoverEvent.Action.SHOW_TEXT) {
                ITextComponent val = h.getValue();
                if (val != null) {
                    return val.getUnformattedText().trim();
                }
            }
        }
        for (ITextComponent sibling : component.getSiblings()) {
            String result = extractHover(sibling);
            if (result != null) return result;
        }
        return null;
    }
}