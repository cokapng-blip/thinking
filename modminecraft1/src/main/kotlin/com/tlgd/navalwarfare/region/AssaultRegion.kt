package com.tlgd.navalwarfare.region

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import java.io.File
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID

/**
 * Representa uma região de assalto naval.
 */
class AssaultRegion(
    val id: String,
    var displayName: String,
    var bounds: RegionBounds,
    var attackWindows: MutableList<AttackWindow> = mutableListOf(),
    var ownerNationId: String? = null,
    var ownerNationName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    var lastCaptureTime: Long = 0,
) {

    var state: AssaultState = AssaultState.INACTIVE
    var nextWindowIndex: Int = 0

    companion object {
        private val gson = GsonBuilder().setPrettyPrinting().create()

        data class AttackWindow(
            val dayOfWeek: DayOfWeek,
            val startTime: LocalTime,
            val durationMinutes: Int,
        ) {
            fun toString(): String {
                return "${dayOfWeek.name} ${startTime} (${durationMinutes}min)"
            }

            fun isNow(): Boolean {
                val now = java.time.LocalDateTime.now()
                return now.dayOfWeek == dayOfWeek &&
                       now.toLocalTime() >= startTime &&
                       now.toLocalTime() < startTime.plusMinutes(durationMinutes.toLong())
            }

            fun getNextOccurrence(): Long {
                val now = java.time.LocalDateTime.now()
                var next = now.`with`(dayOfWeek).with(startTime)
                if (next.isBefore(now) || next.toLocalTime() >= startTime.plusMinutes(durationMinutes.toLong())) {
                    next = next.plusWeeks(1)
                }
                return next.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            }

            fun getEndTime(): Long {
                val start = getNextOccurrence()
                return start + (durationMinutes * 60 * 1000L)
            }
        }

        fun fromJson(json: String): AssaultRegion {
            return gson.fromJson(json, AssaultRegion::class.java)
        }
    }

    fun toJson(): String {
        return gson.toJson(this)
    }

    fun getNextWindow(): AttackWindow? {
        return attackWindows.getOrNull(nextWindowIndex)
    }

    fun advanceWindow() {
        if (attackWindows.isNotEmpty()) {
            nextWindowIndex = (nextWindowIndex + 1) % attackWindows.size
        }
    }

    fun isInWindow(): Boolean {
        return attackWindows.any { it.isNow() }
    }

    fun getTimeUntilNextWindow(): Long {
        val next = getNextWindow() ?: return Long.MAX_VALUE
        return max(0, next.getNextOccurrence() - System.currentTimeMillis())
    }

    fun contains(pos: BlockPos): Boolean {
        return bounds.contains(pos)
    }

    fun getCenter(): BlockPos {
        return BlockPos(
            (bounds.min.x + bounds.max.x) / 2,
            (bounds.min.y + bounds.max.y) / 2,
            (bounds.min.z + bounds.max.z) / 2
        )
    }
}

data class RegionBounds(
    val min: BlockPos,
    val max: BlockPos,
    val dimension: ResourceLocation,
) {
    fun contains(pos: BlockPos): Boolean {
        return pos.x >= min.x && pos.x <= max.x &&
               pos.y >= min.y && pos.y <= max.y &&
               pos.z >= min.z && pos.z <= max.z
    }

    fun getArea(): Long {
        return (max.x - min.x + 1L) * (max.z - min.z + 1L)
    }

    fun getVolume(): Long {
        return getArea() * (max.y - min.y + 1L)
    }
}

enum class AssaultState {
    INACTIVE,    // Região sem janela ativa, livre
    PEACEFUL,    // Região capturada, fora de janela
    PREPARING,   // 5 min antes da janela - anúncio
    ACTIVE,      // Janela de assalto aberta
    COOLDOWN,    // Após assalto - cooldown
}