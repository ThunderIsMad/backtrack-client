package com.yourname.backtrack.hud

import com.yourname.backtrack.config.ConfigManager
import net.minecraft.client.Minecraft
import net.minecraft.client.settings.KeyBinding
import net.minecraft.util.text.TextComponentString
import net.minecraftforge.fml.client.registry.ClientRegistry
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import org.lwjgl.input.Keyboard

class HudControlHandler(
    private val settings: HudSettings,
    private val configManager: ConfigManager
) {
    // ── Bindings ──────────────────────────────────────────────────
    private data class HudBinding(val key: KeyBinding, val action: () -> String)

    private val bindings: List<HudBinding>

    init {
        fun bind(name: String, defaultKey: Int) =
            KeyBinding(name, defaultKey, "Solo Backtrack HUD")

        bindings = listOf(
            HudBinding(bind("HUD Anchor",    Keyboard.KEY_F5))   { settings.cycleAnchor();       "HUD anchor: ${settings.anchorName}" },
            HudBinding(bind("HUD Reset",     Keyboard.KEY_F9))   { settings.reset();             "HUD reset" },
            HudBinding(bind("HUD Left",      Keyboard.KEY_LEFT))  { settings.moveLeft();          "HUD X: ${settings.x}" },
            HudBinding(bind("HUD Right",     Keyboard.KEY_RIGHT)) { settings.moveRight();         "HUD X: ${settings.x}" },
            HudBinding(bind("HUD Up",        Keyboard.KEY_UP))    { settings.moveUp();            "HUD Y: ${settings.y}" },
            HudBinding(bind("HUD Down",      Keyboard.KEY_DOWN))  { settings.moveDown();          "HUD Y: ${settings.y}" },
            HudBinding(bind("HUD Color",     Keyboard.KEY_F6))   { settings.cycleColor();        "HUD color: ${settings.colorIndex}" },
            HudBinding(bind("HUD Toggle",    Keyboard.KEY_F7))   { settings.toggleVisible();     "HUD: ${if (settings.isVisible) "ON" else "OFF"}" },
            HudBinding(bind("HUD Shadow",    Keyboard.KEY_F8))   { settings.toggleShadow();      "HUD shadow: ${if (settings.isShadow) "ON" else "OFF"}" },
            HudBinding(bind("HUD Spacing +", Keyboard.KEY_PRIOR)) { settings.increaseLineHeight(); "HUD spacing: ${settings.lineHeight}" },
            HudBinding(bind("HUD Spacing -", Keyboard.KEY_NEXT))  { settings.decreaseLineHeight(); "HUD spacing: ${settings.lineHeight}" },
            HudBinding(bind("HUD Background",Keyboard.KEY_F10))  { settings.toggleBackground();  "HUD background: ${if (settings.isBackground) "ON" else "OFF"}" },
            HudBinding(bind("HUD Text Mode", Keyboard.KEY_F11))  { settings.cycleTextMode();     "HUD text mode: ${settings.textModeName}" }
        )

        bindings.forEach { ClientRegistry.registerKeyBinding(it.key) }
    }

    @SubscribeEvent
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        // Ignore HUD adjustments during combat — prevents accidental config changes
        val player = Minecraft.getMinecraft().player ?: return
        if (player.hurtTime > 0) return

        for (binding in bindings) {
            if (binding.key.isPressed) {
                val message = binding.action()
                configManager.saveHudSettings(settings)
                player.sendMessage(TextComponentString(message))
                break  // only one HUD action per tick
            }
        }
    }
}