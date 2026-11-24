package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Alliance
import com.example.data.AllianceMessage
import com.example.data.AllianceRally
import com.example.data.AllianceTechType
import com.example.data.GameCatalog
import com.example.data.PlayerEmpire
import com.example.data.RealmKingdom
import com.example.data.ResourceAidRequest
import com.example.data.ResourceType
import com.example.data.formatCompact
import com.example.data.formatCountdown
import com.example.ui.theme.ArcaneEmerald
import com.example.ui.theme.BurnishedCharcoal
import com.example.ui.theme.CrownGold
import com.example.ui.theme.DarkInkOnGold
import com.example.ui.theme.EmberCrimson
import com.example.ui.theme.EtchedSteel
import com.example.ui.theme.ForgedIron
import com.example.ui.theme.GildedHighlight
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.ParchmentIvory
import com.example.ui.theme.WarCrimson
import com.example.ui.theme.WeatheredAsh

@Composable
fun AllianceChatPanel(
    messages: List<AllianceMessage>,
    currentUserId: String,
    onSendMessage: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    ObsidianCard {
                        Text(
                            "The war council awaits your command. Send the first message to your allies!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WeatheredAsh
                        )
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    val isMine = msg.senderUid == currentUserId
                    val accent = when (msg.messageType) {
                        "RALLY" -> EmberCrimson
                        "AID" -> ArcaneEmerald
                        "SYSTEM" -> CrownGold
                        else -> if (isMine) CrownGold else EtchedSteel
                    }
                    ObsidianCard(highlightBorder = accent.copy(alpha = 0.65f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RoleRankTag(msg.senderRole)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = msg.senderName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isMine) GildedHighlight else ParchmentIvory
                                )
                            }
                            if (msg.messageType != "CHAT") {
                                Text(
                                    text = msg.messageType,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = msg.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ParchmentIvory
                        )
                    }
                }
            }
        }

        Surface(
            color = ForgedIron,
            border = BorderStroke(1.dp, EtchedSteel),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 300) draft = it },
                    placeholder = { Text("Issue orders to your alliance…") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("alliance_chat_input")
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (draft.isNotBlank()) {
                            onSendMessage(draft)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = CrownGold,
                        contentColor = DarkInkOnGold,
                        disabledContainerColor = BurnishedCharcoal,
                        disabledContentColor = WeatheredAsh
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("alliance_chat_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send alliance message")
                }
            }
        }
    }
}

