package com.tlgd.navalwarfare.vehicle

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation

/**
 * Classe de veículo naval configurável.
 * Define as categorias de navios para pontuação e restrições.
 */
data class VehicleClass(
    val id: String,
    val displayName: String,
    val maxLength: Int,
    val maxWidth: Int,
    val maxHeight: Int,
    val minBlocks: Int,
    val maxBlocks: Int,
    val scoreValue: Int,
    val requiredBlocks: List<String>,
    val forbiddenBlocks: List<String>,
    val iconTexture: String? = null,
    val color: Int = 0xFFFFFFFF,
    val description: String = "",
    val allowedInRegions: List<String> = listOf("*"),
    val maxPerNation: Int = 5,
    val requiresCrew: Int = 1,
) {
    companion object {
        val CODEC: Codec<VehicleClass> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("display_name").forGetter { it.displayName },
                Codec.INT.fieldOf("max_length").forGetter { it.maxLength },
                Codec.INT.fieldOf("max_width").forGetter { it.maxWidth },
                Codec.INT.fieldOf("max_height").forGetter { it.maxHeight },
                Codec.INT.fieldOf("min_blocks").forGetter { it.minBlocks },
                Codec.INT.fieldOf("max_blocks").forGetter { it.maxBlocks },
                Codec.INT.fieldOf("score_value").forGetter { it.scoreValue },
                Codec.STRING.listOf().fieldOf("required_blocks").forGetter { it.requiredBlocks },
                Codec.STRING.listOf().fieldOf("forbidden_blocks").forGetter { it.forbiddenBlocks },
                Codec.STRING.optionalFieldOf("icon_texture").forGetter { it.iconTexture },
                Codec.INT.fieldOf("color").forGetter { it.color },
                Codec.STRING.fieldOf("description").forGetter { it.description },
                Codec.STRING.listOf().fieldOf("allowed_in_regions").forGetter { it.allowedInRegions },
                Codec.INT.fieldOf("max_per_nation").forGetter { it.maxPerNation },
                Codec.INT.fieldOf("requires_crew").forGetter { it.requiresCrew }
            ).apply(instance, VehicleClass::new)
        }

        fun defaultClasses(): List<VehicleClass> {
            return listOf(
                VehicleClass(
                    id = "patrol_boat",
                    displayName = "Lancha de Patrulha",
                    maxLength = 30,
                    maxWidth = 10,
                    maxHeight = 15,
                    minBlocks = 50,
                    maxBlocks = 500,
                    scoreValue = 25,
                    requiredBlocks = listOf(),
                    forbiddenBlocks = listOf("create:large_propeller", "create:display_link"),
                    color = 0xFF88CC88,
                    description = "Embarcacao leve de reconhecimento e patrulha",
                    requiresCrew = 1
                ),
                VehicleClass(
                    id = "destroyer",
                    displayName = "Destroyer",
                    maxLength = 80,
                    maxWidth = 15,
                    maxHeight = 25,
                    minBlocks = 500,
                    maxBlocks = 3000,
                    scoreValue = 50,
                    requiredBlocks = listOf("create:propeller"),
                    forbiddenBlocks = listOf("create:display_link"),
                    color = 0xFF8888FF,
                    description = "Navio de escolta e combate anti-submarino",
                    requiresCrew = 3
                ),
                VehicleClass(
                    id = "frigate",
                    displayName = "Fragata",
                    maxLength = 120,
                    maxWidth = 20,
                    maxHeight = 30,
                    minBlocks = 3000,
                    maxBlocks = 8000,
                    scoreValue = 75,
                    requiredBlocks = listOf("create:propeller", "minecraft:dispenser"),
                    forbiddenBlocks = listOf("create:display_link"),
                    color = 0xFF88AAFF,
                    description = "Navio de escolta multimissao",
                    requiresCrew = 5
                ),
                VehicleClass(
                    id = "cruiser",
                    displayName = "Cruzador",
                    maxLength = 180,
                    maxWidth = 25,
                    maxHeight = 35,
                    minBlocks = 8000,
                    maxBlocks = 20000,
                    scoreValue = 100,
                    requiredBlocks = listOf("create:propeller", "minecraft:dispenser", "minecraft:observer"),
                    forbiddenBlocks = listOf("create:display_link"),
                    color = 0xFF00AAFF,
                    description = "Navio de guerra de medio porte, versatil",
                    requiresCrew = 8
                ),
                VehicleClass(
                    id = "battlecruiser",
                    displayName = "Cruzador de Batalha",
                    maxLength = 220,
                    maxWidth = 30,
                    maxHeight = 40,
                    minBlocks = 20000,
                    maxBlocks = 40000,
                    scoreValue = 150,
                    requiredBlocks = listOf("create:propeller", "minecraft:dispenser", "minecraft:observer", "minecraft:command_block"),
                    forbiddenBlocks = listOf("create:display_link"),
                    color = 0xFF0088FF,
                    description = "Navio rapido com poder de fogo de couracado",
                    requiresCrew = 12
                ),
                VehicleClass(
                    id = "battleship",
                    displayName = "Couracado",
                    maxLength = 260,
                    maxWidth = 35,
                    maxHeight = 45,
                    minBlocks = 40000,
                    maxBlocks = 80000,
                    scoreValue = 200,
                    requiredBlocks = listOf("create:large_propeller", "minecraft:dispenser", "minecraft:observer", "minecraft:command_block"),
                    forbiddenBlocks = listOf(),
                    color = 0xFF0066CC,
                    description = "Navio capital de linha de frente, blindagem pesada",
                    requiresCrew = 20
                ),
                VehicleClass(
                    id = "dreadnought",
                    displayName = "Dreadnought",
                    maxLength = 300,
                    maxWidth = 45,
                    maxHeight = 50,
                    minBlocks = 80000,
                    maxBlocks = 150000,
                    scoreValue = 300,
                    requiredBlocks = listOf("create:large_propeller", "minecraft:dispenser", "minecraft:observer", "minecraft:command_block", "minecraft:structure_block"),
                    forbiddenBlocks = listOf(),
                    color = 0xFF0044AA,
                    description = "Super-couracado, apice do poder naval",
                    requiresCrew = 30
                ),
                VehicleClass(
                    id = "aircraft_carrier",
                    displayName = "Porta-Avioes",
                    maxLength = 300,
                    maxWidth = 60,
                    maxHeight = 50,
                    minBlocks = 60000,
                    maxBlocks = 200000,
                    scoreValue = 300,
                    requiredBlocks = listOf("create:large_propeller", "minecraft:dispenser", "create:display_link"),
                    forbiddenBlocks = listOf(),
                    color = 0xFFCC8800,
                    description = "Navio capital de projecao aerea, transporta aeronaves",
                    allowedInRegions = listOf("*"),
                    requiresCrew = 25
                ),
                VehicleClass(
                    id = "submarine",
                    displayName = "Submarino",
                    maxLength = 100,
                    maxWidth = 12,
                    maxHeight = 20,
                    minBlocks = 1000,
                    maxBlocks = 10000,
                    scoreValue = 150,
                    requiredBlocks = listOf("create:propeller"),
                    forbiddenBlocks = listOf("create:display_link", "minecraft:glass", "minecraft:glowstone"),
                    color = 0xFF4444AA,
                    description = "Embarcacao submersivel, furtiva",
                    requiresCrew = 4
                ),
                VehicleClass(
                    id = "transport",
                    displayName = "Navio de Transporte",
                    maxLength = 200,
                    maxWidth = 30,
                    maxHeight = 30,
                    minBlocks = 10000,
                    maxBlocks = 50000,
                    scoreValue = 20,
                    requiredBlocks = listOf("create:propeller"),
                    forbiddenBlocks = listOf("minecraft:dispenser", "minecraft:observer"),
                    color = 0xFF88AA44,
                    description = "Navio logistico, sem armamento pesado",
                    requiresCrew = 5
                ),
                VehicleClass(
                    id = "landing_ship",
                    displayName = "Navio de Desembarque",
                    maxLength = 150,
                    maxWidth = 25,
                    maxHeight = 25,
                    minBlocks = 5000,
                    maxBlocks = 25000,
                    scoreValue = 40,
                    requiredBlocks = listOf("create:propeller"),
                    forbiddenBlocks = listOf(),
                    color = 0xFF88AA66,
                    description = "Navio anfibio para transporte de tropas/veiculos terrestres",
                    requiresCrew = 6
                ),
                VehicleClass(
                    id = "minelayer",
                    displayName = "Minador",
                    maxLength = 100,
                    maxWidth = 18,
                    maxHeight = 20,
                    minBlocks = 3000,
                    maxBlocks = 15000,
                    scoreValue = 60,
                    requiredBlocks = listOf("create:propeller", "minecraft:dispenser"),
                    forbiddenBlocks = listOf(),
                    color = 0xFF666666,
                    description = "Navio especializado em guerra de minas",
                    requiresCrew = 4
                )
            )
        }
    }

    fun matches(blockCount: Int, length: Int, width: Int, height: Int, blockTypes: Set<String>): Boolean {
        return blockCount >= minBlocks && blockCount <= maxBlocks &&
               length <= maxLength && width <= maxWidth && height <= maxHeight &&
               requiredBlocks.all { blockTypes.contains(it) } &&
               forbiddenBlocks.none { blockTypes.contains(it) }
    }

    fun isAllowedInRegion(regionId: String): Boolean {
        return allowedInRegions.contains("*") || allowedInRegions.contains(regionId)
    }
}