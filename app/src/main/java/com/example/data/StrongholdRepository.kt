package com.example.data

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class StrongholdGarrison(
    val playerUid: String,
    val playerName: String,
    val troopPower: Long,
    val isAlliedRelief: Boolean
)

data class Stronghold(
    val id: String,
    val realmId: String,
    val allianceId: String,
    val allianceTag: String,
    val allianceName: String,
    val coordX: Long,
    val coordY: Long,
    val level: Int,
    val durability: Long,
    val maxDurability: Long,
    val underSiege: Boolean,
    val callForAid: Boolean,
    val aidMessage: String,
    val garrisons: List<StrongholdGarrison>
) {
    val displayName: String get() = "[$allianceTag] Stronghold"
    val garrisonPower: Long get() = garrisons.sumOf { it.troopPower }

    /** Same formula as the server (walls are 800 per level). */
    fun defensePower(defenseTechLevel: Long): Long =
        (garrisonPower * (1.0 + defenseTechLevel * 0.06)).toLong() + level * 800L

    fun garrisonOf(uid: String): StrongholdGarrison? = garrisons.firstOrNull { it.playerUid == uid }
}

data class AlliancePact(
    val id: String,
    val otherAllianceId: String,
    val otherTag: String,
    val otherName: String,
    val status: String,          // PENDING or ACTIVE
    val proposedByUs: Boolean
)

@Serializable
private data class AllianceBrief(val tag: String = "", val name: String = "")

@Serializable
private data class GarrisonRow(
    @SerialName("player_id") val playerId: String = "",
    @SerialName("troop_power") val troopPower: Long = 0,
    @SerialName("is_allied_relief") val isAlliedRelief: Boolean = false,
    val players: NameOnlyRow? = null
)

@Serializable
private data class NameOnlyRow(val username: String = "")

@Serializable
private data class StrongholdRow(
    val id: String = "",
    @SerialName("alliance_id") val allianceId: String = "",
    @SerialName("home_realm_id") val realmId: String = "",
    @SerialName("pos_x") val posX: Int = 0,
    @SerialName("pos_y") val posY: Int = 0,
    val level: Int = 1,
    val durability: Long = 0,
    @SerialName("max_durability") val maxDurability: Long = 1,
    @SerialName("is_under_siege") val underSiege: Boolean = false,
    @SerialName("call_for_aid_active") val callForAid: Boolean = false,
    @SerialName("aid_message") val aidMessage: String? = null,
    val alliances: AllianceBrief? = null,
    @SerialName("stronghold_garrisons") val garrisons: List<GarrisonRow> = emptyList()
)

@Serializable
private data class PactRow(
    val id: String = "",
    @SerialName("alliance_a_id") val a: String = "",
    @SerialName("alliance_b_id") val b: String = "",
    @SerialName("requested_by") val requestedBy: String = "",
    val status: String = "PENDING",
    @SerialName("a_info") val aInfo: AllianceBrief? = null,
    @SerialName("b_info") val bInfo: AllianceBrief? = null
)

/** Alliance strongholds, their garrisons, calls for aid and pacts between alliances. */
class StrongholdRepository {
    private val client get() = SupabaseProvider.client

    fun observeStrongholds(): Flow<List<Stronghold>> = pollingFlow(TAG, 10_000L) {
        client.from("strongholds").select(
            Columns.raw(
                "*, alliances(tag, name), " +
                    "stronghold_garrisons(player_id, troop_power, is_allied_relief, players(username))"
            )
        ).decodeList<StrongholdRow>().map { s ->
            Stronghold(
                id = s.id,
                realmId = s.realmId,
                allianceId = s.allianceId,
                allianceTag = s.alliances?.tag.orEmpty(),
                allianceName = s.alliances?.name.orEmpty(),
                coordX = (s.posX / SERVER_MAP_SCALE).toLong(),
                coordY = (s.posY / SERVER_MAP_SCALE).toLong(),
                level = s.level,
                durability = s.durability,
                maxDurability = s.maxDurability.coerceAtLeast(1),
                underSiege = s.underSiege,
                callForAid = s.callForAid,
                aidMessage = s.aidMessage.orEmpty(),
                garrisons = s.garrisons.map {
                    StrongholdGarrison(it.playerId, it.players?.username.orEmpty(), it.troopPower, it.isAlliedRelief)
                }.sortedByDescending { it.troopPower }
            )
        }
    }

    fun observePacts(allianceId: String): Flow<List<AlliancePact>> = pollingFlow(TAG, 15_000L) {
        client.from("alliance_pacts").select(
            Columns.raw(
                "*, a_info:alliances!alliance_pacts_alliance_a_id_fkey(tag, name), " +
                    "b_info:alliances!alliance_pacts_alliance_b_id_fkey(tag, name)"
            )
        ) {
            filter {
                or {
                    eq("alliance_a_id", allianceId)
                    eq("alliance_b_id", allianceId)
                }
                neq("status", "ENDED")
            }
        }.decodeList<PactRow>().map { p ->
            val weAreA = p.a == allianceId
            val other = if (weAreA) p.bInfo else p.aInfo
            AlliancePact(
                id = p.id,
                otherAllianceId = if (weAreA) p.b else p.a,
                otherTag = other?.tag.orEmpty(),
                otherName = other?.name.orEmpty(),
                status = p.status,
                proposedByUs = p.requestedBy == allianceId
            )
        }
    }

    private suspend fun call(name: String, params: JsonObject = JsonObject(emptyMap())): Result<Unit> =
        supabaseResult {
            client.postgrest.rpc(name, params)
            GameSignals.poke()
        }

    suspend fun foundStronghold(x: Long, y: Long, realmId: String?) =
        call("found_stronghold", buildJsonObject {
            put("p_x", x)
            put("p_y", y)
            if (!realmId.isNullOrBlank()) put("p_realm", realmId)
        })

    suspend fun withdrawGarrison(stronghold: Stronghold) =
        call("withdraw_garrison", buildJsonObject { put("p_stronghold", stronghold.id) })

    suspend fun callForAid(message: String) =
        call("call_for_aid", buildJsonObject { put("p_message", message) })

    suspend fun endCallForAid() = call("end_call_for_aid")

    suspend fun proposePact(allianceId: String) =
        call("propose_pact", buildJsonObject { put("p_alliance", allianceId) })

    suspend fun respondPact(pact: AlliancePact, accept: Boolean) =
        call("respond_pact", buildJsonObject { put("p_pact", pact.id); put("p_accept", accept) })

    suspend fun endPact(pact: AlliancePact) =
        call("end_pact", buildJsonObject { put("p_pact", pact.id) })

    private companion object {
        const val TAG = "StrongholdRepository"
    }
}
