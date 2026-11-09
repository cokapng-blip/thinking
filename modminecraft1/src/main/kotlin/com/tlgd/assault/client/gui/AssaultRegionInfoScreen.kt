package com.tlgd.assault.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.tlgd.assault.TLGAssaultMod
import com.tlgd.assault.network.NetworkHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber

class AssaultRegionInfoScreen(private val region: NetworkHandler.RegionMarkerData) : Screen(Component.literal("§eInfo: ${region.name}")) {

    private var assaultData: NetworkHandler.AssaultHUDPacket? = null
    private var rewardData: Map<String, Any>? = null
    private var tab = 0 // 0=Geral, 1=Assalto, 2=Recompensas, 3=Histórico

    override fun init() {
        super.init()

        // Tabs
        val tabY = 40
        val tabWidth = 100
        val startX = width / 2 - (tabWidth * 4) / 2

        val tabs = listOf("Geral", "Assalto Ativo", "Recompensas", "Histórico")
        for ((i, name) in tabs.withIndex()) {
            val x = startX + i * tabWidth
            addRenderableWidget(Button.builder(Component.literal(if (i == tab) "§l$name" else name), { btn ->
                tab = i
                rebuildTabs()
            }).bounds(x, tabY, tabWidth, 20).build())
        }

        // Back button
        addRenderableWidget(Button.builder(Component.literal("§cVoltar"), {
            minecraft.setScreen(AssaultMapScreen())
        }).bounds(10, height - 30, 100, 20).build())

        // Refresh button
        addRenderableWidget(Button.builder(Component.literal("§aAtualizar"), {
            requestData()
        }).bounds(width - 110, height - 30, 100, 20).build())

        requestData()
    }

    private fun rebuildTabs() {
        clearWidgets()
        init()
    }

    private fun requestData() {
        val player = minecraft.player ?: return
        // Request region data from server
        // NetworkHandler.sendRequestRegionData(player, region.id)
    }

    fun updateAssaultData(data: NetworkHandler.AssaultHUDPacket) {
        assaultData = data
    }

    fun updateRewardData(data: Map<String, Any>) {
        rewardData = data
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)

        // Header
        guiGraphics.drawString(font, "§e§l${region.name}", width / 2 - font.width(region.name) / 2, 10, 0xFFFFFFFF)
        guiGraphics.drawString(font, "§7ID: §f${region.id}  §7|  §7Dimensão: §f${region.dimension}", width / 2 - 150, 24, 0xFFAAAAAA)

        // State badge
        val (stateText, stateColor) = when (region.state) {
            0 -> "§7LIVRE" to 0xFF88FF88
            1 -> "§aPACÍFICA" to 0xFF88FF88
            2 -> "§ePREPARANDO" to 0xFFFFFF55
            3 -> "§cEM COMBATE" to 0xFFFF5555
            4 -> "§7COOLDOWN" to 0xFF888888
            else -> "§7?" to 0xFFFFFFFF
        }
        val badgeX = width / 2 - font.width(stateText) / 2 - 10
        guiGraphics.fill(badgeX - 5, 48, badgeX + font.width(stateText) + 15, 68, stateColor and 0x88FFFFFF)
        guiGraphics.drawString(font, stateText, badgeX, 50, 0xFFFFFFFF)

