package com.tlgd.navalwarfare.gui

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.tlgd.navalwarfare.TLGAssaultMod
import com.tlgd.navalwarfare.config.NavalWarfareConfig
import com.tlgd.navalwarfare.network.NetworkHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.*
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.Mth
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber

// ===== CORES DO TEMA MILITAR =====
object MilitaryColors {
    val BACKGROUND_DARK = 0xFF0D0D0D
    val BACKGROUND_MEDIUM = 0xFF1A1A1A
    val BACKGROUND_LIGHT = 0xFF2D2D2D
    val PANEL_BG = 0xCC000000
    val PANEL_HEADER = 0xCC1B4D1B // Verde militar escuro
    val PANEL_BORDER = 0xFF2D2D2D

    val TEXT_WHITE = 0xFFFFFFFF
    val TEXT_GRAY = 0xFFAAAAAA
    val TEXT_DARK_GRAY = 0xFF888888
    val TEXT_GREEN = 0xFF88FF88
    val TEXT_DARK_GREEN = 0xFF1B4D1B
    val TEXT_YELLOW = 0xFFFFFF55
    val TEXT_RED = 0xFFFF5555
    val TEXT_ORANGE = 0xFFFFCC00
    val TEXT_CYAN = 0xFF00FFFF
    val TEXT_BLUE = 0xFF8888FF

    val BUTTON_NORMAL = 0x881B4D1B
    val BUTTON_HOVER = 0xCC1B4D1B
    val BUTTON_PRESSED = 0xFF1B4D1B
    val BUTTON_RED = 0x888B0000
    val BUTTON_RED_HOVER = 0xCC8B0000
    val BUTTON_YELLOW = 0x888B8B00
    val BUTTON_YELLOW_HOVER = 0xCC8B8B00
    val BUTTON_BLUE = 0x8800008B
    val BUTTON_BLUE_HOVER = 0xCC00008B
    val BUTTON_DISABLED = 0x88333333

    val HEALTH_HIGH = 0xFF00FF00
    val HEALTH_MED = 0xFFFFFF00
    val HEALTH_LOW = 0xFFFF0000

    val RADAR_BACKGROUND = 0xCC001100
    val RADAR_GRID = 0x44003300
    val RADAR_FRIENDLY = 0xFF00FF00
    val RADAR_ENEMY = 0xFFFF0000
    val RADAR_NEUTRAL = 0xFFFFFF00
    val RADAR_PLAYER = 0xFF00FFFF
    val RADAR_OBJECTIVE = 0xFF00AAFF
}

// ===== COMPONENTES BASE =====

abstract class MilitaryWidget(x: Int, y: Int, width: Int, height: Int) : Widget(Component.empty()) {
    var isHovered = false
    var isFocused = false

    override fun getX() = x
    override fun getY() = y
    override fun getWidth() = width
    override fun getHeight() = height
}

class TacticalButton(
    x: Int, y: Int, width: Int, height: Int,
    text: String,
    onPress: (TacticalButton) -> Unit,
    val style: Style = Style.NORMAL
) : Button(x, y, width, height, Component.literal(text), onPress, DEFAULT_NARRATION) {

    enum class Style { NORMAL, RED, YELLOW, BLUE, DANGER, SUCCESS }

    var customTextColor: Int? = null

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val colors = when (style) {
            Style.NORMAL -> MilitaryColors.BUTTON_NORMAL to MilitaryColors.BUTTON_HOVER
            Style.RED -> MilitaryColors.BUTTON_RED to MilitaryColors.BUTTON_RED_HOVER
            Style.YELLOW -> MilitaryColors.BUTTON_YELLOW to MilitaryColors.BUTTON_YELLOW_HOVER
            Style.BLUE -> MilitaryColors.BUTTON_BLUE to MilitaryColors.BUTTON_BLUE_HOVER
            Style.DANGER -> MilitaryColors.BUTTON_RED to MilitaryColors.BUTTON_RED_HOVER
            Style.SUCCESS -> MilitaryColors.BUTTON_NORMAL to MilitaryColors.BUTTON_HOVER
        }

        val bgColor = if (isHoveredOrFocused()) colors.second else colors.first
        val textColor = customTextColor ?: MilitaryColors.TEXT_WHITE

        // Background
        guiGraphics.fill(getX(), getY(), getX() + width, getY() + height, bgColor)
        // Border
        guiGraphics.fill(getX(), getY(), getX() + width, getY() + 1, MilitaryColors.PANEL_BORDER)
        guiGraphics.fill(getX(), getY() + height - 1, getX() + width, getY() + height, MilitaryColors.PANEL_BORDER)
        guiGraphics.fill(getX(), getY(), getX() + 1, getY() + height, MilitaryColors.PANEL_BORDER)
        guiGraphics.fill(getX() + width - 1, getY(), getX() + width, getY() + height, MilitaryColors.PANEL_BORDER)

        // Text
        val textWidth = Minecraft.getInstance().font.width(getMessage().getString())
        guiGraphics.drawString(
            Minecraft.getInstance().font,
            getMessage(),
            getX() + (width - textWidth) / 2,
            getY() + (height - 8) / 2,
            textColor
        )
    }
}

