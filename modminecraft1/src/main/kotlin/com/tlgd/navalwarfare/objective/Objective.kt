package com.tlgd.navalwarfare.objective

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import com.tlgd.navalwarfare.region.AssaultRegion
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Interface base para objetivos estratégicos.
 */
sealed interface Objective {
    val id: String
    val displayName: String
    val description: String
    val rewardPoints: Int
    val isRequiredForVictory: Boolean
    val regionId: String

    enum class State { PENDING, ACTIVE, COMPLETED, FAILED }

    var state: State
    var progress: Double // 0.0 a 1.0
    var startedAt: Long
    var completedAt: Long
    var completedByNationId: String?
    var completedByNationName: String?

    fun onAssaultStart(region: AssaultRegion, level: ServerLevel)
    fun onTick(region: AssaultRegion, level: ServerLevel): State
    fun onPlayerEvent(region: AssaultRegion, player: ServerPlayer, eventType: String, data: Map<String, Any>)
    fun onVehicleEvent(region: AssaultRegion, entity: Entity, eventType: String, data: Map<String, Any>)
    fun getProgressText(): String
    fun getDisplayInfo(): Map<String, Any>
    fun serialize(): Map<String, Any>
}

/**
 * Objetivo: Controle de área - manter presença em ponto/área por tempo.
 */
data class AreaControlObjective(
    override val id: String,
    override val displayName: String,
    override val description: String,
    override val rewardPoints: Int,
    override val isRequiredForVictory: Boolean,
    override val regionId: String,
    val targetPos: BlockPos,
    val radius: Int = 50,
    val requiredDurationSeconds: Int = 300, // 5 minutos
    val requiredNationPercentage: Double = 0.5, // 50% de presença
    override var state: State = State.PENDING,
    override var progress: Double = 0.0,
    override var startedAt: Long = 0,
    override var completedAt: Long = 0,
    override var completedByNationId: String? = null,
    override var completedByNationName: String? = null,
    private val nationPresenceTime: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap(),
) : Objective {

    companion object {
        val CODEC: Codec<AreaControlObjective> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("display_name").forGetter { it.displayName },
                Codec.STRING.fieldOf("description").forGetter { it.description },
                Codec.INT.fieldOf("reward_points").forGetter { it.rewardPoints },
                Codec.BOOL.fieldOf("required_for_victory").forGetter { it.isRequiredForVictory },
                Codec.STRING.fieldOf("region_id").forGetter { it.regionId },
                BlockPos.CODEC.fieldOf("target_pos").forGetter { it.targetPos },
                Codec.INT.fieldOf("radius").forGetter { it.radius },
                Codec.INT.fieldOf("required_duration_seconds").forGetter { it.requiredDurationSeconds },
                Codec.DOUBLE.fieldOf("required_nation_percentage").forGetter { it.requiredNationPercentage }
            ).apply(instance, AreaControlObjective::new)
        }
    }

    override fun onAssaultStart(region: AssaultRegion, level: ServerLevel) {
        state = State.ACTIVE
        startedAt = System.currentTimeMillis()
        nationPresenceTime.clear()
    }

    override fun onTick(region: AssaultRegion, level: ServerLevel): State {
        if (state != State.ACTIVE) return state

        // Verificar presença de cada nação na área
        val playersInArea = mutableMapOf<String, Int>()

        for (player in level.getServer().getPlayerList().getPlayers()) {
            val dist = player.blockPosition().distSqr(targetPos)
            if (dist <= radius * radius) {
                val nation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(player)
                val nationId = nation?.id ?: "independent_${player.uuid}"
                playersInArea.merge(nationId, 1, Int::plus)
            }
        }

        // Também contar veículos
        val vehicleTracker = com.tlgd.navalwarfare.vehicle.VehicleTracker.getInstance()
        val vehicles = vehicleTracker.getTrackedVehiclesInRegion(regionId)
        for (vehicle in vehicles) {
            if (!vehicle.isOperational) continue
            val dist = vehicle.vehicleData.position.distSqr(targetPos)
            if (dist <= radius * radius) {
                val nationId = vehicle.vehicleData.nationId ?: "unknown"
                playersInArea.merge(nationId, 1, Int::plus) // Veículos contam como presença
            }
        }

        val totalPresence = playersInArea.values.sum()
        if (totalPresence == 0) {
            progress = 0.0
            return State.ACTIVE
        }

        // Verificar se alguma nação tem maioria
        for ((nationId, count) in playersInArea) {
            val percentage = count.toDouble() / totalPresence
            if (percentage >= requiredNationPercentage) {
                val time = nationPresenceTime.computeIfAbsent(nationId) { AtomicInteger(0) }
                val seconds = time.incrementAndGet()
                progress = min(1.0, seconds / requiredDurationSeconds.toDouble())

                if (seconds >= requiredDurationSeconds) {
                    state = State.COMPLETED
                    completedAt = System.currentTimeMillis()
                    completedByNationId = nationId
                    completedByNationName = com.tlgd.navalwarfare.nation.NationIntegration.getNationById(nationId)?.name ?: nationId
                    return State.COMPLETED
                }
            } else {
                nationPresenceTime[nationId]?.set(0)
            }
        }

        return State.ACTIVE
    }

    override fun onPlayerEvent(region: AssaultRegion, player: ServerPlayer, eventType: String, data: Map<String, Any>) {
        // Eventos de jogador na área
    }

    override fun onVehicleEvent(region: AssaultRegion, entity: Entity, eventType: String, data: Map<String, Any>) {
        // Eventos de veículo na área
    }

    override fun getProgressText(): String {
        return when (state) {
            State.PENDING -> "Aguardando inicio"
            State.ACTIVE -> "Progresso: ${(progress * 100).toInt()}% (${(progress * requiredDurationSeconds).toInt()}/${requiredDurationSeconds}s)"
            State.COMPLETED -> "Concluido por ${completedByNationName}"
            State.FAILED -> "Falhou"
        }
    }

    override fun getDisplayInfo(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to displayName,
            "description" to description,
            "type" to "area_control",
            "state" to state.name,
            "progress" to progress,
            "targetPos" to targetPos.toShortString(),
            "radius" to radius,
            "requiredDuration" to requiredDurationSeconds,
            "rewardPoints" to rewardPoints,
            "requiredForVictory" to isRequiredForVictory
        )
    }

    override fun serialize(): Map<String, Any> = getDisplayInfo()
}

