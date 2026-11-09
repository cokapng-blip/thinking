package com.tlgd.assault.region

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mojang.logging.LogUtils
import com.tlgd.assault.TLGAssaultMod
import com.tlgd.assault.config.TLGDAssaultConfig
import com.tlgd.assault.nation.NationIntegration
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.saveddata.SavedData
import org.slf4j.Logger
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.mutableListOf

class AssaultRegionManager : SavedData() {

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private var INSTANCE: AssaultRegionManager? = null
        private val DATA_NAME = "tlgd_assault_regions"
        val REGIONS_FILE = "tlgd_assault_regions.json"
        private val gson = GsonBuilder().setPrettyPrinting().create()

        fun init() {
            INSTANCE = AssaultRegionManager()
        }

        fun getInstance(): AssaultRegionManager {
            return INSTANCE ?: throw IllegalStateException("AssaultRegionManager não inicializado!")
        }

        fun getOrCreate(level: ServerLevel): AssaultRegionManager {
            return level.dataStorage.computeIfPresent(DATA_NAME) { nbt ->
                AssaultRegionManager.load(nbt, level.registryAccess())
            } ?: AssaultRegionManager().also { level.dataStorage.set(DATA_NAME, it) }
        }

        fun load(nbt: CompoundTag, registries: net.minecraft.core.HolderLookup.Provider): AssaultRegionManager {
            val manager = AssaultRegionManager()
            val regionsTag = nbt.getList("regions", 10) // TAG_COMPOUND
            for (i in 0 until regionsTag.size()) {
                val regionTag = regionsTag.getCompound(i)
                try {
                    val region = AssaultRegion.fromTag(regionTag, registries)
                    manager.regions[region.id] = region
                } catch (e: Exception) {
                    LOGGER.error("Erro ao carregar região: {}", e.message)
                }
            }
            manager.loadAttackWindowsFromConfig()
            LOGGER.info("Carregadas {} regiões de assalto", manager.regions.size)
            return manager
        }

        fun saveToFile(file: File) {
            val manager = getInstance()
            val json = gson.toJson(manager.regions.values.map { it.toJson() })
            file.writeText(json)
        }

