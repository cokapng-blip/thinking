package com.tlgd.navalwarfare.vehicle

import com.mojang.logging.LogUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.vehicle.Boat
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import org.slf4j.Logger
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Detector de veículos usando reflection para evitar dependências hard.
 * Suporta: Create, Create:Aeronautics, Movecraft, Archipelago, Eureka, etc.
 */
object VehicleDetector {

    private val LOGGER: Logger = LogUtils.getLogger()

    // Cache de classes detectadas via reflection
    private val createContrailClass: Class<*>? = tryLoadClass("com.simibubi.create.content.contraptions.Contraption")
    private val createEntityAttachmentClass: Class<*>? = tryLoadClass("com.simibubi.create.content.contraptions.entity.EntityAttachment")
    private val aeronauticsAircraftClass: Class<*>? = tryLoadClass("com.simibubi.createaeronautics.content.aircraft.AircraftEntity")
    private val aeronauticsAirshipClass: Class<*>? = tryLoadClass("com.simibubi.createaeronautics.content.airship.AirshipEntity")
    private val movecraftCraftClass: Class<*>? = tryLoadClass("com.movecraft.api.craft.Craft")
    private val movecraftCraftTypeClass: Class<*>? = tryLoadClass("com.movecraft.api.craft.CraftType")
    private val archipelagoShipClass: Class<*>? = tryLoadClass("com.archipelago.ship.Ship")
    private val eurekaVehicleClass: Class<*>? = tryLoadClass("com.eureka.vehicle.Vehicle")

    // Cache de métodos reflection
    private val contraptionGetBlocksMethod: Method? = createContrailClass?.getMethod("getBlocks")
    private val contraptionGetSizeMethod: Method? = createContrailClass?.getMethod("getSize")
    private val entityAttachmentGetContraptionMethod: Method? = createEntityAttachmentClass?.getMethod("getContraption")
    private val aircraftGetTypeMethod: Method? = aeronauticsAircraftClass?.getMethod("getAircraftType")
    private val airshipGetTypeMethod: Method? = aeronauticsAirshipClass?.getMethod("getAirshipType")
    private val craftGetTypeMethod: Method? = movecraftCraftClass?.getMethod("getType")
    private val craftGetBlocksMethod: Method? = movecraftCraftClass?.getMethod("getBlocks")
    private val craftGetPilotsMethod: Method? = movecraftCraftClass?.getMethod("getPilots")
    private val craftGetNameMethod: Method? = movecraftCraftClass?.getMethod("getName")
    private val craftGetOwnerMethod: Method? = movecraftCraftClass?.getMethod("getOwner")
    private val craftGetRotationMethod: Method? = movecraftCraftClass?.getMethod("getRotation")

    // Cache de veículos detectados recentemente (entityId -> VehicleData)
    private val detectedVehicles = ConcurrentHashMap<Int, VehicleData>()
    private val vehicleEntityMap = ConcurrentHashMap<Integer, Entity>() // entityId -> entity

    private fun tryLoadClass(className: String): Class<*>? {
        return try {
            Class.forName(className)
        } catch (e: ClassNotFoundException) {
            null
        } catch (e: Exception) {
            LOGGER.debug("Erro ao carregar classe {}: {}", className, e.message)
            null
        }
    }