/**
 * Objetivo: Escolta - proteger veículo/estrutura até destino.
 */
data class EscortObjective(
    override val id: String,
    override val displayName: String,
    override val description: String,
    override val rewardPoints: Int,
    override val isRequiredForVictory: Boolean,
    override val regionId: String,
    val targetEntityId: Int, // Entity ID do veículo a escoltar
    val destinationPos: BlockPos,
    val maxDistanceFromTarget: Int = 100,
    override var state: State = State.PENDING,
    override var progress: Double = 0.0,
    override var startedAt: Long = 0,
    override var completedAt: Long = 0,
    override var completedByNationId: String? = null,
    override var completedByNationName: String? = null,
) : Objective {

    companion object {
        val CODEC: Codec<EscortObjective> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("display_name").forGetter { it.displayName },
                Codec.STRING.fieldOf("description").forGetter { it.description },
                Codec.INT.fieldOf("reward_points").forGetter { it.rewardPoints },
                Codec.BOOL.fieldOf("required_for_victory").forGetter { it.isRequiredForVictory },
                Codec.STRING.fieldOf("region_id").forGetter { it.regionId },
                Codec.INT.fieldOf("target_entity_id").forGetter { it.targetEntityId },
                BlockPos.CODEC.fieldOf("destination_pos").forGetter { it.destinationPos },
                Codec.INT.fieldOf("max_distance_from_target").forGetter { it.maxDistanceFromTarget }
            ).apply(instance, EscortObjective::new)
        }
    }

    override fun onAssaultStart(region: AssaultRegion, level: ServerLevel) {
        state = State.ACTIVE
        startedAt = System.currentTimeMillis()
    }

    override fun onTick(region: AssaultRegion, level: ServerLevel): State {
        if (state != State.ACTIVE) return state

        val targetEntity = level.getEntity(targetEntityId)
        if (targetEntity == null || !targetEntity.isAlive()) {
            state = State.FAILED
            return State.FAILED
        }

        // Verificar distância ao destino
        val distToDest = targetEntity.blockPosition().distSqr(destinationPos)
        val maxDistSq = maxDistanceFromTarget * maxDistanceFromTarget

        if (distToDest <= maxDistSq) {
            // Chegou ao destino
            progress = 1.0
            state = State.COMPLETED
            completedAt = System.currentTimeMillis()

            // Determinar nação que escoltou (maioria dos jogadores/veículos próximos)
            val escortingNation = findEscortingNation(level, targetEntity)
            completedByNationId = escortingNation?.first
            completedByNationName = escortingNation?.second
            return State.COMPLETED
        }

        // Progresso baseado na distância
        val startDist = region.bounds.getCenter().distSqr(destinationPos)
        val currentDist = targetEntity.blockPosition().distSqr(destinationPos)
        progress = 1.0 - (currentDist.toDouble() / startDist)
        progress = progress.coerceIn(0.0, 1.0)

        return State.ACTIVE
    }

    private fun findEscortingNation(level: ServerLevel, targetEntity: Entity): Pair<String, String>? {
        val nearbyNations = mutableMapOf<String, Int>()

        // Jogadores próximos
        for (player in level.getServer().getPlayerList().getPlayers()) {
            if (player.distanceTo(targetEntity) <= maxDistanceFromTarget) {
                val nation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(player)
                val nationId = nation?.id ?: "independent_${player.uuid}"
                val nationName = nation?.name ?: player.name.getString()
                nearbyNations.merge(nationId, 1, Int::plus)
            }
        }

        // Veículos próximos
        val vehicleTracker = com.tlgd.navalwarfare.vehicle.VehicleTracker.getInstance()
        val vehicles = vehicleTracker.getTrackedVehiclesInRegion(regionId)
        for (vehicle in vehicles) {
            if (vehicle.vehicleData.position.distSqr(targetEntity.blockPosition()) <= maxDistanceFromTarget * maxDistanceFromTarget) {
                val nationId = vehicle.vehicleData.nationId ?: "unknown"
                val nationName = vehicle.vehicleData.nationName ?: "Unknown"
                nearbyNations.merge(nationId, 2, Int::plus) // Veículos contam mais
            }
        }

        return nearbyNations.maxByOrNull { it.value }?.let { (k, v) -> k to nearbyNations[k]!! }
    }

    override fun onPlayerEvent(region: AssaultRegion, player: ServerPlayer, eventType: String, data: Map<String, Any>) {}
    override fun onVehicleEvent(region: AssaultRegion, entity: Entity, eventType: String, data: Map<String, Any>) {}

    override fun getProgressText(): String {
        return when (state) {
            State.PENDING -> "Aguardando inicio"
            State.ACTIVE -> "Progresso: ${(progress * 100).toInt()}% - Distancia: ${Math.sqrt(targetEntity?.blockPosition()?.distSqr(destinationPos)?.toDouble() ?: 0.0).toInt()} blocos"
            State.COMPLETED -> "Concluido por ${completedByNationName}"
            State.FAILED -> "Falhou - Alvo destruido ou perdido"
        }
    }

    override fun getDisplayInfo(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to displayName,
            "description" to description,
            "type" to "escort",
            "state" to state.name,
            "progress" to progress,
            "targetEntityId" to targetEntityId,
            "destination" to destinationPos.toShortString(),
            "rewardPoints" to rewardPoints,
            "requiredForVictory" to isRequiredForVictory
        )
    }

    override fun serialize(): Map<String, Any> = getDisplayInfo()
}

