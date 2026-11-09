package com.tlgd.navalwarfare.region

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mojang.logging.LogUtils
import com.tlgd.navalwarfare.TLGAssaultMod
import com.tlgd.navalwarfare.config.NavalWarfareConfig
import com.tlgd.navalwarfare.network.NetworkHandler
import com.tlgd.navalwarfare.notification.NotificationManager
import com.tlgd.navalwarfare.scoring.ScoringManager
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import org.slf4j.Logger
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gerenciador de regiões de assalto e janelas de ataque.
 */
class AssaultRegionManager {

    companion object {
        private val LOGGER = LogUtils.getLogger()
        private var INSTANCE: AssaultRegionManager? = null
        private val gson = GsonBuilder().setPrettyPrinting().create()

        fun getInstance(): AssaultRegionManager {
            return INSTANCE ?: throw IllegalStateException("AssaultRegionManager nao inicializado!")
        }

        fun init() {
            INSTANCE = AssaultRegionManager()
            LOGGER.info("AssaultRegionManager inicializado")
        }
    }

    private val regions = ConcurrentHashMap<String, AssaultRegion>()
    private val activeAssaults = ConcurrentHashMap<String, ActiveAssault>()

    // Scheduler para verificação de janelas
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var schedulerFuture: ScheduledFuture<*>? = null
    private val isRunning = AtomicBoolean(false)

    data class ActiveAssault(
        val regionId: String,
        val attackerNationId: String,
        val id: String = UUID.randomUUID().toString(),
        var startedAt: Long = System.currentTimeMillis(),
        var endsAt: Long = 0,
        var state: AssaultState = AssaultState.PREPARING,
        val scores: MutableMap<String, Int> = ConcurrentHashMap(),
        var currentWindow: AssaultRegion.AttackWindow? = null,
    )

    private fun saveAll() {
        try {
            val server = MinecraftServer.getServer()
            val worldDir = server?.getWorldPath(net.minecraft.server.packs.resources.ServerResources.WORLD_DIR)?.toFile()
            val regionsDir = worldDir?.let { File(it, "navalwarfare_regions") }?.apply { mkdirs() }
            for (region in regions.values) {
                val file = regionsDir?.let { File(it, "${region.id}.json") }
                file?.writeText(region.toJson())
            }
        } catch (e: Exception) {
            LOGGER.error("Erro ao salvar regioes: {}", e.message)
        }
    }

    fun loadFromDisk(server: MinecraftServer) {
        try {
            val worldDir = server.getWorldPath(net.minecraft.server.packs.resources.ServerResources.WORLD_DIR).toFile()
            val regionsDir = File(worldDir, "navalwarfare_regions")
            if (!regionsDir.exists()) return

            val files = regionsDir.listFiles { _, name -> name.endsWith(".json") } ?: return
            for (file in files) {
                try {
                    val json = file.readText()
                    val region = AssaultRegion.fromJson(json)
                    regions[region.id] = region
                } catch (e: Exception) {
                    LOGGER.debug("Erro ao carregar regiao {}: {}", file.name, e.message)
                }
            }
            LOGGER.info("Carregadas ${regions.size} regioes do disco")
        } catch (e: Exception) {
            LOGGER.error("Erro ao carregar regioes: {}", e.message)
        }
    }

    fun addRegion(region: AssaultRegion) {
        if (regions.size >= NavalWarfareConfig.maxRegionsPerDimension.get()) {
            // Verificar por dimensão
            val dimCount = regions.values.count { it.bounds.dimension == region.bounds.dimension }
            if (dimCount >= NavalWarfareConfig.maxRegionsPerDimension.get()) {
                throw IllegalStateException("Limite de regioes por dimensao atingido")
            }
        }
        regions[region.id] = region
        saveAll()
    }

    fun removeRegion(id: String): Boolean {
        val removed = regions.remove(id) != null
        if (removed) saveAll()
        return removed
    }

    fun getRegion(id: String): AssaultRegion? {
        return regions[id]
    }

    fun getAllRegions(): List<AssaultRegion> {
        return regions.values.toList()
    }

