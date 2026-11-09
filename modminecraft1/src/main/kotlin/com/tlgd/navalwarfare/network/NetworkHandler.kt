package com.tlgd.navalwarfare.network

import com.tlgd.navalwarfare.TLGAssaultMod
import com.tlgd.navalwarfare.map.MapIntegration
import com.tlgd.navalwarfare.objective.Objective
import com.tlgd.navalwarfare.region.AssaultRegion
import com.tlgd.navalwarfare.region.AssaultRegionManager
import com.tlgd.navalwarfare.vehicle.VehicleData
import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import java.util.UUID

object NetworkHandler {

    const val CHANNEL_NAME = "navalwarfare:main"
    const val PROTOCOL_VERSION = 1

    fun init() {
        // Packets registrados via @Mod.EventBusSubscriber
    }

    // ===== Server -> Client Packets =====

    // HUD do assalto
    record class AssaultHUDPacket(
        val regionId: String,
        val regionName: String,
        val remainingSeconds: Int,
        val state: Byte, // 0=inactive, 1=preparing, 2=active, 3=cooldown
        val leaderboard: List<LeaderboardEntry>,
        val activeObjectives: List<ObjectiveInfo>,
        val playerNationRank: String?, // Rank do jogador na nação
    ) : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "assault_hud")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AssaultHUDPacket> = StreamCodec.composite(
                StreamCodec.STRING, AssaultHUDPacket::regionId,
                StreamCodec.STRING, AssaultHUDPacket::regionName,
                StreamCodec.INT, AssaultHUDPacket::remainingSeconds,
                StreamCodec.BYTE, AssaultHUDPacket::state,
                LeaderboardEntry.STREAM_CODEC.apply(StreamCodec.list()), AssaultHUDPacket::leaderboard,
                ObjectiveInfo.STREAM_CODEC.apply(StreamCodec.list()), AssaultHUDPacket::activeObjectives,
                StreamCodec.STRING, AssaultHUDPacket::playerNationRank,
                ::AssaultHUDPacket
            )
        }
    }

    record class LeaderboardEntry(
        val nationName: String,
        val score: Int,
        val kills: Int,
        val vehiclesDestroyed: Int,
        val flagsCaptured: Int,
        val presencePoints: Int,
        val objectivePoints: Int,
        val isPlayerNation: Boolean,
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, LeaderboardEntry> = StreamCodec.composite(
                StreamCodec.STRING, LeaderboardEntry::nationName,
                StreamCodec.INT, LeaderboardEntry::score,
                StreamCodec.INT, LeaderboardEntry::kills,
                StreamCodec.INT, LeaderboardEntry::vehiclesDestroyed,
                StreamCodec.INT, LeaderboardEntry::flagsCaptured,
                StreamCodec.INT, LeaderboardEntry::presencePoints,
                StreamCodec.INT, LeaderboardEntry::objectivePoints,
                StreamCodec.BOOL, LeaderboardEntry::isPlayerNation,
                ::LeaderboardEntry
            )
        }
    }

    record class ObjectiveInfo(
        val id: String,
        val name: String,
        val type: String,
        val state: Byte, // 0=pending, 1=active, 2=completed, 3=failed
        val progress: Float, // 0.0 a 1.0
        val requiredForVictory: Boolean,
        val rewardPoints: Int,
        val controllingNation: String?,
        val timeRemaining: Int, // segundos
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ObjectiveInfo> = StreamCodec.composite(
                StreamCodec.STRING, ObjectiveInfo::id,
                StreamCodec.STRING, ObjectiveInfo::name,
                StreamCodec.STRING, ObjectiveInfo::type,
                StreamCodec.BYTE, ObjectiveInfo::state,
                StreamCodec.FLOAT, ObjectiveInfo::progress,
                StreamCodec.BOOL, ObjectiveInfo::requiredForVictory,
                StreamCodec.INT, ObjectiveInfo::rewardPoints,
                StreamCodec.STRING, ObjectiveInfo::controllingNation,
                StreamCodec.INT, ObjectiveInfo::timeRemaining,
                ::ObjectiveInfo
            )
        }
    }

    // Marcadores de região para mapa
    record class RegionMarkersPacket(
        val regions: List<RegionMarkerData>
    ) : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "region_markers")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RegionMarkersPacket> = StreamCodec.composite(
                RegionMarkerData.STREAM_CODEC.apply(StreamCodec.list()), RegionMarkersPacket::regions,
                ::RegionMarkersPacket
            )
        }
    }

    record class RegionMarkerData(
        val id: String,
        val name: String,
        val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int,
        val dimension: String,
        val ownerNation: String?,
        val state: Byte, // 0=inactive, 1=peaceful, 2=preparing, 3=active, 4=cooldown
        val nextWindow: String?, // "SATURDAY 20:00 (30min)"
        val color: Int,
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RegionMarkerData> = StreamCodec.composite(
                StreamCodec.STRING, RegionMarkerData::id,
                StreamCodec.STRING, RegionMarkerData::name,
                StreamCodec.INT, RegionMarkerData::minX,
                StreamCodec.INT, RegionMarkerData::minY,
                StreamCodec.INT, RegionMarkerData::minZ,
                StreamCodec.INT, RegionMarkerData::maxX,
                StreamCodec.INT, RegionMarkerData::maxY,
                StreamCodec.INT, RegionMarkerData::maxZ,
                StreamCodec.STRING, RegionMarkerData::dimension,
                StreamCodec.STRING, RegionMarkerData::ownerNation,
                StreamCodec.BYTE, RegionMarkerData::state,
                StreamCodec.STRING, RegionMarkerData::nextWindow,
                StreamCodec.INT, RegionMarkerData::color,
                ::RegionMarkerData
            )
        }
    }

    // Anúncio global
    record class AnnouncementPacket(
        val message: String,
        val sound: String,
        val isGlobal: Boolean,
        val titleText: String?,
        val subtitleText: String?,
        val actionBarText: String?,
        val showToast: Boolean,
        val toastIcon: String?,
    ) : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "announcement")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AnnouncementPacket> = StreamCodec.composite(
                StreamCodec.STRING, AnnouncementPacket::message,
                StreamCodec.STRING, AnnouncementPacket::sound,
                StreamCodec.BOOL, AnnouncementPacket::isGlobal,
                StreamCodec.STRING, AnnouncementPacket::titleText,
                StreamCodec.STRING, AnnouncementPacket::subtitleText,
                StreamCodec.STRING, AnnouncementPacket::actionBarText,
                StreamCodec.BOOL, AnnouncementPacket::showToast,
                StreamCodec.STRING, AnnouncementPacket::toastIcon,
                ::AnnouncementPacket
            )
        }
    }

    // Notificação de assalto (iniciado, terminado, vencedor)
    record class AssaultNotificationPacket(
        val type: Byte, // 0=started, 1=ended, 2=winner, 3=objective_completed, 4=objective_failed, 5=capital_destroyed
        val regionId: String,
        val regionName: String,
        val nationName: String?,
        val winnerName: String?,
        val score: Int,
        val extraData: String?, // JSON com dados extras
    ) : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "assault_notification")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AssaultNotificationPacket> = StreamCodec.composite(
                StreamCodec.BYTE, AssaultNotificationPacket::type,
                StreamCodec.STRING, AssaultNotificationPacket::regionId,
                StreamCodec.STRING, AssaultNotificationPacket::regionName,
                StreamCodec.STRING, AssaultNotificationPacket::nationName,
                StreamCodec.STRING, AssaultNotificationPacket::winnerName,
                StreamCodec.INT, AssaultNotificationPacket::score,
                StreamCodec.STRING, AssaultNotificationPacket::extraData,
                ::AssaultNotificationPacket
            )
        }
    }

    // Dados completos da região (para GUI)
    record class RegionDataPacket(
        val region: RegionFullData,
        val objectives: List<ObjectiveFullData>,
        val rewards: RewardData,
        val history: List<BattleHistoryEntry>,
        val rankings: RankingsData,
    ) : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "region_data")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RegionDataPacket> = StreamCodec.composite(
                RegionFullData.STREAM_CODEC, RegionDataPacket::region,
                ObjectiveFullData.STREAM_CODEC.apply(StreamCodec.list()), RegionDataPacket::objectives,
                RewardData.STREAM_CODEC, RegionDataPacket::rewards,
                BattleHistoryEntry.STREAM_CODEC.apply(StreamCodec.list()), RegionDataPacket::history,
                RankingsData.STREAM_CODEC, RegionDataPacket::rankings,
                ::RegionDataPacket
            )
        }
    }

    record class RegionFullData(
        val id: String,
        val name: String,
        val dimension: String,
        val minX: Int, val minY: Int, val minZ: Int,
        val maxX: Int, val maxY: Int, val maxZ: Int,
        val ownerNationId: String?,
        val ownerNationName: String?,
        val state: Byte,
        val attackWindows: List<WindowData>,
        val nextWindowIndex: Int,
        val createdAt: Long,
        val lastCaptureTime: Long,
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RegionFullData> = StreamCodec.composite(
                StreamCodec.STRING, RegionFullData::id,
                StreamCodec.STRING, RegionFullData::name,
                StreamCodec.STRING, RegionFullData::dimension,
                StreamCodec.INT, RegionFullData::minX,
                StreamCodec.INT, RegionFullData::minY,
                StreamCodec.INT, RegionFullData::minZ,
                StreamCodec.INT, RegionFullData::maxX,
                StreamCodec.INT, RegionFullData::maxY,
                StreamCodec.INT, RegionFullData::maxZ,
                StreamCodec.STRING, RegionFullData::ownerNationId,
                StreamCodec.STRING, RegionFullData::ownerNationName,
                StreamCodec.BYTE, RegionFullData::state,
                WindowData.STREAM_CODEC.apply(StreamCodec.list()), RegionFullData::attackWindows,
                StreamCodec.INT, RegionFullData::nextWindowIndex,
                StreamCodec.LONG, RegionFullData::createdAt,
                StreamCodec.LONG, RegionFullData::lastCaptureTime,
                ::RegionFullData
            )
        }
    }

    record class WindowData(
        val dayOfWeek: Int,
        val startTime: String,
        val durationMinutes: Int,
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, WindowData> = StreamCodec.composite(
                StreamCodec.INT, WindowData::dayOfWeek,
                StreamCodec.STRING, WindowData::startTime,
                StreamCodec.INT, WindowData::durationMinutes,
                ::WindowData
            )
        }
    }

    record class ObjectiveFullData(
        val id: String,
        val name: String,
        val description: String,
        val type: String,
        val state: Byte,
        val progress: Float,
        val requiredForVictory: Boolean,
        val rewardPoints: Int,
        val configJson: String, // JSON com config específica do objetivo
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ObjectiveFullData> = StreamCodec.composite(
                StreamCodec.STRING, ObjectiveFullData::id,
                StreamCodec.STRING, ObjectiveFullData::name,
                StreamCodec.STRING, ObjectiveFullData::description,
                StreamCodec.STRING, ObjectiveFullData::type,
                StreamCodec.BYTE, ObjectiveFullData::state,
                StreamCodec.FLOAT, ObjectiveFullData::progress,
                StreamCodec.BOOL, ObjectiveFullData::requiredForVictory,
                StreamCodec.INT, ObjectiveFullData::rewardPoints,
                StreamCodec.STRING, ObjectiveFullData::configJson,
                ::ObjectiveFullData
            )
        }
    }

    record class RewardData(
        val dailyMoney: Long,
        val dailyItems: List<RewardItemData>,
        val dailyResources: List<RewardResourceData>,
        val captureBonusMoney: Long,
        val captureBonusItems: List<RewardItemData>,
        val intervalHours: Int,
        val enabled: Boolean,
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RewardData> = StreamCodec.composite(
                StreamCodec.LONG, RewardData::dailyMoney,
                RewardItemData.STREAM_CODEC.apply(StreamCodec.list()), RewardData::dailyItems,
                RewardResourceData.STREAM_CODEC.apply(StreamCodec.list()), RewardData::dailyResources,
                StreamCodec.LONG, RewardData::captureBonusMoney,
                RewardItemData.STREAM_CODEC.apply(StreamCodec.list()), RewardData::captureBonusItems,
                StreamCodec.INT, RewardData::intervalHours,
                StreamCodec.BOOL, RewardData::enabled,
                ::RewardData
            )
        }
    }

    record class RewardItemData(
        val itemId: String,
        val count: Int,
        val weight: Int,
        val nbt: String?,
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RewardItemData> = StreamCodec.composite(
                StreamCodec.STRING, RewardItemData::itemId,
                StreamCodec.INT, RewardItemData::count,
                StreamCodec.INT, RewardItemData::weight,
                StreamCodec.STRING, RewardItemData::nbt,
                ::RewardItemData
            )
        }
    }

    record class RewardResourceData(
        val resourceId: String,
        val amount: Long,
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RewardResourceData> = StreamCodec.composite(
                StreamCodec.STRING, RewardResourceData::resourceId,
                StreamCodec.LONG, RewardResourceData::amount,
                ::RewardResourceData
            )
        }
    }

    record class BattleHistoryEntry(
        val id: String,
        val date: String,
        val durationSeconds: Long,
        val winnerNationName: String?,
        val finalScores: Map<String, Int>,
        val totalKills: Int,
        val totalVehiclesDestroyed: Int,
        val mvpPlayerName: String?,
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, BattleHistoryEntry> = StreamCodec.composite(
                StreamCodec.STRING, BattleHistoryEntry::id,
                StreamCodec.STRING, BattleHistoryEntry::date,
                StreamCodec.LONG, BattleHistoryEntry::durationSeconds,
                StreamCodec.STRING, BattleHistoryEntry::winnerNationName,
                StreamCodec.STRING.apply(StreamCodec.map(StreamCodec.STRING, StreamCodec.INT)), BattleHistoryEntry::finalScores,
                StreamCodec.INT, BattleHistoryEntry::totalKills,
                StreamCodec.INT, BattleHistoryEntry::totalVehiclesDestroyed,
                StreamCodec.STRING, BattleHistoryEntry::mvpPlayerName,
                ::BattleHistoryEntry
            )
        }
    }

    record class RankingsData(
        val topNations: List<NationRankingEntry>,
        val topPlayers: List<PlayerRankingEntry>,
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RankingsData> = StreamCodec.composite(
                NationRankingEntry.STREAM_CODEC.apply(StreamCodec.list()), RankingsData::topNations,
                PlayerRankingEntry.STREAM_CODEC.apply(StreamCodec.list()), RankingsData::topPlayers,
                ::RankingsData
            )
        }
    }

    record class NationRankingEntry(
        val nationId: String,
        val nationName: String,
        val victories: Int,
        val totalBattles: Int,
        val totalScore: Long,
        val winRate: Float,
        val regionsControlled: Int,
        val currentStreak: Int,
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, NationRankingEntry> = StreamCodec.composite(
                StreamCodec.STRING, NationRankingEntry::nationId,
                StreamCodec.STRING, NationRankingEntry::nationName,
                StreamCodec.INT, NationRankingEntry::victories,
                StreamCodec.INT, NationRankingEntry::totalBattles,
                StreamCodec.LONG, NationRankingEntry::totalScore,
                StreamCodec.FLOAT, NationRankingEntry::winRate,
                StreamCodec.INT, NationRankingEntry::regionsControlled,
                StreamCodec.INT, NationRankingEntry::currentStreak,
                ::NationRankingEntry
            )
        }
    }

    record class PlayerRankingEntry(
        val playerId: String,
        val playerName: String,
        val nationName: String?,
        val totalKills: Int,
        val totalDeaths: Int,
        val kdRatio: Float,
        val vehiclesDestroyed: Int,
        val battlesWon: Int,
        val mvpCount: Int,
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, PlayerRankingEntry> = StreamCodec.composite(
                StreamCodec.STRING, PlayerRankingEntry::playerId,
                StreamCodec.STRING, PlayerRankingEntry::playerName,
                StreamCodec.STRING, PlayerRankingEntry::nationName,
                StreamCodec.INT, PlayerRankingEntry::totalKills,
                StreamCodec.INT, PlayerRankingEntry::totalDeaths,
                StreamCodec.FLOAT, PlayerRankingEntry::kdRatio,
                StreamCodec.INT, PlayerRankingEntry::vehiclesDestroyed,
                StreamCodec.INT, PlayerRankingEntry::battlesWon,
                StreamCodec.INT, PlayerRankingEntry::mvpCount,
                ::PlayerRankingEntry
            )
        }
    }

    // ===== Client -> Server Packets =====

    record class RequestRegionDataPacket(
        val regionId: String
    ) : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "request_region_data")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RequestRegionDataPacket> = StreamCodec.composite(
                StreamCodec.STRING, RequestRegionDataPacket::regionId,
                ::RequestRegionDataPacket
            )
        }
    }

    record class RequestAllRegionsPacket() : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "request_all_regions")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RequestAllRegionsPacket> = StreamCodec.unit(::RequestAllRegionsPacket)
        }
    }

    record class RequestRankingsPacket(
        val type: Byte, // 0=nations, 1=players
        val limit: Int,
    ) : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "request_rankings")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RequestRankingsPacket> = StreamCodec.composite(
                StreamCodec.BYTE, RequestRankingsPacket::type,
                StreamCodec.INT, RequestRankingsPacket::limit,
                ::RequestRankingsPacket
            )
        }
    }

    record class AdminCommandPacket(
        val command: String, // "create_region", "delete_region", "set_window", "set_rewards", "force_end", etc.
        val args: Map<String, String>,
    ) : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "admin_command")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AdminCommandPacket> = StreamCodec.composite(
                StreamCodec.STRING, AdminCommandPacket::command,
                StreamCodec.STRING.apply(StreamCodec.map(StreamCodec.STRING, StreamCodec.STRING)), AdminCommandPacket::args,
                ::AdminCommandPacket
            )
        }
    }

    record class VehicleRegistrationPacket(
        val entityId: Int,
        val vehicleClassId: String,
        val customName: String?,
    ) : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "vehicle_registration")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, VehicleRegistrationPacket> = StreamCodec.composite(
                StreamCodec.INT, VehicleRegistrationPacket::entityId,
                StreamCodec.STRING, VehicleRegistrationPacket::vehicleClassId,
                StreamCodec.STRING, VehicleRegistrationPacket::customName,
                ::VehicleRegistrationPacket
            )
        }
    }

    // ===== Packet Handlers (Server side) =====

    @Suppress("UNUSED_PARAMETER")
    fun handleRequestRegionData(packet: RequestRegionDataPacket, context: IPayloadContext) {
        context.enqueueWork(() -> {
            val player = context.player()
            val manager = AssaultRegionManager.getInstance()
            val region = manager.getRegion(packet.regionId) ?: return

            // Construir packet de resposta
            val regionData = buildRegionFullData(region)
            val objectives = buildObjectiveFullData(region.id)
            val rewards = buildRewardData(region.id)
            val history = buildBattleHistory(region.id)
            val rankings = buildRankingsData()

            val response = RegionDataPacket(regionData, objectives, rewards, history, rankings)
            sendToPlayer(player, response)
        })
    }

    @Suppress("UNUSED_PARAMETER")
    fun handleRequestAllRegions(packet: RequestAllRegionsPacket, context: IPayloadContext) {
        context.enqueueWork(() -> {
            val player = context.player()
            val manager = AssaultRegionManager.getInstance()
            val dimension = player.level.dimension()

            val markers = manager.getRegionsInDimension(dimension).map { region ->
                RegionMarkerData(
                    id = region.id,
                    name = region.displayName,
                    minX = region.bounds.min.x, minY = region.bounds.min.y, minZ = region.bounds.min.z,
                    maxX = region.bounds.max.x, maxY = region.bounds.max.y, maxZ = region.bounds.max.z,
                    dimension = region.bounds.dimension.toString(),
                    ownerNation = region.ownerNationName,
                    state = when (region.state) {
                        com.tlgd.navalwarfare.region.AssaultState.INACTIVE -> 0
                        com.tlgd.navalwarfare.region.AssaultState.PEACEFUL -> 1
                        com.tlgd.navalwarfare.region.AssaultState.PREPARING -> 2
                        com.tlgd.navalwarfare.region.AssaultState.ACTIVE -> 3
                        com.tlgd.navalwarfare.region.AssaultState.COOLDOWN -> 4
                    },
                    nextWindow = region.getNextWindow()?.toString(),
                    color = region.ownerNationId?.let { getNationColor(it) } ?: 0xFF888888
                )
            }

            val packet = RegionMarkersPacket(markers)
            sendToPlayer(player, packet)
        })
    }

    @Suppress("UNUSED_PARAMETER")
    fun handleRequestRankings(packet: RequestRankingsPacket, context: IPayloadContext) {
        context.enqueueWork(() -> {
            val player = context.player()
            val historyManager = com.tlgd.navalwarfare.history.BattleHistoryManager.getInstance()

            if (packet.type == 0) {
                // Nations
                val rankings = historyManager.getNationRankings(packet.limit)
                val entries = rankings.map { r ->
                    NationRankingEntry(
                        nationId = r.nationId,
                        nationName = r.nationName,
                        victories = r.victories,
                        totalBattles = r.totalBattles,
                        totalScore = r.totalScore,
                        winRate = r.winRate.toFloat(),
                        regionsControlled = r.regionsControlled,
                        currentStreak = r.currentStreak
                    )
                }
                val data = RankingsData(entries, emptyList())
                // Enviar packet de rankings (criar novo tipo se necessário)
            } else {
                // Players
            }
        })
    }

    @Suppress("UNUSED_PARAMETER")
    fun handleAdminCommand(packet: AdminCommandPacket, context: IPayloadContext) {
        context.enqueueWork(() -> {
            val player = context.player()
            if (!player.hasPermissions(3)) return // Apenas OP 3+

            // Executar comando admin
            when (packet.command) {
                "create_region" -> { /* implementar */ }
                "delete_region" -> { /* implementar */ }
                "set_window" -> { /* implementar */ }
                "set_rewards" -> { /* implementar */ }
                "force_end" -> { /* implementar */ }
                "set_objective" -> { /* implementar */ }
                "register_vehicle" -> { /* implementar */ }
            }
        })
    }

    @Suppress("UNUSED_PARAMETER")
    fun handleVehicleRegistration(packet: VehicleRegistrationPacket, context: IPayloadContext) {
        context.enqueueWork(() -> {
            val player = context.player()
            val level = player.serverLevel
            val entity = level.getEntity(packet.entityId) ?: return

            // Registrar veículo manualmente
            com.tlgd.navalwarfare.vehicle.VehicleIntegrationManager.getInstance()
                .registerCustomClass(...) // Implementar registro manual
        })
    }

    // ===== Helper functions =====

    private fun buildRegionFullData(region: AssaultRegion): RegionFullData {
        return RegionFullData(
            id = region.id,
            name = region.displayName,
            dimension = region.bounds.dimension.toString(),
            minX = region.bounds.min.x, minY = region.bounds.min.y, minZ = region.bounds.min.z,
            maxX = region.bounds.max.x, maxY = region.bounds.max.y, maxZ = region.bounds.max.z,
            ownerNationId = region.ownerNationId,
            ownerNationName = region.ownerNationName,
            state = when (region.state) {
                com.tlgd.navalwarfare.region.AssaultState.INACTIVE -> 0
                com.tlgd.navalwarfare.region.AssaultState.PEACEFUL -> 1
                com.tlgd.navalwarfare.region.AssaultState.PREPARING -> 2
                com.tlgd.navalwarfare.region.AssaultState.ACTIVE -> 3
                com.tlgd.navalwarfare.region.AssaultState.COOLDOWN -> 4
            },
            attackWindows = region.attackWindows.map { w ->
                WindowData(w.dayOfWeek.ordinal, w.startTime.toString(), w.durationMinutes)
            },
            nextWindowIndex = region.nextWindowIndex,
            createdAt = region.createdAt,
            lastCaptureTime = region.lastCaptureTime
        )
    }

    private fun buildObjectiveFullData(regionId: String): List<ObjectiveFullData> {
        val objManager = com.tlgd.navalwarfare.objective.ObjectiveManager.getInstance()
        return objManager.getRegionObjectives(regionId).map { obj ->
            ObjectiveFullData(
                id = obj.id,
                name = obj.displayName,
                description = obj.description,
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
                configJson = com.google.gson.Gson().toJson(obj.serialize())
            )
        }
    }

    private fun buildRewardData(regionId: String): RewardData {
        val rewardManager = com.tlgd.navalwarfare.reward.RewardManager.getInstance()
        val rewards = rewardManager.getRegionRewards(regionId) ?: return RewardData(0, emptyList(), emptyList(), 0, emptyList(), 24, false)
        return RewardData(
            dailyMoney = rewards.dailyMoney,
            dailyItems = rewards.dailyItems.map { RewardItemData(it.itemId, it.count, it.weight, it.nbt) },
            dailyResources = rewards.dailyResources.map { RewardResourceData(it.resourceId, it.amount) },
            captureBonusMoney = rewards.captureBonusMoney,
            captureBonusItems = rewards.captureBonusItems.map { RewardItemData(it.itemId, it.count, it.weight, it.nbt) },
            intervalHours = rewards.intervalHours,
            enabled = rewards.enabled
        )
    }

    private fun buildBattleHistory(regionId: String): List<BattleHistoryEntry> {
        val historyManager = com.tlgd.navalwarfare.history.BattleHistoryManager.getInstance()
        return historyManager.getBattlesByRegion(regionId, 20).map { battle ->
            BattleHistoryEntry(
                id = battle.id,
                date = battle.getDateString(),
                durationSeconds = battle.durationSeconds,
                winnerNationName = battle.winnerNationName,
                finalScores = battle.finalScores,
                totalKills = battle.totalKills,
                totalVehiclesDestroyed = battle.totalVehiclesDestroyed,
                mvpPlayerName = battle.mvpPlayerName
            )
        }
    }

    private fun buildRankingsData(): RankingsData {
        val historyManager = com.tlgd.navalwarfare.history.BattleHistoryManager.getInstance()
        val topNations = historyManager.getNationRankings(10).map { r ->
            NationRankingEntry(
                nationId = r.nationId,
                nationName = r.nationName,
                victories = r.victories,
                totalBattles = r.totalBattles,
                totalScore = r.totalScore,
                winRate = r.winRate.toFloat(),
                regionsControlled = r.regionsControlled,
                currentStreak = r.currentStreak
            )
        }
        val topPlayers = historyManager.getPlayerRankings(10).map { r ->
            PlayerRankingEntry(
                playerId = r.playerId,
                playerName = r.playerName,
                nationName = r.nationName,
                totalKills = r.totalKills,
                totalDeaths = r.totalDeaths,
                kdRatio = r.kdRatio.toFloat(),
                vehiclesDestroyed = r.vehiclesDestroyed,
                battlesWon = r.battlesWon,
                mvpCount = r.mvpCount
            )
        }
        return RankingsData(topNations, topPlayers)
    }

    private fun getNationColor(nationId: String): Int {
        val hash = nationId.hashCode()
        val r = (hash & 0xFF).toInt()
        val g = ((hash >> 8) & 0xFF).toInt()
        val b = ((hash >> 16) & 0xFF).toInt()
        return 0xFF000000 | ((r + 128) % 256) shl 16 | ((g + 128) % 256) shl 8 | ((b + 128) % 256)
    }

    // ===== Send functions =====

    fun sendAssaultHUD(player: net.minecraft.server.level.ServerPlayer, packet: AssaultHUDPacket) {
        sendToPlayer(player, packet)
    }

    fun sendRegionMarkers(player: net.minecraft.server.level.ServerPlayer, packet: RegionMarkersPacket) {
        sendToPlayer(player, packet)
    }

    fun sendAnnouncement(player: net.minecraft.server.level.ServerPlayer, packet: AnnouncementPacket) {
        sendToPlayer(player, packet)
    }

    fun sendGlobalAnnouncement(packet: AnnouncementPacket) {
        val server = net.minecraft.server.MinecraftServer.getServer() ?: return
        for (player in server.getPlayerList().getPlayers()) {
            sendToPlayer(player, packet)
        }
    }

    fun sendAssaultNotification(player: net.minecraft.server.level.ServerPlayer, packet: AssaultNotificationPacket) {
        sendToPlayer(player, packet)
    }

    fun sendGlobalAssaultNotification(packet: AssaultNotificationPacket) {
        val server = net.minecraft.server.MinecraftServer.getServer() ?: return
        for (player in server.getPlayerList().getPlayers()) {
            sendToPlayer(player, packet)
        }
    }

    fun sendRegionData(player: net.minecraft.server.level.ServerPlayer, packet: RegionDataPacket) {
        sendToPlayer(player, packet)
    }

    private fun sendToPlayer(player: net.minecraft.server.level.ServerPlayer, packet: CustomPacketPayload) {
        player.connection.send(packet)
    }
}