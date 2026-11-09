package com.tlgd.navalwarfare.scoring

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mojang.logging.LogUtils
import com.tlgd.navalwarfare.config.NavalWarfareConfig
import com.tlgd.navalwarfare.vehicle.VehicleClass
import com.tlgd.navalwarfare.vehicle.VehicleIntegrationManager
import net.minecraft.server.level.ServerPlayer
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Gerenciador avançado de pontuação com proteções anti-farm.
 */
class ScoringManager {

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private var INSTANCE: ScoringManager? = null

        fun getInstance(): ScoringManager {
            return INSTANCE ?: throw IllegalStateException("ScoringManager nao inicializado!")
        }

        fun init() {
            INSTANCE = ScoringManager()
            LOGGER.info("ScoringManager inicializado")
        }
    }

    // regionId -> nationId -> NationScore
    private val scores = ConcurrentHashMap<String, ConcurrentHashMap<String, NationScore>>()

    // Anti-farm: playerId -> targetPlayerId -> lastKillTime
    private val killCooldowns = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()
    // playerId -> targetPlayerId -> killCount (última hora)
    private val killCounts = ConcurrentHashMap<String, ConcurrentHashMap<String, AtomicInteger>>()
    // vehicleEntityId -> destroyedTime (anti-duplicação)
    private val destroyedVehicles = ConcurrentHashMap<Int, Long>()

    // Configurações anti-farm
    private val KILL_COOLDOWN_MS = 5 * 60 * 1000L // 5 minutos
    private val MAX_KILLS_PER_TARGET_PER_HOUR = 3
    private val MIN_KILL_DISTANCE = 5.0 // blocos
    private val VEHICLE_DESTROY_COOLDOWN_MS = 60 * 60 * 1000L // 1 hora

    data class NationScore(
        val nationId: String,
        val nationName: String,
        var score: Int = 0,
        var playerKills: Int = 0,
        var vehicleDestroys: Int = 0,
        var objectivePoints: Int = 0,
        var presencePoints: Int = 0,
        var flagCaptures: Int = 0,
        var lastUpdate: Long = System.currentTimeMillis(),
        // Detalhamento para debug/HUD
        var killDetails: MutableMap<String, Int> = ConcurrentHashMap(), // vehicleClassId -> count
        var objectiveDetails: MutableMap<String, Int> = ConcurrentHashMap(), // objectiveId -> points
    ) {
        fun addPoints(points: Int, source: String) {
            score = min(NavalWarfareConfig.maxScoreCap, score + points)
            lastUpdate = System.currentTimeMillis()
        }

        fun addPlayerKill(victimName: String) {
            playerKills++
            addPoints(NavalWarfareConfig.pointsPerPlayerKill, "player_kill")
        }

        fun addVehicleDestroy(vehicleClass: VehicleClass) {
            vehicleDestroys++
            val points = vehicleClass.scoreValue
            addPoints(points, "vehicle_destroy")
            killDetails.merge(vehicleClass.id, 1, Int::plus)
        }

        fun addObjectivePoints(objectiveId: String, points: Int) {
            objectivePoints += points
            addPoints(points, "objective")
            objectiveDetails.merge(objectiveId, points, Int::plus)
        }

        fun addPresencePoints(points: Int) {
            presencePoints += points
            addPoints(points, "presence")
        }

        fun addFlagCapture() {
            flagCaptures++
            addPoints(NavalWarfareConfig.pointsPerFlagCapture, "flag_capture")
        }

        fun getLeaderboardEntry(): LeaderboardEntry {
            return LeaderboardEntry(
                nationId = nationId,
                nationName = nationName,
                score = score,
                playerKills = playerKills,
                vehicleDestroys = vehicleDestroys,
                objectivePoints = objectivePoints,
                presencePoints = presencePoints,
                flagCaptures = flagCaptures
            )
        }
    }

    data class LeaderboardEntry(
        val nationId: String,
        val nationName: String,
        val score: Int,
        val playerKills: Int,
        val vehicleDestroys: Int,
        val objectivePoints: Int,
        val presencePoints: Int,
        val flagCaptures: Int
    )

    /**
     * Obtém ou cria score para uma nação em uma região.
     */
    private fun getOrCreateScore(regionId: String, nationId: String, nationName: String): NationScore {
        val regionMap = scores.computeIfAbsent(regionId) { ConcurrentHashMap() }
        return regionMap.computeIfAbsent(nationId) { NationScore(nationId, nationName) }
    }

    /**
     * Registra eliminação de jogador com validações anti-farm.
     * Retorna true se a pontuação foi concedida.
     */
    fun onPlayerKill(regionId: String, killer: ServerPlayer, victim: ServerPlayer): Boolean {
        val killerNation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(killer)
        val victimNation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(victim)

        val killerNationId = killerNation?.id ?: "independent_${killer.uuid}"
        val killerNationName = killerNation?.name ?: killer.name.getString()
        val victimNationId = victimNation?.id ?: "independent_${victim.uuid}"

        // Não pontuar se mesma nação (teamkill)
        if (killerNationId == victimNationId) {
            return false
        }

        // Verificar distância mínima
        val distance = killer.distanceTo(victim)
        if (distance < MIN_KILL_DISTANCE) {
            LOGGER.debug("Kill ignorado - distancia muito curta: {} blocos", distance)
            return false
        }

        // Verificar cooldown anti-farm
        val now = System.currentTimeMillis()
        val killerCooldowns = killCooldowns.computeIfAbsent(killerNationId) { ConcurrentHashMap() }
        val lastKillTime = killerCooldowns.getOrDefault(victimNationId, 0L)

        if (now - lastKillTime < KILL_COOLDOWN_MS) {
            LOGGER.debug("Kill ignorado - cooldown ativo para {} vs {}", killerNationId, victimNationId)
            return false
        }

        // Verificar limite de kills por alvo/hora
        val killerKillCounts = killCounts.computeIfAbsent(killerNationId) { ConcurrentHashMap() }
        val victimKillCount = killerKillCounts.computeIfAbsent(victimNationId) { AtomicInteger(0) }

        // Limpar contagens antigas (mais de 1 hora)
        cleanupOldKillCounts(killerNationId, now)

        if (victimKillCount.get() >= MAX_KILLS_PER_TARGET_PER_HOUR) {
            LOGGER.debug("Kill ignorado - limite de kills por alvo excedido")
            return false
        }

        // Verificar suicídio indireto (vítima morreu por próprio explosivo logo após dano do killer)
        if (isLikelySuicide(killer, victim)) {
            LOGGER.debug("Kill ignorado - provavel suicidio")
            return false
        }

        // Todas validações passaram - conceder pontos
        killerCooldowns[victimNationId] = now
        victimKillCount.incrementAndGet()

        val score = getOrCreateScore(regionId, killerNationId, killerNationName)
        score.addPlayerKill(victim.name.getString())

        LOGGER.info("Kill registrado: {} ({}) matou {} ({}) em {} - +{} pts",
            killer.name.getString(), killerNationName, victim.name.getString(), victimNationId, regionId, NavalWarfareConfig.pointsPerPlayerKill)

        return true
    }

    /**
     * Registra destruição de veículo com validações.
     */
    fun onVehicleDestroyed(regionId: String, vehicleEntityId: Int, attacker: ServerPlayer?, vehicleClass: VehicleClass): Boolean {
        // Anti-duplicação: veículo já contabilizado recentemente?
        val now = System.currentTimeMillis()
        val lastDestroyed = destroyedVehicles[vehicleEntityId]
        if (lastDestroyed != null && now - lastDestroyed < VEHICLE_DESTROY_COOLDOWN_MS) {
            LOGGER.debug("Destruicao de veiculo ignorada - ja contabilizado (entityId={})", vehicleEntityId)
            return false
        }

        val attackerNation = attacker?.let { com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(it) }
        val attackerNationId = attackerNation?.id ?: "unknown_${attacker?.uuid ?: "none"}"
        val attackerNationName = attackerNation?.name ?: attacker?.name.getString() ?: "Desconhecido"

        if (attackerNationId == "unknown_" || attackerNationId.startsWith("unknown_")) {
            // Não pontuar se não conseguimos identificar o atacante
            return false
        }

        destroyedVehicles[vehicleEntityId] = now

        val score = getOrCreateScore(regionId, attackerNationId, attackerNationName)
        score.addVehicleDestroy(vehicleClass)

        LOGGER.info("Veiculo destruido: {} ({}) destruiu {} em {} - +{} pts",
            attackerNationName, attackerNationId, vehicleClass.displayName, regionId, vehicleClass.scoreValue)

        return true
    }

    /**
     * Registra conclusão de objetivo.
     */
    fun onObjectiveComplete(regionId: String, nationId: String, nationName: String, objectiveId: String, points: Int) {
        val score = getOrCreateScore(regionId, nationId, nationName)
        score.addObjectivePoints(objectiveId, points)

        LOGGER.info("Objetivo completado: {} ({}) completou {} em {} - +{} pts",
            nationName, nationId, objectiveId, regionId, points)
    }

    /**
     * Adiciona pontos de presença.
     */
    fun addPresencePoints(regionId: String, nationId: String, nationName: String, points: Int) {
        val score = getOrCreateScore(regionId, nationId, nationName)
        score.addPresencePoints(points)
    }

    /**
     * Registra captura de bandeira/ponto de controle.
     */
    fun onFlagCapture(regionId: String, nationId: String, nationName: String) {
        val score = getOrCreateScore(regionId, nationId, nationName)
        score.addFlagCapture()
    }

    /**
     * Obtém leaderboard de uma região.
     */
    fun getLeaderboard(regionId: String): List<LeaderboardEntry> {
        return scores[regionId]?.values
            ?.sortedByDescending { it.score }
            ?.map { it.getLeaderboardEntry() }
            ?: emptyList()
    }

    /**
     * Obtém score de uma nação específica.
     */
    fun getScore(regionId: String, nationId: String): NationScore? {
        return scores[regionId]?.get(nationId)
    }

    /**
     * Obtém vencedor atual (maior score).
     */
    fun getCurrentWinner(regionId: String): LeaderboardEntry? {
        return getLeaderboard(regionId).firstOrNull()
    }

    /**
     * Reseta scores de uma região (novo assalto).
     */
    fun resetRegionScores(regionId: String) {
        scores[regionId]?.clear()
        killCooldowns.clear()
        killCounts.clear()
        destroyedVehicles.clear()
    }

    /**
     * Limpa contagens de kill antigas (mais de 1 hora).
     */
    private fun cleanupOldKillCounts(killerNationId: String, now: Long) {
        val nationKillCounts = killCounts[killerNationId] ?: return
        val oneHourAgo = now - 3600000L

        // Como não temos timestamp por kill individual, limpamos tudo periodicamente
        // Uma implementação mais robusta usaria timestamps por entrada
        if (nationKillCounts.size > 50) { // Limitar tamanho
            nationKillCounts.clear()
        }
    }

    /**
     * Verifica se é provável suicídio indireto.
     */
    private fun isLikelySuicide(killer: ServerPlayer, victim: ServerPlayer): Boolean {
        // Verificar se vítima morreu por explosão própria logo após receber dano do killer
        // Isso exigiria rastrear fonte de dano recente - simplificado por enquanto
        return false
    }

    /**
     * Limpeza periódica de caches.
     */
    fun cleanup() {
        val now = System.currentTimeMillis()
        val cooldownThreshold = now - KILL_COOLDOWN_MS * 2
        val vehicleThreshold = now - VEHICLE_DESTROY_COOLDOWN_MS * 2

        killCooldowns.values.forEach { map ->
            map.entries.removeIf { (_, time) -> time < cooldownThreshold }
        }

        destroyedVehicles.entries.removeIf { (_, time) -> time < vehicleThreshold }

        // Limpar scores de regiões inativas há muito tempo
        // (implementar se necessário)
    }

    /**
     * Serializa scores para salvamento.
     */
    fun serializeRegionScores(regionId: String): String {
        val regionScores = scores[regionId]?.values?.toList() ?: emptyList()
        val gson = GsonBuilder().setPrettyPrinting().create()
        return gson.toJson(regionScores.map { it.getLeaderboardEntry() })
    }

    /**
     * Obtém estatísticas detalhadas para HUD/GUI.
     */
    fun getDetailedStats(regionId: String, nationId: String): Map<String, Any> {
        val score = getScore(regionId, nationId) ?: return emptyMap()
        return mapOf(
            "score" to score.score,
            "playerKills" to score.playerKills,
            "vehicleDestroys" to score.vehicleDestroys,
            "objectivePoints" to score.objectivePoints,
            "presencePoints" to score.presencePoints,
            "flagCaptures" to score.flagCaptures,
            "killDetails" to score.killDetails,
            "objectiveDetails" to score.objectiveDetails
        )
    }
}