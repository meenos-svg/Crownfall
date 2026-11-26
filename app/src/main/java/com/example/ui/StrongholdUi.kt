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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Alliance
import com.example.data.AlliancePact
import com.example.data.GameCatalog
import com.example.data.PlayerEmpire
import com.example.data.Stronghold
import com.example.data.TroopType
import com.example.data.formatCompact
import com.example.data.formatCountdown
import com.example.ui.theme.ArcaneEmerald
import com.example.ui.theme.BurnishedCharcoal
import com.example.ui.theme.CrownGold
import com.example.ui.theme.EmberCrimson
import com.example.ui.theme.EtchedSteel
import com.example.ui.theme.ForgedIron
import com.example.ui.theme.GildedHighlight
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.ParchmentIvory
import com.example.ui.theme.RoyalAzure
import com.example.ui.theme.WarCrimson
import com.example.ui.theme.WeatheredAsh
import kotlin.math.roundToLong

private const val STRONGHOLD_STONE = 3000L
private const val STRONGHOLD_GOLD = 2000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StrongholdSheet(
    stronghold: Stronghold,
    empire: PlayerEmpire,
    alliance: Alliance?,
    isPactAlly: Boolean,
    onDismiss: () -> Unit,
    onReinforce: (Long, Long, Long, Long) -> Unit,
    onWithdraw: () -> Unit,
    onCallForAid: (String) -> Unit,
    onEndCallForAid: () -> Unit,
    onSiege: (Long, Long, Long, Long) -> Unit,
    onRally: () -> Unit
) {
    val isOurs = empire.allianceId.isNotBlank() && stronghold.allianceId == empire.allianceId
    val canReinforce = isOurs || isPactAlly || stronghold.callForAid
    val canAttack = !isOurs && !isPactAlly
    val canCommand = isOurs && (empire.allianceRole == "LEADER" || empire.allianceRole == "OFFICER")
    val myGarrison = stronghold.garrisonOf(empire.userId)
    val travelMs = GameCatalog.marchDurationMs(
        empire.coordX, empire.coordY, stronghold.coordX, stronghold.coordY, alliance
    )

    var troopShare by remember(stronghold.id) { mutableFloatStateOf(0.5f) }
    var aidText by remember(stronghold.id) { mutableStateOf("") }
    val sendInf = (empire.infantry * troopShare).roundToLong()
    val sendCav = (empire.cavalry * troopShare).roundToLong()
    val sendArc = (empire.archers * troopShare).roundToLong()
    val sendSiege = (empire.siege * troopShare).roundToLong()
    val sentPower = sendInf * TroopType.INFANTRY.power + sendCav * TroopType.CAVALRY.power +
        sendArc * TroopType.ARCHERS.power + sendSiege * TroopType.SIEGE.power

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ForgedIron
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stronghold.displayName, style = MaterialTheme.typography.headlineSmall, color = ParchmentIvory)
                    Text(
                        "${stronghold.allianceName} • (${stronghold.coordX}, ${stronghold.coordY}) • ETA ${formatCountdown(travelMs)}",
                        style = MonoStatStyle,
                        color = CrownGold
                    )
                }
                val (badge, badgeColor) = when {
                    isOurs -> "OURS" to CrownGold
                    isPactAlly -> "PACT" to RoyalAzure
                    else -> "RIVAL" to EmberCrimson
                }
                Surface(color = BurnishedCharcoal, border = BorderStroke(1.dp, badgeColor), shape = RoundedCornerShape(8.dp)) {
                    Text(badge, style = MaterialTheme.typography.labelMedium, color = badgeColor,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }

            val wallFrac = stronghold.durability.toFloat() / stronghold.maxDurability.toFloat()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Walls ${formatCompact(stronghold.durability)} / ${formatCompact(stronghold.maxDurability)}",
                        style = MaterialTheme.typography.bodyMedium, color = ParchmentIvory)
                    if (stronghold.underSiege) {
                        Text("UNDER SIEGE", style = MaterialTheme.typography.labelMedium, color = EmberCrimson)
                    }
                }
                LinearProgressIndicator(
                    progress = { wallFrac.coerceIn(0f, 1f) },
                    color = if (wallFrac > 0.5f) ArcaneEmerald else if (wallFrac > 0.25f) CrownGold else WarCrimson,
                    trackColor = BurnishedCharcoal,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Garrison ${formatCompact(stronghold.garrisonPower)} • Defense ~${formatCompact(stronghold.defensePower(0L))} (Lv.${stronghold.level} walls)",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeatheredAsh
                )
            }

            if (stronghold.callForAid) {
                Surface(color = CrownGold.copy(alpha = 0.12f), border = BorderStroke(1.dp, CrownGold), shape = RoundedCornerShape(10.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("CALL FOR AID", style = MaterialTheme.typography.labelMedium, color = CrownGold)
                        Text("\"${stronghold.aidMessage}\"", style = MaterialTheme.typography.bodyMedium, color = ParchmentIvory)
                        Text("Any kingdom may send relief troops while this call stands.",
                            style = MaterialTheme.typography.bodySmall, color = WeatheredAsh)
                    }
                }
            }

            if (stronghold.garrisons.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("DEFENDERS", style = MaterialTheme.typography.labelMedium, color = CrownGold)
                    stronghold.garrisons.take(8).forEach { g ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                g.playerName + if (g.isAlliedRelief) "  (relief)" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (g.playerUid == empire.userId) GildedHighlight else ParchmentIvory,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(formatCompact(g.troopPower), style = MonoStatStyle, color = WeatheredAsh)
                        }
                    }
                    if (stronghold.garrisons.size > 8) {
                        Text("+${stronghold.garrisons.size - 8} more", style = MaterialTheme.typography.bodySmall, color = WeatheredAsh)
                    }
                }
            }

            if (canReinforce || canAttack) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Troops to send", style = MaterialTheme.typography.titleSmall, color = ParchmentIvory)
                    Text("${(troopShare * 100).toInt()}% (PWR ${formatCompact(sentPower)})", style = MonoStatStyle, color = GildedHighlight)
                }
                Slider(
                    value = troopShare,
                    onValueChange = { troopShare = it },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = CrownGold,
                        activeTrackColor = if (canReinforce) RoyalAzure else WarCrimson,
                        inactiveTrackColor = BurnishedCharcoal
                    )
                )
            }

            if (canReinforce) {
                GildedButton(
                    text = if (isOurs || isPactAlly) "Reinforce garrison" else "Send relief troops",
                    icon = Icons.Filled.Shield,
                    enabled = sentPower > 0L,
                    onClick = { onReinforce(sendInf, sendCav, sendArc, sendSiege) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (canAttack) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CrimsonButton(
                        text = "Siege",
                        icon = Icons.Filled.Whatshot,
                        enabled = sentPower > 0L,
                        onClick = { onSiege(sendInf, sendCav, sendArc, sendSiege) },
                        modifier = Modifier.weight(1f)
                    )
                    GildedButton(
                        text = "Rally",
                        icon = Icons.Filled.Groups,
                        enabled = alliance != null,
                        onClick = onRally,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    "Every victory breaks the walls. At zero the stronghold falls and the attacker takes the spoils.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeatheredAsh
                )
            }

            if (myGarrison != null) {
                OutlinedButton(
                    onClick = onWithdraw,
                    border = BorderStroke(1.dp, EtchedSteel),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Withdraw my troops (${formatCompact(myGarrison.troopPower)})", color = ParchmentIvory)
                }
            }

            if (canCommand) {
                if (stronghold.callForAid) {
                    OutlinedButton(
                        onClick = onEndCallForAid,
                        border = BorderStroke(1.dp, CrownGold),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Lower the call for aid", color = CrownGold) }
                } else {
                    OutlinedTextField(
                        value = aidText,
                        onValueChange = { aidText = it.take(140) },
                        label = { Text("Message to other kingdoms") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    GildedButton(
                        text = "Call for aid",
                        icon = Icons.Filled.Campaign,
                        onClick = { onCallForAid(aidText) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
fun FoundStrongholdDialog(
    x: Long,
    y: Long,
    empire: PlayerEmpire,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val affordable = empire.stone >= STRONGHOLD_STONE && empire.gold >= STRONGHOLD_GOLD
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForgedIron,
        title = { Text("Raise a stronghold at ($x, $y)?", color = CrownGold) },
        text = {
            Text(
                "Costs ${formatCompact(STRONGHOLD_STONE)} Stone and ${formatCompact(STRONGHOLD_GOLD)} Gold. " +
                    "Your alliance can only hold one. Members and pact allies garrison it; " +
                    "enemies can siege it until the walls fall." +
                    if (!affordable) "\n\nYou don't have enough Stone or Gold yet." else "",
                color = ParchmentIvory
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = affordable) { Text("Raise it", color = CrownGold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = WeatheredAsh) }
        }
    )
}

/** The alliance "Pacts" tab: our stronghold at a glance, plus pacts with other alliances. */
@Composable
fun AlliancePactsPanel(
    empire: PlayerEmpire,
    alliance: Alliance,
    allAlliances: List<Alliance>,
    pacts: List<AlliancePact>,
    ownStronghold: Stronghold?,
    onProposePact: (Alliance) -> Unit,
    onRespondPact: (AlliancePact, Boolean) -> Unit,
    onEndPact: (AlliancePact) -> Unit
) {
    val isLeader = empire.allianceRole == "LEADER"
    val active = pacts.filter { it.status == "ACTIVE" }
    val incoming = pacts.filter { it.status == "PENDING" && !it.proposedByUs }
    val outgoing = pacts.filter { it.status == "PENDING" && it.proposedByUs }
    val busyIds = pacts.map { it.otherAllianceId }.toSet()
    val others = allAlliances.filter { it.id != alliance.id && it.id !in busyIds }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ObsidianCard(highlightBorder = CrownGold.copy(alpha = 0.6f)) {
                Text("ALLIANCE STRONGHOLD", style = MaterialTheme.typography.titleSmall, color = CrownGold)
                Spacer(Modifier.height(4.dp))
                if (ownStronghold == null) {
                    Text(
                        if (isLeader) "None yet. Open the Realm Map and long-press an open spot to raise one (3,000 Stone, 2,000 Gold)."
                        else "None yet. Your leader can raise one from the Realm Map.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WeatheredAsh
                    )
                } else {
                    Text(
                        "At (${ownStronghold.coordX}, ${ownStronghold.coordY}) • Walls ${formatCompact(ownStronghold.durability)}/${formatCompact(ownStronghold.maxDurability)} • " +
                            "Garrison ${formatCompact(ownStronghold.garrisonPower)} from ${ownStronghold.garrisons.size} lords",
                        style = MaterialTheme.typography.bodySmall,
                        color = ParchmentIvory
                    )
                    if (ownStronghold.underSiege) {
                        Text("UNDER SIEGE — send troops!", style = MaterialTheme.typography.labelMedium, color = EmberCrimson)
                    }
                    if (ownStronghold.callForAid) {
                        Text("Calling for aid: \"${ownStronghold.aidMessage}\"", style = MaterialTheme.typography.bodySmall, color = CrownGold)
                    }
                    Text("Tap it on the Realm Map to reinforce or call for aid.", style = MaterialTheme.typography.bodySmall, color = WeatheredAsh)
                }
            }
        }

        item {
            Text("PACTS (${active.size}/3)", style = MaterialTheme.typography.titleSmall, color = CrownGold)
            Text(
                "Pact allies can't attack each other and can always reinforce each other's stronghold.",
                style = MaterialTheme.typography.bodySmall,
                color = WeatheredAsh
            )
        }

        if (incoming.isNotEmpty()) {
            items(incoming, key = { "in-" + it.id }) { pact ->
                ObsidianCard(highlightBorder = CrownGold) {
                    Text("[${pact.otherTag}] ${pact.otherName} offers a pact", style = MaterialTheme.typography.titleSmall, color = ParchmentIvory)
                    if (isLeader) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            GildedButton(text = "Accept", icon = Icons.Filled.Handshake,
                                onClick = { onRespondPact(pact, true) }, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { onRespondPact(pact, false) },
                                border = BorderStroke(1.dp, EtchedSteel), modifier = Modifier.weight(1f)) {
                                Text("Decline", color = ParchmentIvory)
                            }
                        }
                    } else {
                        Text("Waiting for your leader to answer.", style = MaterialTheme.typography.bodySmall, color = WeatheredAsh)
                    }
                }
            }
        }

        items(active, key = { "act-" + it.id }) { pact ->
            ObsidianCard(highlightBorder = RoyalAzure) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("[${pact.otherTag}] ${pact.otherName}", style = MaterialTheme.typography.titleSmall,
                        color = ParchmentIvory, modifier = Modifier.weight(1f))
                    Text("PACT", style = MaterialTheme.typography.labelMedium, color = RoyalAzure)
                }
                if (isLeader) {
                    TextButton(onClick = { onEndPact(pact) }) { Text("End pact", color = EmberCrimson) }
                }
            }
        }

        items(outgoing, key = { "out-" + it.id }) { pact ->
            ObsidianCard {
                Text("Offer sent to [${pact.otherTag}] ${pact.otherName}", style = MaterialTheme.typography.bodyMedium, color = ParchmentIvory)
                if (isLeader) {
                    TextButton(onClick = { onEndPact(pact) }) { Text("Withdraw offer", color = WeatheredAsh) }
                }
            }
        }

        if (active.isEmpty() && incoming.isEmpty() && outgoing.isEmpty()) {
            item {
                Text("No pacts yet.", style = MaterialTheme.typography.bodySmall, color = WeatheredAsh)
            }
        }

        if (isLeader && others.isNotEmpty() && active.size < 3) {
            item {
                Spacer(Modifier.height(6.dp))
                Text("OFFER A PACT", style = MaterialTheme.typography.titleSmall, color = CrownGold)
            }
            items(others.take(20), key = { "other-" + it.id }) { other ->
                ObsidianCard {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("[${other.tag}] ${other.name}", style = MaterialTheme.typography.titleSmall,
                                color = ParchmentIvory, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${other.memberCount} lords • PWR ${formatCompact(other.totalPower)}",
                                style = MaterialTheme.typography.bodySmall, color = WeatheredAsh)
                        }
                        TextButton(onClick = { onProposePact(other) }) { Text("Offer pact", color = CrownGold) }
                    }
                }
            }
        }
    }
}
