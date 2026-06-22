package com.yourname.backtrack.gui

import kotlin.math.*

// ── Top-level color utilities ──────────────────────────────────────

internal fun Int.withAlpha(alpha: Int): Int =
    (alpha.coerceIn(0, 255) shl 24) or (this and 0x00FFFFFF)

internal fun Int.shiftRgb(amount: Int): Int {
    val a = (this shr 24) and 0xFF
    val r = ((this shr 16) and 0xFF) + amount
    val g = ((this shr  8) and 0xFF) + amount
    val b = ( this         and 0xFF) + amount
    return (a shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
}

// ── Accent color palette ───────────────────────────────────────────

enum class AccentColor(val hex: Int, val displayName: String) {
    BLUE(   0x4AA3FF, "Blue"),
    CYAN(   0x3DE1D5, "Cyan"),
    PURPLE( 0x9B59FF, "Purple"),
    GREEN(  0x4CD964, "Green"),
    RED(    0xFF5A6A, "Red"),
    GOLD(   0xFFB347, "Gold");

    fun withAlpha(alpha: Int) = hex.withAlpha(alpha)
}

// ── Background theme palette ───────────────────────────────────────

enum class BackgroundTheme(
    val displayName: String,
    val title:      Int,
    val panel:      Int,
    val panelHover: Int,
    val windowGlass:Int,
    val backdrop:   Int,
    val sliderBg:   Int
) {
    OBSIDIAN("Obsidian",
        0xE6131519.toInt(), 0xD8141720.toInt(), 0xE01D212A.toInt(),
        0xCC0F1218.toInt(), 0xB0080A0E.toInt(), 0x80080A0E.toInt()),
    SLATE("Slate",
        0xE6181E28.toInt(), 0xD81A222D.toInt(), 0xE0232D38.toInt(),
        0xCC161D27.toInt(), 0xB00B0E14.toInt(), 0x800B0E14.toInt()),
    PLUM("Plum",
        0xE6221528.toInt(), 0xD81A101F.toInt(), 0xE023172B.toInt(),
        0xCC1E1222.toInt(), 0xB0100A12.toInt(), 0x80100A12.toInt()),
    FOREST("Forest",
        0xE6131E19.toInt(), 0xD80F1712.toInt(), 0xE016241C.toInt(),
        0xCC101B14.toInt(), 0xB00A110C.toInt(), 0x800A110C.toInt());
}

// ── GuiTheme ───────────────────────────────────────────────────────

class GuiTheme {

    var accent: AccentColor = AccentColor.BLUE
        private set

    var background: BackgroundTheme = BackgroundTheme.OBSIDIAN
        private set

    // ── Cycling ─────────────────────────────────────────────────
    fun cycleAccentColor() {
        val values = AccentColor.values()
        accent = values[(accent.ordinal + 1) % values.size]
    }

    fun cycleBackgroundStyle() {
        val values = BackgroundTheme.values()
        background = values[(background.ordinal + 1) % values.size]
    }

    fun reset() {
        accent = AccentColor.BLUE
        background = BackgroundTheme.OBSIDIAN
    }

    // ── Accent-index bridge (kept for backward compat) ──────────
    var accentIndex: Int
        get() = accent.ordinal
        set(v) { accent = AccentColor.values()[v.coerceIn(0, AccentColor.values().size - 1)] }

    var backgroundIndex: Int
        get() = background.ordinal
        set(v) { background = BackgroundTheme.values()[v.coerceIn(0, BackgroundTheme.values().size - 1)] }

    val accentName: String get() = accent.displayName
    val backgroundName: String get() = background.displayName

    // ── Accent-derived colors ───────────────────────────────────
    fun getAccentTextColor()  = accent.hex
    fun getAccentColor()      = accent.hex.withAlpha(160)
    fun getAccentHoverColor() = accent.hex.withAlpha(210)
    fun getToggleOnColor()    = accent.hex.withAlpha(230)
    fun getStrokeColor()      = accent.hex.withAlpha(92)

    fun getHoverGlowColor(strength: Float): Int {
        val alpha = 15 + (35 * strength.coerceIn(0f, 1f)).toInt()
        return accent.hex.withAlpha(alpha)
    }

    // ── Background-derived colors ───────────────────────────────
    fun getTitleColor()         = background.title
    fun getPanelColor()         = background.panel
    fun getPanelHoverColor()    = background.panelHover
    fun getWindowGlassColor()   = background.windowGlass
    fun getBackdropColor()      = background.backdrop
    fun getSliderBgColor()      = background.sliderBg

    fun getRowAltColor(rowIndex: Int): Int {
        val offset = if (rowIndex % 2 == 0) 0 else -10
        return background.panel.shiftRgb(offset)
    }

    // ── Static text colors ──────────────────────────────────────
    fun getTextPrimaryColor()   = 0xFFECEFF7.toInt()
    fun getTextSecondaryColor() = 0xFFB8C2D6.toInt()
    fun getTextMutedColor()     = 0xFF92A0BA.toInt()
    fun getSoftShadowColor()    = 0x60000000
    fun getToggleOffColor()     = 0x80485564.toInt()

    // ── Animation utilities ─────────────────────────────────────
    fun blend(a: Int, b: Int, t: Float): Int {
        val x = t.coerceIn(0f, 1f)
        val aa = (a shr 24) and 0xFF; val ar = (a shr 16) and 0xFF; val ag = (a shr 8) and 0xFF; val ab = a and 0xFF
        val ba = (b shr 24) and 0xFF; val br = (b shr 16) and 0xFF; val bg = (b shr 8) and 0xFF; val bb = b and 0xFF
        val ca = (aa + (ba - aa) * x).toInt()
        val cr = (ar + (br - ar) * x).toInt()
        val cg = (ag + (bg - ag) * x).toInt()
        val cb = (ab + (bb - ab) * x).toInt()
        return (ca shl 24) or (cr shl 16) or (cg shl 8) or cb
    }

    fun animateTowards(current: Float, target: Float, speed: Float): Float =
        current + (target - current) * speed.coerceIn(0f, 1f)

    fun pulseAlpha(color: Int, speed: Float, phaseOffset: Float, timeMs: Long): Int {
        val wave = (sin((timeMs * speed) + phaseOffset) + 1.0) * 0.5
        val alpha = (((color shr 24) and 0xFF) * (0.62f + 0.38f * wave)).toInt()
        return color.withAlpha(alpha)
    }

    /** Exposed for direct use — kept for backward compat. */
    fun withAlpha(color: Int, alpha: Int) = color.withAlpha(alpha)
}