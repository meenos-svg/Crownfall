package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.Alliance
import com.example.data.GameCatalog
import com.example.data.PlayerEmpire
import com.example.data.RealmKingdom
import com.example.data.ResourceType
import com.example.data.TroopType
import com.example.data.WorldMarch
import com.example.data.WorldNpcNode
import com.example.data.formatCompact
import com.example.data.formatCountdown
import com.example.ui.theme.ArcaneEmerald
import com.example.ui.theme.BasaltSlate
import com.example.ui.theme.BurnishedCharcoal
import com.example.ui.theme.CrownGold
import com.example.ui.theme.DarkInkOnGold
import com.example.ui.theme.EmberCrimson
import com.example.ui.theme.EtchedSteel
import com.example.ui.theme.FoodAmber
import com.example.ui.theme.ForgedIron
import com.example.ui.theme.GildedHighlight
import com.example.ui.theme.GoldCoin
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentIvory
import com.example.ui.theme.RoyalAzure
import com.example.ui.theme.StoneSilver
import com.example.ui.theme.TimberMoss
import com.example.ui.theme.WarCrimson
import com.example.ui.theme.WeatheredAsh
import kotlin.math.hypot
import kotlin.math.roundToLong

sealed interface MapTarget {
    val id: String
    val name: String
    val coordX: Long
    val coordY: Long
    val power: Long

    data class PlayerCastle(val kingdom: RealmKingdom) : MapTarget {
        override val id: String = kingdom.ownerUid
        override val name: String = kingdom.lordName
        override val coordX: Long = kingdom.coordX
        override val coordY: Long = kingdom.coordY
        override val power: Long = kingdom.garrisonPower
    }

