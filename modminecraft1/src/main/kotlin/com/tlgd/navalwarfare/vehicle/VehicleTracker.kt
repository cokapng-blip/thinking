package com.tlgd.navalwarfare.vehicle

import com.mojang.logging.LogUtils
import com.tlgd.navalwarfare.region.AssaultRegion
import com.tlgd.navalwarfare.region.AssaultRegionManager
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Rastreador de veículos em regiões de assalto.
 * Mantém estado operacional, tripulação, nação dona, etc.
 */
class VehicleTracker {

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private var INSTANCE: VehicleTracker? = null

        fun getInstance(): VehicleTracker {
            return INSTANCE ?: throw IllegalStateException("VehicleTracker nao inicializado!")
        }

        fun init() {
            INSTANCE = VehicleTracker()
        }
    }

    // regionId -> nationId -> List<VehicleData>
    private val vehiclesInRegion = ConcurrentHashMap<String, ConcurrentHashMap<String, CopyOnWriteArrayList<VehicleData>>>()
    // vehicleEntityId -> TrackedVehicle
    private val trackedVehicles = ConcurrentHashMap<Int, TrackedVehicle>()
    // Última verificação por região
    private val lastRegionScan = ConcurrentHashMap<String, Long>()
    private val scanIntervalMs = 5000L // 5 segundos

    data class TrackedVehicle(
        val vehicleData: VehicleData,
        var lastSeen: Long = System.currentTimeMillis(),
        var lastPosition: BlockPos = vehicleData.position,
        var isOperational: Boolean = true,
        var damageTaken: Float = 0f,
        var hasMoved: Boolean = false,
        var consecutiveStationaryTicks: Int = 0,
    ) {
        fun updatePosition(newPos: BlockPos) {
            hasMoved = newPos != lastPosition
            lastPosition = newPos
            lastSeen = System.currentTimeMillis()
            if (!hasMoved) consecutiveStationaryTicks++ else consecutiveStationaryTicks = 0
        }

        fun markDamaged(amount: Float) {
            damageTaken += amount
            lastSeen = System.currentTimeMillis()
        }

        fun markDestroyed() {
            isOperational = false
        }

        fun getIdleTimeSeconds(): Long = (System.currentTimeMillis() - lastSeen) / 1000
    }

    /**
     * Escaneia veículos em uma região e atualiza tracking.
     */
    fun scanRegion(region: AssaultRegion, level: ServerLevel): List<TrackedVehicle> {
        val regionId = region.id
        val now = System.currentTimeMillis()

        // Rate limiting
        val lastScan = lastRegionScan[regionId] ?: 0
        if (now - lastScan < scanIntervalMs) {
            return getTrackedVehiclesInRegion(regionId)
        }
        lastRegionScan[regionId] = now

        val bounds = region.bounds
        val aabb = net.minecraft.world.phys.AABB(
            bounds.min.x.toDouble(), bounds.min.y.toDouble(), bounds.min.z.toDouble(),
            bounds.max.x.toDouble() + 1, bounds.max.y.toDouble() + 1, bounds.max.z.toDouble() + 1
        )

        val detectedVehicles = VehicleDetector.detectVehiclesInRegion(level, aabb)
        val trackedList = mutableListOf<TrackedVehicle>()

        for (vehicleData in detectedVehicles) {
            val tracked = trackedVehicles.computeIfAbsent(vehicleData.entityId) { id ->
                TrackedVehicle(vehicleData)
            }

            // Atualizar dados
            tracked.vehicleData = vehicleData.copy(
                nationId = vehicleData.nationId,
                nationName = vehicleData.nationName
            )
            tracked.updatePosition(vehicleData.position)
            tracked.isOperational = vehicleData.isOperational

            trackedList.add(tracked)
        }

        // Remover veículos que não foram vistos há mais de 30 segundos
        val regionVehicles = vehiclesInRegion[regionId] ?: ConcurrentHashMap()
        regionVehicles.values.flatten().forEach { list ->
            list.removeIf { tracked ->
                tracked.getIdleTimeSeconds() > 30
            }
        }

        // Atualizar mapa região -> nação -> veículos
        updateRegionVehicleMap(regionId, trackedList)

        return trackedList
    }

    private fun updateRegionVehicleMap(regionId: String, trackedList: List<TrackedVehicle>) {
        val nationMap = vehiclesInRegion.computeIfAbsent(regionId) { ConcurrentHashMap() }

        // Limpar mapa atual
        nationMap.values.forEach { list -> list.clear() }

        // Repopular
        for (tracked in trackedList) {
            if (!tracked.isOperational) continue

            val nationId = tracked.vehicleData.nationId ?: "independent_${tracked.vehicleData.entityId}"
            val nationList = nationMap.computeIfAbsent(nationId) { CopyOnWriteArrayList() }
            nationList.add(tracked)
        }
    }

    fun getTrackedVehiclesInRegion(regionId: String): List<TrackedVehicle> {
        return vehiclesInRegion[regionId]?.values?.flatten()?.toList() ?: emptyList()
    }

    fun getTrackedVehiclesByNation(regionId: String, nationId: String): List<TrackedVehicle> {
        return vehiclesInRegion[regionId]?.get(nationId)?.toList() ?: emptyList()
    }

    fun getNationVehicleCount(regionId: String, nationId: String): Int {
        return vehiclesInRegion[regionId]?.get(nationId)?.size ?: 0
    }

    fun getTotalVehicleCount(regionId: String): Int {
        return vehiclesInRegion[regionId]?.values?.sumOf { it.size } ?: 0
    }

    fun getVehicleClassCounts(regionId: String, nationId: String): Map<String, Int> {
        val vehicles = getTrackedVehiclesByNation(regionId, nationId)
        return vehicles.groupingBy { it.vehicleData.vehicleClassId }.eachCount()
    }

    fun getVehicleScoreValue(regionId: String, nationId: String): Int {
        return getTrackedVehiclesByNation(regionId, nationId).sumOf { it.vehicleData.getScoreValue() }
    }

    fun onVehicleDamaged(entity: Entity, damage: Float) {
        trackedVehicles[entity.id]?.markDamaged(damage)
    }

    fun onVehicleDestroyed(entity: Entity) {
        trackedVehicles[entity.id]?.markDestroyed()
        // Remover de regiões
        vehiclesInRegion.values.forEach { nationMap ->
            nationMap.values.forEach { list ->
                list.removeIf { it.vehicleData.entityId == entity.id }
            }
        }
    }

    fun onVehicleMoved(entity: Entity, newPos: BlockPos) {
        trackedVehicles[entity.id]?.updatePosition(newPos)
    }

    /**
     * Verifica se veículo está abandonado (sem tripulação por tempo).
     */
    fun checkAbandonedVehicles(maxIdleSeconds: Long = 120): List<TrackedVehicle> {
        val abandoned = mutableListOf<TrackedVehicle>()
        val now = System.currentTimeMillis()

        for (tracked in trackedVehicles.values) {
            if (tracked.isOperational && tracked.getIdleTimeSeconds() > maxIdleSeconds) {
                // Verificar se tem tripulação
                if (tracked.vehicleData.crewCount == 0) {
                    tracked.isOperational = false
                    abandoned.add(tracked)
                    LOGGER.info("Veiculo abandonado detectado: {} (id={})", tracked.vehicleData.getDisplayName(), tracked.vehicleData.entityId)
                }
            }
        }
        return abandoned
    }

    fun registerVehicleNation(vehicleEntityId: Int, nationId: String, nationName: String) {
        trackedVehicles[vehicleEntityId]?.let { tracked ->
            tracked.vehicleData = tracked.vehicleData.copy(
                nationId = nationId,
                nationName = nationName
            )
        }
    }

    fun getTrackedVehicle(entityId: Int): TrackedVehicle? = trackedVehicles[entityId]

    fun getAllTrackedVehicles(): List<TrackedVehicle> = trackedVehicles.values.toList()

    fun cleanup() {
        val now = System.currentTimeMillis()
        trackedVehicles.entries.removeIf { (_, tracked) ->
            !tracked.isOperational || tracked.getIdleTimeSeconds() > 300 // 5 min
        }
        VehicleDetector.cleanupCache()
    }
}