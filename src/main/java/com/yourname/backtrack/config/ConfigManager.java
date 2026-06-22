package com.yourname.backtrack.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.yourname.backtrack.gui.GuiTheme
import com.yourname.backtrack.hud.HudSettings
import com.yourname.backtrack.module.Module
import com.yourname.backtrack.module.ModuleHudSettings
import com.yourname.backtrack.module.ModuleManager
import com.yourname.backtrack.setting.BooleanSetting
import com.yourname.backtrack.setting.ModeSetting
import com.yourname.backtrack.setting.NumberSetting
import com.yourname.backtrack.setting.Setting
import net.minecraft.launchwrapper.Launch
import java.io.File

class ConfigManager {

    private val file: File = File(Launch.minecraftHome, "backtrack/solobacktrack.json").also {
        it.parentFile?.mkdirs()
    }

    // ── GUI ─────────────────────────────────────────────────────
    fun loadGuiX() = loadJson()?.get("gui")?.asJsonObject?.get("x")?.asInt ?: 20
    fun loadGuiY() = loadJson()?.get("gui")?.asJsonObject?.get("y")?.asInt ?: 20
    fun saveGuiPosition(x: Int, y: Int) = updateJson { json ->
        json.addProperty("gui.x", x)
        json.addProperty("gui.y", y)
    }

    // ── Simulator settings ──────────────────────────────────────
    fun loadSimulatorShadow() = loadJson()?.get("simulator")?.asJsonObject?.get("shadow")?.asBoolean ?: false
    fun loadSimulatorDebug() = loadJson()?.get("simulator")?.asJsonObject?.get("debug")?.asBoolean ?: false
    fun loadSimulatorDebugChat() = loadJson()?.get("simulator")?.asJsonObject?.get("debugChat")?.asBoolean ?: false

    // ── HUD ─────────────────────────────────────────────────────
    fun saveHudSettings(hud: HudSettings) = updateJson { json ->
        val hudObj = json.getAsJsonObject("hud") ?: JsonObject()
        hudObj.addProperty("x", hud.x)
        hudObj.addProperty("y", hud.y)
        hudObj.addProperty("lineHeight", hud.lineHeight)
        hudObj.addProperty("visible", hud.visible)
        hudObj.addProperty("shadow", hud.shadow)
        hudObj.addProperty("background", hud.background)
        hudObj.addProperty("colorIndex", hud.colorIndex)
        hudObj.addProperty("anchorIndex", hud.anchor.ordinal)
        hudObj.addProperty("textMode", hud.textMode.ordinal)
        json.add("hud", hudObj)
    }

    fun loadHudSettings(hud: HudSettings) {
        val hudObj = loadJson()?.getAsJsonObject("hud") ?: return
        hud.x = hudObj.get("x")?.asInt ?: 5
        hud.y = hudObj.get("y")?.asInt ?: 5
        hud.lineHeight = hudObj.get("lineHeight")?.asInt ?: 12
        hud.visible = hudObj.get("visible")?.asBoolean ?: true
        hud.shadow = hudObj.get("shadow")?.asBoolean ?: true
        hud.background = hudObj.get("background")?.asBoolean ?: true
        hud.colorIndex = hudObj.get("colorIndex")?.asInt ?: 0
        hud.anchor = HudAnchor.values().getOrElse(hudObj.get("anchorIndex")?.asInt ?: 0) { HudAnchor.TOP_LEFT }
        hud.textMode = HudTextMode.values().getOrElse(hudObj.get("textMode")?.asInt ?: 1) { HudTextMode.NAME_STATUS }
    }

    // ── Module states ───────────────────────────────────────────
    fun saveModuleStates(mm: ModuleManager) = updateJson { json ->
        val modulesObj = json.getAsJsonObject("modules") ?: JsonObject()
        mm.modules.forEach { module ->
            val modObj = modulesObj.getAsJsonObject(module.name) ?: JsonObject()
            modObj.addProperty("enabled", module.enabled)
            modObj.addProperty("key", module.keyCode)
            modulesObj.add(module.name, modObj)
        }
        json.add("modules", modulesObj)
    }

    fun loadModuleStates(mm: ModuleManager) {
        val modulesObj = loadJson()?.getAsJsonObject("modules") ?: return
        mm.modules.forEach { module ->
            val modObj = modulesObj.getAsJsonObject(module.name) ?: return@forEach
            module.enabled = modObj.get("enabled")?.asBoolean ?: false
        }
    }

    fun loadModuleKeybinds(mm: ModuleManager) {
        val modulesObj = loadJson()?.getAsJsonObject("modules") ?: return
        mm.modules.forEach { module ->
            val modObj = modulesObj.getAsJsonObject(module.name) ?: return@forEach
            module.keyCode = modObj.get("key")?.asInt ?: module.keyCode
        }
    }

