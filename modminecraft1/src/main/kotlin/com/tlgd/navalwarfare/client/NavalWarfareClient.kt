package com.tlgd.navalwarfare.client

import com.tlgd.navalwarfare.TLGAssaultMod
import com.tlgd.navalwarfare.client.gui.NavalWarfareMainScreen
import com.tlgd.navalwarfare.client.gui.RegionDetailScreen
import com.tlgd.navalwarfare.client.hud.AssaultHUD
import com.tlgd.navalwarfare.network.NetworkHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.event.RenderGuiOverlayEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import org.lwjgl.glfw.GLFW

@Mod.EventBusSubscriber(modid = TLGAssaultMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object NavalWarfareClient {

    @SubscribeEvent
    @JvmStatic
    fun clientSetup(event: FMLClientSetupEvent) {
        event.enqueueWork(ClientEvents::registerKeyMappings)
    }

    @SubscribeEvent
    @JvmStatic
    fun registerPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")

        // Server -> Client
        registrar.playToClient(NetworkHandler.AssaultHUDPacket.STREAM_CODEC) { packet, context ->
            ClientPacketHandlers.handleAssaultHUD(packet)
        }
        registrar.playToClient(NetworkHandler.RegionMarkersPacket.STREAM_CODEC) { packet, context ->
            ClientPacketHandlers.handleRegionMarkers(packet)
        }
        registrar.playToClient(NetworkHandler.AnnouncementPacket.STREAM_CODEC) { packet, context ->
            ClientPacketHandlers.handleAnnouncement(packet)
        }
        registrar.playToClient(NetworkHandler.AssaultNotificationPacket.STREAM_CODEC) { packet, context ->
            ClientPacketHandlers.handleAssaultNotification(packet)
        }
        registrar.playToClient(NetworkHandler.RegionDataPacket.STREAM_CODEC) { packet, context ->
            ClientPacketHandlers.handleRegionData(packet)
        }
        registrar.playToClient(NetworkHandler.RankingsData.STREAM_CODEC) { packet, context ->
            ClientPacketHandlers.handleRankingsData(packet)
        }

        // Client -> Server
        registrar.playToServer(NetworkHandler.RequestRegionDataPacket.STREAM_CODEC) { packet, context ->
            NetworkHandler.handleRequestRegionData(packet, context)
        }
        registrar.playToServer(NetworkHandler.RequestAllRegionsPacket.STREAM_CODEC) { packet, context ->
            NetworkHandler.handleRequestAllRegions(packet, context)
        }
        registrar.playToServer(NetworkHandler.RequestRankingsPacket.STREAM_CODEC) { packet, context ->
            NetworkHandler.handleRequestRankings(packet, context)
        }
        registrar.playToServer(NetworkHandler.AdminCommandPacket.STREAM_CODEC) { packet, context ->
            NetworkHandler.handleAdminCommand(packet, context)
        }
        registrar.playToServer(NetworkHandler.VehicleRegistrationPacket.STREAM_CODEC) { packet, context ->
            NetworkHandler.handleVehicleRegistration(packet, context)
        }
    }
}

