package com.yourname.backtrack.config;

import com.yourname.backtrack.gui.GuiTheme;
import com.yourname.backtrack.hud.HudSettings;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.module.ModuleHudSettings;
import module.ModuleManager;
import com.yourname.backtrack.module.impl.BacktrackModule;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import com.yourname.backtrack.setting.Setting;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigManager {

    private final File file;
    // Cached properties — loaded once on first access, invalidated after each write
    private Properties cachedProperties = null;

    public ConfigManager() {
        File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");

        if (!configDir.exists()) {
            configDir.mkdirs();
        }

        file = new File(configDir, "solobacktrack.properties");
    }

    public void loadHudSettings(HudSettings hudSettings) {
        Properties properties = loadProperties();

        hudSettings.setX(parseInt(properties.getProperty("hud.x"), 5));
        hudSettings.setY(parseInt(properties.getProperty("hud.y"), 5));
        hudSettings.setLineHeight(parseInt(properties.getProperty("hud.lineHeight"), 12));
        hudSettings.setVisible(parseBoolean(properties.getProperty("hud.visible"), true));
        hudSettings.setShadow(parseBoolean(properties.getProperty("hud.shadow"), true));
        hudSettings.setBackground(parseBoolean(properties.getProperty("hud.background"), true));
        hudSettings.setColorIndex(parseInt(properties.getProperty("hud.colorIndex"), 0));
        hudSettings.setAnchorIndex(parseInt(properties.getProperty("hud.anchorIndex"), 0));
        hudSettings.setTextMode(parseInt(properties.getProperty("hud.textMode"), 1));
    }

    public int loadGuiX() {
        Properties properties = loadProperties();
        return parseInt(properties.getProperty("gui.x"), 20);
    }

    public int loadGuiY() {
        Properties properties = loadProperties();
        return parseInt(properties.getProperty("gui.y"), 20);
    }

    public void saveGuiPosition(int x, int y) {
        Properties properties = loadProperties();

        properties.setProperty("gui.x", String.valueOf(x));
        properties.setProperty("gui.y", String.valueOf(y));

        saveProperties(properties);
    }


    public void loadGuiTheme(GuiTheme guiTheme) {
        Properties properties = loadProperties();

        guiTheme.setAccentIndex(parseInt(properties.getProperty("gui.theme.accent"), 0));
        guiTheme.setBackgroundIndex(parseInt(properties.getProperty("gui.theme.background"), 0));
    }

    public void saveGuiTheme(GuiTheme guiTheme) {
        Properties properties = loadProperties();

        properties.setProperty("gui.theme.accent", String.valueOf(guiTheme.getAccentIndex()));
        properties.setProperty("gui.theme.background", String.valueOf(guiTheme.getBackgroundIndex()));

        saveProperties(properties);
    }


    public void saveHudSettings(HudSettings hudSettings) {
        Properties properties = loadProperties();

        properties.setProperty("hud.x", String.valueOf(hudSettings.getX()));
        properties.setProperty("hud.y", String.valueOf(hudSettings.getY()));
        properties.setProperty("hud.lineHeight", String.valueOf(hudSettings.getLineHeight()));
        properties.setProperty("hud.visible", String.valueOf(hudSettings.isVisible()));
        properties.setProperty("hud.shadow", String.valueOf(hudSettings.isShadow()));
        properties.setProperty("hud.background", String.valueOf(hudSettings.isBackground()));
        properties.setProperty("hud.colorIndex", String.valueOf(hudSettings.getColorIndex()));
        properties.setProperty("hud.anchorIndex", String.valueOf(hudSettings.getAnchorIndex()));
        properties.setProperty("hud.textMode", String.valueOf(hudSettings.getTextMode()));

        saveProperties(properties);
    }

    public void loadModuleStates(ModuleManager moduleManager) {
        Properties properties = loadProperties();

        for (Module module : moduleManager.getModules()) {
            boolean enabled = parseBoolean(
                    properties.getProperty("module." + module.getName() + ".enabled"),
                    false
            );

            module.setEnabled(enabled);
        }
    }

    public void saveModuleStates(ModuleManager moduleManager) {
        Properties properties = loadProperties();

        for (Module module : moduleManager.getModules()) {
            properties.setProperty(
                    "module." + module.getName() + ".enabled",
                    String.valueOf(module.isEnabled())
            );
        }

        saveProperties(properties);
    }

    public void loadModuleKeybinds(ModuleManager moduleManager) {
        Properties properties = loadProperties();

        for (Module module : moduleManager.getModules()) {
            int keyCode = parseInt(
                    properties.getProperty("module." + module.getName() + ".key"),
                    module.getKeyCode()
            );

            module.setKeyCode(keyCode);
        }
    }

    public void saveModuleKeybinds(ModuleManager moduleManager) {
        Properties properties = loadProperties();

        for (Module module : moduleManager.getModules()) {
            properties.setProperty(
                    "module." + module.getName() + ".key",
                    String.valueOf(module.getKeyCode())
            );
        }

        saveProperties(properties);
    }

    public void loadModuleSettings(ModuleManager moduleManager) {
        Properties properties = loadProperties();

        for (Module module : moduleManager.getModules()) {
            for (Setting setting : module.getSettings()) {
                String key = "module." + module.getName() + ".setting." + setting.getName();

                if (setting instanceof BooleanSetting) {
                    ((BooleanSetting) setting).setValue(
                            parseBoolean(properties.getProperty(key), ((BooleanSetting) setting).getValue())
                    );
                }

                if (setting instanceof NumberSetting) {
                    ((NumberSetting) setting).setValue(
                            parseDouble(properties.getProperty(key), ((NumberSetting) setting).getValue())
                    );
                }

                if (setting instanceof ModeSetting) {
                    String value = properties.getProperty(key);
                    if (value != null) {
                        ((ModeSetting) setting).setValue(value);
                    }
                }
            }
        }
    }

    public void saveModuleSettings(ModuleManager moduleManager) {
        Properties properties = loadProperties();

        for (Module module : moduleManager.getModules()) {
            for (Setting setting : module.getSettings()) {
                String key = "module." + module.getName() + ".setting." + setting.getName();

                if (setting instanceof BooleanSetting) {
                    properties.setProperty(key, String.valueOf(((BooleanSetting) setting).getValue()));
                }

                if (setting instanceof NumberSetting) {
                    properties.setProperty(key, String.valueOf(((NumberSetting) setting).getValue()));
                }

                if (setting instanceof ModeSetting) {
                    properties.setProperty(key, ((ModeSetting) setting).getValue());
                }
            }
        }

        saveProperties(properties);
    }

    public void loadModuleHudSettings(ModuleManager moduleManager) {
        Properties properties = loadProperties();

        for (Module module : moduleManager.getModules()) {
            ModuleHudSettings hud = module.getHudSettings();
            String base = "module." + module.getName() + ".hud.";

            hud.setX(parseInt(properties.getProperty(base + "x"), hud.getX()));
            hud.setY(parseInt(properties.getProperty(base + "y"), hud.getY()));
            hud.setVisible(parseBoolean(properties.getProperty(base + "visible"), hud.isVisible()));
            hud.setShadow(parseBoolean(properties.getProperty(base + "shadow"), hud.isShadow()));
            hud.setBackground(parseBoolean(properties.getProperty(base + "background"), hud.isBackground()));
            hud.setColorIndex(parseInt(properties.getProperty(base + "color"), hud.getColorIndex()));
        }
    }

    public void saveModuleHudSettings(ModuleManager moduleManager) {
        Properties properties = loadProperties();

        for (Module module : moduleManager.getModules()) {
            ModuleHudSettings hud = module.getHudSettings();
            String base = "module." + module.getName() + ".hud.";

            properties.setProperty(base + "x", String.valueOf(hud.getX()));
            properties.setProperty(base + "y", String.valueOf(hud.getY()));
            properties.setProperty(base + "visible", String.valueOf(hud.isVisible()));
            properties.setProperty(base + "shadow", String.valueOf(hud.isShadow()));
            properties.setProperty(base + "background", String.valueOf(hud.isBackground()));
            properties.setProperty(base + "color", String.valueOf(hud.getColorIndex()));
        }

        saveProperties(properties);
    }

    public void loadBacktrackInfoPosition(ModuleManager moduleManager) {
        Properties properties = loadProperties();

        for (Module module : moduleManager.getModules()) {
            if (module instanceof BacktrackModule) {
                BacktrackModule backtrackModule = (BacktrackModule) module;

                backtrackModule.setInfoX(parseInt(properties.getProperty("backtrack.info.x"), 0));
                backtrackModule.setInfoY(parseInt(properties.getProperty("backtrack.info.y"), 0));
            }
        }
    }

    public void saveBacktrackInfoPosition(ModuleManager moduleManager) {
        Properties properties = loadProperties();

        for (Module module : moduleManager.getModules()) {
            if (module instanceof BacktrackModule) {
                BacktrackModule backtrackModule = (BacktrackModule) module;

                properties.setProperty("backtrack.info.x", String.valueOf(backtrackModule.getInfoX()));
                properties.setProperty("backtrack.info.y", String.valueOf(backtrackModule.getInfoY()));
            }
        }

        saveProperties(properties);
    }

    private Properties loadProperties() {
        if (cachedProperties != null) {
            return cachedProperties;
        }

        Properties properties = new Properties();

        if (file.exists()) {
            try (FileInputStream inputStream = new FileInputStream(file)) {
                properties.load(inputStream);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        cachedProperties = properties;
        return properties;
    }

    private void saveProperties(Properties properties) {
        cachedProperties = null; // invalidate so the next load re-reads from disk
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            properties.store(outputStream, "Solo Backtrack Config");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double parseDouble(String value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        return Boolean.parseBoolean(value);
    }
}