    /**
     * Tenta detectar veículo a partir de uma entidade.
     * Retorna VehicleData se for veículo reconhecido, null caso contrário.
     */
    fun detectVehicle(entity: Entity): VehicleData? {
        val entityId = entity.id

        // Verificar cache
        val cached = detectedVehicles[entityId]
        if (cached != null && cached.entity == entity && cached.isValid()) {
            return cached
        }

        val vehicleData = when {
            // Create: Contraption via EntityAttachment
            createEntityAttachmentClass != null && createEntityAttachmentClass.isInstance(entity) -> {
                detectCreateContraption(entity)
            }
            // Create: Aeronautics Aircraft
            aeronauticsAircraftClass != null && aeronauticsAircraftClass.isInstance(entity) -> {
                detectAeronauticsAircraft(entity)
            }
            // Create: Aeronautics Airship
            aeronauticsAirshipClass != null && aeronauticsAirshipClass.isInstance(entity) -> {
                detectAeronauticsAirship(entity)
            }
            // Movecraft
            movecraftCraftClass != null && movecraftCraftClass.isInstance(entity) -> {
                detectMovecraft(entity)
            }
            // Archipelago
            archipelagoShipClass != null && archipelagoShipClass.isInstance(entity) -> {
                detectArchipelago(entity)
            }
            // Eureka
            eurekaVehicleClass != null && eurekaVehicleClass.isInstance(entity) -> {
                detectEureka(entity)
            }
            // Barco vanilla (fallback)
            entity is Boat -> {
                detectVanillaBoat(entity)
            }
            else -> null
        }

        vehicleData?.let {
            detectedVehicles[entityId] = it
            vehicleEntityMap[entityId] = entity
        }

        return vehicleData
    }

    private fun detectCreateContraption(entity: Entity): VehicleData? {
        try {
            val contraption = entityAttachmentGetContraptionMethod?.invoke(entity)
            contraption?.let {
                val blocks = contraptionGetBlocksMethod?.invoke(it) as? java.util.Collection<*> ?: emptyList()
                val size = contraptionGetSizeMethod?.invoke(it) as? Int ?: blocks.size
                val blockStates = blocks.mapNotNull { b ->
                    try {
                        val method = b.javaClass.getMethod("getBlockState")
                        method.invoke(b) as? BlockState
                    } catch (e: Exception) { null }
                }.filterNotNull()

                return analyzeBlocks(entity, blockStates, "create_contraption")
            }
        } catch (e: Exception) {
            LOGGER.debug("Erro ao detectar Create Contraption: {}", e.message)
        }
        return null
    }

    private fun detectAeronauticsAircraft(entity: Entity): VehicleData? {
        try {
            val aircraftType = aircraftGetTypeMethod?.invoke(entity)?.toString() ?: "unknown"
            // AircraftEntity tem lista de blocos internamente
            val blocks = getBlocksFromAeronauticsEntity(entity)
            return analyzeBlocks(entity, blocks, "aeronautics_aircraft", aircraftType)
        } catch (e: Exception) {
            LOGGER.debug("Erro ao detectar Aeronautics Aircraft: {}", e.message)
        }
        return null
    }

    private fun detectAeronauticsAirship(entity: Entity): VehicleData? {
        try {
            val airshipType = airshipGetTypeMethod?.invoke(entity)?.toString() ?: "unknown"
            val blocks = getBlocksFromAeronauticsEntity(entity)
            return analyzeBlocks(entity, blocks, "aeronautics_airship", airshipType)
        } catch (e: Exception) {
            LOGGER.debug("Erro ao detectar Aeronautics Airship: {}", e.message)
        }
        return null
    }

