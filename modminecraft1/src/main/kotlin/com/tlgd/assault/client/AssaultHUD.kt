package com.tlgd.assault.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.tlgd.assault.TLGAssaultMod
import com.tlgd.assault.config.TLGDAssaultConfig
import com.tlgd.assault.network.NetworkHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderGuiOverlayEvent
import net.neoforged.neoforge.client.event.RegisterGuiOverlaysEvent
import net.neoforged.neoforge.client.gui.overlay.VanillaGuiOverlay

@EventBusSubscriber(modid = TLGAssaultMod.MOD_ID, value = [Dist.CLIENT])
object AssaultHUD {

    private var currentHUDData: NetworkHandler.AssaultHUDPacket? = null
    private var showHUD = true

    @SubscribeEvent
    @JvmStatic
    fun registerOverlays(event: RegisterGuiOverlaysEvent) {
        event.registerAbove(VanillaGuiOverlay.PLAYER_HEALTH.id(), "tlgdassault_assault_hud") { gui, poseStack, partialTick, screenWidth, screenHeight ->
            renderHUD(gui, poseStack, screenWidth, screenHeight)
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onRenderGuiOverlay(event: RenderGuiOverlayEvent.Post) {
        if (event.overlay().id() == VanillaGuiOverlay.PLAYER_HEALTH.id()) {
            // Nosso overlay é renderizado via registerAbove acima
        }
    }

    fun updateHUDData(packet: NetworkHandler.AssaultHUDPacket) {
        currentHUDData = packet
    }

    fun clearHUD() {
        currentHUDData = null
    }

    fun setShowHUD(show: Boolean) {
        showHUD = show
    }

    private fun renderHUD(gui: net.minecraft.client.gui.GuiGraphics, poseStack: PoseStack, screenWidth: Int, screenHeight: Int) {
        if (!showHUD || !TLGDAssaultConfig.showAssaultHud) return
        val data = currentHUDData ?: return
        if (data.state != 2) return // Só mostra durante assalto ativo (state=2)

        val minecraft = Minecraft.getInstance()
        val font = minecraft.font
        val player = minecraft.player ?: return

        // Posição: topo central
        val hudWidth = 280
        val hudHeight = 80 + data.leaderboard.size * 18
        val x = (screenWidth - hudWidth) / 2
        val y = 10

        // Background semi-transparente
        gui.fill(poseStack, x - 5, y - 5, x + hudWidth + 5, y + hudHeight + 5, 0xCC000000)
        gui.fill(poseStack, x - 5, y - 5, x + hudWidth + 5, y + 25, 0xCC8B0000) // Barra vermelha no topo

        // Título
        val title = "⚔ ASSALTO: ${data.regionName}"
        val titleWidth = font.width(title)
        gui.drawString(poseStack, font, title, x + (hudWidth - titleWidth) / 2, y + 2, 0xFFFFFFFF)

        // Timer
        val minutes = data.remainingSeconds / 60
        val seconds = data.remainingSeconds % 60
        val timeStr = String.format("⏱ %02d:%02d", minutes, seconds)
        val timeColor = if (data.remainingSeconds < 300) 0xFFFF5555 else 0xFFFFFFFF
        val timeWidth = font.width(timeStr)
        gui.drawString(poseStack, font, timeStr, x + (hudWidth - timeWidth) / 2, y + 28, timeColor)

        // Leaderboard
        var lineY = y + 48
        gui.drawString(poseStack, font, "§l§ePlacar:", x + 10, lineY, 0xFFFFFF55)
        lineY += 16

        for ((i, entry) in data.leaderboard.withIndex()) {
            val medal = when (i) {
                0 -> "§6🥇 "
                1 -> "§f🥈 "
                2 -> "§6🥉 "
                else -> "§7  "
            }
            val line = "$medal§f${entry.nationName} §7- §e${entry.score} §7pts"
            gui.drawString(poseStack, font, line, x + 10, lineY, 0xFFFFFFFF)

            // Sub-stats
            val subLine = "§8   K:${entry.kills}  V:${entry.vehiclesDestroyed}  F:${entry.flagsCaptured}"
            gui.drawString(poseStack, font, subLine, x + 10, lineY + 10, 0xFFAAAAAA)
            lineY += 22
        }
    }
}