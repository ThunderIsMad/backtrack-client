package com.yourname.backtrack.input;

import com.yourname.backtrack.config.ConfigManager;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.module.ModuleManager;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

public class KeybindHandler {

    private final ModuleManager moduleManager;
    private final ConfigManager configManager;

    public KeybindHandler(ModuleManager moduleManager, ConfigManager configManager) {
        this.moduleManager = moduleManager;
        this.configManager = configManager;
    }

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        for (Module module : moduleManager.getModules()) {
            if (module.getKeyBinding().isPressed()) {
                module.toggle();
                configManager.saveModuleStates(moduleManager);
            }
        }
    }

    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        for (Module module : moduleManager.getModules()) {
            if (module.isEnabled()) {
                module.onInputUpdate(event);
            }
        }
    }
}