/**
 * Objetivo: Destruição de alvo específico.
 */
data class DestroyTargetObjective(
    override val id: String,
    override val displayName: String,
    override val description: String,
    override val rewardPoints: Int,
    override val isRequiredForVictory: Boolean,
    override val regionId: String,
    val targetEntityId: Int, // Entity ID do alvo
    val targetType: String, // "vehicle", "structure", "coordinate"
    val targetPos: BlockPos? = null, // Para alvos fixos
    override var state: State = State.PENDING,
    override var progress: Double = 0.0,
    override var startedAt: Long = 0,
    override var completedAt: Long = 0,
    override var completedByNationId: String? = null,
    override var completedByNationName: String? = null,
) : Objective {

    companion object {
        val CODEC: Codec<DestroyTargetObjective> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("display_name").forGetter { it.displayName },
                Codec.STRING.fieldOf("description").forGetter { it.description },
                Codec.INT.fieldOf("reward_points").forGetter { it.rewardPoints },
                Codec.BOOL.fieldOf("required_for_victory").forGetter { it.isRequiredForVictory },
                Codec.STRING.fieldOf("region_id").forGetter { it.regionId },
                Codec.INT.fieldOf("target_entity_id").forGetter { it.targetEntityId },
                Codec.STRING.fieldOf("target_type").forGetter { it.targetType },
                BlockPos.CODEC.optionalFieldOf("target_pos").forGetter { it.targetPos }
            ).apply(instance, DestroyTargetObjective::new)
        }
    }

    override fun onAssaultStart(region: AssaultRegion, level: ServerLevel) {
        state = State.ACTIVE
        startedAt = System.currentTimeMillis()
    }

    override fun onTick(region: AssaultRegion, level: ServerLevel): State {
        if (state != State.ACTIVE) return state

        if (targetType == "vehicle") {
            val targetEntity = level.getEntity(targetEntityId)
            if (targetEntity == null || !targetEntity.isAlive()) {
                state = State.COMPLETED
                progress = 1.0
                completedAt = System.currentTimeMillis()
                return State.COMPLETED
            }
        } else if (targetType == "coordinate" && targetPos != null) {
            // Verificar se estrutura no local foi destruída
            // Implementação simplificada
        }

        return State.ACTIVE
    }

    override fun onPlayerEvent(region: AssaultRegion, player: ServerPlayer, eventType: String, data: Map<String, Any>) {
        if (eventType == "vehicle_destroyed") {
            val destroyedEntityId = data["entityId"] as? Int
            if (destroyedEntityId == targetEntityId) {
                val nation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(player)
                completedByNationId = nation?.id
                completedByNationName = nation?.name
            }
        }
    }

    override fun onVehicleEvent(region: AssaultRegion, entity: Entity, eventType: String, data: Map<String, Any>) {
        if (eventType == "destroyed" && entity.id == targetEntityId) {
            val attacker = data["attacker"] as? net.minecraft.server.level.ServerPlayer
            attacker?.let { player ->
                val nation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(player)
                completedByNationId = nation?.id
                completedByNationName = nation?.name
            }
        }
    }

    override fun getProgressText(): String {
        return when (state) {
            State.PENDING -> "Aguardando inicio"
            State.ACTIVE -> "Alvo ativo - Procurar e destruir"
            State.COMPLETED -> "Destruido por ${completedByNationName}"
            State.FAILED -> "Falhou"
        }
    }

    override fun getDisplayInfo(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to displayName,
            "description" to description,
            "type" to "destroy_target",
            "state" to state.name,
            "progress" to progress,
            "targetEntityId" to targetEntityId,
            "targetType" to targetType,
            "rewardPoints" to rewardPoints,
            "requiredForVictory" to isRequiredForVictory
        )
    }

    override fun serialize(): Map<String, Any> = getDisplayInfo()
}

