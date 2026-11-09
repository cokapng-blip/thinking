package com.tlgd.assault.network

import com.tlgd.assault.TLGAssaultMod
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import java.util.UUID

object NetworkHandler {

    const val CHANNEL_NAME = "tlgdassault:main"

    fun init() {
        // Packets são registrados via @Mod.EventBusSubscriber no Mod class
    }

    // ===== Server -> Client Packets =====

    // Sync assault state for HUD
    record class AssaultHUDPacket(
        val regionId: String,
        val regionName: String,
        val remainingSeconds: Int,
        val state: Int, // 0=inactive, 1=preparing, 2=active, 3=cooldown
        val leaderboard: List<LeaderboardEntry>
    ) : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "assault_hud")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AssaultHUDPacket> = StreamCodec.composite(
                StreamCodec.STRING, AssaultHUDPacket::regionId,
                StreamCodec.STRING, AssaultHUDPacket::regionName,
                StreamCodec.INT, AssaultHUDPacket::remainingSeconds,
                StreamCodec.BYTE, AssaultHUDPacket::state,
                LeaderboardEntry.STREAM_CODEC.apply(StreamCodec.list()), AssaultHUDPacket::leaderboard,
                ::AssaultHUDPacket
            )
        }
    }

    record class LeaderboardEntry(
        val nationName: String,
        val score: Int,
        val kills: Int,
        val vehiclesDestroyed: Int,
        val flagsCaptured: Int
    ) {
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, LeaderboardEntry> = StreamCodec.composite(
                StreamCodec.STRING, LeaderboardEntry::nationName,
                StreamCodec.INT, LeaderboardEntry::score,
                StreamCodec.INT, LeaderboardEntry::kills,
                StreamCodec.INT, LeaderboardEntry::vehiclesDestroyed,
                StreamCodec.INT, LeaderboardEntry::flagsCaptured,
                ::LeaderboardEntry
            )
        }
    }

    // Region markers for client rendering
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
        val state: Int
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
                ::RegionMarkerData
            )
        }
    }

    // Announcement packet
    record class AnnouncementPacket(
        val message: String,
        val sound: String,
        val isGlobal: Boolean
    ) : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "announcement")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, AnnouncementPacket> = StreamCodec.composite(
                StreamCodec.STRING, AnnouncementPacket::message,
                StreamCodec.STRING, AnnouncementPacket::sound,
                StreamCodec.BOOL, AnnouncementPacket::isGlobal,
                ::AnnouncementPacket
            )
        }
    }

    // Assault started/ended notification
    record class AssaultNotificationPacket(
        val type: Byte, // 0=started, 1=ended, 2=winner
        val regionId: String,
        val regionName: String,
        val nationName: String?,
        val winnerName: String?,
        val score: Int
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
                ::AssaultNotificationPacket
            )
        }
    }

    // ===== Client -> Server Packets =====

    // Request region data (when opening GUI)
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

    // Request all regions (for map GUI)
    record class RequestAllRegionsPacket() : CustomPacketPayload {
        override fun id(): ResourceLocation = ResourceLocation(TLGAssaultMod.MOD_ID, "request_all_regions")
        companion object {
            val STREAM_CODEC: StreamCodec<FriendlyByteBuf, RequestAllRegionsPacket> = StreamCodec.unit(::RequestAllRegionsPacket)
        }
    }

    // ===== Packet Handlers (Server side) =====

    @Suppress("UNUSED_PARAMETER")
    fun handleRequestRegionData(packet: RequestRegionDataPacket, context: IPayloadContext) {
        context.enqueueWork(() -> {
            val player = context.player()
            // Send region data back
            // TODO: Implementar envio de dados da região para o player
        })
    }

    @Suppress("UNUSED_PARAMETER")
    fun handleRequestAllRegions(packet: RequestAllRegionsPacket, context: IPayloadContext) {
        context.enqueueWork(() -> {
            val player = context.player()
            // Send all regions markers
            // TODO: Implementar
        })
    }

    // ===== Helper functions to send packets =====

    fun sendAssaultHUD(player: net.minecraft.server.level.ServerPlayer, regionId: String, regionName: String, remainingSeconds: Int, state: Int, leaderboard: List<LeaderboardEntry>) {
        val packet = AssaultHUDPacket(regionId, regionName, remainingSeconds, state, leaderboard)
        sendToPlayer(player, packet)
    }

    fun sendRegionMarkers(player: net.minecraft.server.level.ServerPlayer, regions: List<RegionMarkerData>) {
        val packet = RegionMarkersPacket(regions)
        sendToPlayer(player, packet)
    }

    fun sendAnnouncement(player: net.minecraft.server.level.ServerPlayer, message: String, sound: String, isGlobal: Boolean) {
        val packet = AnnouncementPacket(message, sound, isGlobal)
        sendToPlayer(player, packet)
    }

    fun sendGlobalAnnouncement(message: String, sound: String) {
        val packet = AnnouncementPacket(message, sound, true)
        for (player in net.minecraft.server.MinecraftServer.getServer()?.getPlayerList()?.getPlayers() ?: emptyList()) {
            sendToPlayer(player, packet)
        }
    }

    fun sendAssaultNotification(player: net.minecraft.server.level.ServerPlayer, type: Byte, regionId: String, regionName: String, nationName: String?, winnerName: String?, score: Int) {
        val packet = AssaultNotificationPacket(type, regionId, regionName, nationName, winnerName, score)
        sendToPlayer(player, packet)
    }

    fun sendGlobalAssaultNotification(type: Byte, regionId: String, regionName: String, nationName: String?, winnerName: String?, score: Int) {
        val packet = AssaultNotificationPacket(type, regionId, regionName, nationName, winnerName, score)
        for (player in net.minecraft.server.MinecraftServer.getServer()?.getPlayerList()?.getPlayers() ?: emptyList()) {
            sendToPlayer(player, packet)
        }
    }

    private fun sendToPlayer(player: net.minecraft.server.level.ServerPlayer, packet: CustomPacketPayload) {
        player.connection.send(packet)
    }
}