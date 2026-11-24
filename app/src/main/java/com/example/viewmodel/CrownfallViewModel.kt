package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.Alliance
import com.example.data.AllianceInvite
import com.example.data.AllianceMessage
import com.example.data.AllianceRally
import com.example.data.AllianceRepository
import com.example.data.AllianceTechType
import com.example.data.BattleLog
import com.example.data.BuildingType
import com.example.data.EmpireRepository
import com.example.data.PlayerEmpire
import com.example.data.RealmKingdom
import com.example.data.ResourceAidRequest
import com.example.data.ResourceCost
import com.example.data.ResourceType
import com.example.data.TroopType
import com.example.data.UiState
import com.example.data.WorldMarch
import com.example.data.WorldWarRepository
import com.example.data.formatCompact
import com.example.data.unwrapFirestoreError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CrownfallViewModel(
    private val empireRepository: EmpireRepository,
    private val worldWarRepository: WorldWarRepository,
    private val allianceRepository: AllianceRepository,
    val currentUserId: String,
    initialDisplayName: String?
) : ViewModel() {

    private val _nowEpochMs = MutableStateFlow(System.currentTimeMillis())
    val nowEpochMs: StateFlow<Long> = _nowEpochMs.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _perResourceLastHarvestMs = MutableStateFlow<Map<ResourceType, Long>>(emptyMap())
    val perResourceLastHarvestMs: StateFlow<Map<ResourceType, Long>> = _perResourceLastHarvestMs.asStateFlow()

    private var completingBuild = false
    private var completingTrain = false
    private val resolvingMarchIds = mutableSetOf<String>()

    val empireState: StateFlow<UiState<PlayerEmpire>> = empireRepository.observeEmpire(currentUserId)
        .map { empire ->
            if (empire == null) UiState.Loading else UiState.Success(empire)
        }
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing empire", root)
            emit(UiState.Error(root.message ?: "Failed to load Citadel state"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val realmKingdoms: StateFlow<List<RealmKingdom>> = empireRepository.observeRealmKingdoms()
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing kingdoms", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val activeMarches: StateFlow<List<WorldMarch>> = worldWarRepository.observeActiveMarches()
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing marches", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val battleLogs: StateFlow<List<BattleLog>> = worldWarRepository.observeBattleLogs()
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing battleLogs", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val alliances: StateFlow<List<Alliance>> = allianceRepository.observeAlliances()
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing alliances", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val myInvites: StateFlow<List<AllianceInvite>> = allianceRepository.observeMyInvites(currentUserId)
        .catch { error ->
            val root = unwrapFirestoreError(error)
            Log.w(TAG, "Error observing invites", root)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val currentAlliance: StateFlow<Alliance?> = combine(empireState, alliances) { empState, all ->
        val emp = (empState as? UiState.Success)?.data ?: return@combine null
        if (emp.allianceId.isBlank()) return@combine null
        all.firstOrNull { it.id == emp.allianceId && currentUserId in it.memberUids }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val verifiedAllianceId = currentAlliance
        .map { it?.id.orEmpty() }
        .distinctUntilChanged()

    val allianceMessages: StateFlow<List<AllianceMessage>> = verifiedAllianceId
        .flatMapLatest { aid ->
            if (aid.isBlank()) flowOf(emptyList())
            else allianceRepository.observeMessages(aid, currentUserId)
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    Log.w(TAG, "Error observing alliance messages", root)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val allianceRallies: StateFlow<List<AllianceRally>> = verifiedAllianceId
        .flatMapLatest { aid ->
            if (aid.isBlank()) flowOf(emptyList())
            else allianceRepository.observeRallies(aid, currentUserId)
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    Log.w(TAG, "Error observing rallies", root)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val allianceAidRequests: StateFlow<List<ResourceAidRequest>> = verifiedAllianceId
        .flatMapLatest { aid ->
            if (aid.isBlank()) flowOf(emptyList())
            else allianceRepository.observeAidRequests(aid, currentUserId)
                .catch { error ->
                    val root = unwrapFirestoreError(error)
                    Log.w(TAG, "Error observing aidRequests", root)
                    emit(emptyList())
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    init {
        viewModelScope.launch {
            val initResult = empireRepository.ensureEmpireInitialized(initialDisplayName)
            initResult.onFailure {
                postMessage(it.localizedMessage ?: "Failed to initialize empire.")
            }
        }
        startGameLoopTicker()
    }

    private fun startGameLoopTicker() {
        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                _nowEpochMs.value = now
                checkExpiredTimers(now)
                delay(1000L)
            }
        }
    }

    private fun checkExpiredTimers(now: Long) {
        val emp = (empireState.value as? UiState.Success)?.data ?: return

        if (emp.activeBuildKey.isNotBlank() && emp.activeBuildEndsAtMs in 1..now && !completingBuild) {
            completingBuild = true
            viewModelScope.launch {
                try {
                    empireRepository.completeBuildingUpgrade(emp, useSpeedup = false)
                        .onSuccess { postMessage(it) }
                } finally {
                    completingBuild = false
                }
            }
        }

        if (emp.activeTrainKey.isNotBlank() && emp.activeTrainEndsAtMs in 1..now && !completingTrain) {
            completingTrain = true
            viewModelScope.launch {
                try {
                    empireRepository.completeTroopTraining(emp, useSpeedup = false)
                        .onSuccess { postMessage(it) }
                } finally {
                    completingTrain = false
                }
            }
        }

        val arrivedMarches = activeMarches.value.filter {
            it.ownerUid == currentUserId && it.status == "MARCHING" && it.arrivesAtEpochMs <= now
        }
        arrivedMarches.forEach { march ->
            if (resolvingMarchIds.add(march.id)) {
                viewModelScope.launch {
                    try {
                        val targetKingdom = realmKingdoms.value.firstOrNull { it.ownerUid == march.targetId }
                        worldWarRepository.resolveMarch(emp, currentAlliance.value, march, targetKingdom)
                            .onSuccess { log ->
                                val tag = if (log.battleType == "GATHER") "Expedition complete"
                                else if (log.victory) "VICTORY at ${log.defenderName}"
                                else "DEFEAT at ${log.defenderName}"
                                postMessage("$tag! +${formatCompact(log.lootFood)}F +${formatCompact(log.lootGold)}G")
                            }
                    } finally {
                        resolvingMarchIds.remove(march.id)
                    }
                }
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private fun postMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    private fun currentEmpireOrWarn(): PlayerEmpire? {
        val emp = (empireState.value as? UiState.Success)?.data
        if (emp == null) postMessage("Still syncing Citadel records…")
        return emp
    }

    fun collectTribute(onHarvested: (ResourceCost) -> Unit = {}) {
        val emp = currentEmpireOrWarn() ?: return
        runAction {
            val now = System.currentTimeMillis()
            empireRepository.collectResources(emp, currentAlliance.value, _perResourceLastHarvestMs.value)
                .onSuccess { gain ->
                    _perResourceLastHarvestMs.value = ResourceType.entries.associateWith { now }
                    onHarvested(gain)
                    postMessage(
                        "Tribute collected: +${formatCompact(gain.food)} Food, +${formatCompact(gain.timber)} Timber, +${formatCompact(gain.stone)} Stone, +${formatCompact(gain.gold)} Gold"
                    )
                }
                .onFailure { postMessage(it.localizedMessage ?: "Unable to collect tribute.") }
        }
    }

    fun collectSingleResource(resourceType: ResourceType, onHarvested: (Long) -> Unit = {}) {
        val emp = currentEmpireOrWarn() ?: return
        runAction {
            val now = System.currentTimeMillis()
            empireRepository.collectSingleResource(emp, currentAlliance.value, resourceType, _perResourceLastHarvestMs.value)
                .onSuccess { amount ->
                    _perResourceLastHarvestMs.value = _perResourceLastHarvestMs.value + (resourceType to now)
                    onHarvested(amount)
                }
                .onFailure { postMessage(it.localizedMessage ?: "Unable to collect ${resourceType.displayName}.") }
        }
    }

    fun upgradeBuilding(type: BuildingType) {
        val emp = currentEmpireOrWarn() ?: return
        runAction {
            empireRepository.startBuildingUpgrade(emp, type)
                .onSuccess { postMessage("Construction begun on ${type.displayName}!") }
                .onFailure { postMessage(it.localizedMessage ?: "Upgrade failed.") }
        }
    }

    fun speedupBuilding() {
        val emp = currentEmpireOrWarn() ?: return
        runAction {
            empireRepository.completeBuildingUpgrade(emp, useSpeedup = true)
                .onSuccess { postMessage(it) }
                .onFailure { postMessage(it.localizedMessage ?: "Speedup failed.") }
        }
    }

    fun trainTroops(type: TroopType, qty: Long) {
        val emp = currentEmpireOrWarn() ?: return
        runAction {
            empireRepository.startTroopTraining(emp, type, qty)
                .onSuccess { postMessage("Training $qty ${type.displayName}…") }
                .onFailure { postMessage(it.localizedMessage ?: "Training failed.") }
        }
    }

    fun speedupTraining() {
        val emp = currentEmpireOrWarn() ?: return
        runAction {
            empireRepository.completeTroopTraining(emp, useSpeedup = true)
                .onSuccess { postMessage(it) }
                .onFailure { postMessage(it.localizedMessage ?: "Speedup failed.") }
        }
    }

    fun dispatchMarch(
        marchType: String,
        targetType: String,
        targetId: String,
        targetName: String,
        toX: Long,
        toY: Long,
        infantry: Long,
        cavalry: Long,
        archers: Long,
        siege: Long
    ) {
        val emp = currentEmpireOrWarn() ?: return
        runAction {
            worldWarRepository.dispatchMarch(
                empire = emp,
                alliance = currentAlliance.value,
                marchType = marchType,
                targetType = targetType,
                targetId = targetId,
                targetName = targetName,
                toX = toX,
                toY = toY,
                infantrySent = infantry,
                cavalrySent = cavalry,
                archersSent = archers,
                siegeSent = siege
            ).onSuccess {
                postMessage("Army marching on $targetName ($toX, $toY)!")
            }.onFailure {
                postMessage(it.localizedMessage ?: "Failed to dispatch march.")
            }
        }
    }

    fun recallMarch(march: WorldMarch) {
        val emp = currentEmpireOrWarn() ?: return
        runAction {
            worldWarRepository.recallMarch(emp, march)
                .onSuccess { postMessage("March recalled. Troops returned to garrison.") }
                .onFailure { postMessage(it.localizedMessage ?: "Unable to recall march.") }
        }
    }

    fun resolveMarchNow(march: WorldMarch) {
        val emp = currentEmpireOrWarn() ?: return
        runAction {
            val targetKingdom = realmKingdoms.value.firstOrNull { it.ownerUid == march.targetId }
            worldWarRepository.resolveMarch(emp, currentAlliance.value, march, targetKingdom)
                .onSuccess { log ->
                    val status = if (log.battleType == "GATHER") "Gathered resources"
                    else if (log.victory) "Victory against ${log.defenderName}"
                    else "Defeated at ${log.defenderName}"
                    postMessage("$status!")
                }
                .onFailure { postMessage(it.localizedMessage ?: "Unable to resolve march.") }
        }
    }

    fun createAlliance(name: String, tag: String, motto: String, crestColor: String, openJoin: Boolean) {
        val emp = currentEmpireOrWarn() ?: return
        runAction {
            allianceRepository.createAlliance(emp, name, tag, motto, crestColor, openJoin)
                .onSuccess { postMessage("Alliance [${tag.uppercase()}] $name founded!") }
                .onFailure { postMessage(it.localizedMessage ?: "Could not found alliance.") }
        }
    }

    fun joinAlliance(alliance: Alliance, invite: AllianceInvite? = null) {
        val emp = currentEmpireOrWarn() ?: return
        runAction {
            allianceRepository.joinAlliance(emp, alliance, invite)
                .onSuccess { postMessage("Joined [${alliance.tag}] ${alliance.name}!") }
                .onFailure { postMessage(it.localizedMessage ?: "Unable to join alliance.") }
        }
    }

    fun declineInvite(invite: AllianceInvite) {
        runAction {
            allianceRepository.declineInvite(invite)
                .onSuccess { postMessage("Invitation declined.") }
                .onFailure { postMessage(it.localizedMessage ?: "Failed to decline invite.") }
        }
    }

    fun leaveOrDisbandAlliance() {
        val emp = currentEmpireOrWarn() ?: return
        val alliance = currentAlliance.value ?: return
        runAction {
            allianceRepository.leaveOrDisbandAlliance(emp, alliance)
                .onSuccess { postMessage("You have left [${alliance.tag}].") }
                .onFailure { postMessage(it.localizedMessage ?: "Unable to leave alliance.") }
        }
    }

    fun sendAllianceMessage(body: String) {
        val emp = currentEmpireOrWarn() ?: return
        val alliance = currentAlliance.value ?: return
        runAction {
            allianceRepository.sendMessage(emp, alliance, body)
                .onFailure { postMessage(it.localizedMessage ?: "Failed to send message.") }
        }
    }

    fun launchAllianceRally(
        targetType: String,
        targetId: String,
        targetName: String,
        targetX: Long,
        targetY: Long,
        targetPower: Long
    ) {
        val emp = currentEmpireOrWarn() ?: return
        val alliance = currentAlliance.value
        if (alliance == null) {
            postMessage("Join or found an Alliance to launch War Rallies!")
            return
        }
        runAction {
            allianceRepository.launchRally(
                empire = emp,
                alliance = alliance,
                targetType = targetType,
                targetId = targetId,
                targetName = targetName,
                targetX = targetX,
                targetY = targetY,
                targetPower = targetPower,
                committedPower = emp.garrisonPower().coerceAtLeast(300L)
            ).onSuccess {
                postMessage("War Rally sounded against $targetName!")
            }.onFailure {
                postMessage(it.localizedMessage ?: "Failed to launch rally.")
            }
        }
    }

    fun joinAllianceRally(rally: AllianceRally) {
        val emp = currentEmpireOrWarn() ?: return
        val alliance = currentAlliance.value ?: return
        runAction {
            allianceRepository.joinRally(
                empire = emp,
                alliance = alliance,
                rally = rally,
                committedPower = emp.garrisonPower().coerceAtLeast(300L)
            ).onSuccess { postMessage("Committed army to rally against ${rally.targetName}!") }
                .onFailure { postMessage(it.localizedMessage ?: "Could not join rally.") }
        }
    }

    fun resolveAllianceRally(rally: AllianceRally) {
        val emp = currentEmpireOrWarn() ?: return
        val alliance = currentAlliance.value ?: return
        runAction {
            allianceRepository.resolveRally(emp, alliance, rally)
                .onSuccess { log ->
                    val label = if (log.victory) "RALLY VICTORY" else "RALLY DEFEAT"
                    postMessage("$label vs ${rally.targetName}! +${formatCompact(log.lootGold)} Gold")
                }
                .onFailure { postMessage(it.localizedMessage ?: "Could not resolve rally.") }
        }
    }

    fun requestAllianceAid(resourceType: ResourceType, amount: Long) {
        val emp = currentEmpireOrWarn() ?: return
        val alliance = currentAlliance.value ?: return
        runAction {
            allianceRepository.requestAid(emp, alliance, resourceType, amount)
                .onSuccess { postMessage("Aid request posted for ${formatCompact(amount)} ${resourceType.displayName}.") }
                .onFailure { postMessage(it.localizedMessage ?: "Could not request aid.") }
        }
    }

    fun donateAllianceAid(request: ResourceAidRequest) {
        val emp = currentEmpireOrWarn() ?: return
        val alliance = currentAlliance.value ?: return
        runAction {
            allianceRepository.donateAid(emp, alliance, request)
                .onSuccess { postMessage("Aid dispatched! +45 Honor & +75 Alliance Tech XP") }
                .onFailure { postMessage(it.localizedMessage ?: "Unable to donate aid.") }
        }
    }

    fun contributeAllianceTech(techType: AllianceTechType) {
        val emp = currentEmpireOrWarn() ?: return
        val alliance = currentAlliance.value ?: return
        runAction {
            allianceRepository.contributeAllianceTech(emp, alliance, techType)
                .onSuccess { postMessage(it) }
                .onFailure { postMessage(it.localizedMessage ?: "Unable to fund alliance tech.") }
        }
    }

    fun inviteLord(target: RealmKingdom) {
        val emp = currentEmpireOrWarn() ?: return
        val alliance = currentAlliance.value ?: return
        runAction {
            allianceRepository.inviteLord(emp, alliance, target)
                .onSuccess { postMessage("Invitation sent to ${target.lordName}!") }
                .onFailure { postMessage(it.localizedMessage ?: "Unable to send invite.") }
        }
    }

    fun toggleOfficer(targetUid: String) {
        val alliance = currentAlliance.value ?: return
        runAction {
            allianceRepository.toggleOfficer(alliance, targetUid)
                .onSuccess { postMessage(it) }
                .onFailure { postMessage(it.localizedMessage ?: "Role change failed.") }
        }
    }

    fun transferLeadership(newLeaderUid: String) {
        val alliance = currentAlliance.value ?: return
        runAction {
            allianceRepository.transferLeadership(alliance, newLeaderUid)
                .onSuccess { postMessage("Alliance crown transferred.") }
                .onFailure { postMessage(it.localizedMessage ?: "Transfer failed.") }
        }
    }

    fun kickMember(targetUid: String, targetPower: Long) {
        val alliance = currentAlliance.value ?: return
        runAction {
            allianceRepository.kickMember(alliance, targetUid, targetPower)
                .onSuccess { postMessage("Member removed from alliance.") }
                .onFailure { postMessage(it.localizedMessage ?: "Removal failed.") }
        }
    }

    fun renameLord(newName: String) {
        runAction {
            empireRepository.renameLord(newName)
                .onSuccess { postMessage("Royal decree proclaimed: $newName") }
                .onFailure { postMessage(it.localizedMessage ?: "Rename failed.") }
        }
    }

    fun activatePeaceShield() {
        val emp = currentEmpireOrWarn() ?: return
        runAction {
            empireRepository.activatePeaceShield(emp)
                .onSuccess { postMessage("1-Hour Obsidian Peace Shield activated!") }
                .onFailure { postMessage(it.localizedMessage ?: "Cannot activate Peace Shield.") }
        }
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                block()
            } finally {
                _isBusy.value = false
            }
        }
    }

    private companion object {
        const val TAG = "CrownfallVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