    // ── Module settings ─────────────────────────────────────────
    fun saveModuleSettings(mm: ModuleManager) = updateJson { json ->
        val settingsObj = json.getAsJsonObject("moduleSettings") ?: JsonObject()
        mm.modules.forEach { module ->
            val modObj = settingsObj.getAsJsonObject(module.name) ?: JsonObject()
            module.settings.forEach { setting ->
                when (setting) {
                    is BooleanSetting -> modObj.addProperty(setting.name, setting.value)
                    is NumberSetting  -> modObj.addProperty(setting.name, setting.value)
                    is ModeSetting    -> modObj.addProperty(setting.name, setting.value)
                }
            }
            settingsObj.add(module.name, modObj)
        }
        json.add("moduleSettings", settingsObj)
    }

    fun loadModuleSettings(mm: ModuleManager) {
        val settingsObj = loadJson()?.getAsJsonObject("moduleSettings") ?: return
        mm.modules.forEach { module ->
            val modObj = settingsObj.getAsJsonObject(module.name) ?: return@forEach
            module.settings.forEach { setting ->
                val key = setting.name
                when (setting) {
                    is BooleanSetting -> modObj.get(key)?.asBoolean?.let { setting.value = it }
                    is NumberSetting  -> modObj.get(key)?.asDouble?.let { setting.value = it }
                    is ModeSetting    -> {
                        val value = modObj.get(key)?.asString
                        if (value != null && value in setting.modes) setting.value = value
                    }
                }
            }
        }
    }

    // ── Module HUD settings ─────────────────────────────────────
    fun saveModuleHudSettings(mm: ModuleManager) = updateJson { json ->
        val hudSettingsObj = json.getAsJsonObject("moduleHud") ?: JsonObject()
        mm.modules.forEach { module ->
            val hud = module.hudSettings
            val hudObj = hudSettingsObj.getAsJsonObject(module.name) ?: JsonObject()
            hudObj.addProperty("x", hud.x)
            hudObj.addProperty("y", hud.y)
            hudObj.addProperty("visible", hud.visible)
            hudObj.addProperty("shadow", hud.shadow)
            hudObj.addProperty("background", hud.background)
            hudObj.addProperty("color", hud.colorIndex)
            hudSettingsObj.add(module.name, hudObj)
        }
        json.add("moduleHud", hudSettingsObj)
    }

    fun loadModuleHudSettings(mm: ModuleManager) {
        val hudSettingsObj = loadJson()?.getAsJsonObject("moduleHud") ?: return
        mm.modules.forEach { module ->
            val hud = module.hudSettings
            val hudObj = hudSettingsObj.getAsJsonObject(module.name) ?: return@forEach
            hud.x = hudObj.get("x")?.asInt ?: hud.x
            hud.y = hudObj.get("y")?.asInt ?: hud.y
            hud.visible = hudObj.get("visible")?.asBoolean ?: hud.visible
            hud.shadow = hudObj.get("shadow")?.asBoolean ?: hud.shadow
            hud.background = hudObj.get("background")?.asBoolean ?: hud.background
            hud.colorIndex = hudObj.get("color")?.asInt ?: hud.colorIndex
        }
    }

    // ── Bulk save/load ──────────────────────────────────────────
    fun saveAll(mm: ModuleManager, hud: HudSettings, theme: GuiTheme, guiX: Int, guiY: Int) {
        saveGuiPosition(guiX, guiY)
        saveHudSettings(hud)
        saveModuleStates(mm)
        saveModuleSettings(mm)
        saveModuleHudSettings(mm)
        updateJson { json ->
            val themeObj = json.getAsJsonObject("theme") ?: JsonObject()
            themeObj.addProperty("accent", theme.accentIndex)
            themeObj.addProperty("background", theme.backgroundIndex)
            json.add("theme", themeObj)
        }
    }

    fun loadAll(mm: ModuleManager, hud: HudSettings, theme: GuiTheme) {
        val json = loadJson()
        theme.accentIndex = json?.getAsJsonObject("theme")?.get("accent")?.asInt ?: 0
        theme.backgroundIndex = json?.getAsJsonObject("theme")?.get("background")?.asInt ?: 0
        loadHudSettings(hud)
        loadModuleStates(mm)
        loadModuleKeybinds(mm)
        loadModuleSettings(mm)
        loadModuleHudSettings(mm)
    }

    // ── JSON persistence ────────────────────────────────────────
    private var cachedJson: JsonObject? = null

    private fun loadJson(): JsonObject? {
        if (cachedJson != null) return cachedJson
        if (!file.exists()) return null
        return try {
            JsonParser.parseReader(file.reader()).asJsonObject.also { cachedJson = it }
        } catch (e: Exception) {
            System.err.println("[ConfigManager] Failed to load config: ${e.message}")
            null
        }
    }

    private fun updateJson(block: (JsonObject) -> Unit) {
        val json = loadJson() ?: JsonObject()
        block(json)
        try {
            file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(json))
            cachedJson = json
        } catch (e: Exception) {
            System.err.println("[ConfigManager] Failed to save config: ${e.message}")
        }
    }
}