package com.tlgd.assault.region

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.Level
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.*
import kotlin.collections.mutableListOf

data class AttackWindow(
    val dayOfWeek: DayOfWeek,
    val startTime: LocalTime,
    val durationMinutes: Int = 30
) {
    companion object {
        val CODEC: Codec<AttackWindow> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("day_of_week").forGetter { it.dayOfWeek.ordinal },
                Codec.STRING.fieldOf("start_time").forGetter { it.startTime.toString },
                Codec.INT.fieldOf("duration_minutes").forGetter { it.durationMinutes }
            ).apply(instance, AttackWindow::new)
        }

        fun fromOrdinal(ordinal: Int): DayOfWeek = DayOfWeek.values()[ordinal]
    }

    fun getEndTime(): LocalTime = startTime.plusMinutes(durationMinutes.toLong())

    fun isActiveNow(): Boolean {
        val now = LocalTime.now()
        val today = DayOfWeek.from(java.time.LocalDate.now())
        return today == dayOfWeek && now >= startTime && now < getEndTime()
    }

    fun getTimeUntilStart(): Long {
        val now = java.time.LocalDateTime.now()
        val targetDate = now.toLocalDate().`with`(java.time.temporal.TemporalAdjusters.nextOrSame(dayOfWeek))
        val targetDateTime = java.time.LocalDateTime.of(targetDate, startTime)
        return java.time.Duration.between(now, targetDateTime).toMinutes()
    }

    override fun toString(): String {
        return "${dayOfWeek.name} ${startTime} (${durationMinutes}min)"
    }
}

data class RegionBounds(
    val min: BlockPos,
    val max: BlockPos,
    val dimension: ResourceLocation
) {
    companion object {
        val CODEC: Codec<RegionBounds> = RecordCodecBuilder.create { instance ->
            instance.group(
                BlockPos.CODEC.fieldOf("min").forGetter { it.min },
                BlockPos.CODEC.fieldOf("max").forGetter { it.max },
                ResourceLocation.CODEC.fieldOf("dimension").forGetter { it.dimension }
            ).apply(instance, RegionBounds::new)
        }
    }

    fun contains(pos: BlockPos, dim: ResourceLocation): Boolean {
        return dimension == dim &&
               pos.x >= min.x && pos.x <= max.x &&
               pos.y >= min.y && pos.y <= max.y &&
               pos.z >= min.z && pos.z <= max.z
    }

    fun getCenter(): BlockPos = BlockPos(
        (min.x + max.x) / 2,
        (min.y + max.y) / 2,
        (min.z + max.z) / 2
    )

    fun getArea(): Long = (max.x - min.x + 1L) * (max.z - min.z + 1L)

    fun getVolume(): Long = getArea() * (max.y - min.y + 1L)

    fun getChunks(): List<ChunkPos> {
        val chunks = mutableListOf<ChunkPos>()
        val minChunkX = min.x >> 4
        val maxChunkX = max.x >> 4
        val minChunkZ = min.z >> 4
        val maxChunkZ = max.z >> 4
        for (x in minChunkX..maxChunkX) {
            for (z in minChunkZ..maxChunkZ) {
                chunks.add(ChunkPos(x, z))
            }
        }
        return chunks
    }
}

enum class AssaultState {
    INACTIVE,       // Fora de janela, ninguém dono
    PEACEFUL,       // Fora de janela, tem dono
    PREPARING,      // Janela aberta, aguardando /assault begin
    ACTIVE,         // Assalto em andamento
    COOLDOWN        // Acabou, cooldown antes da próxima janela
}

