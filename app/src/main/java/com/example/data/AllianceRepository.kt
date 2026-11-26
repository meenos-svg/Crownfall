package com.example.data

import android.util.Log
import com.google.firebase.Timestamp
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Date
import java.util.UUID

/** Asks every polling flow (alliances, chat, aid, invites, own empire) to refresh now. */
object GameSignals {
    val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    fun poke() { refresh.tryEmit(Unit) }
}

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

private fun String?.toTimestamp(): Timestamp? =
    this.toEpochMs().takeIf { it > 0 }?.let { Timestamp(Date(it)) }

/**
 * Alliances live in Supabase. Every change goes through a server function
 * (part 5 SQL) that checks rank and rules; the app only reads and polls.
 * War rallies are still kept on this device until marches and battles move
 * to the server.
 */
class AllianceRepository(
    @Suppress("unused") private val db: Any? = null,
    @Suppress("unused") private val auth: Any? = null
) {
    private val client get() = SupabaseProvider.client
    private val myUid: String get() = client.auth.currentUserOrNull()?.id.orEmpty()

    private val ralliesByAlliance = MutableStateFlow<Map<String, List<AllianceRally>>>(emptyMap())

    /** Fetch immediately, then again every [intervalMs] or whenever something pokes [GameSignals]. */
    private fun <T> polled(intervalMs: Long, fetch: suspend () -> T): Flow<T> = channelFlow {
        while (isActive) {
            runCatching { fetch() }
                .onSuccess { send(it) }
                .onFailure { Log.w(TAG, "Alliance refresh failed", it) }
            withTimeoutOrNull(intervalMs) { GameSignals.refresh.first() }
        }
    }

    private suspend fun rpc(name: String, params: JsonObject = JsonObject(emptyMap())) =
        client.postgrest.rpc(name, params)

    private fun afterChange() = GameSignals.poke()

    // ------------------------------------------------------------------ reads

    fun observeAlliances(): Flow<List<Alliance>> = polled(15_000L) {
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

    fun observeMessages(allianceId: String, userId: String): Flow<List<AllianceMessage>> = polled(5_000L) {
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

    fun observeRallies(allianceId: String, userId: String): Flow<List<AllianceRally>> =
        ralliesByAlliance.map { map -> map[allianceId].orEmpty() }

    fun observeAidRequests(allianceId: String, userId: String): Flow<List<ResourceAidRequest>> = polled(8_000L) {
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

    fun observeMyInvites(userId: String): Flow<List<AllianceInvite>> = polled(15_000L) {
        val me = myUid
        if (me.isBlank()) return@polled emptyList()
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
        ralliesByAlliance.value = ralliesByAlliance.value - alliance.id
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

    // ------------------------------------------------------------------ rallies (still on this device)

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
    ): Result<Unit> {
        val uid = empire.userId.ifBlank { "local-player-1" }
        val powerCommit = committedPower.coerceAtLeast(100L)
        val rally = AllianceRally(
            id = UUID.randomUUID().toString(),
            allianceId = alliance.id,
            leaderUid = uid,
            leaderName = empire.lordName,
            targetType = targetType,
            targetId = targetId,
            targetName = targetName.take(48),
            targetX = targetX.coerceIn(0L, 99L),
            targetY = targetY.coerceIn(0L, 99L),
            targetPower = targetPower.coerceAtLeast(100L),
            totalPower = powerCommit,
            participantUids = listOf(uid),
            participantSummary = "${empire.lordName} (${formatCompact(powerCommit)})",
            launchAtEpochMs = System.currentTimeMillis() + 60_000L,
            status = "GATHERING",
            memberUids = alliance.memberUids
        )
        val current = ralliesByAlliance.value[alliance.id].orEmpty()
        ralliesByAlliance.value = ralliesByAlliance.value + (alliance.id to (listOf(rally) + current))
        sendMessage(
            empire,
            alliance,
            "WAR RALLY called against $targetName ($targetX, $targetY)! Join before launch!",
            messageType = "RALLY"
        )
        return Result.success(Unit)
    }

    suspend fun joinRally(
        empire: PlayerEmpire,
        alliance: Alliance,
        rally: AllianceRally,
        committedPower: Long
    ): Result<Unit> {
        val uid = empire.userId.ifBlank { "local-player-1" }
        if (uid in rally.participantUids) {
            return Result.failure(IllegalStateException("You already joined this rally."))
        }
        val powerCommit = committedPower.coerceAtLeast(100L)
        val updatedList = ralliesByAlliance.value[alliance.id].orEmpty().map {
            if (it.id == rally.id) {
                it.copy(
                    participantUids = it.participantUids + uid,
                    participantSummary = "${it.participantSummary}, ${empire.lordName} (${formatCompact(powerCommit)})".take(300),
                    totalPower = it.totalPower + powerCommit
                )
            } else {
                it
            }
        }
        ralliesByAlliance.value = ralliesByAlliance.value + (alliance.id to updatedList)
        return Result.success(Unit)
    }

    suspend fun resolveRally(
        empire: PlayerEmpire,
        alliance: Alliance,
        rally: AllianceRally
    ): Result<BattleLog> {
        val uid = empire.userId.ifBlank { "local-player-1" }
        val npc = GameCatalog.worldNpcNodes.firstOrNull { it.id == rally.targetId }
        val outcome = GameCatalog.resolveCombat(
            attackerPower = rally.totalPower,
            siegeSent = empire.siege,
            defenderPower = rally.targetPower,
            targetType = rally.targetType,
            alliance = alliance,
            npcNode = npc
        )
        val battleLog = BattleLog(
            id = UUID.randomUUID().toString(),
            realmId = REALM_ID,
            attackerUid = uid,
            attackerName = "[${alliance.tag}] War Rally",
            attackerTag = alliance.tag,
            defenderUid = rally.targetId,
            defenderName = rally.targetName,
            defenderTag = if (rally.targetType == "BASTION") "NPC" else "PVP",
            battleType = "RALLY",
            victory = outcome.victory,
            attackerPower = outcome.effectiveAttackerPower,
            defenderPower = outcome.defenderPower,
            troopsLost = outcome.troopsLost,
            lootFood = outcome.lootFood,
            lootTimber = outcome.lootTimber,
            lootStone = outcome.lootStone,
            lootGold = outcome.lootGold,
            allianceId = alliance.id
        )
        val updatedList = ralliesByAlliance.value[alliance.id].orEmpty().map {
            if (it.id == rally.id) it.copy(status = "RESOLVED") else it
        }
        ralliesByAlliance.value = ralliesByAlliance.value + (alliance.id to updatedList)
        return Result.success(battleLog)
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
