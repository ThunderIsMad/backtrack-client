package com.yourname.backtrack.setting

import setting.SettingGroup

open class BooleanSetting(
    name: String,
    defaultValue: Boolean = false,
    group: SettingGroup = SettingGroup.MAIN
) : Setting(name, group) {

    var value: Boolean = defaultValue
        protected set

    open fun toggle() {
        value = !value
    }

    // Backwards-compat for Java callers
    fun getValue(): Boolean = value
    fun setValue(v: Boolean) { value = v }
}