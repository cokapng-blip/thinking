package com.tlgd.assault.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.BoolArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.tlgd.assault.TLGAssaultMod
import com.tlgd.assault.config.TLGDAssaultConfig
import com.tlgd.assault.region.AssaultRegion
import com.tlgd.assault.region.AssaultRegionManager
import com.tlgd.assault.region.AttackWindow
import com.tlgd.assault.region.RegionBounds
import com.tlgd.assault.nation.NationIntegration
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.blocks.BlockPosArgument
import net.minecraft.commands.arguments.coordinates.BlockPosArgument
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object TLGDAssaultCommands {

    private val ERROR_NOT_IN_REGION = SimpleCommandExceptionType(Component.literal("§cVocê não está em uma região de assalto!"))
    private val ERROR_NO_PERMISSION = SimpleCommandExceptionType(Component.literal("§cSem permissão!"))
    private val ERROR_REGION_EXISTS = SimpleCommandExceptionType(Component.literal("§cRegião já existe!"))
    private val ERROR_REGION_NOT_FOUND = SimpleCommandExceptionType(Component.literal("§cRegião não encontrada!"))
    private val ERROR_INVALID_WINDOW = SimpleCommandExceptionType(Component.literal("§cJanela de ataque inválida!"))
    private val ERROR_NOT_IN_WINDOW = SimpleCommandExceptionType(Component.literal("§cNão é horário de janela de ataque!"))

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("assault")
            .requires { it.hasPermission(2) } // OP level 2+

        // /assault create <id> <nome> <min> <max> <dimension> [janelas...]
        root.then(Commands.literal("create")
            .then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.argument("displayName", StringArgumentType.greedyString())
                    .then(Commands.argument("min", BlockPosArgument.blockPos())
                        .then(Commands.argument("max", BlockPosArgument.blockPos())
                            .then(Commands.argument("dimension", StringArgumentType.string())
                                .executes { ctx -> createRegion(ctx) }
                            )
                        )
                    )
                )
            )
        )

        // /assault remove <id>
        root.then(Commands.literal("remove")
            .then(Commands.argument("id", StringArgumentType.word())
                .executes { ctx -> removeRegion(ctx) }
            )
        )

        // /assault list
        root.then(Commands.literal("list")
            .executes { ctx -> listRegions(ctx) }
        )

        // /assault info <id>
        root.then(Commands.literal("info")
            .then(Commands.argument("id", StringArgumentType.word())
                .executes { ctx -> infoRegion(ctx) }
            )
        )

        // /assault setwindow <id> <dia> <HH:mm> [duracao]
        root.then(Commands.literal("setwindow")
            .then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.argument("day", StringArgumentType.word())
                    .then(Commands.argument("time", StringArgumentType.string())
                        .then(Commands.argument("duration", IntegerArgumentType.integer(5, 120))
                            .executes { ctx -> setWindow(ctx) }
                        )
                        .executes { ctx -> setWindow(ctx) }
                    )
                )
            )
        )

        // /assault begin <id> - Iniciar assalto (para jogadores com cargo)
        root.then(Commands.literal("begin")
            .then(Commands.argument("id", StringArgumentType.word())
                .executes { ctx -> beginAssault(ctx) }
            )
        )

        // /assault status [id] - Ver status do assalto atual
        root.then(Commands.literal("status")
            .then(Commands.argument("id", StringArgumentType.word())
                .executes { ctx -> statusAssault(ctx) }
            )
            .executes { ctx -> statusAssault(ctx) }
        )

        // /assault leaderboard <id>
        root.then(Commands.literal("leaderboard")
            .then(Commands.argument("id", StringArgumentType.word())
                .executes { ctx -> leaderboard(ctx) }
            )
        )

        // /assault rewards <id> - Ver recompensas da região
        root.then(Commands.literal("rewards")
            .then(Commands.argument("id", StringArgumentType.word())
                .executes { ctx -> rewards(ctx) }
            )
        )

        // Admin commands
        val admin = Commands.literal("admin")
            .requires { it.hasPermission(3) }

        admin.then(Commands.literal("forceend")
            .then(Commands.argument("id", StringArgumentType.word())
                .executes { ctx -> forceEnd(ctx) }
            )
        )

        admin.then(Commands.literal("setowner")
            .then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.argument("nationId", StringArgumentType.string())
                    .then(Commands.argument("nationName", StringArgumentType.greedyString())
                        .executes { ctx -> setOwner(ctx) }
                    )
                )
            )
        )

        admin.then(Commands.literal("reloadconfig")
            .executes { ctx -> reloadConfig(ctx) }
        )

        admin.then(Commands.literal("debug")
            .executes { ctx -> debugInfo(ctx) }
        )

        root.then(admin)
        dispatcher.register(root)
    }

    private fun createRegion(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: throw CommandSyntaxException(ERROR_NO_PERMISSION, Component.literal("Apenas jogadores"))
        val level = player.serverLevel

        val id = StringArgumentType.getString(ctx, "id").lowercase()
        val displayName = StringArgumentType.getString(ctx, "displayName")
        val min = BlockPosArgument.getLoadedBlockPos(ctx, "min")
        val max = BlockPosArgument.getLoadedBlockPos(ctx, "max")
        val dimensionStr = StringArgumentType.getString(ctx, "dimension")
        val dimension = ResourceLocation.parse(dimensionStr)

        val manager = AssaultRegionManager.getInstance()

        if (manager.getRegion(id) != null) {
            throw CommandSyntaxException(ERROR_REGION_EXISTS, Component.literal("Região '$id' já existe!"))
        }

        val area = (max.x - min.x + 1L) * (max.z - min.z + 1L)
        if (area < TLGDAssaultConfig.regionMinSize || area > TLGDAssaultConfig.regionMaxSize) {
            source.sendFailure(Component.literal("§cTamanho inválido: ${area} blocos² (min: ${TLGDAssaultConfig.regionMinSize}, max: ${TLGDAssaultConfig.regionMaxSize})"))
            return 0
        }

        val windows = defaultWindows()
        val region = manager.createRegion(id, displayName, min, max, dimension, windows)

        source.sendSuccess(() -> Component.literal("§aRegião criada: §e$displayName §7($id)"), true)
        source.sendSuccess(() -> Component.literal("§7Bounds: $min -> $max | Dim: $dimension"), true)
        source.sendSuccess(() -> Component.literal("§7Janelas padrão: Sábado 20:00 e Domingo 15:00 (${TLGDAssaultConfig.defaultWindowDurationMinutes}min)"), true)
        return 1
    }

    private fun removeRegion(ctx: CommandContext<CommandSourceStack>): Int {
        val id = StringArgumentType.getString(ctx, "id").lowercase()
        val manager = AssaultRegionManager.getInstance()

        if (manager.removeRegion(id)) {
            ctx.source.sendSuccess(() -> Component.literal("§aRegião '$id' removida!"), true)
            return 1
        }
        throw CommandSyntaxException(ERROR_REGION_NOT_FOUND, Component.literal("Região '$id' não encontrada!"))
    }

    private fun listRegions(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val manager = AssaultRegionManager.getInstance()
        val regions = manager.getAllRegions()

        if (regions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Nenhuma região cadastrada."), false)
            return 0
        }

        source.sendSuccess(() -> Component.literal("§e§lRegiões de Assalto (§f${regions.size}§e):"), false)
        for (region in regions.sortedBy { it.id }) {
            val owner = region.ownerNationName ?: "§7Nenhuma"
            val nextWindow = region.getNextWindow()?.toString() ?: "§cNenhuma"
            val line = MutableComponent.literal("  §f- §e${region.id} §7| §f${region.displayName} §7| Dono: $owner §7| Próxima: $nextWindow")
            source.sendSuccess(() -> line, false)
        }
        return 1
    }

    private fun infoRegion(ctx: CommandContext<CommandSourceStack>): Int {
        val id = StringArgumentType.getString(ctx, "id").lowercase()
        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(id) ?: throw CommandSyntaxException(ERROR_REGION_NOT_FOUND, Component.literal("Região '$id' não encontrada!"))

        val source = ctx.source
        val bounds = region.bounds

        source.sendSuccess(() -> Component.literal("§e§lInfo: §f${region.displayName} §7($id)"), false)
        source.sendSuccess(() -> Component.literal("§7Dimensão: §f${bounds.dimension}"), false)
        source.sendSuccess(() -> Component.literal("§7Bounds: §f${bounds.min} §7-> §f${bounds.max}"), false)
        source.sendSuccess(() -> Component.literal("§7Área: §f${bounds.getArea()} §7blocos² | Volume: §f${bounds.getVolume()}"), false)
        source.sendSuccess(() -> Component.literal("§7Dono: §f${region.ownerNationName ?: "§7Nenhum"} §7(${region.ownerNationId ?: "N/A"})"), false)
        source.sendSuccess(() -> Component.literal("§7Estado: ${region.getStateForDisplay()}"), false)

        if (region.attackWindows.isNotEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Janelas de ataque:"), false)
            for ((i, window) in region.attackWindows.withIndex()) {
                val marker = if (i == region.nextWindowIndex) "§a► " else "   "
                val timeLeft = window.getTimeUntilStart()
                val timeStr = if (timeLeft > 0) " §7(inicia em ${timeLeft}min)" else ""
                source.sendSuccess(() -> Component.literal("$marker§f${window}$timeStr"), false)
            }
        }

        val assault = manager.getActiveAssault(id)
        if (assault != null) {
            val remaining = assault.getRemainingTime() / 1000 / 60
            source.sendSuccess(() -> Component.literal("§c§lASSALTO ATIVO§r §7- Termina em ${remaining}min"), false)
            source.sendSuccess(() -> Component.literal("§7Iniciado por: §f${assault.initiatorNationName}"), false)
        }

        return 1
    }

    private fun setWindow(ctx: CommandContext<CommandSourceStack>): Int {
        val id = StringArgumentType.getString(ctx, "id").lowercase()
        val dayStr = StringArgumentType.getString(ctx, "day").uppercase()
        val timeStr = StringArgumentType.getString(ctx, "time")
        val duration = ctx.getArgument("duration", Int::class.java) ?: TLGDAssaultConfig.defaultWindowDurationMinutes

        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(id) ?: throw CommandSyntaxException(ERROR_REGION_NOT_FOUND, Component.literal("Região '$id' não encontrada!"))

        val day = try {
            DayOfWeek.valueOf(dayStr)
        } catch (e: IllegalArgumentException) {
            throw CommandSyntaxException(ERROR_INVALID_WINDOW, Component.literal("Dia inválido! Use: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY"))
        }

        val time = try {
            LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            throw CommandSyntaxException(ERROR_INVALID_WINDOW, Component.literal("Horário inválido! Use formato HH:mm (ex: 20:00)"))
        }

        if (duration < 5 || duration > 120) {
            throw CommandSyntaxException(ERROR_INVALID_WINDOW, Component.literal("Duração deve ser entre 5 e 120 minutos"))
        }

        val windows = mutableListOf<AttackWindow>()
        windows.addAll(region.attackWindows)
        windows.add(AttackWindow(day, time, duration))
        manager.setAttackWindows(id, windows)

        ctx.source.sendSuccess(() -> Component.literal("§aJanela adicionada: §f$day $time §7($duration min)"), true)
        return 1
    }

    private fun beginAssault(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val player = source.player ?: throw CommandSyntaxException(ERROR_NO_PERMISSION, Component.literal("Apenas jogadores"))
        val id = StringArgumentType.getString(ctx, "id").lowercase()

        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(id) ?: throw CommandSyntaxException(ERROR_REGION_NOT_FOUND, Component.literal("Região '$id' não encontrada!"))

        if (!NationIntegration.canDeclareAssault(player)) {
            source.sendFailure(Component.literal("§cVocê precisa ser General, Co-líder ou Líder da nação!"))
            return 0
        }

        val success = manager.startAssault(id, player)
        if (success) {
            source.sendSuccess(() -> Component.literal("§aAssalto iniciado em §e${region.displayName}!"), true)
        }
        return if (success) 1 else 0
    }

    private fun statusAssault(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val manager = AssaultRegionManager.getInstance()

        val id = ctx.getArgument("id", String::class.java)?.lowercase()
        val regions = if (id != null) {
            manager.getRegion(id)?.let { listOf(it) } ?: emptyList()
        } else {
            manager.getAllRegions().filter { it.state == com.tlgd.assault.region.AssaultState.ACTIVE }
        }

        if (regions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Nenhum assalto ativo no momento."), false)
            return 0
        }

        for (region in regions) {
            val assault = manager.getActiveAssault(region.id) ?: continue
            val remaining = assault.getRemainingTime() / 1000 / 60

            source.sendSuccess(() -> Component.literal("§c§l[ASSALTO ATIVO] §f${region.displayName} §7| Tempo: ${remaining}min"), false)
            source.sendSuccess(() -> Component.literal("§7Iniciado por: §f${assault.initiatorNationName}"), false)

            val leaderboard = region.getLeaderboard()
            for ((i, score) in leaderboard.withIndex().take(5)) {
                val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "  " }
                source.sendSuccess(() -> Component.literal("  $medal §f${score.nationName} §7- §e${score.score} §7pts (K:${score.kills} V:${score.vehiclesDestroyed} F:${score.flagsCaptured})"), false)
            }
        }
        return 1
    }

    private fun leaderboard(ctx: CommandContext<CommandSourceStack>): Int {
        val id = StringArgumentType.getString(ctx, "id").lowercase()
        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(id) ?: throw CommandSyntaxException(ERROR_REGION_NOT_FOUND, Component.literal("Região '$id' não encontrada!"))

        val source = ctx.source
        val leaderboard = region.getLeaderboard()

        if (leaderboard.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Nenhuma pontuação registrada."), false)
            return 0
        }

        source.sendSuccess(() -> Component.literal("§e§lLeaderboard: §f${region.displayName}"), false)
        for ((i, score) in leaderboard.withIndex()) {
            val medal = when (i) { 0 -> "§6🥇"; 1 -> "§f🥈"; 2 -> "§6🥉"; else -> "§7  " }
            source.sendSuccess(() -> Component.literal("$medal §f${score.nationName} §7- §e${score.score} §7pts §8(K:${score.kills} V:${score.vehiclesDestroyed} F:${score.flagsCaptured} P:${score.presenceTimeSeconds}s)"), false)
        }
        return 1
    }

    private fun rewards(ctx: CommandContext<CommandSourceStack>): Int {
        val id = StringArgumentType.getString(ctx, "id").lowercase()
        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(id) ?: throw CommandSyntaxException(ERROR_REGION_NOT_FOUND, Component.literal("Região '$id' não encontrada!"))

        val source = ctx.source
        source.sendSuccess(() -> Component.literal("§e§lRecompensas Diárias: §f${region.displayName}"), false)
        source.sendSuccess(() -> Component.literal("§7Dinheiro: §a$${TLGDAssaultConfig.dailyMoneyReward}/dia"), false)

        if (TLGDAssaultConfig.dailyResourceRewardEnabled && TLGDAssaultConfig.resourceRewardItems.isNotEmpty()) {
            source.sendSuccess(() -> Component.literal("§7Recursos:"), false)
            for (itemStr in TLGDAssaultConfig.resourceRewardItems) {
                source.sendSuccess(() -> Component.literal("  §f- $itemStr"), false)
            }
        } else {
            source.sendSuccess(() -> Component.literal("§7Recursos: §cDesabilitado ou não configurado"), false)
        }

        if (region.ownerNationId != null) {
            source.sendSuccess(() -> Component.literal("§7Dono atual: §f${region.ownerNationName} §7- Recebendo recompensas!"), false)
        } else {
            source.sendSuccess(() -> Component.literal("§7Região sem dono - capture para receber recompensas!"), false)
        }
        return 1
    }

    private fun forceEnd(ctx: CommandContext<CommandSourceStack>): Int {
        val id = StringArgumentType.getString(ctx, "id").lowercase()
        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(id) ?: throw CommandSyntaxException(ERROR_REGION_NOT_FOUND, Component.literal("Região '$id' não encontrada!"))

        val assault = manager.getActiveAssault(id)
        if (assault != null) {
            manager.endAssault(assault.id)
            ctx.source.sendSuccess(() -> Component.literal("§aAssalto forçado a terminar em §e${region.displayName}!"), true)
            return 1
        }
        ctx.source.sendFailure(Component.literal("§cNenhum assalto ativo nesta região!"))
        return 0
    }

    private fun setOwner(ctx: CommandContext<CommandSourceStack>): Int {
        val id = StringArgumentType.getString(ctx, "id").lowercase()
        val nationId = StringArgumentType.getString(ctx, "nationId")
        val nationName = StringArgumentType.getString(ctx, "nationName")

        val manager = AssaultRegionManager.getInstance()
        val region = manager.getRegion(id) ?: throw CommandSyntaxException(ERROR_REGION_NOT_FOUND, Component.literal("Região '$id' não encontrada!"))

        region.ownerNationId = nationId
        region.ownerNationName = nationName
        region.state = com.tlgd.assault.region.AssaultState.PEACEFUL
        manager.setDirty()

        ctx.source.sendSuccess(() -> Component.literal("§aDono definido: §f$nationName §7($nationId)"), true)
        return 1
    }

    private fun reloadConfig(ctx: CommandContext<CommandSourceStack>): Int {
        // Config recarrega automaticamente via Forge
        ctx.source.sendSuccess(() -> Component.literal("§aConfig recarregada! (Reinicie o servidor para mudanças completas)"), true)
        return 1
    }

    private fun debugInfo(ctx: CommandContext<CommandSourceStack>): Int {
        val source = ctx.source
        val manager = AssaultRegionManager.getInstance()

        source.sendSuccess(() -> Component.literal("§e§lDebug TLGD Assault"), false)
        source.sendSuccess(() -> Component.literal("§7Regiões: §f${manager.regions.size}"), false)
        source.sendSuccess(() -> Component.literal("§7Assaltos ativos: §f${manager.activeAssaults.size}"), false)
        source.sendSuccess(() -> Component.literal("§7Jogadores em regiões: §f${manager.playerRegionCache.size}"), false)
        return 1
    }

    private fun defaultWindows(): List<AttackWindow> {
        return listOf(
            AttackWindow(DayOfWeek.SATURDAY, LocalTime.of(20, 0), TLGDAssaultConfig.defaultWindowDurationMinutes),
            AttackWindow(DayOfWeek.SUNDAY, LocalTime.of(15, 0), TLGDAssaultConfig.defaultWindowDurationMinutes)
        )
    }
}