package com.yourname.backtrack.setting;

import com.yourname.backtrack.config.ConfigManager;
import com.yourname.backtrack.gui.ClickGuiScreen;
import com.yourname.backtrack.gui.GuiTheme;
import module.ModuleManager;
import setting.SettingGroup;

public class ActionSetting extends Setting {

    public interface ContextAction {
        void run(ActionContext context);
    }

    public static class ActionContext {
        private final ClickGuiScreen clickGuiScreen;
        private final ModuleManager moduleManager;
        private final ConfigManager configManager;
        private final GuiTheme guiTheme;

        public ActionContext(ClickGuiScreen clickGuiScreen,
                             ModuleManager moduleManager,
                             ConfigManager configManager,
                             GuiTheme guiTheme) {
            this.clickGuiScreen = clickGuiScreen;
            this.moduleManager = moduleManager;
            this.configManager = configManager;
            this.guiTheme = guiTheme;
        }

        public ClickGuiScreen getClickGuiScreen() {
            return clickGuiScreen;
        }

        public ModuleManager getModuleManager() {
            return moduleManager;
        }

        public ConfigManager getConfigManager() {
            return configManager;
        }

        public GuiTheme getGuiTheme() {
            return guiTheme;
        }
    }

    private final Runnable action;
    private final ContextAction contextAction;

    public ActionSetting(String name, Runnable action) {
        this(name, action, SettingGroup.MAIN);
    }

    public ActionSetting(String name, Runnable action, SettingGroup group) {
        super(name, group);
        this.action = action;
        this.contextAction = null;
    }

    public ActionSetting(String name, ContextAction contextAction) {
        this(name, contextAction, SettingGroup.MAIN);
    }

    public ActionSetting(String name, ContextAction contextAction, SettingGroup group) {
        super(name, group);
        this.action = null;
        this.contextAction = contextAction;
    }

    public void trigger() {
        if (action != null) {
            action.run();
        }
    }

    public void trigger(ActionContext context) {
        if (contextAction != null) {
            contextAction.run(context);
        } else {
            trigger();
        }
    }
}