        when (tab) {
            0 -> renderGeneralTab(guiGraphics)
            1 -> renderAssaultTab(guiGraphics)
            2 -> renderRewardsTab(guiGraphics)
            3 -> renderHistoryTab(guiGraphics)
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderGeneralTab(guiGraphics: GuiGraphics) {
        val startY = 80
        val lineHeight = 20
        var y = startY

        // Bounds
        guiGraphics.drawString(font, "§e§lLocalização", 30, y, 0xFFFFFF55)
        y += lineHeight
        guiGraphics.drawString(font, "§7Mín: §f(${region.minX}, ${region.minY}, ${region.minZ})", 40, y, 0xFFFFFFFF)
        y += lineHeight
        guiGraphics.drawString(font, "§7Máx: §f(${region.maxX}, ${region.maxY}, ${region.maxZ})", 40, y, 0xFFFFFFFF)
        y += lineHeight

        val area = ((region.maxX - region.minX + 1L) * (region.maxZ - region.minZ + 1L))
        val volume = area * (region.maxY - region.minY + 1L)
        guiGraphics.drawString(font, "§7Área: §f${area} blocos²  §7|  §7Volume: §f${volume} blocos³", 40, y, 0xFFFFFFFF)
        y += lineHeight * 2

        // Owner
        guiGraphics.drawString(font, "§e§lPropriedade", 30, y, 0xFFFFFF55)
        y += lineHeight
        val owner = region.ownerNation?.let { "§f$it" } ?: "§7Nenhuma nação"
        guiGraphics.drawString(font, "§7Dona: $owner", 40, y, 0xFFFFFFFF)
        y += lineHeight

        if (region.ownerNation != null) {
            guiGraphics.drawString(font, "§aEsta nação recebe recompensas diárias!", 40, y, 0xFF88FF88)
        } else {
            guiGraphics.drawString(font, "§eCapture esta região para ganhar recompensas!", 40, y, 0xFFFFFF55)
        }
        y += lineHeight * 2

        // Next window
        guiGraphics.drawString(font, "§e§lPróxima Janela de Ataque", 30, y, 0xFFFFFF55)
        y += lineHeight
        guiGraphics.drawString(font, "§7Use §f/assault info ${region.id} §7no jogo para ver horários exatos", 40, y, 0xFFAAAAAA)
        y += lineHeight * 2

        // Quick actions
        guiGraphics.drawString(font, "§e§lAções Rápidas", 30, y, 0xFFFFFF55)
        y += lineHeight

        val btnY = y
        renderButton(guiGraphics, "§aTeleportar para Centro", 30, btnY, 180, 22, 0x8800AA00) {
            teleportToRegion()
        }
        renderButton(guiGraphics, "§bVer no Mapa", 220, btnY, 120, 22, 0x880000AA) {
            minecraft.setScreen(AssaultMapScreen())
        }
        renderButton(guiGraphics, "§eVer Leaderboard", 350, btnY, 140, 22, 0x88AAAA00) {
            // Abrir leaderboard
        }
        renderButton(guiGraphics, "§cIniciar Assalto", 500, btnY, 130, 22, 0x88AA0000) {
            startAssault()
        }
    }

    private fun renderAssaultTab(guiGraphics: GuiGraphics) {
        val startY = 80
        var y = startY

        assaultData?.let { data ->
            // Timer
            val minutes = data.remainingSeconds / 60
            val seconds = data.remainingSeconds % 60
            val timeStr = String.format("⏱ Tempo Restante: %02d:%02d", minutes, seconds)
            val timeColor = if (data.remainingSeconds < 300) 0xFFFF5555 else 0xFFFFFFFF
            guiGraphics.drawString(font, "§e§l$timeStr", 30, y, timeColor)
            y += 30

            // Initiator
            guiGraphics.drawString(font, "§7Iniciado por: §f${data.regionName}", 30, y, 0xFFFFFFFF)
            y += 30

            // Leaderboard
            guiGraphics.drawString(font, "§e§lPlacar Atual", 30, y, 0xFFFFFF55)
            y += 25

            if (data.leaderboard.isEmpty()) {
                guiGraphics.drawString(font, "§7Nenhuma pontuação ainda...", 40, y, 0xFF888888)
            } else {
                for ((i, entry) in data.leaderboard.withIndex()) {
                    val medal = when (i) {
                        0 -> "§6🥇 "
                        1 -> "§f🥈 "
                        2 -> "§6🥉 "
                        else -> "§7${i + 1}. "
                    }
                    val line = "$medal§f${entry.nationName} §7- §e${entry.score} §7pts"
                    guiGraphics.drawString(font, line, 40, y, 0xFFFFFFFF)
                    y += 18
                    val subLine = "§8   Kills: ${entry.kills}  |  Veículos: ${entry.vehiclesDestroyed}  |  Capturas: ${entry.flagsCaptured}"
                    guiGraphics.drawString(font, subLine, 40, y, 0xFFAAAAAA)
                    y += 22
                }
            }
        } ?: run {
            guiGraphics.drawString(font, "§7Nenhum assalto ativo no momento.", 30, y, 0xFF888888)
            y += 25
            guiGraphics.drawString(font, "§7A próxima janela de ataque iniciará automaticamente.", 30, y, 0xFF888888)
        }
    }

    private fun renderRewardsTab(guiGraphics: GuiGraphics) {
        val startY = 80
        var y = startY

        guiGraphics.drawString(font, "§e§lRecompensas Diárias", 30, y, 0xFFFFFF55)
        y += 30

        // Money
        guiGraphics.drawString(font, "§7Dinheiro: §a$${com.tlgd.assault.config.TLGDAssaultConfig.dailyMoneyReward}/dia", 40, y, 0xFFFFFFFF)
        y += 25

        // Resources
        if (com.tlgd.assault.config.TLGDAssaultConfig.dailyResourceRewardEnabled) {
            guiGraphics.drawString(font, "§7Recursos:", 40, y, 0xFFFFFF55)
            y += 22

            val items = com.tlgd.assault.config.TLGDAssaultConfig.resourceRewardItems
            if (items.isEmpty()) {
                guiGraphics.drawString(font, "§8  (Nenhum item configurado)", 50, y, 0xFF888888)
            } else {
                for (itemStr in items) {
                    guiGraphics.drawString(font, "§8  • §f$itemStr", 50, y, 0xFFFFFFFF)
                    y += 20
                }
            }
            y += 10
        } else {
            guiGraphics.drawString(font, "§cRecursos desabilitados na configuração", 40, y, 0xFFFF5555)
            y += 25
        }

        // Reward status
        y += 10
        guiGraphics.drawString(font, "§e§lStatus", 30, y, 0xFFFFFF55)
        y += 25

        if (region.ownerNation != null) {
            guiGraphics.drawString(font, "§a✓ Região capturada - Recompensas ativas!", 40, y, 0xFF88FF88)
            y += 22
            guiGraphics.drawString(font, "§7Próximo pagamento: §fAmanhã às 00:00 (horário do servidor)", 40, y, 0xFFAAAAAA)
        } else {
            guiGraphics.drawString(font, "§c✗ Região livre - Capture para ativar recompensas", 40, y, 0xFFFF5555)
        }
    }

    private fun renderHistoryTab(guiGraphics: GuiGraphics) {
        val startY = 80
        var y = startY

        guiGraphics.drawString(font, "§e§lHistórico de Capturas", 30, y, 0xFFFFFF55)
        y += 30

        // Placeholder - seria carregado do servidor
        guiGraphics.drawString(font, "§7Carregando histórico do servidor...", 40, y, 0xFF888888)
        y += 25

        // Exemplo de como seria
        val mockHistory = listOf(
            "§72024-01-15 §fNação A §7capturou de §fNação B §7(1.234 pts)",
            "§72024-01-08 §fNação B §7capturou (livre) §7(987 pts)",
            "§72024-01-01 §fNação C §7defendeu contra §fNação A §7(2.100 pts)"
        )

        for (entry in mockHistory) {
            guiGraphics.drawString(font, entry, 40, y, 0xFFFFFFFF)
            y += 22
        }
    }

    private fun renderButton(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, w: Int, h: Int, color: Int, action: () -> Unit) {
        val mouseX = minecraft.mouseHandler.xpos().toInt() * width / minecraft.getWindow().guiScaledWidth
        val mouseY = minecraft.mouseHandler.ypos().toInt() * height / minecraft.getWindow().guiScaledHeight
        val hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h

        // Store action for click handling
        if (hovered && minecraft.mouseHandler.isLeftPressed()) {
            // This is hacky - proper way is to use Button widgets
        }

        guiGraphics.fill(x, y, x + w, y + h, if (hovered) (color | 0xFF000000) else color)
        val textWidth = font.width(text)
        guiGraphics.drawString(font, text, x + (w - textWidth) / 2, y + (h - 8) / 2, 0xFFFFFFFF)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        // Check buttons in General tab
        if (tab == 0) {
            val btnY = 180 // approximate
            if (my >= btnY && my < btnY + 22) {
                if (mx >= 30 && mx < 210) { teleportToRegion(); return true }
                if (mx >= 220 && mx < 340) { minecraft.setScreen(AssaultMapScreen()); return true }
                if (mx >= 350 && mx < 490) { /* leaderboard */ return true }
                if (mx >= 500 && mx < 630) { startAssault(); return true }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    private fun teleportToRegion() {
        val player = minecraft.player ?: return
        val centerX = (region.minX + region.maxX) / 2
        val centerZ = (region.minZ + region.maxZ) / 2
        val centerY = region.maxY + 5
        player.connection.sendCommand("/tpg ${region.dimension} $centerX $centerY $centerZ")
        onClose()
    }

    private fun startAssault() {
        val player = minecraft.player ?: return
        player.connection.sendCommand("/assault begin ${region.id}")
        onClose()
    }

    override fun isPauseScreen(): Boolean = false
}