/**
 * Objetivo: Superioridade naval - manter mais veículos/tonelagem na região.
 */
data class NavalSuperiorityObjective(
    override val id: String,
    override val displayName: String,
    override val description: String,
    override val rewardPoints: Int,
    override val isRequiredForVictory: Boolean,
    override val regionId: String,
    val requiredMargin: Double = 0.3, // 30% a mais que o segundo colocado
    val holdDurationSeconds: Int = 180, // Manter por 3 minutos
    override var state: State = State.PENDING,
    override var progress: Double = 0.0,
    override var startedAt: Long = 0,
    override var completedAt: Long = 0,
    override var completedByNationId: String? = null,
    override var completedByNationName: String? = null,
    private val nationHoldTime: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap(),
) : Objective {

    companion object {
        val CODEC: Codec<NavalSuperiorityObjective> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("display_name").forGetter { it.displayName },
                Codec.STRING.fieldOf("description").forGetter { it.description },
                Codec.INT.fieldOf("reward_points").forGetter { it.rewardPoints },
                Codec.BOOL.fieldOf("required_for_victory").forGetter { it.isRequiredForVictory },
                Codec.STRING.fieldOf("region_id").forGetter { it.regionId },
                Codec.DOUBLE.fieldOf("required_margin").forGetter { it.requiredMargin },
                Codec.INT.fieldOf("hold_duration_seconds").forGetter { it.holdDurationSeconds }
            ).apply(instance, NavalSuperiorityObjective::new)
        }
    }

    override fun onAssaultStart(region: AssaultRegion, level: ServerLevel) {
        state = State.ACTIVE
        startedAt = System.currentTimeMillis()
        nationHoldTime.clear()
    }

    override fun onTick(region: AssaultRegion, level: ServerLevel): State {
        if (state != State.ACTIVE) return state

        val vehicleTracker = com.tlgd.navalwarfare.vehicle.VehicleTracker.getInstance()
        val vehicles = vehicleTracker.getTrackedVehiclesInRegion(regionId)
            .filter { it.isOperational }
            .groupBy { it.vehicleData.nationId ?: "unknown" }

        val nationScores = vehicles.mapValues { (nationId, list) ->
            nationId to list.sumOf { it.vehicleData.getScoreValue() }
        }.toSortedMap(Comparator.reverseOrder()) // Maior score primeiro

        if (nationScores.size < 2) {
            // Não há competição
            progress = 0.0
            nationHoldTime.values.forEach { it.set(0) }
            return State.ACTIVE
        }

        val topEntry = nationScores.first()
        val secondEntry = nationScores.drop(1).firstOrNull()

        val topNationId = topEntry.key
        val topScore = topEntry.value
        val secondScore = secondEntry?.value ?: 0

        val margin = if (secondScore > 0) (topScore - secondScore).toDouble() / secondScore else 1.0

        if (margin >= requiredMargin) {
            val time = nationHoldTime.computeIfAbsent(topNationId) { AtomicInteger(0) }
            val seconds = time.incrementAndGet()
            progress = min(1.0, seconds / holdDurationSeconds.toDouble())

            if (seconds >= holdDurationSeconds) {
                state = State.COMPLETED
                completedAt = System.currentTimeMillis()
                completedByNationId = topNationId
                completedByNationName = com.tlgd.navalwarfare.nation.NationIntegration.getNationById(topNationId)?.name ?: topNationId
                return State.COMPLETED
            }
        } else {
            // Resetar tempos
            nationHoldTime.values.forEach { it.set(0) }
            progress = 0.0
        }

        return State.ACTIVE
    }

    override fun onPlayerEvent(region: AssaultRegion, player: ServerPlayer, eventType: String, data: Map<String, Any>) {}
    override fun onVehicleEvent(region: AssaultRegion, entity: Entity, eventType: String, data: Map<String, Any>) {}

    override fun getProgressText(): String {
        return when (state) {
            State.PENDING -> "Aguardando inicio"
            State.ACTIVE -> "Superioridade: ${(progress * 100).toInt()}% (${(progress * holdDurationSeconds).toInt()}/${holdDurationSeconds}s)"
            State.COMPLETED -> "Conquistado por ${completedByNationName}"
            State.FAILED -> "Falhou"
        }
    }

    override fun getDisplayInfo(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to displayName,
            "description" to description,
            "type" to "naval_superiority",
            "state" to state.name,
            "progress" to progress,
            "requiredMargin" to requiredMargin,
            "holdDuration" to holdDurationSeconds,
            "rewardPoints" to rewardPoints,
            "requiredForVictory" to isRequiredForVictory
        )
    }

    override fun serialize(): Map<String, Any> = getDisplayInfo()
}

