package com.tlgd.navalwarfare.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.DoubleArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.tlgd.navalwarfare.TLGAssaultMod
import com.tlgd.navalwarfare.config.NavalWarfareConfig
import com.tlgd.navalwarfare.history.BattleHistoryManager
import com.tlgd.navalwarfare.map.MapIntegration
import com.tlgd.navalwarfare.nation.NationIntegration
import com.tlgd.navalwarfare.notification.NotificationManager
import com.tlgd.navalwarfare.objective.ObjectiveManager
import com.tlgd.navalwarfare.objective.objectives.AreaControlObjective
import com.tlgd.navalwarfare.objective.objectives.DestroyTargetObjective
import com.tlgd.navalwarfare.objective.objectives.EscortObjective
import com.tlgd.navalwarfare.objective.objectives.MultiPointObjective
import com.tlgd.navalwarfare.objective.objectives.NavalSuperiorityObjective
import com.tlgd.navalwarfare.reward.RewardManager
import com.tlgd.navalwarfare.region.AssaultRegion
import com.tlgd.navalwarfare.region.AssaultRegionManager
import com.tlgd.navalwarfare.scoring.ScoringManager
import com.tlgd.navalwarfare.vehicle.VehicleIntegrationManager
import com.tlgd.navalwarfare.vehicle.VehicleRegistry
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.ResourceLocationArgument
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import java.util.UUID

class NavalWarfareCommands {

    companion object {
        private val LOGGER = com.mojang.logging.LogUtils.getLogger()
    }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("navalwarfare")
            .requires { it.hasPermission(2) }