class MilitaryPanel(x: Int, y: Int, width: Int, height: Int, val title: String?) : MilitaryWidget(x, y, width, height) {
    var showHeader = true
    var headerColor = MilitaryColors.PANEL_HEADER
    var backgroundColor = MilitaryColors.PANEL_BG
    var borderColor = MilitaryColors.PANEL_BORDER

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Background
        guiGraphics.fill(x, y, x + width, y + height, backgroundColor)

        // Header
        if (showHeader && title != null) {
            guiGraphics.fill(x, y, x + width, y + 24, headerColor)
            guiGraphics.fill(x, y + 24, x + width, y + 25, borderColor)

            val font = Minecraft.getInstance().font
            val titleText = "§l$title"
            val titleWidth = font.width(titleText)
            guiGraphics.drawString(font, titleText, x + 10, y + 6, MilitaryColors.TEXT_WHITE)
        }

        // Border
        guiGraphics.fill(x, y, x + width, y + 1, borderColor)
        guiGraphics.fill(x, y + height - 1, x + width, y + height, borderColor)
        guiGraphics.fill(x, y, x + 1, y + height, borderColor)
        guiGraphics.fill(x + width - 1, y, x + width, y + height, borderColor)

        renderContent(guiGraphics, mouseX, mouseY, partialTick)
    }

    open fun renderContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}
}