@Mod.EventBusSubscriber(modid = TLGAssaultMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object KeyRegistration {

    @SubscribeEvent
    @JvmStatic
    fun registerKeys(event: RegisterKeyMappingsEvent) {
        val keyOpenMap = net.minecraft.client.KeyMapping(
            "key.navalwarfare.open_map",
            net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
            GLFW.GLFW_KEY_M,
            "key.categories.navalwarfare"
        )
        event.register(keyOpenMap)
        ClientEvents.keyOpenMap = keyOpenMap

        val keyOpenHUD = net.minecraft.client.KeyMapping(
            "key.navalwarfare.open_hud",
            net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
            GLFW.GLFW_KEY_N,
            "key.categories.navalwarfare"
        )
        event.register(keyOpenHUD)
        ClientEvents.keyOpenHUD = keyOpenHUD

        val keyToggleMarkers = net.minecraft.client.KeyMapping(
            "key.navalwarfare.toggle_markers",
            net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
            GLFW.GLFW_KEY_B,
            "key.categories.navalwarfare"
        )
        event.register(keyToggleMarkers)
        ClientEvents.keyToggleMarkers = keyToggleMarkers
    }
}

object ClientEvents {
    var keyOpenMap: net.minecraft.client.KeyMapping? = null
    var keyOpenHUD: net.minecraft.client.KeyMapping? = null
    var keyToggleMarkers: net.minecraft.client.KeyMapping? = null

    @JvmStatic
    fun registerKeyMappings() {
        // Já registrado via KeyRegistration
    }

    @SubscribeEvent
    @JvmStatic
    fun onKeyInput(event: net.neoforged.neoforge.client.event.InputEvent.Key) {
        if (keyOpenMap?.consumeClick() == true) {
            openMainScreen()
        }
        if (keyOpenHUD?.consumeClick() == true) {
            toggleAssaultHUD()
        }
        if (keyToggleMarkers?.consumeClick() == true) {
            toggleRegionMarkers()
        }
    }

    private fun openMainScreen() {
        val minecraft = Minecraft.getInstance()
        if (minecraft.screen == null) {
            minecraft.setScreen(NavalWarfareMainScreen())
        }
    }

    private fun toggleAssaultHUD() {
        AssaultHUD.toggle()
    }

    private fun toggleRegionMarkers() {
        val config = com.tlgd.navalwarfare.config.NavalWarfareConfig.showRegionMarkers
        config.set(!config.get())
        val player = Minecraft.getInstance().player
        player?.displayClientMessage(
            net.minecraft.network.chat.Component.literal(
                "§e[NavalWarfare] Marcadores de região: §${if (config.get()) "aAtivados" else "cDesativados"}"
            ),
            true
        )
    }
}

// HUD Rendering
@Mod.EventBusSubscriber(modid = TLGAssaultMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.GAME, value = [Dist.CLIENT])
object HUDRenderer {

    @SubscribeEvent
    @JvmStatic
    fun onRenderHUD(event: RenderGuiOverlayEvent.Post) {
        val minecraft = Minecraft.getInstance()
        if (minecraft.screen != null) return
        if (!com.tlgd.navalwarfare.config.NavalWarfareConfig.showAssaultHUD.get()) return

        AssaultHUD.render(event.guiGraphics, event.partialTick)
    }
}

// ===== Client Packet Handlers =====
object ClientPacketHandlers {

    private var currentHUDData: NetworkHandler.AssaultHUDPacket? = null
    private var currentRegionMarkers: List<NetworkHandler.RegionMarkerData> = emptyList()
    private var currentRegionData: NetworkHandler.RegionDataPacket? = null
    private var currentRankingsData: NetworkHandler.RankingsData? = null

    fun handleAssaultHUD(packet: NetworkHandler.AssaultHUDPacket) {
        currentHUDData = packet
        AssaultHUD.updateFromPacket(packet)
    }

    fun handleRegionMarkers(packet: NetworkHandler.RegionMarkersPacket) {
        currentRegionMarkers = packet.regions
        // Atualizar marcadores no HUD/Mapa
        AssaultHUD.updateRegionMarkers(packet.regions)
    }

    fun handleAnnouncement(packet: NetworkHandler.AnnouncementPacket) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return

        // Som
        if (packet.sound.isNotBlank()) {
            player.playNotifySound(
                net.minecraft.sounds.SoundEvent(net.minecraft.resources.ResourceLocation(packet.sound)),
                net.minecraft.sounds.SoundSource.MASTER,
                com.tlgd.navalwarfare.config.NavalWarfareConfig.announcementSoundVolume.get().floatValue(),
                1.0f
            )
        }

        // Title/Subtitle
        if (packet.titleText != null || packet.subtitleText != null) {
            val title = packet.titleText?.let { net.minecraft.network.chat.Component.literal(it) }
            val subtitle = packet.subtitleText?.let { net.minecraft.network.chat.Component.literal(it) }
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(packet.message),
                false
            )
            if (title != null) {
                player.showTitle(title, subtitle)
            }
        }

        // Action bar
        if (packet.actionBarText != null) {
            player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(packet.actionBarText),
                true
            )
        }

        // Toast
        if (packet.showToast && packet.toastIcon != null) {
            val icon = net.minecraft.resources.ResourceLocation(packet.toastIcon!!)
            minecraft.getToasts().addToast(
                net.minecraft.client.gui.toasts.SystemToast(
                    net.minecraft.client.gui.toasts.SystemToast.SystemToastIds.ADVANCEMENT,
                    net.minecraft.network.chat.Component.literal("Naval Warfare"),
                    net.minecraft.network.chat.Component.literal(packet.message)
                )
            )
        } else if (packet.showToast) {
            minecraft.getToasts().addToast(
                net.minecraft.client.gui.toasts.SystemToast(
                    net.minecraft.client.gui.toasts.SystemToast.SystemToastIds.ADVANCEMENT,
                    net.minecraft.network.chat.Component.literal("Naval Warfare"),
                    net.minecraft.network.chat.Component.literal(packet.message)
                )
            )
        }
    }

    fun handleAssaultNotification(packet: NetworkHandler.AssaultNotificationPacket) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.player ?: return

        val message = when (packet.type) {
            0 -> "§c§l⚔ ASSALTO INICIADO §r§c em ${packet.regionName} por ${packet.nationName}!"
            1 -> "§a§l✓ ASSALTO FINALIZADO §r§a em ${packet.regionName}"
            2 -> "§e§l🏆 VITÓRIA §r§e${packet.winnerName} venceu em ${packet.regionName} com ${packet.score} pontos!"
            3 -> "§b§l✓ OBJETIVO COMPLETADO §r§b${packet.nationName} completou objetivo em ${packet.regionName}"
            4 -> "§c§l✗ OBJETIVO FALHADO §r§c${packet.nationName} falhou objetivo em ${packet.regionName}"
            5 -> "§c§l💥 NAVIO CAPITAL DESTRUÍDO §r§c${packet.extraData} em ${packet.regionName}"
            else -> "§e[NavalWarfare] ${packet.extraData}"
        }

        player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), false)

        // Som baseado no tipo
        val sound = when (packet.type) {
            0 -> com.tlgd.navalwarfare.config.NavalWarfareConfig.assaultStartSound.get()
            1 -> com.tlgd.navalwarfare.config.NavalWarfareConfig.assaultEndSound.get()
            2 -> com.tlgd.navalwarfare.config.NavalWarfareConfig.victorySound.get()
            3, 4 -> com.tlgd.navalwarfare.config.NavalWarfareConfig.objectiveSound.get()
            5 -> com.tlgd.navalwarfare.config.NavalWarfareConfig.capitalShipDestroyedSound.get()
            else -> ""
        }

        if (sound.isNotBlank()) {
            player.playNotifySound(
                net.minecraft.sounds.SoundEvent(net.minecraft.resources.ResourceLocation(sound)),
                net.minecraft.sounds.SoundSource.MASTER,
                com.tlgd.navalwarfare.config.NavalWarfareConfig.announcementSoundVolume.get().floatValue(),
                1.0f
            )
        }
    }

    fun handleRegionData(packet: NetworkHandler.RegionDataPacket) {
        currentRegionData = packet

        // Se tiver tela aberta, atualizar
        val minecraft = Minecraft.getInstance()
        if (minecraft.screen is RegionDetailScreen) {
            (minecraft.screen as RegionDetailScreen).updateFromPacket(packet)
        }
    }

    fun handleRankingsData(packet: NetworkHandler.RankingsData) {
        currentRankingsData = packet

        val minecraft = Minecraft.getInstance()
        if (minecraft.screen is NavalWarfareMainScreen) {
            (minecraft.screen as NavalWarfareMainScreen).updateRankings(packet)
        }
    }

    // Getters para GUI
    fun getCurrentHUDData(): NetworkHandler.AssaultHUDPacket? = currentHUDData
    fun getCurrentRegionMarkers(): List<NetworkHandler.RegionMarkerData> = currentRegionMarkers
    fun getCurrentRegionData(): NetworkHandler.RegionDataPacket? = currentRegionData
    fun getCurrentRankingsData(): NetworkHandler.RankingsData? = currentRankingsData
}