/**
 * Objetivo: Controle de múltiplos pontos simultâneos.
 */
data class MultiPointControlObjective(
    override val id: String,
    override val displayName: String,
    override val description: String,
    override val rewardPoints: Int,
    override val isRequiredForVictory: Boolean,
    override val regionId: String,
    val points: List<ControlPoint>, // Lista de pontos de controle
    val requiredPoints: Int, // Quantos pontos precisa controlar simultaneamente
    val holdDurationSeconds: Int = 120,
    override var state: State = State.PENDING,
    override var progress: Double = 0.0,
    override var startedAt: Long = 0,
    override var completedAt: Long = 0,
    override var completedByNationId: String? = null,
    override var completedByNationName: String? = null,
    private val pointControl: ConcurrentHashMap<String, String> = ConcurrentHashMap(), // pointId -> nationId
    private val nationHoldTime: ConcurrentHashMap<String, AtomicInteger> = ConcurrentHashMap(),
) : Objective {

    data class ControlPoint(
        val id: String,
        val name: String,
        val pos: BlockPos,
        val radius: Int = 30
    ) {
        companion object {
            val CODEC: Codec<ControlPoint> = RecordCodecBuilder.create { instance ->
                instance.group(
                    Codec.STRING.fieldOf("id").forGetter { it.id },
                    Codec.STRING.fieldOf("name").forGetter { it.name },
                    BlockPos.CODEC.fieldOf("pos").forGetter { it.pos },
                    Codec.INT.fieldOf("radius").forGetter { it.radius }
                ).apply(instance, ControlPoint::new)
            }
        }
    }

    companion object {
        val CODEC: Codec<MultiPointControlObjective> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("display_name").forGetter { it.displayName },
                Codec.STRING.fieldOf("description").forGetter { it.description },
                Codec.INT.fieldOf("reward_points").forGetter { it.rewardPoints },
                Codec.BOOL.fieldOf("required_for_victory").forGetter { it.isRequiredForVictory },
                Codec.STRING.fieldOf("region_id").forGetter { it.regionId },
                ControlPoint.CODEC.listOf().fieldOf("points").forGetter { it.points },
                Codec.INT.fieldOf("required_points").forGetter { it.requiredPoints },
                Codec.INT.fieldOf("hold_duration_seconds").forGetter { it.holdDurationSeconds }
            ).apply(instance, MultiPointControlObjective::new)
        }
    }

    override fun onAssaultStart(region: AssaultRegion, level: ServerLevel) {
        state = State.ACTIVE
        startedAt = System.currentTimeMillis()
        pointControl.clear()
        nationHoldTime.clear()
    }

    override fun onTick(region: AssaultRegion, level: ServerLevel): State {
        if (state != State.ACTIVE) return state

        // Verificar controle de cada ponto
        for (point in points) {
            val controllingNation = getControllingNation(level, point)
            pointControl[point.id] = controllingNation ?: "none"
        }

        // Contar pontos por nação
        val pointsByNation = pointControl.values
            .filter { it != "none" }
            .groupingBy { it }
            .eachCount()

        // Verificar se alguma nação controla pontos suficientes
        for ((nationId, controlledCount) in pointsByNation) {
            if (controlledCount >= requiredPoints) {
                val time = nationHoldTime.computeIfAbsent(nationId) { AtomicInteger(0) }
                val seconds = time.incrementAndGet()
                progress = min(1.0, seconds / holdDurationSeconds.toDouble())

                if (seconds >= holdDurationSeconds) {
                    state = State.COMPLETED
                    completedAt = System.currentTimeMillis()
                    completedByNationId = nationId
                    completedByNationName = com.tlgd.navalwarfare.nation.NationIntegration.getNationById(nationId)?.name ?: nationId
                    return State.COMPLETED
                }
            } else {
                nationHoldTime[nationId]?.set(0)
            }
        }

        // Progresso geral = pontos controlados pela nação líder / requiredPoints
        val maxControlled = pointsByNation.values.maxOrNull() ?: 0
        progress = maxControlled / requiredPoints.toDouble()

        return State.ACTIVE
    }

    private fun getControllingNation(level: ServerLevel, point: ControlPoint): String? {
        val nationsInRange = mutableMapOf<String, Int>()

        // Jogadores
        for (player in level.getServer().getPlayerList().getPlayers()) {
            if (player.blockPosition().distSqr(point.pos) <= point.radius * point.radius) {
                val nation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(player)
                val nationId = nation?.id ?: "independent_${player.uuid}"
                nationsInRange.merge(nationId, 1, Int::plus)
            }
        }

        // Veículos
        val vehicleTracker = com.tlgd.navalwarfare.vehicle.VehicleTracker.getInstance()
        val vehicles = vehicleTracker.getTrackedVehiclesInRegion(regionId)
        for (vehicle in vehicles) {
            if (!vehicle.isOperational) continue
            if (vehicle.vehicleData.position.distSqr(point.pos) <= point.radius * point.radius) {
                val nationId = vehicle.vehicleData.nationId ?: "unknown"
                nationsInRange.merge(nationId, 2, Int::plus)
            }
        }

        return nationsInRange.maxByOrNull { it.value }?.key
    }

    override fun onPlayerEvent(region: AssaultRegion, player: ServerPlayer, eventType: String, data: Map<String, Any>) {}
    override fun onVehicleEvent(region: AssaultRegion, entity: Entity, eventType: String, data: Map<String, Any>) {}

    override fun getProgressText(): String {
        val controlled = pointControl.values.filter { it != "none" }.groupingBy { it }.eachCount()
        val maxCtrl = controlled.values.maxOrNull() ?: 0
        return when (state) {
            State.PENDING -> "Aguardando inicio"
            State.ACTIVE -> "Pontos controlados: $maxCtrl/$requiredPoints - ${(progress * 100).toInt()}%"
            State.COMPLETED -> "Concluido por ${completedByNationName}"
            State.FAILED -> "Falhou"
        }
    }

    override fun getDisplayInfo(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "name" to displayName,
            "description" to description,
            "type" to "multi_point_control",
            "state" to state.name,
            "progress" to progress,
            "points" to points.map { mapOf("id" to it.id, "name" to it.name, "pos" to it.pos.toShortString()) },
            "requiredPoints" to requiredPoints,
            "holdDuration" to holdDurationSeconds,
            "currentControl" to pointControl,
            "rewardPoints" to rewardPoints,
            "requiredForVictory" to isRequiredForVictory
        )
    }

    override fun serialize(): Map<String, Any> = getDisplayInfo()
}

