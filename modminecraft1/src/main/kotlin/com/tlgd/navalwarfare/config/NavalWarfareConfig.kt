package com.tlgd.navalwarfare.config

import net.neoforged.fml.config.ModConfig
import net.neoforged.neoforge.common.ModConfigSpec
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue
import net.neoforged.neoforge.common.ModConfigSpec.IntValue
import net.neoforged.neoforge.common.ModConfigSpec.ListValue
import net.neoforged.neoforge.common.ModConfigSpec.LongValue

/**
 * Configuração completa do Naval Warfare.
 */
class NavalWarfareConfig {

    companion object {
        // ===== GENERAL =====
        var enabled: BooleanValue = ModConfigSpec.BooleanValue(false)
        var debugMode: BooleanValue = ModConfigSpec.BooleanValue(false)
        var logLevel: ConfigValue<String> = ModConfigSpec.ConfigValue("INFO")

        // ===== ASSAULT WINDOWS =====
        var defaultWindowDurationMinutes: IntValue = ModConfigSpec.IntValue(30)
        var maxWindowsPerWeek: IntValue = ModConfigSpec.IntValue(2)
        var windowCooldownHours: IntValue = ModConfigSpec.IntValue(48)
        var minPlayersToStart: IntValue = ModConfigSpec.IntValue(3)
        var assaultWarning30Min: BooleanValue = ModConfigSpec.BooleanValue(true)
        var assaultWarning5Min: BooleanValue = ModConfigSpec.BooleanValue(true)

        // ===== SCORING =====
        var pointsPerPlayerKill: IntValue = ModConfigSpec.IntValue(10)
        var pointsPerPresenceTick: IntValue = ModConfigSpec.IntValue(5)
        var presenceCheckIntervalSeconds: IntValue = ModConfigSpec.IntValue(10)
        var presenceRewardIntervalSeconds: IntValue = ModConfigSpec.IntValue(30)
        var vehiclePresenceMultiplier: DoubleValue = ModConfigSpec.DoubleValue(0.1)
        var presenceStreakBonusPerTick: IntValue = ModConfigSpec.IntValue(1)
        var maxPresenceStreakBonus: IntValue = ModConfigSpec.IntValue(100)
        var defenderPresenceMultiplier: DoubleValue = ModConfigSpec.DoubleValue(1.5)

        // Vehicle score multipliers
        var destroyerScore: IntValue = ModConfigSpec.IntValue(50)
        var frigateScore: IntValue = ModConfigSpec.IntValue(75)
        var cruiserScore: IntValue = ModConfigSpec.IntValue(100)
        var battlecruiserScore: IntValue = ModConfigSpec.IntValue(150)
        var battleshipScore: IntValue = ModConfigSpec.IntValue(200)
        var dreadnoughtScore: IntValue = ModConfigSpec.IntValue(300)
        var aircraftCarrierScore: IntValue = ModConfigSpec.IntValue(300)
        var submarineScore: IntValue = ModConfigSpec.IntValue(150)
        var patrolBoatScore: IntValue = ModConfigSpec.IntValue(25)
        var transportScore: IntValue = ModConfigSpec.IntValue(20)
        var landingShipScore: IntValue = ModConfigSpec.IntValue(40)
        var minelayerScore: IntValue = ModConfigSpec.IntValue(60)

        var pointsPerFlagCapture: IntValue = ModConfigSpec.IntValue(100)
        var maxScoreCap: IntValue = ModConfigSpec.IntValue(10000)

        // ===== ANTI-FARM =====
        var killCooldownMinutes: IntValue = ModConfigSpec.IntValue(5)
        var maxKillsPerTargetPerHour: IntValue = ModConfigSpec.IntValue(3)
        var minKillDistance: DoubleValue = ModConfigSpec.DoubleValue(5.0)
        var vehicleDestroyCooldownHours: IntValue = ModConfigSpec.IntValue(1)
        var enableSuicideDetection: BooleanValue = ModConfigSpec.BooleanValue(true)
        var enableSameIPDetection: BooleanValue = ModConfigSpec.BooleanValue(true)

        // ===== VEHICLES =====
        var allowedVehicleClasses: ListValue<String> = ModConfigSpec.ListValue(listOf(
            "patrol_boat", "destroyer", "frigate", "cruiser", "battlecruiser",
            "battleship", "dreadnought", "aircraft_carrier", "submarine",
            "transport", "landing_ship", "minelayer"
        ))
        var maxVehiclesPerNation: IntValue = ModConfigSpec.IntValue(5)
        var abandonedVehicleTimeoutSeconds: IntValue = ModConfigSpec.IntValue(120)
        var requireVehicleCrew: BooleanValue = ModConfigSpec.BooleanValue(true)
        var minCrewForCapitalShip: IntValue = ModConfigSpec.IntValue(10)

        // ===== REGIONS =====
        var regionMinSize: IntValue = ModConfigSpec.IntValue(10000)
        var regionMaxSize: IntValue = ModConfigSpec.IntValue(500000)
        var regionHeightLimit: IntValue = ModConfigSpec.IntValue(256)
        var maxRegionsPerDimension: IntValue = ModConfigSpec.IntValue(50)

        // ===== REWARDS =====
        var defaultDailyMoneyReward: LongValue = ModConfigSpec.LongValue(3456)
        var dailyResourceRewardEnabled: BooleanValue = ModConfigSpec.BooleanValue(true)
        var rewardIntervalHours: IntValue = ModConfigSpec.IntValue(24)
        var captureBonusMoney: LongValue = ModConfigSpec.LongValue(5000)
        var captureBonusItems: ListValue<String> = ModConfigSpec.ListValue(listOf())

        // ===== NATION INTEGRATION =====
        var requireNationForAssault: BooleanValue = ModConfigSpec.BooleanValue(true)
        var requiredNationRank: ConfigValue<String> = ModConfigSpec.ConfigValue("general")

        // ===== OBJECTIVES =====
        var objectivesEnabled: BooleanValue = ModConfigSpec.BooleanValue(true)
        var requiredObjectivesForVictory: BooleanValue = ModConfigSpec.BooleanValue(false)

        // ===== MAP INTEGRATION =====
        var enableMapIntegration: BooleanValue = ModConfigSpec.BooleanValue(true)
        var mapExportIntervalMinutes: IntValue = ModConfigSpec.IntValue(5)
        var preferDynmap: BooleanValue = ModConfigSpec.BooleanValue(true)
        var preferBlueMap: BooleanValue = ModConfigSpec.BooleanValue(false)
        var preferSquaremap: BooleanValue = ModConfigSpec.BooleanValue(false)

        // ===== NOTIFICATIONS =====
        var globalAnnouncementsEnabled: BooleanValue = ModConfigSpec.BooleanValue(true)
        var assaultStartSound: ConfigValue<String> = ModConfigSpec.ConfigValue("minecraft:entity.ender_dragon.growl")
        var assaultEndSound: ConfigValue<String> = ModConfigSpec.ConfigValue("minecraft:block.beacon.deactivate")
        var victorySound: ConfigValue<String> = ModConfigSpec.ConfigValue("minecraft:entity.player.levelup")
        var defeatSound: ConfigValue<String> = ModConfigSpec.ConfigValue("minecraft:entity.wither.death")
        var objectiveSound: ConfigValue<String> = ModConfigSpec.ConfigValue("minecraft:block.note_block.bell")
        var capitalShipDestroyedSound: ConfigValue<String> = ModConfigSpec.ConfigValue("minecraft:entity.generic.explode")
        var announcementSoundVolume: DoubleValue = ModConfigSpec.DoubleValue(1.0)
        var enableDiscordWebhooks: BooleanValue = ModConfigSpec.BooleanValue(false)
        var discordWebhookGlobal: ConfigValue<String> = ModConfigSpec.ConfigValue("")

        // ===== GUI/HUD =====
        var showAssaultHUD: BooleanValue = ModConfigSpec.BooleanValue(true)
        var showRegionMarkers: BooleanValue = ModConfigSpec.BooleanValue(true)
        var hudPosition: ConfigValue<String> = ModConfigSpec.ConfigValue("top_center") // top_center, top_left, top_right
        var hudScale: DoubleValue = ModConfigSpec.DoubleValue(1.0)
        var militaryThemeEnabled: BooleanValue = ModConfigSpec.BooleanValue(true)

        // ===== HISTORY =====
        var saveBattleHistory: BooleanValue = ModConfigSpec.BooleanValue(true)
        var maxBattlesInMemory: IntValue = ModConfigSpec.IntValue(1000)
        var autoExportCSV: BooleanValue = ModConfigSpec.BooleanValue(false)
        var autoExportJSON: BooleanValue = ModConfigSpec.BooleanValue(true)

        // ===== PERFORMANCE =====
        var presenceCheckThreads: IntValue = ModConfigSpec.IntValue(1)
        var vehicleScanIntervalMs: IntValue = ModConfigSpec.IntValue(5000)
        var cleanupIntervalMinutes: IntValue = ModConfigSpec.IntValue(10)
        var maxTrackedVehicles: IntValue = ModConfigSpec.IntValue(500)

        val SPEC: ModConfigSpec = buildSpec()

        private fun buildSpec(): ModConfigSpec {
            val builder = ModConfigSpec.Builder()
            val clientBuilder = ModConfigSpec.Builder()

            builder.push("general")
            enabled = builder.comment("Habilita o sistema Naval Warfare").define("enabled", true)
            debugMode = builder.comment("Modo debug com logs detalhados").define("debug_mode", false)
            logLevel = builder.comment("Nivel de log: DEBUG, INFO, WARN, ERROR").define("log_level", "INFO")
            builder.pop()

            builder.push("assault_windows")
            defaultWindowDurationMinutes = builder.comment("Duracao padrao da janela de ataque (minutos)").defineInRange("duration_minutes", 30, 5, 120)
            maxWindowsPerWeek = builder.comment("Maximo de janelas por semana por regiao").defineInRange("max_per_week", 2, 1, 7)
            windowCooldownHours = builder.comment("Cooldown entre janelas da mesma regiao (horas)").defineInRange("cooldown_hours", 48, 1, 168)
            minPlayersToStart = builder.comment("Minimo de jogadores online para iniciar assalto").defineInRange("min_players", 3, 1, 50)
            assaultWarning30Min = builder.comment("Avisar 30 min antes da janela").define("warning_30min", true)
            assaultWarning5Min = builder.comment("Avisar 5 min antes da janela").define("warning_5min", true)
            builder.pop()

            builder.push("scoring")
            pointsPerPlayerKill = builder.comment("Pontos por eliminacao de jogador").defineInRange("player_kill", 10, 1, 500)
            pointsPerPresenceTick = builder.comment("Pontos base por tick de presença").defineInRange("presence_tick", 5, 0, 100)
            presenceCheckIntervalSeconds = builder.comment("Intervalo de verificacao de presença (segundos)").defineInRange("presence_interval", 10, 5, 300)
            presenceRewardIntervalSeconds = builder.comment("Intervalo minimo entre recompensas de presença (segundos)").defineInRange("presence_reward_interval", 30, 10, 600)
            vehiclePresenceMultiplier = builder.comment("Multiplicador de pontos por valor do veiculo na presença").defineInRange("vehicle_presence_mult", 0.1, 0.0, 10.0)
            presenceStreakBonusPerTick = builder.comment("Bonus por tick consecutivo de presença").defineInRange("streak_bonus_per_tick", 1, 0, 50)
            maxPresenceStreakBonus = builder.comment("Bonus maximo de streak de presença").defineInRange("max_streak_bonus", 100, 0, 1000)
            defenderPresenceMultiplier = builder.comment("Multiplicador de presença para nação dona defendendo").defineInRange("defender_presence_mult", 1.5, 1.0, 5.0)

            builder.comment("Pontuacao por classe de veiculo destruido:")
            destroyerScore = builder.comment("Destroyer").defineInRange("destroyer", 50, 1, 5000)
            frigateScore = builder.comment("Fragata").defineInRange("frigate", 75, 1, 5000)
            cruiserScore = builder.comment("Cruzador").defineInRange("cruiser", 100, 1, 5000)
            battlecruiserScore = builder.comment("Cruzador de Batalha").defineInRange("battlecruiser", 150, 1, 5000)
            battleshipScore = builder.comment("Couracado").defineInRange("battleship", 200, 1, 5000)
            dreadnoughtScore = builder.comment("Dreadnought").defineInRange("dreadnought", 300, 1, 5000)
            aircraftCarrierScore = builder.comment("Porta-Avioes").defineInRange("aircraft_carrier", 300, 1, 5000)
            submarineScore = builder.comment("Submarino").defineInRange("submarine", 150, 1, 5000)
            patrolBoatScore = builder.comment("Lancha de Patrulha").defineInRange("patrol_boat", 25, 1, 5000)
            transportScore = builder.comment("Navio de Transporte").defineInRange("transport", 20, 1, 5000)
            landingShipScore = builder.comment("Navio de Desembarque").defineInRange("landing_ship", 40, 1, 5000)
            minelayerScore = builder.comment("Minador").defineInRange("minelayer", 60, 1, 5000)

            pointsPerFlagCapture = builder.comment("Pontos por captura de bandeira/ponto").defineInRange("flag_capture", 100, 1, 5000)
            maxScoreCap = builder.comment("Teto maximo de pontuação por assalto").defineInRange("max_score", 10000, 100, 1000000)
            builder.pop()

            builder.push("anti_farm")
            killCooldownMinutes = builder.comment("Cooldown entre kills no mesmo alvo (minutos)").defineInRange("kill_cooldown_min", 5, 1, 60)
            maxKillsPerTargetPerHour = builder.comment("Max kills no mesmo alvo por hora").defineInRange("max_kills_per_target_hour", 3, 1, 20)
            minKillDistance = builder.comment("Distancia minima para kill valer (blocos)").defineInRange("min_kill_distance", 5.0, 0.0, 50.0)
            vehicleDestroyCooldownHours = builder.comment("Cooldown anti-duplicacao de veiculo destruido (horas)").defineInRange("vehicle_destroy_cooldown_hours", 1, 1, 24)
            enableSuicideDetection = builder.comment("Detectar suicidios indiretos").define("suicide_detection", true)
            enableSameIPDetection = builder.comment("Avisar admins se mesmo IP killando").define("same_ip_detection", true)
            builder.pop()

            builder.push("vehicles")
            allowedVehicleClasses = builder.comment("Classes de veiculos permitidas (IDs do VehicleClass)").defineList("allowed_classes", listOf(
                "patrol_boat", "destroyer", "frigate", "cruiser", "battlecruiser",
                "battleship", "dreadnought", "aircraft_carrier", "submarine",
                "transport", "landing_ship", "minelayer"
            ), ModConfigSpec.ValidateString())
            maxVehiclesPerNation = builder.comment("Maximo de veiculos por nacao em assalto").defineInRange("max_per_nation", 5, 1, 50)
            abandonedVehicleTimeoutSeconds = builder.comment("Segundos sem tripulacao para considerar abandonado").defineInRange("abandoned_timeout", 120, 30, 3600)
            requireVehicleCrew = builder.comment("Exigir tripulacao minima para veiculo operacional").define("require_crew", true)
            minCrewForCapitalShip = builder.comment("Tripulacao minima para navios capitais").defineInRange("min_crew_capital", 10, 1, 50)
            builder.pop()

            builder.push("regions")
            regionMinSize = builder.comment("Tamanho minimo da regiao (blocos²)").defineInRange("min_size", 10000, 100, 1000000)
            regionMaxSize = builder.comment("Tamanho maximo da regiao (blocos²)").defineInRange("max_size", 500000, 1000, 10000000)
            regionHeightLimit = builder.comment("Limite de altura da regiao").defineInRange("height_limit", 256, 64, 384)
            maxRegionsPerDimension = builder.comment("Maximo de regioes por dimensao").defineInRange("max_per_dimension", 50, 1, 200)
            builder.pop()

            builder.push("rewards")
            defaultDailyMoneyReward = builder.comment("Recompensa diaria padrao em dinheiro").defineInRange("daily_money", 3456, 0, 1000000)
            dailyResourceRewardEnabled = builder.comment("Habilita recompensas de itens/recursos diarios").define("resource_rewards_enabled", true)
            rewardIntervalHours = builder.comment("Intervalo entre recompensas recorrentes (horas)").defineInRange("interval_hours", 24, 1, 168)
            captureBonusMoney = builder.comment("Bonus de dinheiro ao capturar regiao").defineInRange("capture_bonus_money", 5000, 0, 1000000)
            captureBonusItems = builder.comment("Itens bonus na captura (formato: 'item_id:count:weight')").defineList("capture_bonus_items", listOf(), ModConfigSpec.ValidateString())
            builder.pop()

            builder.push("nation_integration")
            requireNationForAssault = builder.comment("Exige nacao para participar de assalto").define("require_nation", true)
            requiredNationRank = builder.comment("Cargo minimo para declarar assalto (general, coleader, leader)").define("required_rank", "general")
            builder.pop()

            builder.push("objectives")
            objectivesEnabled = builder.comment("Habilita objetivos estrategicos").define("enabled", true)
            requiredObjectivesForVictory = builder.comment("Objetivos obrigatorios para vitoria").define("required_for_victory", false)
            builder.pop()

            builder.push("map_integration")
            enableMapIntegration = builder.comment("Habilita integracao com mapas dinamicos").define("enabled", true)
            mapExportIntervalMinutes = builder.comment("Intervalo de exportacao de dados do mapa (minutos)").defineInRange("export_interval_min", 5, 1, 60)
            preferDynmap = builder.comment("Preferir Dynmap se disponivel").define("prefer_dynmap", true)
            preferBlueMap = builder.comment("Preferir BlueMap se disponivel").define("prefer_bluemap", false)
            preferSquaremap = builder.comment("Preferir Squaremap se disponivel").define("prefer_squaremap", false)
            builder.pop()

            builder.push("notifications")
            globalAnnouncementsEnabled = builder.comment("Habilita anuncios globais").define("global_enabled", true)
            assaultStartSound = builder.comment("Som de inicio de assalto").define("assault_start_sound", "minecraft:entity.ender_dragon.growl")
            assaultEndSound = builder.comment("Som de fim de assalto").define("assault_end_sound", "minecraft:block.beacon.deactivate")
            victorySound = builder.comment("Som de vitoria").define("victory_sound", "minecraft:entity.player.levelup")
            defeatSound = builder.comment("Som de derrota").define("defeat_sound", "minecraft:entity.wither.death")
            objectiveSound = builder.comment("Som de objetivo completado").define("objective_sound", "minecraft:block.note_block.bell")
            capitalShipDestroyedSound = builder.comment("Som de navio capital destruido").define("capital_destroyed_sound", "minecraft:entity.generic.explode")
            announcementSoundVolume = builder.comment("Volume dos sons de anuncio").defineInRange("sound_volume", 1.0, 0.1, 2.0)
            enableDiscordWebhooks = builder.comment("Habilita webhooks Discord").define("discord_enabled", false)
            discordWebhookGlobal = builder.comment("URL do webhook Discord global").define("discord_webhook_global", "")
            builder.pop()

            clientBuilder.push("gui")
            showAssaultHUD = clientBuilder.comment("Mostra HUD durante assalto ativo").define("show_hud", true)
            showRegionMarkers = clientBuilder.comment("Mostra marcadores de regiao no mundo").define("show_markers", true)
            hudPosition = clientBuilder.comment("Posicao do HUD: top_center, top_left, top_right").define("hud_position", "top_center")
            hudScale = clientBuilder.comment("Escala do HUD").defineInRange("hud_scale", 1.0, 0.5, 2.0)
            militaryThemeEnabled = clientBuilder.comment("Habilita tema militar nas GUIs").define("military_theme", true)
            clientBuilder.pop()

            builder.push("history")
            saveBattleHistory = builder.comment("Salvar historico de batalhas").define("save_history", true)
            maxBattlesInMemory = builder.comment("Maximo de batalhas na memoria").defineInRange("max_in_memory", 1000, 100, 10000)
            autoExportCSV = builder.comment("Exportar CSV automaticamente").define("auto_export_csv", false)
            autoExportJSON = builder.comment("Exportar JSON automaticamente").define("auto_export_json", true)
            builder.pop()

            builder.push("performance")
            presenceCheckThreads = builder.comment("Threads para verificacao de presença").defineInRange("presence_threads", 1, 1, 4)
            vehicleScanIntervalMs = builder.comment("Intervalo de scan de veiculos (ms)").defineInRange("vehicle_scan_interval_ms", 5000, 1000, 60000)
            cleanupIntervalMinutes = builder.comment("Intervalo de limpeza de caches (minutos)").defineInRange("cleanup_interval_min", 10, 1, 60)
            maxTrackedVehicles = builder.comment("Maximo de veiculos rastreados simultaneamente").defineInRange("max_tracked_vehicles", 500, 50, 5000)
            builder.pop()

            return builder.build().also { clientBuilder.build() }
        }
    }
}