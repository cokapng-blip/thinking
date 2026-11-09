package com.tlgd.assault.client

import com.tlgd.assault.TLGAssaultMod
import net.minecraft.client.Minecraft
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.registration.PayloadRegistrar
import org.lwjgl.glfw.GLFW

@Mod.EventBusSubscriber(modid = TLGAssaultMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object TLGDAssaultClient {

    @SubscribeEvent
    @JvmStatic
    fun clientSetup(event: FMLClientSetupEvent) {
        // Registrar key mappings
        event.enqueueWork(ClientEvents::registerKeyMappings)
    }

    @SubscribeEvent
    @JvmStatic
    fun registerPayloadHandlers(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar("1")
        registrar.playToClient(NetworkHandler.AssaultHUDPacket.STREAM_CODEC) { packet, context ->
            ClientPacketHandlers().handleAssaultHUD(packet, context)
        }
        registrar.playToClient(NetworkHandler.RegionMarkersPacket.STREAM_CODEC) { packet, context ->
            ClientPacketHandlers().handleRegionMarkers(packet, context)
        }
        registrar.playToClient(NetworkHandler.AnnouncementPacket.STREAM_CODEC) { packet, context ->
            ClientPacketHandlers().handleAnnouncement(packet, context)
        }
        registrar.playToClient(NetworkHandler.AssaultNotificationPacket.STREAM_CODEC) { packet, context ->
            ClientPacketHandlers().handleAssaultNotification(packet, context)
        }

        registrar.playToServer(NetworkHandler.RequestRegionDataPacket.STREAM_CODEC) { packet, context ->
            NetworkHandler.handleRequestRegionData(packet, context)
        }
        registrar.playToServer(NetworkHandler.RequestAllRegionsPacket.STREAM_CODEC) { packet, context ->
            NetworkHandler.handleRequestAllRegions(packet, context)
        }
    }
}

// KeyMapping registration separado
@Mod.EventBusSubscriber(modid = TLGAssaultMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object KeyRegistration {

    @SubscribeEvent
    @JvmStatic
    fun registerKeys(event: RegisterKeyMappingsEvent) {
        val keyOpenMap = net.minecraft.client.KeyMapping(
            "key.tlgdassault.open_map",
            net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
            GLFW.GLFW_KEY_M,
            "key.categories.tlgdassault"
        )
        event.register(keyOpenMap)
        ClientEvents.keyOpenMap = keyOpenMap
    }
}