/**
 * Factory para criar objetivos a partir de configuração.
 */
object ObjectiveFactory {
    fun createFromJson(json: Map<String, Any>): Objective? {
        val type = json["type"] as? String ?: return null
        return when (type) {
            "area_control" -> createAreaControl(json)
            "escort" -> createEscort(json)
            "destroy_target" -> createDestroyTarget(json)
            "naval_superiority" -> createNavalSuperiority(json)
            "multi_point_control" -> createMultiPointControl(json)
            else -> null
        }
    }

    private fun createAreaControl(json: Map<String, Any>): AreaControlObjective {
        val pos = json["target_pos"] as Map<String, Int>
        return AreaControlObjective(
            id = json["id"] as String,
            displayName = json["display_name"] as String,
            description = json["description"] as String,
            rewardPoints = json["reward_points"] as Int,
            isRequiredForVictory = json["required_for_victory"] as Boolean,
            regionId = json["region_id"] as String,
            targetPos = BlockPos(pos["x"]!!, pos["y"]!!, pos["z"]!!),
            radius = json["radius"] as? Int ?: 50,
            requiredDurationSeconds = json["required_duration_seconds"] as? Int ?: 300,
            requiredNationPercentage = json["required_nation_percentage"] as? Double ?: 0.5
        )
    }

