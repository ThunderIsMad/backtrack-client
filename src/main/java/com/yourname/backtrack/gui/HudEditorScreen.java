package com.yourname.backtrack.gui

import com.yourname.backtrack.config.ConfigManager
import com.yourname.backtrack.module.Module
import com.yourname.backtrack.module.ModuleHudSettings
import com.yourname.backtrack.module.ModuleManager
import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.GuiScreen
import org.lwjgl.input.Keyboard

class HudEditorScreen(
    private val parent: GuiScreen,
    private val moduleManager: ModuleManager,
    private val configManager: ConfigManager,
    private val guiTheme: GuiTheme
) : GuiScreen() {

    private var draggingModule: Module? = null
    private var selectedModule: Module? = null
    private var dragOffsetX = 0
    private var dragOffsetY = 0

    // Animation state for smooth drag interpolation
    private data class SmoothDrag(var targetX: Int, var targetY: Int, var currentX: Int, var currentY: Int)
    private val dragAnim = SmoothDrag(0, 0, 0, 0)
    private var needsSmoothDrag = false

    init {
        if (moduleManager.modules.isNotEmpty()) {
            selectedModule = moduleManager.modules[0]
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()

        // Draw alignment grid for precise HUD positioning
        drawAlignmentGrid()

        // Header
        Gui.drawRect(20, 20, 250, 74, guiTheme.titleColor)
        fontRenderer.drawStringWithShadow("HUD Editor", 28, 28, 0xFFFFFF)
        fontRenderer.drawStringWithShadow("LMB - drag selected box", 28, 42, 0xFFFFFF)
        fontRenderer.drawStringWithShadow("ESC - back to ClickGUI", 28, 56, 0xAAAAAA)

        // Animate dragged module toward target
        if (needsSmoothDrag && draggingModule == null) {
            dragAnim.currentX += ((dragAnim.targetX - dragAnim.currentX) * 0.45f).toInt()
            dragAnim.currentY += ((dragAnim.targetY - dragAnim.currentY) * 0.45f).toInt()
            if (abs(dragAnim.targetX - dragAnim.currentX) < 1 &&
                abs(dragAnim.targetY - dragAnim.currentY) < 1) {
                needsSmoothDrag = false
            }
        }

        for (module in moduleManager.modules) {
            drawModulePreview(module, mouseX)
        }

        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    private fun drawAlignmentGrid() {
        val gridColor = 0x20FFFFFF
        for (x in 0..width step 25) Gui.drawRect(x, 0, x + 1, height, gridColor)
        for (y in 0..height step 25) Gui.drawRect(0, y, width, y + 1, gridColor)
    }

    private fun drawModulePreview(module: Module, mouseX: Int) {
        val hud = module.hudSettings

        // Use animated position if this module was just dropped
        val drawX = if (needsSmoothDrag && module == selectedModule && draggingModule == null) dragAnim.currentX else hud.x
        val drawY = if (needsSmoothDrag && module == selectedModule && draggingModule == null) dragAnim.currentY else hud.y

        val displayText = if (module.enabled) "${module.name} [ON]" else module.name
        val textWidth = fontRenderer.getStringWidth(displayText)
        val boxWidth = textWidth + 8
        val boxHeight = 14

        val boxColor = if (module == selectedModule) guiTheme.accentColor else guiTheme.panelColor

        // Background glow when selected
        if (module == selectedModule) {
            Gui.drawRect(drawX - 2, drawY - 2, drawX + boxWidth + 2, drawY + boxHeight + 2, guiTheme.getHoverGlowColor(0.5f))
        }

        // Hover outline
        if (isHovered(drawX, drawY, boxWidth, boxHeight, mouseX, mouseY)) {
            Gui.drawRect(drawX - 1, drawY - 1, drawX + boxWidth + 1, drawY + boxHeight + 1, guiTheme.accent.hex.withAlpha(80))
        }

        // Main box
        Gui.drawRect(drawX, drawY, drawX + boxWidth, drawY + boxHeight, boxColor)

        // Background
        if (hud.background) {
            Gui.drawRect(drawX - 2, drawY - 2, drawX + boxWidth + 2, drawY + boxHeight + 2, 0x50000000)
        }

        // Text with current color setting
        val textColor = hud.getTextColor()
        if (hud.shadow) {
            fontRenderer.drawStringWithShadow(displayText, drawX + 4, drawY + 3, textColor)
        } else {
            fontRenderer.drawString(displayText, drawX + 4, drawY + 3, textColor)
        }
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        for (module in moduleManager.modules) {
            val hud = module.hudSettings
            val displayText = if (module.enabled) "${module.name} [ON]" else module.name
            val boxWidth = fontRenderer.getStringWidth(displayText) + 8
            val boxHeight = 14

            if (mouseButton == 0 && isHovered(hud.x, hud.y, boxWidth, boxHeight, mouseX, mouseY)) {
                selectedModule = module
                draggingModule = module
                dragOffsetX = mouseX - hud.x
                dragOffsetY = mouseY - hud.y
                dragAnim.targetX = hud.x
                dragAnim.targetY = hud.y
                dragAnim.currentX = hud.x
                dragAnim.currentY = hud.y
                needsSmoothDrag = false
                break
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton)
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, clickedMouseButton: Int, timeSinceLastClick: Long) {
        val module = draggingModule
        if (module != null && clickedMouseButton == 0) {
            val hud = module.hudSettings

            // Snap to grid if holding SHIFT
            val snap = if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) 5 else 1
            val rawX = mouseX - dragOffsetX
            val rawY = mouseY - dragOffsetY
            val snappedX = (rawX / snap) * snap
            val snappedY = (rawY / snap) * snap

            hud.x = snappedX
            hud.y = snappedY
            clampHudToScreen(module)

            dragAnim.targetX = hud.x
            dragAnim.targetY = hud.y
            dragAnim.currentX = hud.x
            dragAnim.currentY = hud.y
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick)
    }

    override fun mouseReleased(mouseX: Int, mouseY: Int, state: Int) {
        if (draggingModule != null) {
            dragAnim.targetX = draggingModule!!.hudSettings.x
            dragAnim.targetY = draggingModule!!.hudSettings.y
            needsSmoothDrag = true
            draggingModule = null
            configManager.saveModuleHudSettings(moduleManager)
        }
        super.mouseReleased(mouseX, mouseY, state)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            configManager.saveModuleHudSettings(moduleManager)
            mc.displayGuiScreen(parent)
            return
        }
        super.keyTyped(typedChar, keyCode)
    }

    private fun clampHudToScreen(module: Module) {
        val hud = module.hudSettings
        val displayText = if (module.enabled) "${module.name} [ON]" else module.name
        val boxWidth = fontRenderer.getStringWidth(displayText) + 8
        val boxHeight = 14

        hud.x = hud.x.coerceIn(0, width  - boxWidth)
        hud.y = hud.y.coerceIn(0, height - boxHeight)
    }

    private fun isHovered(x: Int, y: Int, w: Int, h: Int, mx: Int, my: Int) =
        mx in x..x + w && my in y..y + h

    override fun doesGuiPauseGame() = false

    override fun onGuiClosed() {
        configManager.saveModuleHudSettings(moduleManager)
        super.onGuiClosed()
    }

    private fun abs(v: Int) = if (v < 0) -v else v
    private fun Int.coerceIn(min: Int, max: Int) = kotlin.math.coerceIn(this, min, max)
}