package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.outlined.Castle
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.R
import com.example.auth.signOutUser
import com.example.data.AllianceRepository
import com.example.data.EmpireRepository
import com.example.data.UiState
import com.example.data.WorldWarRepository
import com.example.ui.theme.BasaltSlate
import com.example.ui.theme.CrownGold
import com.example.ui.theme.DarkInkOnGold
import com.example.ui.theme.EmberCrimson
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentIvory
import com.example.ui.theme.WeatheredAsh
import com.example.viewmodel.CrownfallViewModel
import com.google.firebase.firestore.FirebaseFirestore

private data class NavDest(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

private val CrownfallDestinations = listOf(
    NavDest(R.string.nav_citadel, Icons.Filled.Castle, Icons.Outlined.Castle, "nav_citadel"),
    NavDest(R.string.nav_realm, Icons.Filled.Public, Icons.Outlined.Public, "nav_realm"),
    NavDest(R.string.nav_alliance, Icons.Filled.Groups, Icons.Outlined.Groups, "nav_alliance"),
    NavDest(R.string.nav_war_room, Icons.Filled.EmojiEvents, Icons.Outlined.EmojiEvents, "nav_war_room")
)

@Composable
fun CrownfallMainShell(
    currentUserId: String,
    initialDisplayName: String?,
    onSignedOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: CrownfallViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                CrownfallViewModel(
                    empireRepository = EmpireRepository(db),
                    worldWarRepository = WorldWarRepository(db),
                    allianceRepository = AllianceRepository(db),
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName
                )
            }
        }
    )

    val empireState by viewModel.empireState.collectAsStateWithLifecycle()
    val kingdoms by viewModel.realmKingdoms.collectAsStateWithLifecycle()
    val marches by viewModel.activeMarches.collectAsStateWithLifecycle()
    val battleLogs by viewModel.battleLogs.collectAsStateWithLifecycle()
    val alliances by viewModel.alliances.collectAsStateWithLifecycle()
    val currentAlliance by viewModel.currentAlliance.collectAsStateWithLifecycle()
    val myInvites by viewModel.myInvites.collectAsStateWithLifecycle()
    val messages by viewModel.allianceMessages.collectAsStateWithLifecycle()
    val rallies by viewModel.allianceRallies.collectAsStateWithLifecycle()
    val aidRequests by viewModel.allianceAidRequests.collectAsStateWithLifecycle()
    val nowEpochMs by viewModel.nowEpochMs.collectAsStateWithLifecycle()
    val perResourceLastHarvestMs by viewModel.perResourceLastHarvestMs.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        val msg = snackbarMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Back press from secondary tabs returns to Citadel Home cleanly
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    val empire = (empireState as? UiState.Success)?.data

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        val useRail = maxWidth >= 680.dp

        Scaffold(
            containerColor = ObsidianVoid,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                CrownfallTopResourceHud(
                    empire = empire,
                    alliance = currentAlliance,
                    nowEpochMs = nowEpochMs,
                    perResourceLastHarvestMs = perResourceLastHarvestMs
                )
            },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = BasaltSlate,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        CrownfallDestinations.forEachIndexed { index, dest ->
                            val selected = selectedTab == index
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(dest.labelRes),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = DarkInkOnGold,
                                    selectedTextColor = CrownGold,
                                    indicatorColor = CrownGold,
                                    unselectedIconColor = WeatheredAsh,
                                    unselectedTextColor = WeatheredAsh
                                ),
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        containerColor = BasaltSlate,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        Spacer(Modifier.height(12.dp))
                        CrownfallDestinations.forEachIndexed { index, dest ->
                            val selected = selectedTab == index
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = index },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = stringResource(dest.labelRes)
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = DarkInkOnGold,
                                    selectedTextColor = CrownGold,
                                    indicatorColor = CrownGold,
                                    unselectedIconColor = WeatheredAsh,
                                    unselectedTextColor = WeatheredAsh
                                ),
                                modifier = Modifier.testTag(dest.tag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    when (val state = empireState) {
                        UiState.Loading -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = CrownGold)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Raising Your Obsidian Citadel…",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ParchmentIvory
                                )
                            }
                        }

                        is UiState.Error -> {
                            ObsidianCard(
                                highlightBorder = EmberCrimson,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp)
                                    .widthIn(max = 460.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.error_load_failed),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = EmberCrimson
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ParchmentIvory
                                )
                            }
                        }

                        is UiState.Success -> {
                            val emp = state.data
                            val contentModifier = if (selectedTab == 0 || selectedTab == 1) {
                                Modifier.fillMaxSize()
                            } else {
                                Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 720.dp)
                            }
                            Box(modifier = contentModifier) {
                                when (selectedTab) {
                                    0 -> CitadelScreen(
                                        empire = emp,
                                        alliance = currentAlliance,
                                        nowEpochMs = nowEpochMs,
                                        perResourceLastHarvestMs = perResourceLastHarvestMs,
                                        onCollectTribute = viewModel::collectTribute,
                                        onCollectSingleResource = viewModel::collectSingleResource,
                                        onUpgradeBuilding = viewModel::upgradeBuilding,
                                        onSpeedupBuilding = viewModel::speedupBuilding,
                                        onTrainTroops = viewModel::trainTroops,
                                        onSpeedupTraining = viewModel::speedupTraining
                                    )

                                    1 -> RealmMapScreen(
                                        empire = emp,
                                        alliance = currentAlliance,
                                        kingdoms = kingdoms,
                                        activeMarches = marches,
                                        nowEpochMs = nowEpochMs,
                                        onDispatchMarch = viewModel::dispatchMarch,
                                        onRecallMarch = viewModel::recallMarch,
                                        onResolveMarch = viewModel::resolveMarchNow,
                                        onLaunchRally = viewModel::launchAllianceRally
                                    )

                                    2 -> AllianceScreen(
                                        empire = emp,
                                        currentAlliance = currentAlliance,
                                        allAlliances = alliances,
                                        kingdoms = kingdoms,
                                        myInvites = myInvites,
                                        messages = messages,
                                        rallies = rallies,
                                        aidRequests = aidRequests,
                                        nowEpochMs = nowEpochMs,
                                        onCreateAlliance = viewModel::createAlliance,
                                        onJoinAlliance = viewModel::joinAlliance,
                                        onDeclineInvite = viewModel::declineInvite,
                                        onLeaveOrDisband = viewModel::leaveOrDisbandAlliance,
                                        onSendMessage = viewModel::sendAllianceMessage,
                                        onLaunchQuickRally = viewModel::launchAllianceRally,
                                        onJoinRally = viewModel::joinAllianceRally,
                                        onResolveRally = viewModel::resolveAllianceRally,
                                        onRequestAid = viewModel::requestAllianceAid,
                                        onDonateAid = viewModel::donateAllianceAid,
                                        onContributeTech = viewModel::contributeAllianceTech,
                                        onInviteLord = viewModel::inviteLord,
                                        onToggleOfficer = viewModel::toggleOfficer,
                                        onTransferLeadership = viewModel::transferLeadership,
                                        onKickMember = viewModel::kickMember
                                    )

                                    else -> WarRoomScreen(
                                        empire = emp,
                                        alliance = currentAlliance,
                                        kingdoms = kingdoms,
                                        alliances = alliances,
                                        battleLogs = battleLogs,
                                        onRenameLord = viewModel::renameLord,
                                        onActivateShield = viewModel::activatePeaceShield,
                                        onSignOut = {
                                            signOutUser(
                                                credentialManager = credentialManager,
                                                scope = scope,
                                                onSignOutComplete = onSignedOut
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
