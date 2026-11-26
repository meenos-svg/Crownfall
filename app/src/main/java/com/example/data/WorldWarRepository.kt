package com.example.data

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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

@Serializable
private data class MarchOwner(
    val username: String = "",
    @SerialName("alliance_id") val allianceId: String? = null
)

@Serializable
private data class MarchRow(
    val id: String = "",
    @SerialName("player_id") val playerId: String = "",
    @SerialName("realm_id") val realmId: String = "",
    @SerialName("march_type") val marchType: String = "RAID",
    @SerialName("target_type") val targetType: String = "BASTION",
    @SerialName("target_id") val targetId: String? = null,
    @SerialName("rally_id") val rallyId: String? = null,
    @SerialName("start_x") val startX: Int = 0,
    @SerialName("start_y") val startY: Int = 0,
    @SerialName("target_x") val targetX: Int = 0,
    @SerialName("target_y") val targetY: Int = 0,
    @SerialName("departed_at") val departedAt: String? = null,
    @SerialName("arrives_at") val arrivesAt: String? = null,
    @SerialName("troops_json") val troops: JsonObject? = null,
    @SerialName("troop_power") val troopPower: Long = 0,
    val status: String = "MARCHING",
    val players: MarchOwner? = null
)

@Serializable
private data class IdName(val id: String = "", val username: String = "")

@Serializable
private data class IdOnly(val id: String = "")

/** The phone's clock said the army had arrived, but the server hasn't reached that moment yet. */
class MarchStillMarching : Exception("Army still on the way")

@Serializable
private data class TagOnly(val tag: String = "")

@Serializable
private data class BattleRow(
    val id: String = "",
    @SerialName("realm_id") val realmId: String = "",
    @SerialName("battle_type") val battleType: String = "RAID",
    @SerialName("attacker_id") val attackerId: String? = null,
    @SerialName("attacker_name") val attackerName: String = "",
    @SerialName("attacker_alliance_id") val attackerAllianceId: String? = null,
    @SerialName("defender_id") val defenderId: String? = null,
    @SerialName("defender_name") val defenderName: String = "",
    @SerialName("defender_kind") val defenderKind: String = "PLAYER",
    val victory: Boolean = false,
    @SerialName("attacker_power") val attackerPower: Long = 0,
    @SerialName("defender_power") val defenderPower: Long = 0,
    @SerialName("attacker_losses") val attackerLosses: Long = 0,
    @SerialName("defender_losses") val defenderLosses: Long = 0,
    @SerialName("loot_food") val lootFood: Long = 0,
    @SerialName("loot_timber") val lootTimber: Long = 0,
    @SerialName("loot_stone") val lootStone: Long = 0,
    @SerialName("loot_gold") val lootGold: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
    val att: TagOnly? = null,
    val def: TagOnly? = null
)

private const val BATTLE_COLUMNS =
    "*, att:alliances!battle_logs_attacker_alliance_id_fkey(tag), def:alliances!battle_logs_defender_alliance_id_fkey(tag)"

/**
 * Marches, raids and battles live in Supabase. The server takes troops when an
 * army leaves, fights the battle when it arrives and brings survivors and loot
 * home. Any player's app asks the server to settle battles that are due, so a
 * raid still lands while the attacker is offline.
 */
