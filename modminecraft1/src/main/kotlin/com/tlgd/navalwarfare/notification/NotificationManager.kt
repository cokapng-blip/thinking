package com.tlgd.navalwarfare.notification

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mojang.logging.LogUtils
import com.tlgd.navalwarfare.config.NavalWarfareConfig
import com.tlgd.navalwarfare.region.AssaultRegion
import com.tlgd.navalwarfare.objective.Objective
import com.tlgd.navalwarfare.vehicle.VehicleData
import com.tlgd.navalwarfare.network.NetworkHandler
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Gerenciador de notificações e alertas globais.
 * Suporta múltiplos canais: chat, title, actionbar, sound, toast, discord webhook.
 */
class NotificationManager {

    companion object {
        private val LOGGER = com.mojang.logging.LogUtils.getLogger()
        private var INSTANCE: NotificationManager? = null
        private val gson = GsonBuilder().setPrettyPrinting().create()

        fun getInstance(): NotificationManager {
            return INSTANCE ?: throw IllegalStateException("NotificationManager nao inicializado!")
        }

        fun init() {
            INSTANCE = NotificationManager()
            LOGGER.info("NotificationManager inicializado")
        }
    }

    // Templates de notificação
    private val templates = ConcurrentHashMap<String, NotificationTemplate>()
    // Webhooks Discord (opcional)
    private val discordWebhooks = ConcurrentHashMap<String, String>() // eventType -> url
    // Cooldown por jogador para evitar spam
    private val playerCooldowns = ConcurrentHashMap<String, Long>()

