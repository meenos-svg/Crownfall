package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Alliance
import com.example.data.AllianceInvite
import com.example.data.AllianceMessage
import com.example.data.AllianceRally
import com.example.data.AlliancePact
import com.example.data.Stronghold
import androidx.compose.material.icons.filled.Handshake
import com.example.data.AllianceTechType
import com.example.data.PlayerEmpire
import com.example.data.RealmKingdom
import com.example.data.ResourceAidRequest
import com.example.data.ResourceType
import com.example.data.formatCompact
import com.example.ui.theme.ArcaneEmerald
import com.example.ui.theme.CrownGold
import com.example.ui.theme.DarkInkOnGold
import com.example.ui.theme.EmberCrimson
import com.example.ui.theme.EtchedSteel
import com.example.ui.theme.ForgedIron
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentIvory
import com.example.ui.theme.WarCrimson
import com.example.ui.theme.WeatheredAsh

@Composable
fun AllianceScreen(
    empire: PlayerEmpire,
    currentAlliance: Alliance?,
    allAlliances: List<Alliance>,
    kingdoms: List<RealmKingdom>,
    myInvites: List<AllianceInvite>,
    messages: List<AllianceMessage>,
    rallies: List<AllianceRally>,
    aidRequests: List<ResourceAidRequest>,
    nowEpochMs: Long,
    onCreateAlliance: (name: String, tag: String, motto: String, crest: String, openJoin: Boolean) -> Unit,
    onJoinAlliance: (Alliance, AllianceInvite?) -> Unit,
    onDeclineInvite: (AllianceInvite) -> Unit,
    onLeaveOrDisband: () -> Unit,
    onSendMessage: (String) -> Unit,
    onLaunchQuickRally: (targetType: String, targetId: String, targetName: String, x: Long, y: Long, power: Long) -> Unit,
    onJoinRally: (AllianceRally) -> Unit,
    onResolveRally: (AllianceRally) -> Unit,
    onRequestAid: (ResourceType, Long) -> Unit,
    onDonateAid: (ResourceAidRequest) -> Unit,
    onContributeTech: (AllianceTechType) -> Unit,
    onInviteLord: (RealmKingdom) -> Unit,
    onToggleOfficer: (String) -> Unit,
    onTransferLeadership: (String) -> Unit,
    onKickMember: (String, Long) -> Unit,
    pacts: List<AlliancePact> = emptyList(),
    ownStronghold: Stronghold? = null,
    onProposePact: (Alliance) -> Unit = {},
    onRespondPact: (AlliancePact, Boolean) -> Unit = { _, _ -> },
    onEndPact: (AlliancePact) -> Unit = {}
) {
    if (currentAlliance == null) {
        AllianceDirectoryView(
            allAlliances = allAlliances,
            myInvites = myInvites,
            onCreateAlliance = onCreateAlliance,
            onJoinAlliance = onJoinAlliance,
            onDeclineInvite = onDeclineInvite
        )
    } else {
        var subTab by remember { mutableIntStateOf(0) } // 0 Chat, 1 Rallies, 2 Aid & Tech, 3 Roster, 4 Pacts

        Column(modifier = Modifier.fillMaxSize()) {
            // Compact Alliance Banner Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_alliance_banner_1795493494497),
                    contentDescription = stringResource(R.string.cd_alliance_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(ObsidianVoid.copy(alpha = 0.88f), ObsidianVoid.copy(alpha = 0.45f))
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "[${currentAlliance.tag}] ${currentAlliance.name}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = CrownGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentAlliance.motto,
                            style = MaterialTheme.typography.bodySmall,
                            color = ParchmentIvory,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${currentAlliance.memberCount}/30 Lords • PWR ${formatCompact(currentAlliance.totalPower)} • Rank: ${currentAlliance.roleOf(empire.userId)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = WeatheredAsh
                        )
                    }
                }
            }

            // 4 Sub-Tab Segmented Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AllianceSubTabChip(subTab == 0, "Chat", Icons.Filled.Chat, { subTab = 0 }, Modifier.weight(1f).testTag("alliance_tab_chat"))
                AllianceSubTabChip(subTab == 1, "Rallies", Icons.Filled.Whatshot, { subTab = 1 }, Modifier.weight(1f).testTag("alliance_tab_rallies"))
                AllianceSubTabChip(subTab == 2, "Aid/Tech", Icons.Filled.Science, { subTab = 2 }, Modifier.weight(1f).testTag("alliance_tab_tech"))
                AllianceSubTabChip(subTab == 3, "Roster", Icons.Filled.Groups, { subTab = 3 }, Modifier.weight(1f).testTag("alliance_tab_roster"))
                AllianceSubTabChip(subTab == 4, "Pacts", Icons.Filled.Handshake, { subTab = 4 }, Modifier.weight(1f).testTag("alliance_tab_pacts"))
            }

            Spacer(Modifier.height(6.dp))

            when (subTab) {
                0 -> AllianceChatPanel(
                    messages = messages,
                    currentUserId = empire.userId,
                    onSendMessage = onSendMessage
                )
                1 -> AllianceRalliesPanel(
                    empire = empire,
                    rallies = rallies,
                    nowEpochMs = nowEpochMs,
                    onLaunchQuickRally = onLaunchQuickRally,
                    onJoinRally = onJoinRally,
                    onResolveRally = onResolveRally
                )
                2 -> AllianceAidAndTechPanel(
                    empire = empire,
                    alliance = currentAlliance,
                    aidRequests = aidRequests,
                    onRequestAid = onRequestAid,
                    onDonateAid = onDonateAid,
                    onContributeTech = onContributeTech
                )
                3 -> AllianceRosterPanel(
                    empire = empire,
                    alliance = currentAlliance,
                    kingdoms = kingdoms,
                    onInviteLord = onInviteLord,
                    onToggleOfficer = onToggleOfficer,
                    onTransferLeadership = onTransferLeadership,
                    onKickMember = onKickMember,
                    onLeaveOrDisband = onLeaveOrDisband
                )
                4 -> AlliancePactsPanel(
                    empire = empire,
                    alliance = currentAlliance,
                    allAlliances = allAlliances,
                    pacts = pacts,
                    ownStronghold = ownStronghold,
                    onProposePact = onProposePact,
                    onRespondPact = onRespondPact,
                    onEndPact = onEndPact
                )
            }
        }
    }
}

