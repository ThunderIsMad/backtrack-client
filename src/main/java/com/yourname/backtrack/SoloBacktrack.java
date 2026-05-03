package com.yourname.backtrack;

import com.yourname.backtrack.config.ConfigManager;
import com.yourname.backtrack.gui.GuiOpener;
import com.yourname.backtrack.gui.GuiTheme;
import com.yourname.backtrack.hud.HudControlHandler;
import com.yourname.backtrack.hud.HudRenderer;
import com.yourname.backtrack.hud.HudSettings;
import com.yourname.backtrack.util.IntaveChatLogger;
import com.yourname.backtrack.input.KeybindHandler;
import com.yourname.backtrack.module.ModuleManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;

@Mod(modid = "solobacktrack", name = "Solo Backtrack", version = "1.0")
public class SoloBacktrack {

    private static SoloBacktrack instance;
    private ModuleManager moduleManager;
    private HudSettings hudSettings;
    private ConfigManager configManager;
    private GuiTheme guiTheme;

    public static SoloBacktrack getInstance() {
        return instance;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public HudSettings getHudSettings() {
        return hudSettings;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        instance = this;
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Minecraft.getMinecraft() is guaranteed non-null by postInit
        moduleManager = new ModuleManager();
        hudSettings = new HudSettings();
        configManager = new ConfigManager();
        guiTheme = new GuiTheme();

        configManager.loadHudSettings(hudSettings);
        configManager.loadModuleKeybinds(moduleManager);
        configManager.loadModuleSettings(moduleManager);
        configManager.loadModuleHudSettings(moduleManager);
        configManager.loadModuleStates(moduleManager);

        MinecraftForge.EVENT_BUS.register(new KeybindHandler(moduleManager, configManager));
        MinecraftForge.EVENT_BUS.register(new HudRenderer(moduleManager, hudSettings));
        MinecraftForge.EVENT_BUS.register(new HudControlHandler(hudSettings, configManager));
        MinecraftForge.EVENT_BUS.register(new GuiOpener(moduleManager, configManager, hudSettings, guiTheme));
        MinecraftForge.EVENT_BUS.register(new IntaveChatLogger());
    }
}
