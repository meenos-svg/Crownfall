package com.example.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/** Realm maps on the server are 1400x1400; the current map screen draws 0..99. */
const val SERVER_MAP_SCALE = 14

/** One row of the Supabase `players` table. */
@Serializable
data class PlayerRow(
    val id: String = "",
    val username: String = "",
    @SerialName("home_realm_id") val homeRealmId: String = "",
    @SerialName("pos_x") val posX: Int = 0,
    @SerialName("pos_y") val posY: Int = 0,
    val food: Long = 0,
    val timber: Long = 0,
    val stone: Long = 0,
    val gold: Long = 0,
    val speedups: Int = 0,
    @SerialName("last_harvest_at") val lastHarvestAt: String? = null,
    @SerialName("keep_level") val keepLevel: Int = 1,
    @SerialName("barracks_level") val barracksLevel: Int = 1,
    @SerialName("stables_level") val stablesLevel: Int = 1,
    @SerialName("archery_level") val archeryLevel: Int = 1,
    @SerialName("siege_level") val siegeLevel: Int = 1,
    @SerialName("alliance_hall_level") val allianceHallLevel: Int = 1,
    @SerialName("farm_level") val farmLevel: Int = 1,
    @SerialName("lumber_level") val lumberLevel: Int = 1,
    @SerialName("quarry_level") val quarryLevel: Int = 1,
    @SerialName("mint_level") val mintLevel: Int = 1,
    @SerialName("waygate_level") val waygateLevel: Int = 1,
    val infantry: Long = 0,
    val cavalry: Long = 0,
    val archers: Long = 0,
    val siege: Long = 0,
    @SerialName("build_key") val buildKey: String? = null,
    @SerialName("build_ends_at") val buildEndsAt: String? = null,
    @SerialName("train_key") val trainKey: String? = null,
    @SerialName("train_qty") val trainQty: Int? = null,
    @SerialName("train_ends_at") val trainEndsAt: String? = null,
    val power: Long = 0,
    val honor: Long = 0,
    @SerialName("shield_until") val shieldUntil: String? = null,
    @SerialName("alliance_id") val allianceId: String? = null,
    @SerialName("alliance_role") val allianceRole: String = "NONE",
    @SerialName("is_bot") val isBot: Boolean = false
)

fun String?.toEpochMs(): Long =
    if (this.isNullOrBlank()) 0L
    else runCatching { OffsetDateTime.parse(this).toInstant().toEpochMilli() }.getOrDefault(0L)

fun PlayerRow.toEmpire(): PlayerEmpire = PlayerEmpire(
    id = id,
    userId = id,
    lordName = username,
    food = food,
    timber = timber,
    stone = stone,
    gold = gold,
    speedups = speedups.toLong(),
    lastHarvestEpochMs = lastHarvestAt.toEpochMs(),
    coordX = (posX / SERVER_MAP_SCALE).toLong(),
    coordY = (posY / SERVER_MAP_SCALE).toLong(),
    power = power,
    keepLevel = keepLevel.toLong(),
    barracksLevel = barracksLevel.toLong(),
    stablesLevel = stablesLevel.toLong(),
    archeryLevel = archeryLevel.toLong(),
    siegeLevel = siegeLevel.toLong(),
    allianceHallLevel = allianceHallLevel.toLong(),
    farmLevel = farmLevel.toLong(),
    lumberLevel = lumberLevel.toLong(),
    quarryLevel = quarryLevel.toLong(),
    mintLevel = mintLevel.toLong(),
    infantry = infantry,
    cavalry = cavalry,
    archers = archers,
    siege = siege,
    activeBuildKey = buildKey.orEmpty(),
    activeBuildEndsAtMs = buildEndsAt.toEpochMs(),
    activeTrainKey = trainKey.orEmpty(),
    activeTrainQty = (trainQty ?: 0).toLong(),
    activeTrainEndsAtMs = trainEndsAt.toEpochMs(),
    allianceId = allianceId.orEmpty(),
    allianceTag = "",
    allianceRole = allianceRole,
    honor = honor,
    shieldUntilEpochMs = shieldUntil.toEpochMs(),
    homeRealmId = homeRealmId
)

fun PlayerRow.toKingdom(): RealmKingdom = RealmKingdom(
    id = id,
    ownerUid = id,
    realmId = homeRealmId,
    publicVisible = true,
    lordName = username,
    keepLevel = keepLevel.toLong(),
    power = power,
    garrisonPower = infantry * TroopType.INFANTRY.power +
        cavalry * TroopType.CAVALRY.power +
        archers * TroopType.ARCHERS.power +
        siege * TroopType.SIEGE.power,
    coordX = (posX / SERVER_MAP_SCALE).toLong(),
    coordY = (posY / SERVER_MAP_SCALE).toLong(),
    allianceId = allianceId.orEmpty(),
    allianceTag = "",
    shieldUntilEpochMs = shieldUntil.toEpochMs(),
    isBot = isBot
)
