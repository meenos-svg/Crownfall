package com.example.data

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToLong

enum class ResourceType(val displayName: String, val shortLabel: String) {
    FOOD("Food", "FOOD"),
    TIMBER("Timber", "TIMBER"),
    STONE("Stone", "STONE"),
    GOLD("Gold", "GOLD");

    companion object {
        fun fromRaw(raw: String): ResourceType =
            entries.firstOrNull { it.name == raw } ?: FOOD
    }
}

enum class BuildingCategory(val label: String) {
    CORE_MILITARY("Core & Military"),
    ECONOMY("Economy & Tribute")
}

enum class BuildingType(
    val firestoreField: String,
    val displayName: String,
    val subtitle: String,
    val category: BuildingCategory,
    val baseFood: Long,
    val baseTimber: Long,
    val baseStone: Long,
    val baseGold: Long,
    val baseDurationSec: Long
) {
    KEEP(
        "keepLevel", "Obsidian Keep", "Heart of your dominion. Unlocks higher structure tiers & garrison strength.",
        BuildingCategory.CORE_MILITARY, 320, 320, 280, 150, 20
    ),
    BARRACKS(
        "barracksLevel", "War Barracks", "Trains Oathsworn Infantry who counter Cavalry charges.",
        BuildingCategory.CORE_MILITARY, 240, 220, 180, 90, 15
    ),
    STABLES(
        "stablesLevel", "Dread Stables", "Trains Dread Lancers who devastate Archer lines.",
        BuildingCategory.CORE_MILITARY, 260, 240, 160, 110, 16
    ),
    ARCHERY(
        "archeryLevel", "Ashbow Range", "Trains Ashbowmen who rain arrows upon Infantry.",
        BuildingCategory.CORE_MILITARY, 220, 260, 160, 100, 15
    ),
    SIEGE(
        "siegeLevel", "Siege Foundry", "Forges Iron Trebuchets that shatter Keep fortifications.",
        BuildingCategory.CORE_MILITARY, 240, 300, 260, 160, 22
    ),
    ALLIANCE_HALL(
        "allianceHallLevel", "Alliance Hall", "Expands rally capacity and war council diplomacy.",
        BuildingCategory.CORE_MILITARY, 200, 240, 220, 140, 18
    ),
    FARM(
        "farmLevel", "Ashen Farmstead", "Produces hourly Food rations to sustain marches and armies.",
        BuildingCategory.ECONOMY, 120, 180, 120, 40, 12
    ),
    LUMBER(
        "lumberLevel", "Timber Mill", "Harvests darkwood Timber for siegecraft and towers.",
        BuildingCategory.ECONOMY, 160, 120, 140, 40, 12
    ),
    QUARRY(
        "quarryLevel", "Basalt Quarry", "Extracts obsidian Stone for fortresses and walls.",
        BuildingCategory.ECONOMY, 180, 180, 120, 60, 14
    ),
    MINT(
        "mintLevel", "Royal Gold Mint", "Smelts Crown Gold for elite troops and alliance tech.",
        BuildingCategory.ECONOMY, 200, 200, 180, 80, 16
    );

    companion object {
        fun fromRaw(raw: String): BuildingType? =
            entries.firstOrNull { it.name == raw }
    }
}

enum class TroopType(
    val firestoreField: String,
    val displayName: String,
    val counters: String,
    val building: BuildingType,
    val power: Long,
    val foodCost: Long,
    val timberCost: Long,
    val stoneCost: Long,
    val goldCost: Long,
    val trainSecPerUnit: Long
) {
    INFANTRY(
        "infantry", "Oathsworn Infantry", "Strong vs Cavalry",
        BuildingType.BARRACKS, 10L, 12L, 8L, 4L, 3L, 1L
    ),
    CAVALRY(
        "cavalry", "Dread Lancers", "Strong vs Archers",
        BuildingType.STABLES, 16L, 18L, 10L, 6L, 8L, 1L
    ),
    ARCHERS(
        "archers", "Ashbowmen", "Strong vs Infantry",
        BuildingType.ARCHERY, 12L, 10L, 14L, 4L, 5L, 1L
    ),
    SIEGE(
        "siege", "Iron Trebuchets", "Bonus vs Keeps & Bastions",
        BuildingType.SIEGE, 30L, 14L, 28L, 24L, 16L, 2L
    );

    companion object {
        fun fromRaw(raw: String): TroopType? =
            entries.firstOrNull { it.name == raw }
    }
}

