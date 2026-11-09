package com.tlgd.assault.client.gui

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.tlgd.assault.TLGAssaultMod
import com.tlgd.assault.network.NetworkHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import java.awt.Color

class AssaultMapScreen : Screen(Component.literal("§eMapa de Assalto Regions")) {

    private var regions: List<NetworkHandler.RegionMarkerData> = emptyList()
    private var selectedRegion: NetworkHandler.RegionMarkerData? = null
    private var scrollOffset = 0
    private var searchBox: EditBox? = null
    private var filterDimension = "all"
    private var filterState = "all"
    private val regionButtonHeight = 40

    override fun init() {
        super.init()

        // Search box
        searchBox = EditBox(font, width / 2 - 100, 30, 200, 20, Component.literal("Buscar..."))
        searchBox?.setMaxLength(32)
        searchBox?.setValue("")
        addRenderableWidget(searchBox!!)

        // Filter buttons
        val filterY = 55
        addRenderableWidget(Button.builder(Component.literal("Todas"), { btn ->
            filterDimension = "all"; updateButtonText(btn)
        }).bounds(width / 2 - 150, filterY, 100, 20).build())

        addRenderableWidget(Button.builder(Component.literal("Overworld"), { btn ->
            filterDimension = "minecraft:overworld"; updateButtonText(btn)
        }).bounds(width / 2 - 45, filterY, 100, 20).build())

        addRenderableWidget(Button.builder(Component.literal("Nether"), { btn ->
            filterDimension = "minecraft:the_nether"; updateButtonText(btn)
        }).bounds(width / 2 + 60, filterY, 100, 20).build())

        addRenderableWidget(Button.builder(Component.literal("The End"), { btn ->
            filterDimension = "minecraft:the_end"; updateButtonText(btn)
        }).bounds(width / 2 + 165, filterY, 100, 20).build())

        // State filter
        val stateY = 80
        addRenderableWidget(Button.builder(Component.literal("Todos Estados"), { btn ->
            filterState = "all"; updateButtonText(btn)
        }).bounds(width / 2 - 150, stateY, 100, 20).build())

        addRenderableWidget(Button.builder(Component.literal("Livres"), { btn ->
            filterState = "inactive"; updateButtonText(btn)
        }).bounds(width / 2 - 45, stateY, 100, 20).build())

        addRenderableWidget(Button.builder(Component.literal("Ocupadas"), { btn ->
            filterState = "peaceful"; updateButtonText(btn)
        }).bounds(width / 2 + 60, stateY, 100, 20).build())

        addRenderableWidget(Button.builder(Component.literal("Em Combate"), { btn ->
            filterState = "active"; updateButtonText(btn)
        }).bounds(width / 2 + 165, stateY, 100, 20).build())

        // Refresh button
        addRenderableWidget(Button.builder(Component.literal("§aAtualizar"), {
            requestRegions()
        }).bounds(width - 110, 5, 100, 20).build())

        // Close button
        addRenderableWidget(Button.builder(Component.literal("Fechar"), {
            onClose()
        }).bounds(width - 110, height - 25, 100, 20).build())

        // Request regions from server
        requestRegions()
    }

    private fun updateButtonText(btn: Button) {
        // Atualiza texto do botão baseado no filtro
    }

    private fun requestRegions() {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return
        // Enviar pacote para servidor
        // NetworkHandler.sendRequestAllRegions(player)
    }

    fun updateRegions(newRegions: List<NetworkHandler.RegionMarkerData>) {
        regions = newRegions
        scrollOffset = 0
        selectedRegion = null
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)

        // Title
        val title = "§e§lMapa de Assault Regions"
        val titleWidth = font.width(title)
        guiGraphics.drawString(font, title, (width - titleWidth) / 2, 8, 0xFFFFFFFF)

        // Subtitle
        val count = filteredRegions().size
        guiGraphics.drawString(font, "§7${count} região(ões) encontrada(s)", (width - font.width("${count} região(ões) encontrada(s)")) / 2, 24, 0xFFAAAAAA)

        // Search hint
        if (searchBox?.value.isNotBlank() == true) {
            guiGraphics.drawString(font, "§7Busca: §f${searchBox?.value}", width / 2 - 100, 15, 0xFF888888)
        }

        // Region list
        val listX = 20
        val listY = 110
        val listWidth = width - 40
        val listHeight = height - 150

        // Background
        guiGraphics.fill(listX - 5, listY - 5, listX + listWidth + 5, listY + listHeight + 5, 0xCC000000)
        guiGraphics.fill(listX - 5, listY - 5, listX + listWidth + 5, listY + 25, 0xCC333333)

        // Headers
        guiGraphics.drawString(font, "§l§eRegião", listX + 10, listY, 0xFFFFFF55)
        guiGraphics.drawString(font, "§l§eDimensão", listX + 200, listY, 0xFFFFFF55)
        guiGraphics.drawString(font, "§l§eDono", listX + 350, listY, 0xFFFFFF55)
        guiGraphics.drawString(font, "§l§eEstado", listX + 500, listY, 0xFFFFFF55)
        guiGraphics.drawString(font, "§l§eAções", listX + 620, listY, 0xFFFFFF55)