    data class NotificationTemplate(
        val eventType: String,
        val globalMessage: String, // Template com placeholders
        val regionMessage: String?,
        val nationMessage: String?,
        val personalMessage: String?,
        val titleText: String?,
        val subtitleText: String?,
        val actionBarText: String?,
        val sound: String,
        val soundVolume: Float = 1.0f,
        val soundPitch: Float = 1.0f,
        val showToast: Boolean = true,
        val toastIcon: String? = null,
        val cooldownSeconds: Int = 0,
    ) {
        companion object {
            val DEFAULT_TEMPLATES: Map<String, NotificationTemplate> = mapOf(
                "assault_starting_30min" to NotificationTemplate(
                    eventType = "assault_starting_30min",
                    globalMessage = "§e⚠ Janela de assalto em §f{region} §einicia em §f30 §eminutos!",
                    regionMessage = null,
                    nationMessage = "§eSua nação tem assalto em §f{region} §eem 30 minutos. Preparem-se!",
                    personalMessage = null,
                    titleText = "§e⚠ ASSALTO PRÓXIMO",
                    subtitleText = "§f{region} §einicia em 30 min",
                    actionBarText = "§eAssalto em {region} inicia em 30 minutos",
                    sound = "minecraft:block.note_block.pling",
                    cooldownSeconds = 300
                ),
                "assault_starting_5min" to NotificationTemplate(
                    eventType = "assault_starting_5min",
                    globalMessage = "§c⚠ §lASSALTO IMINENTE§r §cem §f{region} §c- Inicia em §f5 §cminutos!",
                    regionMessage = null,
                    nationMessage = "§c§lASSALTO EM 5 MINUTOS§r em {region}! Todos ao posto de combate!",
                    personalMessage = null,
                    titleText = "§c⚠ ASSALTO EM 5 MIN",
                    subtitleText = "§f{region}",
                    actionBarText = "§cASSALTO EM {region} INICIA EM 5 MINUTOS!",
                    sound = "minecraft:entity.ender_dragon.growl",
                    soundVolume = 1.5f,
                    cooldownSeconds = 300
                ),
                "assault_started" to NotificationTemplate(
                    eventType = "assault_started",
                    globalMessage = "§c§l⚔ ASSALTO INICIADO§r §7em §e{region} §7por §e{attacker_nation}§7!",
                    regionMessage = "§c§lASSALTO INICIADO§r - Lute pelo controle de {region}!",
                    nationMessage = "§cSua nação iniciou assalto em {region}! Boa sorte!",
                    personalMessage = "§cVocê iniciou o assalto em {region}!",
                    titleText = "§c§lASSALTO INICIADO",
                    subtitleText = "§f{region}",
                    actionBarText = "§cAssalto iniciado em {region} por {attacker_nation}",
                    sound = "minecraft:entity.ender_dragon.growl",
                    soundVolume = 1.0f,
                    showToast = true,
                    toastIcon = "navalwarfare:assault_start"
                ),
                "assault_ended" to NotificationTemplate(
                    eventType = "assault_ended",
                    globalMessage = "§7Assalto em §e{region} §7terminou.",
                    regionMessage = "§7O assalto em {region} terminou.",
                    nationMessage = null,
                    personalMessage = null,
                    titleText = "§7Assalto Finalizado",
                    subtitleText = "§f{region}",
                    actionBarText = "§7Assalto em {region} finalizado",
                    sound = "minecraft:block.beacon.deactivate",
                    cooldownSeconds = 60
                ),
                "region_captured" to NotificationTemplate(
                    eventType = "region_captured",
                    globalMessage = "§a§l🏴 REGIÃO CAPTURADA§r §7- §e{winner_nation}§7 capturou §e{region} §7com §e{score} §7pontos!",
                    regionMessage = "§a§lVITÓRIA!§r {winner_nation} capturou {region}!",
                    nationMessage = "§a§lSUA NAÇÃO CAPTUROU {region}!§r Parabéns!",
                    personalMessage = "§aVocê ajudou a capturar {region}!",
                    titleText = "§a§lREGIÃO CAPTURADA",
                    subtitleText = "§f{region} → {winner_nation}",
                    actionBarText = "§a{winner_nation} capturou {region} com {score} pts",
                    sound = "minecraft:entity.player.levelup",
                    soundVolume = 1.0f,
                    showToast = true,
                    toastIcon = "navalwarfare:victory"
                ),
                "region_lost" to NotificationTemplate(
                    eventType = "region_lost",
                    globalMessage = "§c§l🏴 REGIÃO PERDIDA§r §7- §e{region} §7foi capturada por §e{winner_nation}§7.",
                    regionMessage = "§c§lDERROTA§r {region} foi capturada por {winner_nation}.",
                    nationMessage = "§c§lSUA NAÇÃO PERDEU {region}!§r Recuperem-na na próxima janela!",
                    personalMessage = null,
                    titleText = "§c§lREGIÃO PERDIDA",
                    subtitleText = "§f{region} → {winner_nation}",
                    actionBarText = "§c{region} perdida para {winner_nation}",
                    sound = "minecraft:entity.wither.death",
                    soundVolume = 1.0f,
                    showToast = true,
                    toastIcon = "navalwarfare:defeat"
                ),
                "objective_completed" to NotificationTemplate(
                    eventType = "objective_completed",
                    globalMessage = "§b§l⭐ OBJETIVO CONCLUÍDO§r §7- §e{nation} §7completou §f{objective} §7em §e{region}§7 (+{points} pts)",
                    regionMessage = "§bObjetivo completado: {objective} por {nation} (+{points} pts)",
                    nationMessage = "§bSua nação completou o objetivo: {objective} (+{points} pts)!",
                    personalMessage = "§bVocê completou o objetivo: {objective} (+{points} pts)!",
                    titleText = "§b§lOBJETIVO CONCLUÍDO",
                    subtitleText = "§f{objective}",
                    actionBarText = "§bObjetivo '{objective}' completado por {nation}",
                    sound = "minecraft:block.note_block.bell",
                    soundVolume = 1.0f,
                    showToast = true
                ),
                "objective_failed" to NotificationTemplate(
                    eventType = "objective_failed",
                    globalMessage = "§c§l✗ OBJETIVO FALHOU§r §7- §f{objective} §7em §e{region} §7não foi completado a tempo.",
                    regionMessage = "§cObjetivo falhou: {objective}",
                    nationMessage = "§cSua nação falhou no objetivo: {objective}.",
                    personalMessage = null,
                    titleText = "§c§lOBJETIVO FALHOU",
                    subtitleText = "§f{objective}",
                    actionBarText = "§cObjetivo '{objective}' falhou",
                    sound = "minecraft:block.anvil.break",
                    cooldownSeconds = 60
                ),
                "capital_ship_destroyed" to NotificationTemplate(
                    eventType = "capital_ship_destroyed",
                    globalMessage = "§6§l💥 NAVIO CAPITAL DESTRUÍDO§r §7- §e{ship_class} §7de §e{owner_nation} §7foi afundado por §e{attacker_nation}§7 em §e{region}§7!",
                    regionMessage = "§6§lNAVIO CAPITAL AFUNDADO!§r {ship_class} de {owner_nation} destruído!",
                    nationMessage = "§c§lSEU {ship_class} FOI DESTRUÍDO!§r por {attacker_nation} em {region}",
                    personalMessage = "§6Você destruiu um {ship_class} inimigo!",
                    titleText = "§6§lNAVIO CAPITAL DESTRUÍDO",
                    subtitleText = "§f{ship_class} de {owner_nation}",
                    actionBarText = "§6{ship_class} de {owner_nation} afundado por {attacker_nation}",
                    sound = "minecraft:entity.generic.explode",
                    soundVolume = 1.5f,
                    showToast = true,
                    toastIcon = "navalwarfare:capital_destroyed"
                ),
                "daily_reward" to NotificationTemplate(
                    eventType = "daily_reward",
                    globalMessage = null,
                    regionMessage = null,
                    nationMessage = "§a§l💰 RECOMPENSA DIÁRIA§r Recebidos §e${money} §ee {items} de §e{region}§7!",
                    personalMessage = "§aSua nação recebeu recompensas diárias de {region}: ${money}",
                    titleText = "§a§lRECOMPENSA DIÁRIA",
                    subtitleText = "§f{region}",
                    actionBarText = "§aRecompensa diária de {region}: ${money}",
                    sound = "minecraft:entity.experience_orb.pickup",
                    showToast = true,
                    toastIcon = "navalwarfare:reward"
                ),
                "window_announcement" to NotificationTemplate(
                    eventType = "window_announcement",
                    globalMessage = "§e📅 Próxima janela: §f{region} §e- §f{day} §eàs §f{time} §e({duration} min)",
                    regionMessage = null,
                    nationMessage = null,
                    personalMessage = null,
                    titleText = null,
                    subtitleText = null,
                    actionBarText = "§ePróxima janela: {region} em {timeUntil}",
                    sound = "minecraft:block.note_block.harp",
                    cooldownSeconds = 3600
                )
            )
        }
    }

