package com.yourname.backtrack.setting

import setting.SettingGroup

open class NumberSetting(
    name: String,
    defaultValue: Double,
    val min: Double,
    val max: Double,
    val increment: Double,
    group: SettingGroup = SettingGroup.MAIN
) : Setting(name, group) {

    var value: Double = defaultValue
        protected set(v) {
            field = v.coerceIn(min, max)
        }

    fun increase() { value += increment }
    fun decrease() { value -= increment }

    // Java compatibility
    fun getValue(): Double = value
    fun setValue(v: Double) { value = v }
    fun getMin(): Double = min
    fun getMax(): Double = max
    fun getIncrement(): Double = increment
}