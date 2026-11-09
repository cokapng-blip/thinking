package com.tlgd.navalwarfare.map

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mojang.logging.LogUtils
import com.tlgd.navalwarfare.region.AssaultRegion
import com.tlgd.navalwarfare.region.AssaultRegionManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Integração com sistemas de mapa dinâmico (Dynmap, BlueMap, Squaremap, Pl3xMap).
 * Arquitetura plugin-agnostic com fallback para exportação de arquivos.
 */
interface MapProvider {
    val name: String
    val isAvailable: Boolean

    fun initialize()
    fun shutdown()

    // Regiões
    fun addRegionMarker(region: AssaultRegion)
    fun updateRegionMarker(region: AssaultRegion)
    fun removeRegionMarker(regionId: String)

    // Assalto ativo
    fun highlightRegionUnderAssault(region: AssaultRegion)
    fun unhighlightRegion(regionId: String)

    // Nações
    fun setRegionOwner(regionId: String, nationId: String, nationName: String, color: Int)

    // Marcadores personalizados
    fun addMarker(marker: MapMarker)
    fun removeMarker(markerId: String)
}

data class MapMarker(
    val id: String,
    val label: String,
    val position: MarkerPosition,
    val icon: String? = null,
    val color: Int = 0xFFFFFFFF,
    val layer: String = "navalwarfare",
    val minZoom: Int = 0,
    val maxZoom: Int = 24,
    val tooltip: String? = null,
)

data class MarkerPosition(
    val x: Double,
    val y: Double,
    val z: Double,
    val dimension: String,
)

/**
 * Provedor genérico que exporta arquivos JSON/GeoJSON para consumo externo.
 * Funciona sem nenhum plugin de mapa instalado.
 */
class GenericMapProvider : MapProvider {

    companion object {
        private val LOGGER = com.mojang.logging.LogUtils.getLogger()
        private val gson = GsonBuilder().setPrettyPrinting().create()
    }

    override val name = "GenericFileExport"
    override val isAvailable = true

    private var outputDir: File? = null
    private val markers = ConcurrentHashMap<String, MapMarker>()

    override fun initialize() {
        val server = net.minecraft.server.MinecraftServer.getServer()
        val worldDir = server?.getWorldPath(net.minecraft.server.packs.resources.ServerResources.WORLD_DIR)?.toFile()
        outputDir = worldDir?.let { File(it, "navalwarfare_map") }?.apply { mkdirs() }
        LOGGER.info("GenericMapProvider inicializado - exportando para: {}", outputDir?.absolutePath)
    }

    override fun shutdown() {
        exportAll()
    }

    override fun addRegionMarker(region: AssaultRegion) {
        val bounds = region.bounds
        val centerX = (bounds.min.x + bounds.max.x) / 2.0
        val centerZ = (bounds.min.z + bounds.max.z) / 2.0
        val centerY = (bounds.min.y + bounds.max.y) / 2.0

        val marker = MapMarker(
            id = "region_${region.id}",
            label = region.displayName,
            position = MarkerPosition(centerX, centerY, centerZ, bounds.dimension.toString()),
            icon = "navalwarfare:region",
            color = region.ownerNationId?.let { getNationColor(it) } ?: 0xFF888888,
            tooltip = "Regiao: ${region.displayName}\nDono: ${region.ownerNationName ?: "Nenhum"}\nEstado: ${region.getStateForDisplay()}"
        )
        markers[marker.id] = marker
        exportAll()
    }

    override fun updateRegionMarker(region: AssaultRegion) {
        addRegionMarker(region)
    }

    override fun removeRegionMarker(regionId: String) {
        markers.remove("region_$regionId")
        exportAll()
    }