@Composable
fun AllianceRalliesPanel(
    empire: PlayerEmpire,
    rallies: List<AllianceRally>,
    nowEpochMs: Long,
    onLaunchQuickRally: (String, String, String, Long, Long, Long) -> Unit,
    onJoinRally: (AllianceRally) -> Unit,
    onResolveRally: (AllianceRally) -> Unit
) {
    val bastions = remember { GameCatalog.worldNpcNodes.filter { it.targetType == "BASTION" } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ObsidianCard(highlightBorder = WarCrimson.copy(alpha = 0.7f)) {
                Text("SOUND A WAR RALLY", style = MaterialTheme.typography.titleSmall, color = CrownGold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Call allies to combine armies against high-tier Shadow Bastions or target rival castles from the Realm Map.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeatheredAsh
                )
                Spacer(Modifier.height(10.dp))
                val target = bastions[2] // Crimson Redoubt
                CrimsonButton(
                    text = "Rally on ${target.name} (DEF ${formatCompact(target.garrisonPower)})",
                    icon = Icons.Filled.Whatshot,
                    onClick = {
                        onLaunchQuickRally(
                            "BASTION", target.id, target.name,
                            target.coordX, target.coordY, target.garrisonPower
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_rally_button")
                )
            }
        }

        if (rallies.isEmpty()) {
            item {
                ObsidianCard {
                    Text(
                        "No war rallies are currently gathering. Sound the horn above or tap any castle on the Realm Map.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WeatheredAsh
                    )
                }
            }
        } else {
            items(rallies, key = { it.id }) { rally ->
                val remaining = (rally.launchAtEpochMs - nowEpochMs).coerceAtLeast(0L)
                val joined = empire.userId in rally.participantUids
                val isActive = rally.status == "GATHERING" || rally.status == "LAUNCHED"
                val powerRatio = (rally.totalPower.toFloat() / rally.targetPower.coerceAtLeast(1L).toFloat()).coerceIn(0.05f, 1f)

                ObsidianCard(highlightBorder = if (isActive) EmberCrimson else EtchedSteel) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "vs ${rally.targetName} (${rally.targetX}, ${rally.targetY})",
                                style = MaterialTheme.typography.titleSmall,
                                color = ParchmentIvory
                            )
                            Text(
                                text = "Led by ${rally.leaderName} • ${rally.participantUids.size} Lords",
                                style = MaterialTheme.typography.bodySmall,
                                color = WeatheredAsh
                            )
                        }
                        Surface(
                            color = if (isActive) WarCrimson.copy(alpha = 0.25f) else BurnishedCharcoal,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isActive) formatCountdown(remaining) else rally.status,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isActive) CrownGold else WeatheredAsh,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Combined Power: ${formatCompact(rally.totalPower)} / ${formatCompact(rally.targetPower)}",
                        style = MonoStatStyle,
                        color = if (rally.totalPower >= rally.targetPower) ArcaneEmerald else CrownGold
                    )
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { powerRatio },
                        color = if (rally.totalPower >= rally.targetPower) ArcaneEmerald else WarCrimson,
                        trackColor = BurnishedCharcoal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = rally.participantSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = WeatheredAsh,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isActive) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!joined) {
                                GildedButton(
                                    text = "Join Rally",
                                    onClick = { onJoinRally(rally) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (joined || rally.leaderUid == empire.userId) {
                                CrimsonButton(
                                    text = "Launch Strike",
                                    onClick = { onResolveRally(rally) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AllianceAidAndTechPanel(
    empire: PlayerEmpire,
    alliance: Alliance,
    aidRequests: List<ResourceAidRequest>,
    onRequestAid: (ResourceType, Long) -> Unit,
    onDonateAid: (ResourceAidRequest) -> Unit,
    onContributeTech: (AllianceTechType) -> Unit
) {
    var selectedAidRes by remember { mutableStateOf(ResourceType.FOOD) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Shared Alliance Technology Section
        item {
            Text(
                text = "SHARED ALLIANCE TECHNOLOGIES (XP: ${formatCompact(alliance.techXp)})",
                style = MaterialTheme.typography.titleSmall,
                color = CrownGold
            )
        }

        items(AllianceTechType.entries, key = { it.name }) { tech ->
            val level = alliance.techLevel(tech)
            val neededXp = GameCatalog.techUpgradeCostXp(level)
            val progress = (alliance.techXp.toFloat() / neededXp.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            ObsidianCard(highlightBorder = CrownGold.copy(alpha = 0.45f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${tech.displayName} — Lv.$level/10",
                            style = MaterialTheme.typography.titleSmall,
                            color = ParchmentIvory
                        )
                        Text(
                            text = tech.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = WeatheredAsh
                        )
                        Text(
                            text = "Active Buff: +${level * tech.bonusPerLevelPercent}%",
                            style = MonoStatStyle,
                            color = ArcaneEmerald
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    GildedButton(
                        text = "120G",
                        icon = Icons.Filled.Science,
                        enabled = empire.gold >= 120L && level < 10L,
                        onClick = { onContributeTech(tech) },
                        modifier = Modifier.testTag("fund_tech_${tech.name.lowercase()}")
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = CrownGold,
                    trackColor = BurnishedCharcoal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }

        // Resource Aid Request Creator
        item {
            Spacer(Modifier.height(4.dp))
            Text("ALLIANCE RESOURCE AID", style = MaterialTheme.typography.titleSmall, color = CrownGold)
            Spacer(Modifier.height(6.dp))
            ObsidianCard {
                Text("Request 600 Resources from Allies", style = MaterialTheme.typography.titleSmall, color = ParchmentIvory)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ResourceType.entries.forEach { res ->
                        FilterChip(
                            selected = selectedAidRes == res,
                            onClick = { selectedAidRes = res },
                            label = { Text(res.displayName, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrownGold,
                                selectedLabelColor = DarkInkOnGold,
                                containerColor = BurnishedCharcoal,
                                labelColor = ParchmentIvory
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                GildedButton(
                    text = "Post ${selectedAidRes.displayName} Aid Request",
                    icon = Icons.Filled.CardGiftcard,
                    onClick = { onRequestAid(selectedAidRes, 600L) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("post_aid_request_button")
                )
            }
        }

        items(aidRequests, key = { it.id }) { req ->
            val progress = (req.fulfilledAmount.toFloat() / req.requestedAmount.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
            val isMine = req.requesterUid == empire.userId
            ObsidianCard(highlightBorder = if (req.status == "OPEN") ArcaneEmerald.copy(alpha = 0.6f) else EtchedSteel) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${req.requesterName} needs ${req.resourceType}",
                            style = MaterialTheme.typography.titleSmall,
                            color = ParchmentIvory
                        )
                        Text(
                            text = "${formatCompact(req.fulfilledAmount)} / ${formatCompact(req.requestedAmount)} (${req.status})",
                            style = MonoStatStyle,
                            color = if (req.status == "FULFILLED") ArcaneEmerald else CrownGold
                        )
                    }
                    if (req.status == "OPEN" && !isMine) {
                        GildedButton(
                            text = "Send Aid",
                            onClick = { onDonateAid(req) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = ArcaneEmerald,
                    trackColor = BurnishedCharcoal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

@Composable
fun AllianceRosterPanel(
    empire: PlayerEmpire,
    alliance: Alliance,
    kingdoms: List<RealmKingdom>,
    onInviteLord: (RealmKingdom) -> Unit,
    onToggleOfficer: (String) -> Unit,
    onTransferLeadership: (String) -> Unit,
    onKickMember: (String, Long) -> Unit,
    onLeaveOrDisband: () -> Unit
) {
    var showInviteDialog by remember { mutableStateOf(false) }
    val myRole = alliance.roleOf(empire.userId)
    val isLeader = myRole == "LEADER"
    val canManage = isLeader || myRole == "OFFICER"

    val memberKingdoms = remember(alliance.memberUids, kingdoms) {
        alliance.memberUids.map { uid ->
            kingdoms.firstOrNull { it.ownerUid == uid } ?: RealmKingdom(
                ownerUid = uid,
                lordName = if (uid == empire.userId) empire.lordName else "Allied Lord",
                power = if (uid == empire.userId) empire.power else 1000L
            )
        }.sortedWith(
            compareBy<RealmKingdom> {
                when (alliance.roleOf(it.ownerUid)) {
                    "LEADER" -> 0
                    "OFFICER" -> 1
                    else -> 2
                }
            }.thenByDescending { it.power }
        )
    }

    val unaffiliatedLords = remember(kingdoms, alliance.memberUids) {
        kingdoms.filter { it.ownerUid !in alliance.memberUids && it.allianceId.isBlank() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (canManage) {
            item {
                GildedButton(
                    text = "Invite Unaffiliated Lord (${unaffiliatedLords.size} Available)",
                    icon = Icons.Filled.PersonAdd,
                    enabled = unaffiliatedLords.isNotEmpty(),
                    onClick = { showInviteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("invite_lord_button")
                )
            }
        }

        items(memberKingdoms, key = { it.ownerUid }) { member ->
            val role = alliance.roleOf(member.ownerUid)
            ObsidianCard(highlightBorder = if (role == "LEADER") CrownGold else EtchedSteel) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RoleRankTag(role)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = member.lordName,
                                style = MaterialTheme.typography.titleSmall,
                                color = ParchmentIvory
                            )
                        }
                        Text(
                            text = "Keep Lv.${member.keepLevel} • PWR ${formatCompact(member.power)} • (${member.coordX}, ${member.coordY})",
                            style = MaterialTheme.typography.bodySmall,
                            color = WeatheredAsh
                        )
                    }
                }

                if (canManage && member.ownerUid != empire.userId && role != "LEADER") {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isLeader) {
                            OutlinedButton(
                                onClick = { onToggleOfficer(member.ownerUid) },
                                border = BorderStroke(1.dp, CrownGold),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Text(
                                    if (role == "OFFICER") "Demote" else "Make R4",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CrownGold
                                )
                            }
                            OutlinedButton(
                                onClick = { onTransferLeadership(member.ownerUid) },
                                border = BorderStroke(1.dp, EtchedSteel),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Text("Pass R5", style = MaterialTheme.typography.labelSmall, color = ParchmentIvory)
                            }
                        }
                        OutlinedButton(
                            onClick = { onKickMember(member.ownerUid, member.power) },
                            border = BorderStroke(1.dp, EmberCrimson),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Text("Remove", style = MaterialTheme.typography.labelSmall, color = EmberCrimson)
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = onLeaveOrDisband,
                border = BorderStroke(1.dp, EmberCrimson),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("leave_alliance_button")
            ) {
                Text(
                    text = if (isLeader && alliance.memberUids.size <= 1) "Disband Alliance" else "Leave Alliance",
                    color = EmberCrimson,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
            containerColor = ForgedIron,
            title = { Text("Invite Lord to [${alliance.tag}]", color = CrownGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (unaffiliatedLords.isEmpty()) {
                        Text("All active lords in Realm I are already pledged to an alliance.", color = WeatheredAsh)
                    } else {
                        unaffiliatedLords.take(8).forEach { lord ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(lord.lordName, style = MaterialTheme.typography.titleSmall, color = ParchmentIvory)
                                    Text("PWR ${formatCompact(lord.power)}", style = MaterialTheme.typography.bodySmall, color = WeatheredAsh)
                                }
                                GildedButton(
                                    text = "Invite",
                                    onClick = {
                                        onInviteLord(lord)
                                        showInviteDialog = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showInviteDialog = false }) {
                    Text("Close", color = WeatheredAsh)
                }
            }
        )
    }
}

@Composable
private fun RoleRankTag(role: String) {
    val (label, color) = when (role) {
        "LEADER" -> "R5" to CrownGold
        "OFFICER" -> "R4" to EmberCrimson
        else -> "R1" to WeatheredAsh
    }
    Surface(
        color = color.copy(alpha = 0.2f),
        border = BorderStroke(1.dp, color),
        shape = RoundedCornerShape(5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}
