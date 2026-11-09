package com.tlgd.navalwarfare.presence

import com.mojang.logging.LogUtils
import com.tlgd.navalwarfare.config.NavalWarfareConfig
import com.tlgd.navalwarfare.region.AssaultRegion
import com.tlgd.navalwarfare.region.AssaultRegionManager
import com.tlgd.navalwarfare.scoring.ScoringManager
import com.tlgd.navalwarfare.vehicle.VehicleIntegrationManager
import com.tlgd.navalwarfare.vehicle.VehicleTracker
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Gerenciador de presença naval em regiões de assalto.
 * Verifica periodicamente quais nações têm presença (jogadores + veículos) na região.
 */
class PresenceManager {

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private var INSTANCE: PresenceManager? = null
        private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "NavalWarfare-PresenceThread").apply { isDaemon = true }
        }

        fun getInstance(): PresenceManager {
            return INSTANCE ?: throw IllegalStateException("PresenceManager nao inicializado!")
        }

        fun init() {
            INSTANCE = PresenceManager()
            // Agendar verificação periódica
            val intervalSeconds = NavalWarfareConfig.presenceCheckIntervalSeconds
            scheduler.scheduleAtFixedRate(
                { getInstance().checkAllRegions() },
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
            )
            LOGGER.info("PresenceManager inicializado - verificacao a cada {}s", intervalSeconds)
        }

        fun shutdown() {
            scheduler.shutdown()
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow()
                }
            } catch (e: InterruptedException) {
                scheduler.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }

    // regionId -> nationId -> PresenceData
    private val presenceData = ConcurrentHashMap<String, ConcurrentHashMap<String, PresenceData>>()
    // regionId -> playerId -> lastSeenTime
    private val playerPresence = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()
    // Última vez que pontos de presença foram concedidos por região
    private val lastPresenceReward = ConcurrentHashMap<String, Long>()

    data class PresenceData(
        val nationId: String,
        val nationName: String,
        var playerCount: Int = 0,
        var vehicleCount: Int = 0,
        var totalVehicleScore: Int = 0, // soma de scoreValue dos veículos
        var lastUpdate: Long = System.currentTimeMillis(),
        var consecutiveTicks: Int = 0, // ticks consecutivos com presença
        var totalPresenceSeconds: Long = 0,
    ) {
        fun update(playerCount: Int, vehicleCount: Int, vehicleScore: Int) {
            this.playerCount = playerCount
            this.vehicleCount = vehicleCount
            this.totalVehicleScore = vehicleScore
            this.lastUpdate = System.currentTimeMillis()
            this.consecutiveTicks++
        }

        fun reset() {
            playerCount = 0
            vehicleCount = 0
            totalVehicleScore = 0
            consecutiveTicks = 0
        }

        fun hasPresence(): Boolean = playerCount > 0 || vehicleCount > 0
    }

    /**
     * Verifica presença em todas as regiões ativas.
     */
    fun checkAllRegions() {
        val manager = AssaultRegionManager.getInstance()
        val server = net.minecraft.server.MinecraftServer.getServer() ?: return

        for (region in manager.getAllRegions()) {
            if (region.state == com.tlgd.navalwarfare.region.AssaultState.ACTIVE) {
                checkRegion(region, server)
            }
        }

        // Limpar dados de regiões inativas
        cleanupInactiveRegions()
    }

    /**
     * Verifica presença em uma região específica.
     */
    fun checkRegion(region: AssaultRegion, server: net.minecraft.server.MinecraftServer) {
        val regionId = region.id
        val level = server.getLevel(region.bounds.dimension) ?: return

        // Escanear veículos
        val vehicleTracker = VehicleIntegrationManager.getInstance().scanRegion(region, level)

        // Contar jogadores por nação
        val playersByNation = mutableMapOf<String, MutableList<ServerPlayer>>()
        for (player in server.getPlayerList().getPlayers()) {
            if (region.bounds.contains(player.blockPosition(), player.level.dimension())) {
                val nation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(player)
                val nationId = nation?.id ?: "independent_${player.uuid}"
                val nationName = nation?.name ?: player.name.getString()
                playersByNation.computeIfAbsent(nationId) { mutableListOf() }.add(player)
            }
        }

        // Atualizar dados de presença
        val regionPresence = presenceData.computeIfAbsent(regionId) { ConcurrentHashMap() }

        // Processar cada nação com presença
        val allNationIds = (playersByNation.keys + vehicleTracker.groupBy { it.vehicleData.nationId ?: "unknown" }.keys).toSet()

        for (nationId in allNationIds) {
            val nationName = playersByNation[nationId]?.firstOrNull()
                ?.let { com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(it)?.name }
                ?: vehicleTracker.firstOrNull { it.vehicleData.nationId == nationId }?.vehicleData.nationName
                ?: nationId

            val players = playersByNation[nationId] ?: emptyList()
            val vehicles = vehicleTracker.filter { it.vehicleData.nationId == nationId && it.isOperational }
            val vehicleScore = vehicles.sumOf { it.vehicleData.getScoreValue() }

            val presence = regionPresence.computeIfAbsent(nationId) { PresenceData(nationId, nationName) }
            presence.update(players.size, vehicles.size, vehicleScore)
            presence.totalPresenceSeconds += NavalWarfareConfig.presenceCheckIntervalSeconds

            // Atualizar última presença dos jogadores
            val playerMap = playerPresence.computeIfAbsent(regionId) { ConcurrentHashMap() }
            for (player in players) {
                playerMap["${nationId}_${player.uuid}"] = System.currentTimeMillis()
            }
        }

        // Remover nações sem presença
        regionPresence.entries.removeIf { (_, data) -> !data.hasPresence() }

        // Conceder pontos de presença
        awardPresencePoints(region)
    }

    /**
     * Concede pontos de presença baseados na presença atual.
     */
    private fun awardPresencePoints(region: AssaultRegion) {
        val regionId = region.id
        val now = System.currentTimeMillis()
        val lastReward = lastPresenceReward[regionId] ?: 0

        // Verificar intervalo mínimo entre recompensas
        if (now - lastReward < NavalWarfareConfig.presenceRewardIntervalSeconds * 1000L) {
            return
        }

        val regionPresence = presenceData[regionId] ?: return
        val scoringManager = ScoringManager.getInstance()

        for ((nationId, data) in regionPresence) {
            if (!data.hasPresence()) continue

            // Calcular pontos: base + bonus por veículos + bonus por streak
            var points = NavalWarfareConfig.pointsPerPresenceTick

            // Bonus por veículos (peso pelo valor do veículo)
            val vehicleBonus = (data.totalVehicleScore * NavalWarfareConfig.vehiclePresenceMultiplier).toInt()
            points += vehicleBonus

            // Bonus por streak de presença consecutiva
            val streakBonus = min(data.consecutiveTicks * NavalWarfareConfig.presenceStreakBonusPerTick, NavalWarfareConfig.maxPresenceStreakBonus)
            points += streakBonus

            // Aplicar multiplicador se for nação dona defendendo
            val isOwner = region.ownerNationId == nationId
            if (isOwner) {
                points = (points * NavalWarfareConfig.defenderPresenceMultiplier).toInt()
            }

            if (points > 0) {
                scoringManager.addPresencePoints(regionId, nationId, data.nationName, points)
            }
        }

        lastPresenceReward[regionId] = now
    }

    /**
     * Obtém dados de presença de uma região.
     */
    fun getRegionPresence(regionId: String): Map<String, PresenceData> {
        return presenceData[regionId]?.toMap() ?: emptyMap()
    }

    /**
     * Obtém nação com maior presença (para desempate).
     */
    fun getDominantNation(regionId: String): String? {
        return presenceData[regionId]?.maxByOrNull { (_, data) ->
            data.playerCount + data.vehicleCount * 2 + data.totalVehicleScore / 100
        }?.key
    }

    /**
     * Obtém contagem de jogadores de uma nação na região.
     */
    fun getNationPlayerCount(regionId: String, nationId: String): Int {
        return presenceData[regionId]?.get(nationId)?.playerCount ?: 0
    }

    /**
     * Obtém contagem de veículos de uma nação na região.
     */
    fun getNationVehicleCount(regionId: String, nationId: String): Int {
        return presenceData[regionId]?.get(nationId)?.vehicleCount ?: 0
    }

    /**
     * Verifica se uma nação tem presença na região.
     */
    fun hasNationPresence(regionId: String, nationId: String): Boolean {
        return presenceData[regionId]?.get(nationId)?.hasPresence() == true
    }

    /**
     * Registra presença de jogador (chamado no tick do jogador).
     */
    fun onPlayerTick(regionId: String, player: ServerPlayer) {
        val nation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(player)
        val nationId = nation?.id ?: "independent_${player.uuid}"

        val playerMap = playerPresence.computeIfAbsent(regionId) { ConcurrentHashMap() }
        playerMap["${nationId}_${player.uuid}"] = System.currentTimeMillis()
    }

    /**
     * Remove presença de jogador (logout/saída da região).
     */
    fun onPlayerLeaveRegion(regionId: String, player: ServerPlayer) {
        val nation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(player)
        val nationId = nation?.id ?: "independent_${player.uuid}"

        val playerMap = playerPresence[regionId] ?: return
        playerMap.remove("${nationId}_${player.uuid}")
    }

    /**
     * Força atualização de presença para uma nação (ex: veículo entrou/saiu).
     */
    fun forceUpdate(regionId: String, nationId: String, nationName: String, playerCount: Int, vehicleCount: Int, vehicleScore: Int) {
        val regionPresence = presenceData.computeIfAbsent(regionId) { ConcurrentHashMap() }
        val presence = regionPresence.computeIfAbsent(nationId) { PresenceData(nationId, nationName) }
        presence.update(playerCount, vehicleCount, vehicleScore)
    }

    /**
     * Reseta presença de uma região (novo assalto).
     */
    fun resetRegionPresence(regionId: String) {
        presenceData[regionId]?.clear()
        playerPresence[regionId]?.clear()
        lastPresenceReward.remove(regionId)
    }

    /**
     * Limpa dados de regiões inativas.
     */
    private fun cleanupInactiveRegions() {
        val manager = AssaultRegionManager.getInstance()
        val activeRegionIds = manager.getAllRegions()
            .filter { it.state == com.tlgd.navalwarfare.region.AssaultState.ACTIVE }
            .map { it.id }
            .toSet()

        presenceData.keys.filter { !activeRegionIds.contains(it) }.forEach { presenceData.remove(it) }
        playerPresence.keys.filter { !activeRegionIds.contains(it) }.forEach { playerPresence.remove(it) }
        lastPresenceReward.keys.filter { !activeRegionIds.contains(it) }.forEach { lastPresenceReward.remove(it) }
    }

    /**
     * Obtém estatísticas para HUD/GUI.
     */
    fun getPresenceStats(regionId: String): Map<String, Any> {
        val regionPresence = presenceData[regionId] ?: return emptyMap()
        return regionPresence.mapValues { (nationId, data) ->
            mapOf(
                "nationName" to data.nationName,
                "playerCount" to data.playerCount,
                "vehicleCount" to data.vehicleCount,
                "vehicleScore" to data.totalVehicleScore,
                "consecutiveTicks" to data.consecutiveTicks,
                "totalPresenceSeconds" to data.totalPresenceSeconds,
                "hasPresence" to data.hasPresence()
            )
        }
    }
}