    override fun highlightRegionUnderAssault(region: AssaultRegion) {
        val marker = MapMarker(
            id = "assault_${region.id}",
            label = "⚔ ASSALTO: ${region.displayName}",
            position = MarkerPosition(
                (region.bounds.min.x + region.bounds.max.x) / 2.0,
                (region.bounds.min.y + region.bounds.max.y) / 2.0,
                (region.bounds.min.z + region.bounds.max.z) / 2.0,
                region.bounds.dimension.toString()
            ),
            icon = "navalwarfare:assault",
            color = 0xFFFF0000,
            tooltip = "ASSALTO ATIVO em ${region.displayName}"
        )
        markers[marker.id] = marker
        exportAll()
    }

    override fun unhighlightRegion(regionId: String) {
        markers.remove("assault_$regionId")
        exportAll()
    }

    override fun setRegionOwner(regionId: String, nationId: String, nationName: String, color: Int) {
        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(regionId)
        region?.let { updateRegionMarker(it) }
    }

    override fun addMarker(marker: MapMarker) {
        markers[marker.id] = marker
        exportAll()
    }

    override fun removeMarker(markerId: String) {
        markers.remove(markerId)
        exportAll()
    }

    private fun exportAll() {
        outputDir?.let { dir ->
            try {
                // Exportar regiões como GeoJSON
                val regionsFile = File(dir, "regions.geojson")
                exportRegionsGeoJSON(regionsFile)

                // Exportar marcadores como JSON
                val markersFile = File(dir, "markers.json")
                markersFile.writeText(gson.toJson(markers.values))

                // Exportar assaltos ativos
                val assaultsFile = File(dir, "active_assaults.json")
                exportActiveAssaults(assaultsFile)

            } catch (e: Exception) {
                LOGGER.error("Erro ao exportar dados do mapa: {}", e.message)
            }
        }
    }

    private fun exportRegionsGeoJSON(file: File) {
        val manager = AssaultRegionManager.getInstance()
        val features = mutableListOf<Map<String, Any>>()

        for (region in manager.getAllRegions()) {
            val bounds = region.bounds
            val coordinates = listOf(listOf(
                listOf(bounds.min.x.toDouble(), bounds.min.z.toDouble()),
                listOf(bounds.max.x.toDouble(), bounds.min.z.toDouble()),
                listOf(bounds.max.x.toDouble(), bounds.max.z.toDouble()),
                listOf(bounds.min.x.toDouble(), bounds.max.z.toDouble()),
                listOf(bounds.min.x.toDouble(), bounds.min.z.toDouble())
            ))

            val properties = mapOf(
                "id" to region.id,
                "name" to region.displayName,
                "owner" to region.ownerNationName ?: "none",
                "owner_id" to region.ownerNationId ?: "none",
                "state" to region.state.name,
                "color" to region.ownerNationId?.let { getNationColor(it) } ?: 0xFF888888,
                "next_window" to region.getNextWindow()?.toString(),
                "dimension" to bounds.dimension.toString()
            )

            features.add(mapOf(
                "type" to "Feature",
                "geometry" to mapOf("type" to "Polygon", "coordinates" to coordinates),
                "properties" to properties
            ))
        }

        val geojson = mapOf(
            "type" to "FeatureCollection",
            "features" to features
        )
        file.writeText(gson.toJson(geojson))
    }

    private fun exportActiveAssaults(file: File) {
        val manager = AssaultRegionManager.getInstance()
        val assaults = mutableListOf<Map<String, Any>>()

        for (region in manager.getAllRegions()) {
            if (region.state == com.tlgd.navalwarfare.region.AssaultState.ACTIVE) {
                val assault = manager.getActiveAssault(region.id)
                assault?.let {
                    assaults.add(mapOf(
                        "regionId" to region.id,
                        "regionName" to region.displayName,
                        "initiatorNation" to it.initiatorNationName,
                        "initiatorNationId" to it.initiatorNationId,
                        "startedAt" to it.startedAt,
                        "endsAt" to it.endsAt,
                        "remainingSeconds" to (it.getRemainingTime() / 1000),
                        "leaderboard" to region.getLeaderboard().take(5).map { score ->
                            mapOf("nation" to score.nationName, "score" to score.score)
                        }
                    ))
                }
            }
        }
        file.writeText(gson.toJson(assaults))
    }