    fun getRegionsInDimension(dimension: ResourceLocation): List<AssaultRegion> {
        return regions.values.filter { it.bounds.dimension == dimension }.toList()
    }

    fun getRegionAt(pos: BlockPos): AssaultRegion? {
        return regions.values.firstOrNull { it.contains(pos) }
    }

    fun getRegionsNear(pos: BlockPos, radius: Int): List<AssaultRegion> {
        return regions.values.filter { region ->
            val center = region.getCenter()
            val dx = center.x - pos.x
            val dz = center.z - pos.z
            (dx * dx + dz * dz) <= (radius * radius)
        }.toList()
    }

    // ===== ASSALTO =====

    fun startAssault(regionId: String, attackerNationId: String): Boolean {
        val region = regions[regionId] ?: return false
        val server = MinecraftServer.getServer() ?: return false

        // Verificar se já há assalto ativo
        if (activeAssaults.containsKey(regionId)) return false

        // Verificar janela
        val nextWindow = region.getNextWindow() ?: return false
        if (!nextWindow.isNow()) {
            // Verificar se está no período de preparação (5 min antes)
            val timeUntil = nextWindow.getNextOccurrence() - System.currentTimeMillis()
            if (timeUntil > 5 * 60 * 1000) return false
        }

        // Verificar jogadores online mínimos
        val onlinePlayers = server.getPlayerList().getPlayers().size
        if (onlinePlayers < NavalWarfareConfig.minPlayersToStart.get()) return false

        // Verificar cooldown
        val cooldownMs = (NavalWarfareConfig.windowCooldownHours.get().toLong() * 60 * 60 * 1000)
        if (System.currentTimeMillis() - region.lastCaptureTime < cooldownMs) return false

        // Criar assalto
        val assault = ActiveAssault(
            regionId = regionId,
            attackerNationId = attackerNationId,
            endsAt = System.currentTimeMillis() + (nextWindow.durationMinutes * 60 * 1000L),
            currentWindow = nextWindow
        )

        activeAssaults[regionId] = assault
        region.state = AssaultState.PREPARING

        // Notificar
        NotificationManager.getInstance().notifyAssaultStarted(region.displayName, attackerNationId)

        // Agendar transição para ACTIVE após 5 min
        scheduler.schedule({
            transitionToActive(regionId)
        }, 5, TimeUnit.MINUTES)

        // Agendar fim do assalto
        scheduler.schedule({
            endAssault(regionId)
        }, nextWindow.durationMinutes.toLong(), TimeUnit.MINUTES)

        saveAll()
        return true
    }

    private fun transitionToActive(regionId: String) {
        val assault = activeAssaults[regionId] ?: return
        val region = regions[regionId] ?: return

        assault.state = AssaultState.ACTIVE
        region.state = AssaultState.ACTIVE

        // Iniciar scoring
        ScoringManager.getInstance().startAssault(regionId)

        // Notificar
        NotificationManager.getInstance().notifyAssaultActive(region.displayName)

        // Enviar HUD para jogadores na região
        sendAssaultHUDToRegion(regionId)

        saveAll()
    }

