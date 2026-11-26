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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Alliance
import com.example.data.AllianceTechType
import com.example.data.BattleLog
import com.example.data.PlayerEmpire
import com.example.data.RealmKingdom
import com.example.data.formatCompact
import com.example.ui.theme.ArcaneEmerald
import com.example.ui.theme.BurnishedCharcoal
import com.example.ui.theme.CrownGold
import com.example.ui.theme.DarkInkOnGold
import com.example.ui.theme.EmberCrimson
import com.example.ui.theme.EtchedSteel
import com.example.ui.theme.ForgedIron
import com.example.ui.theme.GildedHighlight
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentIvory
import com.example.ui.theme.StoneSilver
import com.example.ui.theme.WarCrimson
import com.example.ui.theme.WeatheredAsh

@Composable
fun WarRoomScreen(
    empire: PlayerEmpire,
    alliance: Alliance?,
    kingdoms: List<RealmKingdom>,
    alliances: List<Alliance>,
    battleLogs: List<BattleLog>,
    onRenameLord: (String) -> Unit,
    onActivateShield: () -> Unit,
    onSignOut: () -> Unit
) {
    var mainTab by remember { mutableIntStateOf(0) } // 0 = Rankings, 1 = Battle Logs, 2 = Lord Profile
    var rankingType by remember { mutableIntStateOf(0) } // 0 = Top Lords, 1 = Top Alliances
    var logFilter by remember { mutableIntStateOf(0) } // 0 = Realm, 1 = Mine, 2 = Alliance

    val filteredLogs = remember(battleLogs, logFilter, empire.userId, empire.allianceId) {
        when (logFilter) {
            1 -> battleLogs.filter { it.attackerUid == empire.userId || it.defenderUid == empire.userId }
            2 -> battleLogs.filter { empire.allianceId.isNotBlank() && it.allianceId == empire.allianceId }
            else -> battleLogs
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // War Room Siege Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_battle_crest_1795493493410),
                    contentDescription = stringResource(R.string.cd_battle_crest),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(ObsidianVoid.copy(alpha = 0.2f), ObsidianVoid.copy(alpha = 0.88f))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "IMPERIAL WAR ROOM",
                        style = MaterialTheme.typography.headlineSmall,
                        color = CrownGold
                    )
                    Text(
                        text = "${kingdoms.size} Active Lords • ${alliances.size} Alliances • ${battleLogs.size} Chronicles",
                        style = MaterialTheme.typography.bodySmall,
                        color = ParchmentIvory
                    )
                }
            }
        }

        // Primary War Room Tabs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WarTabChip(
                    selected = mainTab == 0,
                    label = "Rankings",
                    icon = Icons.Filled.EmojiEvents,
                    onClick = { mainTab = 0 },
                    modifier = Modifier.weight(1f).testTag("war_tab_rankings")
                )
                WarTabChip(
                    selected = mainTab == 1,
                    label = "Battle Logs",
                    icon = Icons.Filled.HistoryEdu,
                    onClick = { mainTab = 1 },
                    modifier = Modifier.weight(1f).testTag("war_tab_logs")
                )
                WarTabChip(
                    selected = mainTab == 2,
                    label = "Lord",
                    icon = Icons.Filled.Person,
                    onClick = { mainTab = 2 },
                    modifier = Modifier.weight(1f).testTag("war_tab_profile")
                )
            }
        }

        when (mainTab) {
            0 -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = rankingType == 0,
                            onClick = { rankingType = 0 },
                            label = { Text("Top Lords") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CrownGold,
                                selectedLabelColor = DarkInkOnGold,
                                containerColor = ForgedIron,
                                labelColor = ParchmentIvory
                            ),
                            modifier = Modifier.weight(1f).height(44.dp)
                        )
                        FilterChip(
                            selected = rankingType == 1,
                            onClick = { rankingType = 1 },
                            label = { Text("Top Alliances") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = WarCrimson,
                                selectedLabelColor = ParchmentIvory,
                                containerColor = ForgedIron,
                                labelColor = ParchmentIvory
                            ),
                            modifier = Modifier.weight(1f).height(44.dp)
                        )
                    }
                }

                if (rankingType == 0) {
                    if (kingdoms.isEmpty()) {
                        item {
                            ObsidianCard {
                                Text(
                                    "No lords recorded in Realm I yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WeatheredAsh
                                )
                            }
                        }
                    } else {
                        itemsIndexed(kingdoms, key = { _, k -> k.ownerUid }) { index, k ->
                            val isMe = k.ownerUid == empire.userId
                            ObsidianCard(highlightBorder = if (isMe) CrownGold else EtchedSteel) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        RankBadge(index + 1)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = (if (k.allianceTag.isNotBlank()) "[${k.allianceTag}] " else "") + k.lordName,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (isMe) GildedHighlight else ParchmentIvory,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Keep Lv.${k.keepLevel} • (${k.coordX}, ${k.coordY})",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = WeatheredAsh
                                            )
                                        }
                                    }
                                    Text(
                                        text = formatCompact(k.power),
                                        style = MonoStatStyle,
                                        color = CrownGold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    if (alliances.isEmpty()) {
                        item {
                            ObsidianCard {
                                Text(
                                    "No alliances have been forged yet. Found the first banner in the Alliance tab!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = WeatheredAsh
                                )
                            }
                        }
                    } else {
                        itemsIndexed(alliances, key = { _, a -> a.id }) { index, a ->
                            ObsidianCard(highlightBorder = if (a.id == empire.allianceId) CrownGold else EtchedSteel) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        RankBadge(index + 1)
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "[${a.tag}] ${a.name}",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = ParchmentIvory,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${a.memberCount}/30 Lords • ${a.motto}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = WeatheredAsh,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Text(
                                        text = formatCompact(a.totalPower),
                                        style = MonoStatStyle,
                                        color = CrownGold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            1 -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All Realm", "My Battles", "Alliance").forEachIndexed { idx, title ->
                            FilterChip(
                                selected = logFilter == idx,
                                onClick = { logFilter = idx },
                                label = { Text(title) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = CrownGold,
                                    selectedLabelColor = DarkInkOnGold,
                                    containerColor = ForgedIron,
                                    labelColor = ParchmentIvory
                                ),
                                modifier = Modifier.weight(1f).height(42.dp)
                            )
                        }
                    }
                }

                if (filteredLogs.isEmpty()) {
                    item {
                        ObsidianCard {
                            Text(
                                text = "The War Room is quiet — dispatch a march or rally from the Realm Map to record your first conquest.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = WeatheredAsh
                            )
                        }
                    }
                } else {
                    items(filteredLogs, key = { it.id }) { log ->
                        BattleLogCard(log = log)
                    }
                }
            }

            2 -> {
                item {
                    LordProfileCard(
                        empire = empire,
                        alliance = alliance,
                        onRenameLord = onRenameLord,
                        onActivateShield = onActivateShield,
                        onSignOut = onSignOut
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun WarTabChip(
    selected: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = CrownGold,
            selectedLabelColor = DarkInkOnGold,
            selectedLeadingIconColor = DarkInkOnGold,
            containerColor = ForgedIron,
            labelColor = ParchmentIvory
        ),
        modifier = modifier.height(48.dp)
    )
}

@Composable
private fun RankBadge(rank: Int) {
    val color = when (rank) {
        1 -> CrownGold
        2 -> StoneSilver
        3 -> Color(0xFFCD7F32)
        else -> BurnishedCharcoal
    }
    val textColor = if (rank <= 3) DarkInkOnGold else ParchmentIvory
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text("#$rank", style = MaterialTheme.typography.labelMedium, color = textColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BattleLogCard(log: BattleLog) {
    val badgeColor = when {
        log.battleType == "GATHER" -> ArcaneEmerald
        log.defenderTag == "DRAGON" -> if (log.victory) CrownGold else EmberCrimson
        log.victory -> CrownGold
        else -> EmberCrimson
    }
    val badgeText = when {
        log.battleType == "GATHER" -> "HARVEST"
        log.defenderTag == "DRAGON" -> if (log.victory) "SLAIN" else "STRUCK"
        log.victory -> "VICTORY"
        else -> "DEFEAT"
    }
    ObsidianCard(highlightBorder = badgeColor.copy(alpha = 0.6f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${log.attackerName} vs ${log.defenderName}",
                style = MaterialTheme.typography.titleSmall,
                color = ParchmentIvory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Surface(
                color = badgeColor.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, badgeColor),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "${log.battleType} • $badgeText",
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "ATK ${formatCompact(log.attackerPower)} vs DEF ${formatCompact(log.defenderPower)} • Losses: ${log.troopsLost}",
            style = MonoStatStyle,
            color = WeatheredAsh
        )
        val totalLoot = log.lootFood + log.lootTimber + log.lootStone + log.lootGold
        if (totalLoot > 0L) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Plunder: +${formatCompact(log.lootFood)}F  +${formatCompact(log.lootTimber)}T  +${formatCompact(log.lootStone)}S  +${formatCompact(log.lootGold)}G",
                style = MaterialTheme.typography.bodySmall,
                color = GildedHighlight
            )
        }
    }
}

@Composable
private fun LordProfileCard(
    empire: PlayerEmpire,
    alliance: Alliance?,
    onRenameLord: (String) -> Unit,
    onActivateShield: () -> Unit,
    onSignOut: () -> Unit
) {
    var nameInput by remember(empire.lordName) { mutableStateOf(empire.lordName) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ObsidianCard(highlightBorder = CrownGold.copy(alpha = 0.7f)) {
            Text("ROYAL IDENTITY", style = MaterialTheme.typography.titleSmall, color = CrownGold)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Honor: ${formatCompact(empire.honor)} • Keep Lv.${empire.keepLevel} • Coords (${empire.coordX}, ${empire.coordY})",
                style = MonoStatStyle,
                color = ParchmentIvory
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = nameInput,
                onValueChange = { if (it.length <= 28) nameInput = it },
                label = { Text("Lord Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lord_name_input")
            )
            Spacer(Modifier.height(10.dp))
            GildedButton(
                text = "Proclaim New Title",
                onClick = { onRenameLord(nameInput) },
                enabled = nameInput.trim().length in 2..28 && nameInput.trim() != empire.lordName,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_lord_button")
            )
        }

        ObsidianCard(highlightBorder = ArcaneEmerald.copy(alpha = 0.6f)) {
            Text("OBSIDIAN PEACE SHIELD", style = MaterialTheme.typography.titleSmall, color = ArcaneEmerald)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Shields your Citadel from rival PvP raids for 1 hour (Cost: 250 Gold).",
                style = MaterialTheme.typography.bodySmall,
                color = WeatheredAsh
            )
            Spacer(Modifier.height(10.dp))
            GildedButton(
                text = "Activate 1h Peace Shield (250G)",
                icon = Icons.Filled.Shield,
                enabled = empire.gold >= 250L,
                onClick = onActivateShield,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("activate_shield_button")
            )
        }

        if (alliance != null) {
            ObsidianCard {
                Text("ACTIVE ALLIANCE TECH BUFFS", style = MaterialTheme.typography.titleSmall, color = CrownGold)
                Spacer(Modifier.height(6.dp))
                AllianceTechType.entries.forEach { tech ->
                    val lvl = alliance.techLevel(tech)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(tech.displayName, style = MaterialTheme.typography.bodyMedium, color = ParchmentIvory)
                        Text(
                            "Lv.$lvl (+${lvl * tech.bonusPerLevelPercent}%)",
                            style = MonoStatStyle,
                            color = ArcaneEmerald
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            border = BorderStroke(1.dp, EmberCrimson),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = EmberCrimson)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.sign_out), color = EmberCrimson)
        }
    }
}