        // ===== REGIÕES =====
        root.then(Commands.literal("region")
            .then(Commands.literal("create")
                .then(Commands.argument("id", StringArgumentType.string())
                    .then(Commands.argument("name", StringArgumentType.greedyString())
                        .then(Commands.argument("minPos", BlockPosArgument.blockPos())
                            .then(Commands.argument("maxPos", BlockPosArgument.blockPos())
                                .executes { ctx ->
                                    createRegion(ctx)
                                }
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("delete")
                .then(Commands.argument("id", StringArgumentType.string())
                    .executes { ctx -> deleteRegion(ctx) }
                )
            )
            .then(Commands.literal("list")
                .executes { ctx -> listRegions(ctx) }
            )
            .then(Commands.literal("info")
                .then(Commands.argument("id", StringArgumentType.string())
                    .executes { ctx -> infoRegion(ctx) }
                )
            )
            .then(Commands.literal("setowner")
                .then(Commands.argument("id", StringArgumentType.string())
                    .then(Commands.argument("nation", StringArgumentType.string())
                        .executes { ctx -> setRegionOwner(ctx) }
                    )
                )
            )
            .then(Commands.literal("tp")
                .then(Commands.argument("id", StringArgumentType.string())
                    .executes { ctx -> tpToRegion(ctx) }
                )
            )
        )

        // ===== JANELAS DE ASSALTO =====
        root.then(Commands.literal("window")
            .then(Commands.literal("add")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .then(Commands.argument("day", IntegerArgumentType.integer(0, 6))
                        .then(Commands.argument("time", StringArgumentType.string()) // HH:MM
                            .then(Commands.argument("duration", IntegerArgumentType.integer(5, 120))
                                .executes { ctx -> addWindow(ctx) }
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .then(Commands.argument("index", IntegerArgumentType.integer(0))
                        .executes { ctx -> removeWindow(ctx) }
                    )
                )
            )
            .then(Commands.literal("list")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .executes { ctx -> listWindows(ctx) }
                )
            )
        )

        // ===== ASSALTO =====
        root.then(Commands.literal("assault")
            .then(Commands.literal("begin")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .executes { ctx -> beginAssault(ctx) }
                )
            )
            .then(Commands.literal("end")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .executes { ctx -> endAssault(ctx) }
                )
            )
            .then(Commands.literal("forceend")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .executes { ctx -> forceEndAssault(ctx) }
                )
            )
            .then(Commands.literal("status")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .executes { ctx -> assaultStatus(ctx) }
                )
            )
        )

        // ===== OBJETIVOS =====
        root.then(Commands.literal("objective")
            .then(Commands.literal("add")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .then(Commands.argument("type", StringArgumentType.string())
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                            .executes { ctx -> addObjective(ctx) }
                        )
                    )
                )
            )
            .then(Commands.literal("remove")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .then(Commands.argument("objectiveId", StringArgumentType.string())
                        .executes { ctx -> removeObjective(ctx) }
                    )
                )
            )
            .then(Commands.literal("list")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .executes { ctx -> listObjectives(ctx) }
                )
            )
            .then(Commands.literal("complete")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .then(Commands.argument("objectiveId", StringArgumentType.string())
                        .then(Commands.argument("nation", StringArgumentType.string())
                            .executes { ctx -> completeObjective(ctx) }
                        )
                    )
                )
            )
        )

        // ===== RECOMPENSAS =====
        root.then(Commands.literal("reward")
            .then(Commands.literal("set")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .then(Commands.argument("dailyMoney", IntegerArgumentType.integer(0))
                        .executes { ctx -> setRewards(ctx) }
                    )
                )
            )
            .then(Commands.literal("additem")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .then(Commands.argument("item", ResourceLocationArgument.resourceLocation())
                        .then(Commands.argument("count", IntegerArgumentType.integer(1))
                            .then(Commands.argument("weight", IntegerArgumentType.integer(1))
                                .executes { ctx -> addRewardItem(ctx) }
                            )
                        )
                    )
                )
            )
            .then(Commands.literal("list")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .executes { ctx -> listRewards(ctx) }
                )
            )
            .then(Commands.literal("give")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .then(Commands.argument("nation", StringArgumentType.string())
                        .executes { ctx -> giveRewards(ctx) }
                    )
                )
            )
        )

        // ===== VEÍCULOS =====
        root.then(Commands.literal("vehicle")
            .then(Commands.literal("register")
                .then(Commands.argument("classId", StringArgumentType.string())
                    .then(Commands.argument("customName", StringArgumentType.greedyString())
                        .executes { ctx -> registerVehicle(ctx) }
                    )
                )
            )
            .then(Commands.literal("list")
                .executes { ctx -> listVehicles(ctx) }
            )
            .then(Commands.literal("reload")
                .executes { ctx -> reloadVehicles(ctx) }
            )
            .then(Commands.literal("classes")
                .executes { ctx -> listVehicleClasses(ctx) }
            )
        )

        // ===== SCORE/SCORING =====
        root.then(Commands.literal("score")
            .then(Commands.literal("show")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .executes { ctx -> showScore(ctx) }
                )
            )
            .then(Commands.literal("reset")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .executes { ctx -> resetScore(ctx) }
                )
            )
            .then(Commands.literal("setkill")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .then(Commands.argument("nation", StringArgumentType.string())
                        .then(Commands.argument("kills", IntegerArgumentType.integer(0))
                            .executes { ctx -> setKills(ctx) }
                        )
                    )
                )
            )
        )

        // ===== HISTÓRICO/RANKINGS =====
        root.then(Commands.literal("history")
            .then(Commands.literal("list")
                .executes { ctx -> listHistory(ctx) }
            )
            .then(Commands.literal("region")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .executes { ctx -> historyByRegion(ctx) }
                )
            )
            .then(Commands.literal("nation")
                .then(Commands.argument("nationId", StringArgumentType.string())
                    .executes { ctx -> historyByNation(ctx) }
                )
            )
            .then(Commands.literal("export")
                .then(Commands.argument("format", StringArgumentType.string()) // csv, json
                    .executes { ctx -> exportHistory(ctx) }
                )
            )
        )

        root.then(Commands.literal("ranking")
            .then(Commands.literal("nations")
                .executes { ctx -> rankingNations(ctx) }
            )
            .then(Commands.literal("players")
                .executes { ctx -> rankingPlayers(ctx) }
            )
            .then(Commands.literal("region")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .executes { ctx -> rankingRegion(ctx) }
                )
            )
        )

        // ===== MAPA =====
        root.then(Commands.literal("map")
            .then(Commands.literal("export")
                .executes { ctx -> exportMap(ctx) }
            )
            .then(Commands.literal("refresh")
                .executes { ctx -> refreshMap(ctx) }
            )
        )

        // ===== NOTIFICAÇÕES =====
        root.then(Commands.literal("notify")
            .then(Commands.literal("test")
                .then(Commands.argument("type", StringArgumentType.string()) // assault_start, assault_end, victory, etc.
                    .executes { ctx -> testNotification(ctx) }
                )
            )
            .then(Commands.literal("broadcast")
                .then(Commands.argument("message", StringArgumentType.greedyString())
                    .executes { ctx -> broadcastMessage(ctx) }
                )
            )
        )

        // ===== ADMIN/DEBUG =====
        root.then(Commands.literal("admin")
            .requires { it.hasPermission(3) }
            .then(Commands.literal("reload")
                .executes { ctx -> reloadConfig(ctx) }
            )
            .then(Commands.literal("debug")
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                    .executes { ctx -> setDebug(ctx) }
                )
            )
            .then(Commands.literal("cleanup")
                .executes { ctx -> cleanup(ctx) }
            )
            .then(Commands.literal("simulate")
                .then(Commands.argument("regionId", StringArgumentType.string())
                    .executes { ctx -> simulateAssault(ctx) }
                )
            )
        )

        // ===== AJUDA =====
        root.then(Commands.literal("help")
            .executes { ctx -> showHelp(ctx) }
        )

        dispatcher.register(root)
    }

    // ===== IMPLEMENTAÇÕES =====

    private fun createRegion(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: return 0
        val id = StringArgumentType.getString(ctx, "id").lowercase()
        val name = StringArgumentType.getString(ctx, "name")
        val minPos = BlockPosArgument.getLoadedBlockPos(ctx, "minPos")
        val maxPos = BlockPosArgument.getLoadedBlockPos(ctx, "maxPos")
        val dimension = player.level.dimension()

        val manager = AssaultRegionManager.getInstance()

        if (manager.getRegion(id) != null) {
            source.sendFailure(Component.literal("§cRegião '$id' já existe!"))
            return 0
        }

        // Validar tamanho
        val area = (maxPos.x - minPos.x + 1L) * (maxPos.z - minPos.z + 1L)
        if (area < NavalWarfareConfig.regionMinSize.get() || area > NavalWarfareConfig.regionMaxSize.get()) {
            source.sendFailure(Component.literal("§cÁrea inválida! Min: ${NavalWarfareConfig.regionMinSize.get()}, Max: ${NavalWarfareConfig.regionMaxSize.get()}"))
            return 0
        }

        val region = AssaultRegion(
            id = id,
            displayName = name,
            bounds = com.tlgd.navalwarfare.region.RegionBounds(minPos, maxPos, dimension),
            attackWindows = emptyList(),
            ownerNationId = null,
            ownerNationName = null,
            createdAt = System.currentTimeMillis(),
            lastCaptureTime = 0
        )

        manager.addRegion(region)
        source.sendSuccess(() -> Component.literal("§aRegião '$id' criada: $name ($area blocos²)"), true)
        return 1
    }

    private fun deleteRegion(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val id = StringArgumentType.getString(ctx, "id")
        val manager = AssaultRegionManager.getInstance()

        if (manager.removeRegion(id)) {
            source.sendSuccess(() -> Component.literal("§aRegião '$id' removida"), true)
        } else {
            source.sendFailure(Component.literal("§cRegião '$id' não encontrada"))
        }
        return 1
    }

    private fun listRegions(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val manager = AssaultRegionManager.getInstance()
        val regions = manager.getAllRegions()

        if (regions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Nenhuma região cadastrada"), false)
            return 1
        }

        source.sendSuccess(() -> Component.literal("§e§l=== REGIÕES (${regions.size}) ==="), false)
        for (region in regions) {
            val owner = region.ownerNationName ?: "§7Nenhuma"
            val state = region.state.name
            source.sendSuccess(() -> Component.literal("  §f${region.id} §7- §e${region.displayName} §7| Dona: $owner §7| Estado: $state"), false)
        }
        return 1
    }

    private fun infoRegion(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val id = StringArgumentType.getString(ctx, "id")
        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(id) ?: return 0

        source.sendSuccess(() -> Component.literal("§e§l=== INFO: ${region.displayName} ==="), false)
        source.sendSuccess(() -> Component.literal("  §7ID: §f${region.id}"), false)
        source.sendSuccess(() -> Component.literal("  §7Dim: §f${region.bounds.dimension}"), false)
        source.sendSuccess(() -> Component.literal("  §7Bounds: §f(${region.bounds.min.x},${region.bounds.min.y},${region.bounds.min.z}) §7-> §f(${region.bounds.max.x},${region.bounds.max.y},${region.bounds.max.z})"), false)
        source.sendSuccess(() -> Component.literal("  §7Área: §f${(region.bounds.max.x - region.bounds.min.x + 1L) * (region.bounds.max.z - region.bounds.min.z + 1L)} blocos²"), false)
        source.sendSuccess(() -> Component.literal("  §7Dona: §f${region.ownerNationName ?: "Nenhuma"}"), false)
        source.sendSuccess(() -> Component.literal("  §7Estado: §f${region.state}"), false)
        source.sendSuccess(() -> Component.literal("  §7Janelas: §f${region.attackWindows.size}"), false)
        for ((i, w) in region.attackWindows.withIndex()) {
            source.sendSuccess(() -> Component.literal("    §7[$i] §f${w.dayOfWeek.name} ${w.startTime} (${w.durationMinutes}min)"), false)
        }
        return 1
    }

    private fun setRegionOwner(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val id = StringArgumentType.getString(ctx, "id")
        val nation = StringArgumentType.getString(ctx, "nation")
        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(id) ?: return 0

        val nationObj = NationIntegration.getNationById(nation) ?: NationIntegration.getNationByName(nation)
        if (nationObj == null) {
            source.sendFailure(Component.literal("§cNação '$nation' não encontrada"))
            return 0
        }

        region.ownerNationId = nationObj.id
        region.ownerNationName = nationObj.name
        region.lastCaptureTime = System.currentTimeMillis()
        manager.saveAll()

        source.sendSuccess(() -> Component.literal("§aDona da região '$id' definida para ${nationObj.name}"), true)
        NotificationManager.getInstance().notifyRegionCaptured(region, nationObj.name)
        return 1
    }

    private fun tpToRegion(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: return 0
        val id = StringArgumentType.getString(ctx, "id")
        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(id) ?: return 0

        val cx = (region.bounds.min.x + region.bounds.max.x) / 2
        val cz = (region.bounds.min.z + region.bounds.max.z) / 2
        val cy = region.bounds.max.y + 5

        player.teleportTo(region.bounds.dimension, cx.toDouble(), cy.toDouble(), cz.toDouble(), player.getYRot(), player.getXRot())
        source.sendSuccess(() -> Component.literal("§aTeleportado para região '$id'"), true)
        return 1
    }

    // ... continuar com os outros comandos
    private fun addWindow(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val day = IntegerArgumentType.getInteger(ctx, "day")
        val time = StringArgumentType.getString(ctx, "time")
        val duration = IntegerArgumentType.getInteger(ctx, "duration")

        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(regionId) ?: return 0

        val dayOfWeek = java.time.DayOfWeek.of(day + 1) // 0=MONDAY
        val window = AssaultRegion.AttackWindow(dayOfWeek, java.time.LocalTime.parse(time), duration)
        region.attackWindows += window
        manager.saveAll()

        source.sendSuccess(() -> Component.literal("§aJanela adicionada a '$regionId': ${dayOfWeek.name} $time (${duration}min)"), true)
        return 1
    }

    private fun removeWindow(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val index = IntegerArgumentType.getInteger(ctx, "index")

        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(regionId) ?: return 0

        if (index >= 0 && index < region.attackWindows.size) {
            region.attackWindows.removeAt(index)
            manager.saveAll()
            source.sendSuccess(() -> Component.literal("§aJanela $index removida"), true)
        } else {
            source.sendFailure(Component.literal("§cÍndice inválido"))
        }
        return 1
    }

    private fun listWindows(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(regionId) ?: return 0

        source.sendSuccess(() -> Component.literal("§eJanelas de $regionId:"), false)
        for ((i, w) in region.attackWindows.withIndex()) {
            val next = if (i == region.nextWindowIndex) " §a§l(PRÓXIMA)" else ""
            source.sendSuccess(() -> Component.literal("  §7[$i] §f${w.dayOfWeek.name} ${w.startTime} (${w.durationMinutes}min)$next"), false)
        }
        return 1
    }

    private fun beginAssault(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: return 0
        val regionId = StringArgumentType.getString(ctx, "regionId")

        val nation = NationIntegration.getPlayerNationByPlayer(player)
        if (nation == null && NavalWarfareConfig.requireNationForAssault.get()) {
            source.sendFailure(Component.literal("§cVocê precisa estar em uma nação para iniciar um assalto!"))
            return 0
        }

        val requiredRank = NavalWarfareConfig.requiredNationRank.get()
        if (!NationIntegration.hasRank(player, requiredRank)) {
            source.sendFailure(Component.literal("§cVocê precisa ser $requiredRank ou superior para declarar assalto!"))
            return 0
        }

        val manager = AssaultRegionManager.getInstance()
        val success = manager.startAssault(regionId, nation?.id ?: player.uuid.toString())

        if (success) {
            source.sendSuccess(() -> Component.literal("§aAssalto iniciado na região '$regionId'!"), true)
        } else {
            source.sendFailure(Component.literal("§cNão foi possível iniciar assalto (verifique janelas, cooldown, jogadores online)"))
        }
        return 1
    }

    private fun endAssault(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val manager = AssaultRegionManager.getInstance()
        manager.endAssault(regionId)
        source.sendSuccess(() -> Component.literal("§aAssalto finalizado em '$regionId'"), true)
        return 1
    }

    private fun forceEndAssault(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val manager = AssaultRegionManager.getInstance()
        manager.forceEndAssault(regionId)
        source.sendSuccess(() -> Component.literal("§cAssalto forçado a terminar em '$regionId'"), true)
        return 1
    }

    private fun assaultStatus(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val manager = AssaultRegionManager.getInstance()
        val assault = manager.getActiveAssault(regionId)

        if (assault == null) {
            source.sendSuccess(() -> Component.literal("§7Nenhum assalto ativo em '$regionId'"), false)
            return 1
        }

        val remaining = (assault.endsAt - System.currentTimeMillis()) / 1000
        val scoring = ScoringManager.getInstance().getAllScores(regionId)

        source.sendSuccess(() -> Component.literal("§e§l=== ASSALTO ATIVO: $regionId ==="), false)
        source.sendSuccess(() -> Component.literal("  §7Tempo restante: §f${formatTime(remaining)}"), false)
        source.sendSuccess(() -> Component.literal("  §7Estado: §f${assault.state}"), false)

        for ((nationId, score) in scoring) {
            val nation = NationIntegration.getNationById(nationId)
            val name = nation?.name ?: nationId
            source.sendSuccess(() -> Component.literal("  §f$name §7- §e$score pontos"), false)
        }
        return 1
    }

    private fun addObjective(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val type = StringArgumentType.getString(ctx, "type").lowercase()
        val name = StringArgumentType.getString(ctx, "name")

        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(regionId) ?: return 0
        val objManager = ObjectiveManager.getInstance()

        val objective = when (type) {
            "areacontrol", "control" -> AreaControlObjective(
                id = UUID.randomUUID().toString(),
                regionId = regionId,
                displayName = name,
                description = "Controlar área",
                centerX = (region.bounds.min.x + region.bounds.max.x) / 2,
                centerZ = (region.bounds.min.z + region.bounds.max.z) / 2,
                radius = 100,
                requiredTimeSeconds = 300,
                rewardPoints = 500,
                requiredForVictory = false
            )
            "escort" -> EscortObjective(
                id = UUID.randomUUID().toString(),
                regionId = regionId,
                displayName = name,
                description = "Escoltar comboio",
                startX = region.bounds.min.x.toDouble(),
                startZ = region.bounds.min.z.toDouble(),
                endX = region.bounds.max.x.toDouble(),
                endZ = region.bounds.max.z.toDouble(),
                requiredVehicles = 3,
                rewardPoints = 400,
                requiredForVictory = false
            )
            "destroy", "destroytarget" -> DestroyTargetObjective(
                id = UUID.randomUUID().toString(),
                regionId = regionId,
                displayName = name,
                description = "Destruir alvo",
                targetX = (region.bounds.min.x + region.bounds.max.x) / 2,
                targetY = region.bounds.max.y,
                targetZ = (region.bounds.min.z + region.bounds.max.z) / 2,
                targetType = "structure",
                requiredDamage = 10000,
                rewardPoints = 600,
                requiredForVictory = false
            )
            "superiority", "naval" -> NavalSuperiorityObjective(
                id = UUID.randomUUID().toString(),
                regionId = regionId,
                displayName = name,
                description = "Superioridade naval",
                minShips = 5,
                requiredClasses = listOf("destroyer", "frigate", "cruiser"),
                durationSeconds = 600,
                rewardPoints = 800,
                requiredForVictory = false
            )
            "multipoint", "multi" -> MultiPointObjective(
                id = UUID.randomUUID().toString(),
                regionId = regionId,
                displayName = name,
                description = "Múltiplos pontos",
                points = listOf(),
                requiredPoints = 3,
                captureTimeSeconds = 60,
                rewardPoints = 1000,
                requiredForVictory = false
            )
            else -> {
                source.sendFailure(Component.literal("§cTipo inválido: areacontrol, escort, destroy, superiority, multipoint"))
                return 0
            }
        }

        objManager.addObjective(objective)
        source.sendSuccess(() -> Component.literal("§aObjetivo '$name' ($type) adicionado a '$regionId'"), true)
        return 1
    }

    private fun removeObjective(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val objectiveId = StringArgumentType.getString(ctx, "objectiveId")

        val objManager = ObjectiveManager.getInstance()
        objManager.removeObjective(objectiveId)
        source.sendSuccess(() -> Component.literal("§aObjetivo '$objectiveId' removido"), true)
        return 1
    }

    private fun listObjectives(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val objManager = ObjectiveManager.getInstance()
        val objectives = objManager.getRegionObjectives(regionId)

        if (objectives.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Nenhum objetivo em '$regionId'"), false)
            return 1
        }

        source.sendSuccess(() -> Component.literal("§eObjetivos em $regionId:"), false)
        for (obj in objectives) {
            val req = if (obj.isRequiredForVictory) " §c§l★" else ""
            source.sendSuccess(() -> Component.literal("  §f${obj.id} §7- §e${obj.displayName}$req §7(${obj.javaClass.simpleName}) §7| §f${obj.state} §7| §e${(obj.progress * 100).toInt()}%"), false)
        }
        return 1
    }

    private fun completeObjective(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val objectiveId = StringArgumentType.getString(ctx, "objectiveId")
        val nationId = StringArgumentType.getString(ctx, "nation")

        val objManager = ObjectiveManager.getInstance()
        objManager.completeObjective(objectiveId, nationId)
        source.sendSuccess(() -> Component.literal("§aObjetivo '$objectiveId' completado para '$nationId'"), true)
        return 1
    }

    private fun setRewards(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val dailyMoney = IntegerArgumentType.getInteger(ctx, "dailyMoney")

        val rewardManager = RewardManager.getInstance()
        rewardManager.setRegionRewards(regionId, dailyMoney.toLong())
        source.sendSuccess(() -> Component.literal("§aRecompensa diária de '$regionId' definida para $${dailyMoney}"), true)
        return 1
    }

    private fun addRewardItem(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val item = ResourceLocationArgument.getResourceLocation(ctx, "item")
        val count = IntegerArgumentType.getInteger(ctx, "count")
        val weight = IntegerArgumentType.getInteger(ctx, "weight")

        val rewardManager = RewardManager.getInstance()
        rewardManager.addRewardItem(regionId, item.toString(), count, weight)
        source.sendSuccess(() -> Component.literal("§aItem $item x$count (peso $weight) adicionado às recompensas de '$regionId'"), true)
        return 1
    }

    private fun listRewards(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val rewardManager = RewardManager.getInstance()
        val rewards = rewardManager.getRegionRewards(regionId)

        if (rewards == null) {
            source.sendSuccess(() -> Component.literal("§7Nenhuma recompensa configurada para '$regionId'"), false)
            return 1
        }

        source.sendSuccess(() -> Component.literal("§eRecompensas de $regionId:"), false)
        source.sendSuccess(() -> Component.literal("  §7Dinheiro diário: §a$${rewards.dailyMoney}"), false)
        for (item in rewards.dailyItems) {
            source.sendSuccess(() -> Component.literal("  §7Item: §f${item.itemId} x${item.count} §7(peso: ${item.weight})"), false)
        }
        return 1
    }

    private fun giveRewards(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val nationId = StringArgumentType.getString(ctx, "nation")

        val rewardManager = RewardManager.getInstance()
        rewardManager.giveDailyRewards(regionId, nationId)
        source.sendSuccess(() -> Component.literal("§aRecompensas entregues para '$nationId' em '$regionId'"), true)
        return 1
    }

    private fun registerVehicle(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: return 0
        val classId = StringArgumentType.getString(ctx, "classId")
        val customName = StringArgumentType.getString(ctx, "customName")

        val vehicle = VehicleIntegrationManager.getInstance().getVehiclePlayerIsIn(player)
        if (vehicle == null) {
            source.sendFailure(Component.literal("§cVocê não está em um veículo válido!"))
            return 0
        }

        val registry = VehicleRegistry.getInstance()
        val vClass = registry.getClass(classId) ?: return 0

        VehicleIntegrationManager.getInstance().registerVehicle(vehicle, vClass, customName, player.uuid.toString())
        source.sendSuccess(() -> Component.literal("§aVeículo registrado como $classId: $customName"), true)
        return 1
    }

    private fun listVehicles(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val vehicles = VehicleIntegrationManager.getInstance().getAllTrackedVehicles()

        source.sendSuccess(() -> Component.literal("§eVeículos rastreados (${vehicles.size}):"), false)
        for (v in vehicles.take(20)) {
            source.sendSuccess(() -> Component.literal("  §f${v.id} §7- §e${v.vehicleClass?.id ?: "?"} §7| Nação: ${v.nationId ?: "N/A"} §7| Trip: ${v.crew.size}"), false)
        }
        return 1
    }

    private fun reloadVehicles(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        VehicleRegistry.getInstance().loadFromDatapacks(source.server)
        source.sendSuccess(() -> Component.literal("§aClasses de veículo recarregadas dos datapacks"), true)
        return 1
    }

    private fun listVehicleClasses(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val registry = VehicleRegistry.getInstance()
        val classes = registry.getAllClasses()

        source.sendSuccess(() -> Component.literal("§eClasses de veículo (${classes.size}):"), false)
        for (vc in classes) {
            val req = vc.requiredBlocks.joinToString(", ") { "${it.key}=${it.value}" }
            source.sendSuccess(() -> Component.literal("  §f${vc.id} §7- §e${vc.displayName} §7| §f${vc.minLength}x${vc.minWidth}x${vc.minHeight} §7| §e${vc.scoreValue} pts §7| Req: $req"), false)
        }
        return 1
    }

    private fun showScore(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val scoring = ScoringManager.getInstance().getAllScores(regionId)

        source.sendSuccess(() -> Component.literal("§ePontuação em $regionId:"), false)
        for ((nationId, score) in scoring) {
            val nation = NationIntegration.getNationById(nationId)
            val name = nation?.name ?: nationId
            val details = ScoringManager.getInstance().getScore(regionId, nationId)
            source.sendSuccess(() -> Component.literal("  §f$name §7- §e$score pts §7(Kills: ${details?.playerKills ?: 0}, Veic: ${details?.vehicleDestroys ?: 0}, Pres: ${details?.presencePoints ?: 0}, Obj: ${details?.objectivePoints ?: 0})"), false)
        }
        return 1
    }

    private fun resetScore(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        ScoringManager.getInstance().resetScores(regionId)
        source.sendSuccess(() -> Component.literal("§aPontuação de '$regionId' resetada"), true)
        return 1
    }

    private fun setKills(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val nationId = StringArgumentType.getString(ctx, "nation")
        val kills = IntegerArgumentType.getInteger(ctx, "kills")

        ScoringManager.getInstance().setPlayerKills(regionId, nationId, kills)
        source.sendSuccess(() -> Component.literal("§aKills de '$nationId' em '$regionId' definidos para $kills"), true)
        return 1
    }

    private fun listHistory(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val history = BattleHistoryManager.getInstance()
        val battles = history.getAllBattles(20)

        source.sendSuccess(() -> Component.literal("§e§l=== ÚLTIMAS BATALHAS ==="), false)
        for (battle in battles) {
            val winner = battle.winnerNationName ?: "Empate"
            source.sendSuccess(() -> Component.literal("  §7${battle.getDateString()} §f${battle.regionName} §7- §e$winner §7| §f${battle.durationSeconds}s §7| §c${battle.totalKills} kills §7| §e${battle.totalVehiclesDestroyed} veic"), false)
        }
        return 1
    }

    private fun historyByRegion(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val history = BattleHistoryManager.getInstance()
        val battles = history.getBattlesByRegion(regionId, 20)

        source.sendSuccess(() -> Component.literal("§eBatalhas em $regionId:"), false)
        for (battle in battles) {
            val winner = battle.winnerNationName ?: "Empate"
            source.sendSuccess(() -> Component.literal("  §7${battle.getDateString()} §7- §e$winner §7| §f${battle.finalScores}"), false)
        }
        return 1
    }

    private fun historyByNation(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val nationId = StringArgumentType.getString(ctx, "nationId")
        val history = BattleHistoryManager.getInstance()
        val battles = history.getBattlesByNation(nationId, 20)

        source.sendSuccess(() -> Component.literal("§eBatalhas da nação $nationId:"), false)
        for (battle in battles) {
            val winner = battle.winnerNationName ?: "Empate"
            source.sendSuccess(() -> Component.literal("  §7${battle.getDateString()} §f${battle.regionName} §7- §e$winner"), false)
        }
        return 1
    }

    private fun exportHistory(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val format = StringArgumentType.getString(ctx, "format").lowercase()
        val history = BattleHistoryManager.getInstance()
        val server = source.server
        val worldDir = server.getWorldPath(net.minecraft.server.packs.resources.ServerResources.WORLD_DIR).toFile()
        val file = java.io.File(worldDir, "navalwarfare_history/export_${java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))}.${if (format == "csv") "csv" else "json"}")

        if (format == "csv") {
            history.exportToCSV(file)
        } else {
            history.exportToJSON(file)
        }
        source.sendSuccess(() -> Component.literal("§aHistórico exportado para: ${file.absolutePath}"), true)
        return 1
    }

    private fun rankingNations(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val history = BattleHistoryManager.getInstance()
        val rankings = history.getNationRankings(10)

        source.sendSuccess(() -> Component.literal("§e§l=== RANKING DE NAÇÕES ==="), false)
        for ((i, r) in rankings.withIndex()) {
            val medal = when (i) { 0 -> "🥇" ; 1 -> "🥈" ; 2 -> "🥉" ; else -> "${i+1}." }
            source.sendSuccess(() -> Component.literal("  §f$medal §e${r.nationName} §7| Vitórias: §a${r.victories} §7| Derrotas: §c${r.defeats} §7| WinRate: §e${(r.winRate * 100).toInt()}% §7| Score: §f${r.totalScore}"), false)
        }
        return 1
    }

    private fun rankingPlayers(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val history = BattleHistoryManager.getInstance()
        val rankings = history.getPlayerRankings(10)

        source.sendSuccess(() -> Component.literal("§e§l=== RANKING DE JOGADORES ==="), false)
        for ((i, r) in rankings.withIndex()) {
            val medal = when (i) { 0 -> "🥇" ; 1 -> "🥈" ; 2 -> "🥉" ; else -> "${i+1}." }
            val nation = r.nationName?.let { " [$it]" } ?: ""
            source.sendSuccess(() -> Component.literal("  §f$medal §e${r.playerName}$nation §7| K/D: §f${String.format("%.2f", r.kdRatio)} §7| Kills: §c${r.totalKills} §7| Vitórias: §a${r.battlesWon} §7| MVP: §e${r.mvpCount}"), false)
        }
        return 1
    }

    private fun rankingRegion(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val history = BattleHistoryManager.getInstance()
        val stats = history.getRegionStatistics(regionId)

        source.sendSuccess(() -> Component.literal("§eEstatísticas de $regionId:"), false)
        source.sendSuccess(() -> Component.literal("  §7Total batalhas: §f${stats["totalBattles"]}"), false)
        source.sendSuccess(() -> Component.literal("  §7Mais vitoriosa: §e${stats["mostVictoriousNation"] ?: "N/A"}"), false)
        source.sendSuccess(() -> Component.literal("  §7Duração média: §f${String.format("%.0f", stats["averageDuration"])}s"), false)
        source.sendSuccess(() -> Component.literal("  §7Total kills: §c${stats["totalKills"]}"), false)
        source.sendSuccess(() -> Component.literal("  §7Total veículos: §e${stats["totalVehiclesDestroyed"]}"), false)
        source.sendSuccess(() -> Component.literal("  §7Última batalha: §7${stats["lastBattle"] ?: "N/A"}"), false)
        return 1
    }

    private fun exportMap(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        MapIntegration.getInstance().exportMapData()
        source.sendSuccess(() -> Component.literal("§aDados do mapa exportados"), true)
        return 1
    }

    private fun refreshMap(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        MapIntegration.getInstance().refresh()
        source.sendSuccess(() -> Component.literal("§aMapa atualizado"), true)
        return 1
    }

    private fun testNotification(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val type = StringArgumentType.getString(ctx, "type")

        when (type) {
            "assault_start" -> NotificationManager.getInstance().notifyAssaultStarted("TestRegion", "TestNation")
            "assault_end" -> NotificationManager.getInstance().notifyAssaultEnded("TestRegion")
            "victory" -> NotificationManager.getInstance().notifyVictory("TestRegion", "TestNation", 1500)
            "defeat" -> NotificationManager.getInstance().notifyDefeat("TestRegion", "TestNation")
            "objective" -> NotificationManager.getInstance().notifyObjectiveCompleted("TestRegion", "TestObjective", "TestNation", 500)
            "capital_destroyed" -> NotificationManager.getInstance().notifyCapitalShipDestroyed("TestRegion", "Battleship", "AttackerNation", "DefenderNation")
            else -> source.sendFailure(Component.literal("§cTipos: assault_start, assault_end, victory, defeat, objective, capital_destroyed"))
        }
        source.sendSuccess(() -> Component.literal("§aNotificação de teste '$type' enviada"), true)
        return 1
    }

    private fun broadcastMessage(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val message = StringArgumentType.getString(ctx, "message")
        NotificationManager.getInstance().broadcastGlobal(Component.literal("§e§l[NAVAL WARFARE] §r$message"))
        source.sendSuccess(() -> Component.literal("§aMensagem global enviada"), true)
        return 1
    }

    private fun reloadConfig(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        // Config reload handled by Forge
        source.sendSuccess(() -> Component.literal("§aConfig recarregado (reinicie para aplicar completamente)"), true)
        return 1
    }

    private fun setDebug(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val enabled = BoolArgumentType.getBool(ctx, "enabled")
        NavalWarfareConfig.debugMode.set(enabled)
        source.sendSuccess(() -> Component.literal("§aDebug mode: ${if (enabled) "ativado" else "desativado"}"), true)
        return 1
    }

    private fun cleanup(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        VehicleIntegrationManager.getInstance().cleanupAbandoned()
        ScoringManager.getInstance().cleanupOldScores()
        BattleHistoryManager.getInstance().loadHistory() // reload
        source.sendSuccess(() -> Component.literal("§aLimpeza realizada"), true)
        return 1
    }

    private fun simulateAssault(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val regionId = StringArgumentType.getString(ctx, "regionId")
        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(regionId) ?: return 0

        // Simular assalto com nações fictícias
        manager.startAssault(regionId, "sim_nation_1")
        source.sendSuccess(() -> Component.literal("§aAssalto simulado iniciado em '$regionId'"), true)
        return 1
    }

    private fun showHelp(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        source.sendSuccess(() -> Component.literal("§e§l=== NAVAL WARFARE COMANDOS ==="), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare region create <id> <name> <min> <max> §7- Criar região"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare region delete <id> §7- Deletar região"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare region list §7- Listar regiões"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare region info <id> §7- Info da região"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare region setowner <id> <nation> §7- Definir dona"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare region tp <id> §7- Teleportar"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare window add <region> <day> <HH:MM> <duration> §7- Add janela"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare assault begin <region> §7- Iniciar assalto"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare assault end <region> §7- Finalizar assalto"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare objective add <region> <type> <name> §7- Add objetivo"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare reward set <region> <money> §7- Set recompensa"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare vehicle register <class> <name> §7- Registrar veículo"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare score show <region> §7- Ver pontuação"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare history list §7- Ver histórico"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare ranking nations §7- Ranking nações"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare notify test <type> §7- Testar notificação"), false)
        source.sendSuccess(() -> Component.literal("§f/navalwarfare admin debug <true|false> §7- Debug mode"), false)
        return 1
    }

    private fun formatTime(seconds: Long): String {
        val mins = seconds / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }
}