    fun endAssault(regionId: String) {
        val assault = activeAssaults.remove(regionId) ?: return
        val region = regions[regionId] ?: return

        // Calcular vencedor
        val scores = ScoringManager.getInstance().getAllScores(regionId)
        val winner = scores.maxByOrNull { it.value }?.key

        if (winner != null) {
            region.ownerNationId = winner
            val nation = com.tlgd.navalwarfare.nation.NationIntegration.getNationById(winner)
            region.ownerNationName = nation?.name
            region.lastCaptureTime = System.currentTimeMillis()
            region.state = AssaultState.PEACEFUL

            // Dar recompensas
            com.tlgd.navalwarfare.reward.RewardManager.getInstance().giveCaptureRewards(regionId, winner)

            // Registrar batalha
            val objectives = com.tlgd.navalwarfare.objective.ObjectiveManager.getInstance().getRegionObjectives(regionId)
            val objCompletions = objectives.filter { it.state == com.tlgd.navalwarfare.objective.Objective.State.COMPLETED }
                .map { obj ->
                    com.tlgd.navalwarfare.history.BattleHistoryManager.ObjectiveCompletion(
                        objectiveId = obj.id,
                        objectiveName = obj.displayName,
                        objectiveType = obj.javaClass.simpleName.replace("Objective", "").lowercase(),
                        completedByNationId = winner,
                        completedByNationName = region.ownerNationName ?: winner,
                        pointsAwarded = obj.rewardPoints,
                        completedAt = System.currentTimeMillis()
                    )
                }

            val mvp = ScoringManager.getInstance().getMVP(regionId)
            val mvpName = mvp?.let { com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNameByUUID(it) }

            com.tlgd.navalwarfare.history.BattleHistoryManager.getInstance().recordBattle(
                region = region,
                assault = assault,
                finalScores = scores,
                objectivesCompleted = objCompletions,
                mvpPlayerId = mvp,
                mvpPlayerName = mvpName
            )

            NotificationManager.getInstance().notifyVictory(region.displayName, region.ownerNationName ?: winner, scores[winner] ?: 0)
        } else {
            region.state = AssaultState.PEACEFUL
            NotificationManager.getInstance().notifyAssaultEnded(region.displayName)
        }

        // Avançar janela
        region.advanceWindow()

        // Cooldown
        region.state = AssaultState.COOLDOWN
        scheduler.schedule({
            region.state = AssaultState.PEACEFUL
            saveAll()
        }, NavalWarfareConfig.windowCooldownHours.get().toLong(), TimeUnit.HOURS)

        // Limpar scoring
        ScoringManager.getInstance().endAssault(regionId)

        saveAll()
    }

    fun forceEndAssault(regionId: String) {
        val assault = activeAssaults.remove(regionId)
        val region = regions[regionId] ?: return

        ScoringManager.getInstance().endAssault(regionId)
        region.state = AssaultState.COOLDOWN

        NotificationManager.getInstance().notifyAssaultForceEnded(region.displayName)
        saveAll()
    }

    fun getActiveAssault(regionId: String): ActiveAssault? {
        return activeAssaults[regionId]
    }

    fun getAllActiveAssaults(): List<ActiveAssault> {
        return activeAssaults.values.toList()
    }

    // ===== PLAYER EVENTS =====

    fun onPlayerJoin(player: ServerPlayer) {
        // Verificar se entrou em região com assalto ativo
        val region = getRegionAt(player.blockPosition())
        region?.let { r ->
            val assault = activeAssaults[r.id]
            if (assault != null && assault.state == AssaultState.ACTIVE) {
                sendAssaultHUDToPlayer(player, r.id)
            }
        }
    }

    fun onPlayerLeave(player: ServerPlayer) {
        // Nada especial por enquanto
    }

    // ===== HUD NETWORK =====

    private fun sendAssaultHUDToRegion(regionId: String) {
        val server = MinecraftServer.getServer() ?: return
        val region = regions[regionId] ?: return
        val assault = activeAssaults[regionId] ?: return

        for (player in server.getPlayerList().getPlayers()) {
            if (region.contains(player.blockPosition())) {
                sendAssaultHUDToPlayer(player, regionId)
            }
        }
    }