    private fun getNationColor(nationId: String): Int {
        // Hash simples do ID para cor consistente
        val hash = nationId.hashCode()
        val r = (hash & 0xFF).toInt()
        val g = ((hash >> 8) & 0xFF).toInt()
        val b = ((hash >> 16) & 0xFF).toInt()
        // Garantir cor visível (não muito escura)
        return 0xFF000000 | ((r + 128) % 256) shl 16 | ((g + 128) % 256) shl 8 | ((b + 128) % 256)
    }
}

/**
 * Provedor para Dynmap (requer Dynmap instalado no servidor).
 */
class DynmapProvider : MapProvider {

    companion object {
        private val LOGGER = com.mojang.logging.LogUtils.getLogger()
    }

    override val name = "Dynmap"
    override val isAvailable: Boolean
        get() = try {
            Class.forName("org.dynmap.DynmapAPI")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

    private var api: Any? = null

    override fun initialize() {
        if (!isAvailable) return
        try {
            val apiClass = Class.forName("org.dynmap.DynmapAPI")
            val method = apiClass.getMethod("getAPI")
            api = method.invoke(null)
            LOGGER.info("DynmapProvider inicializado com sucesso")
        } catch (e: Exception) {
            LOGGER.error("Erro ao inicializar DynmapProvider: {}", e.message)
        }
    }

    override fun shutdown() {
        api = null
    }

    override fun addRegionMarker(region: AssaultRegion) {
        // Implementar usando API do Dynmap
        // api?.let { ... }
    }

    override fun updateRegionMarker(region: AssaultRegion) {
        addRegionMarker(region)
    }

    override fun removeRegionMarker(regionId: String) {}

    override fun highlightRegionUnderAssault(region: AssaultRegion) {}

    override fun unhighlightRegion(regionId: String) {}

    override fun setRegionOwner(regionId: String, nationId: String, nationName: String, color: Int) {}

    override fun addMarker(marker: MapMarker) {}

    override fun removeMarker(markerId: String) {}
}

/**
 * Provedor para BlueMap.
 */
class BlueMapProvider : MapProvider {

    companion object {
        private val LOGGER = com.mojang.logging.LogUtils.getLogger()
    }

    override val name = "BlueMap"
    override val isAvailable: Boolean
        get() = try {
            Class.forName("de.bluecolored.bluemap.api.BlueMapAPI")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

    override fun initialize() {
        if (!isAvailable) return
        LOGGER.info("BlueMapProvider inicializado")
    }

    override fun shutdown() {}

    override fun addRegionMarker(region: AssaultRegion) {}
    override fun updateRegionMarker(region: AssaultRegion) {}
    override fun removeRegionMarker(regionId: String) {}
    override fun highlightRegionUnderAssault(region: AssaultRegion) {}
    override fun unhighlightRegion(regionId: String) {}
    override fun setRegionOwner(regionId: String, nationId: String, nationName: String, color: Int) {}
    override fun addMarker(marker: MapMarker) {}
    override fun removeMarker(markerId: String) {}
}

/**
 * Provedor para Squaremap.
 */
class SquaremapProvider : MapProvider {

    companion object {
        private val LOGGER = com.mojang.logging.LogUtils.getLogger()
    }

    override val name = "Squaremap"
    override val isAvailable: Boolean
        get() = try {
            Class.forName("io.github.mrcsxsi.squaremap.api.SquaremapAPI")
            true
        } catch (e: ClassNotFoundException) {
            false
        }

    override fun initialize() {
        if (!isAvailable) return
        LOGGER.info("SquaremapProvider inicializado")
    }

    override fun shutdown() {}