data class NationScore(
    val nationId: String,
    val nationName: String,
    var score: Int = 0,
    var kills: Int = 0,
    var vehiclesDestroyed: Int = 0,
    var flagsCaptured: Int = 0,
    var presenceTimeSeconds: Long = 0,
    var lastPresenceUpdate: Long = System.currentTimeMillis()
) {
    fun addPresenceTime(seconds: Long) {
        presenceTimeSeconds += seconds
        score += (seconds * TLGDAssaultConfig.pointsPerSecondPresence).toInt()
    }

    fun addKill() {
        kills++
        score += TLGDAssaultConfig.pointsPerKill
    }

    fun addVehicleDestroyed() {
        vehiclesDestroyed++
        score += TLGDAssaultConfig.pointsPerVehicleDestroyed
    }

    fun addFlagCapture() {
        flagsCaptured++
        score += TLGDAssaultConfig.pointsPerFlagCapture
    }

    companion object {
        val CODEC: Codec<NationScore> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("nation_id").forGetter { it.nationId },
                Codec.STRING.fieldOf("nation_name").forGetter { it.nationName },
                Codec.INT.fieldOf("score").forGetter { it.score },
                Codec.INT.fieldOf("kills").forGetter { it.kills },
                Codec.INT.fieldOf("vehicles_destroyed").forGetter { it.vehiclesDestroyed },
                Codec.INT.fieldOf("flags_captured").forGetter { it.flagsCaptured },
                Codec.LONG.fieldOf("presence_time").forGetter { it.presenceTimeSeconds },
                Codec.LONG.fieldOf("last_update").forGetter { it.lastPresenceUpdate }
            ).apply(instance, NationScore::new)
        }
    }
}

class AssaultRegion(
    val id: String,
    val displayName: String,
    val bounds: RegionBounds,
    val attackWindows: List<AttackWindow>,
    val ownerNationId: String? = null,
    val ownerNationName: String? = null,
    val state: AssaultState = AssaultState.INACTIVE,
    val activeAssaultId: UUID? = null,
    val scores: MutableMap<String, NationScore> = mutableMapOf(),
    val createdAt: Long = System.currentTimeMillis(),
    var lastCaptureTime: Long = 0,
    var nextWindowIndex: Int = 0
) {
    companion object {
        val CODEC: Codec<AssaultRegion> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.STRING.fieldOf("id").forGetter { it.id },
                Codec.STRING.fieldOf("display_name").forGetter { it.displayName },
                RegionBounds.CODEC.fieldOf("bounds").forGetter { it.bounds },
                AttackWindow.CODEC.listOf().fieldOf("attack_windows").forGetter { it.attackWindows },
                Codec.STRING.optionalFieldOf("owner_nation_id").forGetter { it.ownerNationId },
                Codec.STRING.optionalFieldOf("owner_nation_name").forGetter { it.ownerNationName },
                Codec.STRING.fieldOf("state").xmap(AssaultState::valueOf, AssaultState::name).forGetter { it.state },
                Codec.STRING.optionalFieldOf("active_assault_id").xmap({ UUID.fromString(it) }, { it.toString() }).forGetter { it.activeAssaultId },
                NationScore.CODEC.listOf().fieldOf("scores").xmap({ it.associateBy { it.nationId } }, { it.values.toList() }).forGetter { it.scores },
                Codec.LONG.fieldOf("created_at").forGetter { it.createdAt },
                Codec.LONG.fieldOf("last_capture_time").forGetter { it.lastCaptureTime },
                Codec.INT.fieldOf("next_window_index").forGetter { it.nextWindowIndex }
            ).apply(instance, AssaultRegion::new)
        }
    }

    fun getCurrentWindow(): AttackWindow? {
        if (attackWindows.isEmpty()) return null
        return attackWindows[nextWindowIndex % attackWindows.size]
    }

    fun getNextWindow(): AttackWindow? {
        if (attackWindows.isEmpty()) return null
        return attackWindows[(nextWindowIndex + 1) % attackWindows.size]
    }

    fun advanceWindow() {
        nextWindowIndex = (nextWindowIndex + 1) % attackWindows.size
    }

    fun getTimeUntilNextWindow(): Long {
        getCurrentWindow()?.getTimeUntilStart() ?: -1
    }

    fun isInWindow(): Boolean {
        getCurrentWindow()?.isActiveNow() == true
    }

    fun getStateForDisplay(): String {
        return when (state) {
            AssaultState.INACTIVE -> "§7Livre"
            AssaultState.PEACEFUL -> "§aPacífica (${ownerNationName})"
            AssaultState.PREPARING -> "§ePreparando..."
            AssaultState.ACTIVE -> "§cEM COMBATE"
            AssaultState.COOLDOWN -> "§7Cooldown"
        }
    }

    fun getLeaderboard(): List<NationScore> {
        return scores.values.sortedByDescending { it.score }
    }

    fun getWinner(): NationScore? {
        return getLeaderboard().firstOrNull()
    }

    fun toTag(lookup: HolderLookup.Provider): CompoundTag {
        return CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow()
    }

    companion object {
        fun fromTag(tag: CompoundTag, lookup: HolderLookup.Provider): AssaultRegion {
            return CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().first
        }
    }
}