package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.Alliance
import com.example.data.GameCatalog
import com.example.data.PlayerEmpire
import com.example.data.ResourceType
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
import com.example.ui.theme.IvoryOnCrimson
import com.example.ui.theme.MonoStatStyle
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.ParchmentIvory
import com.example.ui.theme.RoyalAzure
import com.example.ui.theme.StoneSilver
import com.example.ui.theme.SubtleSteelBorder
import com.example.ui.theme.TimberMoss
import com.example.ui.theme.WarCrimson
import com.example.ui.theme.WeatheredAsh

@Composable
fun CrownfallTopResourceHud(
    empire: PlayerEmpire?,
    alliance: Alliance?,
    nowEpochMs: Long,
    modifier: Modifier = Modifier,
    perResourceLastHarvestMs: Map<ResourceType, Long> = emptyMap(),
    onAvatarClick: () -> Unit = {}
) {
    val pending = if (empire != null) {
        GameCatalog.accruedPendingResources(empire, alliance, nowEpochMs, perResourceLastHarvestMs)
    } else null

    Surface(
        color = BasaltSlate,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    listOf(EtchedSteel, CrownGold.copy(alpha = 0.55f), EtchedSteel)
                ),
                shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Avatar (tap to change) with the Keep level on a small badge
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onAvatarClick)
                    ) {
                        LordAvatar(
                            avatar = empire?.avatar ?: "crest:1",
                            avatarUrl = empire?.avatarUrl,
                            size = 44.dp,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(WarCrimson, ObsidianVoid)))
                                .border(1.dp, CrownGold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${empire?.keepLevel ?: 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = GildedHighlight
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!empire?.allianceTag.isNullOrBlank()) {
                                Text(
                                    text = "[${empire?.allianceTag}] ",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CrownGold
                                )
                            }
                            Text(
                                text = empire?.lordName ?: "Syncing Realm…",
                                style = MaterialTheme.typography.titleSmall,
                                color = ParchmentIvory,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "PWR ${formatCompact(empire?.power ?: 0L)}",
                                style = MonoStatStyle,
                                color = CrownGold
                            )
                            Text(
                                text = "(${empire?.coordX ?: 50}, ${empire?.coordY ?: 50})",
                                style = MaterialTheme.typography.labelMedium,
                                color = WeatheredAsh
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if ((empire?.shieldUntilEpochMs ?: 0L) > nowEpochMs) {
                        Surface(
                            color = ArcaneEmerald.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, ArcaneEmerald),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Shield,
                                    contentDescription = "Peace Shield active",
                                    tint = ArcaneEmerald,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = formatCountdown((empire?.shieldUntilEpochMs ?: 0L) - nowEpochMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = ArcaneEmerald
                                )
                            }
                        }
                    }

                    Surface(
                        color = ForgedIron,
                        border = BorderStroke(1.dp, EtchedSteel),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.HourglassTop,
                                contentDescription = "Speedup hourglasses",
                                tint = GildedHighlight,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = "${empire?.speedups ?: 0}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ParchmentIvory
                            )
                        }
                    }
                }
            }

            // 4-Resource Live Ticking Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ResourcePill(
                    icon = Icons.Filled.Grass,
                    tint = FoodAmber,
                    value = (empire?.food ?: 0L) + (pending?.food ?: 0L),
                    label = "Food",
                    modifier = Modifier.weight(1f)
                )
                ResourcePill(
                    icon = Icons.Filled.Forest,
                    tint = TimberMoss,
                    value = (empire?.timber ?: 0L) + (pending?.timber ?: 0L),
                    label = "Timber",
                    modifier = Modifier.weight(1f)
                )
                ResourcePill(
                    icon = Icons.Filled.Landscape,
                    tint = StoneSilver,
                    value = (empire?.stone ?: 0L) + (pending?.stone ?: 0L),
                    label = "Stone",
                    modifier = Modifier.weight(1f)
                )
                ResourcePill(
                    icon = Icons.Filled.MonetizationOn,
                    tint = GoldCoin,
                    value = (empire?.gold ?: 0L) + (pending?.gold ?: 0L),
                    label = "Gold",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ResourcePill(
    icon: ImageVector,
    tint: Color,
    value: Long,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = ForgedIron,
        border = BorderStroke(1.dp, SubtleSteelBorder),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatCompact(value),
                style = MaterialTheme.typography.labelMedium,
                color = ParchmentIvory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ObsidianCard(
    modifier: Modifier = Modifier,
    highlightBorder: Color = EtchedSteel,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        color = ForgedIron,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, highlightBorder),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .let { base ->
                if (onClick != null) base.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
                else base
            }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            content = content
        )
    }
}

@Composable
fun GildedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CrownGold,
            contentColor = DarkInkOnGold,
            disabledContainerColor = BurnishedCharcoal,
            disabledContentColor = WeatheredAsh
        ),
        modifier = modifier.height(48.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CrimsonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = WarCrimson,
            contentColor = IvoryOnCrimson,
            disabledContainerColor = BurnishedCharcoal,
            disabledContentColor = WeatheredAsh
        ),
        modifier = modifier.height(48.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ActiveTimerRibbon(
    title: String,
    subtitle: String,
    remainingMs: Long,
    accentColor: Color,
    speedupLabel: String,
    onSpeedup: () -> Unit,
    modifier: Modifier = Modifier
) {
    ObsidianCard(
        highlightBorder = accentColor.copy(alpha = 0.75f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ParchmentIvory
                )
                Text(
                    text = "$subtitle • ${formatCountdown(remainingMs)}",
                    style = MonoStatStyle,
                    color = accentColor
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onSpeedup,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor,
                    contentColor = if (accentColor == CrownGold) DarkInkOnGold else IvoryOnCrimson
                ),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(speedupLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(8.dp))
        val progress = (1f - (remainingMs.coerceIn(0L, 60_000L) / 60_000f)).coerceIn(0.12f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            color = accentColor,
            trackColor = BurnishedCharcoal,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

fun crestColorToCompose(crest: String): Color = when (crest) {
    "CRIMSON" -> WarCrimson
    "GOLD" -> CrownGold
    "EMERALD" -> ArcaneEmerald
    "ROYAL" -> RoyalAzure
    else -> EtchedSteel
}