enum class AllianceTechType(
    val firestoreField: String,
    val displayName: String,
    val description: String,
    val bonusPerLevelPercent: Int
) {
    MARCH_SPEED(
        "techMarchLevel", "Warhorn Logistics",
        "Reduces army march travel time across the realm by 5% per level.", 5
    ),
    RESOURCE_YIELD(
        "techYieldLevel", "Imperial Harvest",
        "Boosts Citadel passive resource production by 8% per level.", 8
    ),
    RALLY_ATTACK(
        "techAttackLevel", "Crimson Vanguard",
        "Increases Raid & Alliance Rally combat power by 6% per level.", 6
    ),
    CITADEL_DEFENSE(
        "techDefenseLevel", "Obsidian Ramparts",
        "Fortifies Citadel garrison defense power by 6% per level.", 6
    )
}

data class ResourceCost(
    val food: Long = 0L,
    val timber: Long = 0L,
    val stone: Long = 0L,
    val gold: Long = 0L
) {
    fun canAfford(empire: PlayerEmpire): Boolean =
        empire.food >= food && empire.timber >= timber && empire.stone >= stone && empire.gold >= gold
}

data class WorldNpcNode(
    val id: String,
    val name: String,
    val targetType: String, // "BASTION" or "RESOURCE"
    val level: Int,
    val coordX: Long,
    val coordY: Long,
    val garrisonPower: Long,
    val resourceType: ResourceType? = null,
    val rewardFood: Long = 0L,
    val rewardTimber: Long = 0L,
    val rewardStone: Long = 0L,
    val rewardGold: Long = 0L
)

data class CombatOutcome(
    val victory: Boolean,
    val effectiveAttackerPower: Long,
    val defenderPower: Long,
    val troopsLost: Long,
    val lootFood: Long,
    val lootTimber: Long,
    val lootStone: Long,
    val lootGold: Long,
    val honorGained: Long
)

object GameCatalog {
    fun upgradeCost(type: BuildingType, currentLevel: Long): ResourceCost {
        val factor = 1.0 + (currentLevel - 1) * 0.55
        return ResourceCost(
            food = (type.baseFood * factor).roundToLong(),
            timber = (type.baseTimber * factor).roundToLong(),
            stone = (type.baseStone * factor).roundToLong(),
            gold = (type.baseGold * factor).roundToLong()
        )
    }

    fun upgradeDurationMs(type: BuildingType, currentLevel: Long): Long {
        val sec = (type.baseDurationSec * (1.0 + (currentLevel - 1) * 0.45)).roundToLong()
            .coerceIn(10L, 180L)
        return sec * 1000L
    }

    fun trainingCost(type: TroopType, qty: Long): ResourceCost = ResourceCost(
        food = type.foodCost * qty,
        timber = type.timberCost * qty,
        stone = type.stoneCost * qty,
        gold = type.goldCost * qty
    )

    fun trainingDurationMs(type: TroopType, qty: Long, buildingLevel: Long): Long {
        val speedFactor = (1.0 - (buildingLevel - 1) * 0.04).coerceAtLeast(0.45)
        val rawSec = (qty * type.trainSecPerUnit * 0.65 * speedFactor).roundToLong().coerceIn(8L, 120L)
        return rawSec * 1000L
    }

