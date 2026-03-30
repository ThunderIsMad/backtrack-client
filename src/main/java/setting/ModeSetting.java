package com.yourname.backtrack.setting;

import java.util.List;

public class ModeSetting extends Setting {

    private final List<String> modes;
    private String value;

    public ModeSetting(String name, List<String> modes, String defaultValue) {
        this(name, modes, defaultValue, SettingGroup.MAIN);
    }

    public ModeSetting(String name, List<String> modes, String defaultValue, SettingGroup group) {
        super(name, group);
        this.modes = modes;
        this.value = defaultValue;
    }

    public List<String> getModes() {
        return modes;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        if (modes.contains(value)) {
            this.value = value;
        }
    }

    public void cycle() {
        if (modes.isEmpty()) {
            return;
        }

        int index = modes.indexOf(value);
        if (index == -1 || index + 1 >= modes.size()) {
            this.value = modes.get(0);
        } else {
            this.value = modes.get(index + 1);
        }
    }
}