        fun loadFromFile(file: File, registries: net.minecraft.core.HolderLookup.Provider) {
            if (!file.exists()) return
            val json = file.readText()
            val list = gson.fromJson(json, Array<RegionJson>::class.java).toList()
            val manager = getInstance()
            manager.regions.clear()
            for (regionJson in list) {
                val region = regionJson.toRegion(registries)
                manager.regions[region.id] = region
            }
            manager.loadAttackWindowsFromConfig()
            LOGGER.info("Carregadas {} regiões do arquivo JSON", manager.regions.size)
        }
    }

    private val regions = ConcurrentHashMap<String, AssaultRegion>()
    private val activeAssaults = ConcurrentHashMap<UUID, ActiveAssault>()
    private val playerRegionCache = ConcurrentHashMap<UUID, String>() // playerId -> regionId
    private var attackWindowTask: java.util.TimerTask? = null
    private val timer = Timer("TLGD-Assault-Timer", true)

    data class RegionJson(
        val id: String,
        val displayName: String,
        val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int,
        val dimension: String,
        val attackWindows: List<WindowJson>,
        val ownerNationId: String? = null,
        val ownerNationName: String? = null
    ) {
        data class WindowJson(
            val dayOfWeek: Int,
            val startTime: String, // HH:mm
            val durationMinutes: Int = 30
        )

        fun toRegion(registries: net.minecraft.core.HolderLookup.Provider): AssaultRegion {
            val bounds = RegionBounds(
                net.minecraft.core.BlockPos(minX, minY, minZ),
                net.minecraft.core.BlockPos(maxX, maxY, maxZ),
                ResourceLocation.parse(dimension)
            )
            val windows = attackWindows.map { w ->
                AttackWindow(DayOfWeek.values()[w.dayOfWeek], LocalTime.parse(w.startTime), w.durationMinutes)
            }
            return AssaultRegion(id, displayName, bounds, windows, ownerNationId, ownerNationName)
        }
    }

    fun toJson(): RegionJson {
        val bounds = this.bounds
        return RegionJson(
            id, displayName,
            bounds.min.x, bounds.min.y, bounds.min.z,
            bounds.max.x, bounds.max.y, bounds.max.z,
            bounds.dimension.toString(),
            attackWindows.map { w ->
                RegionJson.WindowJson(w.dayOfWeek.ordinal, w.startTime.toString(), w.durationMinutes)
            },
            ownerNationId, ownerNationName
        )
    }

    override fun save(nbt: CompoundTag): CompoundTag {
        val listTag = net.minecraft.nbt.ListTag()
        for (region in regions.values) {
            listTag.add(region.toTag(net.minecraft.core.HolderLookup.Provider.NULL))
        }
        nbt.put("regions", listTag)
        return nbt
    }

    // Region management
    fun createRegion(
        id: String,
        displayName: String,
        min: net.minecraft.core.BlockPos,
        max: net.minecraft.core.BlockPos,
        dimension: ResourceLocation,
        attackWindows: List<AttackWindow>
    ): AssaultRegion {
        val bounds = RegionBounds(min, max, dimension)
        val area = bounds.getArea()
        if (area < TLGDAssaultConfig.regionMinSize || area > TLGDAssaultConfig.regionMaxSize) {
            throw IllegalArgumentException("Tamanho da região fora dos limites: $area")
        }
        val region = AssaultRegion(id, displayName, bounds, attackWindows)
        regions[id] = region
        setDirty()
        LOGGER.info("Região criada: $id ($displayName)")
        return region
    }

    fun getRegion(id: String): AssaultRegion? = regions[id]

    fun getAllRegions(): Collection<AssaultRegion> = regions.values

    fun getRegionsInDimension(dimension: ResourceLocation): List<AssaultRegion> {
        return regions.values.filter { it.bounds.dimension == dimension }.toList()
    }

    fun getRegionAt(pos: net.minecraft.core.BlockPos, dimension: ResourceLocation): AssaultRegion? {
        return regions.values.firstOrNull { it.bounds.contains(pos, dimension) }
    }

    fun removeRegion(id: String): Boolean {
        val removed = regions.remove(id) != null
        if (removed) setDirty()
        return removed
    }

    // Attack window management
    private fun loadAttackWindowsFromConfig() {
        // Janelas padrão se não configuradas
        for (region in regions.values) {
            if (region.attackWindows.isEmpty()) {
                region.attackWindows.addAll(defaultWindows())
            }
        }
    }

    private fun defaultWindows(): List<AttackWindow> {
        return listOf(
            AttackWindow(DayOfWeek.SATURDAY, LocalTime.of(20, 0), TLGDAssaultConfig.defaultWindowDurationMinutes),
            AttackWindow(DayOfWeek.SUNDAY, LocalTime.of(15, 0), TLGDAssaultConfig.defaultWindowDurationMinutes)
        )
    }

    fun setAttackWindows(regionId: String, windows: List<AttackWindow>) {
        regions[regionId]?.attackWindows?.clear()
        regions[regionId]?.attackWindows?.addAll(windows)
        regions[regionId]?.nextWindowIndex = 0
        setDirty()
    }

    // Assault lifecycle
    fun startAssault(regionId: String, player: ServerPlayer): Boolean {
        val region = regions[regionId] ?: return false
        val currentWindow = region.getCurrentWindow() ?: return false

        if (!currentWindow.isActiveNow()) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNão é horário de janela de ataque!"))
            return false
        }

        if (region.state == AssaultState.ACTIVE) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cAssalto já em andamento!"))
            return false
        }

        // Verificar nação do jogador
        val nation = NationIntegration.getPlayerNation(player)
        if (nation == null) {
            if (TLGDAssaultConfig.requireNationForAssault) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cVocê precisa estar em uma nação!"))
                return false
            }
        } else if (!NationIntegration.hasRank(player, TLGDAssaultConfig.requiredNationRank)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cCargo insuficiente! Precisa: ${TLGDAssaultConfig.requiredNationRank}"))
            return false
        }

        // Verificar jogadores online
        val onlinePlayers = player.server.getPlayerCount()
        if (onlinePlayers < TLGDAssaultConfig.minPlayersToStart) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cJogadores online insuficientes ($onlinePlayers/${TLGDAssaultConfig.minPlayersToStart})!"))
            return false
        }

        val assaultId = UUID.randomUUID()
        val activeAssault = ActiveAssault(
            id = assaultId,
            regionId = regionId,
            startedAt = System.currentTimeMillis(),
            endsAt = System.currentTimeMillis() + (currentWindow.durationMinutes * 60 * 1000L),
            initiatorNationId = nation?.id ?: "independent",
            initiatorNationName = nation?.name ?: "Independentes",
            participants = mutableMapOf()
        )

        activeAssaults[assaultId] = activeAssault
        region.state = AssaultState.ACTIVE
        region.activeAssaultId = assaultId
        region.scores.clear()

        // Anunciar global
        announceGlobal("§c§lASSALTO INICIADO§r §7na região §e${region.displayName}§7 por §e${activeAssault.initiatorNationName}§7!")
        playGlobalSound(TLGDAssaultConfig.announcementSound)

        setDirty()
        scheduleAssaultEnd(assaultId, currentWindow.durationMinutes * 60 * 1000L)
        return true
    }

    fun endAssault(assaultId: UUID) {
        val assault = activeAssaults.remove(assaultId) ?: return
        val region = regions[assault.regionId] ?: return

        val winner = region.getWinner()
        if (winner != null) {
            region.ownerNationId = winner.nationId
            region.ownerNationName = winner.nationName
            region.lastCaptureTime = System.currentTimeMillis()
            region.state = AssaultState.PEACEFUL

            announceGlobal("§a§lASSALTO FINALIZADO§r §7- §e${winner.nationName}§7 capturou §e${region.displayName}§7 com §e${winner.score}§7 pontos!")
            playGlobalSound("minecraft:entity.player.levelup")

            // Agendar recompensas diárias
            scheduleDailyRewards(region)
        } else {
            region.state = AssaultState.INACTIVE
            region.ownerNationId = null
            region.ownerNationName = null
            announceGlobal("§7Assalto em §e${region.displayName}§7 terminou sem vencedor.")
        }

        region.activeAssaultId = null
        region.advanceWindow()
        setDirty()
    }

    private fun scheduleAssaultEnd(assaultId: UUID, delayMs: Long) {
        timer.schedule(object : TimerTask() {
            override fun run() {
                endAssault(assaultId)
            }
        }, delayMs)
    }

    private fun scheduleDailyRewards(region: AssaultRegion) {
        // Implementar: agendar task diária para dar recompensas
    }

    // Player tracking
    fun onPlayerTick(player: ServerPlayer) {
        val region = getRegionAt(player.blockPosition(), player.level.dimension())
        val playerId = player.uuid

        if (region != null && region.state == AssaultState.ACTIVE) {
            val assault = region.activeAssaultId?.let { activeAssaults[it] } ?: return
            val nation = NationIntegration.getPlayerNation(player)
            val nationId = nation?.id ?: "independent_${playerId}"
            val nationName = nation?.name ?: player.name.getString()

            // Atualizar pontuação de presença
            assault.participants.computeIfAbsent(nationId) { key ->
                ParticipantData(nationId, nationName, playerId)
            }.updatePresence(playerId)

            playerRegionCache[playerId] = region.id
        } else {
            playerRegionCache.remove(playerId)
        }
    }

    fun onPlayerDeath(victim: ServerPlayer, attacker: ServerPlayer?) {
        val regionId = playerRegionCache[victim.uuid] ?: return
        val region = regions[regionId] ?: return
        val assault = region.activeAssaultId?.let { activeAssaults[it] } ?: return

        val victimNation = NationIntegration.getPlayerNation(victim)?.id ?: "independent_${victim.uuid}"
        val attackerNation = attacker?.let { NationIntegration.getPlayerNation(it)?.id ?: "independent_${it.uuid}" }

        if (attackerNation != null && attackerNation != victimNation) {
            val score = region.scores.computeIfAbsent(attackerNation) { k ->
                val nation = NationIntegration.getNationById(k) ?: return@computeIfAbsent NationScore(k, k, 0, 0, 0, 0, 0, 0)
                NationScore(k, nation.name)
            }
            score.addKill()

            // Atualizar participant
            assault.participants[attackerNation]?.kills++
        }
    }

    fun onVehicleDestroyed(vehicleEntity: net.minecraft.world.entity.Entity, attacker: ServerPlayer?) {
        val region = getRegionAt(vehicleEntity.blockPosition(), vehicleEntity.level.dimension())
            ?: return
        if (region.state != AssaultState.ACTIVE) return

        val assault = region.activeAssaultId?.let { activeAssaults[it] } ?: return
        val attackerNation = attacker?.let { NationIntegration.getPlayerNation(it)?.id }

        if (attackerNation != null) {
            val score = region.scores.computeIfAbsent(attackerNation) { k ->
                val nation = NationIntegration.getNationById(k) ?: return@computeIfAbsent NationScore(k, k, 0, 0, 0, 0, 0, 0)
                NationScore(k, nation.name)
            }
            score.addVehicleDestroyed()
            assault.participants[attackerNation]?.vehiclesDestroyed++
        }
    }

    fun onFlagCapture(player: ServerPlayer, flagPos: net.minecraft.core.BlockPos) {
        val region = getRegionAt(flagPos, player.level.dimension()) ?: return
        if (region.state != AssaultState.ACTIVE) return

        val assault = region.activeAssaultId?.let { activeAssaults[it] } ?: return
        val nation = NationIntegration.getPlayerNation(player)?.id ?: "independent_${player.uuid}"

        val score = region.scores.computeIfAbsent(nation) { k ->
            val n = NationIntegration.getNationById(k) ?: return@computeIfAbsent NationScore(k, k, 0, 0, 0, 0, 0, 0)
            NationScore(k, n.name)
        }
        score.addFlagCapture()
        assault.participants[nation]?.flagsCaptured++
    }

    // Helpers
    private fun announceGlobal(message: String) {
        TLGAssaultMod.LOGGER.info(message)
        // Implementar broadcast para todos os players
    }

    private fun playGlobalSound(sound: String) {
        // Implementar tocar som global
    }

    fun getActiveAssault(regionId: String): ActiveAssault? {
        return regions[regionId]?.activeAssaultId?.let { activeAssaults[it] }
    }

    fun isPlayerInAssaultRegion(player: ServerPlayer): Boolean {
        return playerRegionCache.containsKey(player.uuid)
    }
}

data class ActiveAssault(
    val id: UUID,
    val regionId: String,
    val startedAt: Long,
    val endsAt: Long,
    val initiatorNationId: String,
    val initiatorNationName: String,
    val participants: MutableMap<String, ParticipantData> = mutableMapOf()
) {
    fun getRemainingTime(): Long = max(0, endsAt - System.currentTimeMillis())
    fun isActive(): Boolean = System.currentTimeMillis() < endsAt
}

data class ParticipantData(
    val nationId: String,
    val nationName: String,
    val firstPlayerId: UUID,
    var kills: Int = 0,
    var vehiclesDestroyed: Int = 0,
    var flagsCaptured: Int = 0,
    var totalPresenceSeconds: Long = 0,
    private val playerPresence: MutableMap<UUID, Long> = mutableMapOf()
) {
    fun updatePresence(playerId: UUID) {
        val now = System.currentTimeMillis()
        val last = playerPresence[playerId] ?: now
        val diff = (now - last) / 1000
        if (diff > 0) {
            totalPresenceSeconds += diff
            playerPresence[playerId] = now
        }
    }
}