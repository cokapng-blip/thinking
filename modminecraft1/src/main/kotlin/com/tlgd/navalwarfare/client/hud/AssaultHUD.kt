package com.tlgd.navalwarfare.client.hud

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.tlgd.navalwarfare.TLGAssaultMod
import com.tlgd.navalwarfare.config.NavalWarfareConfig
import com.tlgd.navalwarfare.network.NetworkHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber

@EventBusSubscriber(modid = TLGAssaultMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = [Dist.CLIENT])
object AssaultHUD {

    private var enabled = true
    private var showDebug = false
    private var currentPacket: NetworkHandler.AssaultHUDPacket? = null
    private var regionMarkers: List<NetworkHandler.RegionMarkerData> = emptyList()
    private var lastUpdateTime = 0L

    // Posição do HUD
    private var hudX = 0
    private var hudY = 0
    private var hudWidth = 320
    private var hudHeight = 200

    fun updateFromPacket(packet: NetworkHandler.AssaultHUDPacket) {
        currentPacket = packet
        lastUpdateTime = System.currentTimeMillis()

        // Auto-posicionar baseado na config
        val minecraft = Minecraft.getInstance()
        when (NavalWarfareConfig.hudPosition.get()) {
            "top_left" -> { hudX = 10; hudY = 10 }
            "top_right" -> { hudX = minecraft.window.guiScaledWidth - hudWidth - 10; hudY = 10 }
            else -> { hudX = (minecraft.window.guiScaledWidth - hudWidth) / 2; hudY = 10 } // top_center
        }
    }

    fun updateRegionMarkers(markers: List<NetworkHandler.RegionMarkerData>) {
        regionMarkers = markers
    }

    fun toggle() {
        enabled = !enabled
        val player = Minecraft.getInstance().player
        player?.displayClientMessage(
            Component.literal("§e[NavalWarfare] HUD: §${if (enabled) "aAtivado" else "cDesativado"}"),
            true
        )
    }

    fun setDebug(debug: Boolean) {
        showDebug = debug
    }

    fun render(guiGraphics: GuiGraphics, partialTick: Float) {
        if (!enabled || currentPacket == null) return

        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        val font = minecraft.font
        val scale = NavalWarfareConfig.hudScale.get().floatValue()

        val poseStack = PoseStack()
        poseStack.pushPose()
        poseStack.translate(hudX.toFloat(), hudY.toFloat(), 0f)
        poseStack.scale(scale, scale, 1f)

        val packet = currentPacket!!

        // Só renderiza se assalto ativo
        if (packet.state == 0) {
            // Sem assalto ativo - mostrar info mínima se em região
            if (regionMarkers.isNotEmpty()) {
                renderIdleHUD(guiGraphics, font)
            }
            poseStack.popPose()
            return
        }

        // Background do painel
        val bgColor = 0xCC000000
        val headerColor = 0xCC1B4D1B
        val borderColor = 0xFF2D2D2D

        guiGraphics.fill(poseStack, 0, 0, hudWidth, hudHeight, bgColor)

        // Header
        val stateText = when (packet.state) {
            1 -> "§e§lPREPARANDO"
            2 -> "§c§lEM COMBATE"
            3 -> "§7COOLDOWN"
            else -> "§7INATIVO"
        }
        val headerText = "§e§l⚓ ASSALTO NAVAL §7| §f${packet.regionName} §7| $stateText"
        guiGraphics.fill(poseStack, 0, 0, hudWidth, 24, headerColor)
        guiGraphics.drawString(font, headerText, 10, 6, 0xFFFFFFFF)

        // Timer
        val timeColor = if (packet.remainingSeconds < 300) 0xFFFF5555 else if (packet.remainingSeconds < 600) 0xFFFFFF55 else 0xFF88FF88
        val timeText = formatTime(packet.remainingSeconds)
        val timeWidth = font.width(timeText)
        guiGraphics.drawString(font, "§l$timeText", hudWidth - timeWidth - 10, 6, timeColor)

        // Border
        guiGraphics.fill(poseStack, 0, 0, hudWidth, 1, borderColor)
        guiGraphics.fill(poseStack, 0, hudHeight - 1, hudWidth, hudHeight, borderColor)
        guiGraphics.fill(poseStack, 0, 0, 1, hudHeight, borderColor)
        guiGraphics.fill(poseStack, hudWidth - 1, 0, hudWidth, hudHeight, borderColor)

        var y = 30

        // Leaderboard
        if (packet.leaderboard.isNotEmpty()) {
            y = renderLeaderboard(guiGraphics, font, packet.leaderboard, y)
        }

        // Objetivos ativos
        if (packet.activeObjectives.isNotEmpty()) {
            y += 5
            y = renderObjectives(guiGraphics, font, packet.activeObjectives, y)
        }

        // Nation rank do jogador
        if (packet.playerNationRank != null) {
            y += 5
            guiGraphics.drawString(font, "§7Seu Rank: §f${packet.playerNationRank}", 10, y, 0xFFAAAAAA)
        }

        poseStack.popPose()
    }