    init {
        // Carregar templates padrão
        for ((key, template) in NotificationTemplate.DEFAULT_TEMPLATES) {
            templates[key] = template
        }
    }

    /**
     * Envia notificação global para todos os jogadores.
     */
    fun notifyGlobal(eventType: String, placeholders: Map<String, String>) {
        val template = templates[eventType] ?: return
        val message = applyPlaceholders(template.globalMessage, placeholders)
        if (message.isNullOrBlank()) return

        val sound = parseSound(template.sound)
        val server = net.minecraft.server.MinecraftServer.getServer() ?: return

        for (player in server.getPlayerList().getPlayers()) {
            sendToPlayer(player, message, template, sound, placeholders)
        }

        // Discord webhook
        sendDiscordWebhook(eventType, message, placeholders)
    }

    /**
     * Envia notificação para jogadores em uma região.
     */
    fun notifyRegion(region: AssaultRegion, eventType: String, placeholders: Map<String, String>) {
        val template = templates[eventType] ?: return
        val message = applyPlaceholders(template.regionMessage, placeholders)
        if (message.isNullOrBlank()) return

        val sound = parseSound(template.sound)
        val server = net.minecraft.server.MinecraftServer.getServer() ?: return

        for (player in server.getPlayerList().getPlayers()) {
            if (region.bounds.contains(player.blockPosition(), player.level.dimension())) {
                sendToPlayer(player, message, template, sound, placeholders)
            }
        }
    }

    /**
     * Envia notificação para membros de uma nação.
     */
    fun notifyNation(nationId: String, eventType: String, placeholders: Map<String, String>) {
        val template = templates[eventType] ?: return
        val message = applyPlaceholders(template.nationMessage, placeholders)
        if (message.isNullOrBlank()) return

        val sound = parseSound(template.sound)
        val server = net.minecraft.server.MinecraftServer.getServer() ?: return

        for (player in server.getPlayerList().getPlayers()) {
            val nation = com.tlgd.navalwarfare.nation.NationIntegration.getPlayerNation(player)
            if (nation?.id == nationId) {
                sendToPlayer(player, message, template, sound, placeholders)
            }
        }
    }