    data class NpcSite(val node: WorldNpcNode) : MapTarget {
        override val id: String = node.id
        override val name: String = node.name
        override val coordX: Long = node.coordX
        override val coordY: Long = node.coordY
        override val power: Long = node.garrisonPower
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealmMapScreen(
    empire: PlayerEmpire,
    alliance: Alliance?,
    kingdoms: List<RealmKingdom>,
    activeMarches: List<WorldMarch>,
    nowEpochMs: Long,
    onDispatchMarch: (
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
    ) -> Unit,
    onRecallMarch: (WorldMarch) -> Unit,
    onResolveMarch: (WorldMarch) -> Unit,
    onLaunchRally: (
        targetType: String,
        targetId: String,
        targetName: String,
        targetX: Long,
        targetY: Long,
        targetPower: Long
    ) -> Unit
) {
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var zoomScale by remember { mutableFloatStateOf(1.15f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var selectedTarget by remember { mutableStateOf<MapTarget?>(null) }

    val allTargets = remember(kingdoms) {
        kingdoms.map { MapTarget.PlayerCastle(it) } +
            GameCatalog.worldNpcNodes.map { MapTarget.NpcSite(it) }
    }

    fun worldToScreen(wx: Long, wy: Long, size: IntSize): Offset {
        val base = minOf(size.width, size.height).toFloat().coerceAtLeast(1f) * zoomScale
        val cx = size.width / 2f + panOffset.x
        val cy = size.height / 2f + panOffset.y
        val nx = (wx - empire.coordX) / 60f
        val ny = (wy - empire.coordY) / 60f
        return Offset(cx + nx * base, cy + ny * base)
    }

    val myMarches = remember(activeMarches, empire.userId) {
        activeMarches.filter { it.ownerUid == empire.userId }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianVoid)
    ) {
        // Interactive 2D Live Tactical Kingdom Map Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.75f, 2.8f)
                        panOffset = Offset(
                            (panOffset.x + pan.x).coerceIn(-900f, 900f),
                            (panOffset.y + pan.y).coerceIn(-900f, 900f)
                        )
                    }
                }
                .pointerInput(allTargets, panOffset, zoomScale, canvasSize) {
                    detectTapGestures { tap ->
                        val hit = allTargets.minByOrNull { target ->
                            val pos = worldToScreen(target.coordX, target.coordY, canvasSize)
                            hypot((pos.x - tap.x).toDouble(), (pos.y - tap.y).toDouble())
                        }
                        if (hit != null) {
                            val pos = worldToScreen(hit.coordX, hit.coordY, canvasSize)
                            if (hypot((pos.x - tap.x).toDouble(), (pos.y - tap.y).toDouble()) <= 68.0) {
                                selectedTarget = hit
                            }
                        }
                    }
                }
                .testTag("realm_map_canvas")
        ) {
            // Atmospheric obsidian radial backdrop
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1A1622), ObsidianVoid),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = maxOf(size.width, size.height) * 0.75f
                )
            )

            // Tactical grid lines
            val step = 10L
            for (g in 0L..100L step step) {
                val pStartV = worldToScreen(g, 0L, canvasSize)
                val pEndV = worldToScreen(g, 100L, canvasSize)
                drawLine(EtchedSteel.copy(alpha = 0.22f), pStartV, pEndV, strokeWidth = 1.2f)

                val pStartH = worldToScreen(0L, g, canvasSize)
                val pEndH = worldToScreen(100L, g, canvasSize)
                drawLine(EtchedSteel.copy(alpha = 0.22f), pStartH, pEndH, strokeWidth = 1.2f)
            }

            // Realm Center Citadel Territory Ring around (50, 50)
            val centerCrown = worldToScreen(50L, 50L, canvasSize)
            drawCircle(
                color = WarCrimson.copy(alpha = 0.14f),
                radius = 120f * zoomScale,
                center = centerCrown
            )
            drawCircle(
                color = CrownGold.copy(alpha = 0.35f),
                radius = 120f * zoomScale,
                center = centerCrown,
                style = Stroke(width = 2f)
            )

            // Animated Real-Time Marching Vectors
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
            activeMarches.forEach { march ->
                val start = worldToScreen(march.fromX, march.fromY, canvasSize)
                val end = worldToScreen(march.toX, march.toY, canvasSize)
                val totalMs = (march.arrivesAtEpochMs - march.departedAtEpochMs).coerceAtLeast(1000L)
                val progress = ((nowEpochMs - march.departedAtEpochMs).toFloat() / totalMs.toFloat())
                    .coerceIn(0f, 1f)
                val currentPos = Offset(
                    start.x + (end.x - start.x) * progress,
                    start.y + (end.y - start.y) * progress
                )
                val lineColor = when {
                    march.marchType == "GATHER" -> ArcaneEmerald
                    march.ownerUid == empire.userId -> CrownGold
                    else -> EmberCrimson
                }
                drawLine(
                    color = lineColor.copy(alpha = 0.7f),
                    start = start,
                    end = end,
                    strokeWidth = 3.5f,
                    pathEffect = dashEffect
                )
                drawCircle(color = lineColor.copy(alpha = 0.35f), radius = 15f, center = currentPos)
                drawCircle(color = lineColor, radius = 7f, center = currentPos)
            }

            // Draw NPC Resource Nodes & Shadow Bastions
            GameCatalog.worldNpcNodes.forEach { node ->
                val pos = worldToScreen(node.coordX, node.coordY, canvasSize)
                if (node.targetType == "BASTION") {
                    drawCircle(color = WarCrimson.copy(alpha = 0.25f), radius = 26f, center = pos)
                    drawCircle(color = WarCrimson, radius = 14f, center = pos)
                    drawCircle(
                        color = CrownGold,
                        radius = 14f,
                        center = pos,
                        style = Stroke(width = 2.5f)
                    )
                } else {
                    val resColor = when (node.resourceType) {
                        ResourceType.FOOD -> FoodAmber
                        ResourceType.TIMBER -> TimberMoss
                        ResourceType.STONE -> StoneSilver
                        ResourceType.GOLD -> GoldCoin
                        null -> ArcaneEmerald
                    }
                    drawCircle(color = resColor.copy(alpha = 0.22f), radius = 20f, center = pos)
                    drawCircle(color = resColor, radius = 10f, center = pos)
                }
            }

            // Draw Live Player Strongholds
            kingdoms.forEach { k ->
                val pos = worldToScreen(k.coordX, k.coordY, canvasSize)
                val isMine = k.ownerUid == empire.userId
                val isAlly = !empire.allianceId.isBlank() && k.allianceId == empire.allianceId
                val fillColor = when {
                    isMine -> CrownGold
                    isAlly -> RoyalAzure
                    else -> EmberCrimson
                }
                if (k.shieldUntilEpochMs > nowEpochMs) {
                    drawCircle(
                        color = ArcaneEmerald.copy(alpha = 0.38f),
                        radius = 28f,
                        center = pos,
                        style = Stroke(width = 2.5f)
                    )
                }
                drawCircle(color = fillColor.copy(alpha = 0.24f), radius = 24f, center = pos)
                drawCircle(color = fillColor, radius = if (isMine) 15f else 13f, center = pos)
                drawCircle(
                    color = ParchmentIvory,
                    radius = if (isMine) 15f else 13f,
                    center = pos,
                    style = Stroke(width = 2f)
                )
            }
        }

        // Top Map Legend + Quick Target Chips
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = BasaltSlate.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, EtchedSteel),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MapLegendDot(CrownGold, "You")
                    MapLegendDot(RoyalAzure, "Ally")
                    MapLegendDot(WarCrimson, "Bastion/Rival")
                    MapLegendDot(TimberMoss, "Nodes")
                }
            }

            // Quick Target Selector Strip so players can also tap targets effortlessly
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(
                    items = allTargets.filter { it.id != empire.userId }.take(12),
                    key = { it.id }
                ) { target ->
                    val badgeColor = when (target) {
                        is MapTarget.PlayerCastle -> EmberCrimson
                        is MapTarget.NpcSite -> if (target.node.targetType == "BASTION") WarCrimson else ArcaneEmerald
                    }
                    Surface(
                        color = ForgedIron.copy(alpha = 0.92f),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(10.dp),
                        onClick = { selectedTarget = target },
                        modifier = Modifier.testTag("map_target_chip_${target.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(badgeColor, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = target.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = ParchmentIvory,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Bottom Controls: Active Marches Strip + Center-on-Home Button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilledIconButton(
                    onClick = {
                        panOffset = Offset.Zero
                        zoomScale = 1.15f
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = CrownGold,
                        contentColor = DarkInkOnGold
                    ),
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("map_recenter_button")
                ) {
                    Icon(Icons.Filled.Home, contentDescription = "Center on Citadel")
                }
            }

            if (myMarches.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(myMarches, key = { it.id }) { march ->
                        val remaining = (march.arrivesAtEpochMs - nowEpochMs).coerceAtLeast(0L)
                        Surface(
                            color = BasaltSlate.copy(alpha = 0.95f),
                            border = BorderStroke(1.dp, CrownGold),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.width(275.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${march.marchType}: ${march.targetName}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = ParchmentIvory,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatCountdown(remaining),
                                        style = MonoStatStyle,
                                        color = CrownGold
                                    )
                                }
                                Text(
                                    text = "PWR ${formatCompact(march.troopPower)} -> (${march.toX}, ${march.toY})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WeatheredAsh
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onRecallMarch(march) },
                                        border = BorderStroke(1.dp, EtchedSteel),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                    ) {
                                        Icon(Icons.Filled.Undo, null, Modifier.size(15.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Recall", style = MaterialTheme.typography.labelSmall)
                                    }
                                    if (remaining <= 0L) {
                                        GildedButton(
                                            text = "Resolve",
                                            onClick = { onResolveMarch(march) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(40.dp)
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

    // Tactical Target Command Bottom Sheet
    selectedTarget?.let { target ->
        val isOwnCastle = target.id == empire.userId
        val isResource = target is MapTarget.NpcSite && target.node.targetType == "RESOURCE"
        val isShielded = target is MapTarget.PlayerCastle && target.kingdom.shieldUntilEpochMs > nowEpochMs
        val isAllyCastle = target is MapTarget.PlayerCastle &&
            empire.allianceId.isNotBlank() &&
            target.kingdom.allianceId == empire.allianceId
        val travelMs = GameCatalog.marchDurationMs(
            empire.coordX, empire.coordY, target.coordX, target.coordY, alliance
        )

        var troopShare by remember(target) { mutableFloatStateOf(0.65f) }
        val sendInf = (empire.infantry * troopShare).roundToLong()
        val sendCav = (empire.cavalry * troopShare).roundToLong()
        val sendArc = (empire.archers * troopShare).roundToLong()
        val sendSiege = (empire.siege * troopShare).roundToLong()
        val sentPower = sendInf * TroopType.INFANTRY.power +
            sendCav * TroopType.CAVALRY.power +
            sendArc * TroopType.ARCHERS.power +
            sendSiege * TroopType.SIEGE.power

        ModalBottomSheet(
            onDismissRequest = { selectedTarget = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = ForgedIron
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = target.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = ParchmentIvory
                        )
                        Text(
                            text = "Coordinates (${target.coordX}, ${target.coordY}) • March ETA ${formatCountdown(travelMs)}",
                            style = MonoStatStyle,
                            color = CrownGold
                        )
                    }
                    Surface(
                        color = BurnishedCharcoal,
                        border = BorderStroke(1.dp, EtchedSteel),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (isResource) "RESOURCE" else "DEF ${formatCompact(target.power)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isResource) ArcaneEmerald else EmberCrimson,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                if (target is MapTarget.NpcSite && target.node.targetType == "RESOURCE") {
                    Text(
                        text = "Lv.${target.node.level} Resource Deposit — Dispatch troops to harvest ${target.node.resourceType?.displayName ?: "Resources"} safely.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WeatheredAsh
                    )
                } else if (target is MapTarget.NpcSite) {
                    Text(
                        text = "Lv.${target.node.level} Shadow Bastion — Victory grants plunder (${formatCompact(target.node.rewardFood)}F, ${formatCompact(target.node.rewardGold)}G) + 1 Speedup Hourglass.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WeatheredAsh
                    )
                } else if (target is MapTarget.PlayerCastle) {
                    val tagText = if (target.kingdom.allianceTag.isNotBlank()) "[${target.kingdom.allianceTag}] " else ""
                    Text(
                        text = "${tagText}Keep Lv.${target.kingdom.keepLevel} • Total Power ${formatCompact(target.kingdom.power)}" +
                            if (isShielded) " • Protected by Peace Shield" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isShielded) ArcaneEmerald else WeatheredAsh
                    )
                }

                if (!isOwnCastle && !isAllyCastle && !isShielded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Commit Garrison", style = MaterialTheme.typography.titleSmall, color = ParchmentIvory)
                        Text(
                            "${(troopShare * 100).toInt()}% (PWR ${formatCompact(sentPower)})",
                            style = MonoStatStyle,
                            color = GildedHighlight
                        )
                    }
                    Slider(
                        value = troopShare,
                        onValueChange = { troopShare = it },
                        valueRange = 0.1f..1.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = CrownGold,
                            activeTrackColor = if (isResource) ArcaneEmerald else WarCrimson,
                            inactiveTrackColor = BurnishedCharcoal
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isResource) {
                            GildedButton(
                                text = "March & Gather",
                                icon = Icons.Filled.Flag,
                                enabled = sentPower > 0L,
                                onClick = {
                                    onDispatchMarch(
                                        "GATHER", "RESOURCE", target.id, target.name,
                                        target.coordX, target.coordY,
                                        sendInf, sendCav, sendArc, sendSiege
                                    )
                                    selectedTarget = null
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("dispatch_gather_button")
                            )
                        } else {
                            CrimsonButton(
                                text = "March & Raid",
                                icon = Icons.Filled.Whatshot,
                                enabled = sentPower > 0L,
                                onClick = {
                                    val tType = if (target is MapTarget.PlayerCastle) "KINGDOM" else "BASTION"
                                    onDispatchMarch(
                                        "RAID", tType, target.id, target.name,
                                        target.coordX, target.coordY,
                                        sendInf, sendCav, sendArc, sendSiege
                                    )
                                    selectedTarget = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dispatch_raid_button")
                            )
                            GildedButton(
                                text = "Rally",
                                icon = Icons.Filled.Groups,
                                enabled = alliance != null,
                                onClick = {
                                    val tType = if (target is MapTarget.PlayerCastle) "KINGDOM" else "BASTION"
                                    onLaunchRally(
                                        tType, target.id, target.name,
                                        target.coordX, target.coordY, target.power
                                    )
                                    selectedTarget = null
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("launch_rally_button")
                            )
                        }
                    }
                    if (!isResource && alliance == null) {
                        Text(
                            text = "Join or found an Alliance to coordinate War Rallies.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WeatheredAsh
                        )
                    }
                } else {
                    Surface(
                        color = BurnishedCharcoal,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isShielded) Icons.Filled.Shield else Icons.Filled.MyLocation,
                                contentDescription = null,
                                tint = CrownGold
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = when {
                                    isOwnCastle -> "This is your Obsidian Citadel."
                                    isAllyCastle -> "Allied Citadel — friendly territory."
                                    else -> "Protected by an active Obsidian Peace Shield."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = ParchmentIvory
                            )
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun MapLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, CircleShape)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ParchmentIvory,
            fontWeight = FontWeight.Medium
        )
    }
}
