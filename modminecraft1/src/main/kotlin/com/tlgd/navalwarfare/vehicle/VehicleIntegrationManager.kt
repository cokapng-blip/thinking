package com.tlgd.navalwarfare.vehicle

import com.mojang.logging.LogUtils
import com.tlgd.navalwarfare.config.NavalWarfareConfig
import com.tlgd.navalwarfare.nation.NationIntegration
import com.tlgd.navalwarfare.region.AssaultRegion
import com.tlgd.navalwarfare.region.AssaultRegionManager
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Gerenciador principal de integração com veículos.
 * Fachada para todos os sistemas de veículos.
 */
class VehicleIntegrationManager {

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private var INSTANCE: VehicleIntegrationManager? = null

        fun getInstance(): VehicleIntegrationManager {
            return INSTANCE ?: throw IllegalStateException("VehicleIntegrationManager nao inicializado!")
        }

        fun init() {
            INSTANCE = VehicleIntegrationManager()
            VehicleRegistry.init()
            VehicleTracker.init()
            LOGGER.info("VehicleIntegrationManager inicializado")
        }
    }

    // Cache de validação de veículos por nação em assalto
    private val nationVehicleCount = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>() // regionId -> nationId -> count

    /**
     * Verifica se uma entidade é um veículo naval válido.
     */
    fun isNavalVehicle(entity: Entity): Boolean {
        return VehicleDetector.detectVehicle(entity) != null
    }

    /**
     * Obtém dados do veículo.
     */
    fun getVehicleData(entity: Entity): VehicleData? {
        return VehicleDetector.detectVehicle(entity)
    }

    /**
     * Obtém a classe do veículo.
     */
    fun getVehicleClass(entity: Entity): VehicleClass? {
        return VehicleDetector.detectVehicle(entity)?.getVehicleClass()
    }

    /**
     * Verifica se veículo é permitido na região.
     */
    fun isVehicleAllowedInRegion(entity: Entity, region: AssaultRegion): Boolean {
        val vehicleData = VehicleDetector.detectVehicle(entity) ?: return false
        val vehicleClass = vehicleData.getVehicleClass() ?: return false
        return vehicleClass.isAllowedInRegion(region.id) &&
               NavalWarfareConfig.allowedVehicleClasses.contains(vehicleClass.id)
    }

    /**
     * Obtém o valor de pontuação do veículo.
     */
    fun getVehicleScoreValue(entity: Entity): Int {
        return VehicleDetector.detectVehicle(entity)?.getScoreValue() ?: 0
    }

    /**
     * Registra veículo para uma nação em uma região.
     */
    fun registerVehicleForNation(entity: Entity, nationId: String, nationName: String, regionId: String) {
        VehicleTracker.getInstance().registerVehicleNation(entity.id, nationId, nationName)

        val regionMap = nationVehicleCount.computeIfAbsent(regionId) { ConcurrentHashMap() }
        val currentCount = regionMap.getOrDefault(nationId, 0)
        regionMap[nationId] = currentCount + 1
    }

    /**
     * Remove veículo da contagem da nação.
     */
    fun unregisterVehicleForNation(entity: Entity, nationId: String, regionId: String) {
        val regionMap = nationVehicleCount[regionId] ?: return
        val currentCount = regionMap.getOrDefault(nationId, 0)
        if (currentCount <= 1) {
            regionMap.remove(nationId)
        } else {
            regionMap[nationId] = currentCount - 1
        }
    }

    /**
     * Verifica limite de veículos por nação.
     */
    fun canNationDeployMoreVehicles(nationId: String, regionId: String): Boolean {
        val regionMap = nationVehicleCount[regionId] ?: return true
        val currentCount = regionMap.getOrDefault(nationId, 0)
        return currentCount < NavalWarfareConfig.maxVehiclesPerNation
    }

    /**
     * Obtém contagem de veículos da nação na região.
     */
    fun getNationVehicleCount(nationId: String, regionId: String): Int {
        return nationVehicleCount[regionId]?.getOrDefault(nationId, 0) ?: 0
    }

    /**
     * Obtém veículos operacionais de uma nação em uma região.
     */
    fun getOperationalVehicles(nationId: String, regionId: String): List<VehicleTracker.TrackedVehicle> {
        return VehicleTracker.getInstance().getTrackedVehiclesByNation(regionId, nationId)
            .filter { it.isOperational }
    }

    /**
     * Escaneia região para atualizar veículos (chamado periodicamente).
     */
    fun scanRegion(region: AssaultRegion, level: ServerLevel): List<VehicleTracker.TrackedVehicle> {
        return VehicleTracker.getInstance().scanRegion(region, level)
    }

    /**
     * Evento: veículo danificado.
     */
    fun onVehicleDamaged(entity: Entity, damage: Float) {
        VehicleTracker.getInstance().onVehicleDamaged(entity, damage)
    }

    /**
     * Evento: veículo destruído.
     */
    fun onVehicleDestroyed(entity: Entity, attacker: ServerPlayer?) {
        val vehicleData = VehicleDetector.detectVehicle(entity)
        vehicleData?.let { data ->
            val nationId = data.nationId
            val regionId = data.nationId?.let { AssaultRegionManager.getInstance().getRegionAt(entity.blockPosition(), entity.level.dimension())?.id }

            VehicleTracker.getInstance().onVehicleDestroyed(entity)
            nationId?.let { regionId?.let { unregisterVehicleForNation(entity, it, it) } }
        }
    }

    /**
     * Evento: veículo movido.
     */
    fun onVehicleMoved(entity: Entity, newPos: net.minecraft.core.BlockPos) {
        VehicleTracker.getInstance().onVehicleMoved(entity, newPos)
    }

    /**
     * Verifica veículos abandonados.
     */
    fun checkAbandonedVehicles(): List<VehicleTracker.TrackedVehicle> {
        return VehicleTracker.getInstance().checkAbandonedVehicles(NavalWarfareConfig.abandonedVehicleTimeoutSeconds)
    }

    /**
     * Obtém todas as classes de veículos registradas.
     */
    fun getAllVehicleClasses(): List<VehicleClass> = VehicleRegistry.getInstance().getAllClasses()

    /**
     * Obtém classes permitidas para uma região.
     */
    fun getAllowedClassesForRegion(regionId: String): List<VehicleClass> = VehicleRegistry.getInstance().getClassesForRegion(regionId)

    /**
     * Registra classe customizada (via comando admin).
     */
    fun registerCustomClass(vehicleClass: VehicleClass) {
        VehicleRegistry.getInstance().registerClass(vehicleClass)
    }

    /**
     * Remove classe customizada.
     */
    fun unregisterClass(id: String): Boolean = VehicleRegistry.getInstance().unregisterClass(id)

    /**
     * Limpeza periódica.
     */
    fun cleanup() {
        VehicleTracker.getInstance().cleanup()
        VehicleDetector.cleanupCache()

        // Limpar contagens antigas
        val now = System.currentTimeMillis()
        nationVehicleCount.values.forEach { nationMap ->
            nationMap.entries.removeIf { (_, count) -> count <= 0 }
        }
    }

    /**
     * Tenta obter nação do veículo via integração de nações.
     */
    fun getVehicleNation(entity: Entity): NationIntegration.NationData? {
        // Tentar via dono do veículo (Movecraft, etc)
        val ownerName = VehicleDetector.detectVehicle(entity)?.ownerName
        ownerName?.let { name ->
            // Buscar jogador pelo nome
            // Isso seria implementado conforme o sistema de nações
        }
        return null
    }

    /**
     * Atualiza nação do veículo baseada no piloto/dono.
     */
    fun updateVehicleNationFromPilot(entity: Entity, player: ServerPlayer) {
        val nation = NationIntegration.getPlayerNation(player)
        nation?.let { nationData ->
            registerVehicleForNation(entity, nationData.id, nationData.name, "")
        }
    }
}