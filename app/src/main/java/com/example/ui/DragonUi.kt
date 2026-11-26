package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Alliance
import com.example.data.Dragon
import com.example.data.DragonBoard
import com.example.data.GameCatalog
import com.example.data.PlayerEmpire
import com.example.data.TroopType
import com.example.data.formatCompact
import com.example.data.formatCountdown
import com.example.ui.theme.BurnishedCharcoal
import com.example.ui.theme.CrownGold
import com.example.ui.theme.EmberCrimson
import com.example.ui.theme.ForgedIron
import com.example.ui.theme.GildedHighlight
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.ParchmentIvory
import com.example.ui.theme.RoyalAzure
import com.example.ui.theme.WarCrimson
import com.example.ui.theme.WeatheredAsh
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

private val DragonRed = Color(0xFFE53935)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DragonSheet(
    dragon: Dragon,
    empire: PlayerEmpire,
    alliance: Alliance?,
    visiting: Boolean,
    nowEpochMs: Long,
    onDismiss: () -> Unit,
    onStrike: (Long, Long, Long, Long) -> Unit,
    onRally: () -> Unit,
    onLoadBoard: suspend (String) -> DragonBoard?,
    onViewProfile: (String) -> Unit
) {
    var board by remember(dragon.id) { mutableStateOf<DragonBoard?>(null) }
    // Refresh the leaderboard while the sheet is open.
    LaunchedEffect(dragon.id) {
        while (true) {
            onLoadBoard(dragon.id)?.let { board = it }
            delay(10_000L)
        }
    }

    val travelMs = GameCatalog.marchDurationMs(empire.coordX, empire.coordY, dragon.coordX, dragon.coordY, alliance)
    var troopShare by remember(dragon.id) { mutableFloatStateOf(0.5f) }
    val sendInf = (empire.infantry * troopShare).roundToLong()
    val sendCav = (empire.cavalry * troopShare).roundToLong()
    val sendArc = (empire.archers * troopShare).roundToLong()
    val sendSiege = (empire.siege * troopShare).roundToLong()
    val sentPower = sendInf * TroopType.INFANTRY.power + sendCav * TroopType.CAVALRY.power +
        sendArc * TroopType.ARCHERS.power + sendSiege * TroopType.SIEGE.power
    // Same as the server: power plus 12 per siege engine, before attack tech and the ±10% roll.
    val estDamage = (sentPower + sendSiege * 12).coerceAtMost(dragon.hp)
    val burnedUnits = ((sendInf + sendCav + sendArc + sendSiege) * dragon.burnPercent / 100.0).roundToLong()

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
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(dragon.name, style = MaterialTheme.typography.headlineSmall, color = ParchmentIvory)
                    Text("The Red Dragon • (${dragon.coordX}, ${dragon.coordY}) • ETA ${formatCountdown(travelMs)}",
                        style = MonoStatStyle, color = CrownGold)
                }
                Surface(color = BurnishedCharcoal, border = BorderStroke(1.dp, DragonRed), shape = RoundedCornerShape(8.dp)) {
                    Text("LV ${dragon.level}", style = MaterialTheme.typography.labelLarge, color = DragonRed,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { dragon.hpFraction },
                    color = DragonRed,
                    trackColor = BurnishedCharcoal,
                    modifier = Modifier.fillMaxWidth().height(10.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${formatCompact(dragon.hp)} / ${formatCompact(dragon.maxHp)} HP", style = MonoStatStyle, color = ParchmentIvory)
                    val left = (dragon.fleesAtMs - nowEpochMs).coerceAtLeast(0L)
                    Text("Flies off in ${formatCountdown(left)}", style = MonoStatStyle, color = WeatheredAsh)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("THE HOARD", style = MaterialTheme.typography.labelMedium, color = CrownGold)
                Text("${formatCompact(dragon.hoardFood)} each of food, timber and stone • ${formatCompact(dragon.hoardGold)} gold",
                    style = MaterialTheme.typography.bodyMedium, color = GildedHighlight)
                Text("Shared by damage dealt. Alliances share ${formatCompact(dragon.hoardTechXp)} tech XP. " +
                    "The killing blow earns ${formatCompact(dragon.killingBlowGold)} gold more.",
                    style = MaterialTheme.typography.bodySmall, color = WeatheredAsh)
                Text("Its fire burns ${dragon.burnPercent}% of every army that strikes it (rallies lose a quarter less and hit 25% harder).",
                    style = MaterialTheme.typography.bodySmall, color = EmberCrimson)
            }

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
                    activeTrackColor = WarCrimson,
                    inactiveTrackColor = BurnishedCharcoal
                )
            )
            Text("~${formatCompact(estDamage)} damage • ~${formatCompact(burnedUnits)} troops burned",
                style = MonoStatStyle, color = WeatheredAsh)

            CrimsonButton(
                text = "Strike the dragon",
                icon = Icons.Filled.Whatshot,
                enabled = sentPower > 0L,
                onClick = { onStrike(sendInf, sendCav, sendArc, sendSiege) },
                modifier = Modifier.fillMaxWidth()
            )
            if (alliance != null && !visiting) {
                GildedButton(
                    text = "Sound a War Rally (all troops at home)",
                    icon = Icons.Filled.Campaign,
                    enabled = empire.infantry + empire.cavalry + empire.archers + empire.siege > 0L,
                    onClick = onRally,
                    modifier = Modifier.fillMaxWidth()
                )
            } else if (alliance == null) {
                Text("Join an alliance to rally against the dragon together.",
                    style = MaterialTheme.typography.bodySmall, color = WeatheredAsh)
            }

            DragonLeaderboard(board, empire.userId, onViewProfile)
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun DragonLeaderboard(board: DragonBoard?, myUid: String, onViewProfile: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("DRAGONSLAYERS", style = MaterialTheme.typography.labelMedium, color = CrownGold)
        when {
            board == null -> Text("Loading…", style = MaterialTheme.typography.bodySmall, color = WeatheredAsh)
            board.lords.isEmpty() -> Text("No one has drawn blood yet. Be the first.",
                style = MaterialTheme.typography.bodySmall, color = WeatheredAsh)
            else -> {
                board.mine?.let { mine ->
                    Text("You: #${mine.rank} • ${formatCompact(mine.damage)} damage in ${mine.hits} hits" +
                        " (${pct(mine.damage, board.total)}%)",
                        style = MonoStatStyle, color = GildedHighlight)
                }
                board.lords.forEachIndexed { i, lord ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onViewProfile(lord.id) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#${i + 1}", style = MonoStatStyle, color = if (i == 0) CrownGold else WeatheredAsh,
                            modifier = Modifier.width(30.dp))
                        LordAvatar(avatar = lord.avatar, avatarUrl = lord.avatarUrl, size = 28.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            (lord.tag?.let { "[$it] " } ?: "") + lord.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (lord.id == myUid) GildedHighlight else ParchmentIvory,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${formatCompact(lord.damage)} (${pct(lord.damage, board.total)}%)",
                            style = MonoStatStyle, color = WeatheredAsh)
                    }
                }
                if (board.alliances.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("ALLIANCES", style = MaterialTheme.typography.labelMedium, color = RoyalAzure)
                    board.alliances.forEach { a ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("[${a.tag}] ${a.name}", style = MaterialTheme.typography.bodySmall, color = ParchmentIvory,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${pct(a.damage, board.total)}%", style = MonoStatStyle, color = WeatheredAsh)
                        }
                    }
                }
            }
        }
    }
}

private fun pct(part: Long, total: Long): Int = if (total <= 0) 0 else (part * 100 / total).toInt()

/** Shown on the map while the realm waits for its next dragon. */
@Composable
fun DragonAwayBanner(dragon: Dragon, nowEpochMs: Long, modifier: Modifier = Modifier) {
    val left = (dragon.respawnAtMs - nowEpochMs).coerceAtLeast(0L)
    val text = when (dragon.status) {
        "SLAIN" -> "${dragon.name} was slain by ${dragon.slainByName.ifBlank { "a brave lord" }}. A new dragon lands in ${formatCountdown(left)}"
        else -> "${dragon.name} flew off. A new dragon lands in ${formatCountdown(left)}"
    }
    Surface(
        color = ForgedIron.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, DragonRed.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = ParchmentIvory,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
