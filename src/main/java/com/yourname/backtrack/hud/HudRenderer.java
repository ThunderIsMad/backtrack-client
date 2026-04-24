package com.yourname.backtrack.hud;

import com.yourname.backtrack.module.Module;
import com.yourname.backtrack.module.ModuleHudSettings;
import com.yourname.backtrack.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class HudRenderer {

    private final ModuleManager moduleManager;
    private final HudSettings hudSettings;

    public HudRenderer(ModuleManager moduleManager, HudSettings hudSettings) {
        this.moduleManager = moduleManager;
        this.hudSettings = hudSettings;
    }

    private static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Text event) {
        if (mc().player == null || mc().world == null) return;
        if (!hudSettings.isVisible()) return;

        for (Module module : moduleManager.getModules()) {
            if (!module.isEnabled()) continue;

            ModuleHudSettings moduleHud = module.getHudSettings();

            if (!moduleHud.isVisible()) continue;

            String text = module.getHudText();
            int x = moduleHud.getX();
            int y = moduleHud.getY();

            if (moduleHud.isBackground()) {
                Gui.drawRect(
                        x - 2,
                        y - 1,
                        x + mc().fontRenderer.getStringWidth(text) + 3,
                        y + mc().fontRenderer.FONT_HEIGHT + 1,
                        0x70000000
                );
            }

            if (moduleHud.isShadow()) {
                mc().fontRenderer.drawStringWithShadow(text, x, y, moduleHud.getTextColor());
            } else {
                mc().fontRenderer.drawString(text, x, y, moduleHud.getTextColor());
            }
        }
    }
}
