package com.yourname.backtrack.hud;

import com.yourname.backtrack.config.ConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class HudControlHandler {

    private final HudSettings settings;
    private final ConfigManager configManager;

    private final KeyBinding anchorKey;
    private final KeyBinding resetKey;
    private final KeyBinding leftKey;
    private final KeyBinding rightKey;
    private final KeyBinding upKey;
    private final KeyBinding downKey;
    private final KeyBinding colorKey;
    private final KeyBinding toggleHudKey;
    private final KeyBinding shadowKey;
    private final KeyBinding spacingUpKey;
    private final KeyBinding spacingDownKey;
    private final KeyBinding backgroundKey;
    private final KeyBinding textModeKey;

    public HudControlHandler(HudSettings settings, ConfigManager configManager) {
        this.settings = settings;
        this.configManager = configManager;

        anchorKey = new KeyBinding("HUD Anchor", Keyboard.KEY_F5, "Solo Backtrack HUD");
        resetKey = new KeyBinding("HUD Reset", Keyboard.KEY_F9, "Solo Backtrack HUD");
        leftKey = new KeyBinding("HUD Left", Keyboard.KEY_LEFT, "Solo Backtrack HUD");
        rightKey = new KeyBinding("HUD Right", Keyboard.KEY_RIGHT, "Solo Backtrack HUD");
        upKey = new KeyBinding("HUD Up", Keyboard.KEY_UP, "Solo Backtrack HUD");
        downKey = new KeyBinding("HUD Down", Keyboard.KEY_DOWN, "Solo Backtrack HUD");
        colorKey = new KeyBinding("HUD Color", Keyboard.KEY_F6, "Solo Backtrack HUD");
        toggleHudKey = new KeyBinding("HUD Toggle", Keyboard.KEY_F7, "Solo Backtrack HUD");
        shadowKey = new KeyBinding("HUD Shadow", Keyboard.KEY_F8, "Solo Backtrack HUD");
        spacingUpKey = new KeyBinding("HUD Spacing +", Keyboard.KEY_PRIOR, "Solo Backtrack HUD");
        spacingDownKey = new KeyBinding("HUD Spacing -", Keyboard.KEY_NEXT, "Solo Backtrack HUD");
        backgroundKey = new KeyBinding("HUD Background", Keyboard.KEY_F10, "Solo Backtrack HUD");
        textModeKey = new KeyBinding("HUD Text Mode", Keyboard.KEY_F11, "Solo Backtrack HUD");

        ClientRegistry.registerKeyBinding(anchorKey);
        ClientRegistry.registerKeyBinding(resetKey);
        ClientRegistry.registerKeyBinding(leftKey);
        ClientRegistry.registerKeyBinding(rightKey);
        ClientRegistry.registerKeyBinding(upKey);
        ClientRegistry.registerKeyBinding(downKey);
        ClientRegistry.registerKeyBinding(colorKey);
        ClientRegistry.registerKeyBinding(toggleHudKey);
        ClientRegistry.registerKeyBinding(shadowKey);
        ClientRegistry.registerKeyBinding(spacingUpKey);
        ClientRegistry.registerKeyBinding(spacingDownKey);
        ClientRegistry.registerKeyBinding(backgroundKey);
        ClientRegistry.registerKeyBinding(textModeKey);
    }

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (anchorKey.isPressed()) {
            settings.cycleAnchor();
            saveAndNotify("HUD anchor: " + settings.getAnchorName());
        }

        if (resetKey.isPressed()) {
            settings.reset();
            saveAndNotify("HUD reset");
        }

        if (leftKey.isPressed()) {
            settings.moveLeft();
            saveAndNotify("HUD X: " + settings.getX());
        }

        if (rightKey.isPressed()) {
            settings.moveRight();
            saveAndNotify("HUD X: " + settings.getX());
        }

        if (upKey.isPressed()) {
            settings.moveUp();
            saveAndNotify("HUD Y: " + settings.getY());
        }

        if (downKey.isPressed()) {
            settings.moveDown();
            saveAndNotify("HUD Y: " + settings.getY());
        }

        if (colorKey.isPressed()) {
            settings.cycleColor();
            saveAndNotify("HUD color: " + settings.getColorIndex());
        }

        if (toggleHudKey.isPressed()) {
            settings.toggleVisible();
            saveAndNotify("HUD: " + (settings.isVisible() ? "ON" : "OFF"));
        }

        if (shadowKey.isPressed()) {
            settings.toggleShadow();
            saveAndNotify("HUD shadow: " + (settings.isShadow() ? "ON" : "OFF"));
        }

        if (spacingUpKey.isPressed()) {
            settings.increaseLineHeight();
            saveAndNotify("HUD spacing: " + settings.getLineHeight());
        }

        if (spacingDownKey.isPressed()) {
            settings.decreaseLineHeight();
            saveAndNotify("HUD spacing: " + settings.getLineHeight());
        }

        if (backgroundKey.isPressed()) {
            settings.toggleBackground();
            saveAndNotify("HUD background: " + (settings.isBackground() ? "ON" : "OFF"));
        }

        if (textModeKey.isPressed()) {
            settings.cycleTextMode();
            saveAndNotify("HUD text mode: " + settings.getTextModeName());
        }
    }

    private void saveAndNotify(String message) {
        configManager.saveHudSettings(settings);
        notifyClient(message);
    }

    private void notifyClient(String message) {
        if (mc().player != null) {
            mc().player.sendMessage(new TextComponentString(message));
        }
    }
}
