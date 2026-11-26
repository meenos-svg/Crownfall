package com.example.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The Red Dragon world boss. One lives in each realm at a time; when it dies or flees, another lands two hours later. */
data class Dragon(
    val id: String,
    val realmId: String,
    val name: String,
    val level: Int,
    val maxHp: Long,
    val hp: Long,
    val coordX: Long,
    val coordY: Long,
    val status: String,          // ALIVE, SLAIN, FLED
    val fleesAtMs: Long,
    val respawnAtMs: Long,
    val slainByName: String
) {
    val isAlive: Boolean get() = status == "ALIVE"
    val hpFraction: Float get() = if (maxHp <= 0) 0f else (hp.toFloat() / maxHp).coerceIn(0f, 1f)

    /** What the hoard holds, matching the server's payout. */
    val hoardFood: Long get() = 40_000L * level
    val hoardGold: Long get() = 25_000L * level
    val hoardTechXp: Long get() = 3_000L * level
    val killingBlowGold: Long get() = 5_000L * level

    /** Share of an army the dragon's fire burns away on one hit (rallies lose a quarter less). */
    val burnPercent: Int get() = ((0.08 + 0.03 * level).coerceAtMost(0.38) * 100).toInt()
}

@Serializable
data class DragonLord(
    val id: String = "",
    val name: String = "",
    val avatar: String = "crest:1",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val tag: String? = null,
    val damage: Long = 0,
    val hits: Int = 0
)

@Serializable
data class DragonAlliance(val tag: String = "", val name: String = "", val damage: Long = 0)

@Serializable
data class DragonMine(val damage: Long = 0, val hits: Int = 0, val rank: Int = 0)

@Serializable
data class DragonBoard(
    val total: Long = 0,
    val lords: List<DragonLord> = emptyList(),
    val alliances: List<DragonAlliance> = emptyList(),
    val mine: DragonMine? = null
)

@Serializable
private data class DragonRow(
    val id: String = "",
    @SerialName("realm_id") val realmId: String = "",
    val name: String = "",
    val level: Int = 1,
    @SerialName("max_hp") val maxHp: Long = 1,
    val hp: Long = 0,
    @SerialName("map_x") val mapX: Int = 0,
    @SerialName("map_y") val mapY: Int = 0,
    val status: String = "ALIVE",
    @SerialName("flees_at") val fleesAt: String? = null,
    @SerialName("respawn_at") val respawnAt: String? = null,
    @SerialName("slain_by_name") val slainByName: String? = null
)

class DragonRepository {
    private val client get() = SupabaseProvider.client

    /** The newest dragon of each realm, alive or not (a dead one tells you when the next arrives). */
    fun observeDragons(): Flow<List<Dragon>> = pollingFlow(TAG, 10_000L) {
        client.from("dragons").select {
            order("spawned_at", Order.DESCENDING)
            limit(24)
        }.decodeList<DragonRow>()
            .distinctBy { it.realmId }
            .map { r ->
                Dragon(
                    id = r.id,
                    realmId = r.realmId,
                    name = r.name,
                    level = r.level,
                    maxHp = r.maxHp,
                    hp = r.hp,
                    coordX = r.mapX.toLong(),
                    coordY = r.mapY.toLong(),
                    status = r.status,
                    fleesAtMs = r.fleesAt.toEpochMs(),
                    respawnAtMs = r.respawnAt.toEpochMs(),
                    slainByName = r.slainByName.orEmpty()
                )
            }
    }

    suspend fun board(dragonId: String): Result<DragonBoard> = supabaseResult {
        client.postgrest.rpc("dragon_board", buildJsonObject { put("p_dragon", dragonId) })
            .decodeAs<DragonBoard>()
    }

    private companion object {
        const val TAG = "DragonRepository"
    }
}
