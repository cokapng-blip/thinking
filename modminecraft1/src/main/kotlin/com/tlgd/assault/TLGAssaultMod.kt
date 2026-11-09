package com.tlgd.assault

import com.mojang.logging.LogUtils
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.registries.DeferredRegister
import org.slf4j.Logger
import com.tlgd.assault.config.TLGDAssaultConfig
import com.tlgd.assault.region.AssaultRegionManager
import com.tlgd.assault.command.TLGDAssaultCommands
import com.tlgd.assault.network.NetworkHandler
import com.tlgd.assault.event.ServerEvents

@Mod(TLGAssaultMod.MOD_ID)
class TLGAssaultMod(container: ModContainer) {

    companion object {
        const val MOD_ID = "tlgdassault"
        const val MOD_NAME = "TLGD Assault Regions"
        private val LOGGER: Logger = LogUtils.getLogger()
    }

    init {
        val modEventBus: IEventBus = container.bus
        val forgeEventBus = NeoForge.EVENT_BUS

        // Registrar config
        container.registerConfig(ModConfig.Type.COMMON, TLGDAssaultConfig.SPEC)

        // Inicializar sistemas core
        AssaultRegionManager.init()
        NetworkHandler.init()

        // Registrar comandos
        forgeEventBus.addListener(ServerEvents::onServerStarting)

        // Event listeners
        forgeEventBus.addListener(ServerEvents::onPlayerJoin)
        forgeEventBus.addListener(ServerEvents::onPlayerLeave)
        forgeEventBus.addListener(ServerEvents::onEntityDeath)
        forgeEventBus.addListener(ServerEvents::onBlockBreak)
        forgeEventBus.addListener(ServerEvents::onExplosion)

        LOGGER.info("{} carregado com sucesso!", MOD_NAME)
    }
}