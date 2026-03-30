package com.yourname.backtrack.module;

import com.yourname.backtrack.setting.ActionSetting;
import com.yourname.backtrack.setting.BooleanSetting;
import com.yourname.backtrack.setting.ModeSetting;
import com.yourname.backtrack.setting.Setting;
import com.yourname.backtrack.setting.SettingGroup;
import com.yourname.backtrack.gui.HudEditorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class Module {

    protected final Minecraft mc = Minecraft.getMinecraft();

    private final String name;
    private final Category category;
    private final KeyBinding keyBinding;
    private final ModuleHudSettings hudSettings;
    private final List<Setting> settings = new ArrayList<>();
    private boolean enabled;

    public Module(String name, Category category, int defaultKey) {
        this.name = name;
        this.category = category;
        this.keyBinding = new KeyBinding(name, defaultKey, "Solo Backtrack");
        this.hudSettings = new ModuleHudSettings(name);
        this.enabled = false;
    }

    public String getName() {
        return name;
    }

    public String getHudText() {
        return getName() + " [ON]";
    }

    public Category getCategory() {
        return category;
    }

    public KeyBinding getKeyBinding() {
        return keyBinding;
    }

    public int getKeyCode() {
        return keyBinding.getKeyCode();
    }

    public void setKeyCode(int keyCode) {
        if (mc.gameSettings != null) {
            mc.gameSettings.setOptionKeyBinding(keyBinding, keyCode);
            KeyBinding.resetKeyBindingArrayAndHash();
        } else {
            keyBinding.setKeyCode(keyCode);
            KeyBinding.resetKeyBindingArrayAndHash();
        }
    }

    public String getKeyName() {
        if (keyBinding.getKeyCode() == Keyboard.KEY_NONE) {
            return "NONE";
        }

        return Keyboard.getKeyName(keyBinding.getKeyCode());
    }

    public ModuleHudSettings getHudSettings() {
        return hudSettings;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }

        this.enabled = enabled;

        if (enabled) {
            onEnable();
        } else {
            onDisable();
        }
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    protected void onEnable() {
        sendClientMessage(name + ": ON");
    }

    protected void onDisable() {
        sendClientMessage(name + ": OFF");
    }

    protected void sendClientMessage(String message) {
        if (mc.player != null) {
            mc.player.sendMessage(new TextComponentString(message));
        }
    }

    protected void addSetting(Setting setting) {
        settings.add(setting);
    }

    protected void addSettings(Setting... settings) {
        this.settings.addAll(Arrays.asList(settings));
    }

    public List<Setting> getSettings() {
        return Collections.unmodifiableList(settings);
    }

    protected BooleanSetting createHudVisibleSetting() {
        return new BooleanSetting("HUD Visible", hudSettings.isVisible(), SettingGroup.HUD_TEXT) {
            @Override
            public void toggle() {
                super.toggle();
                hudSettings.setVisible(getValue());
            }

            @Override
            public void setValue(boolean value) {
                super.setValue(value);
                hudSettings.setVisible(value);
            }
        };
    }

    protected BooleanSetting createHudShadowSetting() {
        return new BooleanSetting("HUD Shadow", hudSettings.isShadow(), SettingGroup.HUD_TEXT) {
            @Override
            public void toggle() {
                super.toggle();
                hudSettings.setShadow(getValue());
            }

            @Override
            public void setValue(boolean value) {
                super.setValue(value);
                hudSettings.setShadow(value);
            }
        };
    }

    protected BooleanSetting createHudBackgroundSetting() {
        return new BooleanSetting("HUD Background", hudSettings.isBackground(), SettingGroup.HUD_TEXT) {
            @Override
            public void toggle() {
                super.toggle();
                hudSettings.setBackground(getValue());
            }

            @Override
            public void setValue(boolean value) {
                super.setValue(value);
                hudSettings.setBackground(value);
            }
        };
    }

    protected ModeSetting createHudColorSetting() {
        return new ModeSetting(
                "HUD Color",
                HUD_COLOR_NAMES,
                hudSettings.getColorName(),
                SettingGroup.HUD_TEXT
        ) {
            @Override
            public void cycle() {
                super.cycle();
                syncHudColorFromMode(getValue());
            }

            @Override
            public void setValue(String value) {
                super.setValue(value);
                syncHudColorFromMode(getValue());
            }
        };
    }

    protected ActionSetting createResetHudPositionSetting() {
        return new ActionSetting("Reset HUD Position", () -> {
            if (hudSettings != null) {
                hudSettings.resetToDefault();
                sendClientMessage(getName() + " HUD position reset");
            }
        }, SettingGroup.HUD_TEXT);
    }

    private static final List<String> HUD_COLOR_NAMES =
            Arrays.asList("BLUE", "CYAN", "PURPLE", "GREEN", "RED", "GOLD");

    private void syncHudColorFromMode(String value) {
        int index = HUD_COLOR_NAMES.indexOf(value);
        if (index >= 0) {
            hudSettings.setColorIndex(index);
        }
    }

    protected void addHudSettings() {
        addSettings(
                createHudVisibleSetting(),
                createHudShadowSetting(),
                createHudBackgroundSetting(),
                createHudColorSetting(),
                createOpenHudEditorSetting(),
                createResetHudPositionSetting()
        );
    }

    protected ActionSetting createOpenHudEditorSetting() {
        return new ActionSetting("Open HUD Editor", context ->
                mc.displayGuiScreen(new HudEditorScreen(
                        context.getClickGuiScreen(),
                        context.getModuleManager(),
                        context.getConfigManager(),
                        context.getGuiTheme()
                )), SettingGroup.HUD_TEXT);
    }

    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
    }
}
