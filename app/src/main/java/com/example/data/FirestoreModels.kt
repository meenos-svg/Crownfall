package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

const val REALM_ID = "realm_1"

@Keep
@IgnoreExtraProperties
data class PlayerEmpire(
    @DocumentId val id: String = "",
    val userId: String = "",
    val lordName: String = "Lord Ashborn",
    val food: Long = 1500L,
    val timber: Long = 1500L,
    val stone: Long = 1200L,
    val gold: Long = 800L,
    val speedups: Long = 4L,
    val lastHarvestEpochMs: Long = 0L,
    val coordX: Long = 50L,
    val coordY: Long = 50L,
    val power: Long = 1500L,
    val keepLevel: Long = 1L,
    val barracksLevel: Long = 1L,
    val stablesLevel: Long = 1L,
    val archeryLevel: Long = 1L,
    val siegeLevel: Long = 1L,
    val allianceHallLevel: Long = 1L,
    val farmLevel: Long = 1L,
    val lumberLevel: Long = 1L,
    val quarryLevel: Long = 1L,
    val mintLevel: Long = 1L,
    val infantry: Long = 80L,
    val cavalry: Long = 30L,
    val archers: Long = 50L,
    val siege: Long = 8L,
    val activeBuildKey: String = "",
    val activeBuildEndsAtMs: Long = 0L,
    val activeTrainKey: String = "",
    val activeTrainQty: Long = 0L,
    val activeTrainEndsAtMs: Long = 0L,
    val allianceId: String = "",
    val allianceTag: String = "",
    val allianceRole: String = "NONE",
    val honor: Long = 0L,
    val shieldUntilEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun buildingLevel(type: BuildingType): Long = when (type) {
        BuildingType.KEEP -> keepLevel
        BuildingType.BARRACKS -> barracksLevel
        BuildingType.STABLES -> stablesLevel
        BuildingType.ARCHERY -> archeryLevel
        BuildingType.SIEGE -> siegeLevel
        BuildingType.ALLIANCE_HALL -> allianceHallLevel
        BuildingType.FARM -> farmLevel
        BuildingType.LUMBER -> lumberLevel
        BuildingType.QUARRY -> quarryLevel
        BuildingType.MINT -> mintLevel
    }

    fun troopCount(type: TroopType): Long = when (type) {
        TroopType.INFANTRY -> infantry
        TroopType.CAVALRY -> cavalry
        TroopType.ARCHERS -> archers
        TroopType.SIEGE -> siege
    }

    fun totalTroops(): Long = infantry + cavalry + archers + siege

    fun garrisonPower(): Long =
        infantry * TroopType.INFANTRY.power +
            cavalry * TroopType.CAVALRY.power +
            archers * TroopType.ARCHERS.power +
            siege * TroopType.SIEGE.power

    fun computeTotalPower(): Long {
        val buildingScore = (keepLevel * 450L) +
            (barracksLevel + stablesLevel + archeryLevel + siegeLevel + allianceHallLevel) * 160L +
            (farmLevel + lumberLevel + quarryLevel + mintLevel) * 110L
        return buildingScore + garrisonPower() + honor
    }

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "lordName" to lordName,
        "food" to food,
        "timber" to timber,
        "stone" to stone,
        "gold" to gold,
        "speedups" to speedups,
        "lastHarvestEpochMs" to lastHarvestEpochMs,
        "coordX" to coordX,
        "coordY" to coordY,
        "power" to power,
        "keepLevel" to keepLevel,
        "barracksLevel" to barracksLevel,
        "stablesLevel" to stablesLevel,
        "archeryLevel" to archeryLevel,
        "siegeLevel" to siegeLevel,
        "allianceHallLevel" to allianceHallLevel,
        "farmLevel" to farmLevel,
        "lumberLevel" to lumberLevel,
        "quarryLevel" to quarryLevel,
        "mintLevel" to mintLevel,
        "infantry" to infantry,
        "cavalry" to cavalry,
        "archers" to archers,
        "siege" to siege,
        "activeBuildKey" to activeBuildKey,
        "activeBuildEndsAtMs" to activeBuildEndsAtMs,
        "activeTrainKey" to activeTrainKey,
        "activeTrainQty" to activeTrainQty,
        "activeTrainEndsAtMs" to activeTrainEndsAtMs,
        "allianceId" to allianceId,
        "allianceTag" to allianceTag,
        "allianceRole" to allianceRole,
        "honor" to honor,
        "shieldUntilEpochMs" to shieldUntilEpochMs,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class RealmKingdom(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val realmId: String = REALM_ID,
    val publicVisible: Boolean = true,
    val lordName: String = "",
    val keepLevel: Long = 1L,
    val power: Long = 0L,
    val garrisonPower: Long = 0L,
    val coordX: Long = 50L,
    val coordY: Long = 50L,
    val allianceId: String = "",
    val allianceTag: String = "",
    val shieldUntilEpochMs: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "ownerUid" to ownerUid,
        "realmId" to realmId,
        "publicVisible" to publicVisible,
        "lordName" to lordName,
        "keepLevel" to keepLevel,
        "power" to power,
        "garrisonPower" to garrisonPower,
        "coordX" to coordX,
        "coordY" to coordY,
        "allianceId" to allianceId,
        "allianceTag" to allianceTag,
        "shieldUntilEpochMs" to shieldUntilEpochMs,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class WorldMarch(
    @DocumentId val id: String = "",
    val ownerUid: String = "",
    val realmId: String = REALM_ID,
    val ownerName: String = "",
    val allianceId: String = "",
    val allianceTag: String = "",
    val marchType: String = "RAID",
    val targetType: String = "BASTION",
    val targetId: String = "",
    val targetName: String = "",
    val fromX: Long = 0L,
    val fromY: Long = 0L,
    val toX: Long = 0L,
    val toY: Long = 0L,
    val troopPower: Long = 1L,
    val infantrySent: Long = 0L,
    val cavalrySent: Long = 0L,
    val archersSent: Long = 0L,
    val siegeSent: Long = 0L,
    val departedAtEpochMs: Long = 0L,
    val arrivesAtEpochMs: Long = 0L,
    val status: String = "MARCHING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "ownerUid" to ownerUid,
        "realmId" to realmId,
        "ownerName" to ownerName,
        "allianceId" to allianceId,
        "allianceTag" to allianceTag,
        "marchType" to marchType,
        "targetType" to targetType,
        "targetId" to targetId,
        "targetName" to targetName,
        "fromX" to fromX,
        "fromY" to fromY,
        "toX" to toX,
        "toY" to toY,
        "troopPower" to troopPower,
        "infantrySent" to infantrySent,
        "cavalrySent" to cavalrySent,
        "archersSent" to archersSent,
        "siegeSent" to siegeSent,
        "departedAtEpochMs" to departedAtEpochMs,
        "arrivesAtEpochMs" to arrivesAtEpochMs,
        "status" to status,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Alliance(
    @DocumentId val id: String = "",
    val realmId: String = REALM_ID,
    val name: String = "",
    val tag: String = "",
    val motto: String = "",
    val crestColor: String = "CRIMSON",
    val leaderUid: String = "",
    val officerUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    val memberCount: Long = 1L,
    val totalPower: Long = 0L,
    val openJoin: Boolean = true,
    val techMarchLevel: Long = 0L,
    val techYieldLevel: Long = 0L,
    val techAttackLevel: Long = 0L,
    val techDefenseLevel: Long = 0L,
    val techXp: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleOf(uid: String): String = when {
        leaderUid == uid -> "LEADER"
        uid in officerUids -> "OFFICER"
        uid in memberUids -> "MEMBER"
        else -> "NONE"
    }

    fun techLevel(type: AllianceTechType): Long = when (type) {
        AllianceTechType.MARCH_SPEED -> techMarchLevel
        AllianceTechType.RESOURCE_YIELD -> techYieldLevel
        AllianceTechType.RALLY_ATTACK -> techAttackLevel
        AllianceTechType.CITADEL_DEFENSE -> techDefenseLevel
    }

    fun toCreateMap(): Map<String, Any> = mapOf(
        "realmId" to realmId,
        "name" to name,
        "tag" to tag,
        "motto" to motto,
        "crestColor" to crestColor,
        "leaderUid" to leaderUid,
        "officerUids" to officerUids,
        "memberUids" to memberUids,
        "memberCount" to memberCount,
        "totalPower" to totalPower,
        "openJoin" to openJoin,
        "techMarchLevel" to techMarchLevel,
        "techYieldLevel" to techYieldLevel,
        "techAttackLevel" to techAttackLevel,
        "techDefenseLevel" to techDefenseLevel,
        "techXp" to techXp,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AllianceMessage(
    @DocumentId val id: String = "",
    val allianceId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val senderRole: String = "MEMBER",
    val body: String = "",
    val messageType: String = "CHAT",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "allianceId" to allianceId,
        "senderUid" to senderUid,
        "senderName" to senderName,
        "senderRole" to senderRole,
        "body" to body,
        "messageType" to messageType,
        "memberUids" to memberUids,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AllianceRally(
    @DocumentId val id: String = "",
    val allianceId: String = "",
    val leaderUid: String = "",
    val leaderName: String = "",
    val targetType: String = "BASTION",
    val targetId: String = "",
    val targetName: String = "",
    val targetX: Long = 50L,
    val targetY: Long = 50L,
    val targetPower: Long = 1000L,
    val totalPower: Long = 1000L,
    val participantUids: List<String> = emptyList(),
    val participantSummary: String = "",
    val launchAtEpochMs: Long = 0L,
    val status: String = "GATHERING",
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "allianceId" to allianceId,
        "leaderUid" to leaderUid,
        "leaderName" to leaderName,
        "targetType" to targetType,
        "targetId" to targetId,
        "targetName" to targetName,
        "targetX" to targetX,
        "targetY" to targetY,
        "targetPower" to targetPower,
        "totalPower" to totalPower,
        "participantUids" to participantUids,
        "participantSummary" to participantSummary,
        "launchAtEpochMs" to launchAtEpochMs,
        "status" to status,
        "memberUids" to memberUids,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ResourceAidRequest(
    @DocumentId val id: String = "",
    val allianceId: String = "",
    val requesterUid: String = "",
    val requesterName: String = "",
    val resourceType: String = "FOOD",
    val requestedAmount: Long = 500L,
    val fulfilledAmount: Long = 0L,
    val status: String = "OPEN",
    val donorUids: List<String> = emptyList(),
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "allianceId" to allianceId,
        "requesterUid" to requesterUid,
        "requesterName" to requesterName,
        "resourceType" to resourceType,
        "requestedAmount" to requestedAmount,
        "fulfilledAmount" to fulfilledAmount,
        "status" to status,
        "donorUids" to donorUids,
        "memberUids" to memberUids,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AllianceInvite(
    @DocumentId val id: String = "",
    val allianceId: String = "",
    val allianceName: String = "",
    val allianceTag: String = "",
    val inviterUid: String = "",
    val inviterName: String = "",
    val targetUid: String = "",
    val targetName: String = "",
    val status: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "allianceId" to allianceId,
        "allianceName" to allianceName,
        "allianceTag" to allianceTag,
        "inviterUid" to inviterUid,
        "inviterName" to inviterName,
        "targetUid" to targetUid,
        "targetName" to targetName,
        "status" to status,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class BattleLog(
    @DocumentId val id: String = "",
    val realmId: String = REALM_ID,
    val attackerUid: String = "",
    val attackerName: String = "",
    val attackerTag: String = "",
    val defenderUid: String = "",
    val defenderName: String = "",
    val defenderTag: String = "",
    val battleType: String = "RAID",
    val victory: Boolean = true,
    val attackerPower: Long = 0L,
    val defenderPower: Long = 0L,
    val troopsLost: Long = 0L,
    val lootFood: Long = 0L,
    val lootTimber: Long = 0L,
    val lootStone: Long = 0L,
    val lootGold: Long = 0L,
    val allianceId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "realmId" to realmId,
        "attackerUid" to attackerUid,
        "attackerName" to attackerName,
        "attackerTag" to attackerTag,
        "defenderUid" to defenderUid,
        "defenderName" to defenderName,
        "defenderTag" to defenderTag,
        "battleType" to battleType,
        "victory" to victory,
        "attackerPower" to attackerPower,
        "defenderPower" to defenderPower,
        "troopsLost" to troopsLost,
        "lootFood" to lootFood,
        "lootTimber" to lootTimber,
        "lootStone" to lootStone,
        "lootGold" to lootGold,
        "allianceId" to allianceId,
        "createdAt" to FieldValue.serverTimestamp()
    )
}