class RadarMap(
    x: Int, y: Int, width: Int, height: Int,
    val centerX: Double, val centerZ: Double,
    val scale: Double = 1.0 // blocos por pixel
) : MilitaryWidget(x, y, width, height) {

    var playerPos: BlockPos? = null
    var playerRotation: Float = 0f
    var entities: List<RadarEntity> = emptyList()
    var objectives: List<RadarObjective> = emptyList()
    val regionBounds: List<RadarBounds> = emptyList()

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val poseStack = PoseStack()
        poseStack.pushPose()

        // Background
        guiGraphics.fill(x, y, x + width, y + height, MilitaryColors.RADAR_BACKGROUND)

        // Grid
        drawGrid(guiGraphics)

        // Region bounds
        drawRegionBounds(guiGraphics)

        // Objectives
        drawObjectives(guiGraphics)

        // Entities
        drawEntities(guiGraphics)

        // Player
        drawPlayer(guiGraphics)

        // Compass
        drawCompass(guiGraphics)

        // Scale indicator
        drawScale(guiGraphics)

        poseStack.popPose()

        // Border
        guiGraphics.fill(x, y, x + width, y + 1, MilitaryColors.PANEL_BORDER)
        guiGraphics.fill(x, y + height - 1, x + width, y + height, MilitaryColors.PANEL_BORDER)
        guiGraphics.fill(x, y, x + 1, y + height, MilitaryColors.PANEL_BORDER)
        guiGraphics.fill(x + width - 1, y, x + width, y + height, MilitaryColors.PANEL_BORDER)
    }

    private fun drawGrid(guiGraphics: GuiGraphics) {
        val gridSize = 50 // pixels
        val color = MilitaryColors.RADAR_GRID

        for (i in 0..width / gridSize) {
            val gx = x + i * gridSize
            guiGraphics.fill(gx, y, gx + 1, y + height, color)
        }
        for (i in 0..height / gridSize) {
            val gy = y + i * gridSize
            guiGraphics.fill(x, gy, x + width, gy + 1, color)
        }

        // Center lines
        val cx = x + width / 2
        val cy = y + height / 2
        guiGraphics.fill(cx - 1, y, cx + 1, y + height, MilitaryColors.TEXT_DARK_GREEN)
        guiGraphics.fill(x, cy - 1, x + width, cy + 1, MilitaryColors.TEXT_DARK_GREEN)
    }

    private fun drawRegionBounds(guiGraphics: GuiGraphics) {
        for (bounds in regionBounds) {
            val minPx = worldToScreen(bounds.minX, bounds.minZ)
            val maxPx = worldToScreen(bounds.maxX, bounds.maxZ)

            val color = bounds.color
            // Desenhar retângulo
            guiGraphics.fill(minPx.x, minPx.y, maxPx.x, minPx.y + 1, color)
            guiGraphics.fill(minPx.x, maxPx.y - 1, maxPx.x, maxPx.y, color)
            guiGraphics.fill(minPx.x, minPx.y, minPx.x + 1, maxPx.y, color)
            guiGraphics.fill(maxPx.x - 1, minPx.y, maxPx.x, maxPx.y, color)
        }
    }

    private fun drawObjectives(guiGraphics: GuiGraphics) {
        for (obj in objectives) {
            val pos = worldToScreen(obj.x, obj.z)
            val color = when (obj.type) {
                "control" -> MilitaryColors.TEXT_CYAN
                "escort" -> MilitaryColors.TEXT_YELLOW
                "destroy" -> MilitaryColors.TEXT_RED
                "superiority" -> MilitaryColors.TEXT_BLUE
                else -> MilitaryColors.TEXT_GREEN
            }

            // Diamond shape
            val size = 8
            for (i in -size..size) {
                val w = size - abs(i)
                guiGraphics.fill(pos.x - w, pos.y + i, pos.x + w + 1, pos.y + i + 1, color)
            }

            // Progress ring if active
            if (obj.progress > 0f && obj.progress < 1f) {
                drawProgressRing(guiGraphics, pos.x, pos.y, 12, obj.progress, color)
            }
        }
    }

    private fun drawProgressRing(guiGraphics: GuiGraphics, cx: Int, cy: Int, radius: Int, progress: Float, color: Int) {
        val segments = 32
        val filledSegments = (segments * progress).toInt()
        for (i in 0 until filledSegments) {
            val angle1 = (i * 360.0 / segments) * Math.PI / 180
            val angle2 = ((i + 1) * 360.0 / segments) * Math.PI / 180
            val x1 = (cx + radius * Math.cos(angle1)).toInt()
            val y1 = (cy + radius * Math.sin(angle1)).toInt()
            val x2 = (cx + radius * Math.cos(angle2)).toInt()
            val y2 = (cy + radius * Math.sin(angle2)).toInt()
            guiGraphics.fill(min(x1, x2), min(y1, y2), max(x1, x2) + 1, max(y1, y2) + 1, color)
        }
    }

    private fun drawEntities(guiGraphics: GuiGraphics) {
        for (entity in entities) {
            val pos = worldToScreen(entity.x, entity.z)
            val color = when (entity.relation) {
                Relation.FRIENDLY -> MilitaryColors.RADAR_FRIENDLY
                Relation.ENEMY -> MilitaryColors.RADAR_ENEMY
                Relation.NEUTRAL -> MilitaryColors.RADAR_NEUTRAL
            }

            // Triangle pointing in direction
            val size = 6
            val angle = Math.toRadians(entity.rotation)
            val x1 = (pos.x + size * Math.cos(angle)).toInt()
            val y1 = (pos.y + size * Math.sin(angle)).toInt()
            val x2 = (pos.x + size * Math.cos(angle + 2.5)).toInt()
            val y2 = (pos.y + size * Math.sin(angle + 2.5)).toInt()
            val x3 = (pos.x + size * Math.cos(angle - 2.5)).toInt()
            val y3 = (pos.y + size * Math.sin(angle - 2.5)).toInt()

            // Fill triangle (simplified as rect for now)
            guiGraphics.fill(pos.x - 3, pos.y - 3, pos.x + 3, pos.y + 3, color)
        }
    }

    private fun drawPlayer(guiGraphics: GuiGraphics) {
        playerPos?.let { pos ->
            val screenPos = worldToScreen(pos.x.toDouble(), pos.z.toDouble())
            val color = MilitaryColors.RADAR_PLAYER
            val size = 8
            // Circle
            for (i in -size..size) {
                val w = (size * size - i * i).toDouble().sqrt().toInt()
                guiGraphics.fill(screenPos.x - w, screenPos.y + i, screenPos.x + w + 1, screenPos.y + i + 1, color)
            }
            // Direction line
            val angle = Math.toRadians(playerRotation)
            val endX = (screenPos.x + 15 * Math.cos(angle)).toInt()
            val endY = (screenPos.y + 15 * Math.sin(angle)).toInt()
            drawLine(guiGraphics, screenPos.x, screenPos.y, endX, endY, color)
        }
    }

    private fun drawLine(guiGraphics: GuiGraphics, x1: Int, y1: Int, x2: Int, y2: Int, color: Int) {
        val dx = abs(x2 - x1)
        val dy = abs(y2 - y1)
        val sx = if (x1 < x2) 1 else -1
        val sy = if (y1 < y2) 1 else -1
        var err = dx - dy
        var cx = x1
        var cy = y1
        while (true) {
            guiGraphics.fill(cx, cy, cx + 1, cy + 1, color)
            if (cx == x2 && cy == y2) break
            val e2 = 2 * err
            if (e2 > -dy) { err -= dy; cx += sx }
            if (e2 < dx) { err += dx; cy += sy }
        }
    }

    private fun drawCompass(guiGraphics: GuiGraphics) {
        val font = Minecraft.getInstance().font
        val cx = x + width - 30
        val cy = y + 30
        val directions = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")

        for (i in directions.indices) {
            val angle = (i * 45.0 - playerRotation) * Math.PI / 180
            val dist = 20.0
            val dx = (cx + dist * Math.sin(angle)).toInt()
            val dy = (cy - dist * Math.cos(angle)).toInt()
            val dir = directions[i]
            val color = if (i == 0 || i == 4) MilitaryColors.TEXT_YELLOW else MilitaryColors.TEXT_GRAY
            guiGraphics.drawString(font, dir, dx - font.width(dir) / 2, dy - 4, color)
        }
    }

    private fun drawScale(guiGraphics: GuiGraphics) {
        val font = Minecraft.getInstance().font
        val scaleText = "1px = ${(scale * 100).toInt() / 100.0} blocos"
        guiGraphics.drawString(font, scaleText, x + 5, y + height - 15, MilitaryColors.TEXT_DARK_GRAY)
    }

    private fun worldToScreen(worldX: Double, worldZ: Double): Pair<Int, Int> {
        val relX = (worldX - centerX) * scale
        val relZ = (worldZ - centerZ) * scale
        val sx = (x + width / 2 + relX).toInt()
        val sy = (y + height / 2 - relZ).toInt() // Z invertido
        return sx to sy
    }

    data class RadarEntity(
        val x: Double, val z: Double,
        val rotation: Float,
        val relation: Relation,
        val name: String,
        val vehicleClass: String?
    )

    data class RadarObjective(
        val x: Double, val z: Double,
        val type: String,
        val progress: Float,
        val name: String
    )

    data class RadarBounds(
        val minX: Double, val minZ: Double,
        val maxX: Double, val maxZ: Double,
        val color: Int
    )

    enum class Relation { FRIENDLY, ENEMY, NEUTRAL }
}

