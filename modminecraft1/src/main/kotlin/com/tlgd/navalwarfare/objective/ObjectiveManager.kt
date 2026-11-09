package com.tlgd.navalwarfare.objective

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mojang.logging.LogUtils
import com.tlgd.navalwarfare.region.AssaultRegion
import com.tlgd.navalwarfare.scoring.ScoringManager
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import org.slf4j.Logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Gerenciador de objetivos estratégicos por região.
 */
class ObjectiveManager {

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private var INSTANCE: ObjectiveManager? = null
        private val gson = GsonBuilder().setPrettyPrinting().create()

        fun getInstance(): ObjectiveManager {
            return INSTANCE ?: throw IllegalStateException("ObjectiveManager nao inicializado!")
        }

        fun init() {
            INSTANCE = ObjectiveManager()
            LOGGER.info("ObjectiveManager inicializado")
        }
    }

    // regionId -> List<Objective>
    private val regionObjectives = ConcurrentHashMap<String, CopyOnWriteArrayList<Objective>>()
    // regionId -> objectiveId -> Objective (para lookup rápido)
    private val objectiveMap = ConcurrentHashMap<String, ConcurrentHashMap<String, Objective>>()

    /**
     * Carrega objetivos de uma região a partir de arquivo JSON.
     */
    fun loadRegionObjectives(regionId: String, file: File) {
        if (!file.exists()) {
            regionObjectives[regionId] = CopyOnWriteArrayList()
            objectiveMap[regionId] = ConcurrentHashMap()
            return
        }

        try {
            val json = file.readText()
            val list = gson.fromJson(json, Array<Map<String, Any>>::class.java).toList()
            val objectives = list.mapNotNull { ObjectiveFactory.createFromJson(it) }

            val objectiveList = CopyOnWriteArrayList<Objective>()
            objectiveList.addAll(objectives)
            regionObjectives[regionId] = objectiveList

            val map = ConcurrentHashMap<String, Objective>()
            for (obj in objectives) {
                map[obj.id] = obj
            }
            objectiveMap[regionId] = map

            LOGGER.info("Carregados {} objetivos para regiao {}", objectives.size, regionId)
        } catch (e: Exception) {
            LOGGER.error("Erro ao carregar objetivos da regiao {}: {}", regionId, e.message)
            regionObjectives[regionId] = CopyOnWriteArrayList()
            objectiveMap[regionId] = ConcurrentHashMap()
        }
    }

    /**
     * Salva objetivos de uma região em arquivo JSON.
     */
    fun saveRegionObjectives(regionId: String, file: File) {
        val objectives = regionObjectives[regionId] ?: return
        val json = gson.toJson(objectives.map { it.serialize() })
        file.writeText(json)
    }

    /**
     * Inicia objetivos de uma região (chamado quando assalto começa).
     */
    fun startRegionObjectives(region: AssaultRegion, level: ServerLevel) {
        val objectives = regionObjectives[region.id] ?: return
        for (objective in objectives) {
            if (objective.state == Objective.State.PENDING) {
                objective.onAssaultStart(region, level)
            }
        }
    }

    /**
     * Atualiza objetivos (chamado periodicamente durante assalto).
     */
    fun tickRegionObjectives(region: AssaultRegion, level: ServerLevel): List<Objective> {
        val objectives = regionObjectives[region.id] ?: return emptyList()
        val completed = mutableListOf<Objective>()

        for (objective in objectives) {
            if (objective.state == Objective.State.ACTIVE) {
                val newState = objective.onTick(region, level)
                if (newState == Objective.State.COMPLETED) {
                    completed.add(objective)
                    // Conceder pontos
                    objective.completedByNationId?.let { nationId ->
                        objective.completedByNationName?.let { nationName ->
                            ScoringManager.getInstance().onObjectiveComplete(
                                region.id, nationId, nationName, objective.id, objective.rewardPoints
                            )
                        }
                    }
                    // Notificar
                    com.tlgd.navalwarfare.notification.NotificationManager.getInstance().notifyObjectiveCompleted(
                        region, objective
                    )
                } else if (newState == Objective.State.FAILED) {
                    // Objetivo falhou
                    com.tlgd.navalwarfare.notification.NotificationManager.getInstance().notifyObjectiveFailed(
                        region, objective
                    )
                }
            }
        }

        return completed
    }

    /**
     * Evento de jogador (kill, captura, etc).
     */
    fun onPlayerEvent(region: AssaultRegion, player: ServerPlayer, eventType: String, data: Map<String, Any>) {
        val objectives = regionObjectives[region.id] ?: return
        for (objective in objectives) {
            if (objective.state == Objective.State.ACTIVE) {
                objective.onPlayerEvent(region, player, eventType, data)
            }
        }
    }

    /**
     * Evento de veículo (destruição, movimento, etc).
     */
    fun onVehicleEvent(region: AssaultRegion, entity: Entity, eventType: String, data: Map<String, Any>) {
        val objectives = regionObjectives[region.id] ?: return
        for (objective in objectives) {
            if (objective.state == Objective.State.ACTIVE) {
                objective.onVehicleEvent(region, entity, eventType, data)
            }
        }
    }

    /**
     * Obtém objetivos de uma região.
     */
    fun getRegionObjectives(regionId: String): List<Objective> {
        return regionObjectives[regionId]?.toList() ?: emptyList()
    }

    /**
     * Obtém objetivos ativos de uma região.
     */
    fun getActiveObjectives(regionId: String): List<Objective> {
        return regionObjectives[regionId]?.filter { it.state == Objective.State.ACTIVE } ?: emptyList()
    }

    /**
     * Obtém objetivos concluídos de uma região.
     */
    fun getCompletedObjectives(regionId: String): List<Objective> {
        return regionObjectives[regionId]?.filter { it.state == Objective.State.COMPLETED } ?: emptyList()
    }

    /**
     * Obtém um objetivo específico.
     */
    fun getObjective(regionId: String, objectiveId: String): Objective? {
        return objectiveMap[regionId]?.get(objectiveId)
    }

    /**
     * Adiciona objetivo a uma região (admin).
     */
    fun addObjective(regionId: String, objective: Objective) {
        val list = regionObjectives.computeIfAbsent(regionId) { CopyOnWriteArrayList() }
        list.add(objective)
        objectiveMap.computeIfAbsent(regionId) { ConcurrentHashMap() }[objective.id] = objective
    }

    /**
     * Remove objetivo de uma região.
     */
    fun removeObjective(regionId: String, objectiveId: String): Boolean {
        val list = regionObjectives[regionId] ?: return false
        val removed = list.removeIf { it.id == objectiveId }
        objectiveMap[regionId]?.remove(objectiveId)
        return removed
    }

    /**
     * Reseta objetivos de uma região (novo assalto).
     */
    fun resetRegionObjectives(regionId: String) {
        val objectives = regionObjectives[regionId] ?: return
        for (objective in objectives) {
            objective.state = Objective.State.PENDING
            objective.progress = 0.0
            objective.startedAt = 0
            objective.completedAt = 0
            objective.completedByNationId = null
            objective.completedByNationName = null
        }
    }

    /**
     * Verifica se todos os objetivos obrigatórios foram completados.
     */
    fun areRequiredObjectivesCompleted(regionId: String): Boolean {
        val objectives = regionObjectives[regionId] ?: return true
        return objectives.filter { it.isRequiredForVictory }.all { it.state == Objective.State.COMPLETED }
    }

    /**
     * Obtém progresso geral dos objetivos para HUD.
     */
    fun getObjectivesProgress(regionId: String): Map<String, Any> {
        val objectives = regionObjectives[regionId] ?: return emptyMap()
        return mapOf(
            "total" to objectives.size,
            "active" to objectives.count { it.state == Objective.State.ACTIVE },
            "completed" to objectives.count { it.state == Objective.State.COMPLETED },
            "failed" to objectives.count { it.state == Objective.State.FAILED },
            "pending" to objectives.count { it.state == Objective.State.PENDING },
            "objectives" to objectives.map { it.getDisplayInfo() }
        )
    }
}