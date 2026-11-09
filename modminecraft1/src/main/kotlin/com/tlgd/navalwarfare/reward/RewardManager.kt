package com.tlgd.navalwarfare.reward

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mojang.logging.LogUtils
import com.tlgd.navalwarfare.config.NavalWarfareConfig
import com.tlgd.navalwarfare.region.AssaultRegion
import com.tlgd.navalwarfare.nation.NationIntegration
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Gerenciador de recompensas por controle de regiões.
 * Suporta: dinheiro, itens, recursos, blueprints, permissões.
 */
class RewardManager {

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private var INSTANCE: RewardManager? = null
        private val gson = GsonBuilder().setPrettyPrinting().create()
        private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "NavalWarfare-RewardThread").apply { isDaemon = true }
        }

        fun getInstance(): RewardManager {
            return INSTANCE ?: throw IllegalStateException("RewardManager nao inicializado!")
        }

        fun init() {
            INSTANCE = RewardManager()
            // Agendar recompensas diárias à meia-noite do servidor
            scheduler.scheduleAtFixedRate(
                { getInstance().distributeDailyRewards() },
                calculateInitialDelay(),
                24 * 60 * 60, // 24 horas
                TimeUnit.SECONDS
            )
            LOGGER.info("RewardManager inicializado - recompensas diarias agendadas")
        }

        fun shutdown() {
            scheduler.shutdown()
        }

        private fun calculateInitialDelay(): Long {
            // Calcular segundos até próxima meia-noite (horário do servidor)
            val now = java.time.LocalTime.now()
            val midnight = java.time.LocalTime.MIDNIGHT.plusMinutes(1) // 00:01 para evitar conflito com reset
            val secondsUntil = java.time.Duration.between(now, midnight).seconds
            return if (secondsUntil > 0) secondsUntil else secondsUntil + 24 * 3600
        }
    }

    // regionId -> RegionRewards
    private val regionRewards = ConcurrentHashMap<String, RegionRewards>()
    // nationId -> regionId -> lastRewardTime
    private val lastRewardTime = ConcurrentHashMap<String, ConcurrentHashMap<String, Long>>()
    // Log de entregas
    private val deliveryLog = ConcurrentHashMap<String, ConcurrentHashMap<String, DeliveryEntry>>()

    data class RegionRewards(
        val regionId: String,
        val dailyMoney: Long = NavalWarfareConfig.defaultDailyMoneyReward,
        val dailyItems: List<RewardItem> = emptyList(),
        val dailyResources: List<RewardResource> = emptyList(),
        val captureBonusMoney: Long = 0,
        val captureBonusItems: List<RewardItem> = emptyList(),
        val intervalHours: Int = 24, // Intervalo entre recompensas recorrentes
        val enabled: Boolean = true,
    ) {
        companion object {
            val CODEC: com.mojang.serialization.Codec<RegionRewards> = com.mojang.serialization.codecs.RecordCodecBuilder.create { instance ->
                instance.group(
                    com.mojang.serialization.Codec.STRING.fieldOf("region_id").forGetter { it.regionId },
                    com.mojang.serialization.Codec.LONG.fieldOf("daily_money").forGetter { it.dailyMoney },
                    RewardItem.CODEC.listOf().fieldOf("daily_items").forGetter { it.dailyItems },
                    RewardResource.CODEC.listOf().fieldOf("daily_resources").forGetter { it.dailyResources },
                    com.mojang.serialization.Codec.LONG.fieldOf("capture_bonus_money").forGetter { it.captureBonusMoney },
                    RewardItem.CODEC.listOf().fieldOf("capture_bonus_items").forGetter { it.captureBonusItems },
                    com.mojang.serialization.Codec.INT.fieldOf("interval_hours").forGetter { it.intervalHours },
                    com.mojang.serialization.Codec.BOOL.fieldOf("enabled").forGetter { it.enabled }
                ).apply(instance, RegionRewards::new)
            }
        }
    }

    data class RewardItem(
        val itemId: String, // "minecraft:diamond"
        val count: Int = 1,
        val weight: Int = 100, // Peso para chance (100 = garantido)
        val nbt: String? = null, // NBT opcional
    ) {
        companion object {
            val CODEC: com.mojang.serialization.Codec<RewardItem> = com.mojang.serialization.codecs.RecordCodecBuilder.create { instance ->
                instance.group(
                    com.mojang.serialization.Codec.STRING.fieldOf("item_id").forGetter { it.itemId },
                    com.mojang.serialization.Codec.INT.fieldOf("count").forGetter { it.count },
                    com.mojang.serialization.Codec.INT.fieldOf("weight").forGetter { it.weight },
                    com.mojang.serialization.Codec.STRING.optionalFieldOf("nbt").forGetter { it.nbt }
                ).apply(instance, RewardItem::new)
            }
        }

        fun createStack(): ItemStack {
            val item = net.minecraft.world.item.Items.byName(ResourceLocation.parse(itemId)).orElse(null)
            if (item == null) return ItemStack.EMPTY
            val stack = ItemStack(item, count)
            if (nbt != null) {
                try {
                    val tag = com.mojang.serialization.JsonOps.INSTANCE
                    // Parse NBT string se necessário
                } catch (e: Exception) {}
            }
            return stack
        }
    }

    data class RewardResource(
        val resourceId: String, // ID do recurso (ex: "iron", "coal", "naval_steel")
        val amount: Long,
    ) {
        companion object {
            val CODEC: com.mojang.serialization.Codec<RewardResource> = com.mojang.serialization.codecs.RecordCodecBuilder.create { instance ->
                instance.group(
                    com.mojang.serialization.Codec.STRING.fieldOf("resource_id").forGetter { it.resourceId },
                    com.mojang.serialization.Codec.LONG.fieldOf("amount").forGetter { it.amount }
                ).apply(instance, RewardResource::new)
            }
        }
    }

    data class DeliveryEntry(
        val timestamp: Long,
        val regionId: String,
        val nationId: String,
        val money: Long,
        val items: List<String>,
        val resources: List<String>,
        val type: String, // "daily", "capture", "interval"
    )

    /**
     * Carrega recompensas de uma região a partir de arquivo.
     */
    fun loadRegionRewards(regionId: String, file: File) {
        if (!file.exists()) {
            regionRewards[regionId] = RegionRewards(regionId = regionId)
            return
        }

        try {
            val json = file.readText()
            val rewards = gson.fromJson(json, RegionRewards::class.java)
            regionRewards[regionId] = rewards
            LOGGER.info("Recompensas carregadas para regiao {}: $${} dinheiro, {} itens, {} recursos",
                regionId, rewards.dailyMoney, rewards.dailyItems.size, rewards.dailyResources.size)
        } catch (e: Exception) {
            LOGGER.error("Erro ao carregar recompensas da regiao {}: {}", regionId, e.message)
            regionRewards[regionId] = RegionRewards(regionId = regionId)
        }
    }

    /**
     * Salva recompensas de uma região.
     */
    fun saveRegionRewards(regionId: String, file: File) {
        val rewards = regionRewards[regionId] ?: return
        val json = gson.toJson(rewards)
        file.writeText(json)
    }

    /**
     * Entrega recompensas de captura (imediata).
     */
    fun deliverCaptureRewards(region: AssaultRegion, nationId: String, nationName: String) {
        val rewards = regionRewards[region.id] ?: return
        if (!rewards.enabled) return

        val nation = NationIntegration.getNationById(nationId) ?: return
        var moneyDelivered = 0L
        val itemsDelivered = mutableListOf<String>()
        val resourcesDelivered = mutableListOf<String>()

        // Dinheiro de captura
        if (rewards.captureBonusMoney > 0) {
            EconomyIntegration.addMoney(nationId, rewards.captureBonusMoney)
            moneyDelivered += rewards.captureBonusMoney
        }

        // Itens de captura
        for (item in rewards.captureBonusItems) {
            if (shouldGiveReward(item.weight)) {
                val stack = item.createStack()
                if (!stack.isEmpty()) {
                    giveItemsToNation(nation, stack)
                    itemsDelivered.add("${stack.count}x ${item.itemId}")
                }
            }
        }

        logDelivery(region.id, nationId, moneyDelivered, itemsDelivered, resourcesDelivered, "capture")
        LOGGER.info("Recompensas de captura entregues a {} em {}: ${} + {} itens", nationName, region.id, moneyDelivered, itemsDelivered.size)
    }

    /**
     * Distribui recompensas diárias para todas as regiões controladas.
     */
    fun distributeDailyRewards() {
        val manager = com.tlgd.navalwarfare.region.AssaultRegionManager.getInstance()
        val now = System.currentTimeMillis()

        for (region in manager.getAllRegions()) {
            val ownerNationId = region.ownerNationId ?: continue
            val ownerNationName = region.ownerNationName ?: continue

            // Verificar se região está pacífica (não em assalto)
            if (region.state != com.tlgd.navalwarfare.region.AssaultState.PEACEFUL) continue

            val rewards = regionRewards[region.id] ?: continue
            if (!rewards.enabled) continue

            // Verificar intervalo
            val nationLastRewards = lastRewardTime[ownerNationId] ?: ConcurrentHashMap()
            val lastTime = nationLastRewards[region.id] ?: 0L
            val intervalMs = rewards.intervalHours * 3600 * 1000L

            if (now - lastTime < intervalMs) continue

            deliverDailyRewards(region, ownerNationId, ownerNationName, rewards)
            nationLastRewards[region.id] = now
        }
    }

    private fun deliverDailyRewards(region: AssaultRegion, nationId: String, nationName: String, rewards: RegionRewards) {
        val nation = NationIntegration.getNationById(nationId) ?: return
        var moneyDelivered = 0L
        val itemsDelivered = mutableListOf<String>()
        val resourcesDelivered = mutableListOf<String>()

        // Dinheiro diário
        if (rewards.dailyMoney > 0) {
            EconomyIntegration.addMoney(nationId, rewards.dailyMoney)
            moneyDelivered += rewards.dailyMoney
        }

        // Itens diários
        for (item in rewards.dailyItems) {
            if (shouldGiveReward(item.weight)) {
                val stack = item.createStack()
                if (!stack.isEmpty()) {
                    giveItemsToNation(nation, stack)
                    itemsDelivered.add("${stack.count}x ${item.itemId}")
                }
            }
        }

        // Recursos diários
        for (resource in rewards.dailyResources) {
            addResourceToNation(nation, resource.resourceId, resource.amount)
            resourcesDelivered.add("${resource.amount}x ${resource.resourceId}")
        }

        logDelivery(region.id, nationId, moneyDelivered, itemsDelivered, resourcesDelivered, "daily")
        LOGGER.info("Recompensas diarias entregues a {} em {}: ${} + {} itens + {} recursos",
            nationName, region.id, moneyDelivered, itemsDelivered.size, resourcesDelivered.size)
    }

    /**
     * Entrega recompensa de intervalo configurável.
     */
    fun deliverIntervalRewards(regionId: String, nationId: String) {
        val regionRewards = regionRewards[regionId] ?: return
        val nation = NationIntegration.getNationById(nationId) ?: return
        // Similar ao daily mas com intervalo customizado
    }

    private fun shouldGiveReward(weight: Int): Boolean {
        return weight >= 100 || (Math.random() * 100) < weight
    }

    private fun giveItemsToNation(nation: NationIntegration.NationData, stack: ItemStack) {
        // Tentar adicionar ao armazenamento da nação
        // Implementação depende do sistema de nações
        NationIntegration.addDailyResources(nation.id, mapOf(stack.getItem().toString() to stack.count))
    }

    private fun addResourceToNation(nation: NationIntegration.NationData, resourceId: String, amount: Long) {
        NationIntegration.addDailyResources(nation.id, mapOf(resourceId to amount))
    }

    private fun logDelivery(regionId: String, nationId: String, money: Long, items: List<String>, resources: List<String>, type: String) {
        val entry = DeliveryEntry(
            timestamp = System.currentTimeMillis(),
            regionId = regionId,
            nationId = nationId,
            money = money,
            items = items,
            resources = resources,
            type = type
        )

        val nationLog = deliveryLog.computeIfAbsent(nationId) { ConcurrentHashMap() }
        val key = "${regionId}_${System.currentTimeMillis()}"
        nationLog[key] = entry

        // Limitar tamanho do log (manter últimas 100 entradas por nação)
        if (nationLog.size > 100) {
            val oldestKey = nationLog.keys.minOrNull()
            oldestKey?.let { nationLog.remove(it) }
        }
    }

    /**
     * Obtém recompensas configuradas para uma região.
     */
    fun getRegionRewards(regionId: String): RegionRewards? {
        return regionRewards[regionId]
    }

    /**
     * Define recompensas para uma região (admin).
     */
    fun setRegionRewards(regionId: String, rewards: RegionRewards) {
        regionRewards[regionId] = rewards
    }

    /**
     * Obtém histórico de entregas para uma nação.
     */
    fun getDeliveryLog(nationId: String): List<DeliveryEntry> {
        return deliveryLog[nationId]?.values?.toList() ?: emptyList()
    }

    /**
     * Força entrega de recompensa diária (admin/teste).
     */
    fun forceDailyReward(regionId: String) {
        val region = com.tlgd.navalwarfare.region.AssaultRegionManager.getInstance().getRegion(regionId) ?: return
        val ownerNationId = region.ownerNationId ?: return
        val ownerNationName = region.ownerNationName ?: return
        val rewards = regionRewards[regionId] ?: return
        deliverDailyRewards(region, ownerNationId, ownerNationName, rewards)
    }
}

