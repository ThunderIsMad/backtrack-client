package com.yourname.backtrack.hud

import com.yourname.backtrack.client.ClientSimulator
import com.yourname.backtrack.client.SimDebug
import com.yourname.backtrack.module.ModuleManager
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class HudRenderer(
    private val moduleManager: ModuleManager,
    private val hudSettings: HudSettings
) {
    @SubscribeEvent
    fun onRenderGameOverlay(event: RenderGameOverlayEvent.Text) {
        val mc = Minecraft.getMinecraft()
        if (mc.player == null || mc.world == null) return
        if (!hudSettings.isVisible) return
        val fr = mc.fontRenderer

        for (module in moduleManager.modules) {
            if (!module.enabled) continue

            val modHud = module.hudSettings
            if (!modHud.visible) continue

            val text = module.hudText
            val x = modHud.x
            val y = modHud.y
            val color = modHud.getTextColor()

            if (modHud.background) {
                Gui.drawRect(
                    x - 2,
                    y - 1,
                    x + fr.getStringWidth(text) + 3,
                    y + fr.FONT_HEIGHT + 1,
                    0x70000000.toInt()
                )
            }

            if (modHud.shadow) {
                fr.drawStringWithShadow(text, x, y, color)
            } else {
                fr.drawString(text, x, y, color)
            }
        }

        // Intave debug overlay — shows simulator state when SimDebug is enabled
        if (SimDebug.enabled && SimDebug.logToChat) {
            val sim = ClientSimulator
            val s = sim.state()
            val debugLine = "§7[Sim] pev=${s.pastExternalVelocity} " +
                "tolXZ=%.4f tolY=%.4f inWin=${s.isInVelocityWindow()}".format(s.toleranceXZ, s.toleranceY)
            fr.drawStringWithShadow(debugLine, 2, 2, 0xFFFFFF)
        }
    }
}