    private fun renderIdleHUD(guiGraphics: GuiGraphics, font: net.minecraft.client.gui.Font) {
        // HUD mínimo quando não há assalto ativo
        val nearRegions = regionMarkers.filter { it.state == 1 || it.state == 2 } // peaceful or preparing
        if (nearRegions.isEmpty()) return

        val region = nearRegions.first()
        val text = "§7Região próxima: §f${region.name} §7(${region.nextWindow ?: "Sem janela"})"
        val w = font.width(text) + 20
        val h = 24
        val x = (hudWidth - w) / 2

        guiGraphics.fill(poseStack, x - 5, 0, x + w + 5, h, 0xCC000000)
        guiGraphics.fill(poseStack, x - 5, 0, x + w + 5, 1, 0xFF2D2D2D)
        guiGraphics.fill(poseStack, x - 5, h - 1, x + w + 5, h, 0xFF2D2D2D)
        guiGraphics.drawString(font, text, x, 6, 0xFFFFFFFF)
    }

    private fun renderLeaderboard(guiGraphics: GuiGraphics, font: net.minecraft.client.gui.Font,
                                  leaderboard: List<NetworkHandler.LeaderboardEntry>, startY: Int): Int {
        var y = startY

        // Header
        guiGraphics.fill(poseStack, 5, y, hudWidth - 5, y + 18, 0x881B4D1B)
        guiGraphics.drawString(font, "§l§eNAÇÃO", 10, y + 4, 0xFFFFFF)
        guiGraphics.drawString(font, "§l§ePONTOS", 180, y + 4, 0xFFFFFF)
        guiGraphics.drawString(font, "§l§eK/D", 240, y + 4, 0xFFFFFF)
        guiGraphics.drawString(font, "§l§eVEÍC", 270, y + 4, 0xFFFFFF)
        guiGraphics.drawString(font, "§l§ePRES", 300, y + 4, 0xFFFFFF)
        y += 20

        val maxEntries = min(5, leaderboard.size)

        for (i in 0 until maxEntries) {
            val entry = leaderboard[i]
            val isPlayer = entry.isPlayerNation

            // Highlight player's nation
            if (isPlayer) {
                guiGraphics.fill(poseStack, 5, y - 2, hudWidth - 5, y + 16, 0x44FFFFFF)
            } else if (i % 2 == 0) {
                guiGraphics.fill(poseStack, 5, y - 2, hudWidth - 5, y + 16, 0x22FFFFFF)
            }

            val placeColor = when (i) {
                0 -> 0xFFFFD700 // Gold
                1 -> 0xFFC0C0C0 // Silver
                2 -> 0xFFCD7F32 // Bronze
                else -> 0xFFFFFFFF
            }

            val placeText = "${i + 1}."
            guiGraphics.drawString(font, "§l$placeText", 10, y, placeColor)

            val nationText = if (isPlayer) "§e§l${entry.nationName} §r§7(Você)" else entry.nationName
            guiGraphics.drawString(font, nationText, 30, y, if (isPlayer) 0xFFFFFF00 else 0xFFFFFFFF)

            val scoreText = "${entry.score}"
            val scoreWidth = font.width(scoreText)
            guiGraphics.drawString(font, scoreText, 180 + (50 - scoreWidth), y, 0xFF88FF88)

            val kdText = "${entry.kills}/${entry.vehiclesDestroyed}"
            guiGraphics.drawString(font, kdText, 240, y, 0xFFAAAAAA)

            val vehiclesText = "${entry.flagsCaptured}"
            guiGraphics.drawString(font, vehiclesText, 270, y, 0xFF8888FF)

            val presText = "${entry.presencePoints}"
            guiGraphics.drawString(font, presText, 300, y, 0xFFFFFF55)

            y += 18
            if (y > hudHeight - 30) break
        }

        return y
    }

