package com.yourname.backtrack.config;

import com.yourname.backtrack.gui.GuiTheme;
import com.yourname.backtrack.hud.HudSettings;
import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.module.ModuleHudSettings;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.NumberSetting;
import com.yourname.backtrack.setting.Setting;
import com.yourname.backtrack.module.ModuleManager;
import net.minecraft.client.Minecraft;

import java.io.*;
import java.util.Properties;

public class ConfigManager {

    private File file = null;
    private Properties cachedProperties = null;

    public ConfigManager() {
        // Do NOT call Minecraft.getMinecraft() here.
        // The constructor is invoked during @PostInit while the obfuscated
        // runtime maps getMinecraft() to func_71410_x.  Calling it at
        // class-init time raises NoSuchMethodError before Minecraft is ready.
        // Instead, resolve the file lazily on first access via getFile().
    }

    // Lazily resolve the config file so Minecraft.getMinecraft() is only
    // called after the game has fully initialised its instance.
    private File getFile() {
        if (file == null) {
            File configDir = new File(Minecraft.getMinecraft().mcDataDir, "backtrack");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            file = new File(configDir, "solobacktrack.properties");
        }
        return file;
    }

    // --- GUI position ---

    public int loadGuiX() {
        return parseInt(loadProperties().getProperty("gui.x"), 20);
    }

    public int loadGuiY() {
        return parseInt(loadProperties().getProperty("gui.y"), 20);
    }

    public void saveGuiPosition(int x, int y) {
        Properties p = loadProperties();
        p.setProperty("gui.x", String.valueOf(x));
        p.setProperty("gui.y", String.valueOf(y));
        saveProperties(p);
    }

    // --- HUD settings ---

    public void saveHudSettings(HudSettings hudSettings) {
        Properties p = loadProperties();
        p.setProperty("hud.x", String.valueOf(hudSettings.getX()));
        p.setProperty("hud.y", String.valueOf(hudSettings.getY()));
        p.setProperty("hud.lineHeight", String.valueOf(hudSettings.getLineHeight()));
        p.setProperty("hud.visible", String.valueOf(hudSettings.isVisible()));
        p.setProperty("hud.shadow", String.valueOf(hudSettings.isShadow()));
        p.setProperty("hud.background", String.valueOf(hudSettings.isBackground()));
        p.setProperty("hud.colorIndex", String.valueOf(hudSettings.getColorIndex()));
        p.setProperty("hud.anchorIndex", String.valueOf(hudSettings.getAnchorIndex()));
        p.setProperty("hud.textMode", String.valueOf(hudSettings.getTextMode()));
        saveProperties(p);
    }

    public void loadHudSettings(HudSettings hudSettings) {
        Properties p = loadProperties();
        hudSettings.setX(parseInt(p.getProperty("hud.x"), 5));
        hudSettings.setY(parseInt(p.getProperty("hud.y"), 5));
        hudSettings.setLineHeight(parseInt(p.getProperty("hud.lineHeight"), 12));
        hudSettings.setVisible(parseBoolean(p.getProperty("hud.visible"), true));
        hudSettings.setShadow(parseBoolean(p.getProperty("hud.shadow"), true));
        hudSettings.setBackground(parseBoolean(p.getProperty("hud.background"), true));
        hudSettings.setColorIndex(parseInt(p.getProperty("hud.colorIndex"), 0));
        hudSettings.setAnchorIndex(parseInt(p.getProperty("hud.anchorIndex"), 0));
        hudSettings.setTextMode(parseInt(p.getProperty("hud.textMode"), 1));
    }

    // --- Module states ---

    public void saveModuleStates(ModuleManager mm) {
        Properties p = loadProperties();
        for (Module module : mm.getModules()) {
            String name = module.getName();
            p.setProperty("module." + name + ".enabled", String.valueOf(module.isEnabled()));
            p.setProperty("module." + name + ".key", String.valueOf(module.getKeyCode()));
        }
        saveProperties(p);
    }

    public void loadModuleStates(ModuleManager mm) {
        Properties p = loadProperties();
        for (Module module : mm.getModules()) {
            String name = module.getName();
            module.setEnabled(parseBoolean(p.getProperty("module." + name + ".enabled"), false));
        }
    }

    // --- Module keybinds ---

    public void loadModuleKeybinds(ModuleManager mm) {
        Properties p = loadProperties();
        for (Module module : mm.getModules()) {
            String name = module.getName();
            module.setKeyCode(parseInt(p.getProperty("module." + name + ".key"), module.getKeyCode()));
        }
    }

    // --- Module settings ---

    public void saveModuleSettings(ModuleManager mm) {
        Properties p = loadProperties();
        for (Module module : mm.getModules()) {
            String name = module.getName();
            for (Setting setting : module.getSettings()) {
                String key = "module." + name + ".setting." + setting.getName();
                if (setting instanceof BooleanSetting) {
                    p.setProperty(key, String.valueOf(((BooleanSetting) setting).getValue()));
                } else if (setting instanceof NumberSetting) {
                    p.setProperty(key, String.valueOf(((NumberSetting) setting).getValue()));
                } else if (setting instanceof ModeSetting) {
                    p.setProperty(key, ((ModeSetting) setting).getValue());
                }
            }
        }
        saveProperties(p);
    }

