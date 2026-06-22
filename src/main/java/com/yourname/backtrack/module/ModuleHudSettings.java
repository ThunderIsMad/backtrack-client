package com.yourname.backtrack.module

import net.minecraft.util.math.MathHelper
import kotlin.math.abs

class ModuleHudSettings(val moduleName: String) {

    // ── Built-in color palette ──────────────────────────────────
    enum class HudColor(val hex: Int, val displayName: String) {
        BLUE(   0x4AA3FF, "Blue"),
        CYAN(   0x3DE1D5, "Cyan"),
        PURPLE( 0x9B59FF, "Purple"),
        GREEN(  0x4CD964, "Green"),
        RED(    0xFF5A6A, "Red"),
        GOLD(   0xFFB347, "Gold"),
        WHITE(  0xFFFFFF, "White"),
        RAINBOW(-1,       "Rainbow")
    }

    var x = 5
    var y = 5

    private var defaultX = 5
    private var defaultY = 5

    var visible    = true
    var shadow     = true
    var background = false

    var colorIndex = 0
        set(value) { field = value.coerceIn(0, HudColor.values().size - 1) }

    val color: HudColor
        get() = HudColor.values().getOrElse(colorIndex) { HudColor.BLUE }

    val colorName: String
        get() = color.displayName

    /** Returns the actual ARGB colour, computing rainbow if needed. */
    fun getTextColor(): Int {
        val base = color
        if (base != HudColor.RAINBOW) return base.hex

        // Classic cycling rainbow — offset = time / 10
        val offset = (System.currentTimeMillis() / 10).toInt()
        val hue = (offset % 360).toFloat() / 360f
        return java.awt.Color.HSBtoRGB(hue, 1f, 1f) and 0xFFFFFF
    }

    // ── Convenience toggles ────────────────────────────────────
    fun toggleVisible()    { visible    = !visible }
    fun toggleShadow()     { shadow     = !shadow }
    fun toggleBackground() { background = !background }

    fun cycleColor() {
        val total = HudColor.values().size
        colorIndex = (colorIndex + 1) % total
    }

    fun setDefaultPosition(x: Int, y: Int) {
        defaultX = x; defaultY = y
        this.x = x; this.y = y
    }

    fun resetToDefault() {
        x = defaultX; y = defaultY
    }
}