    private fun getBlocksFromAeronauticsEntity(entity: Entity): List<BlockState> {
        // Tentar acessar campo/ método interno para pegar blocos
        try {
            val field = entity.javaClass.getDeclaredField("blocks")
            field.isAccessible = true
            val blocks = field.get(entity) as? java.util.Collection<*> ?: emptyList()
            return blocks.mapNotNull { b ->
                try {
                    val method = b.javaClass.getMethod("getBlockState")
                    method.invoke(b) as? BlockState
                } catch (e: Exception) { null }
            }.filterNotNull()
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun detectMovecraft(entity: Entity): VehicleData? {
        try {
            val craftType = craftGetTypeMethod?.invoke(entity)?.toString() ?: "unknown"
            val name = craftGetNameMethod?.invoke(entity)?.toString() ?: "Unknown"
            val blocks = craftGetBlocksMethod?.invoke(entity) as? java.util.Collection<*> ?: emptyList()
            val pilots = craftGetPilotsMethod?.invoke(entity) as? java.util.Collection<*> ?: emptyList()
            val owner = craftGetOwnerMethod?.invoke(entity)?.toString()

            val blockStates = blocks.mapNotNull { b ->
                try {
                    val method = b.javaClass.getMethod("getBlockState")
                    method.invoke(b) as? BlockState
                } catch (e: Exception) { null }
            }.filterNotNull()

            val vehicleData = analyzeBlocks(entity, blockStates, "movecraft_$craftType")
            return vehicleData?.copy(
                customName = name,
                ownerName = owner,
                crewCount = pilots.size,
                modSource = "Movecraft",
                modType = craftType
            )
        } catch (e: Exception) {
            LOGGER.debug("Erro ao detectar Movecraft: {}", e.message)
        }
        return null
    }

    private fun detectArchipelago(entity: Entity): VehicleData? {
        try {
            // Archipelago tem API similar
            val blocks = tryGetBlocksFromEntity(entity, "getBlocks", "getBlockStates")
            return analyzeBlocks(entity, blocks, "archipelago")?.copy(modSource = "Archipelago")
        } catch (e: Exception) {
            LOGGER.debug("Erro ao detectar Archipelago: {}", e.message)
        }
        return null
    }

    private fun detectEureka(entity: Entity): VehicleData? {
        try {
            val blocks = tryGetBlocksFromEntity(entity, "getBlocks", "getStructureBlocks")
            return analyzeBlocks(entity, blocks, "eureka")?.copy(modSource = "Eureka")
        } catch (e: Exception) {
            LOGGER.debug("Erro ao detectar Eureka: {}", e.message)
        }
        return null
    }

    private fun tryGetBlocksFromEntity(entity: Entity, vararg methodNames: String): List<BlockState> {
        for (methodName in methodNames) {
            try {
                val method = entity.javaClass.getMethod(methodName)
                val blocks = method.invoke(entity) as? java.util.Collection<*> ?: emptyList()
                return blocks.mapNotNull { b ->
                    try {
                        val stateMethod = b.javaClass.getMethod("getBlockState")
                        stateMethod.invoke(b) as? BlockState
                    } catch (e: Exception) { null }
                }.filterNotNull()
            } catch (e: Exception) {
                // tentar próximo método
            }
        }
        return emptyList()
    }

    private fun detectVanillaBoat(entity: Boat): VehicleData {
        return VehicleData(
            entityId = entity.id,
            entity = entity,
            position = entity.blockPosition(),
            dimensions = AABB(entity.getBoundingBox()),
            blockCount = 1,
            blockTypes = setOf("minecraft:${entity.type.name.toLowerCase()}"),
            length = 3,
            width = 2,
            height = 1,
            vehicleClassId = "patrol_boat",
            modSource = "Vanilla",
            modType = entity.type.name,
            customName = entity.getCustomName()?.getString() ?: "Boat",
            crewCount = entity.getPassengers().size,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Analisa blocos para determinar classe do veículo.
     */
    private fun analyzeBlocks(
        entity: Entity,
        blockStates: List<BlockState>,
        modSource: String,
        modType: String = ""
    ): VehicleData? {
        if (blockStates.isEmpty()) {
            // Tentar estimar pela bounding box
            val bb = entity.getBoundingBox()
            return VehicleData(
                entityId = entity.id,
                entity = entity,
                position = entity.blockPosition(),
                dimensions = AABB(bb),
                blockCount = 0,
                blockTypes = emptySet(),
                length = (bb.maxX - bb.minX).toInt(),
                width = (bb.maxZ - bb.minZ).toInt(),
                height = (bb.maxY - bb.minY).toInt(),
                vehicleClassId = "unknown",
                modSource = modSource,
                modType = modType,
                crewCount = entity.getPassengers().size,
                timestamp = System.currentTimeMillis()
            )
        }

        // Contar blocos únicos e tipos
        val blockTypes = blockStates.map { it.block.toString() }.toSet()
        val blockCount = blockStates.size

        // Calcular dimensões aproximadas
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        for (state in blockStates) {
            // Não temos posições individuais facilmente, usar bounding box da entidade
            val bb = entity.getBoundingBox()
            minX = min(minX, bb.minX.toInt())
            minY = min(minY, bb.minY.toInt())
            minZ = min(minZ, bb.minZ.toInt())
            maxX = max(maxX, bb.maxX.toInt())
            maxY = max(maxY, bb.maxY.toInt())
            maxZ = max(maxZ, bb.maxZ.toInt())
        }

        val length = maxX - minX
        val width = maxZ - minZ
        val height = maxY - minY

        // Classificar
        val registry = VehicleRegistry.getInstance()
        val vehicleClass = registry.findMatchingClass(blockCount, length, width, height, blockTypes)

        return VehicleData(
            entityId = entity.id,
            entity = entity,
            position = entity.blockPosition(),
            dimensions = AABB(entity.getBoundingBox()),
            blockCount = blockCount,
            blockTypes = blockTypes,
            length = length,
            width = width,
            height = height,
            vehicleClassId = vehicleClass?.id ?: "unknown",
            modSource = modSource,
            modType = modType,
            crewCount = entity.getPassengers().size,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Detecta veículos em uma região (para PresenceManager).
     */
    fun detectVehiclesInRegion(level: Level, bounds: AABB): List<VehicleData> {
        val vehicles = mutableListOf<VehicleData>()

        // Buscar entidades na área
        val entities = level.getEntitiesOfClass(Entity::class.java, bounds) { entity ->
            // Filtrar apenas entidades que podem ser veículos
            detectVehicle(entity) != null
        }

        for (entity in entities) {
            detectVehicle(entity)?.let { vehicles.add(it) }
        }

        return vehicles
    }

    /**
     * Limpa cache de veículos inválidos.
     */
    fun cleanupCache() {
        val now = System.currentTimeMillis()
        detectedVehicles.entries.removeIf { entry ->
            entry.value.entity.isRemoved || entry.value.entity.level != entry.value.entity.level || now - entry.value.timestamp > 60000 // 1 min
        }
        vehicleEntityMap.entries.removeIf { entry ->
            !detectedVehicles.containsKey(entry.key)
        }
    }

    fun getCachedVehicle(entityId: Int): VehicleData? = detectedVehicles[entityId]

    fun getAllCachedVehicles(): List<VehicleData> = detectedVehicles.values.toList()
}

/**
 * Dados de um veículo detectado.
 */
data class VehicleData(
    val entityId: Int,
    val entity: Entity,
    val position: BlockPos,
    val dimensions: AABB,
    val blockCount: Int,
    val blockTypes: Set<String>,
    val length: Int,
    val width: Int,
    val height: Int,
    val vehicleClassId: String,
    val modSource: String,
    val modType: String,
    val customName: String? = null,
    val ownerName: String? = null,
    val crewCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val nationId: String? = null,
    val nationName: String? = null,
    val isOperational: Boolean = true,
) {
    fun copy(
        customName: String? = this.customName,
        ownerName: String? = this.ownerName,
        crewCount: Int = this.crewCount,
        modSource: String = this.modSource,
        modType: String = this.modType,
        nationId: String? = this.nationId,
        nationName: String? = this.nationName,
        isOperational: Boolean = this.isOperational,
    ): VehicleData {
        return VehicleData(
            entityId = entityId,
            entity = entity,
            position = position,
            dimensions = dimensions,
            blockCount = blockCount,
            blockTypes = blockTypes,
            length = length,
            width = width,
            height = height,
            vehicleClassId = vehicleClassId,
            modSource = modSource,
            modType = modType,
            customName = customName,
            ownerName = ownerName,
            crewCount = crewCount,
            timestamp = timestamp,
            nationId = nationId,
            nationName = nationName,
            isOperational = isOperational
        )
    }

    fun getVehicleClass(): VehicleClass? = VehicleRegistry.getInstance().getClass(vehicleClassId)

    fun getScoreValue(): Int = getVehicleClass()?.scoreValue ?: 0

    fun getDisplayName(): String {
        val className = getVehicleClass()?.displayName ?: vehicleClassId
        return customName?.let { "$it ($className)" } ?: className
    }

    fun isValid(): Boolean = !entity.isRemoved && entity.isAlive()

    fun getCenterPos(): BlockPos = BlockPos(
        (dimensions.minX + dimensions.maxX) / 2,
        (dimensions.minY + dimensions.maxY) / 2,
        (dimensions.minZ + dimensions.maxZ) / 2
    )
}