class WorldWarRepository(
    @Suppress("unused") private val db: Any? = null,
    @Suppress("unused") private val auth: Any? = null
) {
    private val client get() = SupabaseProvider.client
    private val myUid: String get() = client.auth.currentUserOrNull()?.id.orEmpty()

    fun observeActiveMarches(): Flow<List<WorldMarch>> = pollingFlow(TAG, 5_000L) {
        runCatching { client.postgrest.rpc("resolve_due_battles") }
        val rows = client.from("marches").select(Columns.raw("*, players(username, alliance_id)")) {
            filter { eq("status", "MARCHING") }
            order("arrives_at", Order.ASCENDING)
            limit(200)
        }.decodeList<MarchRow>()

        val kingdomIds = rows.filter { it.targetType == "KINGDOM" }.mapNotNull { it.targetId }.distinct()
        val kingdomNames = if (kingdomIds.isEmpty()) emptyMap() else
            client.from("players").select(Columns.list("id", "username")) {
                filter { isIn("id", kingdomIds) }
            }.decodeList<IdName>().associate { it.id to it.username }

        rows.map { m ->
            val targetId = m.targetId.orEmpty()
            val targetName = when (m.targetType) {
                "KINGDOM" -> kingdomNames[targetId] ?: "Rival Kingdom"
                else -> GameCatalog.worldNpcNodes.firstOrNull { it.id == targetId }?.name ?: "Unknown"
            }
            WorldMarch(
                id = m.id,
                ownerUid = m.playerId,
                realmId = m.realmId,
                ownerName = m.players?.username.orEmpty(),
                allianceId = m.players?.allianceId.orEmpty(),
                allianceTag = "",
                marchType = m.marchType,
                targetType = m.targetType,
                targetId = targetId,
                targetName = targetName,
                fromX = (m.startX / SERVER_MAP_SCALE).toLong(),
                fromY = (m.startY / SERVER_MAP_SCALE).toLong(),
                toX = (m.targetX / SERVER_MAP_SCALE).toLong(),
                toY = (m.targetY / SERVER_MAP_SCALE).toLong(),
                troopPower = m.troopPower,
                infantrySent = m.troops.count("infantry"),
                cavalrySent = m.troops.count("cavalry"),
                archersSent = m.troops.count("archers"),
                siegeSent = m.troops.count("siege"),
                departedAtEpochMs = m.departedAt.toEpochMs(),
                arrivesAtEpochMs = m.arrivesAt.toEpochMs(),
                status = m.status
            )
        }
    }

    fun observeBattleLogs(): Flow<List<BattleLog>> = pollingFlow(TAG, 10_000L) {
        val me = myUid
        client.from("battle_logs").select(Columns.raw(BATTLE_COLUMNS)) {
            order("created_at", Order.DESCENDING)
            limit(50)
        }.decodeList<BattleRow>().map { it.toBattleLog(me) }
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
    ): Result<Unit> = supabaseResult {
        client.postgrest.rpc("dispatch_march", buildJsonObject {
            put("p_type", marchType)
            put("p_target_type", targetType)
            put("p_target_id", targetId)
            put("p_infantry", infantrySent)
            put("p_cavalry", cavalrySent)
            put("p_archers", archersSent)
            put("p_siege", siegeSent)
        })
        GameSignals.poke()
    }

    suspend fun recallMarch(empire: PlayerEmpire, march: WorldMarch): Result<Unit> = supabaseResult {
        client.postgrest.rpc("recall_march", buildJsonObject { put("p_march", march.id) })
        GameSignals.poke()
    }

    /** Asks the server to settle anything due, then returns this march's battle report. */
    suspend fun resolveMarch(
        empire: PlayerEmpire,
        alliance: Alliance?,
        march: WorldMarch,
        targetKingdom: RealmKingdom?
    ): Result<BattleLog> = supabaseResult {
        client.postgrest.rpc("resolve_due_battles")
        GameSignals.poke()
        val row = client.from("battle_logs").select(Columns.raw(BATTLE_COLUMNS)) {
            filter { eq("march_id", march.id) }
            limit(1)
        }.decodeList<BattleRow>().firstOrNull()
        if (row == null) {
            val stillMarching = client.from("marches").select(Columns.list("id")) {
                filter {
                    eq("id", march.id)
                    eq("status", "MARCHING")
                }
            }.decodeList<IdOnly>().isNotEmpty()
            if (stillMarching) throw MarchStillMarching()
            error("Your army reached ${march.targetName} but turned back.")
        }
        row.toBattleLog(myUid)
    }

    private companion object {
        const val TAG = "WorldWarRepository"
    }
}

private fun JsonObject?.count(key: String): Long =
    this?.get(key)?.jsonPrimitive?.longOrNull ?: 0L

/** Reports are written from the attacker's side; a defender sees the battle flipped. */
private fun BattleRow.toBattleLog(me: String): BattleLog {
    val iDefended = me.isNotBlank() && defenderId == me && attackerId != me
    val defenderTag = def?.tag ?: when (defenderKind) {
        "BASTION" -> "NPC"
        "RESOURCE" -> "NODE"
        else -> ""
    }
    return BattleLog(
        id = id,
        realmId = realmId,
        attackerUid = attackerId.orEmpty(),
        attackerName = attackerName,
        attackerTag = att?.tag.orEmpty(),
        defenderUid = defenderId.orEmpty(),
        defenderName = defenderName,
        defenderTag = defenderTag,
        battleType = if (iDefended) "DEFENSE" else battleType,
        victory = if (iDefended) !victory else victory,
        attackerPower = attackerPower,
        defenderPower = defenderPower,
        troopsLost = if (iDefended) defenderLosses else attackerLosses,
        lootFood = if (iDefended) 0L else lootFood,
        lootTimber = if (iDefended) 0L else lootTimber,
        lootStone = if (iDefended) 0L else lootStone,
        lootGold = if (iDefended) 0L else lootGold,
        allianceId = attackerAllianceId.orEmpty(),
        createdAt = createdAt.toTimestamp()
    )
}
