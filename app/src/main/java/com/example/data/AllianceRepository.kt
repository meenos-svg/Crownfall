package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class AllianceRepository(
    private val db: Any? = null,
    private val auth: Any? = null
) {
    private val alliancesFlow = MutableStateFlow<List<Alliance>>(emptyList())
    private val messagesByAlliance = MutableStateFlow>>(emptyMap())
    private val ralliesByAlliance = MutableStateFlow>>(emptyMap())
    private val aidByAlliance = MutableStateFlow>>(emptyMap())
    private val invitesFlow = MutableStateFlow<List<AllianceInvite>>(emptyList())

    fun observeAlliances(): Flow<List<Alliance>> =
        alliancesFlow.map { list -> list.sortedByDescending { it.totalPower } }

    fun observeMessages(allianceId: String, userId: String): Flow<List<AllianceMessage>> =
        messagesByAlliance.map { map -> map[allianceId].orEmpty() }

    fun observeRallies(allianceId: String, userId: String): Flow<List<AllianceRally>> =
        ralliesByAlliance.map { map -> map[allianceId].orEmpty() }

    fun observeAidRequests(allianceId: String, userId: String): Flow<List<ResourceAidRequest>> =
        aidByAlliance.map { map -> map[allianceId].orEmpty() }

    fun observeMyInvites(userId: String): Flow<List<AllianceInvite>> =
        invitesFlow.map { list -> list.filter { it.status == "PENDING" } }

    suspend fun createAlliance(
        empire: PlayerEmpire,
        name: String,
        tag: String,
        motto: String,
        crestColor: String,
        openJoin: Boolean
    ): Result<Unit> {
        val uid = empire.userId.ifBlank { "local-player-1" }
        val cleanName = name.trim()
        val cleanTag = tag.trim().uppercase()
        val cleanMotto = motto.trim().take(120)
        if (cleanName.length !in 3..32) {
            return Result.failure(IllegalArgumentException("Alliance name must be 3–32 characters."))
        }
        if (cleanTag.length !in 2..5) {
            return Result.failure(IllegalArgumentException("Alliance tag must be 2–5 characters."))
        }
        if (empire.allianceId.isNotBlank()) {
            return Result.failure(IllegalStateException("Leave your current alliance first."))
        }

        val allianceId = UUID.randomUUID().toString()
        val alliance = Alliance(
            id = allianceId,
            realmId = REALM_ID,
            name = cleanName,
            tag = cleanTag,
            motto = cleanMotto.ifBlank { "Forged in Obsidian and Gold" },
            crestColor = crestColor,
            leaderUid = uid,
            officerUids = emptyList(),
            memberUids = listOf(uid),
            memberCount = 1L,
            totalPower = empire.power,
            openJoin = openJoin
        )
        alliancesFlow.value = listOf(alliance) + alliancesFlow.value

        val welcome = AllianceMessage(
            id = UUID.randomUUID().toString(),
            allianceId = allianceId,
            senderUid = uid,
            senderName = empire.lordName,
            senderRole = "LEADER",
            body = "${empire.lordName} founded [$cleanTag] $cleanName!",
            messageType = "SYSTEM",
            memberUids = listOf(uid)
        )
        messagesByAlliance.value = messagesByAlliance.value + (allianceId to listOf(welcome))
        return Result.success(Unit)
    }

    suspend fun joinAlliance(
        empire: PlayerEmpire,
        alliance: Alliance,
        invite: AllianceInvite? = null
    ): Result<Unit> {
        val uid = empire.userId.ifBlank { "local-player-1" }
        if (empire.allianceId.isNotBlank()) {
            return Result.failure(IllegalStateException("You are already in an alliance."))
        }
        if (alliance.memberUids.size >= 30) {
            return Result.failure(IllegalStateException("This alliance is already at 30/30 members."))
        }
        if (!alliance.openJoin && invite == null) {
            return Result.failure(IllegalStateException("This alliance is invite-only."))
        }

        val updatedMembers = (alliance.memberUids + uid).distinct()
        alliancesFlow.value = alliancesFlow.value.map {
            if (it.id == alliance.id) {
                it.copy(
                    memberUids = updatedMembers,
                    memberCount = updatedMembers.size.toLong(),
                    totalPower = it.totalPower + empire.power
                )
            } else {
                it
            }
        }
        if (invite != null) {
            invitesFlow.value = invitesFlow.value.map {
                if (it.id == invite.id) it.copy(status = "ACCEPTED") else it
            }
        }
        val msg = AllianceMessage(
            id = UUID.randomUUID().toString(),
            allianceId = alliance.id,
            senderUid = uid,
            senderName = empire.lordName,
            senderRole = "MEMBER",
            body = "${empire.lordName} swore fealty to [${alliance.tag}]!",
            messageType = "SYSTEM",
            memberUids = updatedMembers
        )
        val currentMsgs = messagesByAlliance.value[alliance.id].orEmpty()
        messagesByAlliance.value = messagesByAlliance.value + (alliance.id to (currentMsgs + msg))
        return Result.success(Unit)
    }

    suspend fun declineInvite(invite: AllianceInvite): Result<Unit> {
        invitesFlow.value = invitesFlow.value.map {
            if (it.id == invite.id) it.copy(status = "DECLINED") else it
        }
        return Result.success(Unit)
    }

    suspend fun leaveOrDisbandAlliance(empire: PlayerEmpire, alliance: Alliance): Result<Unit> {
        val uid = empire.userId.ifBlank { "local-player-1" }
        if (alliance.leaderUid == uid && alliance.memberUids.size > 1) {
            return Result.failure(IllegalStateException("Transfer leadership before leaving."))
        }
        alliancesFlow.value = if (alliance.leaderUid == uid) {
            alliancesFlow.value.filterNot { it.id == alliance.id }
        } else {
            alliancesFlow.value.map {
                if (it.id == alliance.id) {
                    val nextMembers = it.memberUids.filter { id -> id != uid }
                    val nextOfficers = it.officerUids.filter { id -> id != uid }
                    it.copy(
                        memberUids = nextMembers,
                        officerUids = nextOfficers,
                        memberCount = nextMembers.size.toLong(),
                        totalPower = (it.totalPower - empire.power).coerceAtLeast(0L)
                    )
                } else {
                    it
                }
            }
        }
        return Result.success(Unit)
    }

    suspend fun sendMessage(
        empire: PlayerEmpire,
        alliance: Alliance,
        body: String,
        messageType: String = "CHAT"
    ): Result<Unit> {
        val uid = empire.userId.ifBlank { "local-player-1" }
        val clean = body.trim()
        if (clean.isEmpty() || clean.length > 300) {
            return Result.failure(IllegalArgumentException("Message must be 1–300 characters."))
        }
        val role = alliance.roleOf(uid).takeIf { it != "NONE" } ?: "MEMBER"
        val msg = AllianceMessage(
            id = UUID.randomUUID().toString(),
            allianceId = alliance.id,
            senderUid = uid,
            senderName = empire.lordName,
            senderRole = role,
            body = clean,
            messageType = messageType,
            memberUids = alliance.memberUids
        )
        val current = messagesByAlliance.value[alliance.id].orEmpty()
        messagesByAlliance.value = messagesByAlliance.value + (alliance.id to (current + msg))
        return Result.success(Unit)
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
}
