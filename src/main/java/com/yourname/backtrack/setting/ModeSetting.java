package com.yourname.backtrack.setting

import setting.SettingGroup

open class ModeSetting(
    name: String,
    val modes: List<String>,
    defaultValue: String,
    group: SettingGroup = SettingGroup.MAIN
) : Setting(name, group) {

    var value: String = defaultValue
        protected set(v) {
            if (v in modes) field = v
        }

    fun cycle() {
        if (modes.isEmpty()) return
        val index = modes.indexOf(value)
        value = if (index == -1 || index + 1 >= modes.size) modes[0]
                else modes[index + 1]
    }

    // Java compatibility
    fun getValue(): String = value
    fun setValue(v: String) { value = v }
    fun getModes(): List<String> = modes
}