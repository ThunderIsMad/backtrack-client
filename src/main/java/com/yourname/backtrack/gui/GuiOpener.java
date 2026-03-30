package com.yourname.backtrack.gui;

import com.yourname.backtrack.config.ConfigManager;
import com.yourname.backtrack.hud.HudSettings;
import com.yourname.backtrack.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class GuiOpener {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ModuleManager moduleManager;
    private final ConfigManager configManager;
    private final HudSettings hudSettings;
    private final GuiTheme guiTheme;
    private final KeyBinding openGuiKey;

    public GuiOpener(ModuleManager moduleManager, ConfigManager configManager, HudSettings hudSettings, GuiTheme guiTheme) {
        this.moduleManager = moduleManager;
        this.configManager = configManager;
        this.hudSettings = hudSettings;
        this.guiTheme = guiTheme;

        openGuiKey = new KeyBinding("Open ClickGUI", Keyboard.KEY_RSHIFT, "Solo Backtrack");
        ClientRegistry.registerKeyBinding(openGuiKey);
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (openGuiKey.isPressed()) {
            mc.displayGuiScreen(new ClickGuiScreen(moduleManager, configManager, hudSettings, guiTheme));
        }
    }
}