    fun hourlyRates(empire: PlayerEmpire, alliance: Alliance?): ResourceCost {
        val yieldMultiplier = 1.0 + ((alliance?.techYieldLevel ?: 0L) * 0.08)
        return ResourceCost(
            food = ((360L + empire.farmLevel * 220L) * yieldMultiplier).roundToLong(),
            timber = ((340L + empire.lumberLevel * 210L) * yieldMultiplier).roundToLong(),
            stone = ((280L + empire.quarryLevel * 185L) * yieldMultiplier).roundToLong(),
            gold = ((180L + empire.mintLevel * 135L) * yieldMultiplier).roundToLong()
        )
    }

    fun resourceTypeFor(building: BuildingType): ResourceType? = when (building) {
        BuildingType.FARM -> ResourceType.FOOD
        BuildingType.LUMBER -> ResourceType.TIMBER
        BuildingType.QUARRY -> ResourceType.STONE
        BuildingType.MINT -> ResourceType.GOLD
        else -> null
    }

    fun economyBuildingFor(resourceType: ResourceType): BuildingType = when (resourceType) {
        ResourceType.FOOD -> BuildingType.FARM
        ResourceType.TIMBER -> BuildingType.LUMBER
        ResourceType.STONE -> BuildingType.QUARRY
        ResourceType.GOLD -> BuildingType.MINT
    }

    fun troopTypeFor(building: BuildingType): TroopType? = when (building) {
        BuildingType.BARRACKS -> TroopType.INFANTRY
        BuildingType.STABLES -> TroopType.CAVALRY
        BuildingType.ARCHERY -> TroopType.ARCHERS
        BuildingType.SIEGE -> TroopType.SIEGE
        else -> null
    }

    fun canUpgradeBuilding(empire: PlayerEmpire, type: BuildingType): Boolean {
        if (empire.activeBuildKey.isNotBlank()) return false
        val level = empire.buildingLevel(type)
        if (level >= 25L) return false
        if (type != BuildingType.KEEP && level >= empire.keepLevel + 2L) return false
        return upgradeCost(type, level).canAfford(empire)
    }

    fun hourlyRateFor(empire: PlayerEmpire, alliance: Alliance?, resourceType: ResourceType): Long {
        val rates = hourlyRates(empire, alliance)
        return when (resourceType) {
            ResourceType.FOOD -> rates.food
            ResourceType.TIMBER -> rates.timber
            ResourceType.STONE -> rates.stone
            ResourceType.GOLD -> rates.gold
        }
    }

    fun accruedForResource(
        empire: PlayerEmpire,
        alliance: Alliance?,
        resourceType: ResourceType,
        nowEpochMs: Long,
        perResourceLastHarvestMs: Map<ResourceType, Long> = emptyMap()
    ): Long {
        val baseLastMs = if (empire.lastHarvestEpochMs <= 0L) nowEpochMs else empire.lastHarvestEpochMs
        val overrideMs = perResourceLastHarvestMs[resourceType] ?: 0L
        val effectiveLastMs = max(baseLastMs, overrideMs)
        val elapsedSec = ((nowEpochMs - effectiveLastMs).coerceAtLeast(0L) / 1000.0).coerceAtMost(8.0 * 3600.0)
        val hourlyRate = hourlyRateFor(empire, alliance, resourceType)
        // Accelerated gameplay rate (x10 real-time feel so resources visibly tick every second)
        val mult = (elapsedSec / 3600.0) * 10.0
        return (hourlyRate * mult).roundToLong()
    }

    fun accruedPendingResources(
        empire: PlayerEmpire,
        alliance: Alliance?,
        nowEpochMs: Long,
        perResourceLastHarvestMs: Map<ResourceType, Long> = emptyMap()
    ): ResourceCost = ResourceCost(
        food = accruedForResource(empire, alliance, ResourceType.FOOD, nowEpochMs, perResourceLastHarvestMs),
        timber = accruedForResource(empire, alliance, ResourceType.TIMBER, nowEpochMs, perResourceLastHarvestMs),
        stone = accruedForResource(empire, alliance, ResourceType.STONE, nowEpochMs, perResourceLastHarvestMs),
        gold = accruedForResource(empire, alliance, ResourceType.GOLD, nowEpochMs, perResourceLastHarvestMs)
    )

