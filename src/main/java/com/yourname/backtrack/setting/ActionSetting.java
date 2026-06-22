package com.yourname.backtrack.setting

import com.yourname.backtrack.config.ConfigManager
import com.yourname.backtrack.gui.ClickGuiScreen
import com.yourname.backtrack.gui.GuiTheme
import com.yourname.backtrack.module.ModuleManager
import setting.SettingGroup

data class ActionContext(
    val clickGuiScreen: ClickGuiScreen,
    val moduleManager: ModuleManager,
    val configManager: ConfigManager,
    val guiTheme: GuiTheme
)

open class ActionSetting(
    name: String,
    private val action: Runnable?,
    private val contextAction: ContextAction?,
    group: SettingGroup = SettingGroup.MAIN
) : Setting(name, group) {

    fun interface ContextAction {
        fun run(context: ActionContext)
    }

    // Runnable-only constructor
    constructor(name: String, action: Runnable, group: SettingGroup = SettingGroup.MAIN) :
        this(name, action, null, group)

    // ContextAction-only constructor
    constructor(name: String, contextAction: ContextAction, group: SettingGroup = SettingGroup.MAIN) :
        this(name, null, contextAction, group)

    fun trigger() {
        action?.run()
    }

    fun trigger(context: ActionContext) {
        contextAction?.run(context) ?: trigger()
    }
}