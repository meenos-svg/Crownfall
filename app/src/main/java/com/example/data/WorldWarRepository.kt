package com.example.data

import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WorldWarRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google.")

    fun observeActiveMarches(): Flow<List<WorldMarch>> {
        val path = "marches"
        return db.collection("marches")
            .whereEqualTo("realmId", REALM_ID)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    WorldMarch::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).filter { it.status == "MARCHING" }
                    .sortedBy { it.arrivesAtEpochMs }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser != null) {
                    handleFirestoreError(root, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeBattleLogs(): Flow<List<BattleLog>> {
        val path = "battleLogs"
        return db.collection("battleLogs")
            .whereEqualTo("realmId", REALM_ID)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    BattleLog::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                    .take(50)
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser != null) {
                    handleFirestoreError(root, OperationType.LIST, path)
                }
                throw error
            }
    }

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
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val docRef = db.collection("marches").document()
        return try {
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
            val updatedEmpire = empire.copy(
                infantry = empire.infantry - infantrySent,
                cavalry = empire.cavalry - cavalrySent,
                archers = empire.archers - archersSent,
                siege = empire.siege - siegeSent
            )
            val batch = db.batch()
            batch.set(docRef, march.toCreateMap())
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "infantry" to updatedEmpire.infantry,
                    "cavalry" to updatedEmpire.cavalry,
                    "archers" to updatedEmpire.archers,
                    "siege" to updatedEmpire.siege,
                    "shieldUntilEpochMs" to if (marchType == "RAID" && targetType == "KINGDOM") 0L else empire.shieldUntilEpochMs,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun recallMarch(empire: PlayerEmpire, march: WorldMarch): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val marchPath = "marches/${march.id}"
        return try {
            if (march.ownerUid != uid || march.status != "MARCHING") {
                return Result.failure(IllegalStateException("Cannot recall this march."))
            }
            val batch = db.batch()
            batch.update(
                db.collection("marches").document(march.id),
                mapOf("status" to "RECALLED", "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "infantry" to (empire.infantry + march.infantrySent),
                    "cavalry" to (empire.cavalry + march.cavalrySent),
                    "archers" to (empire.archers + march.archersSent),
                    "siege" to (empire.siege + march.siegeSent),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, marchPath)
            Result.failure(e)
        }
    }

    suspend fun resolveMarch(
        empire: PlayerEmpire,
        alliance: Alliance?,
        march: WorldMarch,
        targetKingdom: RealmKingdom?
    ): Result<BattleLog> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val marchPath = "marches/${march.id}"
        return try {
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

            // Distribute casualties proportionally across returning troops
            val totalSent = (march.infantrySent + march.cavalrySent + march.archersSent + march.siegeSent).coerceAtLeast(1L)
            val survivalRatio = ((totalSent - outcome.troopsLost).toDouble() / totalSent.toDouble()).coerceIn(0.45, 1.0)
            val retInf = (march.infantrySent * survivalRatio).toLong()
            val retCav = (march.cavalrySent * survivalRatio).toLong()
            val retArc = (march.archersSent * survivalRatio).toLong()
            val retSiege = (march.siegeSent * survivalRatio).toLong()

            val nextEmpire = empire.copy(
                food = (empire.food + outcome.lootFood).coerceAtMost(999_999_999L),
                timber = (empire.timber + outcome.lootTimber).coerceAtMost(999_999_999L),
                stone = (empire.stone + outcome.lootStone).coerceAtMost(999_999_999L),
                gold = (empire.gold + outcome.lootGold).coerceAtMost(999_999_999L),
                infantry = (empire.infantry + retInf).coerceAtMost(9_999_999L),
                cavalry = (empire.cavalry + retCav).coerceAtMost(9_999_999L),
                archers = (empire.archers + retArc).coerceAtMost(9_999_999L),
                siege = (empire.siege + retSiege).coerceAtMost(9_999_999L),
                honor = (empire.honor + outcome.honorGained).coerceAtMost(999_999_999L),
                speedups = if (outcome.victory && march.targetType == "BASTION") empire.speedups + 1L else empire.speedups
            )
            val newPower = nextEmpire.computeTotalPower()
            val battleLog = BattleLog(
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

            val logRef = db.collection("battleLogs").document()
            val batch = db.batch()
            batch.update(
                db.collection("marches").document(march.id),
                mapOf("status" to "RESOLVED", "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.set(logRef, battleLog.toCreateMap())
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "food" to nextEmpire.food,
                    "timber" to nextEmpire.timber,
                    "stone" to nextEmpire.stone,
                    "gold" to nextEmpire.gold,
                    "infantry" to nextEmpire.infantry,
                    "cavalry" to nextEmpire.cavalry,
                    "archers" to nextEmpire.archers,
                    "siege" to nextEmpire.siege,
                    "honor" to nextEmpire.honor,
                    "speedups" to nextEmpire.speedups,
                    "power" to newPower,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("kingdoms").document(uid),
                mapOf(
                    "power" to newPower,
                    "garrisonPower" to nextEmpire.garrisonPower(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(battleLog)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, marchPath)
            Result.failure(e)
        }
    }
}