        // Render regions
        val filtered = filteredRegions()
        val visibleCount = listHeight / regionButtonHeight

        for (i in scrollOffset until min(scrollOffset + visibleCount + 1, filtered.size)) {
            val region = filtered[i]
            val itemY = listY + 25 + (i - scrollOffset) * regionButtonHeight

            if (itemY > listY + listHeight) break

            // Highlight selected
            if (selectedRegion?.id == region.id) {
                guiGraphics.fill(listX, itemY - 2, listX + listWidth, itemY + regionButtonHeight - 2, 0x88FFFFFF)
            } else if (i % 2 == 0) {
                guiGraphics.fill(listX, itemY - 2, listX + listWidth, itemY + regionButtonHeight - 2, 0x33FFFFFF)
            }

            // Region name
            guiGraphics.drawString(font, "§f${region.name}", listX + 10, itemY + 2, 0xFFFFFFFF)
            guiGraphics.drawString(font, "§7ID: ${region.id}", listX + 10, itemY + 14, 0xFF888888)

            // Dimension
            val dimName = when (region.dimension) {
                "minecraft:overworld" -> "§aOverworld"
                "minecraft:the_nether" -> "§cNether"
                "minecraft:the_end" -> "§dThe End"
                else -> "§7${region.dimension}"
            }
            guiGraphics.drawString(font, dimName, listX + 200, itemY + 8, 0xFFFFFFFF)

            // Owner
            val owner = region.ownerNation?.let { "§f$it" } ?: "§7Nenhum"
            guiGraphics.drawString(font, owner, listX + 350, itemY + 8, 0xFFFFFFFF)

            // State
            val (stateText, stateColor) = when (region.state) {
                0 -> "§7Livre" to 0xFF88FF88
                1 -> "§aPacífica" to 0xFF88FF88
                2 -> "§ePreparando" to 0xFFFFFF55
                3 -> "§cEM COMBATE" to 0xFFFF5555
                4 -> "§7Cooldown" to 0xFF888888
                else -> "§7?" to 0xFFFFFFFF
            }
            guiGraphics.drawString(font, stateText, listX + 500, itemY + 8, stateColor)

            // Action buttons (rendered as text, click handled in mouseClicked)
            val actionText = "§b[Info]  §e[TP]"
            guiGraphics.drawString(font, actionText, listX + 620, itemY + 8, 0xFFFFFFFF)
        }

        // Scroll indicator
        if (filtered.size > visibleCount) {
            val scrollBarHeight = (listHeight.toDouble() / filtered.size * visibleCount).toInt()
            val scrollBarY = listY + 25 + (scrollOffset.toDouble() / filtered.size * (listHeight - scrollBarHeight)).toInt()
            guiGraphics.fill(width - 15, scrollBarY, width - 5, scrollBarY + scrollBarHeight, 0x88FFFFFF)
        }

        // Selected region details panel
        selectedRegion?.let { renderDetailPanel(guiGraphics, it) }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun filteredRegions(): List<NetworkHandler.RegionMarkerData> {
        return regions.filter { region ->
            val matchesSearch = searchBox?.value.isBlank() == true ||
                region.name.lowercase().contains(searchBox?.value.lowercase() ?: "") ||
                region.id.lowercase().contains(searchBox?.value.lowercase() ?: "")
            val matchesDim = filterDimension == "all" || region.dimension == filterDimension
            val matchesState = filterState == "all" || when (filterState) {
                "inactive" -> region.state == 0
                "peaceful" -> region.state == 1
                "active" -> region.state == 3
                else -> true
            }
            matchesSearch && matchesDim && matchesState
        }
    }

