package com.tlgd.navalwarfare.event

import com.tlgd.navalwarfare.TLGAssaultMod
import com.tlgd.navalwarfare.vehicle.VehicleIntegrationManager
import com.tlgd.navalwarfare.region.AssaultRegionManager
import com.tlgd.navalwarfare.scoring.ScoringManager
import com.tlgd.navalwarfare.presence.PresenceManager
import com.tlgd.navalwarfare.objective.ObjectiveManager
import com.tlgd.navalwarfare.reward.RewardManager
import com.tlgd.navalwarfare.notification.NotificationManager
import com.tlgd.navalwarfare.history.BattleHistoryManager
import com.tlgd.navalwarfare.network.NetworkHandler
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.living.LivingHurtEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.level.BlockEvent
import net.neoforged.neoforge.event.level.ExplosionEvent
import net.neoforged.neoforge.event.server.ServerStartingEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent

@EventBusSubscriber(modid = TLGAssaultMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
object ServerEvents {

    @SubscribeEvent
    @JvmStatic
    fun onServerStarting(event: ServerStartingEvent) {
        val server = event.server

        // Registrar comandos
        server.commands.register(com.tlgd.navalwarfare.command.NavalWarfareCommands())

        // Carregar histórico de batalhas
        BattleHistoryManager.getInstance().loadHistory()

        // Carregar veículos de datapacks
        com.tlgd.navalwarfare.vehicle.VehicleRegistry.getInstance().loadFromDatapacks(server)

        // Iniciar agendadores periódicos
        PresenceManager.getInstance().startScheduler(server)
        RewardManager.getInstance().startScheduler(server)
        MapIntegration.getInstance().startScheduler(server)
        BattleHistoryManager.getInstance().startCleanupScheduler(server)

        com.mojang.logging.LogUtils.getLogger().info("[NavalWarfare] Servidor iniciado - sistemas ativos")
    }

    @SubscribeEvent
    @JvmStatic
    fun onServerStopping(event: ServerStoppingEvent) {
        // Parar schedulers
        PresenceManager.getInstance().stopScheduler()
        RewardManager.getInstance().stopScheduler()
        MapIntegration.getInstance().stopScheduler()
        BattleHistoryManager.getInstance().stopCleanupScheduler()

        // Salvar estado
        com.tlgd.navalwarfare.vehicle.VehicleRegistry.getInstance().saveState()
        AssaultRegionManager.getInstance().saveAll()
        ObjectiveManager.getInstance().saveAll()
        RewardManager.getInstance().saveAll()

        com.mojang.logging.LogUtils.getLogger().info("[NavalWarfare] Servidor parando - estado salvo")
    }

    @SubscribeEvent
    @JvmStatic
    fun onPlayerJoin(event: PlayerEvent.PlayerLoggedInEvent) {
        val player = event.entity as ServerPlayer
        val level = player.serverLevel

        // Notificar gerenciadores
        AssaultRegionManager.getInstance().onPlayerJoin(player)
        PresenceManager.getInstance().onPlayerJoin(player)
        ObjectiveManager.getInstance().onPlayerJoin(player)
        NotificationManager.getInstance().onPlayerJoin(player)

        // Enviar marcadores de região
        NetworkHandler.handleRequestAllRegions(NetworkHandler.RequestAllRegionsPacket(),
            net.neoforged.neoforge.network.handling.IPayloadContext.CLIENTBOUND.create(player.connection))
    }

    @SubscribeEvent
    @JvmStatic
    fun onPlayerLeave(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as ServerPlayer

        AssaultRegionManager.getInstance().onPlayerLeave(player)
        PresenceManager.getInstance().onPlayerLeave(player)
        ObjectiveManager.getInstance().onPlayerLeave(player)
    }

    @SubscribeEvent
    @JvmStatic
    fun onEntityDeath(event: LivingDeathEvent) {
        val entity = event.entity
        val source = event.source

        // Verificar se foi um veículo
        if (VehicleIntegrationManager.getInstance().isTrackedVehicle(entity)) {
            val vehicleData = VehicleIntegrationManager.getInstance().getVehicleData(entity.id)
            vehicleData?.let { data ->
                val destroyer = source.getEntity() as? ServerPlayer
                val destroyerNation = destroyer?.let { com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNationByPlayer(it)?.id }

                // Registrar destruição no scoring
                data.nationId?.let { ownerNation ->
                    ScoringManager.getInstance().recordVehicleDestroyed(
                        regionId = data.currentRegionId ?: "",
                        attackerNationId = destroyerNation ?: "",
                        vehicleClass = data.vehicleClass,
                        vehicleValue = data.vehicleClass.let { getVehicleScore(it) }
                    )
                }

                // Notificar
                NotificationManager.getInstance().notifyVehicleDestroyed(
                    vehicle = data,
                    destroyer = destroyer,
                    destroyerNation = destroyerNation ?: "Desconhecido"
                )

                // Remover do tracker
                VehicleIntegrationManager.getInstance().untrackVehicle(entity.id)
            }
        }

        // Player death
        if (entity is ServerPlayer) {
            val killer = source.getEntity() as? ServerPlayer
            killer?.let { k ->
                if (k != entity) {
                    val victimNation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNationByPlayer(entity)?.id
                    val killerNation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNationByPlayer(k)?.id

                    victimNation?.let { vNation ->
                        killerNation?.let { kNation ->
                            if (vNation != kNation) {
                                // Verificar anti-farm
                                if (ScoringManager.getInstance().validateKill(k, entity, kNation, vNation)) {
                                    ScoringManager.getInstance().recordPlayerKill(
                                        regionId = getCurrentAssaultRegion(entity) ?: "",
                                        killerNationId = kNation,
                                        victimNationId = vNation
                                    )

                                    NotificationManager.getInstance().notifyPlayerKill(
                                        killer = k,
                                        victim = entity,
                                        killerNation = kNation,
                                        victimNation = vNation
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onEntityHurt(event: LivingHurtEvent) {
        val entity = event.entity
        val source = event.source

        // Track vehicle damage for scoring
        if (VehicleIntegrationManager.getInstance().isTrackedVehicle(entity)) {
            val vehicleData = VehicleIntegrationManager.getInstance().getVehicleData(entity.id)
            vehicleData?.let { data ->
                // Registrar dano no veículo para possível assist
                data.lastDamageSource = source
                data.lastDamageTime = System.currentTimeMillis()
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onBlockBreak(event: BlockEvent.BreakEvent) {
        val player = event.player as? ServerPlayer ?: return
        val pos = event.pos
        val state = event.state

        // Verificar se está em região de assalto ativa
        val region = AssaultRegionManager.getInstance().getRegionAt(pos)
        region?.takeIf { it.state == com.tlgd.navalwarfare.region.AssaultState.ACTIVE }?.let { activeRegion ->
            val nation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNationByPlayer(player)?.id
            nation?.let { nationId ->
                // Verificar se é bloco de objetivo
                ObjectiveManager.getInstance().onBlockBroken(activeRegion.id, pos, state, player, nationId)
            }
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onExplosion(event: ExplosionEvent.Detonate) {
        val level = event.level
        if (level.isClientSide) return

        val center = event.explosion.center
        val radius = event.explosion.radius

        // Verificar veículos na área
        val vehicles = VehicleIntegrationManager.getInstance().getVehiclesInRadius(
            center.x, center.y, center.z, radius + 10
        )

        for (vehicle in vehicles) {
            // Aplicar dano baseado na distância
            val distance = Vec3(vehicle.position.x, vehicle.position.y, vehicle.position.z).distanceTo(center)
            val damageRatio = 1.0 - (distance / (radius + 10))
            val damage = (event.explosion.radius * 10 * damageRatio).toInt()

            if (damage > 0) {
                vehicle.entity.hurt(event.explosion.explosionDamageSource, damage.toFloat())
            }
        }

        // Verificar objetivos afetados
        val region = AssaultRegionManager.getInstance().getRegionAt(BlockPos(center.x.toInt(), center.y.toInt(), center.z.toInt()))
        region?.takeIf { it.state == com.tlgd.navalwarfare.region.AssaultState.ACTIVE }?.let { activeRegion ->
            ObjectiveManager.getInstance().onExplosion(activeRegion.id, center, radius)
        }
    }

    @SubscribeEvent
    @JvmStatic
    fun onVehicleSpawn(event: EntityJoinLevelEvent) {
        val entity = event.entity
        val level = entity.level()

        if (level.isClientSide) return

        // Auto-detectar veículos
        VehicleIntegrationManager.getInstance().tryDetectAndTrack(entity)
    }

    @SubscribeEvent
    @JvmStatic
    fun onVehicleDespawn(event: EntityLeaveLevelEvent) {
        val entity = event.entity

        if (VehicleIntegrationManager.getInstance().isTrackedVehicle(entity)) {
            VehicleIntegrationManager.getInstance().untrackVehicle(entity.id)
        }
    }

    // ===== Helpers =====

    private fun getCurrentAssaultRegion(entity: Entity): String? {
        val pos = entity.blockPosition()
        return AssaultRegionManager.getInstance().getRegionAt(pos)
            ?.takeIf { it.state == com.tlgd.navalwarfare.region.AssaultState.ACTIVE }
            ?.id
    }

    private fun getVehicleScore(vehicleClass: String): Int {
        return when (vehicleClass) {
            "patrol_boat" -> com.tlgd.navalwarfare.config.NavalWarfareConfig.patrolBoatScore.get()
            "destroyer" -> com.tlgd.navalwarfare.config.NavalWarfareConfig.destroyerScore.get()
            "frigate" -> com.tlgd.navalwarfare.config.NavalWarfareConfig.frigateScore.get()
            "cruiser" -> com.tlgd.navalwarfare.config.NavalWarfareConfig.cruiserScore.get()
            "battlecruiser" -> com.tlgd.navalwarfare.config.NavalWarfareConfig.battlecruiserScore.get()
            "battleship" -> com.tlgd.navalwarfare.config.NavalWarfareConfig.battleshipScore.get()
            "dreadnought" -> com.tlgd.navalwarfare.config.NavalWarfareConfig.dreadnoughtScore.get()
            "aircraft_carrier" -> com.tlgd.navalwarfare.config.NavalWarfareConfig.aircraftCarrierScore.get()
            "submarine" -> com.tlgd.navalwarfare.config.NavalWarfareConfig.submarineScore.get()
            "transport" -> com.tlgd.navalwarfare.config.NavalWarfareConfig.transportScore.get()
            "landing_ship" -> com.tlgd.navalwarfare.config.NavalWarfareConfig.landingShipScore.get()
            "minelayer" -> com.tlgd.navalwarfare.config.NavalWarfareConfig.minelayerScore.get()
            else -> 10
        }
    }
}