package com.tlgd.navalwarfare.vehicle

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mojang.logging.LogUtils
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener
import net.minecraft.util.profiling.ProfilerFiller
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Registro central de classes de veículos.
 * Carrega classes de arquivos JSON (datapack) e permite registro dinâmico.
 */
class VehicleRegistry : SimpleJsonResourceReloadListener(GsonBuilder().create(), "navalwarfare/vehicle_classes") {

    companion object {
        private val LOGGER: Logger = LogUtils.getLogger()
        private var INSTANCE: VehicleRegistry? = null

        fun getInstance(): VehicleRegistry {
            return INSTANCE ?: throw IllegalStateException("VehicleRegistry nao inicializado!")
        }

        fun init() {
            INSTANCE = VehicleRegistry()
        }
    }

    private val classes = ConcurrentHashMap<String, VehicleClass>()
    private val classList = CopyOnWriteArrayList<VehicleClass>()

    override fun apply(resourceMap: Map<ResourceLocation, com.google.gson.JsonElement>, resourceManager: ResourceManager, profiler: ProfilerFiller) {
        classes.clear()
        classList.clear()

        // Carregar classes padrão primeiro
        for (vc in VehicleClass.defaultClasses()) {
            registerClass(vc)
        }

        // Carregar classes de datapacks
        for ((rl, element) in resourceMap) {
            try {
                val json = element.jsonObject
                val vc = VehicleClass.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, json).getOrThrow().first
                registerClass(vc)
                LOGGER.info("Carregada classe de veiculo de datapack: {} ({})", vc.id, rl)
            } catch (e: Exception) {
                LOGGER.error("Erro ao carregar classe de veiculo {}: {}", rl, e.message)
            }
        }

        LOGGER.info("VehicleRegistry carregado: {} classes", classes.size)
    }

    fun registerClass(vehicleClass: VehicleClass) {
        classes[vehicleClass.id] = vehicleClass
        if (!classList.contains(vehicleClass)) {
            classList.add(vehicleClass)
        }
        // Manter ordenado por scoreValue decrescente (maiores primeiro para classificação)
        classList.sortByDescending { it.scoreValue }
    }

    fun unregisterClass(id: String): Boolean {
        val removed = classes.remove(id)
        classList.removeIf { it.id == id }
        return removed != null
    }

    fun getClass(id: String): VehicleClass? = classes[id]

    fun getAllClasses(): List<VehicleClass> = classList.toList()

    fun getClassesForRegion(regionId: String): List<VehicleClass> {
        return classList.filter { it.isAllowedInRegion(regionId) }
    }

    fun findMatchingClass(blockCount: Int, length: Int, width: Int, height: Int, blockTypes: Set<String>, regionId: String? = null): VehicleClass? {
        val candidates = if (regionId != null) getClassesForRegion(regionId) else classList
        return candidates.firstOrNull { it.matches(blockCount, length, width, height, blockTypes) }
    }

    fun getClassByScoreValue(scoreValue: Int): VehicleClass? {
        return classList.firstOrNull { it.scoreValue == scoreValue }
    }
}