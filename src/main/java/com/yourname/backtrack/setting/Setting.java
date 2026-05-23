package com.yourname.backtrack.setting;

import setting.SettingGroup;

public abstract class Setting {

    private final String name;
    private final SettingGroup group;

    public Setting(String name) {
        this(name, SettingGroup.MAIN);
    }

    public Setting(String name, SettingGroup group) {
        this.name = name;
        this.group = group == null ? SettingGroup.MAIN : group;
    }

    public String getName() {
        return name;
    }

    public SettingGroup getGroup() {
        return group;
    }
}