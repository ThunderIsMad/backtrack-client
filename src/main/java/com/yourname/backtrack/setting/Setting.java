package com.yourname.backtrack.setting

import setting.SettingGroup

abstract class Setting(
    val name: String,
    val group: SettingGroup = SettingGroup.MAIN
)