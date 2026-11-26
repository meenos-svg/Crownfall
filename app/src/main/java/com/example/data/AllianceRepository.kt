package com.example.data

import com.google.firebase.Timestamp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Date

@Serializable
private data class AllianceRow(
    val id: String = "",
    @SerialName("realm_id") val realmId: String = "",
    val name: String = "",
    val tag: String = "",
    val motto: String = "",
    @SerialName("crest_color") val crestColor: String = "CRIMSON",
    @SerialName("leader_id") val leaderId: String = "",
    @SerialName("open_join") val openJoin: Boolean = true,
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("total_power") val totalPower: Long = 0,
    @SerialName("tech_march_level") val techMarch: Int = 0,
    @SerialName("tech_yield_level") val techYield: Int = 0,
    @SerialName("tech_attack_level") val techAttack: Int = 0,
    @SerialName("tech_defense_level") val techDefense: Int = 0,
    @SerialName("tech_xp") val techXp: Long = 0
)

@Serializable
private data class MemberRow(
    val id: String = "",
    @SerialName("alliance_id") val allianceId: String? = null,
    @SerialName("alliance_role") val role: String = "NONE"
)

@Serializable
private data class MessageRow(
    val id: String = "",
    @SerialName("alliance_id") val allianceId: String = "",
    @SerialName("sender_id") val senderId: String? = null,
    @SerialName("sender_name") val senderName: String = "",
    @SerialName("sender_role") val senderRole: String = "MEMBER",
    val body: String = "",
    @SerialName("message_type") val messageType: String = "CHAT",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
private data class NameOnly(val username: String = "")

@Serializable
private data class AidRow(
    val id: String = "",
    @SerialName("alliance_id") val allianceId: String = "",
    @SerialName("requester_id") val requesterId: String = "",
    @SerialName("resource_type") val resourceType: String = "FOOD",
    @SerialName("requested_amount") val requested: Long = 0,
    @SerialName("fulfilled_amount") val fulfilled: Long = 0,
    val status: String = "OPEN",
    @SerialName("donor_ids") val donorIds: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
    val players: NameOnly? = null
)

@Serializable
private data class AllianceNameTag(val name: String = "", val tag: String = "")

@Serializable
private data class InviteRow(
    val id: String = "",
    @SerialName("alliance_id") val allianceId: String = "",
    @SerialName("inviter_id") val inviterId: String = "",
    @SerialName("target_id") val targetId: String = "",
    val status: String = "PENDING",
    @SerialName("created_at") val createdAt: String? = null,
    val alliances: AllianceNameTag? = null,
    val inviter: NameOnly? = null
)

@Serializable
private data class ParticipantRow(
    @SerialName("player_id") val playerId: String = "",
    val power: Long = 0,
    val players: NameOnly? = null
)

@Serializable
private data class RallyRow(
    val id: String = "",
    @SerialName("alliance_id") val allianceId: String = "",
    @SerialName("leader_id") val leaderId: String = "",
    @SerialName("target_type") val targetType: String = "BASTION",
    @SerialName("target_id") val targetId: String = "",
    @SerialName("target_name") val targetName: String = "",
    @SerialName("target_x") val targetX: Int = 0,
    @SerialName("target_y") val targetY: Int = 0,
    @SerialName("total_power") val totalPower: Long = 0,
    @SerialName("target_power") val targetPower: Long = 0,
    @SerialName("launch_at") val launchAt: String? = null,
    @SerialName("arrives_at") val arrivesAt: String? = null,
    val status: String = "GATHERING",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val leader: NameOnly? = null,
    @SerialName("rally_participants") val participants: List<ParticipantRow> = emptyList()
)

internal fun String?.toTimestamp(): Timestamp? =
    this.toEpochMs().takeIf { it > 0 }?.let { Timestamp(Date(it)) }

/**
 * Alliances live in Supabase. Every change goes through a server function
 * (part 5 SQL) that checks rank and rules; the app only reads and polls.
 */
class AllianceRepository(
    @Suppress("unused") private val db: Any? = null,
    @Suppress("unused") private val auth: Any? = null
) {
    private val client get() = SupabaseProvider.client
    private val myUid: String get() = client.auth.currentUserOrNull()?.id.orEmpty()

    private suspend fun rpc(name: String, params: JsonObject = JsonObject(emptyMap())) =
        client.postgrest.rpc(name, params)

    private fun afterChange() = GameSignals.poke()

    // ------------------------------------------------------------------ reads

    fun observeAlliances(): Flow<List<Alliance>> = pollingFlow(TAG, 15_000L) {
        val rows = client.from("alliances").select {
            order("total_power", Order.DESCENDING)
            limit(100)
        }.decodeList<AllianceRow>()
        val members = client.from("players").select(Columns.list("id", "alliance_id", "alliance_role")) {
            filter { neq("alliance_role", "NONE") }
        }.decodeList<MemberRow>()
        val byAlliance = members.groupBy { it.allianceId.orEmpty() }
        rows.map { r ->
            val mine = byAlliance[r.id].orEmpty()
            Alliance(
                id = r.id,
                realmId = r.realmId,
                name = r.name,
                tag = r.tag,
                motto = r.motto,
                crestColor = r.crestColor,
                leaderUid = r.leaderId,
                officerUids = mine.filter { it.role == "OFFICER" }.map { it.id },
                memberUids = mine.map { it.id },
                memberCount = mine.size.toLong(),
                totalPower = r.totalPower,
                openJoin = r.openJoin,
                techMarchLevel = r.techMarch.toLong(),
                techYieldLevel = r.techYield.toLong(),
                techAttackLevel = r.techAttack.toLong(),
                techDefenseLevel = r.techDefense.toLong(),
                techXp = r.techXp
            )
        }
    }

    fun observeMessages(allianceId: String, userId: String): Flow<List<AllianceMessage>> = pollingFlow(TAG, 5_000L) {
        client.from("alliance_messages").select {
            filter { eq("alliance_id", allianceId) }
            order("created_at", Order.DESCENDING)
            limit(80)
        }.decodeList<MessageRow>().reversed().map { m ->
            AllianceMessage(
                id = m.id,
                allianceId = m.allianceId,
                senderUid = m.senderId.orEmpty(),
                senderName = m.senderName,
                senderRole = m.senderRole,
                body = m.body,
                messageType = m.messageType,
                createdAt = m.createdAt.toTimestamp()
            )
        }
    }

    fun observeRallies(allianceId: String, userId: String): Flow<List<AllianceRally>> = pollingFlow(TAG, 5_000L) {
        val cutoff = System.currentTimeMillis() - 15 * 60_000L
        client.from("rallies").select(
            Columns.raw("*, leader:players!rallies_leader_id_fkey(username), rally_participants(player_id, power, players(username))")
        ) {
            filter {
                eq("alliance_id", allianceId)
                neq("status", "CANCELLED")
            }
            order("created_at", Order.DESCENDING)
            limit(10)
        }.decodeList<RallyRow>()
            .filter { it.status == "GATHERING" || it.status == "MARCHING" || it.updatedAt.toEpochMs() > cutoff }
            .map { r ->
                val marching = r.status == "MARCHING"
                AllianceRally(
                    id = r.id,
                    allianceId = r.allianceId,
                    leaderUid = r.leaderId,
                    leaderName = r.leader?.username.orEmpty(),
                    targetType = r.targetType,
                    targetId = r.targetId,
                    targetName = r.targetName,
                    targetX = (r.targetX / SERVER_MAP_SCALE).toLong(),
                    targetY = (r.targetY / SERVER_MAP_SCALE).toLong(),
                    targetPower = r.targetPower,
                    totalPower = r.totalPower,
                    participantUids = r.participants.map { it.playerId },
                    participantSummary = r.participants.joinToString(", ") {
                        "${it.players?.username.orEmpty()} (${formatCompact(it.power)})"
                    },
                    launchAtEpochMs = if (marching) r.arrivesAt.toEpochMs() else r.launchAt.toEpochMs(),
                    status = when (r.status) {
                        "MARCHING" -> "LAUNCHED"
                        else -> r.status
                    },
                    memberUids = emptyList(),
                    createdAt = r.createdAt.toTimestamp()
                )
            }
    }

    fun observeAidRequests(allianceId: String, userId: String): Flow<List<ResourceAidRequest>> = pollingFlow(TAG, 8_000L) {
        client.from("aid_requests").select(Columns.raw("*, players(username)")) {
            filter {
                eq("alliance_id", allianceId)
                eq("status", "OPEN")
            }
            order("created_at", Order.DESCENDING)
        }.decodeList<AidRow>().map { a ->
            ResourceAidRequest(
                id = a.id,
                allianceId = a.allianceId,
                requesterUid = a.requesterId,
                requesterName = a.players?.username.orEmpty(),
                resourceType = a.resourceType,
                requestedAmount = a.requested,
                fulfilledAmount = a.fulfilled,
                status = a.status,
                donorUids = a.donorIds,
                createdAt = a.createdAt.toTimestamp()
            )
        }
    }

    fun observeMyInvites(userId: String): Flow<List<AllianceInvite>> = pollingFlow(TAG, 15_000L) {
        val me = myUid
        if (me.isBlank()) return@pollingFlow emptyList()
        client.from("alliance_invites").select(
            Columns.raw("*, alliances(name, tag), inviter:players!alliance_invites_inviter_id_fkey(username)")
        ) {
            filter {
                eq("target_id", me)
                eq("status", "PENDING")
            }
        }.decodeList<InviteRow>().map { i ->
            AllianceInvite(
                id = i.id,
                allianceId = i.allianceId,
                allianceName = i.alliances?.name.orEmpty(),
                allianceTag = i.alliances?.tag.orEmpty(),
                inviterUid = i.inviterId,
                inviterName = i.inviter?.username.orEmpty(),
                targetUid = i.targetId,
                status = i.status,
                createdAt = i.createdAt.toTimestamp()
            )
        }
    }

    // ------------------------------------------------------------------ membership

    suspend fun createAlliance(
        empire: PlayerEmpire,
        name: String,
        tag: String,
        motto: String,
        crestColor: String,
        openJoin: Boolean
    ): Result<Unit> = supabaseResult {
        rpc("create_alliance", buildJsonObject {
            put("p_name", name.trim())
            put("p_tag", tag.trim().uppercase())
            put("p_motto", motto.trim())
            put("p_crest", crestColor)
            put("p_open", openJoin)
        })
        afterChange()
    }

    suspend fun joinAlliance(
        empire: PlayerEmpire,
        alliance: Alliance,
        invite: AllianceInvite? = null
    ): Result<Unit> = supabaseResult {
        rpc("join_alliance", buildJsonObject {
            put("p_alliance", alliance.id.ifBlank { invite?.allianceId.orEmpty() })
            invite?.let { put("p_invite", it.id) }
        })
        afterChange()
    }

    suspend fun declineInvite(invite: AllianceInvite): Result<Unit> = supabaseResult {
        rpc("decline_invite", buildJsonObject { put("p_invite", invite.id) })
        afterChange()
    }

    suspend fun leaveOrDisbandAlliance(empire: PlayerEmpire, alliance: Alliance): Result<Unit> = supabaseResult {
        rpc("leave_alliance")
        afterChange()
    }

    // ------------------------------------------------------------------ chat

    suspend fun sendMessage(
        empire: PlayerEmpire,
        alliance: Alliance,
        body: String,
        messageType: String = "CHAT"
    ): Result<Unit> = supabaseResult {
        rpc("send_alliance_message", buildJsonObject {
            put("p_body", body.trim())
            put("p_type", messageType)
        })
        afterChange()
    }

    // ------------------------------------------------------------------ war rallies

    /** Sends every troop at home; the server checks the counts. */
    private fun allHomeTroops(empire: PlayerEmpire) = buildJsonObject {
        put("p_infantry", empire.infantry)
        put("p_cavalry", empire.cavalry)
        put("p_archers", empire.archers)
        put("p_siege", empire.siege)
    }

    suspend fun launchRally(
        empire: PlayerEmpire,
        alliance: Alliance,
        targetType: String,
        targetId: String,
        targetName: String,
        targetX: Long,
        targetY: Long,
        targetPower: Long,
        committedPower: Long
    ): Result<Unit> = supabaseResult {
        if (empire.garrisonPower() <= 0L) error("You have no troops at home to lead a rally.")
        val params = buildJsonObject {
            put("p_target_type", targetType)
            put("p_target_id", targetId)
            allHomeTroops(empire).forEach { (k, v) -> put(k, v) }
            put("p_wait_seconds", 60)
        }
        rpc("launch_rally", params)
        afterChange()
    }

    suspend fun joinRally(
        empire: PlayerEmpire,
        alliance: Alliance,
        rally: AllianceRally,
        committedPower: Long
    ): Result<Unit> = supabaseResult {
        if (empire.garrisonPower() <= 0L) error("You have no troops at home to send.")
        val params = buildJsonObject {
            put("p_rally", rally.id)
            allHomeTroops(empire).forEach { (k, v) -> put(k, v) }
        }
        rpc("join_rally", params)
        afterChange()
    }

    /** "Launch Strike": the leader sends a gathering rally right away. */
    suspend fun resolveRally(
        empire: PlayerEmpire,
        alliance: Alliance,
        rally: AllianceRally
    ): Result<String> = supabaseResult {
        when {
            rally.status != "GATHERING" -> {
                rpc("resolve_due_battles")
                afterChange()
                "The rally is already marching on ${rally.targetName}."
            }
            rally.leaderUid != myUid ->
                "Only ${rally.leaderName} can launch early. It marches automatically when the timer ends."
            else -> {
                rpc("launch_rally_now", buildJsonObject { put("p_rally", rally.id) })
                afterChange()
                "Strike launched! The rally marches on ${rally.targetName}."
            }
        }
    }

    suspend fun cancelRally(rally: AllianceRally): Result<Unit> = supabaseResult {
        rpc("cancel_rally", buildJsonObject { put("p_rally", rally.id) })
        afterChange()
    }

    // ------------------------------------------------------------------ aid & tech

    suspend fun requestAid(
        empire: PlayerEmpire,
        alliance: Alliance,
        resourceType: ResourceType,
        amount: Long
    ): Result<Unit> = supabaseResult {
        rpc("request_aid", buildJsonObject {
            put("p_resource", resourceType.name)
            put("p_amount", amount)
        })
        afterChange()
    }

    suspend fun donateAid(
        empire: PlayerEmpire,
        alliance: Alliance,
        request: ResourceAidRequest
    ): Result<Unit> = supabaseResult {
        rpc("donate_aid", buildJsonObject { put("p_request", request.id) })
        afterChange()
    }

    suspend fun contributeAllianceTech(
        empire: PlayerEmpire,
        alliance: Alliance,
        techType: AllianceTechType
    ): Result<String> = supabaseResult {
        val msg = rpc("contribute_alliance_tech", buildJsonObject { put("p_tech", techType.name) })
            .decodeAs<String>()
        afterChange()
        msg
    }

    // ------------------------------------------------------------------ leadership

    suspend fun inviteLord(
        empire: PlayerEmpire,
        alliance: Alliance,
        targetKingdom: RealmKingdom
    ): Result<Unit> = supabaseResult {
        rpc("invite_lord", buildJsonObject { put("p_target", targetKingdom.ownerUid) })
        afterChange()
    }

    suspend fun toggleOfficer(alliance: Alliance, targetUid: String): Result<String> = supabaseResult {
        val msg = rpc("toggle_officer", buildJsonObject { put("p_target", targetUid) }).decodeAs<String>()
        afterChange()
        msg
    }

    suspend fun transferLeadership(alliance: Alliance, targetUid: String): Result<Unit> = supabaseResult {
        rpc("transfer_leadership", buildJsonObject { put("p_target", targetUid) })
        afterChange()
    }

    suspend fun kickMember(
        alliance: Alliance,
        targetUid: String,
        targetPower: Long = 0L
    ): Result<Unit> = supabaseResult {
        rpc("kick_member", buildJsonObject { put("p_target", targetUid) })
        afterChange()
    }

    private companion object {
        const val TAG = "AllianceRepository"
    }
}
