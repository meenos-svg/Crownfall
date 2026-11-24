package com.example.data

import com.google.firebase.Firebase
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

class EmpireRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("User must be signed in with Google.")

    fun observeEmpire(userId: String): Flow<PlayerEmpire?> {
        val path = "users/$userId"
        return db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        PlayerEmpire::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser?.uid == userId) {
                    handleFirestoreError(root, OperationType.GET, path)
                }
                throw error
            }
    }

    fun observeRealmKingdoms(): Flow<List<RealmKingdom>> {
        val path = "kingdoms"
        return db.collection("kingdoms")
            .whereEqualTo("realmId", REALM_ID)
            .whereEqualTo("publicVisible", true)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    RealmKingdom::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.power }
            }
            .catch { error ->
                val root = unwrapFirestoreError(error)
                if (auth.currentUser != null) {
                    handleFirestoreError(root, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun ensureEmpireInitialized(displayName: String?): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val userPath = "users/$uid"
        val kingdomPath = "kingdoms/$uid"
        return try {
            val userRef = db.collection("users").document(uid)
            val snap = userRef.get().await()
            if (!snap.exists()) {
                val cleanName = sanitizeLordName(displayName)
                val (cx, cy) = GameCatalog.spawnCoordinateForUid(uid)
                val now = System.currentTimeMillis()
                val starter = PlayerEmpire(
                    userId = uid,
                    lordName = cleanName,
                    food = 1800L,
                    timber = 1800L,
                    stone = 1400L,
                    gold = 950L,
                    speedups = 5L,
                    lastHarvestEpochMs = now,
                    coordX = cx,
                    coordY = cy,
                    keepLevel = 1L,
                    barracksLevel = 1L,
                    stablesLevel = 1L,
                    archeryLevel = 1L,
                    siegeLevel = 1L,
                    allianceHallLevel = 1L,
                    farmLevel = 1L,
                    lumberLevel = 1L,
                    quarryLevel = 1L,
                    mintLevel = 1L,
                    infantry = 90L,
                    cavalry = 35L,
                    archers = 55L,
                    siege = 8L,
                    shieldUntilEpochMs = now + 15 * 60_000L
                )
                val finalEmpire = starter.copy(power = starter.computeTotalPower())
                val kingdom = RealmKingdom(
                    ownerUid = uid,
                    realmId = REALM_ID,
                    publicVisible = true,
                    lordName = cleanName,
                    keepLevel = finalEmpire.keepLevel,
                    power = finalEmpire.power,
                    garrisonPower = finalEmpire.garrisonPower(),
                    coordX = cx,
                    coordY = cy,
                    shieldUntilEpochMs = finalEmpire.shieldUntilEpochMs
                )
                val batch = db.batch()
                batch.set(userRef, finalEmpire.toCreateMap())
                batch.set(db.collection("kingdoms").document(uid), kingdom.toCreateMap())
                batch.commit().await()
            } else {
                val kingdomRef = db.collection("kingdoms").document(uid)
                val kSnap = kingdomRef.get().await()
                if (!kSnap.exists()) {
                    val existing = snap.toObject(
                        PlayerEmpire::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    if (existing != null) {
                        val kingdom = RealmKingdom(
                            ownerUid = uid,
                            realmId = REALM_ID,
                            publicVisible = true,
                            lordName = existing.lordName,
                            keepLevel = existing.keepLevel,
                            power = existing.power,
                            garrisonPower = existing.garrisonPower(),
                            coordX = existing.coordX,
                            coordY = existing.coordY,
                            allianceId = existing.allianceId,
                            allianceTag = existing.allianceTag,
                            shieldUntilEpochMs = existing.shieldUntilEpochMs
                        )
                        kingdomRef.set(kingdom.toCreateMap()).await()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, "$userPath / $kingdomPath")
            Result.failure(e)
        }
    }

    suspend fun collectResources(
        empire: PlayerEmpire,
        alliance: Alliance?,
        perResourceLastHarvestMs: Map<ResourceType, Long> = emptyMap()
    ): Result<ResourceCost> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "users/$uid"
        return try {
            val now = System.currentTimeMillis()
            val pending = GameCatalog.accruedPendingResources(empire, alliance, now, perResourceLastHarvestMs)
            val bonusFood = pending.food.coerceAtLeast(60L)
            val bonusTimber = pending.timber.coerceAtLeast(60L)
            val bonusStone = pending.stone.coerceAtLeast(45L)
            val bonusGold = pending.gold.coerceAtLeast(30L)
            db.collection("users").document(uid).update(
                mapOf(
                    "food" to (empire.food + bonusFood).coerceAtMost(999_999_999L),
                    "timber" to (empire.timber + bonusTimber).coerceAtMost(999_999_999L),
                    "stone" to (empire.stone + bonusStone).coerceAtMost(999_999_999L),
                    "gold" to (empire.gold + bonusGold).coerceAtMost(999_999_999L),
                    "lastHarvestEpochMs" to now,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(ResourceCost(bonusFood, bonusTimber, bonusStone, bonusGold))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun collectSingleResource(
        empire: PlayerEmpire,
        alliance: Alliance?,
        resourceType: ResourceType,
        perResourceLastHarvestMs: Map<ResourceType, Long> = emptyMap()
    ): Result<Long> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "users/$uid"
        return try {
            val now = System.currentTimeMillis()
            val pending = GameCatalog.accruedForResource(empire, alliance, resourceType, now, perResourceLastHarvestMs)
            val floor = when (resourceType) {
                ResourceType.FOOD -> 45L
                ResourceType.TIMBER -> 45L
                ResourceType.STONE -> 35L
                ResourceType.GOLD -> 25L
            }
            val amount = pending.coerceAtLeast(floor)
            val currentVal = when (resourceType) {
                ResourceType.FOOD -> empire.food
                ResourceType.TIMBER -> empire.timber
                ResourceType.STONE -> empire.stone
                ResourceType.GOLD -> empire.gold
            }
            val fieldName = resourceType.name.lowercase()
            db.collection("users").document(uid).update(
                mapOf(
                    fieldName to (currentVal + amount).coerceAtMost(999_999_999L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(amount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun startBuildingUpgrade(empire: PlayerEmpire, type: BuildingType): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "users/$uid"
        return try {
            if (empire.activeBuildKey.isNotBlank()) {
                return Result.failure(IllegalStateException("A construction queue is already active."))
            }
            val level = empire.buildingLevel(type)
            if (level >= 25L) {
                return Result.failure(IllegalStateException("${type.displayName} is already at max level 25."))
            }
            if (type != BuildingType.KEEP && level >= empire.keepLevel + 2L) {
                return Result.failure(IllegalStateException("Upgrade your Obsidian Keep first."))
            }
            val cost = GameCatalog.upgradeCost(type, level)
            if (!cost.canAfford(empire)) {
                return Result.failure(IllegalStateException("Insufficient resources for upgrade."))
            }
            val endsAt = System.currentTimeMillis() + GameCatalog.upgradeDurationMs(type, level)
            db.collection("users").document(uid).update(
                mapOf(
                    "food" to (empire.food - cost.food),
                    "timber" to (empire.timber - cost.timber),
                    "stone" to (empire.stone - cost.stone),
                    "gold" to (empire.gold - cost.gold),
                    "activeBuildKey" to type.name,
                    "activeBuildEndsAtMs" to endsAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun completeBuildingUpgrade(empire: PlayerEmpire, useSpeedup: Boolean): Result<String> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "users/$uid"
        return try {
            val type = BuildingType.fromRaw(empire.activeBuildKey)
                ?: return Result.failure(IllegalStateException("No active construction."))
            val newLevel = (empire.buildingLevel(type) + 1L).coerceAtMost(25L)
            var nextSpeedups = empire.speedups
            var nextGold = empire.gold
            if (useSpeedup) {
                when {
                    empire.speedups > 0L -> nextSpeedups = empire.speedups - 1L
                    empire.gold >= 100L -> nextGold = empire.gold - 100L
                    else -> return Result.failure(IllegalStateException("Need 1 Speedup Hourglass or 100 Gold."))
                }
            }
            val updatedEmpire = when (type) {
                BuildingType.KEEP -> empire.copy(keepLevel = newLevel)
                BuildingType.BARRACKS -> empire.copy(barracksLevel = newLevel)
                BuildingType.STABLES -> empire.copy(stablesLevel = newLevel)
                BuildingType.ARCHERY -> empire.copy(archeryLevel = newLevel)
                BuildingType.SIEGE -> empire.copy(siegeLevel = newLevel)
                BuildingType.ALLIANCE_HALL -> empire.copy(allianceHallLevel = newLevel)
                BuildingType.FARM -> empire.copy(farmLevel = newLevel)
                BuildingType.LUMBER -> empire.copy(lumberLevel = newLevel)
                BuildingType.QUARRY -> empire.copy(quarryLevel = newLevel)
                BuildingType.MINT -> empire.copy(mintLevel = newLevel)
            }
            val newPower = updatedEmpire.computeTotalPower()
            val batch = db.batch()
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    type.firestoreField to newLevel,
                    "speedups" to nextSpeedups,
                    "gold" to nextGold,
                    "power" to newPower,
                    "activeBuildKey" to "",
                    "activeBuildEndsAtMs" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("kingdoms").document(uid),
                mapOf(
                    "keepLevel" to updatedEmpire.keepLevel,
                    "power" to newPower,
                    "garrisonPower" to updatedEmpire.garrisonPower(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success("${type.displayName} reached Level $newLevel!")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun startTroopTraining(empire: PlayerEmpire, type: TroopType, qty: Long): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "users/$uid"
        return try {
            if (empire.activeTrainKey.isNotBlank()) {
                return Result.failure(IllegalStateException("Troops are already training in the barracks."))
            }
            val clampedQty = qty.coerceIn(1L, 500L)
            val cost = GameCatalog.trainingCost(type, clampedQty)
            if (!cost.canAfford(empire)) {
                return Result.failure(IllegalStateException("Insufficient resources to train $clampedQty ${type.displayName}."))
            }
            val endsAt = System.currentTimeMillis() +
                GameCatalog.trainingDurationMs(type, clampedQty, empire.buildingLevel(type.building))
            db.collection("users").document(uid).update(
                mapOf(
                    "food" to (empire.food - cost.food),
                    "timber" to (empire.timber - cost.timber),
                    "stone" to (empire.stone - cost.stone),
                    "gold" to (empire.gold - cost.gold),
                    "activeTrainKey" to type.name,
                    "activeTrainQty" to clampedQty,
                    "activeTrainEndsAtMs" to endsAt,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun completeTroopTraining(empire: PlayerEmpire, useSpeedup: Boolean): Result<String> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val path = "users/$uid"
        return try {
            val type = TroopType.fromRaw(empire.activeTrainKey)
                ?: return Result.failure(IllegalStateException("No active training queue."))
            val qty = empire.activeTrainQty.coerceAtLeast(1L)
            var nextSpeedups = empire.speedups
            var nextGold = empire.gold
            if (useSpeedup) {
                when {
                    empire.speedups > 0L -> nextSpeedups = empire.speedups - 1L
                    empire.gold >= 80L -> nextGold = empire.gold - 80L
                    else -> return Result.failure(IllegalStateException("Need 1 Speedup Hourglass or 80 Gold."))
                }
            }
            val newCount = (empire.troopCount(type) + qty).coerceAtMost(9_999_999L)
            val updatedEmpire = when (type) {
                TroopType.INFANTRY -> empire.copy(infantry = newCount)
                TroopType.CAVALRY -> empire.copy(cavalry = newCount)
                TroopType.ARCHERS -> empire.copy(archers = newCount)
                TroopType.SIEGE -> empire.copy(siege = newCount)
            }
            val newPower = updatedEmpire.computeTotalPower()
            val batch = db.batch()
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    type.firestoreField to newCount,
                    "speedups" to nextSpeedups,
                    "gold" to nextGold,
                    "power" to newPower,
                    "activeTrainKey" to "",
                    "activeTrainQty" to 0L,
                    "activeTrainEndsAtMs" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("kingdoms").document(uid),
                mapOf(
                    "power" to newPower,
                    "garrisonPower" to updatedEmpire.garrisonPower(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success("+$qty ${type.displayName} joined your garrison!")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun renameLord(newName: String): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        val clean = newName.trim()
        if (clean.length !in 2..28) {
            return Result.failure(IllegalArgumentException("Lord name must be 2 to 28 characters."))
        }
        return try {
            val batch = db.batch()
            batch.update(
                db.collection("users").document(uid),
                mapOf("lordName" to clean, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.update(
                db.collection("kingdoms").document(uid),
                mapOf("lordName" to clean, "updatedAt" to FieldValue.serverTimestamp())
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    suspend fun activatePeaceShield(empire: PlayerEmpire): Result<Unit> {
        val uid = try { requireUserId() } catch (e: Exception) { return Result.failure(e) }
        if (empire.gold < 250L) {
            return Result.failure(IllegalStateException("Peace Shield requires 250 Gold."))
        }
        return try {
            val shieldUntil = System.currentTimeMillis() + 60 * 60_000L
            val batch = db.batch()
            batch.update(
                db.collection("users").document(uid),
                mapOf(
                    "gold" to (empire.gold - 250L),
                    "shieldUntilEpochMs" to shieldUntil,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.update(
                db.collection("kingdoms").document(uid),
                mapOf(
                    "shieldUntilEpochMs" to shieldUntil,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, "users/$uid")
            Result.failure(e)
        }
    }

    private fun sanitizeLordName(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        return when {
            trimmed.length >= 2 -> trimmed.take(28)
            else -> "Lord Ashborn"
        }
    }
}
