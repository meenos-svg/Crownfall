package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.PlayerEmpire
import com.example.data.Realm
import com.example.data.TroopType
import com.example.data.VisitorCamp
import com.example.data.formatCompact
import com.example.data.formatCountdown
import com.example.ui.theme.BasaltSlate
import com.example.ui.theme.BurnishedCharcoal
import com.example.ui.theme.CrownGold
import com.example.ui.theme.EtchedSteel
import com.example.ui.theme.ForgedIron
import com.example.ui.theme.GildedHighlight
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.ParchmentIvory
import com.example.ui.theme.RoyalAzure
import com.example.ui.theme.WeatheredAsh
import kotlin.math.roundToLong

/** A row of realm chips at the top of the map: home, the realm you're camped in, and the rest. */
@Composable
fun RealmSwitcher(
    realms: List<Realm>,
    shownRealmId: String,
    homeRealmId: String,
    campRealmId: String?,
    onSelectRealm: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(realms.sortedBy { if (it.id == homeRealmId) "" else it.id }, key = { it.id }) { realm ->
            val selected = realm.id == shownRealmId
            val label = buildString {
                append(realm.name)
                if (realm.id == homeRealmId) append(" • Home")
                if (realm.id == campRealmId) append(" • Camp")
            }
            Surface(
                color = if (selected) CrownGold.copy(alpha = 0.22f) else BasaltSlate.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, if (selected) CrownGold else EtchedSteel),
                shape = RoundedCornerShape(16.dp),
                onClick = { onSelectRealm(realm.id) }
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) CrownGold else ParchmentIvory,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** Tells the player where their expedition is and what they can do in the realm on screen. */
@Composable
fun ExpeditionBanner(
    realms: List<Realm>,
    shownRealmId: String,
    visiting: Boolean,
    myCamp: VisitorCamp?,
    nowEpochMs: Long,
    onRecallCamp: () -> Unit
) {
    fun realmName(id: String) = realms.firstOrNull { it.id == id }?.name ?: "another realm"
    val remaining = ((myCamp?.arrivesAtEpochMs ?: 0L) - nowEpochMs).coerceAtLeast(0L)
    val campHere = myCamp?.realmId == shownRealmId

    val text = when {
        myCamp == null -> "Visiting ${realmName(shownRealmId)}. Long-press an open spot to send an expedition through the Waygate."
        myCamp.status == "RETURNING" -> "Expedition heading home through the Waygate • ${formatCountdown(remaining)}"
        !campHere && visiting -> "Your expedition is in ${realmName(myCamp.realmId)}. Recall it to camp here instead."
        !campHere -> "Expedition camped in ${realmName(myCamp.realmId)} • PWR ${formatCompact(myCamp.troopPower)}"
        myCamp.status == "TRAVELING" -> "Expedition crossing the Waygate • arrives in ${formatCountdown(remaining)}"
        else -> "Camped here • PWR ${formatCompact(myCamp.troopPower)}. Tap targets to raid, gather or reinforce with camp troops."
    }
    val canRecall = myCamp != null && myCamp.status != "RETURNING"

    Surface(
        color = BasaltSlate.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, RoyalAzure.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = ParchmentIvory,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (canRecall) {
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = onRecallCamp) { Text("Recall", color = CrownGold) }
            }
        }
    }
}

@Composable
fun ExpeditionDialog(
    x: Long,
    y: Long,
    realmName: String,
    homeEmpire: PlayerEmpire,
    onConfirm: (Long, Long, Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    var share by remember { mutableFloatStateOf(0.5f) }
    val inf = (homeEmpire.infantry * share).roundToLong()
    val cav = (homeEmpire.cavalry * share).roundToLong()
    val arc = (homeEmpire.archers * share).roundToLong()
    val sg = (homeEmpire.siege * share).roundToLong()
    val power = inf * TroopType.INFANTRY.power + cav * TroopType.CAVALRY.power +
        arc * TroopType.ARCHERS.power + sg * TroopType.SIEGE.power
    val travelSec = (90L - homeEmpire.waygateLevelOrOne() * 4L).coerceAtLeast(30L)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForgedIron,
        title = { Text("Expedition to $realmName", color = CrownGold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Your army crosses the Waygate (${travelSec}s) and makes camp at ($x, $y). " +
                        "From the camp you can raid, gather and reinforce strongholds in this realm. " +
                        "Locals can attack your camp, so send enough to hold it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ParchmentIvory
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Troops", style = MaterialTheme.typography.titleSmall, color = ParchmentIvory)
                    Text("${(share * 100).toInt()}% (PWR ${formatCompact(power)})", style = MonoStatStyle, color = GildedHighlight)
                }
                Slider(
                    value = share,
                    onValueChange = { share = it },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = CrownGold,
                        activeTrackColor = RoyalAzure,
                        inactiveTrackColor = BurnishedCharcoal
                    )
                )
                Text(
                    "${formatCompact(inf)} infantry • ${formatCompact(cav)} cavalry • ${formatCompact(arc)} archers • ${formatCompact(sg)} siege",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeatheredAsh
                )
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(inf, cav, arc, sg) }, enabled = power > 0L) {
                Text("March", color = CrownGold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = WeatheredAsh) }
        }
    )
}

/** The Waygate level isn't on PlayerEmpire yet; treat it as level 1 for the time estimate. */
private fun PlayerEmpire.waygateLevelOrOne(): Long = 1L
