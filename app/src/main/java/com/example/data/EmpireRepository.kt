package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class EmpireRepository(
    private val db: Any? = null,
    private val auth: Any? = null
) {
    private val empireFlow = MutableStateFlow<PlayerEmpire?>(null)
    private val kingdomsFlow = MutableStateFlow<List<RealmKingdom>>(emptyList())

    fun observeEmpire(userId: String): Flow<PlayerEmpire?> = empireFlow

    fun observeRealmKingdoms(): Flow<List<RealmKingdom>> =
        kingdomsFlow.map { list -> list.sortedByDescending { it.power } }

    suspend fun ensureEmpireInitialized(displayName: String?): Result<Unit> {
        val uid = "local-player-1"
        val cleanName = displayName?.trim()?.takeIf { it.isNotEmpty() } ?: "Tempora"
        val now = System.currentTimeMillis()
        val current = empireFlow.value
        if (current == null) {
            val starter = PlayerEmpire(
                id = uid,
                userId = uid,
                lordName = cleanName,
                food = 5000L,
                timber = 5000L,
                stone = 4000L,
                gold = 2500L,
                speedups = 10L,
                lastHarvestEpochMs = now,
                coordX = 50L,
                coordY = 50L,
                keepLevel = 5L,
                barracksLevel = 4L,
                stablesLevel = 4L,
                archeryLevel = 4L,
                siegeLevel = 3L,
                allianceHallLevel = 3L,
                farmLevel = 4L,
                lumberLevel = 4L,
                quarryLevel = 3L,
                mintLevel = 3L,
                infantry = 180L,
                cavalry = 90L,
                archers = 110L,
                siege = 20L,
                shieldUntilEpochMs = now + 30 * 60_000L
            )
            val withPower = starter.copy(power = starter.computeTotalPower())
            empireFlow.value = withPower
            kingdomsFlow.value = listOf(
                RealmKingdom(
                    id = uid,
                    ownerUid = uid,
                    realmId = REALM_ID,
                    publicVisible = true,
                    lordName = withPower.lordName,
                    keepLevel = withPower.keepLevel,
                    power = withPower.power,
                    garrisonPower = withPower.garrisonPower(),
                    coordX = withPower.coordX,
                    coordY = withPower.coordY,
                    shieldUntilEpochMs = withPower.shieldUntilEpochMs
                )
            )
        }
        return Result.success(Unit)
    }

    private fun syncKingdom(empire: PlayerEmpire) {
        val updated = RealmKingdom(
            id = empire.userId.ifBlank { "local-player-1" },
            ownerUid = empire.userId.ifBlank { "local-player-1" },
            realmId = REALM_ID,
            publicVisible = true,
            lordName = empire.lordName,
            keepLevel = empire.keepLevel,
            power = empire.power,
            garrisonPower = empire.garrisonPower(),
            coordX = empire.coordX,
            coordY = empire.coordY,
            allianceId = empire.allianceId,
            allianceTag = empire.allianceTag,
            shieldUntilEpochMs = empire.shieldUntilEpochMs
        )
        val existing = kingdomsFlow.value.filterNot { it.ownerUid == updated.ownerUid }
        kingdomsFlow.value = (existing + updated).sortedByDescending { it.power }
    }

    suspend fun collectResources(
        empire: PlayerEmpire,
        alliance: Alliance?,
        perResourceLastHarvestMs: Map<ResourceType, Long> = emptyMap()
    ): Result<ResourceCost> {
        val now = System.currentTimeMillis()
        val pending = GameCatalog.accruedPendingResources(empire, alliance, now, perResourceLastHarvestMs)
        val bonusFood = pending.food.coerceAtLeast(150L)
        val bonusTimber = pending.timber.coerceAtLeast(150L)
        val bonusStone = pending.stone.coerceAtLeast(120L)
        val bonusGold = pending.gold.coerceAtLeast(80L)
        val updated = empire.copy(
            food = (empire.food + bonusFood).coerceAtMost(999_999_999L),
            timber = (empire.timber + bonusTimber).coerceAtMost(999_999_999L),
            stone = (empire.stone + bonusStone).coerceAtMost(999_999_999L),
            gold = (empire.gold + bonusGold).coerceAtMost(999_999_999L),
            lastHarvestEpochMs = now
        )
        empireFlow.value = updated
        return Result.success(ResourceCost(bonusFood, bonusTimber, bonusStone, bonusGold))
    }

    suspend fun collectSingleResource(
        empire: PlayerEmpire,
        alliance: Alliance?,
        resourceType: ResourceType,
        perResourceLastHarvestMs: Map<ResourceType, Long> = emptyMap()
    ): Result<Long> {
        val now = System.currentTimeMillis()
        val pending = GameCatalog.accruedForResource(empire, alliance, resourceType, now, perResourceLastHarvestMs)
        val amount = pending.coerceAtLeast(100L)
        val updated = when (resourceType) {
            ResourceType.FOOD -> empire.copy(food = (empire.food + amount).coerceAtMost(999_999_999L))
            ResourceType.TIMBER -> empire.copy(timber = (empire.timber + amount).coerceAtMost(999_999_999L))
            ResourceType.STONE -> empire.copy(stone = (empire.stone + amount).coerceAtMost(999_999_999L))
            ResourceType.GOLD -> empire.copy(gold = (empire.gold + amount).coerceAtMost(999_999_999L))
        }
        empireFlow.value = updated
        return Result.success(amount)
    }

    suspend fun startBuildingUpgrade(empire: PlayerEmpire, type: BuildingType): Result<Unit> {
        if (empire.activeBuildKey.isNotBlank()) {
            return Result.failure(IllegalStateException("A construction queue is already active."))
        }
        val level = empire.buildingLevel(type)
        if (level >= 25L) {
            return Result.failure(IllegalStateException("${type.displayName} is already at max level 25."))
        }
        val cost = GameCatalog.upgradeCost(type, level)
        if (!cost.canAfford(empire)) {
            return Result.failure(IllegalStateException("Insufficient resources for upgrade."))
        }
        val endsAt = System.currentTimeMillis() + GameCatalog.upgradeDurationMs(type, level)
        empireFlow.value = empire.copy(
            food = empire.food - cost.food,
            timber = empire.timber - cost.timber,
            stone = empire.stone - cost.stone,
            gold = empire.gold - cost.gold,
            activeBuildKey = type.name,
            activeBuildEndsAtMs = endsAt
        )
        return Result.success(Unit)
    }

    suspend fun completeBuildingUpgrade(empire: PlayerEmpire, useSpeedup: Boolean): Result<String> {
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
        val leveled = when (type) {
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
        }.copy(
            speedups = nextSpeedups,
            gold = nextGold,
            activeBuildKey = "",
            activeBuildEndsAtMs = 0L
        )
        val finalized = leveled.copy(power = leveled.computeTotalPower())
        empireFlow.value = finalized
        syncKingdom(finalized)
        return Result.success("${type.displayName} reached Level $newLevel!")
    }

    suspend fun startTroopTraining(empire: PlayerEmpire, type: TroopType, qty: Long): Result<Unit> {
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
        empireFlow.value = empire.copy(
            food = empire.food - cost.food,
            timber = empire.timber - cost.timber,
            stone = empire.stone - cost.stone,
            gold = empire.gold - cost.gold,
            activeTrainKey = type.name,
            activeTrainQty = clampedQty,
            activeTrainEndsAtMs = endsAt
        )
        return Result.success(Unit)
    }

    suspend fun completeTroopTraining(empire: PlayerEmpire, useSpeedup: Boolean): Result<String> {
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
        val trained = when (type) {
            TroopType.INFANTRY -> empire.copy(infantry = newCount)
            TroopType.CAVALRY -> empire.copy(cavalry = newCount)
            TroopType.ARCHERS -> empire.copy(archers = newCount)
            TroopType.SIEGE -> empire.copy(siege = newCount)
        }.copy(
            speedups = nextSpeedups,
            gold = nextGold,
            activeTrainKey = "",
            activeTrainQty = 0L,
            activeTrainEndsAtMs = 0L
        )
        val finalized = trained.copy(power = trained.computeTotalPower())
        empireFlow.value = finalized
        syncKingdom(finalized)
        return Result.success("+$qty ${type.displayName} joined your garrison!")
    }

    suspend fun renameLord(newName: String): Result<Unit> {
        val clean = newName.trim()
        if (clean.length !in 2..28) {
            return Result.failure(IllegalArgumentException("Lord name must be 2 to 28 characters."))
        }
        empireFlow.value?.let { current ->
            val updated = current.copy(lordName = clean)
            empireFlow.value = updated
            syncKingdom(updated)
        }
        return Result.success(Unit)
    }

    suspend fun activatePeaceShield(empire: PlayerEmpire): Result<Unit> {
        val shieldCostGold = 250L
        if (empire.gold < shieldCostGold) {
            return Result.failure(IllegalStateException("Need $shieldCostGold Gold for an 8h Peace Shield."))
        }
        val until = System.currentTimeMillis() + 8 * 3_600_000L
        val updated = empire.copy(
            gold = empire.gold - shieldCostGold,
            shieldUntilEpochMs = maxOf(empire.shieldUntilEpochMs, System.currentTimeMillis()) + 8 * 3_600_000L
        )
        empireFlow.value = updated.copy(shieldUntilEpochMs = until)
        syncKingdom(empireFlow.value!!)
        return Result.success(Unit)
    }

    suspend fun relocateKingdom(empire: PlayerEmpire, newX: Long, newY: Long): Result<Unit> {
        val moveCostGold = 150L
        if (empire.gold < moveCostGold) {
            return Result.failure(IllegalStateException("Need $moveCostGold Gold to relocate your Keep."))
        }
        val cx = newX.coerceIn(0L, 99L)
        val cy = newY.coerceIn(0L, 99L)
        val updated = empire.copy(
            gold = empire.gold - moveCostGold,
            coordX = cx,
            coordY = cy
        )
        empireFlow.value = updated
        syncKingdom(updated)
        return Result.success(Unit)
    }
}