    fun marchDurationMs(
        fromX: Long,
        fromY: Long,
        toX: Long,
        toY: Long,
        alliance: Alliance?
    ): Long {
        val dist = hypot((toX - fromX).toDouble(), (toY - fromY).toDouble())
        val speedBonus = 1.0 - ((alliance?.techMarchLevel ?: 0L) * 0.05).coerceAtMost(0.5)
        val seconds = ((10.0 + dist * 0.45) * speedBonus).roundToLong().coerceIn(8L, 45L)
        return seconds * 1000L
    }

    fun techUpgradeCostXp(currentLevel: Long): Long = 200L + currentLevel * 250L

    fun resolveCombat(
        attackerPower: Long,
        siegeSent: Long,
        defenderPower: Long,
        targetType: String,
        alliance: Alliance?,
        npcNode: WorldNpcNode? = null
    ): CombatOutcome {
        val attackBonus = 1.0 + ((alliance?.techAttackLevel ?: 0L) * 0.06)
        val siegeBonus = if (targetType == "BASTION" || targetType == "KINGDOM") siegeSent * 12L else 0L
        val effectiveAttacker = ((attackerPower + siegeBonus) * attackBonus).roundToLong()

        if (targetType == "RESOURCE" && npcNode != null) {
            return CombatOutcome(
                victory = true,
                effectiveAttackerPower = effectiveAttacker,
                defenderPower = 0L,
                troopsLost = 0L,
                lootFood = npcNode.rewardFood,
                lootTimber = npcNode.rewardTimber,
                lootStone = npcNode.rewardStone,
                lootGold = npcNode.rewardGold,
                honorGained = 25L
            )
        }

        val victory = effectiveAttacker >= defenderPower
        val ratio = (defenderPower.toDouble() / max(1L, effectiveAttacker).toDouble()).coerceIn(0.08, 1.4)
        val totalUnitsApprox = max(10L, attackerPower / 14L)
        val lossRate = if (victory) (ratio * 0.14).coerceIn(0.03, 0.22) else (0.28 + ratio * 0.12).coerceAtMost(0.45)
        val troopsLost = (totalUnitsApprox * lossRate).roundToLong().coerceAtLeast(1L)

        return if (victory) {
            val baseFood = npcNode?.rewardFood ?: (350L + defenderPower / 5L)
            val baseTimber = npcNode?.rewardTimber ?: (300L + defenderPower / 6L)
            val baseStone = npcNode?.rewardStone ?: (220L + defenderPower / 7L)
            val baseGold = npcNode?.rewardGold ?: (160L + defenderPower / 8L)
            CombatOutcome(
                victory = true,
                effectiveAttackerPower = effectiveAttacker,
                defenderPower = defenderPower,
                troopsLost = troopsLost,
                lootFood = baseFood,
                lootTimber = baseTimber,
                lootStone = baseStone,
                lootGold = baseGold,
                honorGained = if (targetType == "BASTION") 80L + (npcNode?.level ?: 1) * 40L else 120L
            )
        } else {
            CombatOutcome(
                victory = false,
                effectiveAttackerPower = effectiveAttacker,
                defenderPower = defenderPower,
                troopsLost = troopsLost,
                lootFood = 0L,
                lootTimber = 0L,
                lootStone = 0L,
                lootGold = 0L,
                honorGained = 15L
            )
        }
    }

    fun spawnCoordinateForUid(uid: String): Pair<Long, Long> {
        val h1 = abs(uid.hashCode())
        val h2 = abs((uid + "_crownfall").hashCode())
        val x = 14L + (h1 % 72)
        val y = 14L + (h2 % 72)
        return x to y
    }