/**
 * Interface para integração com sistemas de economia.
 * Implementar conforme o plugin/mod de economia do servidor.
 */
object EconomyIntegration {

    /**
     * Adiciona dinheiro a uma nação.
     */
    fun addMoney(nationId: String, amount: Long) {
        // TODO: Implementar integração com:
        // - Vault (Bukkit/Spigot via plugin)
        // - TLGD Economy (se existir)
        // - Essentials Economy
        // - PlayerPoints
        // - Custom economy API

        val nation = NationIntegration.getNationById(nationId) ?: return
        nation.bankBalance += amount
        NationIntegration.saveNation(nation)

        // Log para debug
        com.mojang.logging.LogUtils.getLogger().info("[Economy] Adicionado $${} a nação {}", amount, nationId)
    }

    /**
     * Remove dinheiro de uma nação.
     */
    fun removeMoney(nationId: String, amount: Long): Boolean {
        val nation = NationIntegration.getNationById(nationId) ?: return false
        if (nation.bankBalance < amount) return false
        nation.bankBalance -= amount
        NationIntegration.saveNation(nation)
        return true
    }

    /**
     * Obtém saldo de uma nação.
     */
    fun getBalance(nationId: String): Long {
        return NationIntegration.getNationById(nationId)?.bankBalance ?: 0
    }

    /**
     * Verifica se nação tem saldo suficiente.
     */
    fun hasBalance(nationId: String, amount: Long): Boolean {
        return getBalance(nationId) >= amount
    }
}