// ===== TELAS PRINCIPAIS =====

class NavalWarfareMainScreen : Screen(Component.literal("§e§lNAVAL WARFARE")) {
    private var selectedTab = 0
    private val tabs = listOf("Mapa", "Regiões", "Rankings", "Histórico", "Admin")
    private var regionList: List<NetworkHandler.RegionMarkerData> = emptyList()
    private var selectedRegion: NetworkHandler.RegionMarkerData? = null
    private var scrollOffset = 0

    override fun init() {
        super.init()

        // Tabs
        val tabY = 30
        val tabWidth = 100
        val startX = width / 2 - (tabWidth * tabs.size) / 2

        for ((i, name) in tabs.withIndex()) {
            val x = startX + i * tabWidth
            addRenderableWidget(TacticalButton(x, tabY, tabWidth, 24, name, { btn ->
                selectedTab = tabs.indexOf(btn.getMessage().getString())
                rebuildWidgets()
            }, if (i == selectedTab) TacticalButton.Style.SUCCESS else TacticalButton.Style.NORMAL))
        }

        // Refresh button
        addRenderableWidget(TacticalButton(width - 110, 5, 100, 20, "§aAtualizar", { _ ->
            requestRegions()
        }, TacticalButton.Style.BLUE))

        requestRegions()
    }

    private fun rebuildWidgets() {
        clearWidgets()
        init()
    }

    private fun requestRegions() {
        val player = Minecraft.getInstance().player ?: return
        // Enviar packet request
    }

    fun updateRegions(regions: List<NetworkHandler.RegionMarkerData>) {
        regionList = regions
        scrollOffset = 0
        selectedRegion = null
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)

        // Title
        val title = "§e§lNAVAL WARFARE §7- §fSistema de Guerras Navais"
        guiGraphics.drawString(font, title, width / 2 - font.width(title) / 2, 8, MilitaryColors.TEXT_YELLOW)