    override fun addRegionMarker(region: AssaultRegion) {}
    override fun updateRegionMarker(region: AssaultRegion) {}
    override fun removeRegionMarker(regionId: String) {}
    override fun highlightRegionUnderAssault(region: AssaultRegion) {}
    override fun unhighlightRegion(regionId: String) {}
    override fun setRegionOwner(regionId: String, nationId: String, nationName: String, color: Int) {}
    override fun addMarker(marker: MapMarker) {}
    override fun removeMarker(markerId: String) {}
}

/**
 * Gerenciador principal de integração com mapas.
 */
class MapIntegrationManager {

    companion object {
        private val LOGGER = com.mojang.logging.LogUtils.getLogger()
        private var INSTANCE: MapIntegrationManager? = null

        fun getInstance(): MapIntegrationManager {
            return INSTANCE ?: throw IllegalStateException("MapIntegrationManager nao inicializado!")
        }

        fun init() {
            INSTANCE = MapIntegrationManager()
            INSTANCE?.initialize()
        }

        fun shutdown() {
            INSTANCE?.shutdown()
        }
    }

    private val providers = mutableListOf<MapProvider>()
    private var activeProvider: MapProvider? = null

    private fun initialize() {
        // Tentar provedores em ordem de preferência
        val providerClasses = listOf(
            ::DynmapProvider,
            ::BlueMapProvider,
            ::SquaremapProvider,
            ::GenericMapProvider
        )

        for (providerClass in providerClasses) {
            try {
                val provider = providerClass.call()
                if (provider.isAvailable) {
                    provider.initialize()
                    providers.add(provider)
                    if (activeProvider == null) {
                        activeProvider = provider
                        LOGGER.info("Provedor de mapa ativo: ${provider.name}")
                    }
                }
            } catch (e: Exception) {
                LOGGER.debug("Provedor ${providerClass.simpleName} nao disponivel: {}", e.message)
            }
        }

        // Sempre adicionar Generic como fallback
        if (providers.none { it is GenericMapProvider }) {
            val generic = GenericMapProvider()
            generic.initialize()
            providers.add(generic)
            if (activeProvider == null) activeProvider = generic
        }

        // Sincronizar regiões existentes
        syncAllRegions()
    }

    private fun shutdown() {
        for (provider in providers) {
            provider.shutdown()
        }
        providers.clear()
        activeProvider = null
    }

    fun syncAllRegions() {
        val manager = AssaultRegionManager.getInstance()
        for (region in manager.getAllRegions()) {
            for (provider in providers) {
                provider.addRegionMarker(region)
            }
        }
    }

    fun onRegionCreated(region: AssaultRegion) {
        for (provider in providers) {
            provider.addRegionMarker(region)
        }
    }

    fun onRegionUpdated(region: AssaultRegion) {
        for (provider in providers) {
            provider.updateRegionMarker(region)
        }
    }

    fun onRegionRemoved(regionId: String) {
        for (provider in providers) {
            provider.removeRegionMarker(regionId)
        }
    }

    fun onAssaultStarted(region: AssaultRegion) {
        for (provider in providers) {
            provider.highlightRegionUnderAssault(region)
        }
    }

    fun onAssaultEnded(regionId: String) {
        for (provider in providers) {
            provider.unhighlightRegion(regionId)
        }
    }

    fun onRegionOwnerChanged(regionId: String, nationId: String, nationName: String, color: Int) {
        for (provider in providers) {
            provider.setRegionOwner(regionId, nationId, nationName, color)
        }
    }

    fun addCustomMarker(marker: MapMarker) {
        for (provider in providers) {
            provider.addMarker(marker)
        }
    }

    fun removeCustomMarker(markerId: String) {
        for (provider in providers) {
            provider.removeMarker(markerId)
        }
    }

    fun getActiveProviderName(): String = activeProvider?.name ?: "none"
    fun getAllProviders(): List<String> = providers.map { it.name }
}