    private fun renderObjectives(guiGraphics: GuiGraphics, font: net.minecraft.client.gui.Font,
                                  objectives: List<NetworkHandler.ObjectiveInfo>, startY: Int): Int {
        var y = startY

        guiGraphics.fill(poseStack, 5, y, hudWidth - 5, y + 18, 0x8800008B)
        guiGraphics.drawString(font, "§l§bOBJETIVOS ESTRATÉGICOS", 10, y + 4, 0xFF8888FF)
        y += 20

        for (obj in objectives.take(3)) {
            if (y > hudHeight - 25) break

            val stateText = when (obj.state) {
                0 -> "§7⏳ Pendente"
                1 -> "§e§l▶ Em Andamento"
                2 -> "§a§l✓ Completado"
                3 -> "§c§l✗ Falhou"
                else -> "§7?"
            }

            val reqIcon = if (obj.requiredForVictory) " §c§l★" else ""
            val nameText = "§f${obj.name}$reqIcon"
            guiGraphics.drawString(font, nameText, 10, y, 0xFFFFFFFF)

            val stateWidth = font.width(stateText)
            guiGraphics.drawString(font, stateText, hudWidth - stateWidth - 10, y, 0xFFFFFFFF)
            y += 14

            // Progress bar
            if (obj.state == 1) {
                val barWidth = hudWidth - 20
                val barHeight = 6
                val fillWidth = (barWidth * obj.progress).toInt()

                // Background
                guiGraphics.fill(poseStack, 10, y, 10 + barWidth, y + barHeight, 0xFF333333)
                // Fill
                val progressColor = if (obj.progress < 0.3) 0xFFFF5555 else if (obj.progress < 0.7) 0xFFFFFF55 else 0xFF55FF55
                guiGraphics.fill(poseStack, 10, y, 10 + fillWidth, y + barHeight, progressColor)
                // Border
                guiGraphics.fill(poseStack, 10, y, 10 + barWidth, y + 1, 0xFF555555)
                guiGraphics.fill(poseStack, 10, y + barHeight - 1, 10 + barWidth, y + barHeight, 0xFF555555)
                guiGraphics.fill(poseStack, 10, y, 11, y + barHeight, 0xFF555555)
                guiGraphics.fill(poseStack, 10 + barWidth - 1, y, 10 + barWidth, y + barHeight, 0xFF555555)

                // Percentage
                val pctText = "${(obj.progress * 100).toInt()}%"
                val pctWidth = font.width(pctText)
                guiGraphics.drawString(font, pctText, 10 + (barWidth - pctWidth) / 2, y - 1, 0xFFFFFFFF)

                y += barHeight + 4
            } else if (obj.state == 2) {
                val completedText = "§a§l✓ Completado +${obj.rewardPoints} pts"
                guiGraphics.drawString(font, completedText, 10, y, 0xFF55FF55)
                y += 14
            } else if (obj.state == 3) {
                val failedText = "§c§l✗ Falhou"
                guiGraphics.drawString(font, failedText, 10, y, 0xFFFF5555)
                y += 14
            }

            // Time remaining
            if (obj.timeRemaining > 0 && obj.state == 1) {
                val timeText = "§7Tempo: §f${formatTime(obj.timeRemaining)}"
                guiGraphics.drawString(font, timeText, 10, y, 0xFFAAAAAA)
                y += 12
            }

            y += 4
        }

        return y
    }

    private fun formatTime(seconds: Int): String {
        if (seconds < 0) return "00:00"
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}