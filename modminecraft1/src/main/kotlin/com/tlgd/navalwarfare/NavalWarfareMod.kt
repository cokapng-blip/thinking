package com.tlgd.navalwarfare

import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartingEvent
import org.slf4j.Logger
import com.tlgd.navalwarfare.config.NavalWarfareConfig
import com.tlgd.navalwarfare.region.AssaultRegionManager
import com.tlgd.navalwarfare.vehicle.VehicleRegistry
import com.tlgd.navalwarfare.vehicle.VehicleIntegrationManager
import com.tlgd.navalwarfare.scoring.ScoringManager
import com.tlgd.navalwarfare.presence.PresenceManager
import com.tlgd.navalwarfare.objective.ObjectiveManager
import com.tlgd.navalwarfare.reward.RewardManager
import com.tlgd.navalwarfare.map.MapIntegration
import com.tlgd.navalwarfare.history.BattleHistoryManager
import com.tlgd.navalwarfare.notification.NotificationManager
import com.tlgd.navalwarfare.network.NetworkHandler
import com.tlgd.navalwarfare.command.NavalWarfareCommands

@Mod(NavalWarfareMod.MOD_ID)
class NavalWarfareMod(container: ModContainer) {

    companion object {
        const val MOD_ID = "navalwarfare"
        const val MOD_NAME = "Naval Warfare"
        private val LOGGER: Logger = LogUtils.getLogger()
    }

    init {
        val modEventBus: IEventBus = container.bus
        val forgeEventBus = NeoForge.EVENT_BUS

        // Registrar config
        container.registerConfig(ModConfig.Type.COMMON, NavalWarfareConfig.SPEC)

        // Inicializar sistemas core (ordem importa: dependências primeiro)
        VehicleRegistry.init()                    // 1. Registry de classes de veículo (datapacks)
        VehicleIntegrationManager.init()          // 2. Detector/tracker de veículos
        AssaultRegionManager.init()               // 3. Gerenciador de regiões
        ScoringManager.init()                     // 4. Sistema de pontuação
        PresenceManager.init()                    // 5. Sistema de presença
        ObjectiveManager.init()                   // 6. Objetivos estratégicos
        RewardManager.init()                      // 7. Sistema de recompensas
        MapIntegration.init()                     // 8. Integração com mapas
        BattleHistoryManager.init()               // 9. Histórico de batalhas
        NotificationManager.init()                // 10. Notificações
        NetworkHandler.init()                     // 11. Network

        // Registrar comandos
        forgeEventBus.addListener(NavalWarfareCommands::register)

        // Event listeners do servidor
        forgeEventBus.addListener(ServerEvents::onServerStarting)
        forgeEventBus.addListener(ServerEvents::onPlayerJoin)
        forgeEventBus.addListener(ServerEvents::onPlayerLeave)
        forgeEventBus.addListener(ServerEvents::onEntityDeath)
        forgeEventBus.addListener(ServerEvents::onEntityHurt)
        forgeEventBus.addListener(ServerEvents::onBlockBreak)
        forgeEventBus.addListener(ServerEvents::onExplosion)
        forgeEventBus.addListener(ServerEvents::onVehicleSpawn)
        forgeEventBus.addListener(ServerEvents::onVehicleDespawn)

        LOGGER.info("{} v{} carregado com sucesso!", MOD_NAME, BuildConstants.VERSION)
    }
}

// ===== Constantes de build (injetadas pelo Gradle) =====
object BuildConstants {
    const val VERSION = "1.0.0"
    const val MC_VERSION = "1.21.1"
    const val NEOFORGE_VERSION = "21.1.0"
    const val BUILD_TIME = "2026-11-08"
}