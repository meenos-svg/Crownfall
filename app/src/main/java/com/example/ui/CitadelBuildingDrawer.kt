package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Alliance
import com.example.data.BuildingType
import com.example.data.GameCatalog
import com.example.data.PlayerEmpire
import com.example.data.ResourceCost
import com.example.data.ResourceType
import com.example.data.TroopType
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
import com.example.ui.theme.StoneSilver
import com.example.ui.theme.SubtleSteelBorder
import com.example.ui.theme.TimberMoss
import com.example.ui.theme.WarCrimson
import com.example.ui.theme.WeatheredAsh
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CitadelBuildingDrawer(
    building: BuildingType,
    empire: PlayerEmpire,
    alliance: Alliance?,
    nowEpochMs: Long,
    perResourceLastHarvestMs: Map<ResourceType, Long>,
    onDismiss: () -> Unit,
    onUpgradeBuilding: (BuildingType) -> Unit,
    onSpeedupBuilding: () -> Unit,
    onTrainTroops: (TroopType, Long) -> Unit,
    onSpeedupTraining: () -> Unit,
    onCollectSingleResource: (ResourceType) -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val level = empire.buildingLevel(building)
    val cost = GameCatalog.upgradeCost(building, level)
    val durationMs = GameCatalog.upgradeDurationMs(building, level)
    val isUpgradingThis = empire.activeBuildKey == building.name && empire.activeBuildEndsAtMs > nowEpochMs
    val otherBuildBusy = empire.activeBuildKey.isNotBlank() && empire.activeBuildKey != building.name
    val requiresKeep = building != BuildingType.KEEP && level >= empire.keepLevel + 2L
    val isMaxLevel = level >= 25L
    val canUpgrade = !isUpgradingThis && !otherBuildBusy && !requiresKeep && !isMaxLevel && cost.canAfford(empire)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BasaltSlate,
        contentColor = ParchmentIvory,
        scrimColor = ObsidianVoid.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CrownGold.copy(alpha = 0.7f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Building Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(ForgedIron, BurnishedCharcoal)
                            )
                        )
                        .border(1.5.dp, CrownGold.copy(alpha = 0.75f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = buildingDrawerIcon(building),
                        contentDescription = building.displayName,
                        tint = CrownGold,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = building.displayName,
                            style = MaterialTheme.typography.headlineSmall,
                            color = ParchmentIvory
                        )
                        Surface(
                            color = CrownGold.copy(alpha = 0.16f),
                            border = BorderStroke(1.dp, CrownGold.copy(alpha = 0.7f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Lv.$level",
                                style = MonoStatStyle,
                                color = GildedHighlight,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = building.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = WeatheredAsh
                    )
                }
            }

            // Active Construction Banner for this structure
            if (isUpgradingThis) {
                ActiveTimerRibbon(
                    title = "Upgrading to Level ${level + 1}",
                    subtitle = "Remaining",
                    remainingMs = empire.activeBuildEndsAtMs - nowEpochMs,
                    accentColor = CrownGold,
                    speedupLabel = if (empire.speedups > 0) "Speed Up" else "100 Gold",
                    onSpeedup = onSpeedupBuilding,
                    modifier = Modifier.testTag("drawer_speedup_build")
                )
            }

            // Contextual Section (Troop Training / Economy Harvest / Keep Garrison / Alliance Council)
            val troopType = GameCatalog.troopTypeFor(building)
            val resourceType = GameCatalog.resourceTypeFor(building)

            when {
                troopType != null -> {
                    MilitaryTrainingSection(
                        troop = troopType,
                        empire = empire,
                        nowEpochMs = nowEpochMs,
                        onTrainTroops = onTrainTroops,
                        onSpeedupTraining = onSpeedupTraining
                    )
                }

                resourceType != null -> {
                    EconomyProductionSection(
                        resourceType = resourceType,
                        empire = empire,
                        alliance = alliance,
                        nowEpochMs = nowEpochMs,
                        perResourceLastHarvestMs = perResourceLastHarvestMs,
                        onCollect = { onCollectSingleResource(resourceType) }
                    )
                }

                building == BuildingType.KEEP -> {
                    KeepDominionOverviewSection(empire = empire, nowEpochMs = nowEpochMs)
                }

                building == BuildingType.ALLIANCE_HALL -> {
                    AllianceHallOverviewSection(empire = empire, alliance = alliance)
                }
            }

            HorizontalDivider(color = SubtleSteelBorder)

            // Structure Upgrade Panel
            ObsidianCard(
                highlightBorder = if (canUpgrade) ArcaneEmerald.copy(alpha = 0.75f) else EtchedSteel
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Construction,
                            contentDescription = null,
                            tint = if (canUpgrade) ArcaneEmerald else CrownGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isMaxLevel) "Maximum Tier Reached" else "Upgrade to Level ${level + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = ParchmentIvory
                        )
                    }
                    if (!isMaxLevel) {
                        Text(
                            text = formatCountdown(durationMs),
                            style = MonoStatStyle,
                            color = WeatheredAsh
                        )
                    }
                }

                if (!isMaxLevel) {
                    Spacer(Modifier.height(10.dp))
                    ResourceCostRow(cost = cost, empire = empire)
                    Spacer(Modifier.height(12.dp))

                    val helperReason = when {
                        isUpgradingThis -> "Construction is currently underway."
                        otherBuildBusy -> {
                            val activeName = BuildingType.fromRaw(empire.activeBuildKey)?.displayName ?: "Structure"
                            "Builder busy on $activeName."
                        }
                        requiresKeep -> "Requires Obsidian Keep Lv.${(level - 1).coerceAtLeast(1)}."
                        !cost.canAfford(empire) -> "Gather more resources to start construction."
                        else -> null
                    }
                    if (helperReason != null) {
                        Text(
                            text = helperReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (requiresKeep || !cost.canAfford(empire)) EmberCrimson else WeatheredAsh
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    GildedButton(
                        text = if (isUpgradingThis) "Upgrading…" else "Upgrade to Lv.${level + 1}",
                        onClick = { onUpgradeBuilding(building) },
                        enabled = canUpgrade,
                        icon = Icons.Filled.ArrowUpward,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("drawer_upgrade_button")
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MilitaryTrainingSection(
    troop: TroopType,
    empire: PlayerEmpire,
    nowEpochMs: Long,
    onTrainTroops: (TroopType, Long) -> Unit,
    onSpeedupTraining: () -> Unit
) {
    var sliderValue by remember(troop) { mutableFloatStateOf(25f) }
    val qty = (sliderValue.roundToLong() / 5L * 5L).coerceIn(5L, 250L)
    val cost = GameCatalog.trainingCost(troop, qty)
    val durationMs = GameCatalog.trainingDurationMs(troop, qty, empire.buildingLevel(troop.building))
    val isTrainingThis = empire.activeTrainKey == troop.name && empire.activeTrainEndsAtMs > nowEpochMs
    val otherTrainingBusy = empire.activeTrainKey.isNotBlank() && empire.activeTrainKey != troop.name
    val canTrain = !isTrainingThis && !otherTrainingBusy && cost.canAfford(empire)

    ObsidianCard(highlightBorder = WarCrimson.copy(alpha = 0.65f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = troop.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = ParchmentIvory
                )
                Text(
                    text = "${troop.counters} • ${troop.power} PWR / unit",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeatheredAsh
                )
            }
            Surface(
                color = BurnishedCharcoal,
                border = BorderStroke(1.dp, CrownGold.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Garrison: ${formatCompact(empire.troopCount(troop))}",
                    style = MonoStatStyle,
                    color = GildedHighlight,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        if (isTrainingThis) {
            ActiveTimerRibbon(
                title = "Training ${empire.activeTrainQty} ${troop.displayName}",
                subtitle = "Ready in",
                remainingMs = empire.activeTrainEndsAtMs - nowEpochMs,
                accentColor = EmberCrimson,
                speedupLabel = if (empire.speedups > 0) "Speed Up" else "80 Gold",
                onSpeedup = onSpeedupTraining,
                modifier = Modifier.testTag("drawer_speedup_train")
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recruit Batch: $qty",
                    style = MaterialTheme.typography.labelLarge,
                    color = ParchmentIvory
                )
                Text(
                    text = formatCountdown(durationMs),
                    style = MonoStatStyle,
                    color = WeatheredAsh
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = 5f..250f,
                colors = SliderDefaults.colors(
                    thumbColor = CrownGold,
                    activeTrackColor = WarCrimson,
                    inactiveTrackColor = BurnishedCharcoal
                ),
                modifier = Modifier.testTag("drawer_train_slider")
            )
            ResourceCostRow(cost = cost, empire = empire)
            Spacer(Modifier.height(10.dp))
            if (otherTrainingBusy) {
                val activeTroop = TroopType.fromRaw(empire.activeTrainKey)?.displayName ?: "Troops"
                Text(
                    text = "Barracks queue busy training $activeTroop.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeatheredAsh
                )
                Spacer(Modifier.height(6.dp))
            }
            CrimsonButton(
                text = "Train $qty ${troop.displayName}",
                onClick = { onTrainTroops(troop, qty) },
                enabled = canTrain,
                icon = Icons.Filled.SportsMartialArts,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("drawer_train_button")
            )
        }
    }
}

@Composable
private fun EconomyProductionSection(
    resourceType: ResourceType,
    empire: PlayerEmpire,
    alliance: Alliance?,
    nowEpochMs: Long,
    perResourceLastHarvestMs: Map<ResourceType, Long>,
    onCollect: () -> Unit
) {
    val hourlyRate = GameCatalog.hourlyRateFor(empire, alliance, resourceType)
    val accrued = GameCatalog.accruedForResource(
        empire, alliance, resourceType, nowEpochMs, perResourceLastHarvestMs
    )
    val floor = when (resourceType) {
        ResourceType.FOOD -> 45L
        ResourceType.TIMBER -> 45L
        ResourceType.STONE -> 35L
        ResourceType.GOLD -> 25L
    }
    val collectAmount = accrued.coerceAtLeast(floor)
    val accent = resourceAccentColor(resourceType)
    val yieldBonusPct = (alliance?.techYieldLevel ?: 0L) * 8L

    ObsidianCard(highlightBorder = accent.copy(alpha = 0.7f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "${resourceType.displayName} Production",
                    style = MaterialTheme.typography.titleMedium,
                    color = ParchmentIvory
                )
                Text(
                    text = "+${formatCompact(hourlyRate)} / hr" +
                        if (yieldBonusPct > 0L) " (+$yieldBonusPct% Alliance)" else "",
                    style = MonoStatStyle,
                    color = accent
                )
            }
            Surface(
                color = accent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = resourceIcon(resourceType),
                        contentDescription = resourceType.displayName,
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "+${formatCompact(collectAmount)}",
                        style = MonoStatStyle,
                        color = ParchmentIvory
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        GildedButton(
            text = "Collect +${formatCompact(collectAmount)} ${resourceType.displayName}",
            onClick = onCollect,
            icon = Icons.Filled.TrendingUp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("drawer_collect_resource")
        )
    }
}

@Composable
private fun KeepDominionOverviewSection(empire: PlayerEmpire, nowEpochMs: Long) {
    val shieldRemaining = (empire.shieldUntilEpochMs - nowEpochMs).coerceAtLeast(0L)
    ObsidianCard(highlightBorder = CrownGold.copy(alpha = 0.6f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Dominion Strength", style = MaterialTheme.typography.titleMedium, color = ParchmentIvory)
                Text(
                    text = "Unlocks buildings up to Lv.${(empire.keepLevel + 2L).coerceAtMost(25L)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeatheredAsh
                )
            }
            Text(
                text = "${formatCompact(empire.power)} PWR",
                style = MonoStatStyle,
                color = GildedHighlight
            )
        }

        if (shieldRemaining > 0L) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = ArcaneEmerald, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Peace Shield active • ${formatCountdown(shieldRemaining)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ArcaneEmerald
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GarrisonMiniChip("Infantry", empire.infantry, Modifier.weight(1f))
            GarrisonMiniChip("Cavalry", empire.cavalry, Modifier.weight(1f))
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GarrisonMiniChip("Archers", empire.archers, Modifier.weight(1f))
            GarrisonMiniChip("Siege", empire.siege, Modifier.weight(1f))
        }
    }
}

@Composable
private fun GarrisonMiniChip(label: String, count: Long, modifier: Modifier = Modifier) {
    Surface(
        color = BurnishedCharcoal,
        border = BorderStroke(1.dp, SubtleSteelBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = WeatheredAsh)
            Text(formatCompact(count), style = MonoStatStyle, color = ParchmentIvory)
        }
    }
}

@Composable
private fun AllianceHallOverviewSection(empire: PlayerEmpire, alliance: Alliance?) {
    ObsidianCard(highlightBorder = CrownGold.copy(alpha = 0.55f)) {
        if (alliance != null) {
            Text(
                text = "[${alliance.tag}] ${alliance.name}",
                style = MaterialTheme.typography.titleMedium,
                color = GildedHighlight
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${alliance.memberUids.size} Lords • Rally Capacity Tier ${empire.allianceHallLevel}",
                style = MaterialTheme.typography.bodySmall,
                color = WeatheredAsh
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TechMiniBadge("March +${alliance.techMarchLevel * 5}%", Modifier.weight(1f))
                TechMiniBadge("Yield +${alliance.techYieldLevel * 8}%", Modifier.weight(1f))
                TechMiniBadge("Atk +${alliance.techAttackLevel * 6}%", Modifier.weight(1f))
            }
        } else {
            Text(
                text = "No Alliance Sworn",
                style = MaterialTheme.typography.titleMedium,
                color = ParchmentIvory
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Visit the Alliance tab to found or join a war council, unlock shared Tech Buffs, and launch coordinated War Rallies.",
                style = MaterialTheme.typography.bodySmall,
                color = WeatheredAsh
            )
        }
    }
}

@Composable
private fun TechMiniBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = BurnishedCharcoal,
        border = BorderStroke(1.dp, EtchedSteel),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = GildedHighlight,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ResourceCostRow(cost: ResourceCost, empire: PlayerEmpire) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CostChip(Icons.Filled.Grass, FoodAmber, cost.food, empire.food >= cost.food, Modifier.weight(1f))
        CostChip(Icons.Filled.Forest, TimberMoss, cost.timber, empire.timber >= cost.timber, Modifier.weight(1f))
        CostChip(Icons.Filled.Landscape, StoneSilver, cost.stone, empire.stone >= cost.stone, Modifier.weight(1f))
        CostChip(Icons.Filled.MonetizationOn, GoldCoin, cost.gold, empire.gold >= cost.gold, Modifier.weight(1f))
    }
}

@Composable
private fun CostChip(
    icon: ImageVector,
    tint: Color,
    amount: Long,
    affordable: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (affordable) BurnishedCharcoal else WarCrimson.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, if (affordable) SubtleSteelBorder else EmberCrimson.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(7.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
            Text(
                text = formatCompact(amount),
                style = MaterialTheme.typography.labelSmall,
                color = if (affordable) ParchmentIvory else EmberCrimson,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

internal fun buildingDrawerIcon(type: BuildingType): ImageVector = when (type) {
    BuildingType.KEEP -> Icons.Filled.Castle
    BuildingType.BARRACKS -> Icons.Filled.SportsMartialArts
    BuildingType.STABLES -> Icons.Filled.Shield
    BuildingType.ARCHERY -> Icons.Filled.MyLocation
    BuildingType.SIEGE -> Icons.Filled.LocalFireDepartment
    BuildingType.ALLIANCE_HALL -> Icons.Filled.Groups
    BuildingType.FARM -> Icons.Filled.Agriculture
    BuildingType.LUMBER -> Icons.Filled.Forest
    BuildingType.QUARRY -> Icons.Filled.Hardware
    BuildingType.MINT -> Icons.Filled.AccountBalance
}

internal fun resourceIcon(type: ResourceType): ImageVector = when (type) {
    ResourceType.FOOD -> Icons.Filled.Grass
    ResourceType.TIMBER -> Icons.Filled.Forest
    ResourceType.STONE -> Icons.Filled.Landscape
    ResourceType.GOLD -> Icons.Filled.MonetizationOn
}

internal fun resourceAccentColor(type: ResourceType): Color = when (type) {
    ResourceType.FOOD -> FoodAmber
    ResourceType.TIMBER -> TimberMoss
    ResourceType.STONE -> StoneSilver
    ResourceType.GOLD -> GoldCoin
}