    private fun createEscort(json: Map<String, Any>): EscortObjective {
        val dest = json["destination_pos"] as Map<String, Int>
        return EscortObjective(
            id = json["id"] as String,
            displayName = json["display_name"] as String,
            description = json["description"] as String,
            rewardPoints = json["reward_points"] as Int,
            isRequiredForVictory = json["required_for_victory"] as Boolean,
            regionId = json["region_id"] as String,
            targetEntityId = json["target_entity_id"] as Int,
            destinationPos = BlockPos(dest["x"]!!, dest["y"]!!, dest["z"]!!),
            maxDistanceFromTarget = json["max_distance_from_target"] as? Int ?: 100
        )
    }

    private fun createDestroyTarget(json: Map<String, Any>): DestroyTargetObjective {
        val pos = (json["target_pos"] as? Map<String, Int>)?.let { BlockPos(it["x"]!!, it["y"]!!, it["z"]!!) }
        return DestroyTargetObjective(
            id = json["id"] as String,
            displayName = json["display_name"] as String,
            description = json["description"] as String,
            rewardPoints = json["reward_points"] as Int,
            isRequiredForVictory = json["required_for_victory"] as Boolean,
            regionId = json["region_id"] as String,
            targetEntityId = json["target_entity_id"] as Int,
            targetType = json["target_type"] as String,
            targetPos = pos
        )
    }