    /**
     * Envia notificação para jogador específico.
     */
    fun notifyPlayer(player: ServerPlayer, eventType: String, placeholders: Map<String, String>) {
        val template = templates[eventType] ?: return
        val message = applyPlaceholders(template.personalMessage, placeholders)
        if (message.isNullOrBlank()) return

        val sound = parseSound(template.sound)
        sendToPlayer(player, message, template, sound, placeholders)
    }

    /**
     * Envia notificação para admins/OPs.
     */
    fun notifyAdmins(eventType: String, placeholders: Map<String, String>) {
        val template = templates[eventType] ?: return
        val message = applyPlaceholders(template.globalMessage, placeholders)
        if (message.isNullOrBlank()) return

        val sound = parseSound(template.sound)
        val server = net.minecraft.server.MinecraftServer.getServer() ?: return

        for (player in server.getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) { // OP level 2+
                sendToPlayer(player, message, template, sound, placeholders)
            }
        }
    }

    private fun sendToPlayer(
        player: ServerPlayer,
        message: String,
        template: NotificationTemplate,
        sound: SoundEvent?,
        placeholders: Map<String, String>
    ) {
        // Verificar cooldown
        val cooldownKey = "${player.uuid}_${template.eventType}"
        val now = System.currentTimeMillis()
        val lastSent = playerCooldowns[cooldownKey] ?: 0L
        if (now - lastSent < template.cooldownSeconds * 1000L) return
        playerCooldowns[cooldownKey] = now

        // Chat message
        player.sendSystemMessage(Component.literal(message))

        // Title/Subtitle
        template.titleText?.let { title ->
            val titleMsg = applyPlaceholders(title, placeholders)
            player.connection.send(net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal(titleMsg)))
        }
        template.subtitleText?.let { subtitle ->
            val subtitleMsg = applyPlaceholders(subtitle, placeholders)
            player.connection.send(net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(Component.literal(subtitleMsg)))
        }
        if (template.titleText != null || template.subtitleText != null) {
            player.connection.send(net.minecraft.network.protocol.game.ClientboundSetTitleAnimationPacket(10, 70, 20))
        }

        // Action bar
        template.actionBarText?.let { actionBar ->
            val actionBarMsg = applyPlaceholders(actionBar, placeholders)
            player.sendActionBar(Component.literal(actionBarMsg))
        }

        // Sound
        sound?.let { s ->
            player.playSound(s, template.soundVolume, template.soundPitch)
        }

        // Toast (client-side via packet)
        if (template.showToast) {
            val toastIcon = template.toastIcon ?: "navalwarfare:notification"
            NetworkHandler.sendAnnouncementPacket(player, message, template.sound, true)
        }
    }

    private fun applyPlaceholders(template: String?, placeholders: Map<String, String>): String? {
        return template?.let { str ->
            var result = str
            for ((key, value) in placeholders) {
                result = result.replace("{${key}}", value)
            }
            result
        }
    }

    private fun parseSound(soundId: String): SoundEvent? {
        return try {
            val rl = ResourceLocation.parse(soundId)
            SoundEvent.createVariableRangeEvent(rl)
        } catch (e: Exception) {
            SoundEvents.UI_TOAST_IN
        }
    }

    /**
     * Envia webhook para Discord (se configurado).
     */
    private fun sendDiscordWebhook(eventType: String, message: String, placeholders: Map<String, String>) {
        val webhookUrl = discordWebhooks[eventType] ?: discordWebhooks["global"] ?: return

        // Implementar envio HTTP assíncrono para Discord
        // Usar coroutine ou thread separada para não bloquear
        Thread {
            try {
                val json = gson.toJson(mapOf(
                    "content" to message,
                    "username" to "Naval Warfare",
                    "avatar_url" to "https://example.com/navalwarfare_icon.png"
                ))

                val url = java.net.URL(webhookUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(json.toByteArray()) }
                conn.getInputStream().close()
            } catch (e: Exception) {
                LOGGER.debug("Erro ao enviar webhook Discord: {}", e.message)
            }
        }.start()
    }

    // ===== MÉTODOS DE CONVENIÊNCIA PARA EVENTOS COMUNS =====

    fun notifyAssaultStarting30Min(region: AssaultRegion) {
        notifyGlobal("assault_starting_30min", mapOf("region" to region.displayName))
        notifyNation(region.ownerNationId ?: "", "assault_starting_30min", mapOf("region" to region.displayName))
    }

    fun notifyAssaultStarting5Min(region: AssaultRegion) {
        notifyGlobal("assault_starting_5min", mapOf("region" to region.displayName))
        notifyNation(region.ownerNationId ?: "", "assault_starting_5min", mapOf("region" to region.displayName))
    }

    fun notifyAssaultStarted(region: AssaultRegion, attackerNationName: String) {
        notifyGlobal("assault_started", mapOf(
            "region" to region.displayName,
            "attacker_nation" to attackerNationName
        ))
    }

    fun notifyAssaultEnded(region: AssaultRegion) {
        notifyGlobal("assault_ended", mapOf("region" to region.displayName))
    }

    fun notifyRegionCaptured(region: AssaultRegion, winnerNationName: String, score: Int) {
        val placeholders = mapOf(
            "region" to region.displayName,
            "winner_nation" to winnerNationName,
            "score" to score.toString()
        )
        notifyGlobal("region_captured", placeholders)
        notifyNation(region.ownerNationId ?: "", "region_lost", placeholders)
        // Notificar nação vencedora
        // Precisaria do nationId do vencedor
    }

    fun notifyObjectiveCompleted(region: AssaultRegion, objective: Objective, nationName: String) {
        val placeholders = mapOf(
            "region" to region.displayName,
            "objective" to objective.displayName,
            "nation" to nationName,
            "points" to objective.rewardPoints.toString()
        )
        notifyGlobal("objective_completed", placeholders)
        notifyNation("", "objective_completed", placeholders) // nationId seria passado
    }

    fun notifyObjectiveFailed(region: AssaultRegion, objective: Objective) {
        val placeholders = mapOf(
            "region" to region.displayName,
            "objective" to objective.displayName
        )
        notifyGlobal("objective_failed", placeholders)
    }

    fun notifyCapitalShipDestroyed(region: AssaultRegion, shipData: VehicleData, attackerNationName: String) {
        val placeholders = mapOf(
            "region" to region.displayName,
            "ship_class" to shipData.getVehicleClass()?.displayName ?: shipData.vehicleClassId,
            "owner_nation" to shipData.nationName ?: "Desconhecida",
            "attacker_nation" to attackerNationName
        )
        notifyGlobal("capital_ship_destroyed", placeholders)
        shipData.nationId?.let { notifyNation(it, "capital_ship_destroyed", placeholders) }
    }

    fun notifyDailyReward(region: AssaultRegion, nationName: String, money: Long, items: List<String>) {
        val placeholders = mapOf(
            "region" to region.displayName,
            "nation" to nationName,
            "money" to money.toString(),
            "items" to items.joinToString(", ")
        )
        notifyNation("", "daily_reward", placeholders)
    }

    fun notifyNextWindow(region: AssaultRegion, window: com.tlgd.navalwarfare.region.AttackWindow) {
        val timeUntil = window.getTimeUntilStart()
        val placeholders = mapOf(
            "region" to region.displayName,
            "day" to window.dayOfWeek.name,
            "time" to window.startTime.toString(),
            "duration" to window.durationMinutes.toString(),
            "timeUntil" to "${timeUntil}min"
        )
        notifyGlobal("window_announcement", placeholders)
    }

    /**
     * Configura template customizado (admin).
     */
    fun setTemplate(template: NotificationTemplate) {
        templates[template.eventType] = template
    }

    /**
     * Configura webhook Discord.
     */
    fun setDiscordWebhook(eventType: String, url: String) {
        discordWebhooks[eventType] = url
    }

    /**
     * Remove webhook Discord.
     */
    fun removeDiscordWebhook(eventType: String) {
        discordWebhooks.remove(eventType)
    }

    /**
     * Limpa cooldowns antigos.
     */
    fun cleanupCooldowns() {
        val now = System.currentTimeMillis()
        playerCooldowns.entries.removeIf { (_, time) -> now - time > 3600000 } // 1 hora
    }
}