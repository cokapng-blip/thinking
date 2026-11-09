package com.tlgd.assault.event

import com.tlgd.assault.region.AssaultRegionManager
import com.tlgd.assault.network.NetworkHandler
import com.tlgd.assault.nation.NationIntegration
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.vehicle.Boat
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ExplosionEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

@EventBusSubscriber(modid = "tlgdassault")
object ServerEvents {

    private var tickCounter = 0L

    @SubscribeEvent
    @JvmStatic
    fun onServerStarting(event: ServerStartingEvent) {
        val server = event.server
        // Registrar comandos
        com.tlgd.assault.command.TLGDAssaultCommands.register(server.commands)

        // Carregar regiões do arquivo JSON se existir
        val file = java.io.File(server.getWorldPath(net.minecraft.server.packs.resources.ServerResources.WORLD_DIR).toFile(), "tlgd_assault_regions.json")
        if (file.exists()) {
            AssaultRegionManager.loadFromFile(file, server.registryAccess())
        }

        // Inicializar integração de nações
        NationIntegration.init()

        // Agendar task de tick para HUD e verificação de janelas
        println("[TLGD Assault] Servidor iniciado, sistemas carregados!")
    }

    @SubscribeEvent
    @JvmStatic
    fun onServerTick(event: ServerTickEvent) {
        if (event.phase != ServerTickEvent.Phase.END) return

        tickCounter++
        val manager = AssaultRegionManager.getInstance()

        // A cada 20 ticks (1 segundo)
        if (tickCounter % 20 == 0) {
            // Atualizar jogadores em regiões de assalto
            for (player in event.server.getPlayerList().getPlayers()) {
                manager.onPlayerTick(player)
            }

            // Enviar HUD para jogadores em assalto ativo
            sendHUDUpdates()
        }

        // A cada 6000 ticks (5 minutos) - verificar janelas próximas
        if (tickCounter % 6000 == 0) {
            checkUpcomingWindows()
        }

        // A cada 24000 ticks (20 min) - salvar
        if (tickCounter % 24000 == 0) {
            manager.setDirty()
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as ServerPlayer
        NationIntegration.onPlayerJoin(player)

        // Enviar marcadores de região
        sendRegionMarkers(player)
    }

    @SubscribeEvent
    @JvmStatic
    fun onPlayerLeave(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as ServerPlayer
        NationIntegration.onPlayerLeave(player)
    }

    @SubscribeEvent
    @JvmStatic
    fun onEntityDeath(event: LivingDeathEvent) {
        val entity = event.entity
        val source = event.source

        // Jogador morto por jogador
        if (entity is ServerPlayer) {
            val attacker = source.getEntity()
            if (attacker is ServerPlayer) {
                AssaultRegionManager.getInstance().onPlayerDeath(entity, attacker)
            } else {
                AssaultRegionManager.getInstance().onPlayerDeath(entity, null)
            }
        }

        // Veículo destruído (detecção genérica)
        if (isVehicle(entity)) {
            val attacker = source.getEntity()
            if (attacker is ServerPlayer) {
                AssaultRegionManager.getInstance().onVehicleDestroyed(entity, attacker)
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val player = event.player
        val pos = event.pos
        val level = player.level()

        // Verificar se quebrou uma bandeira/bloco de captura
        if (isCaptureBlock(event.state)) {
            AssaultRegionManager.getInstance().onFlagCapture(player, pos)
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onExplosion(event: ExplosionEvent.Detonate) {
        val explosion = event.explosion
        val level = explosion.level()

        // Verificar se explosão foi causada por canhão de veículo
        val source = explosion.source()
        if (source != null && isVehicle(source)) {
            // Explosão de veículo - pode causar dano a blocos na região
            // O sistema de veículos (Movecraft/etc) já lida com isso
        }
    }

    // ===== Helpers =====

    private fun sendHUDUpdates() {
        val manager = AssaultRegionManager.getInstance()

        for (player in net.minecraft.server.MinecraftServer.getServer()?.getPlayerList()?.getPlayers() ?: emptyList()) {
            val regionId = manager.playerRegionCache[player.uuid]
            if (regionId != null) {
                val region = manager.getRegion(regionId)
                val assault = region?.activeAssaultId?.let { manager.activeAssaults[it] }

                if (region != null && assault != null && assault.isActive()) {
                    val remaining = (assault.getRemainingTime() / 1000).toInt()
                    val leaderboard = region.getLeaderboard().take(5).map { score ->
                        NetworkHandler.LeaderboardEntry(
                            score.nationName,
                            score.score,
                            score.kills,
                            score.vehiclesDestroyed,
                            score.flagsCaptured
                        )
                    }

                    val state = when (region.state) {
                        com.tlgd.assault.region.AssaultState.INACTIVE -> 0
                        com.tlgd.assault.region.AssaultState.PEACEFUL -> 0
                        com.tlgd.assault.region.AssaultState.PREPARING -> 1
                        com.tlgd.assault.region.AssaultState.ACTIVE -> 2
                        com.tlgd.assault.region.AssaultState.COOLDOWN -> 3
                    }

                    NetworkHandler.sendAssaultHUD(player, region.id, region.displayName, remaining, state, leaderboard)
                }
            }
        }
    }

    private fun sendRegionMarkers(player: ServerPlayer) {
        val manager = AssaultRegionManager.getInstance()
        val dimension = player.level.dimension()

        val markers = manager.getRegionsInDimension(dimension).map { region ->
            NetworkHandler.RegionMarkerData(
                region.id,
                region.displayName,
                region.bounds.min.x, region.bounds.min.y, region.bounds.min.z,
                region.bounds.max.x, region.bounds.max.y, region.bounds.max.z,
                region.bounds.dimension.toString(),
                region.ownerNationName,
                when (region.state) {
                    com.tlgd.assault.region.AssaultState.INACTIVE -> 0
                    com.tlgd.assault.region.AssaultState.PEACEFUL -> 1
                    com.tlgd.assault.region.AssaultState.PREPARING -> 2
                    com.tlgd.assault.region.AssaultState.ACTIVE -> 3
                    com.tlgd.assault.region.AssaultState.COOLDOWN -> 4
                }
            )
        }

        if (markers.isNotEmpty()) {
            NetworkHandler.sendRegionMarkers(player, markers)
        }
    }

    private fun checkUpcomingWindows() {
        val manager = AssaultRegionManager.getInstance()
        val server = net.minecraft.server.MinecraftServer.getServer() ?: return

        for (region in manager.getAllRegions()) {
            val window = region.getNextWindow()
            if (window != null) {
                val timeUntil = window.getTimeUntilStart()
                if (timeUntil > 0 && timeUntil <= 30) { // 30 min antes
                    // Avisar nações donas e próximas
                    val message = "§e⚠ Janela de assalto em §f${region.displayName} §einicia em §f${timeUntil} §eminutos!"
                    NetworkHandler.sendGlobalAnnouncement(message, "minecraft:block.note_block.pling")
                }
            }
        }
    }

    private fun isVehicle(entity: Entity): Boolean {
        // Detectar veículos de mods conhecidos
        return NationIntegration.getVehicleClass(entity) != null ||
               entity is Boat || // Barcos vanilla
               entity.javaClass.simpleName.lowercase().contains("vehicle") ||
               entity.javaClass.simpleName.lowercase().contains("craft") ||
               entity.javaClass.simpleName.lowercase().contains("ship") ||
               entity.javaClass.simpleName.lowercase().contains("tank")
    }

    private fun isCaptureBlock(state: BlockState): Boolean {
        // Bloco de bandeira ou bloco customizado de captura
        return state.is(net.minecraft.tags.BlockTags.BANNERS) ||
               state.block.toString().contains("banner") ||
               state.block.toString().contains("flag")
    }
}