@Composable
private fun AllianceSubTabChip(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(icon, null, Modifier.size(14.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CrownGold,
            selectedLabelColor = DarkInkOnGold,
            selectedLeadingIconColor = DarkInkOnGold,
            containerColor = ForgedIron,
            labelColor = ParchmentIvory
        ),
        modifier = modifier.height(44.dp)
    )
}

@Composable
private fun AllianceDirectoryView(
    allAlliances: List<Alliance>,
    myInvites: List<AllianceInvite>,
    onCreateAlliance: (String, String, String, String, Boolean) -> Unit,
    onJoinAlliance: (Alliance, AllianceInvite?) -> Unit,
    onDeclineInvite: (AllianceInvite) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_alliance_banner_1795493494497),
                    contentDescription = stringResource(R.string.cd_alliance_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(ObsidianVoid.copy(alpha = 0.25f), ObsidianVoid.copy(alpha = 0.92f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("UNITE UNDER ONE BANNER", style = MaterialTheme.typography.headlineSmall, color = CrownGold)
                    Text(
                        "Join an alliance or found your own to unlock War Rallies, Live Chat, Resource Aid, and Shared Tech Buffs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ParchmentIvory
                    )
                    GildedButton(
                        text = stringResource(R.string.create_alliance),
                        icon = Icons.Filled.Add,
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("open_create_alliance_button")
                    )
                }
            }
        }

        if (myInvites.isNotEmpty()) {
            item {
                Text("PENDING INVITATIONS", style = MaterialTheme.typography.titleSmall, color = CrownGold)
            }
            items(myInvites, key = { it.id }) { invite ->
                val targetAlliance = allAlliances.firstOrNull { it.id == invite.allianceId }
                ObsidianCard(highlightBorder = CrownGold) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Mail, null, tint = CrownGold, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "[${invite.allianceTag}] ${invite.allianceName}",
                                style = MaterialTheme.typography.titleSmall,
                                color = ParchmentIvory
                            )
                            Text(
                                text = "Invited by ${invite.inviterName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = WeatheredAsh
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GildedButton(
                            text = "Accept",
                            enabled = targetAlliance != null,
                            onClick = { targetAlliance?.let { onJoinAlliance(it, invite) } },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { onDeclineInvite(invite) },
                            border = BorderStroke(1.dp, EmberCrimson),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Decline", color = EmberCrimson)
                        }
                    }
                }
            }
        }

        item {
            Text("REALM ALLIANCES (${allAlliances.size})", style = MaterialTheme.typography.titleSmall, color = CrownGold)
        }

        if (allAlliances.isEmpty()) {
            item {
                ObsidianCard {
                    Text(
                        "No alliances exist in Realm I yet. Tap 'Found Alliance' above to establish the first dynasty!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WeatheredAsh
                    )
                }
            }
        } else {
            items(allAlliances, key = { it.id }) { alliance ->
                ObsidianCard(highlightBorder = crestColorToCompose(alliance.crestColor).copy(alpha = 0.65f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "[${alliance.tag}] ${alliance.name}",
                                style = MaterialTheme.typography.titleMedium,
                                color = ParchmentIvory
                            )
                            Text(
                                text = alliance.motto,
                                style = MaterialTheme.typography.bodySmall,
                                color = WeatheredAsh,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${alliance.memberCount}/30 Lords • PWR ${formatCompact(alliance.totalPower)}",
                                style = MonoStatStyle,
                                color = CrownGold
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        if (alliance.openJoin) {
                            CrimsonButton(
                                text = "Join",
                                onClick = { onJoinAlliance(alliance, null) },
                                modifier = Modifier.testTag("join_alliance_${alliance.tag}")
                            )
                        } else {
                            Surface(
                                color = ObsidianVoid,
                                border = BorderStroke(1.dp, EtchedSteel),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Invite Only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WeatheredAsh,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateAllianceDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, tag, motto, crest, openJoin ->
                onCreateAlliance(name, tag, motto, crest, openJoin)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun CreateAllianceDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("") }
    var motto by remember { mutableStateOf("Iron, Blood, and Crown Gold") }
    var crest by remember { mutableStateOf("CRIMSON") }
    var openJoin by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForgedIron,
        title = { Text("Found New Alliance", style = MaterialTheme.typography.headlineSmall, color = CrownGold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 32) name = it },
                    label = { Text("Alliance Name (3-32 chars)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("alliance_name_input")
                )
                OutlinedTextField(
                    value = tag,
                    onValueChange = { if (it.length <= 5) tag = it.uppercase() },
                    label = { Text("Tag (2-5 chars, e.g. OBV)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("alliance_tag_input")
                )
                OutlinedTextField(
                    value = motto,
                    onValueChange = { if (it.length <= 120) motto = it },
                    label = { Text("War Motto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CRIMSON", "GOLD", "EMERALD", "ROYAL").forEach { c ->
                        FilterChip(
                            selected = crest == c,
                            onClick = { crest = c },
                            label = { Text(c.take(3), style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = crestColorToCompose(c),
                                selectedLabelColor = ParchmentIvory
                            )
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = openJoin, onCheckedChange = { openJoin = it })
                    Text("Open Enrollment (any lord may join)", style = MaterialTheme.typography.bodySmall, color = ParchmentIvory)
                }
            }
        },
        confirmButton = {
            GildedButton(
                text = "Found",
                enabled = name.trim().length in 3..32 && tag.trim().length in 2..5,
                onClick = { onConfirm(name, tag, motto, crest, openJoin) },
                modifier = Modifier.testTag("confirm_create_alliance_button")
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = WeatheredAsh)
            }
        }
    )
}
