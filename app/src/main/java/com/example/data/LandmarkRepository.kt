package com.example.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class LandmarkGarrison(val playerUid: String, val playerName: String, val troopPower: Long)

/** A control point: mines, quarries, lumberyards, granaries and shrines that alliances fight over. */
data class Landmark(
    val id: String,
    val realmId: String,
    val name: String,
    val kind: String,              // MINE, QUARRY, LUMBER, FARM, SHRINE
    val resource: ResourceType?,   // null for the Shrine (it pays alliance tech XP)
    val coordX: Long,
    val coordY: Long,
    val yieldPerHour: Long,
    val guardianPower: Long,
    val holderAllianceId: String,
    val holderTag: String,
    val garrisons: List<LandmarkGarrison>
) {
    val isHeld: Boolean get() = holderAllianceId.isNotBlank()
    val garrisonPower: Long get() = garrisons.sumOf { it.troopPower }

    /** Same as the server: guardians when unclaimed, otherwise garrison + 400 palisade (before defense tech). */
    val defenseEstimate: Long get() = if (isHeld) garrisonPower + 400L else guardianPower

    fun garrisonOf(uid: String) = garrisons.firstOrNull { it.playerUid == uid }
}

@Serializable
private data class HolderRow(val tag: String = "")

@Serializable
private data class LmGarrisonRow(
    @SerialName("player_id") val playerId: String = "",
    @SerialName("troop_power") val troopPower: Long = 0,
    val players: LmName? = null
)

@Serializable
private data class LmName(val username: String = "")

@Serializable
private data class LandmarkRow(
    val id: String = "",
    @SerialName("realm_id") val realmId: String = "",
    val name: String = "",
    val kind: String = "MINE",
    val resource: String? = null,
    @SerialName("map_x") val mapX: Int = 0,
    @SerialName("map_y") val mapY: Int = 0,
    @SerialName("yield_per_hour") val yieldPerHour: Long = 0,
    @SerialName("guardian_power") val guardianPower: Long = 0,
    @SerialName("holder_alliance_id") val holderAllianceId: String? = null,
    val alliances: HolderRow? = null,
    @SerialName("landmark_garrisons") val garrisons: List<LmGarrisonRow> = emptyList()
)

class LandmarkRepository {
    private val client get() = SupabaseProvider.client

    fun observeLandmarks(): Flow<List<Landmark>> = pollingFlow(TAG, 10_000L) {
        client.from("landmarks").select(
            Columns.raw("*, alliances(tag), landmark_garrisons(player_id, troop_power, players(username))")
        ).decodeList<LandmarkRow>().map { r ->
            Landmark(
                id = r.id,
                realmId = r.realmId,
                name = r.name,
                kind = r.kind,
                resource = r.resource?.let { ResourceType.entries.firstOrNull { t -> t.name == it } },
                coordX = r.mapX.toLong(),
                coordY = r.mapY.toLong(),
                yieldPerHour = r.yieldPerHour,
                guardianPower = r.guardianPower,
                holderAllianceId = r.holderAllianceId.orEmpty(),
                holderTag = r.alliances?.tag.orEmpty(),
                garrisons = r.garrisons.map {
                    LandmarkGarrison(it.playerId, it.players?.username.orEmpty(), it.troopPower)
                }.sortedByDescending { it.troopPower }
            )
        }
    }

    suspend fun withdraw(landmark: Landmark): Result<Unit> = supabaseResult {
        client.postgrest.rpc("withdraw_landmark_garrison", buildJsonObject { put("p_landmark", landmark.id) })
        GameSignals.poke()
    }

    private companion object {
        const val TAG = "LandmarkRepository"
    }
}
