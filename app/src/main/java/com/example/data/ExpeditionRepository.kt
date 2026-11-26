package com.example.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

data class Realm(val id: String, val name: String, val theme: String)

data class VisitorCamp(
    val id: String,
    val ownerUid: String,
    val ownerName: String,
    val ownerAllianceId: String,
    val realmId: String,
    val coordX: Long,
    val coordY: Long,
    val infantry: Long,
    val cavalry: Long,
    val archers: Long,
    val siege: Long,
    val troopPower: Long,
    val status: String,          // TRAVELING, CAMPED or RETURNING
    val arrivesAtEpochMs: Long
)

@Serializable
private data class RealmRow(
    val id: String = "",
    val name: String = "",
    @SerialName("biome_theme") val theme: String = ""
)

@Serializable
private data class CampOwner(
    val username: String = "",
    @SerialName("alliance_id") val allianceId: String? = null
)

@Serializable
private data class CampRow(
    val id: String = "",
    @SerialName("player_id") val playerId: String = "",
    @SerialName("realm_id") val realmId: String = "",
    @SerialName("pos_x") val posX: Int = 0,
    @SerialName("pos_y") val posY: Int = 0,
    @SerialName("troops_json") val troops: JsonObject? = null,
    @SerialName("troop_power") val troopPower: Long = 0,
    val status: String = "TRAVELING",
    @SerialName("arrives_at") val arrivesAt: String? = null,
    val players: CampOwner? = null
)

/** Other realms, Waygate expeditions and the visitor camps they set up. */
class ExpeditionRepository {
    private val client get() = SupabaseProvider.client

    fun observeRealms(): Flow<List<Realm>> = pollingFlow(TAG, 300_000L) {
        client.from("realms").select(Columns.list("id", "name", "biome_theme"))
            .decodeList<RealmRow>()
            .map { Realm(it.id, it.name, it.theme) }
            .sortedBy { it.id }
    }

    fun observeCamps(): Flow<List<VisitorCamp>> = pollingFlow(TAG, 8_000L) {
        client.from("camps").select(Columns.raw("*, players(username, alliance_id)"))
            .decodeList<CampRow>()
            .map { c ->
                fun n(k: String) = c.troops?.get(k)?.jsonPrimitive?.longOrNull ?: 0L
                VisitorCamp(
                    id = c.id,
                    ownerUid = c.playerId,
                    ownerName = c.players?.username.orEmpty(),
                    ownerAllianceId = c.players?.allianceId.orEmpty(),
                    realmId = c.realmId,
                    coordX = (c.posX / SERVER_MAP_SCALE).toLong(),
                    coordY = (c.posY / SERVER_MAP_SCALE).toLong(),
                    infantry = n("infantry"),
                    cavalry = n("cavalry"),
                    archers = n("archers"),
                    siege = n("siege"),
                    troopPower = c.troopPower,
                    status = c.status,
                    arrivesAtEpochMs = c.arrivesAt.toEpochMs()
                )
            }
    }

    suspend fun sendExpedition(
        realmId: String, x: Long, y: Long,
        infantry: Long, cavalry: Long, archers: Long, siege: Long
    ): Result<Unit> = supabaseResult {
        client.postgrest.rpc("send_expedition", buildJsonObject {
            put("p_realm", realmId)
            put("p_x", x)
            put("p_y", y)
            put("p_infantry", infantry)
            put("p_cavalry", cavalry)
            put("p_archers", archers)
            put("p_siege", siege)
        })
        GameSignals.poke()
    }

    suspend fun recallCamp(): Result<Unit> = supabaseResult {
        client.postgrest.rpc("recall_camp")
        GameSignals.poke()
    }

    private companion object {
        const val TAG = "ExpeditionRepository"
    }
}
