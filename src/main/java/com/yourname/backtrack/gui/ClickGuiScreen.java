package com.yourname.backtrack.gui

import com.yourname.backtrack.config.ConfigManager
import com.yourname.backtrack.hud.HudSettings
import com.yourname.backtrack.module.Category
import com.yourname.backtrack.module.Module
import com.yourname.backtrack.module.ModuleManager
import com.yourname.backtrack.setting.*
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11
import setting.SettingGroup
import java.util.*
import kotlin.math.*

class ClickGuiScreen(
    val moduleManager: ModuleManager,
    val configManager: ConfigManager,
    val hudSettings: HudSettings,
    val guiTheme: GuiTheme
) : GuiScreen() {

    // ── Layout constants ───────────────────────────────────────────
    private companion object {
        const val PANEL_W   = 150
        const val PANEL_GAP = 8
        const val HEADER_H  = 16
        const val MOD_H     = 14
        const val SET_H     = 12
        const val ROW_GAP   = 4
        const val MAX_VIS_H = 240

        // Slider fixed layout — never shifts regardless of value width
        const val SLIDER_W        = 50
        const val SLIDER_VAL_W    = 28   // reserved right-side area for value text
        const val SLIDER_RIGHT_PAD = 4
        // sliderX = panelX + PANEL_W - SLIDER_VAL_W - SLIDER_RIGHT_PAD - SLIDER_W

        // Colour palette
        const val COLOR_BG        = 0xFF141414.toInt()
        const val COLOR_HOVER     = 0xFF1C1C1C.toInt()
        const val COLOR_OUTLINE   = 0xFF232323.toInt()
        const val COLOR_ACCENT    = 0xFF4DA3FF.toInt()
        const val COLOR_SLIDER_BG = 0xFF282828.toInt()
        const val COLOR_TEXT      = 0xFFD2D2D2.toInt()
        const val COLOR_TEXT_MOD  = 0xFFE1E1E1.toInt()
        const val COLOR_TEXT_SET  = 0xFFBEBEBE.toInt()
        const val COLOR_HEADER    = 0xFF181818.toInt()
    }

    // ── State ──────────────────────────────────────────────────────
    private val openTime: Long = System.currentTimeMillis()
    private val panels = mutableListOf<Panel>()
    private val sliderAnimations = HashMap<NumberSetting, Double>()

    private var expandedModule: Module? = null
    private var waitingForBind = false
    private var draggingSlider = false
    private var draggedSlider: NumberSetting? = null
    private var draggedSliderX = 0
    private var draggedSliderW = 0

    // ── Inner panel class ──────────────────────────────────────────
    private inner class Panel(val category: Category, var x: Int, var y: Int) {
        var collapsed = false
        var scrollOffset = 0f         // animated value
        var targetScrollOffset = 0f    // desired value set by mouse wheel
        var dragging = false
        var dragOffX = 0
        var dragOffY = 0
    }

    // ── Helpers ────────────────────────────────────────────────────
    private fun Int.withAlpha(alpha: Int) = ((alpha and 0xFF) shl 24) or (this and 0x00FFFFFF)

    private val Int.animated: Int
        get() {
            val t = ((System.currentTimeMillis() - openTime) / 220f).coerceIn(0f, 1f)
            val alpha = (this shr 24) and 0xFF
            return ((alpha * t).toInt() shl 24) or (this and 0x00FFFFFF)
        }

    /** Fixed slider X position for a panel — independent of current value. */
    private fun sliderX(panelX: Int) = panelX + PANEL_W - SLIDER_VAL_W - SLIDER_RIGHT_PAD - SLIDER_W

    // ── Initialisation ─────────────────────────────────────────────
    override fun initGui() {
        panels.clear()
        val count = Category.values().count { it != Category.HUD }
        val totalW = count * PANEL_W + (count - 1) * PANEL_GAP
        var startX = (width  - totalW) / 2
        val startY =  height / 4
        for (cat in Category.values()) {
            if (cat == Category.HUD) continue
            panels += Panel(cat, startX, startY)
            startX += PANEL_W + PANEL_GAP
        }
    }

    // ── Drawing ────────────────────────────────────────────────────
    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        for (panel in panels) {
            // Smooth scroll animation (lerp toward target)
            panel.scrollOffset += (panel.targetScrollOffset - panel.scrollOffset) * 0.3f
            drawPanel(panel, mouseX, mouseY)
        }
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun drawPanel(panel: Panel, mouseX: Int, mouseY: Int) {
        val x = panel.x
        val y = panel.y
        val contentH = if (panel.collapsed) 0 else getPanelContentHeight(panel)
        val panelH   = HEADER_H + contentH

        // Panel background & outline
        Gui.drawRect(x, y, x + PANEL_W, y + panelH, COLOR_BG.withAlpha(245))
        drawOutline(x, y, PANEL_W, panelH, COLOR_OUTLINE)
        Gui.drawRect(x, y, x + PANEL_W, y + HEADER_H, COLOR_HEADER.withAlpha(250))

        // Category header
        val catName = panel.category.name.lowercase(Locale.ROOT)
            .replaceFirstChar { it.uppercase() }
        fontRenderer.drawStringWithShadow(catName,
            x + PANEL_W / 2 - fontRenderer.getStringWidth(catName) / 2,
            y + 4, COLOR_TEXT)
        fontRenderer.drawStringWithShadow(if (panel.collapsed) "+" else "-",
            x + PANEL_W - 8, y + 4, COLOR_TEXT.withAlpha(120))

        val underlineWidth = (PANEL_W * 0.6).toInt()
        val underlineX = x + PANEL_W / 2 - underlineWidth / 2
        Gui.drawRect(underlineX, y + HEADER_H - 2, underlineX + underlineWidth, y + HEADER_H,
            if (panel.collapsed) COLOR_OUTLINE else COLOR_ACCENT)

        if (panel.collapsed) return

        // Content background
        Gui.drawRect(x, y + HEADER_H, x + PANEL_W, y + HEADER_H + contentH,
            COLOR_BG.withAlpha(250))
        enableScissor(x, y + HEADER_H, PANEL_W, contentH)

        val scrollOff = panel.scrollOffset.toInt()
        var rowY = y + HEADER_H - scrollOff
        for (mod in getModulesForCategory(panel.category)) {
            val inView = rowY + MOD_H >= y + HEADER_H && rowY < y + HEADER_H + contentH
            if (inView) drawModuleRow(x, rowY, mod, mouseX, mouseY)
            rowY += MOD_H + ROW_GAP
            if (mod == expandedModule) {
                rowY = drawInlineSettings(x, rowY, mod, mouseX, mouseY,
                    y + HEADER_H, y + HEADER_H + contentH)
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST)
    }

    private fun drawModuleRow(x: Int, y: Int, mod: Module, mx: Int, my: Int) {
        val hov      = isHov(x, y, PANEL_W, MOD_H, mx, my)
        val expanded = mod == expandedModule
        val bg = when {
            expanded -> COLOR_HOVER
            hov      -> COLOR_HOVER.withAlpha(200)
            else     -> COLOR_BG
        }
        Gui.drawRect(x, y, x + PANEL_W, y + MOD_H, bg.animated)
        if (mod.enabled) Gui.drawRect(x, y, x + 1, y + MOD_H, COLOR_ACCENT)

        val textColor = when {
            mod.enabled -> COLOR_ACCENT
            expanded    -> COLOR_TEXT_MOD
            else        -> COLOR_TEXT_MOD.withAlpha(180)
        }
        fontRenderer.drawStringWithShadow(mod.name, x + 6, y + 3, textColor.animated)

        if (mod.settings.isNotEmpty()) {
            fontRenderer.drawStringWithShadow(if (expanded) "\u2303" else "\u2304",
                x + PANEL_W - 8, y + 3, COLOR_TEXT.withAlpha(100))
        }
    }

    private fun drawInlineSettings(x: Int, startY: Int, mod: Module,
                                   mx: Int, my: Int, clipTop: Int, clipBot: Int): Int {
        var y = startY

        // Bind row
        if (y + SET_H in clipTop..clipBot) {
            val hov = isHov(x, y, PANEL_W, SET_H, mx, my)
            drawSettingRow(x, y, "Bind", if (waitingForBind) "PRESS KEY..." else mod.keyName,
                0, hov, COLOR_ACCENT)
        }
        y += SET_H + ROW_GAP

        for ((idx, s) in getMainSettings(mod).withIndex()) {
            if (y + SET_H in clipTop..clipBot) {
                val hov = isHov(x, y, PANEL_W, SET_H, mx, my)
                if (s is NumberSetting) drawNumberSettingSlider(x, y, s, hov)
                else drawSettingRow(x, y, s.name, getSettingValueText(s), idx + 1, hov, COLOR_ACCENT)
            }
            y += SET_H + ROW_GAP
        }
        return y
    }

    private fun drawSettingRow(x: Int, y: Int, left: String, right: String?,
                               rowIdx: Int, hov: Boolean, accent: Int) {
        val bg = if (hov) COLOR_HOVER.withAlpha(200) else COLOR_BG
        Gui.drawRect(x, y, x + PANEL_W, y + SET_H, bg.animated)
        Gui.drawRect(x, y, x + 2, y + SET_H, accent.withAlpha(if (hov) 180 else 60))

        val maxLeftW = PANEL_W - (right?.let { fontRenderer.getStringWidth(it) + 10 } ?: 0) - 8
        var label = left
        while (label.length > 1 && fontRenderer.getStringWidth(label) > maxLeftW)
            label = label.dropLast(1)
        fontRenderer.drawStringWithShadow(label, x + 6, y + 2, COLOR_TEXT_SET)

        if (!right.isNullOrEmpty()) {
            val rw = fontRenderer.getStringWidth(right)
            val rColor = when (right) {
                "ON", "PRESS KEY..." -> COLOR_ACCENT
                "OFF"                -> COLOR_TEXT.withAlpha(100)
                else                 -> COLOR_TEXT.withAlpha(140)
            }
            fontRenderer.drawStringWithShadow(right, x + PANEL_W - rw - 6, y + 2, rColor)
        }
    }

    private fun drawNumberSettingSlider(x: Int, y: Int, ns: NumberSetting, hov: Boolean) {
        val bg = if (hov) COLOR_HOVER.withAlpha(200) else COLOR_BG
        Gui.drawRect(x, y, x + PANEL_W, y + SET_H, bg.animated)
        Gui.drawRect(x, y, x + 2, y + SET_H, COLOR_ACCENT.withAlpha(if (hov) 180 else 60))

        val sx = sliderX(x)

        // Label (truncated if needed)
        val maxLeftW = sx - x - 8
        var label = ns.name
        while (label.length > 1 && fontRenderer.getStringWidth(label) > maxLeftW)
            label = label.dropLast(1)
        fontRenderer.drawStringWithShadow(label, x + 6, y + 2, COLOR_TEXT_SET)

        // Slider bar
        val sliderY = y + SET_H / 2 - 2
        val realPct = ((ns.value - ns.min) / (ns.max - ns.min)).coerceIn(0.0, 1.0)
        val anim = sliderAnimations.getOrPut(ns) { realPct }
        val smoothPct = anim + (realPct - anim) * 0.2
        sliderAnimations[ns] = smoothPct

        val fillW = (SLIDER_W * smoothPct).toInt()
        Gui.drawRect(sx, sliderY, sx + SLIDER_W, sliderY + 4, COLOR_SLIDER_BG)
        Gui.drawRect(sx, sliderY, sx + fillW,     sliderY + 4, COLOR_ACCENT)

        // Value text
        val valStr = "%.2f".format(Locale.US, ns.value)
        val rw = fontRenderer.getStringWidth(valStr)
        fontRenderer.drawStringWithShadow(valStr, x + PANEL_W - SLIDER_RIGHT_PAD - rw, y + 2, COLOR_TEXT)
    }

    // ── Input ──────────────────────────────────────────────────────
    override fun mouseClicked(mouseX: Int, mouseY: Int, button: Int) {
        for (panel in panels) {
            if (handlePanelClick(panel, mouseX, mouseY, button)) return
        }
        super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, button: Int, time: Long) {
        for (panel in panels) {
            if (!panel.dragging) continue
            panel.x = (mouseX - panel.dragOffX).coerceIn(0, width  - PANEL_W)
            panel.y = (mouseY - panel.dragOffY).coerceIn(0, height - HEADER_H)
        }
        if (draggingSlider && button == 0) {
            draggedSlider?.let { updateSliderFromMouse(it, mouseX, draggedSliderX, draggedSliderW) }
        }
        super.mouseClickMove(mouseX, mouseY, button, time)
    }

    private fun updateSliderFromMouse(ns: NumberSetting, mouseX: Int, sliderX: Int, sliderW: Int) {
        val pct = ((mouseX - sliderX).toDouble() / sliderW).coerceIn(0.0, 1.0)
        var newValue = ns.min + pct * (ns.max - ns.min)
        val step = ns.increment
        if (step > 0) newValue = round(newValue / step) * step
        ns.value = newValue
    }

    private fun handlePanelClick(panel: Panel, mx: Int, my: Int, btn: Int): Boolean {
        val x = panel.x
        val y = panel.y

        // Header click
        if (isHov(x, y, PANEL_W, HEADER_H, mx, my)) {
            if (btn == 0) {
                panel.dragging = true
                panel.dragOffX = mx - x
                panel.dragOffY = my - y
            } else if (btn == 1) {
                panel.collapsed = !panel.collapsed
                if (panel.collapsed && expandedModule != null
                    && expandedModule!!.category == panel.category) {
                    expandedModule = null
                    waitingForBind = false
                }
            }
            return true
        }

        if (panel.collapsed) return false

        val contentH = getPanelContentHeight(panel)
        if (!isHov(x, y + HEADER_H, PANEL_W, contentH, mx, my)) return false

        val scrollOff = panel.scrollOffset.toInt()
        var rowY = y + HEADER_H - scrollOff
        for (mod in getModulesForCategory(panel.category)) {
            // Module row
            if (isHov(x, rowY, PANEL_W, MOD_H, mx, my)) {
                if (btn == 0) mod.toggle()
                else if (btn == 1) {
                    expandedModule = if (expandedModule == mod) null else mod
                    waitingForBind = false
                }
                return true
            }
            rowY += MOD_H + ROW_GAP
            if (mod != expandedModule) continue

            // Bind row
            if (isHov(x, rowY, PANEL_W, SET_H, mx, my)) {
                if (btn == 0) waitingForBind = !waitingForBind
                return true
            }
            rowY += SET_H + ROW_GAP

            // Settings rows
            for (s in getMainSettings(mod)) {
                if (isHov(x, rowY, PANEL_W, SET_H, mx, my)) {
                    if (s is NumberSetting) {
                        val sx = sliderX(x)
                        if (btn == 0 && mx in sx until sx + SLIDER_W) {
                            draggingSlider = true
                            draggedSlider  = s
                            draggedSliderX = sx
                            draggedSliderW = SLIDER_W
                            updateSliderFromMouse(s, mx, sx, SLIDER_W)
                        }
                    } else {
                        handleSettingClick(mod, s, btn)
                    }
                    return true
                }
                rowY += SET_H + ROW_GAP
            }
        }
        return false
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        val wheel = Mouse.getEventDWheel()
        if (wheel == 0) return

        val mx = Mouse.getEventX()  * width  / mc.displayWidth
        val my = height - Mouse.getEventY() * height / mc.displayHeight - 1

        for (panel in panels) {
            val contentH = getPanelContentHeight(panel)
            if (isHov(panel.x, panel.y + HEADER_H, PANEL_W, contentH, mx, my)) {
                val maxScroll = max(0, getPanelTotalRowHeight(panel) - contentH)
                panel.targetScrollOffset = (panel.targetScrollOffset + if (wheel > 0) -24 else 24)
                    .coerceIn(0f, maxScroll.toFloat())
                break
            }
        }
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, button: Int) {
        if (button == 0) {
            for (panel in panels) panel.dragging = false
            draggingSlider = false
            draggedSlider  = null
            draggedSliderX = 0
            draggedSliderW = 0
        }
        super.mouseReleased(mouseX, mouseY, button)
    }

    override fun keyTyped(typed: Char, keyCode: Int) {
        if (waitingForBind && expandedModule != null) {
            expandedModule!!.keyCode = if (keyCode == Keyboard.KEY_ESCAPE) Keyboard.KEY_NONE else keyCode
            waitingForBind = false
            return
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            saveAndClose()
            return
        }
        super.keyTyped(typed, keyCode)
    }

    private fun saveAndClose() {
        if (panels.isNotEmpty()) configManager.saveGuiPosition(panels[0].x, panels[0].y)
        mc.displayGuiScreen(null)
    }

    // ── Layout calculations ────────────────────────────────────────
    private fun getPanelTotalRowHeight(panel: Panel): Int {
        var h = 0
        for (mod in getModulesForCategory(panel.category)) {
            h += MOD_H + ROW_GAP
            if (mod == expandedModule) {
                h += (1 + getMainSettings(mod).size) * (SET_H + ROW_GAP)
            }
        }
        return h
    }

    private fun getPanelContentHeight(panel: Panel) = min(getPanelTotalRowHeight(panel), MAX_VIS_H)

    // ── Module / setting queries ───────────────────────────────────
    private fun getModulesForCategory(cat: Category) =
        moduleManager.modules.filter { it.category == cat }

    private fun getMainSettings(mod: Module): List<Setting> =
        mod.visibleSettings.filter {
            it.group != SettingGroup.HUDTEXT && it.group != SettingGroup.DEBUG_WINDOW
        }

    // ── Setting interaction ────────────────────────────────────────
    private fun handleSettingClick(mod: Module, s: Setting, btn: Int) {
        when (s) {
            is BooleanSetting -> s.toggle()
            is ModeSetting    -> s.cycle()
            is NumberSetting  -> if (btn == 0) s.increase() else s.decrease()
            is ActionSetting  -> s.trigger(ActionContext(this, moduleManager, configManager, guiTheme))
        }
        configManager.saveModuleSettings(moduleManager)
        configManager.saveModuleHudSettings(moduleManager)
    }

    // ── Generic helpers ────────────────────────────────────────────
    private fun isHov(x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) =
        mx in x until x + w && my in y until y + h

    private fun drawOutline(x: Int, y: Int, w: Int, h: Int, color: Int) {
        Gui.drawRect(x,         y,         x + w,     y + 1,     color)
        Gui.drawRect(x,         y + h - 1, x + w,     y + h,     color)
        Gui.drawRect(x,         y,         x + 1,     y + h,     color)
        Gui.drawRect(x + w - 1, y,         x + w,     y + h,     color)
    }

    private fun enableScissor(x: Int, y: Int, w: Int, h: Int) {
        val scaleX = mc.displayWidth.toDouble()  / width
        val scaleY = mc.displayHeight.toDouble() / height
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(
            (x * scaleX).toInt(),
            (mc.displayHeight - (y + h) * scaleY).toInt(),
            (w * scaleX).toInt(),
            (h * scaleY).toInt()
        )
    }

    private fun getSettingValueText(s: Setting): String = when (s) {
        is BooleanSetting -> if (s.value) "ON" else "OFF"
        is NumberSetting  -> "%.2f".format(Locale.US, s.value)
        is ModeSetting    -> s.value
        is ActionSetting  -> ">"
        else              -> ""
    }

    override fun doesGuiPauseGame() = false
}