    private fun renderDetailPanel(guiGraphics: GuiGraphics, region: NetworkHandler.RegionMarkerData) {
        val panelX = width - 320
        val panelY = 110
        val panelWidth = 300
        val panelHeight = 250

        guiGraphics.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight + 5, 0xCC000000)
        guiGraphics.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY + 30, 0xCC8B0000)

        guiGraphics.drawString(font, "§e§l${region.name}", panelX + 10, panelY + 5, 0xFFFFFFFF)
        guiGraphics.drawString(font, "§7ID: §f${region.id}", panelX + 10, panelY + 25, 0xFFAAAAAA)
        guiGraphics.drawString(font, "§7Dimensão: §f${region.dimension}", panelX + 10, panelY + 40, 0xFFAAAAAA)
        guiGraphics.drawString(font, "§7Bounds: §f(${region.minX}, ${region.minY}, ${region.minZ}) §7-> §f(${region.maxX}, ${region.maxY}, ${region.maxZ})", panelX + 10, panelY + 55, 0xFFAAAAAA)

        val area = ((region.maxX - region.minX + 1L) * (region.maxZ - region.minZ + 1L))
        guiGraphics.drawString(font, "§7Área: §f${area} blocos²", panelX + 10, panelY + 70, 0xFFAAAAAA)

        val owner = region.ownerNation?.let { "§f$it" } ?: "§7Nenhum"
        guiGraphics.drawString(font, "§7Dono: $owner", panelX + 10, panelY + 85, 0xFFAAAAAA)

        val (stateText, stateColor) = when (region.state) {
            0 -> "§7Livre" to 0xFF88FF88
            1 -> "§aPacífica" to 0xFF88FF88
            2 -> "§ePreparando" to 0xFFFFFF55
            3 -> "§cEM COMBATE" to 0xFFFF5555
            4 -> "§7Cooldown" to 0xFF888888
            else -> "§7?" to 0xFFFFFFFF
        }
        guiGraphics.drawString(font, "§7Estado: $stateText", panelX + 10, panelY + 100, stateColor)

        // Botões de ação
        val btnY = panelY + 130
        renderClickableButton(guiGraphics, "§aTeleportar", panelX + 10, btnY, 130, 20, 0x8800AA00)
        renderClickableButton(guiGraphics, "§eVer Info Completa", panelX + 150, btnY, 130, 20, 0x88AAAA00)
        renderClickableButton(guiGraphics, "§cIniciar Assalto", panelX + 10, btnY + 25, 130, 20, 0x88AA0000)
        renderClickableButton(guiGraphics, "§7Fechar Painel", panelX + 150, btnY + 25, 130, 20, 0x88555555)
    }

    private fun renderClickableButton(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, w: Int, h: Int, color: Int) {
        val mouseX = minecraft.mouseHandler.xpos().toInt() * width / minecraft.getWindow().guiScaledWidth
        val mouseY = minecraft.mouseHandler.ypos().toInt() * height / minecraft.getWindow().guiScaledHeight
        val hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h

        guiGraphics.fill(x, y, x + w, y + h, if (hovered) (color | 0xFF000000) else color)
        val textWidth = font.width(text)
        guiGraphics.drawString(font, text, x + (w - textWidth) / 2, y + (h - 8) / 2, 0xFFFFFFFF)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        // Check region list clicks
        val listX = 20
        val listY = 110
        val listWidth = width - 40
        val filtered = filteredRegions()

        for (i in scrollOffset until min(scrollOffset + (height - 150) / regionButtonHeight + 1, filtered.size)) {
            val itemY = listY + 25 + (i - scrollOffset) * regionButtonHeight
            val region = filtered[i]

            if (mx >= listX && mx < listX + listWidth && my >= itemY - 2 && my < itemY + regionButtonHeight - 2) {
                selectedRegion = if (selectedRegion?.id == region.id) null else region
                return true
            }
        }

        // Check detail panel buttons
        selectedRegion?.let { region ->
            val panelX = width - 320
            val btnY = 240

            // Teleport
            if (mx >= panelX + 10 && mx < panelX + 140 && my >= btnY && my < btnY + 20) {
                teleportToRegion(region)
                return true
            }

            // Info
            if (mx >= panelX + 150 && mx < panelX + 280 && my >= btnY && my < btnY + 20) {
                openInfoScreen(region)
                return true
            }

            // Start assault
            if (mx >= panelX + 10 && mx < panelX + 140 && my >= btnY + 25 && my < btnY + 45) {
                startAssault(region)
                return true
            }

            // Close panel
            if (mx >= panelX + 150 && mx < panelX + 280 && my >= btnY + 25 && my < btnY + 45) {
                selectedRegion = null
                return true
            }
        }

        // Scroll wheel
        if (my >= listY && my < listY + height - 150) {
            // Handled by mouseScrolled
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean {
        val filtered = filteredRegions()
        val visibleCount = (height - 150) / regionButtonHeight
        val maxScroll = max(0, filtered.size - visibleCount)
        scrollOffset = Mth.clamp(scrollOffset - deltaY.toInt(), 0, maxScroll)
        return true
    }

    private fun teleportToRegion(region: NetworkHandler.RegionMarkerData) {
        val player = minecraft.player ?: return
        val centerX = (region.minX + region.maxX) / 2
        val centerZ = (region.minZ + region.maxZ) / 2
        val centerY = region.maxY + 5

        // Enviar comando de TP para servidor
        player.connection.sendCommand("/tpg ${region.dimension} $centerX $centerY $centerZ")
        // Ou usar packet customizado
        onClose()
    }

    private fun openInfoScreen(region: NetworkHandler.RegionMarkerData) {
        val minecraft = Minecraft.getInstance()
        minecraft.setScreen(AssaultRegionInfoScreen(region))
    }

    private fun startAssault(region: NetworkHandler.RegionMarkerData) {
        val player = minecraft.player ?: return
        player.connection.sendCommand("/assault begin ${region.id}")
        onClose()
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (keyCode == 256) { // ESC
            if (selectedRegion != null) {
                selectedRegion = null
                return true
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun isPauseScreen(): Boolean = false
}