    private fun createNavalSuperiority(json: Map<String, Any>): NavalSuperiorityObjective {
        return NavalSuperiorityObjective(
            id = json["id"] as String,
            displayName = json["display_name"] as String,
            description = json["description"] as String,
            rewardPoints = json["reward_points"] as Int,
            isRequiredForVictory = json["required_for_victory"] as Boolean,
            regionId = json["region_id"] as String,
            requiredMargin = json["required_margin"] as? Double ?: 0.3,
            holdDurationSeconds = json["hold_duration_seconds"] as? Int ?: 180
        )
    }

    private fun createMultiPointControl(json: Map<String, Any>): MultiPointControlObjective {
        val points = (json["points"] as List<Map<String, Any>>).map { p ->
            val pos = p["pos"] as Map<String, Int>
            MultiPointControlObjective.ControlPoint(
                id = p["id"] as String,
                name = p["name"] as String,
                pos = BlockPos(pos["x"]!!, pos["y"]!!, pos["z"]!!),
                radius = p["radius"] as? Int ?: 30
            )
        }
        return MultiPointControlObjective(
            id = json["id"] as String,
            displayName = json["display_name"] as String,
            description = json["description"] as String,
            rewardPoints = json["reward_points"] as Int,
            isRequiredForVictory = json["required_for_victory"] as Boolean,
            regionId = json["region_id"] as String,
            points = points,
            requiredPoints = json["required_points"] as Int,
            holdDurationSeconds = json["hold_duration_seconds"] as? Int ?: 120
        )
    }
}