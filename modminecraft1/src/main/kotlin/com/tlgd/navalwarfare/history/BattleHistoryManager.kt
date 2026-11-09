package com.tlgd.navalwarfare.history

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mojang.logging.LogUtils
import com.tlgd.navalwarfare.region.AssaultRegion
import com.tlgd.navalwarfare.region.AssaultRegionManager
import net.minecraft.server.level.ServerPlayer
import org.slf4j.Logger
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

/**
 * Gerenciador de histórico de batalhas e rankings.
 */
class BattleHistoryManager {

    companion object {
        private val LOGGER = com.mojang.logging.LogUtils.getLogger()
        private var INSTANCE: BattleHistoryManager? = null
        private val gson = GsonBuilder().setPrettyPrinting().create()
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun getInstance(): BattleHistoryManager {
            return INSTANCE ?: throw IllegalStateException("BattleHistoryManager nao inicializado!")
        }

        fun init() {
            INSTANCE = BattleHistoryManager()
            LOGGER.info("BattleHistoryManager inicializado")
        }
    }

    // Histórico de batalhas (em memória + arquivo)
    private val battles = CopyOnWriteArrayList<BattleRecord>()
    private val maxBattlesInMemory = 1000

    // Rankings (cache)
    private val nationRankings = ConcurrentHashMap<String, NationRanking>()
    private val playerRankings = ConcurrentHashMap<String, PlayerRanking>()
    private var lastRankingUpdate = 0L
    private val rankingCacheValidMs = 60000L // 1 minuto

    data class BattleRecord(
        val id: String, // UUID
        val regionId: String,
        val regionName: String,
        val startedAt: Long,
        val endedAt: Long,
        val durationSeconds: Long,
        val participatingNations: List<NationParticipation>,
        val winnerNationId: String?,
        val winnerNationName: String?,
        val finalScores: Map<String, Int>,
        val totalKills: Int,
        val totalVehiclesDestroyed: Int,
        val objectivesCompleted: List<ObjectiveCompletion>,
        val mvpPlayer: String?, // UUID do jogador com mais kills
        val mvpPlayerName: String?,
    ) {
        fun getDateString(): String = LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(startedAt), java.time.ZoneId.systemDefault()
        ).format(DATE_FORMAT)

        fun toJson(): Map<String, Any> {
            return mapOf(
                "id" to id,
                "regionId" to regionId,
                "regionName" to regionName,
                "startedAt" to startedAt,
                "endedAt" to endedAt,
                "durationSeconds" to durationSeconds,
                "date" to getDateString(),
                "participatingNations" to participatingNations.map { it.toJson() },
                "winnerNationId" to winnerNationId,
                "winnerNationName" to winnerNationName,
                "finalScores" to finalScores,
                "totalKills" to totalKills,
                "totalVehiclesDestroyed" to totalVehiclesDestroyed,
                "objectivesCompleted" to objectivesCompleted.map { it.toJson() },
                "mvpPlayer" to mvpPlayer,
                "mvpPlayerName" to mvpPlayerName
            )
        }