    private fun sendAssaultHUDToPlayer(player: ServerPlayer, regionId: String) {
        val region = regions[regionId] ?: return
        val assault = activeAssaults[regionId] ?: return
        val scores = ScoringManager.getInstance().getAllScores(regionId)

        val leaderboard = scores.entries.map { (nationId, score) ->
            val nation = com.tlgd.navalwarfare.nation.NationIntegration.getNationById(nationId)
            val nationName = nation?.name ?: nationId
            val details = ScoringManager.getInstance().getScore(regionId, nationId)
            NetworkHandler.LeaderboardEntry(
                nationName = nationName,
                score = score,
                kills = details?.playerKills ?: 0,
                vehiclesDestroyed = details?.vehicleDestroys ?: 0,
                flagsCaptured = details?.flagCaptures ?: 0,
                presencePoints = details?.presencePoints ?: 0,
                objectivePoints = details?.objectivePoints ?: 0,
                isPlayerNation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNationByPlayer(player)?.id == nationId
            )
        }.sortedByDescending { it.score }.toList()

        val objectives = com.tlgd.navalwarfare.objective.ObjectiveManager.getInstance()
            .getRegionObjectives(regionId)
            .map { obj ->
                NetworkHandler.ObjectiveInfo(
                    id = obj.id,
                    name = obj.displayName,
                    type = obj.javaClass.simpleName.replace("Objective", "").lowercase(),
                    state = when (obj.state) {
                        com.tlgd.navalwarfare.objective.Objective.State.PENDING -> 0
                        com.tlgd.navalwarfare.objective.Objective.State.ACTIVE -> 1
                        com.tlgd.navalwarfare.objective.Objective.State.COMPLETED -> 2
                        com.tlgd.navalwarfare.objective.Objective.State.FAILED -> 3
                    },
                    progress = obj.progress.toFloat(),
                    requiredForVictory = obj.isRequiredForVictory,
                    rewardPoints = obj.rewardPoints,
                    controllingNation = obj.controllingNationId,
                    timeRemaining = obj.timeRemainingSeconds
                )
            }

        val playerNation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNationByPlayer(player)
        val playerRank = playerNation?.let { nationId ->
            scores.entries.sortedByDescending { it.value }.indexOfFirst { it.key == nationId } + 1
        }?.let { "#$it" }

        val remainingSeconds = max(0, (assault.endsAt - System.currentTimeMillis()) / 1000)

        val packet = NetworkHandler.AssaultHUDPacket(
            regionId = region.id,
            regionName = region.displayName,
            remainingSeconds = remainingSeconds.toInt(),
            state = when (assault.state) {
                AssaultState.INACTIVE -> 0
                AssaultState.PEACEFUL -> 1
                AssaultState.PREPARING -> 2
                AssaultState.ACTIVE -> 3
                AssaultState.COOLDOWN -> 4
            },
            leaderboard = leaderboard,
            activeObjectives = objectives,
            playerNationRank = playerRank
        )

        NetworkHandler.sendAssaultHUD(player, packet)
    }

    // ===== SCHEDULER =====

    fun startWindowScheduler(server: MinecraftServer) {
        if (isRunning.getAndSet(true)) return

        schedulerFuture = scheduler.scheduleAtFixedRate({
            checkWindows(server)
        }, 0, 30, TimeUnit.SECONDS)

        LOGGER.info("Window scheduler iniciado")
    }

    fun stopScheduler() {
        if (!isRunning.getAndSet(false)) return
        schedulerFuture?.cancel(false)
        LOGGER.info("Window scheduler parado")
    }

    private fun checkWindows(server: MinecraftServer) {
        val now = System.currentTimeMillis()

        for (region in regions.values) {
            // Pular se já tem assalto ativo
            if (activeAssaults.containsKey(region.id)) continue

            val nextWindow = region.getNextWindow() ?: continue
            val windowStart = nextWindow.getNextOccurrence()
            val timeUntil = windowStart - now

            // 30 min warning
            if (timeUntil <= 30 * 60 * 1000 && timeUntil > 29 * 60 * 1000 && NavalWarfareConfig.assaultWarning30Min.get()) {
                NotificationManager.getInstance().notifyAssaultWarning(region.displayName, 30)
                region.state = AssaultState.PREPARING
            }

            // 5 min warning
            if (timeUntil <= 5 * 60 * 1000 && timeUntil > 4 * 60 * 1000 && NavalWarfareConfig.assaultWarning5Min.get()) {
                NotificationManager.getInstance().notifyAssaultWarning(region.displayName, 5)
            }

            // Auto-iniciar se na janela
            if (nextWindow.isNow() && region.state != AssaultState.ACTIVE) {
                // Tentar iniciar com nação dona (defesa) ou primeira nação disponível
                val defenderNation = region.ownerNationId
                if (defenderNation != null) {
                    startAssault(region.id, defenderNation)
                }
            }
        }
    }
}