        when (selectedTab) {
            0 -> renderMapTab(guiGraphics)
            1 -> renderRegionsTab(guiGraphics, mouseX, mouseY)
            2 -> renderRankingsTab(guiGraphics)
            3 -> renderHistoryTab(guiGraphics)
            4 -> renderAdminTab(guiGraphics)
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderMapTab(guiGraphics: GuiGraphics) {
        // Radar map overview
        val mapX = 20
        val mapY = 70
        val mapWidth = width - 40
        val mapHeight = height - 100

        // Placeholder for radar
        guiGraphics.fill(mapX, mapY, mapX + mapWidth, mapY + mapHeight, MilitaryColors.RADAR_BACKGROUND)
        guiGraphics.drawString(font, "§7Mapa Tático Mundial - Implementar RadarMap widget", mapX + 20, mapY + 20, MilitaryColors.TEXT_GRAY)
    }

    private fun renderRegionsTab(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val listX = 20
        val listY = 70
        val listWidth = width - 40
        val listHeight = height - 100
        val rowHeight = 40

        // Header
        guiGraphics.fill(listX, listY - 30, listX + listWidth, listY - 5, MilitaryColors.PANEL_HEADER)
        guiGraphics.drawString(font, "§l§eRegião", listX + 10, listY - 25, MilitaryColors.TEXT_WHITE)
        guiGraphics.drawString(font, "§l§eDimensão", listX + 200, listY - 25, MilitaryColors.TEXT_WHITE)
        guiGraphics.drawString(font, "§l§eDona", listX + 350, listY - 25, MilitaryColors.TEXT_WHITE)
        guiGraphics.drawString(font, "§l§eEstado", listX + 500, listY - 25, MilitaryColors.TEXT_WHITE)
        guiGraphics.drawString(font, "§l§ePróxima Janela", listX + 620, listY - 25, MilitaryColors.TEXT_WHITE)

        // List
        val visibleCount = listHeight / rowHeight

        for (i in scrollOffset until min(scrollOffset + visibleCount + 1, regionList.size)) {
            val region = regionList[i]
            val itemY = listY + (i - scrollOffset) * rowHeight

            if (itemY > listY + listHeight) break

            // Highlight
            if (selectedRegion?.id == region.id) {
                guiGraphics.fill(listX, itemY - 5, listX + listWidth, itemY + rowHeight - 5, 0x44FFFFFF)
            } else if (i % 2 == 0) {
                guiGraphics.fill(listX, itemY - 5, listX + listWidth, itemY + rowHeight - 5, 0x22FFFFFF)
            }

            // Data
            guiGraphics.drawString(font, "§f${region.name}", listX + 10, itemY, MilitaryColors.TEXT_WHITE)
            guiGraphics.drawString(font, "§7ID: ${region.id}", listX + 10, itemY + 14, MilitaryColors.TEXT_DARK_GRAY)

            val dimName = when (region.dimension) {
                "minecraft:overworld" -> "§aOverworld"
                "minecraft:the_nether" -> "§cNether"
                "minecraft:the_end" -> "§dThe End"
                else -> "§7${region.dimension}"
            }
            guiGraphics.drawString(font, dimName, listX + 200, itemY + 7, MilitaryColors.TEXT_WHITE)

            val owner = region.ownerNation?.let { "§f$it" } ?: "§7Nenhuma"
            guiGraphics.drawString(font, owner, listX + 350, itemY + 7, MilitaryColors.TEXT_WHITE)

            val (stateText, stateColor) = when (region.state) {
                0 -> "§7Livre" to MilitaryColors.TEXT_GREEN
                1 -> "§aPacífica" to MilitaryColors.TEXT_GREEN
                2 -> "§ePreparando" to MilitaryColors.TEXT_YELLOW
                3 -> "§c§lEM COMBATE" to MilitaryColors.TEXT_RED
                4 -> "§7Cooldown" to MilitaryColors.TEXT_DARK_GRAY
                else -> "§7?" to MilitaryColors.TEXT_WHITE
            }
            guiGraphics.drawString(font, stateText, listX + 500, itemY + 7, stateColor)

            val nextWindow = region.nextWindow ?: "§7Nenhuma"
            guiGraphics.drawString(font, nextWindow, listX + 620, itemY + 7, MilitaryColors.TEXT_GRAY)
        }

        // Detail panel
        selectedRegion?.let { renderRegionDetail(guiGraphics, it) }
    }

    private fun renderRegionDetail(guiGraphics: GuiGraphics, region: NetworkHandler.RegionMarkerData) {
        val panelX = width - 350
        val panelY = 70
        val panelWidth = 330
        val panelHeight = 300

        guiGraphics.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY + panelHeight + 5, MilitaryColors.PANEL_BG)
        guiGraphics.fill(panelX - 5, panelY - 5, panelX + panelWidth + 5, panelY + 25, MilitaryColors.PANEL_HEADER)

        guiGraphics.drawString(font, "§e§l${region.name}", panelX + 10, panelY + 2, MilitaryColors.TEXT_WHITE)
        guiGraphics.drawString(font, "§7ID: §f${region.id}", panelX + 10, panelY + 20, MilitaryColors.TEXT_GRAY)
        guiGraphics.drawString(font, "§7Dim: §f${region.dimension}", panelX + 10, panelY + 35, MilitaryColors.TEXT_GRAY)
        guiGraphics.drawString(font, "§7Bounds: §f(${region.minX},${region.minY},${region.minZ}) §7-> §f(${region.maxX},${region.maxY},${region.maxZ})", panelX + 10, panelY + 50, MilitaryColors.TEXT_GRAY)

        val area = ((region.maxX - region.minX + 1L) * (region.maxZ - region.minZ + 1L))
        guiGraphics.drawString(font, "§7Área: §f${area} blocos²", panelX + 10, panelY + 65, MilitaryColors.TEXT_GRAY)

        val owner = region.ownerNation?.let { "§f$it" } ?: "§7Nenhuma"
        guiGraphics.drawString(font, "§7Dona: $owner", panelX + 10, panelY + 80, MilitaryColors.TEXT_GRAY)

        // Buttons
        val btnY = panelY + 110
        renderTacticalButton(guiGraphics, "§aTeleportar", panelX + 10, btnY, 150, 24, MilitaryColors.BUTTON_NORMAL, MilitaryColors.BUTTON_HOVER) {
            teleportToRegion(region)
        }
        renderTacticalButton(guiGraphics, "§bVer Detalhes", panelX + 170, btnY, 150, 24, MilitaryColors.BUTTON_BLUE, MilitaryColors.BUTTON_BLUE_HOVER) {
            openRegionDetail(region)
        }
        renderTacticalButton(guiGraphics, "§cIniciar Assalto", panelX + 10, btnY + 30, 150, 24, MilitaryColors.BUTTON_RED, MilitaryColors.BUTTON_RED_HOVER) {
            startAssault(region)
        }
        renderTacticalButton(guiGraphics, "§7Fechar", panelX + 170, btnY + 30, 150, 24, MilitaryColors.BUTTON_DISABLED, MilitaryColors.BUTTON_DISABLED) {
            selectedRegion = null
        }
    }