        companion object {
            fun fromJson(json: Map<String, Any>): BattleRecord {
                return BattleRecord(
                    id = json["id"] as String,
                    regionId = json["regionId"] as String,
                    regionName = json["regionName"] as String,
                    startedAt = json["startedAt"] as Long,
                    endedAt = json["endedAt"] as Long,
                    durationSeconds = json["durationSeconds"] as Long,
                    participatingNations = (json["participatingNations"] as List<Map<String, Any>>).map { NationParticipation.fromJson(it) },
                    winnerNationId = json["winnerNationId"] as? String,
                    winnerNationName = json["winnerNationName"] as? String,
                    finalScores = (json["finalScores"] as Map<String, Any>).mapValues { (k, v) -> k to v as Int },
                    totalKills = json["totalKills"] as Int,
                    totalVehiclesDestroyed = json["totalVehiclesDestroyed"] as Int,
                    objectivesCompleted = (json["objectivesCompleted"] as List<Map<String, Any>>).map { ObjectiveCompletion.fromJson(it) },
                    mvpPlayer = json["mvpPlayer"] as? String,
                    mvpPlayerName = json["mvpPlayerName"] as? String
                )
            }
        }
    }

    data class NationParticipation(
        val nationId: String,
        val nationName: String,
        val score: Int,
        val kills: Int,
        val vehiclesDestroyed: Int,
        val flagsCaptured: Int,
        val presenceSeconds: Long,
        val objectivesCompleted: Int,
        val placed: Int, // 1 = vencedor, 2 = segundo, etc.
    ) {
        fun toJson(): Map<String, Any> = mapOf(
            "nationId" to nationId,
            "nationName" to nationName,
            "score" to score,
            "kills" to kills,
            "vehiclesDestroyed" to vehiclesDestroyed,
            "flagsCaptured" to flagsCaptured,
            "presenceSeconds" to presenceSeconds,
            "objectivesCompleted" to objectivesCompleted,
            "placed" to placed
        )

        companion object {
            fun fromJson(json: Map<String, Any>): NationParticipation {
                return NationParticipation(
                    nationId = json["nationId"] as String,
                    nationName = json["nationName"] as String,
                    score = json["score"] as Int,
                    kills = json["kills"] as Int,
                    vehiclesDestroyed = json["vehiclesDestroyed"] as Int,
                    flagsCaptured = json["flagsCaptured"] as Int,
                    presenceSeconds = json["presenceSeconds"] as Long,
                    objectivesCompleted = json["objectivesCompleted"] as Int,
                    placed = json["placed"] as Int
                )
            }
        }
    }

    data class ObjectiveCompletion(
        val objectiveId: String,
        val objectiveName: String,
        val objectiveType: String,
        val completedByNationId: String,
        val completedByNationName: String,
        val pointsAwarded: Int,
        val completedAt: Long,
    ) {
        fun toJson(): Map<String, Any> = mapOf(
            "objectiveId" to objectiveId,
            "objectiveName" to objectiveName,
            "objectiveType" to objectiveType,
            "completedByNationId" to completedByNationId,
            "completedByNationName" to completedByNationName,
            "pointsAwarded" to pointsAwarded,
            "completedAt" to completedAt
        )

        companion object {
            fun fromJson(json: Map<String, Any>): ObjectiveCompletion {
                return ObjectiveCompletion(
                    objectiveId = json["objectiveId"] as String,
                    objectiveName = json["objectiveName"] as String,
                    objectiveType = json["objectiveType"] as String,
                    completedByNationId = json["completedByNationId"] as String,
                    completedByNationName = json["completedByNationName"] as String,
                    pointsAwarded = json["pointsAwarded"] as Int,
                    completedAt = json["completedAt"] as Long
                )
            }
        }
    }

    data class NationRanking(
        val nationId: String,
        val nationName: String,
        var totalBattles: Int = 0,
        var victories: Int = 0,
        var defeats: Int = 0,
        var totalScore: Long = 0,
        var totalKills: Long = 0,
        var totalVehiclesDestroyed: Long = 0,
        var regionsControlled: Int = 0,
        var currentStreak: Int = 0,
        var bestStreak: Int = 0,
        var lastBattleAt: Long = 0,
        var favoriteRegion: String? = null,
    ) {
        val winRate: Double get() = if (totalBattles > 0) victories.toDouble() / totalBattles else 0.0
        val averageScore: Double get() = if (totalBattles > 0) totalScore.toDouble() / totalBattles else 0.0

        fun toJson(): Map<String, Any> = mapOf(
            "nationId" to nationId,
            "nationName" to nationName,
            "totalBattles" to totalBattles,
            "victories" to victories,
            "defeats" to defeats,
            "winRate" to winRate,
            "totalScore" to totalScore,
            "averageScore" to averageScore,
            "totalKills" to totalKills,
            "totalVehiclesDestroyed" to totalVehiclesDestroyed,
            "regionsControlled" to regionsControlled,
            "currentStreak" to currentStreak,
            "bestStreak" to bestStreak,
            "lastBattleAt" to lastBattleAt
        )
    }

    data class PlayerRanking(
        val playerId: String,
        val playerName: String,
        val nationId: String?,
        val nationName: String?,
        var totalKills: Int = 0,
        var totalDeaths: Int = 0,
        var vehiclesDestroyed: Int = 0,
        var flagsCaptured: Int = 0,
        var battlesParticipated: Int = 0,
        var battlesWon: Int = 0,
        var totalScore: Long = 0,
        var mvpCount: Int = 0,
        var lastBattleAt: Long = 0,
    ) {
        val kdRatio: Double get() = if (totalDeaths > 0) totalKills.toDouble() / totalDeaths else totalKills.toDouble()
        val winRate: Double get() = if (battlesParticipated > 0) battlesWon.toDouble() / battlesParticipated else 0.0

        fun toJson(): Map<String, Any> = mapOf(
            "playerId" to playerId,
            "playerName" to playerName,
            "nationId" to nationId,
            "nationName" to nationName,
            "totalKills" to totalKills,
            "totalDeaths" to totalDeaths,
            "kdRatio" to kdRatio,
            "vehiclesDestroyed" to vehiclesDestroyed,
            "flagsCaptured" to flagsCaptured,
            "battlesParticipated" to battlesParticipated,
            "battlesWon" to battlesWon,
            "winRate" to winRate,
            "totalScore" to totalScore,
            "mvpCount" to mvpCount,
            "lastBattleAt" to lastBattleAt
        )
    }

    /**
     * Registra uma batalha finalizada.
     */
    fun recordBattle(
        region: AssaultRegion,
        assault: com.tlgd.navalwarfare.region.AssaultRegionManager.ActiveAssault,
        finalScores: Map<String, Int>,
        objectivesCompleted: List<ObjectiveCompletion>,
        mvpPlayerId: String?,
        mvpPlayerName: String?
    ) {
        val participatingNations = finalScores.entries.map { (nationId, score) ->
            val nation = com.tlgd.navalwarfare.nation.NationIntegration.getNationById(nationId)
            val nationName = nation?.name ?: nationId
            // Buscar stats detalhadas do ScoringManager
            val scoring = com.tlgd.navalwarfare.scoring.ScoringManager.getInstance().getScore(region.id, nationId)
            NationParticipation(
                nationId = nationId,
                nationName = nationName,
                score = score,
                kills = scoring?.playerKills ?: 0,
                vehiclesDestroyed = scoring?.vehicleDestroys ?: 0,
                flagsCaptured = scoring?.flagCaptures ?: 0,
                presenceSeconds = scoring?.presencePoints ?: 0,
                objectivesCompleted = objectivesCompleted.count { it.completedByNationId == nationId },
                placed = 0 // Será preenchido depois
            )
        }.sortedByDescending { it.score }.toList()

        // Definir colocações
        val placedNations = participatingNations.mapIndexed { index, p ->
            p.copy(placed = index + 1)
        }

        val winner = placedNations.firstOrNull()
        val battle = BattleRecord(
            id = assault.id.toString(),
            regionId = region.id,
            regionName = region.displayName,
            startedAt = assault.startedAt,
            endedAt = System.currentTimeMillis(),
            durationSeconds = (assault.endsAt - assault.startedAt) / 1000,
            participatingNations = placedNations,
            winnerNationId = winner?.nationId,
            winnerNationName = winner?.nationName,
            finalScores = finalScores,
            totalKills = placedNations.sumOf { it.kills },
            totalVehiclesDestroyed = placedNations.sumOf { it.vehiclesDestroyed },
            objectivesCompleted = objectivesCompleted,
            mvpPlayer = mvpPlayerId,
            mvpPlayerName = mvpPlayerName
        )

        // Adicionar ao histórico
        battles.add(0, battle)
        if (battles.size > maxBattlesInMemory) {
            battles.removeAt(battles.size - 1)
        }

        // Atualizar rankings
        updateRankings(battle)

        // Salvar em arquivo
        saveBattle(battle)

        LOGGER.info("Batalha registrada: {} em {} - Vencedor: {} ({})",
            battle.id, region.displayName, winner?.nationName ?: "Nenhum", winner?.nationId ?: "N/A")
    }

    private fun updateRankings(battle: BattleRecord) {
        // Atualizar ranking de nações
        for (participation in battle.participatingNations) {
            val ranking = nationRankings.computeIfAbsent(participation.nationId) {
                NationRanking(participation.nationId, participation.nationName)
            }
            ranking.totalBattles++
            ranking.totalScore += participation.score
            ranking.totalKills += participation.kills
            ranking.totalVehiclesDestroyed += participation.vehiclesDestroyed
            ranking.lastBattleAt = battle.endedAt

            if (participation.placed == 1) {
                ranking.victories++
                ranking.currentStreak++
                ranking.bestStreak = max(ranking.bestStreak, ranking.currentStreak)
            } else {
                ranking.defeats++
                ranking.currentStreak = 0
            }
        }

        // Atualizar ranking de jogadores (MVP)
        battle.mvpPlayer?.let { playerId ->
            val playerName = battle.mvpPlayerName ?: playerId
            val nation = battle.participatingNations.firstOrNull { it.nationId == com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNationByPlayerId(playerId)?.id }
            val ranking = playerRankings.computeIfAbsent(playerId) {
                PlayerRanking(playerId, playerName, nation?.nationId, nation?.nationName)
            }
            ranking.mvpCount++
            ranking.battlesWon++
            ranking.lastBattleAt = battle.endedAt
        }

        lastRankingUpdate = 0 // Invalidar cache
    }

    /**
     * Salva batalha em arquivo JSON.
     */
    private fun saveBattle(battle: BattleRecord) {
        try {
            val server = net.minecraft.server.MinecraftServer.getServer()
            val worldDir = server?.getWorldPath(net.minecraft.server.packs.resources.ServerResources.WORLD_DIR)?.toFile()
            val historyDir = worldDir?.let { File(it, "navalwarfare_history") }?.apply { mkdirs() }
            val file = historyDir?.let { File(it, "battle_${battle.id}.json") }
            file?.writeText(gson.toJson(battle.toJson()))
        } catch (e: Exception) {
            LOGGER.error("Erro ao salvar batalha: {}", e.message)
        }
    }

    /**
     * Carrega histórico do disco.
     */
    fun loadHistory() {
        try {
            val server = net.minecraft.server.MinecraftServer.getServer()
            val worldDir = server?.getWorldPath(net.minecraft.server.packs.resources.ServerResources.WORLD_DIR)?.toFile()
            val historyDir = worldDir?.let { File(it, "navalwarfare_history") }
            if (historyDir == null || !historyDir.exists()) return

            val files = historyDir.listFiles { _, name -> name.startsWith("battle_") && name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }
                ?.take(maxBattlesInMemory) ?: return

            battles.clear()
            nationRankings.clear()
            playerRankings.clear()

            for (file in files) {
                try {
                    val json = file.readText()
                    val map = gson.fromJson(json, Map<String, Any>::class.java)
                    val battle = BattleRecord.fromJson(map)
                    battles.add(battle)
                    updateRankings(battle)
                } catch (e: Exception) {
                    LOGGER.debug("Erro ao carregar batalha {}: {}", file.name, e.message)
                }
            }

            LOGGER.info("Historico carregado: {} batalhas", battles.size)
        } catch (e: Exception) {
            LOGGER.error("Erro ao carregar historico: {}", e.message)
        }
    }

    // ===== QUERIES =====

    fun getAllBattles(limit: Int = 100): List<BattleRecord> {
        return battles.take(limit)
    }

    fun getBattlesByRegion(regionId: String, limit: Int = 50): List<BattleRecord> {
        return battles.filter { it.regionId == regionId }.take(limit)
    }

    fun getBattlesByNation(nationId: String, limit: Int = 50): List<BattleRecord> {
        return battles.filter { it.participatingNations.any { it.nationId == nationId } }.take(limit)
    }

    fun getBattlesInPeriod(startTime: Long, endTime: Long): List<BattleRecord> {
        return battles.filter { it.startedAt >= startTime && it.startedAt <= endTime }
    }

    fun getBattle(battleId: String): BattleRecord? {
        return battles.firstOrNull { it.id == battleId }
    }

    // ===== RANKINGS =====

    fun getNationRankings(limit: Int = 50): List<NationRanking> {
        ensureRankingsLoaded()
        return nationRankings.values
            .sortedByDescending { it.victories }
            .thenByDescending { it.totalScore }
            .take(limit)
    }

    fun getPlayerRankings(limit: Int = 50): List<PlayerRanking> {
        ensureRankingsLoaded()
        return playerRankings.values
            .sortedByDescending { it.totalKills }
            .thenByDescending { it.totalScore }
            .take(limit)
    }

    fun getNationRanking(nationId: String): NationRanking? {
        ensureRankingsLoaded()
        return nationRankings[nationId]
    }

    fun getPlayerRanking(playerId: String): PlayerRanking? {
        ensureRankingsLoaded()
        return playerRankings[playerId]
    }

    fun getRegionStatistics(regionId: String): Map<String, Any> {
        val regionBattles = getBattlesByRegion(regionId)
        return mapOf(
            "totalBattles" to regionBattles.size,
            "mostVictoriousNation" to regionBattles
                .flatMap { it.participatingNations.filter { it.placed == 1 } }
                .groupingBy { it.nationId }
                .eachCount()
                .maxByOrNull { it.value }?.key,
            "averageDuration" to if (regionBattles.isNotEmpty()) regionBattles.averageOf { it.durationSeconds } else 0.0,
            "totalKills" to regionBattles.sumOf { it.totalKills },
            "totalVehiclesDestroyed" to regionBattles.sumOf { it.totalVehiclesDestroyed },
            "lastBattle" to regionBattles.firstOrNull()?.getDateString()
        )
    }

    private fun ensureRankingsLoaded() {
        val now = System.currentTimeMillis()
        if (now - lastRankingUpdate > rankingCacheValidMs && battles.isNotEmpty() && nationRankings.isEmpty()) {
            loadHistory()
            lastRankingUpdate = now
        }
    }

    // ===== EXPORT =====

    fun exportToCSV(file: File) {
        try {
            val csv = StringBuilder()
            csv.appendLine("ID,Region,Date,Duration,Winner,WinnerNationId,TotalKills,TotalVehicles,ParticipatingNations")
            for (battle in battles) {
                val nations = battle.participatingNations.joinToString(";") { "${it.nationName}(${it.score})" }
                csv.appendLine("${battle.id},${battle.regionName},${battle.getDateString()},${battle.durationSeconds},${battle.winnerNationName ?: "N/A"},${battle.winnerNationId ?: "N/A"},${battle.totalKills},${battle.totalVehiclesDestroyed},$nations")
            }
            file.writeText(csv.toString())
        } catch (e: Exception) {
            LOGGER.error("Erro ao exportar CSV: {}", e.message)
        }
    }

    fun exportToJSON(file: File) {
        try {
            val json = gson.toJson(battles.map { it.toJson() })
            file.writeText(json)
        } catch (e: Exception) {
            LOGGER.error("Erro ao exportar JSON: {}", e.message)
        }
    }
}