    public void loadModuleSettings(ModuleManager mm) {
        Properties p = loadProperties();
        for (Module module : mm.getModules()) {
            String name = module.getName();
            for (Setting setting : module.getSettings()) {
                String key = "module." + name + ".setting." + setting.getName();
                if (setting instanceof BooleanSetting) {
                    ((BooleanSetting) setting).setValue(
                            parseBoolean(p.getProperty(key), ((BooleanSetting) setting).getValue())
                    );
                } else if (setting instanceof NumberSetting) {
                    ((NumberSetting) setting).setValue(
                            parseDouble(p.getProperty(key), ((NumberSetting) setting).getValue())
                    );
                } else if (setting instanceof ModeSetting) {
                    String val = p.getProperty(key);
                    ModeSetting ms = (ModeSetting) setting;
                    if (val != null && ms.getModes().contains(val)) {
                        ms.setValue(val);
                    }
                }
            }
        }
    }

    // --- Module HUD settings ---

    public void saveModuleHudSettings(ModuleManager mm) {
        Properties p = loadProperties();
        for (Module module : mm.getModules()) {
            String name = module.getName();
            ModuleHudSettings moduleHud = module.getHudSettings();
            String base = "module." + name + ".hud.";
            p.setProperty(base + "x", String.valueOf(moduleHud.getX()));
            p.setProperty(base + "y", String.valueOf(moduleHud.getY()));
            p.setProperty(base + "visible", String.valueOf(moduleHud.isVisible()));
            p.setProperty(base + "shadow", String.valueOf(moduleHud.isShadow()));
            p.setProperty(base + "background", String.valueOf(moduleHud.isBackground()));
            p.setProperty(base + "color", String.valueOf(moduleHud.getColorIndex()));
        }
        saveProperties(p);
    }

    public void loadModuleHudSettings(ModuleManager mm) {
        Properties p = loadProperties();
        for (Module module : mm.getModules()) {
            String name = module.getName();
            ModuleHudSettings moduleHud = module.getHudSettings();
            String base = "module." + name + ".hud.";
            moduleHud.setX(parseInt(p.getProperty(base + "x"), moduleHud.getX()));
            moduleHud.setY(parseInt(p.getProperty(base + "y"), moduleHud.getY()));
            moduleHud.setVisible(parseBoolean(p.getProperty(base + "visible"), moduleHud.isVisible()));
            moduleHud.setShadow(parseBoolean(p.getProperty(base + "shadow"), moduleHud.isShadow()));
            moduleHud.setBackground(parseBoolean(p.getProperty(base + "background"), moduleHud.isBackground()));
            moduleHud.setColorIndex(parseInt(p.getProperty(base + "color"), moduleHud.getColorIndex()));
        }
    }

    // --- saveAll / loadAll ---

    public void saveAll(ModuleManager mm, HudSettings hudSettings, GuiTheme theme, int guiX, int guiY) {
        Properties p = loadProperties();
        p.setProperty("gui.x", String.valueOf(guiX));
        p.setProperty("gui.y", String.valueOf(guiY));
        p.setProperty("gui.theme.accent", String.valueOf(theme.getAccentIndex()));
        p.setProperty("gui.theme.background", String.valueOf(theme.getBackgroundIndex()));
        saveProperties(p);

        saveHudSettings(hudSettings);
        saveModuleStates(mm);
        saveModuleSettings(mm);
        saveModuleHudSettings(mm);
    }

    public void loadAll(ModuleManager mm, HudSettings hudSettings, GuiTheme theme) {
        Properties p = loadProperties();
        theme.setAccentIndex(parseInt(p.getProperty("gui.theme.accent"), 0));
        theme.setBackgroundIndex(parseInt(p.getProperty("gui.theme.background"), 0));

        loadHudSettings(hudSettings);
        loadModuleStates(mm);
        loadModuleKeybinds(mm);
        loadModuleSettings(mm);
        loadModuleHudSettings(mm);
    }

    // --- Internal helpers ---

    private Properties loadProperties() {
        if (cachedProperties != null) return cachedProperties;
        Properties p = new Properties();
        File f = getFile();
        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                p.load(in);
            } catch (IOException e) {
                System.err.println("[ConfigManager] Failed to load config: " + e.getMessage());
            }
        }
        cachedProperties = p;
        return p;
    }

    private void saveProperties(Properties p) {
        try (OutputStream out = new FileOutputStream(getFile())) {
            p.store(out, "Backtrack config");
            cachedProperties = null;
        } catch (IOException e) {
            System.err.println("[ConfigManager] Failed to save config: " + e.getMessage());
        }
    }

    private int parseInt(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }

    private boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value.trim());
    }

    private double parseDouble(String value, double defaultValue) {
        if (value == null) return defaultValue;
        try { return Double.parseDouble(value.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}