    val worldNpcNodes: List<WorldNpcNode> = listOf(
        // 8 Shadow Bastions (PvE Raids & Alliance Rallies)
        WorldNpcNode("bastion_1", "Ashen Watchtower", "BASTION", 1, 28, 34, 850, null, 600, 500, 400, 260),
        WorldNpcNode("bastion_2", "Ironfang Outpost", "BASTION", 2, 66, 26, 1650, null, 950, 820, 680, 420),
        WorldNpcNode("bastion_3", "Crimson Redoubt", "BASTION", 3, 44, 62, 2800, null, 1450, 1250, 1050, 680),
        WorldNpcNode("bastion_4", "Dreadspire Keep", "BASTION", 4, 74, 58, 4500, null, 2100, 1850, 1600, 1050),
        WorldNpcNode("bastion_5", "Obsidian Citadel", "BASTION", 5, 50, 50, 7200, null, 3200, 2800, 2500, 1800),
        WorldNpcNode("bastion_6", "Blighted Fortress", "BASTION", 2, 22, 72, 1800, null, 1000, 900, 740, 450),
        WorldNpcNode("bastion_7", "Gilded Bastion", "BASTION", 3, 80, 78, 3100, null, 1500, 1300, 1100, 950),
        WorldNpcNode("bastion_8", "Shattered Crown Fort", "BASTION", 4, 30, 18, 5200, null, 2400, 2100, 1900, 1300),

        // 12 Rich Realm Resource Nodes (Gathering Expeditions)
        WorldNpcNode("res_food_1", "Golden Grain Vale", "RESOURCE", 2, 36, 42, 0, ResourceType.FOOD, rewardFood = 950),
        WorldNpcNode("res_food_2", "Riverbend Pastures", "RESOURCE", 3, 58, 70, 0, ResourceType.FOOD, rewardFood = 1400),
        WorldNpcNode("res_food_3", "Sunfall Orchards", "RESOURCE", 1, 20, 48, 0, ResourceType.FOOD, rewardFood = 650),
        WorldNpcNode("res_timber_1", "Darkwood Glade", "RESOURCE", 2, 60, 40, 0, ResourceType.TIMBER, rewardTimber = 950),
        WorldNpcNode("res_timber_2", "Elder Pine Ridge", "RESOURCE", 3, 26, 56, 0, ResourceType.TIMBER, rewardTimber = 1400),
        WorldNpcNode("res_timber_3", "Thornbark Thicket", "RESOURCE", 1, 76, 36, 0, ResourceType.TIMBER, rewardTimber = 650),
        WorldNpcNode("res_stone_1", "Basalt Crag Vein", "RESOURCE", 2, 48, 28, 0, ResourceType.STONE, rewardStone = 850),
        WorldNpcNode("res_stone_2", "Obsidian Quarry", "RESOURCE", 3, 68, 66, 0, ResourceType.STONE, rewardStone = 1250),
        WorldNpcNode("res_stone_3", "Granite Chasm", "RESOURCE", 1, 34, 78, 0, ResourceType.STONE, rewardStone = 600),
        WorldNpcNode("res_gold_1", "Sovereign Gold Seam", "RESOURCE", 3, 52, 46, 0, ResourceType.GOLD, rewardGold = 750),
        WorldNpcNode("res_gold_2", "Ember Vault Mine", "RESOURCE", 2, 40, 20, 0, ResourceType.GOLD, rewardGold = 520),
        WorldNpcNode("res_gold_3", "Crownfall Aureate Pit", "RESOURCE", 4, 82, 50, 0, ResourceType.GOLD, rewardGold = 1100)
    )
}

fun formatCompact(value: Long): String = when {
    value >= 1_000_000L -> String.format("%.1fM", value / 1_000_000.0)
    value >= 10_000L -> String.format("%.1fK", value / 1_000.0)
    value >= 1_000L -> String.format("%,d", value)
    else -> value.toString()
}

fun formatCountdown(remainingMs: Long): String {
    val totalSec = (remainingMs / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0L) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}