    private fun renderRankingsTab(guiGraphics: GuiGraphics) {
        guiGraphics.drawString(font, "§e§lRankings - Em desenvolvimento", width / 2 - 100, 100, MilitaryColors.TEXT_GRAY)
    }

    private fun renderHistoryTab(guiGraphics: GuiGraphics) {
        guiGraphics.drawString(font, "§e§lHistórico de Batalhas - Em desenvolvimento", width / 2 - 120, 100, MilitaryColors.TEXT_GRAY)
    }

    private fun renderAdminTab(guiGraphics: GuiGraphics) {
        if (!Minecraft.getInstance().player?.hasPermissions(3) == true) {
            guiGraphics.drawString(font, "§cAcesso negado - Requer OP nível 3+", width / 2 - 100, 100, MilitaryColors.TEXT_RED)
            return
        }
        guiGraphics.drawString(font, "§e§lPainel Administrativo - Em desenvolvimento", width / 2 - 120, 100, MilitaryColors.TEXT_GRAY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        val mx = mouseX.toInt()
        val my = mouseY.toInt()

        // Check region list clicks
        if (selectedTab == 1) {
            val listX = 20
            val listY = 70
            val listWidth = width - 40
            val rowHeight = 40

            for (i in scrollOffset until min(scrollOffset + (height - 100) / rowHeight + 1, regionList.size)) {
                val itemY = listY + (i - scrollOffset) * rowHeight
                if (mx >= listX && mx < listX + listWidth && my >= itemY - 5 && my < itemY + rowHeight - 5) {
                    selectedRegion = if (selectedRegion?.id == regionList[i].id) null else regionList[i]
                    return true
                }
            }
        }

        // Detail panel buttons
        selectedRegion?.let { region ->
            val panelX = width - 350
            val btnY = 180

            if (mx >= panelX + 10 && mx < panelX + 160 && my >= btnY && my < btnY + 24) { teleportToRegion(region); return true }
            if (mx >= panelX + 170 && mx < panelX + 320 && my >= btnY && my < btnY + 24) { openRegionDetail(region); return true }
            if (mx >= panelX + 10 && mx < panelX + 160 && my >= btnY + 30 && my < btnY + 54) { startAssault(region); return true }
            if (mx >= panelX + 170 && mx < panelX + 320 && my >= btnY + 30 && my < btnY + 54) { selectedRegion = null; return true }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean {
        if (selectedTab == 1) {
            val visibleCount = (height - 100) / 40
            val maxScroll = max(0, regionList.size - visibleCount)
            scrollOffset = Mth.clamp(scrollOffset - deltaY.toInt(), 0, maxScroll)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY)
    }

    private fun renderTacticalButton(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, w: Int, h: Int, normalColor: Int, hoverColor: Int, action: () -> Unit) {
        val mx = Minecraft.getInstance().mouseHandler.xpos().toInt() * width / Minecraft.getInstance().window.guiScaledWidth
        val my = Minecraft.getInstance().mouseHandler.ypos().toInt() * height / Minecraft.getInstance().window.guiScaledHeight
        val hovered = mx >= x && mx < x + w && my >= y && my < y + h

        val bgColor = if (hovered) hoverColor else normalColor
        guiGraphics.fill(x, y, x + w, y + h, bgColor)
        guiGraphics.fill(x, y, x + w, y + 1, MilitaryColors.PANEL_BORDER)
        guiGraphics.fill(x, y + h - 1, x + w, y + h, MilitaryColors.PANEL_BORDER)
        guiGraphics.fill(x, y, x + 1, y + h, MilitaryColors.PANEL_BORDER)
        guiGraphics.fill(x + w - 1, y, x + w, y + h, MilitaryColors.PANEL_BORDER)

        val textWidth = font.width(text)
        guiGraphics.drawString(font, text, x + (w - textWidth) / 2, y + (h - 8) / 2, MilitaryColors.TEXT_WHITE)
    }

    private fun teleportToRegion(region: NetworkHandler.RegionMarkerData) {
        val player = minecraft.player ?: return
        val centerX = (region.minX + region.maxX) / 2
        val centerZ = (region.minZ + region.maxZ) / 2
        val centerY = region.maxY + 5
        player.connection.sendCommand("/tpg ${region.dimension} $centerX $centerY $centerZ")
        onClose()
    }

    private fun openRegionDetail(region: NetworkHandler.RegionMarkerData) {
        minecraft.setScreen(RegionDetailScreen(region))
    }

    private fun startAssault(region: NetworkHandler.RegionMarkerData) {
        val player = minecraft.player ?: return
        player.connection.sendCommand("/assault begin ${region.id}")
        onClose()
    }

    override fun isPauseScreen(): Boolean = false
}

// Tela de detalhes da região
class RegionDetailScreen(private val region: NetworkHandler.RegionMarkerData) : Screen(Component.literal("§e${region.name}")) {
    private var tab = 0
    private val tabs = listOf("Geral", "Assalto", "Objetivos", "Recompensas", "Histórico")

    override fun init() {
        super.init()

        val tabY = 35
        val tabWidth = 100
        val startX = width / 2 - (tabWidth * tabs.size) / 2

        for ((i, name) in tabs.withIndex()) {
            addRenderableWidget(TacticalButton(startX + i * tabWidth, tabY, tabWidth, 22, name, { _ ->
                tab = i
            }, if (i == tab) TacticalButton.Style.SUCCESS else TacticalButton.Style.NORMAL))
        }

        addRenderableWidget(TacticalButton(10, height - 30, 100, 20, "§cVoltar", { _ ->
            minecraft.setScreen(NavalWarfareMainScreen())
        }, TacticalButton.Style.RED))

        addRenderableWidget(TacticalButton(width - 110, height - 30, 100, 20, "§aAtualizar", { _ ->
            requestData()
        }, TacticalButton.Style.BLUE))

        requestData()
    }

    private fun requestData() {
        val player = minecraft.player ?: return
        // Request region data
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick)

        // Header
        guiGraphics.drawString(font, "§e§l${region.name}", width / 2 - font.width(region.name) / 2, 8, MilitaryColors.TEXT_YELLOW)
        guiGraphics.drawString(font, "§7ID: §f${region.id}  §7|  §7Dim: §f${region.dimension}", width / 2 - 150, 22, MilitaryColors.TEXT_GRAY)

        when (tab) {
            0 -> renderGeneral(guiGraphics)
            1 -> renderAssault(guiGraphics)
            2 -> renderObjectives(guiGraphics)
            3 -> renderRewards(guiGraphics)
            4 -> renderHistory(guiGraphics)
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick)
    }

    private fun renderGeneral(guiGraphics: GuiGraphics) {
        var y = 70
        val lh = 20

        // Bounds
        drawSectionHeader(guiGraphics, "§e§lLocalização", 30, y); y += lh
        drawInfoLine(guiGraphics, "Mín", "(${region.minX}, ${region.minY}, ${region.minZ})", 40, y); y += lh
        drawInfoLine(guiGraphics, "Máx", "(${region.maxX}, ${region.maxY}, ${region.maxZ})", 40, y); y += lh

        val area = ((region.maxX - region.minX + 1L) * (region.maxZ - region.minZ + 1L))
        val volume = area * (region.maxY - region.minY + 1L)
        drawInfoLine(guiGraphics, "Área", "${area} blocos² | Volume: ${volume} blocos³", 40, y); y += lh * 2

        // Owner
        drawSectionHeader(guiGraphics, "§e§lPropriedade", 30, y); y += lh
        val owner = region.ownerNation?.let { "§f$it" } ?: "§7Nenhuma nação"
        drawInfoLine(guiGraphics, "Dona", owner, 40, y); y += lh

        if (region.ownerNation != null) {
            drawInfoLine(guiGraphics, "", "§aEsta nação recebe recompensas diárias!", 40, y, MilitaryColors.TEXT_GREEN)
        } else {
            drawInfoLine(guiGraphics, "", "§eCapture esta região para ganhar recompensas!", 40, y, MilitaryColors.TEXT_YELLOW)
        }
        y += lh * 2

        // Actions
        drawSectionHeader(guiGraphics, "§e§lAções", 30, y); y += lh
        drawTacticalButton(guiGraphics, "§aTeleportar", 30, y, 180, 22, MilitaryColors.BUTTON_NORMAL, MilitaryColors.BUTTON_HOVER) { teleport() }
        drawTacticalButton(guiGraphics, "§bVer no Mapa", 220, y, 120, 22, MilitaryColors.BUTTON_BLUE, MilitaryColors.BUTTON_BLUE_HOVER) { minecraft.setScreen(NavalWarfareMainScreen()) }
        drawTacticalButton(guiGraphics, "§cIniciar Assalto", 350, y, 150, 22, MilitaryColors.BUTTON_RED, MilitaryColors.BUTTON_RED_HOVER) { startAssault() }
    }

    private fun renderAssault(guiGraphics: GuiGraphics) {
        var y = 70
        drawInfoLine(guiGraphics, "Status", "§7Nenhum assalto ativo no momento", 30, y, MilitaryColors.TEXT_GRAY)
    }

    private fun renderObjectives(guiGraphics: GuiGraphics) {
        var y = 70
        drawInfoLine(guiGraphics, "Objetivos", "§7Carregando...", 30, y, MilitaryColors.TEXT_GRAY)
    }

    private fun renderRewards(guiGraphics: GuiGraphics) {
        var y = 70
        drawSectionHeader(guiGraphics, "§e§lRecompensas Diárias", 30, y); y += 25

        drawInfoLine(guiGraphics, "Dinheiro", "§a$${NavalWarfareConfig.defaultDailyMoneyReward}/dia", 40, y); y += 22

        drawSectionHeader(guiGraphics, "§e§lStatus", 30, y); y += 25
        if (region.ownerNation != null) {
            drawInfoLine(guiGraphics, "", "§a✓ Região capturada - Recompensas ativas!", 40, y, MilitaryColors.TEXT_GREEN)
            drawInfoLine(guiGraphics, "Próximo", "§7Amanhã às 00:00 (horário do servidor)", 40, y)
        } else {
            drawInfoLine(guiGraphics, "", "§c✗ Região livre - Capture para ativar", 40, y, MilitaryColors.TEXT_RED)
        }
    }

    private fun renderHistory(guiGraphics: GuiGraphics) {
        var y = 70
        drawInfoLine(guiGraphics, "Histórico", "§7Carregando...", 30, y, MilitaryColors.TEXT_GRAY)
    }

    private fun drawSectionHeader(guiGraphics: GuiGraphics, text: String, x: Int, y: Int) {
        guiGraphics.fill(x - 5, y - 2, x + font.width(text) + 10, y + 18, MilitaryColors.PANEL_HEADER)
        guiGraphics.drawString(font, text, x, y + 2, MilitaryColors.TEXT_WHITE)
    }

    private fun drawInfoLine(guiGraphics: GuiGraphics, label: String, value: String, x: Int, y: Int, color: Int = MilitaryColors.TEXT_WHITE) {
        if (label.isNotBlank()) {
            guiGraphics.drawString(font, "§7${label}:", x, y, MilitaryColors.TEXT_GRAY)
            guiGraphics.drawString(font, value, x + 80, y, color)
        } else {
            guiGraphics.drawString(font, value, x, y, color)
        }
    }

    private fun drawTacticalButton(guiGraphics: GuiGraphics, text: String, x: Int, y: Int, w: Int, h: Int, n: Int, h: Int, action: () -> Unit) {
        val mx = minecraft.mouseHandler.xpos().toInt() * width / minecraft.window.guiScaledWidth
        val my = minecraft.mouseHandler.ypos().toInt() * height / minecraft.window.guiScaledHeight
        val hovered = mx >= x && mx < x + w && my >= y && my < y + h

        val bgColor = if (hovered) h else n
        guiGraphics.fill(x, y, x + w, y + h, bgColor)
        val tw = font.width(text)
        guiGraphics.drawString(font, text, x + (w - tw) / 2, y + (h - 8) / 2, MilitaryColors.TEXT_WHITE)

        if (hovered && minecraft.mouseHandler.isLeftPressed()) {
            action()
        }
    }

    private fun teleport() {
        val player = minecraft.player ?: return
        val cx = (region.minX + region.maxX) / 2
        val cz = (region.minZ + region.maxZ) / 2
        val cy = region.maxY + 5
        player.connection.sendCommand("/tpg ${region.dimension} $cx $cy $cz")
        onClose()
    }

    private fun startAssault() {
        val player = minecraft.player ?: return
        player.connection.sendCommand("/assault begin ${region.id}")
        onClose()
    }

    override fun isPauseScreen(): Boolean = false
}