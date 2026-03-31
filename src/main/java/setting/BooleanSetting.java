package com.yourname.backtrack.setting;

import setting.SettingGroup;

public class BooleanSetting extends Setting {

    private boolean value;

    public BooleanSetting(String name, boolean defaultValue) {
        this(name, defaultValue, SettingGroup.MAIN);
    }

    public BooleanSetting(String name, boolean defaultValue, SettingGroup group) {
        super(name, group);
        this.value = defaultValue;
    }

    public boolean getValue() {
        return value;
    }

    public void setValue(boolean value) {
        this.value = value;
    }

    public void toggle() {
        this.value = !this.value;
    }
}
