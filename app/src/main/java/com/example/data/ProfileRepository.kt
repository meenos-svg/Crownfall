package com.example.data

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A lord's public profile card: who they are and their war record. */
@Serializable
data class LordProfile(
    val id: String = "",
    val username: String = "",
    val avatar: String = "crest:1",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val motto: String = "",
    @SerialName("keep_level") val keepLevel: Int = 1,
    val power: Long = 0,
    val honor: Long = 0,
    @SerialName("is_bot") val isBot: Boolean = false,
    @SerialName("realm_id") val realmId: String = "",
    @SerialName("realm_name") val realmName: String? = null,
    val x: Int = 0,
    val y: Int = 0,
    @SerialName("alliance_id") val allianceId: String? = null,
    @SerialName("alliance_tag") val allianceTag: String? = null,
    @SerialName("alliance_name") val allianceName: String? = null,
    @SerialName("alliance_role") val allianceRole: String = "NONE",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("attacks_won") val attacksWon: Long = 0,
    @SerialName("attacks_lost") val attacksLost: Long = 0,
    @SerialName("defenses_won") val defensesWon: Long = 0,
    @SerialName("defenses_lost") val defensesLost: Long = 0,
    @SerialName("troops_slain") val troopsSlain: Long = 0,
    @SerialName("troops_lost") val troopsLost: Long = 0,
    val gathers: Long = 0,
    @SerialName("rallies_joined") val ralliesJoined: Long = 0,
    @SerialName("landmarks_garrisoned") val landmarksGarrisoned: Long = 0
) {
    val battles: Long get() = attacksWon + attacksLost + defensesWon + defensesLost
    val winRatePercent: Int get() =
        if (battles == 0L) 0 else (((attacksWon + defensesWon) * 100) / battles).toInt()

    /** Titles are earned with Honor. */
    val title: String get() = lordTitle(honor)
}

fun lordTitle(honor: Long): String = when {
    honor >= 100_000 -> "High King"
    honor >= 40_000 -> "Warlord"
    honor >= 15_000 -> "Duke"
    honor >= 6_000 -> "Count"
    honor >= 2_000 -> "Baron"
    honor >= 500 -> "Knight"
    else -> "Squire"
}

/** Honor needed for the next title, or null at the top. */
fun nextTitleAt(honor: Long): Long? = listOf(500L, 2_000L, 6_000L, 15_000L, 40_000L, 100_000L).firstOrNull { it > honor }

class ProfileRepository {
    private val client get() = SupabaseProvider.client

    suspend fun fetch(playerId: String): Result<LordProfile> = supabaseResult {
        client.postgrest.rpc("lord_profile", buildJsonObject { put("p_player", playerId) })
            .decodeAs<LordProfile>()
    }
}
