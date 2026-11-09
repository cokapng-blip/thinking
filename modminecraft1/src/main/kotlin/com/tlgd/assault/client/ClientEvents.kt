package com.tlgd.assault.client

import com.tlgd.assault.TLGAssaultMod
import com.tlgd.assault.client.gui.AssaultMapScreen
import com.tlgd.assault.network.NetworkHandler
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.settings.KeyConflictContext
import net.neoforged.neoforge.network.event.EventNetworkPayloadHandler
import org.lwjgl.glfw.GLFW

@EventBusSubscriber(modid = TLGAssaultMod.MOD_ID, value = [Dist.CLIENT], bus = EventBusSubscriber.Bus.MOD)
object ClientEvents {

    private var keyOpenMap: KeyMapping? = null

    @SubscribeEvent
    @JvmStatic
    fun registerKeyMappings(event: RegisterKeyMappingsEvent) {
        keyOpenMap = KeyMapping(
            "key.tlgdassault.open_map",
            KeyConflictContext.IN_GAME,
            GLFW.GLFW_KEY_M, // Tecla M
            "key.categories.tlgdassault"
        )
        event.register(keyOpenMap!!)
    }

    @SubscribeEvent
    @JvmStatic
    fun onKeyInput(event: net.neoforged.neoforge.client.event.InputEvent.Key) {
        if (keyOpenMap != null && keyOpenMap!!.consumeClick()) {
            val minecraft = Minecraft.getInstance()
            if (minecraft.screen == null && minecraft.player != null) {
                minecraft.setScreen(AssaultMapScreen())
            }
        }
    }
}

// Packet handlers - registrar no mod principal
class ClientPacketHandlers {

    @EventNetworkPayloadHandler
    fun handleAssaultHUD(packet: NetworkHandler.AssaultHUDPacket, context: net.neoforged.neoforge.network.handling.IPayloadContext) {
        context.enqueueWork(() -> {
            AssaultHUD.updateHUDData(packet)
        })
    }

    @EventNetworkPayloadHandler
    fun handleRegionMarkers(packet: NetworkHandler.RegionMarkersPacket, context: net.neoforged.neoforge.network.handling.IPayloadContext) {
        context.enqueueWork(() -> {
            val screen = Minecraft.getInstance().screen
            if (screen is AssaultMapScreen) {
                screen.updateRegions(packet.regions)
            }
        })
    }

    @EventNetworkPayloadHandler
    fun handleAnnouncement(packet: NetworkHandler.AnnouncementPacket, context: net.neoforged.neoforge.network.handling.IPayloadContext) {
        context.enqueueWork(() -> {
            val minecraft = Minecraft.getInstance()
            val player = minecraft.player
            if (player != null) {
                player.sendSystemMessage(Component.literal(packet.message))
                if (packet.sound.isNotEmpty()) {
                    player.playSound(net.minecraft.resources.ResourceLocation.parse(packet.sound), 1.0f, 1.0f)
                }
            }
        })
    }

    @EventNetworkPayloadHandler
    fun handleAssaultNotification(packet: NetworkHandler.AssaultNotificationPacket, context: net.neoforged.neoforge.network.handling.IPayloadContext) {
        context.enqueueWork(() -> {
            val minecraft = Minecraft.getInstance()
            val player = minecraft.player
            if (player != null) {
                val msg = when (packet.type) {
                    0 -> "§c§lASSALTO INICIADO§r §7em §e${packet.regionName} §7por §e${packet.nationName}"
                    1 -> "§7Assalto em §e${packet.regionName} §7terminou."
                    2 -> "§a§l${packet.winnerName}§r §7venceu em §e${packet.regionName} §7com §e${packet.score} §7pontos!"
                    else -> "§7Notificação de assalto"
                }
                player.sendSystemMessage(Component.literal(msg))
                player.playSound(net.minecraft.resources.ResourceLocation.parse("minecraft:entity.experience_orb.pickup"), 1.0f, 1.0f)
            }
        })
    }
}