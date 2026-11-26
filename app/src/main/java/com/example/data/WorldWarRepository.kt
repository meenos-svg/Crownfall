package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class WorldWarRepository(
    private val db: Any? = null,
    private val auth: Any? = null
) {
    private val marchesFlow = MutableStateFlow<List<WorldMarch>>(emptyList())
    private val battleLogsFlow = MutableStateFlow<List<BattleLog>>(emptyList())

    fun observeActiveMarches(): Flow<List<WorldMarch>> =
        marchesFlow.map { list ->
            list.filter { it.status == "MARCHING" }
                .sortedBy { it.arrivesAtEpochMs }
        }

    fun observeBattleLogs(): Flow<List<BattleLog>> =
        battleLogsFlow.map { list -> list.take(50) }

    suspend fun dispatchMarch(
        empire: PlayerEmpire,
        alliance: Alliance?,
        marchType: String,
        targetType: String,
        targetId: String,
        targetName: String,
        toX: Long,
        toY: Long,
        infantrySent: Long,
        cavalrySent: Long,
        archersSent: Long,
        siegeSent: Long
    ): Result<Unit> {
        val uid = empire.userId.ifBlank { "local-player-1" }
        val totalUnits = infantrySent + cavalrySent + archersSent + siegeSent
        if (totalUnits <= 0L) {
            return Result.failure(IllegalArgumentException("Select at least 1 troop to march."))
        }
        if (infantrySent > empire.infantry || cavalrySent > empire.cavalry ||
            archersSent > empire.archers || siegeSent > empire.siege
        ) {
            return Result.failure(IllegalArgumentException("Selected troops exceed available garrison."))
        }

        val troopPower = (
            infantrySent * TroopType.INFANTRY.power +
                cavalrySent * TroopType.CAVALRY.power +
                archersSent * TroopType.ARCHERS.power +
                siegeSent * TroopType.SIEGE.power
            ).coerceAtLeast(1L)

        val now = System.currentTimeMillis()
        val travelMs = GameCatalog.marchDurationMs(empire.coordX, empire.coordY, toX, toY, alliance)
        val march = WorldMarch(
            id = UUID.randomUUID().toString(),
            ownerUid = uid,
            realmId = REALM_ID,
            ownerName = empire.lordName.take(28),
            allianceId = empire.allianceId,
            allianceTag = empire.allianceTag,
            marchType = marchType,
            targetType = targetType,
            targetId = targetId.take(128),
            targetName = targetName.take(48),
            fromX = empire.coordX,
            fromY = empire.coordY,
            toX = toX.coerceIn(0L, 99L),
            toY = toY.coerceIn(0L, 99L),
            troopPower = troopPower,
            infantrySent = infantrySent,
            cavalrySent = cavalrySent,
            archersSent = archersSent,
            siegeSent = siegeSent,
            departedAtEpochMs = now,
            arrivesAtEpochMs = now + travelMs,
            status = "MARCHING"
        )
        marchesFlow.value = listOf(march) + marchesFlow.value
        return Result.success(Unit)
    }

    suspend fun recallMarch(empire: PlayerEmpire, march: WorldMarch): Result<Unit> {
        val uid = empire.userId.ifBlank { "local-player-1" }
        if (march.ownerUid != uid || march.status != "MARCHING") {
            return Result.failure(IllegalStateException("Cannot recall this march."))
        }
        marchesFlow.value = marchesFlow.value.map {
            if (it.id == march.id) it.copy(status = "RECALLED") else it
        }
        return Result.success(Unit)
    }

    suspend fun resolveMarch(
        empire: PlayerEmpire,
        alliance: Alliance?,
        march: WorldMarch,
        targetKingdom: RealmKingdom?
    ): Result<BattleLog> {
        val uid = empire.userId.ifBlank { "local-player-1" }
        if (march.ownerUid != uid || march.status != "MARCHING") {
            return Result.failure(IllegalStateException("March already resolved."))
        }

        val npcNode = GameCatalog.worldNpcNodes.firstOrNull { it.id == march.targetId }
        val defenderPower = when (march.targetType) {
            "KINGDOM" -> targetKingdom?.garrisonPower ?: 1200L
            "BASTION" -> npcNode?.garrisonPower ?: 1200L
            else -> 0L
        }
        val outcome = GameCatalog.resolveCombat(
            attackerPower = march.troopPower,
            siegeSent = march.siegeSent,
            defenderPower = defenderPower,
            targetType = march.targetType,
            alliance = alliance,
            npcNode = npcNode
        )

        val battleLog = BattleLog(
            id = UUID.randomUUID().toString(),
            realmId = REALM_ID,
            attackerUid = uid,
            attackerName = empire.lordName.take(28),
            attackerTag = empire.allianceTag,
            defenderUid = targetKingdom?.ownerUid ?: march.targetId,
            defenderName = march.targetName.take(48),
            defenderTag = targetKingdom?.allianceTag ?: if (march.targetType == "RESOURCE") "NODE" else "NPC",
            battleType = march.marchType,
            victory = outcome.victory,
            attackerPower = outcome.effectiveAttackerPower,
            defenderPower = outcome.defenderPower,
            troopsLost = outcome.troopsLost,
            lootFood = outcome.lootFood,
            lootTimber = outcome.lootTimber,
            lootStone = outcome.lootStone,
            lootGold = outcome.lootGold,
            allianceId = empire.allianceId
        )

        marchesFlow.value = marchesFlow.value.map {
            if (it.id == march.id) it.copy(status = "RESOLVED") else it
        }
        battleLogsFlow.value = listOf(battleLog) + battleLogsFlow.value
        return Result.success(battleLog)
    }
}
