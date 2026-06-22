package com.yourname.backtrack.module

import com.yourname.backtrack.gui.HudEditorScreen
import com.yourname.backtrack.setting.ActionSetting
import com.yourname.backtrack.setting.BooleanSetting
import com.yourname.backtrack.setting.ModeSetting
import com.yourname.backtrack.setting.Setting
import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraft.util.text.TextComponentString
import org.lwjgl.input.Keyboard
import setting.SettingGroup
import java.lang.reflect.Field
import java.lang.reflect.Modifier

abstract class Module(
    val name: String,
    val category: Category,
    defaultKey: Int
) {
    // ── Minecraft access ───────────────────────────────────────────
    protected val mc: Minecraft get() = Minecraft.getMinecraft()

    // ── Key binding (with obfuscation-safe field resolution) ───────
    val keyBinding: KeyBinding = KeyBinding(name, defaultKey, "Solo Backtrack")
    var keyCode: Int = defaultKey
        set(value) {
            field = value
            keyCodeField.setInt(keyBinding, value)
        }

    val keyName: String
        get() = if (keyCode == Keyboard.KEY_NONE) "NONE"
                else Keyboard.getKeyName(keyCode)

    // ── Settings ───────────────────────────────────────────────────
    private val _settings = mutableListOf<Setting>()
    val settings: List<Setting> get() = _settings

    // ── HUD ────────────────────────────────────────────────────────
    val hudSettings = ModuleHudSettings(name)

    // ── State ──────────────────────────────────────────────────────
    var enabled: Boolean = false
        private set

    // ── Lifecycle ──────────────────────────────────────────────────
    fun setEnabled(value: Boolean) {
        if (enabled == value) return
        enabled = value
        if (value) onEnable() else onDisable()
    }

    fun toggle() = setEnabled(!enabled)

    protected open fun onEnable() {
        mc.player?.sendMessage(TextComponentString("$name: ON"))
    }

    protected open fun onDisable() {
        mc.player?.sendMessage(TextComponentString("$name: OFF"))
    }

    /** Override in subclasses for per-tick logic. */
    open fun onClientTick() {}

    // ── Chat helper ────────────────────────────────────────────────
    protected fun sendClientMessage(message: String) {
        mc.player?.sendMessage(TextComponentString(message))
    }

    // ── Settings registration ──────────────────────────────────────
    protected fun addSetting(setting: Setting) {
        _settings += setting
    }

    protected fun addSettings(vararg settings: Setting) {
        _settings += settings
    }

    open fun getVisibleSettings(): List<Setting> = settings

    // ── HUD helpers ────────────────────────────────────────────────
    private fun createHudBoolSetting(name: String, initial: Boolean,
                                      sync: (Boolean) -> Unit): BooleanSetting {
        return object : BooleanSetting(name, initial, SettingGroup.HUDTEXT) {
            override fun toggle() { super.toggle(); sync(value) }
            override fun setValue(v: Boolean) { super.setValue(v); sync(v) }
        }
    }

    protected fun createHudVisibleSetting() =
        createHudBoolSetting("HUD Visible", hudSettings.isVisible, hudSettings::setVisible)

    protected fun createHudShadowSetting() =
        createHudBoolSetting("HUD Shadow", hudSettings.isShadow, hudSettings::setShadow)

    protected fun createHudBackgroundSetting() =
        createHudBoolSetting("HUD Background", hudSettings.isBackground, hudSettings::setBackground)

    protected fun createHudColorSetting(): ModeSetting {
        return object : ModeSetting("HUD Color", HUD_COLOR_NAMES,
            hudSettings.colorName, SettingGroup.HUDTEXT) {
            override fun cycle() { super.cycle(); syncHudColor(value) }
            override fun setValue(v: String) { super.setValue(v); syncHudColor(v) }
        }
    }

    protected fun createResetHudPositionSetting() =
        ActionSetting("Reset HUD Position", {}, SettingGroup.HUDTEXT)

    protected fun createOpenHudEditorSetting() =
        ActionSetting("Open HUD Editor", { context ->
            mc.displayGuiScreen(HudEditorScreen(
                context.clickGuiScreen,
                context.moduleManager,
                context.configManager,
                context.guiTheme
            ))
        }, SettingGroup.HUDTEXT)

    private fun syncHudColor(value: String) {
        val index = HUD_COLOR_NAMES.indexOf(value)
        if (index >= 0) hudSettings.colorIndex = index
    }

    protected fun addHudSettings() {
        addSettings(
            createHudVisibleSetting(),
            createHudShadowSetting(),
            createHudBackgroundSetting(),
            createHudColorSetting(),
            createOpenHudEditorSetting(),
            createResetHudPositionSetting()
        )
    }

    open fun getHudText(): String = "$name [ON]"

    // ── Reflection cache ───────────────────────────────────────────
    companion object {
        private val HUD_COLOR_NAMES = listOf(
            "White", "Red", "Green", "Blue", "Yellow", "Cyan", "Orange", "Pink", "Rainbow"
        )

        /** Cached reference to KeyBinding.keyCode — resolved once, works under any obfuscation. */
        private val keyCodeField: Field by lazy {
            val probe = KeyBinding("probe", 0, "probe")
            for (f in KeyBinding::class.java.declaredFields) {
                if (f.type != Int::class.javaPrimitiveType) continue
                if (Modifier.isStatic(f.modifiers)) continue
                f.isAccessible = true
                try {
                    if (f.getInt(probe) == 0) return@lazy f
                } catch (_: IllegalAccessException) {}
            }
            throw RuntimeException("[Backtrack] Could not find KeyBinding keyCode field")
        }
    }
}