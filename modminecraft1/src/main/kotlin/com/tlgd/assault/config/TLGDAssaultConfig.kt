package com.tlgd.assault.config

import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue
import net.neoforged.neoforge.common.ModConfigSpec.IntValue
import net.neoforged.neoforge.common.ModConfigSpec.LongValue
import net.neoforged.neoforge.common.ModConfigSpec.ListValue

class TLGDAssaultConfig {

    companion object {
        // General
        var enabled: BooleanValue = ModConfigSpec.BooleanValue(false)
        var debugMode: BooleanValue = ModConfigSpec.BooleanValue(false)

        // Attack Windows
        var defaultWindowDurationMinutes: IntValue = ModConfigSpec.IntValue(30)
        var maxWindowsPerWeek: IntValue = ModConfigSpec.IntValue(2)
        var windowCooldownHours: IntValue = ModConfigSpec.IntValue(48)
        var minPlayersToStart: IntValue = ModConfigSpec.IntValue(3)

        // Scoring
        var pointsPerSecondPresence: DoubleValue = ModConfigSpec.DoubleValue(1.0)
        var pointsPerKill: IntValue = ModConfigSpec.IntValue(50)
        var pointsPerVehicleDestroyed: IntValue = ModConfigSpec.IntValue(200)
        var pointsPerFlagCapture: IntValue = ModConfigSpec.IntValue(100)
        var maxScoreCap: IntValue = ModConfigSpec.IntValue(10000)

        // Rewards
        var dailyMoneyReward: LongValue = ModConfigSpec.LongValue(3456)
        var dailyResourceRewardEnabled: BooleanValue = ModConfigSpec.BooleanValue(true)
        var resourceRewardItems: ListValue<String> = ModConfigSpec.ListValue(listOf())

        // Vehicle restrictions (class names from vehicle mods)
        var allowedVehicleClasses: ListValue<String> = ModConfigSpec.ListValue(
            listOf("destroyer", "cruiser", "dreadnought", "carrier", "tank", "apc", "ifv")
        )
        var maxVehiclesPerNation: IntValue = ModConfigSpec.IntValue(5)

        // Region settings
        var regionMinSize: IntValue = ModConfigSpec.IntValue(10000) // blocks²
        var regionMaxSize: IntValue = ModConfigSpec.IntValue(500000)
        var regionHeightLimit: IntValue = ModConfigSpec.IntValue(256)

        // Nation integration (TLGD)
        var requireNationForAssault: BooleanValue = ModConfigSpec.BooleanValue(true)
        var requiredNationRank: ConfigValue<String> = ModConfigSpec.ConfigValue("general") // general, coleader, leader

        // GUI/UX
        var showAssaultHud: BooleanValue = ModConfigSpec.BooleanValue(true)
        var showRegionMarkers: BooleanValue = ModConfigSpec.BooleanValue(true)
        var announcementSound: ConfigValue<String> = ModConfigSpec.ConfigValue("minecraft:entity.ender_dragon.growl")

        val SPEC: ModConfigSpec = buildSpec()

        private fun buildSpec(): ModConfigSpec {
            val builder = ModConfigSpec.Builder()
            val clientBuilder = ModConfigSpec.Builder()

            builder.push("general")
            enabled = builder.comment("Habilita o sistema de Assault Regions").define("enabled", true)
            debugMode = builder.comment("Modo debug com logs extras").define("debugMode", false)
            builder.pop()

            builder.push("attack_windows")
            defaultWindowDurationMinutes = builder.comment("Duração padrão da janela de ataque (minutos)").defineInRange("duration_minutes", 30, 5, 120)
            maxWindowsPerWeek = builder.comment("Máximo de janelas por semana por região").defineInRange("max_per_week", 2, 1, 7)
            windowCooldownHours = builder.comment("Cooldown entre janelas da mesma região (horas)").defineInRange("cooldown_hours", 48, 1, 168)
            minPlayersToStart = builder.comment("Mínimo de jogadores online para iniciar assalto").defineInRange("min_players", 3, 1, 50)
            builder.pop()

            builder.push("scoring")
            pointsPerSecondPresence = builder.comment("Pontos por segundo de presença na região").defineInRange("presence_per_sec", 1.0, 0.1, 100.0)
            pointsPerKill = builder.comment("Pontos por eliminação de jogador").defineInRange("kill_points", 50, 1, 1000)
            pointsPerVehicleDestroyed = builder.comment("Pontos por veículo destruído").defineInRange("vehicle_destroy_points", 200, 1, 5000)
            pointsPerFlagCapture = builder.comment("Pontos por captura de bandeira/ponto de controle").defineInRange("flag_capture_points", 100, 1, 5000)
            maxScoreCap = builder.comment("Teto máximo de pontuação").defineInRange("max_score", 10000, 100, 1000000)
            builder.pop()

            builder.push("rewards")
            dailyMoneyReward = builder.comment("Recompensa diária em dinheiro para nação dona").defineInRange("daily_money", 3456, 0, 1000000)
            dailyResourceRewardEnabled = builder.comment("Habilita recompensas de itens diários").define("resource_rewards_enabled", true)
            resourceRewardItems = builder.comment("Lista de itens de recompensa (formato: 'modid:itemid:count:weight')").defineList("resource_items", listOf(), ModConfigSpec.ValidateString())
            builder.pop()

            builder.push("vehicles")
            allowedVehicleClasses = builder.comment("Classes de veículos permitidas (nomes do mod de veículos)").defineList("allowed_classes", listOf("destroyer", "cruiser", "dreadnought", "carrier", "tank", "apc", "ifv"), ModConfigSpec.ValidateString())
            maxVehiclesPerNation = builder.comment("Máximo de veículos por nação numa assault region").defineInRange("max_per_nation", 5, 1, 50)
            builder.pop()

            builder.push("regions")
            regionMinSize = builder.comment("Tamanho mínimo da região (blocos²)").defineInRange("min_size", 10000, 100, 1000000)
            regionMaxSize = builder.comment("Tamanho máximo da região (blocos²)").defineInRange("max_size", 500000, 1000, 10000000)
            regionHeightLimit = builder.comment("Limite de altura da região").defineInRange("height_limit", 256, 64, 384)
            builder.pop()

            builder.push("nation_integration")
            requireNationForAssault = builder.comment("Exige nação para participar").define("require_nation", true)
            requiredNationRank = builder.comment("Cargo mínimo para declarar assalto (general, coleader, leader)").define("required_rank", "general")
            builder.pop()

            clientBuilder.push("gui")
            showAssaultHud = clientBuilder.comment("Mostra HUD durante assalto ativo").define("show_hud", true)
            showRegionMarkers = clientBuilder.comment("Mostra marcadores de região no mundo").define("show_markers", true)
            announcementSound = clientBuilder.comment("Som de anúncio global").define("announcement_sound", "minecraft:entity.ender_dragon.growl")
            clientBuilder.pop()

            return builder.build().also { clientBuilder.build() }
        }
    }
}