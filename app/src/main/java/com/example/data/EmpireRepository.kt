package com.example.data

import android.util.Log
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Empire data now lives in Supabase. Every action runs as a server function
 * (see part 4 SQL), so the server checks costs and timers — the app can't cheat.
 */
class EmpireRepository(
    @Suppress("unused") private val db: Any? = null,
    @Suppress("unused") private val auth: Any? = null
) {
    private val client get() = SupabaseProvider.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val empireFlow = MutableStateFlow<PlayerEmpire?>(null)
    private val kingdomsFlow = MutableStateFlow<List<RealmKingdom>>(emptyList())
    private var homeRealmId: String? = null
    private var viewedRealmId: String? = null

    /** Which realm the map shows; null means home. */
    fun setViewedRealm(realmId: String?) {
        if (realmId == viewedRealmId) return
        viewedRealmId = realmId
        kingdomsFlow.value = if (realmId == null || realmId == homeRealmId) {
            listOfNotNull(lastOwnRow?.toKingdom())
        } else emptyList()
        scope.launch { runCatching { refreshKingdoms() } }
    }
    private var pollJob: Job? = null

    fun observeEmpire(userId: String): Flow<PlayerEmpire?> = empireFlow

    fun observeRealmKingdoms(): Flow<List<RealmKingdom>> = kingdomsFlow

    // ---------------------------------------------------------------- setup

    suspend fun ensureEmpireInitialized(displayName: String?): Result<Unit> = supabaseResult {
        val name = displayName?.trim()?.takeIf { it.length >= 2 }?.take(28) ?: "Lord"
        val row = callPlayerFunction("create_player", buildJsonObject { put("p_username", name) })
        publish(row)
        startPolling()
        runCatching { refreshKingdoms() }
            .onFailure { Log.w(TAG, "Initial kingdom load failed", it) }
        Unit
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                withTimeoutOrNull(15_000L) { GameSignals.refresh.first() }
                runCatching { refreshOwnEmpire() }.onFailure { Log.w(TAG, "Empire refresh failed", it) }
                runCatching { refreshKingdoms() }.onFailure { Log.w(TAG, "Kingdom refresh failed", it) }
            }
        }
    }

    private suspend fun refreshOwnEmpire() {
        val uid = client.auth.currentUserOrNull()?.id ?: return
        val row = client.from("players")
            .select { filter { eq("id", uid) } }
            .decodeSingleOrNull<PlayerRow>() ?: return
        publish(row)
    }

    private suspend fun refreshKingdoms() {
        val realm = viewedRealmId ?: homeRealmId ?: return
        val atHome = realm == homeRealmId
        val rows = client.from("players")
            .select {
                filter { eq("home_realm_id", realm) }
                order("power", Order.DESCENDING)
                limit(200)
            }
            .decodeList<PlayerRow>()
        val me = empireFlow.value?.userId
        val others = rows.filter { it.id != me }.map { it.toKingdom() }
        if (realm != (viewedRealmId ?: homeRealmId)) return   // the player switched realms meanwhile
        val mine = if (atHome) lastOwnRow?.toKingdom() else null
        kingdomsFlow.value = (listOfNotNull(mine) + others).sortedByDescending { it.power }
    }

    private var lastOwnRow: PlayerRow? = null

    private fun publish(row: PlayerRow) {
        homeRealmId = row.homeRealmId
        lastOwnRow = row
        empireFlow.value = row.toEmpire()
        // Always keep our own castle on the map, even before the realm list loads.
        if (viewedRealmId != null && viewedRealmId != row.homeRealmId) return
        val others = kingdomsFlow.value.filter { it.ownerUid != row.id }
        kingdomsFlow.value = (others + row.toKingdom()).sortedByDescending { it.power }
    }

    private suspend fun callPlayerFunction(name: String, params: JsonObject = JsonObject(emptyMap())): PlayerRow =
        client.postgrest.rpc(name, params).decodeAs<PlayerRow>()

    /** Runs a server action, stores the new empire state and refreshes the map in the background. */
    private suspend fun act(name: String, params: JsonObject = JsonObject(emptyMap())): PlayerEmpire {
        val row = callPlayerFunction(name, params)
        publish(row)
        scope.launch { runCatching { refreshKingdoms() } }
        return row.toEmpire()
    }

    // ---------------------------------------------------------------- resources

    suspend fun collectResources(
        empire: PlayerEmpire,
        alliance: Alliance?,
        perResourceLastHarvestMs: Map<ResourceType, Long> = emptyMap()
    ): Result<ResourceCost> = supabaseResult {
        val before = empireFlow.value ?: empire
        val after = act("collect_resources")
        ResourceCost(
            food = (after.food - before.food).coerceAtLeast(0L),
            timber = (after.timber - before.timber).coerceAtLeast(0L),
            stone = (after.stone - before.stone).coerceAtLeast(0L),
            gold = (after.gold - before.gold).coerceAtLeast(0L)
        )
    }

    suspend fun collectSingleResource(
        empire: PlayerEmpire,
        alliance: Alliance?,
        resourceType: ResourceType,
        perResourceLastHarvestMs: Map<ResourceType, Long> = emptyMap()
    ): Result<Long> = supabaseResult {
        val before = empireFlow.value ?: empire
        val after = act("collect_resources", buildJsonObject { put("p_resource", resourceType.name) })
        val gained = when (resourceType) {
            ResourceType.FOOD -> after.food - before.food
            ResourceType.TIMBER -> after.timber - before.timber
            ResourceType.STONE -> after.stone - before.stone
            ResourceType.GOLD -> after.gold - before.gold
        }
        gained.coerceAtLeast(0L)
    }

    // ---------------------------------------------------------------- buildings

    suspend fun startBuildingUpgrade(empire: PlayerEmpire, type: BuildingType): Result<Unit> = supabaseResult {
        act("start_building_upgrade", buildJsonObject { put("p_building", type.name) })
        Unit
    }

    suspend fun completeBuildingUpgrade(empire: PlayerEmpire, useSpeedup: Boolean): Result<String> = supabaseResult {
        val type = BuildingType.fromRaw((empireFlow.value ?: empire).activeBuildKey)
            ?: throw IllegalStateException("No active construction.")
        val after = act("complete_building_upgrade", buildJsonObject { put("p_use_speedup", useSpeedup) })
        "${type.displayName} reached Level ${after.buildingLevel(type)}!"
    }

    // ---------------------------------------------------------------- troops

    suspend fun startTroopTraining(empire: PlayerEmpire, type: TroopType, qty: Long): Result<Unit> = supabaseResult {
        act(
            "start_troop_training",
            buildJsonObject {
                put("p_troop", type.name)
                put("p_qty", qty.coerceIn(1L, 500L).toInt())
            }
        )
        Unit
    }

    suspend fun completeTroopTraining(empire: PlayerEmpire, useSpeedup: Boolean): Result<String> = supabaseResult {
        val current = empireFlow.value ?: empire
        val type = TroopType.fromRaw(current.activeTrainKey)
            ?: throw IllegalStateException("No active training queue.")
        val qty = current.activeTrainQty
        act("complete_troop_training", buildJsonObject { put("p_use_speedup", useSpeedup) })
        "+$qty ${type.displayName} joined your garrison!"
    }

    // ---------------------------------------------------------------- lord & keep

    suspend fun renameLord(newName: String): Result<Unit> = supabaseResult {
        act("rename_lord", buildJsonObject { put("p_name", newName.trim()) })
        Unit
    }

    suspend fun activatePeaceShield(empire: PlayerEmpire): Result<Unit> = supabaseResult {
        act("activate_peace_shield")
        Unit
    }

    suspend fun relocateKingdom(empire: PlayerEmpire, newX: Long, newY: Long): Result<Unit> = supabaseResult {
        act(
            "relocate_kingdom",
            buildJsonObject {
                put("p_x", (newX.coerceIn(0L, 99L) * SERVER_MAP_SCALE).toInt())
                put("p_y", (newY.coerceIn(0L, 99L) * SERVER_MAP_SCALE).toInt())
            }
        )
        Unit
    }

    private companion object {
        const val TAG = "EmpireRepository"
    }
}
