package com.yourname.backtrack.hud

enum class HudAnchor { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

enum class HudTextMode {
    NAME_ONLY,
    NAME_STATUS,
    COMPACT
}

class HudSettings {
    private val colors = intArrayOf(
        0xFFFFFF, 0x55FF55, 0x55FFFF, 0xFFAA00, 0xFF5555, 0xAA55FF
    )

    var x = 5
        set(value) { field = maxOf(0, value) }
    var y = 5
        set(value) { field = maxOf(0, value) }
    var lineHeight = 12
        set(value) { field = value.coerceIn(8, 30) }
    var visible = true
    var shadow = true
    var background = true
    var colorIndex = 0
        set(value) { field = value.coerceIn(0, colors.size - 1) }
    var anchor = HudAnchor.TOP_LEFT
    var textMode = HudTextMode.NAME_STATUS

    val currentColor: Int get() = colors[colorIndex]
    val anchorName: String get() = anchor.name
    val textModeName: String get() = textMode.name

    fun moveLeft()  { x = maxOf(0, x - 2) }
    fun moveRight() { x += 2 }
    fun moveUp()    { y = maxOf(0, y - 2) }
    fun moveDown()  { y += 2 }

    fun cycleColor() {
        colorIndex = (colorIndex + 1) % colors.size
    }

    fun cycleAnchor() {
        val values = HudAnchor.values()
        anchor = values[(anchor.ordinal + 1) % values.size]
    }

    fun cycleTextMode() {
        val values = HudTextMode.values()
        textMode = values[(textMode.ordinal + 1) % values.size]
    }

    fun toggleVisible()    { visible    = !visible }
    fun toggleShadow()     { shadow     = !shadow }
    fun toggleBackground() { background = !background }
    fun increaseLineHeight() { lineHeight = minOf(30, lineHeight + 1) }
    fun decreaseLineHeight() { lineHeight = maxOf(8, lineHeight - 1) }

    fun reset() {
        x = 5; y = 5
        lineHeight = 12
        visible = true; shadow = true; background = true
        colorIndex = 0
        anchor = HudAnchor.TOP_LEFT
        textMode = HudTextMode.NAME_STATUS
    }
}