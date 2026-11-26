package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.LordProfile
import com.example.data.formatCompact
import com.example.data.nextTitleAt
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
import com.example.ui.theme.WeatheredAsh
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A lord's profile card. Opens for yourself (tap your avatar) with edit buttons, or for any other
 * lord (from their castle or the alliance roster) with an invite button when you can recruit them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LordProfileSheet(
    playerId: String,
    isMe: Boolean,
    myAllianceId: String,
    reloadKey: Any?,
    loadProfile: suspend (String) -> Result<LordProfile>,
    canInvite: Boolean,
    onInvite: () -> Unit,
    onChangeAvatar: () -> Unit,
    onRename: (String) -> Unit,
    onSetMotto: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val result by produceState<Result<LordProfile>?>(initialValue = null, playerId, reloadKey) {
        value = loadProfile(playerId)
    }
    var mottoOverride by remember(playerId) { mutableStateOf<String?>(null) }
    var editMotto by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ForgedIron
    ) {
        val profile = result?.getOrNull()
        when {
            result == null -> Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CrownGold)
            }
            profile == null -> Box(Modifier.fillMaxWidth().height(200.dp).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    result?.exceptionOrNull()?.localizedMessage ?: "This lord's records are lost.",
                    color = WeatheredAsh, textAlign = TextAlign.Center
                )
            }
            else -> {
                val ring = when {
                    isMe -> CrownGold
                    myAllianceId.isNotBlank() && profile.allianceId == myAllianceId -> RoyalAzure
                    else -> EmberCrimson
                }
                val motto = mottoOverride ?: profile.motto
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Portrait, title and name
                    LordAvatar(profile.avatar, profile.avatarUrl, 104.dp, ringColor = ring)
                    Surface(color = CrownGold.copy(alpha = 0.15f), border = BorderStroke(1.dp, CrownGold), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            profile.title.uppercase(Locale.ROOT),
                            style = MaterialTheme.typography.labelMedium,
                            color = CrownGold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile.username, style = MaterialTheme.typography.headlineSmall, color = ParchmentIvory)
                        if (isMe) {
                            TextButton(onClick = { editName = true }) { Text("Rename", color = WeatheredAsh) }
                        }
                    }
                    if (profile.isBot) {
                        Text("AI Lord", style = MaterialTheme.typography.labelMedium, color = WeatheredAsh)
                    }
                    Text(
                        buildString {
                            if (!profile.allianceTag.isNullOrBlank()) {
                                append("[${profile.allianceTag}] ${profile.allianceName.orEmpty()} • ")
                                append(roleName(profile.allianceRole))
                            } else append("No alliance")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (profile.allianceTag.isNullOrBlank()) WeatheredAsh else ring
                    )
                    Text(
                        "${profile.realmName ?: profile.realmId} • (${profile.x}, ${profile.y}) • Keep Lv.${profile.keepLevel}",
                        style = MonoStatStyle,
                        color = WeatheredAsh
                    )

                    // Motto
                    Surface(
                        color = BurnishedCharcoal,
                        border = BorderStroke(1.dp, EtchedSteel),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (motto.isBlank()) (if (isMe) "Write a motto for your house…" else "This lord keeps their own counsel.")
                                else "“$motto”",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = if (motto.isBlank()) WeatheredAsh else ParchmentIvory,
                                modifier = Modifier.weight(1f)
                            )
                            if (isMe) {
                                TextButton(onClick = { editMotto = true }) { Text("Edit", color = CrownGold) }
                            }
                        }
                    }

                    // Honor and the next title
                    val next = nextTitleAt(profile.honor)
                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Honor ${formatCompact(profile.honor)}", style = MaterialTheme.typography.titleSmall, color = ParchmentIvory)
                            Text(
                                if (next != null) "Next title at ${formatCompact(next)}" else "Highest title reached",
                                style = MaterialTheme.typography.labelSmall,
                                color = WeatheredAsh
                            )
                        }
                        if (next != null) {
                            LinearProgressIndicator(
                                progress = { (profile.honor.toFloat() / next.toFloat()).coerceIn(0f, 1f) },
                                color = CrownGold,
                                trackColor = BurnishedCharcoal,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // War record
                    Text("WAR RECORD", style = MaterialTheme.typography.labelMedium, color = CrownGold, modifier = Modifier.fillMaxWidth())
                    StatRow(
                        "Power" to formatCompact(profile.power),
                        "Win rate" to (if (profile.battles == 0L) "—" else "${profile.winRatePercent}%")
                    )
                    StatRow(
                        "Attacks won" to "${profile.attacksWon} / ${profile.attacksWon + profile.attacksLost}",
                        "Defenses held" to "${profile.defensesWon} / ${profile.defensesWon + profile.defensesLost}"
                    )
                    StatRow(
                        "Troops slain" to formatCompact(profile.troopsSlain),
                        "Troops lost" to formatCompact(profile.troopsLost)
                    )
                    StatRow(
                        "Rallies joined" to "${profile.ralliesJoined}",
                        "Expeditions gathered" to "${profile.gathers}"
                    )
                    sinceText(profile.createdAt)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = WeatheredAsh)
                    }

                    if (isMe) {
                        GildedButton(
                            text = "Change avatar",
                            icon = Icons.Filled.Face,
                            onClick = onChangeAvatar,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (canInvite && profile.allianceTag.isNullOrBlank() && !profile.isBot) {
                        GildedButton(
                            text = "Invite to alliance",
                            icon = Icons.Filled.PersonAdd,
                            onClick = onInvite,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (editMotto) {
        var text by remember { mutableStateOf(mottoOverride ?: result?.getOrNull()?.motto.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editMotto = false },
            containerColor = ForgedIron,
            title = { Text("Your house motto", color = CrownGold) },
            text = {
                Column {
                    OutlinedTextField(value = text, onValueChange = { text = it.take(120) }, singleLine = false,
                        modifier = Modifier.fillMaxWidth())
                    Text("${text.length}/120", style = MaterialTheme.typography.labelSmall, color = WeatheredAsh)
                }
            },
            confirmButton = {
                TextButton(onClick = { onSetMotto(text.trim()); mottoOverride = text.trim(); editMotto = false }) {
                    Text("Proclaim", color = CrownGold)
                }
            },
            dismissButton = { TextButton(onClick = { editMotto = false }) { Text("Cancel", color = WeatheredAsh) } }
        )
    }

    if (editName) {
        var text by remember { mutableStateOf(result?.getOrNull()?.username.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editName = false },
            containerColor = ForgedIron,
            title = { Text("Rename your lord", color = CrownGold) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(28) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(onClick = { onRename(text.trim()); editName = false }, enabled = text.trim().length >= 2) {
                    Text("Proclaim", color = CrownGold)
                }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancel", color = WeatheredAsh) } }
        )
    }
}

@Composable
private fun StatRow(left: Pair<String, String>, right: Pair<String, String>) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile(left.first, left.second, Modifier.weight(1f))
        StatTile(right.first, right.second, Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier) {
    Surface(color = BurnishedCharcoal, border = BorderStroke(1.dp, EtchedSteel), shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = WeatheredAsh)
            Text(value, style = MonoStatStyle, color = GildedHighlight)
        }
    }
}

private fun roleName(role: String) = when (role) {
    "LEADER" -> "Leader (R5)"
    "OFFICER" -> "Officer (R4)"
    "MEMBER" -> "Member"
    else -> ""
}

private fun sinceText(createdAt: String?): String? = runCatching {
    val t = OffsetDateTime.parse(createdAt)
    "Lord since " + t.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH))
}.getOrNull()
